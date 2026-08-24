package com.enil.logez.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.enil.logez.core.domain.model.MuscleGroup

/**
 * Original, stylized front + back body silhouette with per-[MuscleGroup] regions — logEZ-drawn
 * geometry (simple capsules/ovals in normalized coordinates), deliberately NOT a copy of any
 * Hevy asset (standing project rule). Regions fill from the muted silhouette color up to the
 * theme primary by [intensity] (0..1 per group); [selected] regions get an outline; [onRegionTap]
 * (when non-null) makes regions tappable for §5.2 card 4's select/deselect-on-the-diagram.
 *
 * CARDIO, FULL_BODY, and OTHER have no body region — surfaces list them beside the diagram.
 */
object BodyDiagramRegions {
    /** Groups the diagram can render; the three non-anatomical groups are deliberately absent. */
    val MAPPABLE: Set<MuscleGroup> = MuscleGroup.entries.toSet() - setOf(MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.OTHER)
}

private enum class Side { FRONT, BACK }

/** A shape in figure-local coordinates (x 0..1 across one figure, y 0..1 top-to-bottom). */
private sealed interface FigureShape {
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : FigureShape
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float, val corner: Float = 0.02f) : FigureShape
}

private data class Region(val group: MuscleGroup, val side: Side, val shapes: List<FigureShape>)

private fun mirrored(cx: Float, cy: Float, rx: Float, ry: Float) =
    listOf(FigureShape.Oval(cx, cy, rx, ry), FigureShape.Oval(1f - cx, cy, rx, ry))

private val REGIONS: List<Region> = listOf(
    // --- front ---
    Region(MuscleGroup.SHOULDERS, Side.FRONT, mirrored(0.30f, 0.165f, 0.052f, 0.038f)),
    Region(MuscleGroup.CHEST, Side.FRONT, mirrored(0.435f, 0.215f, 0.062f, 0.045f)),
    Region(MuscleGroup.BICEPS, Side.FRONT, mirrored(0.275f, 0.25f, 0.037f, 0.055f)),
    Region(MuscleGroup.FOREARMS, Side.FRONT, mirrored(0.255f, 0.35f, 0.033f, 0.06f)),
    Region(MuscleGroup.ABDOMINALS, Side.FRONT, listOf(FigureShape.Rect(0.43f, 0.27f, 0.57f, 0.42f, 0.03f))),
    Region(MuscleGroup.ABDUCTORS, Side.FRONT, mirrored(0.395f, 0.475f, 0.038f, 0.045f)),
    Region(MuscleGroup.ADDUCTORS, Side.FRONT, mirrored(0.475f, 0.51f, 0.026f, 0.055f)),
    Region(MuscleGroup.QUADRICEPS, Side.FRONT, mirrored(0.435f, 0.575f, 0.045f, 0.095f)),
    // --- back ---
    Region(MuscleGroup.NECK, Side.BACK, listOf(FigureShape.Oval(0.5f, 0.125f, 0.035f, 0.025f))),
    Region(MuscleGroup.TRAPS, Side.BACK, mirrored(0.43f, 0.17f, 0.05f, 0.032f)),
    Region(MuscleGroup.UPPER_BACK, Side.BACK, listOf(FigureShape.Rect(0.40f, 0.20f, 0.60f, 0.27f, 0.03f))),
    Region(MuscleGroup.LATS, Side.BACK, mirrored(0.415f, 0.30f, 0.05f, 0.055f)),
    Region(MuscleGroup.LOWER_BACK, Side.BACK, listOf(FigureShape.Rect(0.44f, 0.355f, 0.56f, 0.42f, 0.03f))),
    Region(MuscleGroup.TRICEPS, Side.BACK, mirrored(0.275f, 0.25f, 0.037f, 0.055f)),
    Region(MuscleGroup.FOREARMS, Side.BACK, mirrored(0.255f, 0.35f, 0.033f, 0.06f)),
    Region(MuscleGroup.GLUTES, Side.BACK, mirrored(0.455f, 0.465f, 0.05f, 0.045f)),
    Region(MuscleGroup.HAMSTRINGS, Side.BACK, mirrored(0.435f, 0.60f, 0.045f, 0.08f)),
    Region(MuscleGroup.CALVES, Side.BACK, mirrored(0.44f, 0.78f, 0.035f, 0.07f)),
)

@Composable
fun BodyDiagram(
    intensity: Map<MuscleGroup, Float>,
    modifier: Modifier = Modifier,
    selected: Set<MuscleGroup>? = null,
    onRegionTap: ((MuscleGroup) -> Unit)? = null,
) {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.tertiary

    // Each figure renders in its own half of the canvas; hit-testing runs the same mapping back.
    fun figureLocal(tap: Offset, size: Size): Pair<Side, Offset>? {
        val half = size.width / 2f
        val side = if (tap.x < half) Side.FRONT else Side.BACK
        val localX = (if (side == Side.FRONT) tap.x else tap.x - half) / half
        return Pair(side, Offset(localX, tap.y / size.height))
    }

    fun FigureShape.contains(p: Offset): Boolean = when (this) {
        is FigureShape.Oval -> {
            val dx = (p.x - cx) / rx
            val dy = (p.y - cy) / ry
            dx * dx + dy * dy <= 1f
        }
        is FigureShape.Rect -> p.x in left..right && p.y in top..bottom
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .let { m ->
                if (onRegionTap == null) m
                else m.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val (side, local) = figureLocal(tap, Size(size.width.toFloat(), size.height.toFloat())) ?: return@detectTapGestures
                        // Smallest matching region wins so inner shapes (adductors) beat the
                        // larger overlapping ones (quadriceps).
                        REGIONS.filter { it.side == side && it.shapes.any { s -> s.contains(local) } }
                            .minByOrNull { r -> r.shapes.sumOf { s -> s.area().toDouble() } }
                            ?.let { onRegionTap(it.group) }
                    }
                }
            },
    ) {
        Side.entries.forEach { side ->
            val originX = if (side == Side.FRONT) 0f else size.width / 2f
            val figureWidth = size.width / 2f
            drawSilhouette(baseColor, originX, figureWidth)
            REGIONS.filter { it.side == side }.forEach { region ->
                val t = (intensity[region.group] ?: 0f).coerceIn(0f, 1f)
                val color = lerp(baseColor, fillColor, t)
                val isSelected = selected != null && region.group in selected
                region.shapes.forEach { shape ->
                    drawFigureShape(shape, color, originX, figureWidth)
                    if (isSelected) drawFigureShape(shape, outlineColor, originX, figureWidth, stroke = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

private fun FigureShape.area(): Float = when (this) {
    is FigureShape.Oval -> (Math.PI * rx * ry).toFloat()
    is FigureShape.Rect -> (right - left) * (bottom - top)
}

private fun DrawScope.drawFigureShape(
    shape: FigureShape,
    color: Color,
    originX: Float,
    figureWidth: Float,
    stroke: Stroke? = null,
) {
    val h = size.height
    when (shape) {
        is FigureShape.Oval -> drawOval(
            color = color,
            topLeft = Offset(originX + (shape.cx - shape.rx) * figureWidth, (shape.cy - shape.ry) * h),
            size = Size(shape.rx * 2 * figureWidth, shape.ry * 2 * h),
            style = stroke ?: androidx.compose.ui.graphics.drawscope.Fill,
        )
        is FigureShape.Rect -> drawRoundRect(
            color = color,
            topLeft = Offset(originX + shape.left * figureWidth, shape.top * h),
            size = Size((shape.right - shape.left) * figureWidth, (shape.bottom - shape.top) * h),
            cornerRadius = CornerRadius(shape.corner * figureWidth),
            style = stroke ?: androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
}

/** The muted base figure both views share: head, trunk, arms, legs — original simple geometry. */
private fun DrawScope.drawSilhouette(color: Color, originX: Float, figureWidth: Float) {
    val h = size.height
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = drawOval(
        color = color.copy(alpha = 0.45f),
        topLeft = Offset(originX + (cx - rx) * figureWidth, (cy - ry) * h),
        size = Size(rx * 2 * figureWidth, ry * 2 * h),
    )
    fun capsule(l: Float, t: Float, r: Float, b: Float) = drawRoundRect(
        color = color.copy(alpha = 0.45f),
        topLeft = Offset(originX + l * figureWidth, t * h),
        size = Size((r - l) * figureWidth, (b - t) * h),
        cornerRadius = CornerRadius(((r - l) / 2f) * figureWidth),
    )
    oval(0.5f, 0.065f, 0.055f, 0.05f)              // head
    capsule(0.46f, 0.10f, 0.54f, 0.15f)            // neck
    capsule(0.35f, 0.14f, 0.65f, 0.44f)            // trunk
    capsule(0.235f, 0.16f, 0.315f, 0.42f)          // left arm
    capsule(0.685f, 0.16f, 0.765f, 0.42f)          // right arm
    capsule(0.40f, 0.43f, 0.49f, 0.90f)            // left leg
    capsule(0.51f, 0.43f, 0.60f, 0.90f)            // right leg
}
