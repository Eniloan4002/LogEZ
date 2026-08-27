package com.enil.logez.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mockup's glow language, in code (v4.0). Scoped narrowly on Owner feedback (2026-08-27): a
 * card top-edge accent was tried and rejected as "ugly" and removed outright — glow is for
 * *titles and dashboards*, not ambient decoration on every card. What remains: [neonGlow] (a
 * colored bloom around a filled element — CTAs, active chips) and the chart-bar helpers
 * ([glowBarBrush], [drawBarGlow]) used by dashboard graphs specifically.
 *
 * Compose has no true blur-shadow primitive below API 31.
 */

/**
 * A colored bloom around a filled element — the mockup's
 * `box-shadow: 0 0 14px rgba(57,255,110,0.45)` on primary buttons and active chips.
 * Apply BEFORE `background(...)`/`clip(...)` so the glow renders outside the fill.
 *
 * Draws [passes] progressively larger, fainter copies of [shape]'s own outline (via
 * [Shape.createOutline], so this works for a pill just as well as a rounded rect) centered on the
 * element, rather than relying on [androidx.compose.ui.draw.shadow]'s elevation model — Android's
 * elevation shadow simulates a single overhead light, so its `spotColor` bloom always reads
 * stronger at the bottom than the top/sides (Owner-reported: "glow around its border not just
 * bottom"). An explicit drawn outline has no such direction; it comes out even on every side.
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(Radius.md),
    alpha: Float = 0.55f,
    passes: Int = 4,
): Modifier = this.drawBehind {
    val step = radius.toPx() / passes
    for (i in passes downTo 1) {
        val grow = step * i
        val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
        translate(left = -grow, top = -grow) {
            drawOutline(outline, color = color.copy(alpha = (alpha / i).coerceIn(0f, 1f)))
        }
    }
}

/**
 * The mockup's bar fill: a vertical gradient from the solid accent at the top to a translucent
 * tail at the baseline, so bars read as glowing columns rather than flat blocks. Used by
 * [BarChart] and the heatmap's fill ramp.
 */
fun glowBarBrush(color: Color, topAlpha: Float = 1f, bottomAlpha: Float = 0.25f): Brush =
    Brush.verticalGradient(listOf(color.copy(alpha = topAlpha), color.copy(alpha = bottomAlpha)))

/**
 * Canvas-side bloom for a drawn rectangle (a chart bar), approximating a blur with a few
 * progressively larger, fainter passes — Compose's Canvas has no blur filter available across
 * this app's whole API range.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBarGlow(
    color: Color,
    topLeft: Offset,
    size: Size,
    passes: Int = 3,
    spreadPx: Float = 6f,
    alpha: Float = 0.18f,
) {
    for (i in 1..passes) {
        val grow = spreadPx * i
        drawRect(
            color = color.copy(alpha = alpha / i),
            topLeft = Offset(topLeft.x - grow / 2f, topLeft.y - grow / 2f),
            size = Size(size.width + grow, size.height + grow),
        )
    }
}
