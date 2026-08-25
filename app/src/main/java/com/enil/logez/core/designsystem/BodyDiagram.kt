package com.enil.logez.core.designsystem

import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.enil.logez.core.domain.model.MuscleGroup

/**
 * Real anatomical front + back body diagram, drawn from [MUSCLE_BODY_REGIONS] (traced SVG data
 * adapted from vulovix/body-muscles, Apache-2.0 — see that file's KDoc). Each [MuscleGroup]'s
 * regions (there can be several per side, e.g. `chest-upper-left`/`chest-lower-left`/…) are
 * merged into one [Path] per (side, group) so intensity fill, selection outline, and tap
 * hit-testing all treat a muscle group as a single shape, matching how [intensity] and
 * [selected] are keyed. Regions with no [MuscleGroup] (hands, feet, head, spine, hip flexors,
 * shin — outside the app's 20-value enum) render as unlabeled base-silhouette context only.
 */
object BodyDiagramRegions {
    /** Groups the diagram can render — every [MuscleGroup] except the three non-anatomical ones. */
    val MAPPABLE: Set<MuscleGroup> = MuscleGroup.entries.toSet() - setOf(MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.OTHER)
}

/** The source data's own viewBox convention (see MuscleBodyData.kt) — one figure is 35×93 units. */
private const val NATIVE_WIDTH = 35f
private const val NATIVE_HEIGHT = 93f

/** BACK paths are traced at x∈[37,72] in the source's combined-canvas viewBox; shift to x∈[0,35]. */
private const val BACK_X_SHIFT = -37f

/**
 * Region.setPath rasterizes at integer-coordinate resolution — native units are only 35×93, so
 * hit-testing directly in native space would be blocky at region boundaries. Scaling the parsed
 * paths up before building the Region (and scaling tap coordinates by the same factor) trades
 * that for sub-unit precision; independent of the render transform, which uses its own scale.
 */
private const val HIT_TEST_SCALE = 24f

/**
 * Grid search radius, in hit-test-scaled units (≈0.4 native units), for bridging inter-sub-region
 * seams — e.g. a tap landing exactly on the drawn edge between chest-upper-left and
 * chest-lower-left. Deliberately modest: some points inside a MuscleGroup's bounding box are
 * correctly unfilled (the sternum notch between the two pecs is real anatomy, not a seam), and a
 * wider search would start bridging genuine gaps between DIFFERENT groups at their true boundary.
 */
private const val SEAM_TOLERANCE = 10

private data class ParsedSide(
    val silhouette: Path,
    val muscles: Map<MuscleGroup, Path>,
    val hitRegions: Map<MuscleGroup, Region>,
)

private fun parseSide(side: BodySide): ParsedSide {
    val regions = MUSCLE_BODY_REGIONS.filter { it.side == side }
    val shiftX = if (side == BodySide.BACK) BACK_X_SHIFT else 0f
    val shift = Matrix().apply { translate(shiftX, 0f) }

    val silhouette = Path()
    val muscles = mutableMapOf<MuscleGroup, Path>()
    for (region in regions) {
        val path = PathParser().parsePathString(region.pathData).toPath().apply { transform(shift) }
        silhouette.addPath(path)
        if (region.group != null) {
            muscles.getOrPut(region.group) { Path() }.addPath(path)
        }
    }

    val hitScale = Matrix().apply { scale(HIT_TEST_SCALE, HIT_TEST_SCALE) }
    val hitRegions = muscles.mapValues { (_, path) ->
        val scaledAndroidPath = Path().apply { addPath(path); transform(hitScale) }.asAndroidPath()
        val rectF = android.graphics.RectF()
        scaledAndroidPath.computeBounds(rectF, true)
        val bounds = android.graphics.Rect()
        rectF.roundOut(bounds)
        Region().apply { setPath(scaledAndroidPath, Region(bounds)) }
    }
    return ParsedSide(silhouette, muscles, hitRegions)
}

private data class FigureTransform(val originX: Float, val originY: Float, val scale: Float)

/** Uniform "contain" fit of the NATIVE_WIDTH×NATIVE_HEIGHT figure into one side's canvas slot, centered. */
private fun fitTransform(slotOriginX: Float, slotWidth: Float, canvasHeight: Float): FigureTransform {
    val scale = minOf(slotWidth / NATIVE_WIDTH, canvasHeight / NATIVE_HEIGHT)
    val drawnWidth = NATIVE_WIDTH * scale
    val drawnHeight = NATIVE_HEIGHT * scale
    return FigureTransform(
        originX = slotOriginX + (slotWidth - drawnWidth) / 2f,
        originY = (canvasHeight - drawnHeight) / 2f,
        scale = scale,
    )
}

@Composable
fun BodyDiagram(
    intensity: Map<MuscleGroup, Float>,
    modifier: Modifier = Modifier,
    selected: Set<MuscleGroup>? = null,
    onRegionTap: ((MuscleGroup) -> Unit)? = null,
) {
    // `surfaceVariant` (the old base tone) sits almost on top of a Card's own background in this
    // dark theme — an unworked region at 45% alpha over a near-identical bg was barely legible.
    // `outline` is a genuinely lighter neutral, and every region also gets a thin `onSurfaceVariant`
    // stroke so the anatomy reads as line art (like a real muscle chart) rather than relying on
    // fill-vs-background contrast alone.
    val restColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.tertiary

    val front = remember { parseSide(BodySide.FRONT) }
    val back = remember { parseSide(BodySide.BACK) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f) // ~ (35*2) : 93, the source figures' true combined proportions
            .let { m ->
                if (onRegionTap == null) m
                else m.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val half = size.width / 2f
                        val (side, parsed, slotOriginX) = if (tap.x < half) {
                            Triple(BodySide.FRONT, front, 0f)
                        } else {
                            Triple(BodySide.BACK, back, half)
                        }
                        val transform = fitTransform(slotOriginX, half, size.height.toFloat())
                        val nativeX = (tap.x - transform.originX) / transform.scale
                        val nativeY = (tap.y - transform.originY) / transform.scale
                        val hitX = (nativeX * HIT_TEST_SCALE).toInt()
                        val hitY = (nativeY * HIT_TEST_SCALE).toInt()
                        fun bestMatchAt(dx: Int, dy: Int) = parsed.hitRegions
                            .filter { (_, region) -> region.contains(hitX + dx, hitY + dy) }
                            .minByOrNull { (_, region) -> region.bounds.let { it.width().toLong() * it.height() } }

                        // The exact tap point is authoritative whenever it matches anything — a
                        // genuinely unambiguous tap must never be overridden by a neighboring
                        // group's region just because a probe offset happens to land there first.
                        // Sub-regions of the same MuscleGroup (e.g. chest-upper/chest-lower) are
                        // independently traced, not mathematically tessellated, so a tap landing
                        // exactly ON the seam between two of them can miss both by a pixel or two
                        // in hit-test space — the grid search (diagonals included, since seams can
                        // run diagonally) exists only to bridge that exact-miss case, not to
                        // second-guess an exact hit.
                        val exact = bestMatchAt(0, 0)
                        val hit = exact ?: (-1..1).asSequence()
                            .flatMap { gx -> (-1..1).asSequence().map { gy -> gx to gy } }
                            .filterNot { (gx, gy) -> gx == 0 && gy == 0 }
                            .firstNotNullOfOrNull { (gx, gy) -> bestMatchAt(gx * SEAM_TOLERANCE, gy * SEAM_TOLERANCE) }
                        hit?.let { (group, _) -> onRegionTap(group) }
                    }
                }
            },
    ) {
        val half = size.width / 2f
        listOf(BodySide.FRONT to front, BodySide.BACK to back).forEach { (side, parsed) ->
            val transform = fitTransform(if (side == BodySide.FRONT) 0f else half, half, size.height)
            translate(left = transform.originX, top = transform.originY) {
                scale(scaleX = transform.scale, scaleY = transform.scale, pivot = Offset.Zero) {
                    val lineWidth = 0.75.dp.toPx() / transform.scale
                    drawPath(parsed.silhouette, restColor.copy(alpha = 0.55f))
                    drawPath(parsed.silhouette, lineColor.copy(alpha = 0.5f), style = Stroke(width = lineWidth))
                    parsed.muscles.forEach { (group, path) ->
                        val t = (intensity[group] ?: 0f).coerceIn(0f, 1f)
                        drawPath(path, lerp(restColor, fillColor, t))
                        drawPath(path, lineColor.copy(alpha = 0.6f), style = Stroke(width = lineWidth))
                        if (selected != null && group in selected) {
                            drawPath(path, outlineColor, style = Stroke(width = 1.5.dp.toPx() / transform.scale))
                        }
                    }
                }
            }
        }
    }
}
