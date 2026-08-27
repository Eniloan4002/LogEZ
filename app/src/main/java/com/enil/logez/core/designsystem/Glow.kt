package com.enil.logez.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mockup's glow language, finally in code (v4.0). The approved design-canvas mockup leaned on
 * CSS `box-shadow`/`text-shadow` bloom around the neon accent — none of which survived into the
 * first implementation passes, which is why the shipped app reads flatter than the mockup did.
 *
 * Compose has no true blur-shadow primitive below API 31, so these approximate it two ways:
 * [neonGlow] uses a colored elevation shadow ([Modifier.shadow]'s `spotColor`/`ambientColor`,
 * honored from API 28) and [glowFalloff] paints an explicit gradient bloom, which works on every
 * supported API. minSdk here is 26, so on 26-27 [neonGlow] degrades to an ordinary dark shadow —
 * a slightly flatter look, never a broken one.
 */

/**
 * A colored bloom around a filled element — the mockup's
 * `box-shadow: 0 0 14px rgba(57,255,110,0.45)` on primary buttons and active chips.
 * Apply BEFORE `background(...)`/`clip(...)` so the shadow renders outside the fill.
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(Radius.md),
    alpha: Float = 0.55f,
): Modifier = this.shadow(
    elevation = radius,
    shape = shape,
    clip = false,
    ambientColor = color.copy(alpha = alpha),
    spotColor = color.copy(alpha = alpha),
)

/**
 * An explicit vertical gradient bloom fading downward from the top edge — the mockup's card
 * top-edge accent (`linear-gradient(90deg, accent, transparent 70%)` plus the ambient wash that
 * made cards feel lit from above rather than flat). API-independent, unlike [neonGlow].
 */
fun Modifier.glowFalloff(
    color: Color,
    height: Dp = 2.dp,
    spread: Dp = 28.dp,
    alpha: Float = 0.5f,
): Modifier = this.drawBehind {
    val lineH = height.toPx()
    val spreadPx = spread.toPx()
    // Soft wash under the top edge, strongest at the edge itself.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = alpha * 0.35f), Color.Transparent),
            startY = 0f,
            endY = spreadPx,
        ),
        size = Size(size.width, spreadPx.coerceAtMost(size.height)),
    )
    // The bright edge itself, fading out along its length like the mockup's accent bar.
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            startX = 0f,
            endX = size.width * 0.7f,
        ),
        size = Size(size.width, lineH),
    )
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
