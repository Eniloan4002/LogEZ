package com.enil.logez.feature.activity.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.net.ConnectivityReceiver
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Metro Manila's approximate centroid — the default camera position when no route is passed. */
private val METRO_MANILA_CENTER = LatLng(14.5995, 120.9842)

/**
 * The zoom the map actually settles at. It's reached via a `FINAL_ZOOM - 1.0` -> `FINAL_ZOOM` step
 * (see the setStyle callback below) -- that exact two-step sequence is what's confirmed working
 * on-device; landing on this value directly on the first frame is not equivalent, nor is starting
 * one level *above* this and stepping down to it (untested, not assumed equivalent by symmetry).
 */
private const val FINAL_ZOOM = 12.0

/** M21c: street-level zoom used while actively following a live GPS fix. */
private const val FOLLOW_ZOOM = 16.0

/** Screen-pixel padding around a fitted route's bounding box -- untuned, revisit on-device if a route ever renders edge-to-edge. */
private const val ROUTE_BOUNDS_PADDING_PX = 96

private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"

private fun routeFeature(points: List<Pair<Double, Double>>): Feature =
    Feature.fromGeometry(LineString.fromLngLats(points.map { (lat, lng) -> Point.fromLngLat(lng, lat) }))

private fun routeBounds(points: List<Pair<Double, Double>>): LatLngBounds {
    val builder = LatLngBounds.Builder()
    points.forEach { (lat, lng) -> builder.include(LatLng(lat, lng)) }
    return builder.build()
}

/**
 * A route with (near-)zero real movement -- a workout where GPS never produced a usable fix (the
 * `adb emu geo fix` AVD limitation makes this the common on-device-testing case, not just a
 * theoretical edge case) -- has a degenerate bounding box. Confirmed on-device: fitting the camera
 * to that near-zero box via `newLatLngBounds` zooms in far past this app's z10-14 tileset's actual
 * data, rendering a flat, blank-looking tile with no visible roads/labels. Below this span, fall
 * back to a fixed reasonable zoom centered on the route instead of bounds-fitting.
 */
private const val MIN_BOUNDS_SPAN_DEGREES = 0.002

private fun isDegenerateRoute(points: List<Pair<Double, Double>>): Boolean {
    val lats = points.map { it.first }
    val lngs = points.map { it.second }
    val latSpan = (lats.max() - lats.min())
    val lngSpan = (lngs.max() - lngs.min())
    return latSpan < MIN_BOUNDS_SPAN_DEGREES && lngSpan < MIN_BOUNDS_SPAN_DEGREES
}

private fun routeCentroid(points: List<Pair<Double, Double>>): LatLng =
    LatLng(points.sumOf { it.first } / points.size, points.sumOf { it.second } / points.size)

/**
 * M21b. A classic `MapView` wrapped in `AndroidView`, not `maplibre-compose` (that wrapper needs
 * Kotlin 2.4.10, ahead of this project's 2.3.0 pin — decisions.md/gradle/libs.versions.toml).
 * Renders entirely from the bundled Metro Manila `.mbtiles` + local style/glyphs/sprites — no
 * network call at any point (verify: build with `INTERNET` stripped, confirm this still renders).
 *
 * The persistent, always-visible attribution text is deliberate, not decorative: MapLibre's own
 * default tap-to-reveal "(i)" control does not, on its own, satisfy OpenStreetMap's requirement
 * that attribution be visible without requiring a tap (M21b research, a real open MapLibre issue).
 *
 * M21c: [routePoints] draws the GPS track as a line layer once 2+ points exist. [followLatest]
 * chooses how the camera reacts to it -- `true` (live tracking) keeps centering on the newest
 * point at street level as fixes arrive; `false` (a finished workout's recap) fits the camera to
 * the whole route's bounding box once. The route source/layer are always created empty during the
 * initial style load and populated afterward by a separate effect (never during the load callback
 * itself) so the already-proven-working first-paint zoom sequence below is never touched by
 * route-specific logic -- see that sequence's own comment for why it's this fragile.
 *
 * M21: while [followLatest] is true, a naive "re-center on every new fix" fights the user the
 * instant they try to pinch-zoom or drag -- the very next GPS fix (every few seconds) snaps the
 * camera straight back, which reads as "the map won't let me zoom or move it at all" (Owner
 * report, 2026-09-11), not as a momentary jump. [MapLibreMap.addOnCameraMoveStartedListener] can
 * tell a real touch gesture (`REASON_API_GESTURE`) apart from this composable's own `easeCamera`
 * calls (`REASON_API_ANIMATION`); [userPanned] latches true on the former and suppresses
 * auto-follow until the user taps the "recenter" button, exactly the pattern every real GPS-track
 * app (Strava, Nike Run Club, Google Maps' own blue-dot follow mode) uses for this same conflict.
 */
@Composable
fun OfflineMapView(
    modifier: Modifier = Modifier,
    routePoints: List<Pair<Double, Double>> = emptyList(),
    followLatest: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var styleJson by remember { mutableStateOf<String?>(null) }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var userPanned by remember { mutableStateOf(false) }
    val routeLineColor = MaterialTheme.colorScheme.primary.toArgb()

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
        // Without ACCESS_NETWORK_STATE (never granted -- no network permissions, ever), MapLibre's
        // own ConnectivityReceiver crashes the first time Android delivers a CONNECTIVITY_CHANGE
        // broadcast: its onReceive() calls ConnectivityManager.getActiveNetworkInfo(), which throws
        // a SecurityException without that permission. setConnected() pre-seeds its internal
        // connected state, which onReceive() checks first and returns early on -- the framework
        // call is never reached. This app is never connected anyway, so `false` is also just true.
        ConnectivityReceiver.instance(context).setConnected(false)
        val mbtiles = OfflineMapAssets.ensureMbtilesInstalled(context)
        styleJson = OfflineMapAssets.loadStyleJson(context, mbtiles.absolutePath)
    }

    Box(modifier = modifier) {
        val json = styleJson
        if (json == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        val mapView = remember { MapView(context) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onDestroy()
            }
        }

        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { mv ->
            mv.getMapAsync { map ->
                if (maplibreMap != null) return@getMapAsync
                maplibreMap = map
                // MapLibre's own default logo mark and tap-to-reveal attribution "(i)" icon are pure
                // SDK branding/decoration, not an OSM compliance requirement -- our own always-visible
                // attribution Text below already satisfies that on its own. Disabling these removes
                // the actual visual "watermark" without touching the legally-required credit line.
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false
                // Explicit, not relying on the SDK's own defaults -- this is the exact set of
                // gestures the "can't zoom or move the map" report (2026-09-11) was about.
                map.uiSettings.isZoomGesturesEnabled = true
                map.uiSettings.isScrollGesturesEnabled = true
                map.uiSettings.isRotateGesturesEnabled = true
                map.uiSettings.isTiltGesturesEnabled = true
                map.uiSettings.isDoubleTapGesturesEnabled = true
                map.uiSettings.isQuickZoomGesturesEnabled = true
                // A real pinch/drag (not this composable's own easeCamera calls) latches
                // userPanned -- see the class doc comment for why this exists.
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        userPanned = true
                    }
                }
                map.setStyle(Style.Builder().fromJson(json)) { style ->
                    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
                    style.addLayer(
                        LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                            PropertyFactory.lineColor(routeLineColor),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )

                    // A camera position set here, on the very first frame, leaves every line/symbol
                    // layer unrendered (roads, place labels) -- only fill/background layers show --
                    // confirmed on-device against this exact mbtiles+style (MapLibre 13.6.1). Only a
                    // genuine zoom-level change, after the first one has had time to actually finish
                    // loading, fixes it -- moveCamera/easeCamera/triggerRepaint immediately after the
                    // first camera position, or a same-frame second camera change, all reproduce the
                    // same broken (roads-missing) render just as plainly as doing nothing. An
                    // OnDidFinishLoadingMapListener-driven version was tried and tested worse (didn't
                    // even reproduce the water fill reliably -- it's a one-shot lifecycle event, not
                    // guaranteed to still be pending by the time a listener attaches to it here), so
                    // this uses an explicit delay instead: land one zoom level out first, then step
                    // to FINAL_ZOOM once that first load has clearly had time to settle. Deliberately
                    // always targets the fixed Metro Manila center here, never the route -- the route
                    // line is still empty at this point (added just above with no data yet), and this
                    // exact sequence is the one confirmed to unblock rendering, so nothing route-aware
                    // touches it. The route's own camera framing happens afterward, once styleReady
                    // flips true below, in the separate effect that reacts to routePoints.
                    map.cameraPosition = CameraPosition.Builder()
                        .target(METRO_MANILA_CENTER)
                        .zoom(FINAL_ZOOM - 1.0)
                        .build()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        map.cameraPosition = CameraPosition.Builder()
                            .target(METRO_MANILA_CENTER)
                            .zoom(FINAL_ZOOM)
                            .build()
                        styleReady = true
                    }, 1500)
                }
            }
        }

        Text(
            stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )

        // Only ever meaningful while actively following -- a static recap/history map has no
        // "latest fix" to snap back to, and isn't fought by a repeating auto-follow call in the
        // first place (its own bounds-fit only runs once, when routePoints first arrives).
        if (followLatest && userPanned) {
            Surface(
                onClick = {
                    userPanned = false
                    val (lat, lng) = routePoints.lastOrNull() ?: return@Surface
                    maplibreMap?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), FOLLOW_ZOOM))
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm).size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.map_recenter),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    // Owns every route-specific update: the initial population once styleReady flips true, and
    // every subsequent growth during live tracking. Never touches the style-load callback above.
    LaunchedEffect(routePoints, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val map = maplibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return@LaunchedEffect

        if (routePoints.size >= 2) source.setGeoJson(routeFeature(routePoints))

        // userPanned: don't fight a gesture the user is mid-way through -- see the class doc
        // comment. The recenter button (above) is the only way back to auto-follow from here.
        if (userPanned) return@LaunchedEffect

        if (followLatest) {
            if (routePoints.isNotEmpty()) {
                val (lat, lng) = routePoints.last()
                map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), FOLLOW_ZOOM))
            }
        } else if (routePoints.size >= 2) {
            if (isDegenerateRoute(routePoints)) {
                map.easeCamera(CameraUpdateFactory.newLatLngZoom(routeCentroid(routePoints), FOLLOW_ZOOM))
            } else {
                map.easeCamera(CameraUpdateFactory.newLatLngBounds(routeBounds(routePoints), ROUTE_BOUNDS_PADDING_PX))
            }
        }
    }
}
