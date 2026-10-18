package com.freemetar.app.ui.flight

import com.freemetar.app.data.nav.AirportNav
import com.freemetar.app.data.nav.Ils
import com.freemetar.app.data.nav.NavRunway
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.*

/**
 * ILS approach paths w 3D dla Navigation Display.
 *
 * Rysuje korytarze podejścia ILS:
 * - Localizer: ±2.5° od kursu pasa (boki korytarza)
 * - Glideslope: 3° od progu (góra korytarza)
 * - Długość: 10 NM przed progiem
 * - Kolor: cyjan/zielony, transparentny
 */
object IlsRenderer {

    private const val SRC_ILS = "oab-ils-paths"
    private const val LYR_ILS_SIDES = "oab-ils-sides"
    private const val LYR_ILS_CENTERLINE = "oab-ils-centerline"

    /**
     * Dodaj warstwy ILS do stylu mapy.
     */
    fun addLayers(style: Style, airportNav: AirportNav?) {
        if (airportNav == null) return

        // Zbierz pasy z ILS
        val ilsRunways = airportNav.runways.filter { it.ils != null }
        if (ilsRunways.isEmpty()) return

        val features = ilsRunways.flatMap { rwy ->
            createIlsFeatures(rwy, airportNav.elevFt ?: 0.0)
        }

        // Źródło danych
        val source = style.getSourceAs<GeoJsonSource>(SRC_ILS)
        if (source != null) {
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        } else {
            style.addSource(GeoJsonSource(SRC_ILS, FeatureCollection.fromFeatures(features)))
        }

        // Warstwa: boki korytarza (3D extrusion)
        if (style.getLayer(LYR_ILS_SIDES) == null) {
            style.addLayer(
                FillExtrusionLayer(LYR_ILS_SIDES, SRC_ILS)
                    .withFilter(
                        org.maplibre.android.style.expressions.Expression.eq(
                            org.maplibre.android.style.expressions.Expression.get("type"),
                            "corridor"
                        )
                    )
                    .withProperties(
                        PropertyFactory.fillExtrusionColor("#00FFFF"),
                        PropertyFactory.fillExtrusionOpacity(0.15f),
                        PropertyFactory.fillExtrusionHeight(
                            org.maplibre.android.style.expressions.Expression.get("height")
                        ),
                        PropertyFactory.fillExtrusionBase(
                            org.maplibre.android.style.expressions.Expression.get("base")
                        )
                    )
            )
        }

        // Warstwa: centerline (linia środkowa)
        if (style.getLayer(LYR_ILS_CENTERLINE) == null) {
            style.addLayer(
                LineLayer(LYR_ILS_CENTERLINE, SRC_ILS)
                    .withFilter(
                        org.maplibre.android.style.expressions.Expression.eq(
                            org.maplibre.android.style.expressions.Expression.get("type"),
                            "centerline"
                        )
                    )
                    .withProperties(
                        PropertyFactory.lineColor("#00FF00"),
                        PropertyFactory.lineWidth(2f),
                        PropertyFactory.lineOpacity(0.6f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                    )
            )
        }
    }

    /**
     * Usuń warstwy ILS.
     */
    fun removeLayers(style: Style) {
        style.getLayer(LYR_ILS_SIDES)?.let { style.removeLayer(it) }
        style.getLayer(LYR_ILS_CENTERLINE)?.let { style.removeLayer(it) }
        style.getSourceAs<GeoJsonSource>(SRC_ILS)?.let { style.removeSource(it) }
    }

    /**
     * Stwórz feature'y GeoJSON dla ILS jednego pasa.
     */
    private fun createIlsFeatures(runway: NavRunway, airportElevFt: Double): List<Feature> {
        val ils = runway.ils ?: return emptyList()
        val thresholdLat = runway.thresholdLat ?: return emptyList()
        val thresholdLon = runway.thresholdLon ?: return emptyList()
        val course = ils.locCourseDeg ?: return emptyList()
        val gsAngle = ils.gsAngleDeg ?: 3.0
        val elevFt = runway.elevFt ?: airportElevFt

        val features = mutableListOf<Feature>()

        // Parametry korytarza
        val lengthNm = 10.0 // 10 NM przed progiem
        val locDeviationDeg = 2.5 // ±2.5° localizer
        val heightAtEndFt = lengthNm * 6076.0 * tan(Math.toRadians(gsAngle)) // Wysokość na końcu glideslope

        // Punkty na ziemi (próg i koniec)
        val thresholdPoint = LatLon(thresholdLat, thresholdLon)
        val endPoint = thresholdPoint.offset(course + 180, lengthNm) // 10 NM wstecz

        // Boki korytarza (lewy i prawy)
        val leftCourse = (course - locDeviationDeg + 360) % 360
        val rightCourse = (course + locDeviationDeg) % 360

        // Punkty narożników (próg i koniec, lewy i prawy)
        val thresholdLeft = thresholdPoint.offset(leftCourse, 0.1) // 0.1 NM szerokość przy progu
        val thresholdRight = thresholdPoint.offset(rightCourse, 0.1)
        val endLeft = endPoint.offset(leftCourse, lengthNm * tan(Math.toRadians(locDeviationDeg)))
        val endRight = endPoint.offset(rightCourse, lengthNm * tan(Math.toRadians(locDeviationDeg)))

        // Feature 1: Lewy bok korytarza (3D polygon)
        val leftSide = Polygon.fromLngLats(
            listOf(
                listOf(
                    Point.fromLngLat(thresholdLeft.lon, thresholdLeft.lat),
                    Point.fromLngLat(endLeft.lon, endLeft.lat),
                    Point.fromLngLat(endLeft.lon, endLeft.lat), // Zamknięcie
                    Point.fromLngLat(thresholdLeft.lon, thresholdLeft.lat)
                )
            )
        )
        features.add(
            Feature.fromGeometry(leftSide).apply {
                addStringProperty("type", "corridor")
                addNumberProperty("base", elevFt)
                addNumberProperty("height", elevFt + heightAtEndFt)
            }
        )

        // Feature 2: Prawy bok korytarza
        val rightSide = Polygon.fromLngLats(
            listOf(
                listOf(
                    Point.fromLngLat(thresholdRight.lon, thresholdRight.lat),
                    Point.fromLngLat(endRight.lon, endRight.lat),
                    Point.fromLngLat(endRight.lon, endRight.lat),
                    Point.fromLngLat(thresholdRight.lon, thresholdRight.lat)
                )
            )
        )
        features.add(
            Feature.fromGeometry(rightSide).apply {
                addStringProperty("type", "corridor")
                addNumberProperty("base", elevFt)
                addNumberProperty("height", elevFt + heightAtEndFt)
            }
        )

        // Feature 3: Centerline (linia środkowa)
        val centerline = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(thresholdPoint.lon, thresholdPoint.lat),
                Point.fromLngLat(endPoint.lon, endPoint.lat)
            )
        )
        features.add(
            Feature.fromGeometry(centerline).apply {
                addStringProperty("type", "centerline")
            }
        )

        return features
    }

    /**
     * Pomocnicza klasa: punkt geograficzny.
     */
    private data class LatLon(val lat: Double, val lon: Double) {
        /**
         * Przesuń punkt o [distanceNm] mil morskich w kierunku [bearingDeg].
         */
        fun offset(bearingDeg: Double, distanceNm: Double): LatLon {
            val R = 3440.065 // Promień Ziemi w milach morskich
            val lat1 = Math.toRadians(lat)
            val lon1 = Math.toRadians(lon)
            val brng = Math.toRadians(bearingDeg)
            val d = distanceNm

            val lat2 = asin(sin(lat1) * cos(d / R) + cos(lat1) * sin(d / R) * cos(brng))
            val lon2 = lon1 + atan2(
                sin(brng) * sin(d / R) * cos(lat1),
                cos(d / R) - sin(lat1) * sin(lat2)
            )

            return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
        }
    }
}
