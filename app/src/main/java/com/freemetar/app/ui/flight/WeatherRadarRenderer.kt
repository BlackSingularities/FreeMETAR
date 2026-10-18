package com.freemetar.app.ui.flight

import com.freemetar.app.data.RadarPoint
import com.freemetar.app.data.WeatherRadar
import com.freemetar.app.data.WeatherRadarData
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Weather Radar overlay renderer dla Navigation Display.
 *
 * Rysuje opady na mapie jako grid kolorowych punktów:
 * - Zielony: lekkie opady (0-1 mm/h)
 * - Żółty: umiarkowane (1-5 mm/h)
 * - Pomarańczowy: silne (5-10 mm/h)
 * - Czerwony: bardzo silne (10+ mm/h)
 */
object WeatherRadarRenderer {

    private const val SRC_RADAR = "oab-weather-radar"
    private const val LYR_RADAR = "oab-weather-radar-layer"

    /**
     * Dodaj warstwę weather radar do stylu mapy.
     */
    fun addLayer(style: Style, radarData: WeatherRadarData?, gridSize: Int = 16) {
        if (radarData == null) {
            removeLayer(style)
            return
        }

        // Generuj grid punktów
        val points = WeatherRadar.generateRadarGrid(radarData, gridSize)
        val features = points.map { point ->
            Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat)).apply {
                addNumberProperty("intensity", point.intensityMmH)
                addNumberProperty("color", WeatherRadar.intensityToColor(point.intensityMmH))
            }
        }

        // Źródło danych
        val source = style.getSourceAs<GeoJsonSource>(SRC_RADAR)
        if (source != null) {
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        } else {
            style.addSource(GeoJsonSource(SRC_RADAR, FeatureCollection.fromFeatures(features)))
        }

        // Warstwa: kolorowe punkty
        if (style.getLayer(LYR_RADAR) == null) {
            style.addLayer(
                CircleLayer(LYR_RADAR, SRC_RADAR)
                    .withProperties(
                        PropertyFactory.circleRadius(
                            org.maplibre.android.style.expressions.Expression.interpolate(
                                org.maplibre.android.style.expressions.Expression.exponential(1.5f),
                                org.maplibre.android.style.expressions.Expression.zoom(),
                                org.maplibre.android.style.expressions.Expression.stop(8, 12f),
                                org.maplibre.android.style.expressions.Expression.stop(10, 18f),
                                org.maplibre.android.style.expressions.Expression.stop(12, 24f)
                            )
                        ),
                        PropertyFactory.circleColor(
                            org.maplibre.android.style.expressions.Expression.toColor(
                                org.maplibre.android.style.expressions.Expression.get("color")
                            )
                        ),
                        PropertyFactory.circleOpacity(0.6f),
                        PropertyFactory.circleBlur(0.8f)
                    )
            )
        }
    }

    /**
     * Usuń warstwę weather radar.
     */
    fun removeLayer(style: Style) {
        style.getLayer(LYR_RADAR)?.let { style.removeLayer(it) }
        style.getSourceAs<GeoJsonSource>(SRC_RADAR)?.let { style.removeSource(it) }
    }

    /**
     * Aktualizuj dane radaru (refresh co 5-10 minut).
     */
    fun updateLayer(style: Style, radarData: WeatherRadarData?, gridSize: Int = 16) {
        if (radarData == null || radarData.isStale()) {
            removeLayer(style)
            return
        }

        addLayer(style, radarData, gridSize)
    }
}
