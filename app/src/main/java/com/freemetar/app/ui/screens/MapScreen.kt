package com.freemetar.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Route
import kotlin.math.roundToInt

import com.freemetar.app.domain.loc

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.Metar
import com.freemetar.app.data.Sigmet
import com.freemetar.app.data.SavedRoute
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.LineString
import com.freemetar.app.ui.components.CategoryTag
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.FmColors
import com.freemetar.app.ui.theme.Mono
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SRC_METAR = "oab-metars"
private const val SRC_SIGMET = "oab-sigmets"
private const val LYR_METAR = "oab-metar-dots"
private const val SRC_ROUTE = "oab-route"
private const val SRC_ROUTE_PTS = "oab-route-points"
private const val SRC_ROUTE_PROC = "oab-route-proc"
private const val SRC_PLANE = "oab-plane"
private const val SRC_TRACK = "oab-track"
private const val IMG_PLANE = "oab-plane-icon"
private const val SRC_CURTAIN = "oab-track-curtain"
private const val SRC_TRACK_LBL = "oab-track-labels"
private const val LYR_CURTAIN = "oab-track-curtain-3d"
private const val LYR_BUILDINGS = "oab-buildings-3d"
private const val LYR_TRACK = "oab-track-line"

/** Kolor wysokości: nisko zieleń, średnio cyjan, wyżej błękit, poziom przelotowy magenta. */
private fun altColor(): Expression = Expression.interpolate(
    Expression.linear(), Expression.get("alt"),
    *ALT_STOPS.map { (ft, c) -> Expression.stop(ft, Expression.rgb(c[0], c[1], c[2])) }.toTypedArray(),
)

/** Symbol samolotu (żółty jak na wyświetlaczach) jako bitmapa dla warstwy mapy. */
private fun planeBitmap(): android.graphics.Bitmap {
    val size = 64
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC21A.toInt() }
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0E1216.toInt(); style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f
    }
    val path = android.graphics.Path().apply {
        moveTo(32f, 4f); lineTo(36f, 24f); lineTo(60f, 36f); lineTo(60f, 42f); lineTo(36f, 36f); lineTo(35f, 50f)
        lineTo(44f, 56f); lineTo(44f, 60f); lineTo(32f, 57f); lineTo(20f, 60f); lineTo(20f, 56f); lineTo(29f, 50f)
        lineTo(28f, 36f); lineTo(4f, 42f); lineTo(4f, 36f); lineTo(28f, 24f); close()
    }
    cv.drawPath(path, p); cv.drawPath(path, stroke)
    return bmp
}

private fun hex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())

/** GeoJSON stacji METAR z kolorem kategorii zapisanym w cechach (warstwa koloruje wg „color”). */
private fun metarFeatures(list: List<Metar>, c: FmColors): FeatureCollection =
    FeatureCollection.fromFeatures(list.mapNotNull { m ->
        val lat = m.lat ?: return@mapNotNull null
        val lon = m.lon ?: return@mapNotNull null
        Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
            addStringProperty("id", m.station)
            addStringProperty("color", hex(c.category(m.flightCategory)))
        }
    })

private fun sigmetFeatures(list: List<Sigmet>): FeatureCollection =
    FeatureCollection.fromFeatures(list.filter { it.area.size >= 3 }.map { s ->
        val ring = s.area.map { Point.fromLngLat(it.lon, it.lat) }.let { if (it.first() != it.last()) it + it.first() else it }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addStringProperty("id", s.id) }
    })

/**
 * Odcinki pełnej ścieżki rozdzielone na trasę (linia ciągła) i procedury SID/STAR/podejście (kreskowana).
 * Odcinek należy do procedury, gdy jego punkt końcowy jest punktem procedury.
 */
private fun routeLines(r: SavedRoute?, procedures: Boolean): FeatureCollection {
    val pts = r?.fullPath()?.filter { it.hasPosition }.orEmpty()
    if (pts.size < 2) return FeatureCollection.fromFeatures(emptyList())
    val feats = pts.zipWithNext().filter { (a, b) ->
        val proc = b.segment in setOf("sid", "star", "approach") || (a.segment == "sid" && b.type == "airport")
        proc == procedures
    }.map { (a, b) -> Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(a.lon!!, a.lat!!), Point.fromLngLat(b.lon!!, b.lat!!)))) }
    return FeatureCollection.fromFeatures(feats)
}

private fun routePoints(r: SavedRoute?): FeatureCollection =
    FeatureCollection.fromFeatures(r?.fullPath()?.filter { it.hasPosition }.orEmpty().map { p ->
        Feature.fromGeometry(Point.fromLngLat(p.lon!!, p.lat!!)).apply {
            // Etykieta z ograniczeniem wysokości, jak na chartach
            addStringProperty("id", p.ident + (p.constraint?.let { " $it" } ?: ""))
        }
    })

/** Mapa pogody: lotniska w kolorach kategorii lotu i obszary SIGMET; dotknięcie lotniska otwiera briefing. */
@Composable
fun MapScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = Fm.c
    val metars by vm.mapMetars.collectAsStateWithLifecycle()
    val sigmets by vm.mapSigmets.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val focus by vm.mapRouteFocus.collectAsStateWithLifecycle()
    val route = routes.firstOrNull { it.id == settings.activeRouteId }
    val live by vm.flightState.collectAsStateWithLifecycle()
    val liveTrack by vm.track.collectAsStateWithLifecycle()
    val logs by vm.flightLogs.collectAsStateWithLifecycle()
    val mapFlightId by vm.mapFlight.collectAsStateWithLifecycle()
    // Zapisany lot wybrany w historii ma pierwszeństwo przed torem bieżącego lotu
    val shownFlight = logs.firstOrNull { it.id == mapFlightId }
    val track = shownFlight?.track ?: liveTrack
    var follow by rememberSaveable { mutableStateOf(true) }
    var handledFocus by rememberSaveable { mutableStateOf(0) }
    var showSigmets by rememberSaveable { mutableStateOf(true) }
    var threeD by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Metar?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var wired by remember { mutableStateOf(false) }
    val openLatest by rememberUpdatedState(onOpen)
    val metarsLatest by rememberUpdatedState(metars)

    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(null) }
    }

    // Cykl życia MapView musi iść za cyklem ekranu
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    val styleUrl = if (c.isNight) "https://tiles.openfreemap.org/styles/dark" else "https://tiles.openfreemap.org/styles/positron"
    LaunchedEffect(styleUrl) {
        mapView.getMapAsync { map ->
            if (map.cameraPosition.zoom < 2.0) {
                val home = vm.mapHome()
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(home?.lat ?: 52.0, home?.lon ?: 19.0)).zoom(if (home != null) 6.5 else 5.0).build()
            }
            // Pochylanie i obracanie dwoma palcami; przycisk 3D ustawia perspektywę jednym dotknięciem
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isTiltGesturesEnabled = true
            map.setStyle(Style.Builder().fromUri(styleUrl)) { st ->
                addOverlay(st, c)
                set3dLayers(st, threeD)
                style = st
            }
            if (wired) return@getMapAsync
            wired = true
            map.addOnCameraIdleListener { requestArea(map, vm) }
            map.addOnMapClickListener { ll ->
                val pt: PointF = map.projection.toScreenLocation(ll)
                val hit = map.queryRenderedFeatures(pt, LYR_METAR).firstOrNull()
                    ?: map.queryRenderedFeatures(android.graphics.RectF(pt.x - 24, pt.y - 24, pt.x + 24, pt.y + 24), LYR_METAR).firstOrNull()
                val id = hit?.getStringProperty("id")
                picked = id?.let { i -> metarsLatest.firstOrNull { it.station == i } }
                id != null
            }
            requestArea(map, vm)
        }
    }

    LaunchedEffect(style, metars, c) {
        (style?.getSource(SRC_METAR) as? GeoJsonSource)?.setGeoJson(metarFeatures(metars, c))
    }
    // Samolot z symulatora i przebyty tor
    LaunchedEffect(style, live?.position, live?.attitude) {
        val st = style ?: return@LaunchedEffect
        val lat = live?.position?.lat; val lon = live?.position?.lon
        val fc = if (lat == null || lon == null) FeatureCollection.fromFeatures(emptyList())
        else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
            addNumberProperty("hdg", live?.attitude?.hdgTrueDeg ?: 0.0)
            live?.position?.altMslFt?.let { addStringProperty("label", altLabel(it.roundToInt())) }
        })
        (st.getSource(SRC_PLANE) as? GeoJsonSource)?.setGeoJson(fc)
        if (follow && lat != null && lon != null) mapView.getMapAsync { m ->
            val b = CameraPosition.Builder().target(LatLng(lat, lon))
            // W 3D kamera patrzy w kierunku lotu, zza samolotu
            if (threeD) (live?.attitude?.trackTrueDeg ?: live?.attitude?.hdgTrueDeg)?.let { b.bearing(it) }
            m.moveCamera(CameraUpdateFactory.newCameraPosition(b.build()))
        }
    }
    // Zapisany lot: widok 3D i kadr na cały tor
    // Kadr i pochylenie w jednej animacji — dwie osobne przerywałyby się nawzajem
    val pendingFit = remember { arrayOfNulls<LatLngBounds>(1) }
    fun moveCamera3d(on: Boolean) = mapView.getMapAsync { m ->
        // Kadr płaski, potem oddalenie — po pochyleniu bliższy koniec toru inaczej ucieka za dolną krawędź
        val fit = pendingFit[0]?.let { m.getCameraForLatLngBounds(it, intArrayOf(80, 240, 80, 160)) }
        pendingFit[0] = null
        val b = CameraPosition.Builder(fit ?: m.cameraPosition).tilt(if (on) 55.0 else 0.0)
        if (fit != null && on) b.zoom(fit.zoom - 0.7)
        if (!on) b.bearing(0.0)
        m.animateCamera(CameraUpdateFactory.newCameraPosition(b.build()), 900)
    }
    // Przed efektem zapisanego lotu: przy jednoczesnym starcie późniejsza animacja (z kadrem) wygrywa
    LaunchedEffect(style, threeD) {
        val st = style ?: return@LaunchedEffect
        set3dLayers(st, threeD)
        moveCamera3d(threeD)
    }
    // Zapisany lot: widok 3D i kadr na cały tor
    LaunchedEffect(style, shownFlight?.id) {
        val f = shownFlight ?: return@LaunchedEffect
        if (style == null || f.track.size < 2) return@LaunchedEffect
        follow = false
        pendingFit[0] = LatLngBounds.Builder().apply { f.track.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        if (threeD) moveCamera3d(true) else threeD = true
    }
    LaunchedEffect(style, track.size, shownFlight?.id) {
        val st = style ?: return@LaunchedEffect
        (st.getSource(SRC_TRACK) as? GeoJsonSource)?.setGeoJson(trackLine(track))
        // Kolor wysokości wzdłuż linii (line-progress = ułamek długości)
        val stops = trackGradient(track)
        if (stops.size >= 2) (st.getLayer(LYR_TRACK) as? LineLayer)?.setProperties(PropertyFactory.lineGradient(
            Expression.interpolate(Expression.linear(), Expression.lineProgress(),
                *stops.map { (f, alt) -> val c = altRgb(alt); Expression.stop(f, Expression.rgb(c[0], c[1], c[2])) }.toTypedArray()),
        ))
        (st.getSource(SRC_CURTAIN) as? GeoJsonSource)?.setGeoJson(trackCurtain(track))
        (st.getSource(SRC_TRACK_LBL) as? GeoJsonSource)?.setGeoJson(trackLabels(track))
    }
    LaunchedEffect(style, route) {
        (style?.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(routeLines(route, procedures = false))
        (style?.getSource(SRC_ROUTE_PROC) as? GeoJsonSource)?.setGeoJson(routeLines(route, procedures = true))
        (style?.getSource(SRC_ROUTE_PTS) as? GeoJsonSource)?.setGeoJson(routePoints(route))
    }
    // „Pokaż na mapie” z sekcji Lot: wykadruj całą trasę
    LaunchedEffect(style, focus, route) {
        val pts = route?.fullPath()?.filter { it.hasPosition }.orEmpty()
        if (style == null || focus == handledFocus || pts.size < 2) return@LaunchedEffect
        handledFocus = focus
        val bounds = LatLngBounds.Builder().apply { pts.forEach { include(LatLng(it.lat!!, it.lon!!)) } }.build()
        mapView.getMapAsync { it.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120), 800) }
    }
    LaunchedEffect(style, sigmets, showSigmets) {
        (style?.getSource(SRC_SIGMET) as? GeoJsonSource)?.setGeoJson(sigmetFeatures(if (showSigmets) sigmets else emptyList()))
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ mapView }, Modifier.fillMaxSize())

        // Przełączniki warstw u góry (przewijane, by nie łamały się na wąskim ekranie), legenda pod nimi
        Column(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapChip("3D", threeD, Icons.Outlined.ViewInAr) { threeD = !threeD }
                MapChip("SIGMET", showSigmets, Icons.Outlined.WarningAmber) { showSigmets = !showSigmets }
                if (live != null) MapChip(loc("Śledź samolot", "Follow aircraft"), follow, Icons.Outlined.MyLocation) { follow = !follow }
                if (shownFlight != null) MapChip("${shownFlight.from ?: "?"} → ${shownFlight.to ?: "?"}", true, Icons.Outlined.Close) { vm.showFlightOnMap(null) }
                if (route != null) MapChip(route.origin + " → " + route.destination, false, Icons.Outlined.Route) { follow = false; vm.focusRouteOnMap() }
            }
            Legend()
        }

        val s = live
        if (picked == null && s != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(50)).background(c.card.copy(alpha = 0.94f)).border(1.dp, c.rule, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LiveValue("ALT", s.position.altIndFt?.let { "%d".format(it.roundToInt()) } ?: "—")
                LiveValue("GS", s.speed.gsKt?.let { "${it.roundToInt()}" } ?: "—")
                LiveValue("TRK", s.attitude.trackTrueDeg?.let { "%03d°".format(((it.roundToInt() % 360) + 360) % 360) } ?: "—")
                LiveValue("V/S", s.speed.vsFpm?.let { "%+d".format(it.roundToInt()) } ?: "—")
            }
        }

        picked?.let { m ->
            Column(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(c.card.copy(alpha = 0.96f))
                    .clickable { openLatest(m.station) }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.station, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink)
                    Spacer(Modifier.width(10.dp))
                    CategoryTag(m.flightCategory)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.Close, loc("Zamknij", "Close"), tint = c.ink2, modifier = Modifier.size(22.dp).clickable { picked = null })
                }
                Text(m.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.ink2)
                Text(m.raw, fontFamily = Mono, fontSize = 12.sp, color = c.accent, modifier = Modifier.padding(top = 6.dp))
                Text(loc("Dotknij, aby otworzyć briefing", "Tap to open the briefing"), style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun requestArea(map: MapLibreMap, vm: MainViewModel) {
    if (map.cameraPosition.zoom < 4.0) return
    val b = map.projection.visibleRegion.latLngBounds
    vm.loadMapArea(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast)
}

private fun addOverlay(st: Style, c: FmColors) {
    st.addSource(GeoJsonSource(SRC_SIGMET))
    st.addLayer(FillLayer("oab-sigmet-fill", SRC_SIGMET).withProperties(
        PropertyFactory.fillColor(hex(c.caution)), PropertyFactory.fillOpacity(0.16f),
    ))
    st.addLayer(LineLayer("oab-sigmet-line", SRC_SIGMET).withProperties(
        PropertyFactory.lineColor(hex(c.caution)), PropertyFactory.lineWidth(1.6f), PropertyFactory.lineDasharray(arrayOf(3f, 2f)),
    ))
    // Trasa w magencie — kolor aktywnej nawigacji w awionice
    st.addSource(GeoJsonSource(SRC_ROUTE))
    st.addLayer(LineLayer("oab-route-line", SRC_ROUTE).withProperties(
        PropertyFactory.lineColor(hex(c.magenta)), PropertyFactory.lineWidth(3.5f),
    ))
    st.addSource(GeoJsonSource(SRC_ROUTE_PROC))
    st.addLayer(LineLayer("oab-route-proc-line", SRC_ROUTE_PROC).withProperties(
        PropertyFactory.lineColor(hex(c.magenta)), PropertyFactory.lineWidth(2.5f), PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
    ))
    st.addSource(GeoJsonSource(SRC_ROUTE_PTS))
    st.addLayer(CircleLayer("oab-route-pts", SRC_ROUTE_PTS).withProperties(
        PropertyFactory.circleRadius(3.5f), PropertyFactory.circleColor(hex(c.magenta)),
    ))
    st.addLayer(SymbolLayer("oab-route-label", SRC_ROUTE_PTS).withProperties(
        PropertyFactory.textField(Expression.get("id")),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(10f),
        PropertyFactory.textOffset(arrayOf(0f, -1.2f)),
        PropertyFactory.textColor(hex(c.magenta)),
        PropertyFactory.textHaloColor(hex(c.paper)),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textOptional(true),
    ).apply { minZoom = 5.5f })
    // Budynki 3D z kafli OpenMapTiles (OpenFreeMap) — tylko w widoku 3D i przy dużym przybliżeniu
    if (st.getSource("openmaptiles") != null) st.addLayer(FillExtrusionLayer(LYR_BUILDINGS, "openmaptiles").withSourceLayer("building").withProperties(
        PropertyFactory.fillExtrusionColor(if (c.isNight) "#2A2A2A" else "#D5DBE1"),
        PropertyFactory.fillExtrusionHeight(Expression.coalesce(Expression.get("render_height"), Expression.literal(8))),
        PropertyFactory.fillExtrusionBase(Expression.coalesce(Expression.get("render_min_height"), Expression.literal(0))),
        PropertyFactory.fillExtrusionOpacity(0.85f),
    ).apply { minZoom = 14f })
    // Tor lotu w kolorze wysokości, pod nim (w 3D) kurtyna do ziemi; samolot nad wszystkim
    // Bez upraszczania: wąskie wstęgi znikałyby przy małym przybliżeniu
    st.addSource(GeoJsonSource(SRC_CURTAIN, GeoJsonOptions().withTolerance(0f)))
    st.addLayer(FillExtrusionLayer(LYR_CURTAIN, SRC_CURTAIN).withProperties(
        PropertyFactory.fillExtrusionColor(altColor()),
        PropertyFactory.fillExtrusionHeight(Expression.get("h")),
        PropertyFactory.fillExtrusionBase(0f),
        PropertyFactory.fillExtrusionOpacity(0.55f),
    ))
    st.addSource(GeoJsonSource(SRC_TRACK, GeoJsonOptions().withLineMetrics(true)))
    st.addLayer(LineLayer(LYR_TRACK, SRC_TRACK).withProperties(
        PropertyFactory.lineColor(hex(c.ink)), PropertyFactory.lineWidth(3.5f),
        PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
    ))
    st.addSource(GeoJsonSource(SRC_TRACK_LBL))
    st.addLayer(SymbolLayer("oab-track-label", SRC_TRACK_LBL).withProperties(
        PropertyFactory.textField(Expression.get("label")),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(11f),
        PropertyFactory.textOffset(arrayOf(0f, -1.1f)),
        PropertyFactory.textColor(altColor()),
        PropertyFactory.textHaloColor(hex(c.paper)),
        PropertyFactory.textHaloWidth(1.5f),
        // Wysokości toru są ważniejsze niż nazwy miejscowości podkładu
        PropertyFactory.textAllowOverlap(true),
        PropertyFactory.textIgnorePlacement(true),
    ))
    st.addImage(IMG_PLANE, planeBitmap())
    st.addSource(GeoJsonSource(SRC_PLANE))
    st.addSource(GeoJsonSource(SRC_METAR))
    // Okrąg w kolorze kategorii, jak symbol lotniska na wyświetlaczu nawigacyjnym
    st.addLayer(CircleLayer(LYR_METAR, SRC_METAR).withProperties(
        PropertyFactory.circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(),
            Expression.stop(4, 3f), Expression.stop(8, 6f), Expression.stop(11, 9f))),
        PropertyFactory.circleColor(hex(c.paper)),
        PropertyFactory.circleStrokeColor(Expression.get("color")),
        PropertyFactory.circleStrokeWidth(2.5f),
    ))
    st.addLayer(SymbolLayer("oab-metar-label", SRC_METAR).withProperties(
        PropertyFactory.textField(Expression.get("id")),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(11f),
        PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
        PropertyFactory.textColor(hex(c.ink)),
        PropertyFactory.textHaloColor(hex(c.paper)),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textOptional(true),
    ).apply { minZoom = 6.5f })
    st.addLayer(SymbolLayer("oab-plane-layer", SRC_PLANE).withProperties(
        PropertyFactory.iconImage(IMG_PLANE),
        PropertyFactory.iconRotate(Expression.get("hdg")),
        PropertyFactory.iconRotationAlignment("map"),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconSize(0.7f),
        // Bieżąca wysokość pod symbolem samolotu
        PropertyFactory.textField(Expression.get("label")),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(11f),
        PropertyFactory.textOffset(arrayOf(0f, 2.0f)),
        PropertyFactory.textColor(hex(c.ink)),
        PropertyFactory.textHaloColor(hex(c.paper)),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textAllowOverlap(true),
    ))
}

/** Kurtyna toru i budynki są widoczne tylko w widoku 3D. */
private fun set3dLayers(st: Style, on: Boolean) {
    val v = PropertyFactory.visibility(if (on) "visible" else "none")
    st.getLayer(LYR_CURTAIN)?.setProperties(v)
    st.getLayer(LYR_BUILDINGS)?.setProperties(v)
}

@Composable
private fun MapChip(text: String, on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Fm.c
    // Włączony: pełny blok w kolorze tekstu; wyłączony: półprzezroczysta pigułka z obwódką
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) c.ink else c.card.copy(alpha = 0.94f))
            .border(1.dp, if (on) c.ink else c.rule, RoundedCornerShape(50))
            .clickable(role = Role.Switch, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) c.paper else c.ink2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) c.paper else c.ink, maxLines = 1)
    }
}

@Composable
private fun LiveValue(label: String, value: String) {
    val c = Fm.c
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.ink2)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
    }
}

@Composable
private fun Legend() {
    val c = Fm.c
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.card.copy(alpha = 0.94f)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("VFR", "MVFR", "IFR", "LIFR").forEach { k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(c.category(k)))
                Spacer(Modifier.width(3.dp))
                Text(k, fontFamily = Mono, fontSize = 10.sp, color = c.ink2)
            }
        }
    }
}
