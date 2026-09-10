package com.enil.logez.feature.activity.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.enil.logez.R
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.net.ConnectivityReceiver

/** Metro Manila's approximate centroid — the default camera position until M21c adds a real GPS route to frame. */
private val METRO_MANILA_CENTER = LatLng(14.5995, 120.9842)

/**
 * The zoom the map actually settles at. It's reached via a `FINAL_ZOOM - 1.0` -> `FINAL_ZOOM` step
 * (see the setStyle callback below) -- that exact two-step sequence is what's confirmed working
 * on-device; landing on this value directly on the first frame is not equivalent, nor is starting
 * one level *above* this and stepping down to it (untested, not assumed equivalent by symmetry).
 */
private const val FINAL_ZOOM = 12.0

/**
 * M21b. A classic `MapView` wrapped in `AndroidView`, not `maplibre-compose` (that wrapper needs
 * Kotlin 2.4.10, ahead of this project's 2.3.0 pin — decisions.md/gradle/libs.versions.toml).
 * Renders entirely from the bundled Metro Manila `.mbtiles` + local style/glyphs/sprites — no
 * network call at any point (verify: build with `INTERNET` stripped, confirm this still renders).
 *
 * The persistent, always-visible attribution text is deliberate, not decorative: MapLibre's own
 * default tap-to-reveal "(i)" control does not, on its own, satisfy OpenStreetMap's requirement
 * that attribution be visible without requiring a tap (M21b research, a real open MapLibre issue).
 */
@Composable
fun OfflineMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var styleJson by remember { mutableStateOf<String?>(null) }

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
                map.setStyle(Style.Builder().fromJson(json)) {
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
                    // to FINAL_ZOOM once that first load has clearly had time to settle.
                    map.cameraPosition = CameraPosition.Builder()
                        .target(METRO_MANILA_CENTER)
                        .zoom(FINAL_ZOOM - 1.0)
                        .build()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        map.cameraPosition = CameraPosition.Builder()
                            .target(METRO_MANILA_CENTER)
                            .zoom(FINAL_ZOOM)
                            .build()
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
    }
}
