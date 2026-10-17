package com.freemetar.app.ui.screens

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
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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
private const val SRC_PLANE = "oab-plane"
private const val SRC_TRACK = "oab-track"
private const val IMG_PLANE = "oab-plane-icon"

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

private fun routeLine(r: SavedRoute?): FeatureCollection {
    val pts = r?.points?.filter { it.hasPosition }.orEmpty()
    if (pts.size < 2) return FeatureCollection.fromFeatures(emptyList())
    return FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon!!, it.lat!!) })))
}

private fun routePoints(r: SavedRoute?): FeatureCollection =
    FeatureCollection.fromFeatures(r?.points?.filter { it.hasPosition }.orEmpty().map { p ->
        Feature.fromGeometry(Point.fromLngLat(p.lon!!, p.lat!!)).apply { addStringProperty("id", p.ident) }
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
    val track by vm.track.collectAsStateWithLifecycle()
    var follow by rememberSaveable { mutableStateOf(true) }
    var handledFocus by rememberSaveable { mutableStateOf(0) }
    var showSigmets by rememberSaveable { mutableStateOf(true) }
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
            map.uiSettings.isRotateGesturesEnabled = false
            map.setStyle(Style.Builder().fromUri(styleUrl)) { st ->
                addOverlay(st, c)
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
        })
        (st.getSource(SRC_PLANE) as? GeoJsonSource)?.setGeoJson(fc)
        if (follow && lat != null && lon != null) mapView.getMapAsync { it.moveCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon))) }
    }
    LaunchedEffect(style, track.size) {
        val pts = track.map { Point.fromLngLat(it.lon, it.lat) }
        (style?.getSource(SRC_TRACK) as? GeoJsonSource)?.setGeoJson(
            if (pts.size < 2) FeatureCollection.fromFeatures(emptyList()) else FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts)))
        )
    }
    LaunchedEffect(style, route) {
        (style?.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(routeLine(route))
        (style?.getSource(SRC_ROUTE_PTS) as? GeoJsonSource)?.setGeoJson(routePoints(route))
    }
    // „Pokaż na mapie” z sekcji Lot: wykadruj całą trasę
    LaunchedEffect(style, focus, route) {
        val pts = route?.points?.filter { it.hasPosition }.orEmpty()
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

        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapChip("SIGMET", showSigmets, Icons.Outlined.WarningAmber) { showSigmets = !showSigmets }
            if (live != null) MapChip("Śledź samolot", follow, Icons.Outlined.MyLocation) { follow = !follow }
            Spacer(Modifier.weight(1f))
            Legend()
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
                    Icon(Icons.Outlined.Close, "Zamknij", tint = c.ink2, modifier = Modifier.size(22.dp).clickable { picked = null })
                }
                Text(m.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.ink2)
                Text(m.raw, fontFamily = Mono, fontSize = 12.sp, color = c.accent, modifier = Modifier.padding(top = 6.dp))
                Text("Dotknij, aby otworzyć briefing", style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
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
    // Tor lotu i samolot nad wszystkim
    st.addSource(GeoJsonSource(SRC_TRACK))
    st.addLayer(LineLayer("oab-track-line", SRC_TRACK).withProperties(
        PropertyFactory.lineColor(hex(c.ink)), PropertyFactory.lineWidth(2f), PropertyFactory.lineOpacity(0.7f),
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
    ))
}

@Composable
private fun MapChip(text: String, on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Fm.c
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(c.card.copy(alpha = 0.94f))
            .clickable(role = Role.Switch, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) c.caution else c.ink2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) c.ink else c.ink2)
    }
}

@Composable
private fun Legend() {
    val c = Fm.c
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(c.card.copy(alpha = 0.94f)).padding(horizontal = 10.dp, vertical = 8.dp),
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
