package com.enil.logez.feature.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enil.logez.R

/**
 * A map-free sketch of a GPS route -- just its shape, auto-scaled to fill the available space.
 * Deliberately not a real basemap: for a live-tracking or workout-recap context, the route's
 * shape and current/end position are what matter, not street context -- and unlike the offline
 * MapLibre map already shipped for the History detail screen (which this doesn't replace), it
 * costs no bundled tile data and no native rendering engine, so it can live on the live-tracking
 * and post-Finish screens too without repeating that size cost there.
 *
 * The projection itself (scaling, centering, longitude correction) is [RouteSketchGeometry], kept
 * separately unit-testable -- this composable is just a thin drawer on top of it.
 */
@Composable
fun RouteSketch(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (points.isEmpty()) {
            Text(
                stringResource(R.string.route_sketch_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        val lineColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val projected = RouteSketchGeometry.project(points, size.width, size.height)
            if (projected.size == 1) {
                drawCircle(color = lineColor, radius = 6.dp.toPx(), center = Offset(projected[0].x, projected[0].y))
                return@Canvas
            }

            val path = Path().apply {
                moveTo(projected[0].x, projected[0].y)
                for (i in 1 until projected.size) lineTo(projected[i].x, projected[i].y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            // The endpoint is "where you are" live, or "where you finished" in a recap -- worth
            // calling out distinctly from the rest of the line either way.
            val end = projected.last()
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(end.x, end.y))
        }
    }
}
