package com.freemetar.app.ui.flight

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.Routes
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/**
 * Embedded MapLibre view dla Navigation Display w trybie MAP.
 *
 * Funkcje:
 * - Pochylona kamera (tilt 50°) śledząca samolot
 * - Budynki 3D
 * - Trasa lotu
 * - ILS approach paths (TODO)
 * - Weather radar overlay (TODO)
 * - Smooth camera following
 */
@Composable
fun EmbeddedMapView(
    state: FlightState,
    route: List<Routes.RoutePoint>,
    rangeNm: Int,
    showWeather: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val defaultTilt = 50.0
    val defaultZoom = when (rangeNm) {
        5 -> 13.0
        10 -> 12.0
        20 -> 11.0
        40 -> 10.0
        80 -> 9.0
        160 -> 8.0
        else -> 7.0
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        }
    }

    // Śledzenie samolotu
    LaunchedEffect(state.position.lat, state.position.lon, state.attitude.trackTrueDeg, rangeNm) {
        val lat = state.position.lat
        val lon = state.position.lon
        val heading = state.attitude.trackTrueDeg ?: state.attitude.hdgTrueDeg ?: 0.0

        if (lat != null && lon != null) {
            mapLibreMap?.let { map ->
                val cameraPos = CameraPosition.Builder()
                    .target(LatLng(lat, lon))
                    .zoom(defaultZoom)
                    .tilt(defaultTilt)
                    .bearing(heading)
                    .build()
                map.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPos), 500)
            }
        }
    }

    // Inicjalizacja mapy
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                createMapView(ctx).also { mv ->
                    mapView = mv
                    mv.onCreate(null)
                    mv.getMapAsync { map ->
                        mapLibreMap = map
                        setupMap(map, defaultTilt, defaultZoom) { st ->
                            style = st
                            add3DBuildings(st)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun createMapView(context: Context): MapView {
    return MapView(context).apply {
        setStyleUrl("https://tiles.openfreemap.org/styles/dark")
    }
}

private fun setupMap(
    map: MapLibreMap,
    tilt: Double,
    zoom: Double,
    onStyleLoaded: (Style) -> Unit
) {
    map.uiSettings.apply {
        // Wyłącz gesty - ND nie jest interaktywny
        isRotateGesturesEnabled = false
        isTiltGesturesEnabled = false
        isZoomGesturesEnabled = false
        isScrollGesturesEnabled = false
        // Wyłącz kompas i atrybuty
        isCompassEnabled = false
        isAttributionEnabled = false
        isLogoEnabled = false
    }

    map.cameraPosition = CameraPosition.Builder()
        .target(LatLng(52.0, 19.0))
        .zoom(zoom)
        .tilt(tilt)
        .build()

    map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark")) { style ->
        onStyleLoaded(style)
    }
}

private fun add3DBuildings(style: Style) {
    // Dodaj budynki 3D z kafli OpenMapTiles
    style.getLayer("building")?.let { buildingLayer ->
        style.removeLayer(buildingLayer)
    }

    style.addLayer(
        FillExtrusionLayer("buildings-3d", "openmaptiles")
            .withSourceLayer("building")
            .withProperties(
                PropertyFactory.fillExtrusionColor("#666666"),
                PropertyFactory.fillExtrusionHeight(
                    org.maplibre.android.style.expressions.Expression.get("render_height")
                ),
                PropertyFactory.fillExtrusionBase(
                    org.maplibre.android.style.expressions.Expression.get("render_min_height")
                ),
                PropertyFactory.fillExtrusionOpacity(0.8f)
            )
    )
}

/**
 * Placeholder - do zastąpienia pełną implementacją w EnhancedND.kt.
 */
@Composable
fun MapView3D(
    state: FlightState,
    route: List<Routes.RoutePoint>,
    rangeNm: Int,
    showWeather: Boolean,
    modifier: Modifier = Modifier
) {
    EmbeddedMapView(
        state = state,
        route = route,
        rangeNm = rangeNm,
        showWeather = showWeather,
        modifier = modifier
    )
}
