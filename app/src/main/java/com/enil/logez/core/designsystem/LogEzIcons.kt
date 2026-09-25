package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * M9b (Neon Lab, docs/adr/0003-neon-lab-rebrand.md) — custom lab-signage-styled line icons for the
 * app's nav tabs, replacing default Material icons. Personal records use the Material trophy
 * (`Icons.Outlined.EmojiEvents`), not a custom glyph. Per-[com.enil.logez.core.domain.model.MuscleGroup]
 * illustrations remain the separate, already-deferred §7.5 placeholder ([muscleGroupIcon]).
 *
 * 2026-09-25 (Owner: "more premium feeling icons"): the whole app moved to one line-icon style.
 * Every Material icon switched from Filled to Outlined, so these three were redrawn to match it:
 * a 2-unit stroke on the 24-unit grid (the Outlined set's own weight, up from 1.8), rounded
 * joins and caps, and rounded corners instead of the old hard rectangles. Before this, line-art
 * tab icons sat beside heavy filled chrome icons, which read as two different apps.
 *
 * `Icon(imageVector = ..., tint = ...)` recolors whatever is drawn regardless of the stroke color
 * set below, so these use a plain black stroke -- what matters is that each path is drawn as a
 * STROKE (fill = null), not a filled shape, so the icon reads as line art at any tint.
 */
object LogEzIcons {
    private const val STROKE_WIDTH = 2f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply(block)
            .build()

    private fun ImageVector.Builder.line(pathBuilder: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }

    /** A closed rectangle from ([left], [top]) to ([right], [bottom]) with corner radius [r]. */
    private fun PathBuilder.roundedRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right, y1 = top + r)
        lineTo(right, bottom - r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right - r, y1 = bottom)
        lineTo(left + r, bottom)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left, y1 = bottom - r)
        lineTo(left, top + r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left + r, y1 = top)
        close()
    }

    /** Workout tab, empty states and the generic exercise fallback: a loaded barbell, two plates a side. */
    val Workout: ImageVector by lazy {
        icon("LogEzWorkout") {
            line { roundedRect(5f, 6f, 8f, 18f, 1.2f) } // inner left plate
            line { roundedRect(16f, 6f, 19f, 18f, 1.2f) } // inner right plate
            line { roundedRect(2f, 8.5f, 5f, 15.5f, 1f) } // outer left plate
            line { roundedRect(19f, 8.5f, 22f, 15.5f, 1f) } // outer right plate
            line { moveTo(8f, 12f); lineTo(16f, 12f) } // bar
        }
    }

    /** History tab and screen header: a lab-log clipboard. */
    val History: ImageVector by lazy {
        icon("LogEzHistory") {
            line { roundedRect(5f, 4f, 19f, 21f, 2.2f) } // board
            line { roundedRect(9f, 2.5f, 15f, 6f, 1.2f) } // clip
            line {
                moveTo(8.5f, 10.5f); lineTo(15.5f, 10.5f)
                moveTo(8.5f, 14f); lineTo(15.5f, 14f)
                moveTo(8.5f, 17.5f); lineTo(12.5f, 17.5f)
            }
        }
    }

    /** Profile tab: a specimen-ID badge. */
    val Profile: ImageVector by lazy {
        icon("LogEzProfile") {
            line { roundedRect(4f, 3f, 20f, 21f, 2.5f) } // badge
            line {
                // head
                moveTo(9.25f, 9.5f)
                arcTo(2.75f, 2.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14.75f, y1 = 9.5f)
                arcTo(2.75f, 2.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 9.25f, y1 = 9.5f)
                close()
            }
            line {
                // shoulders
                moveTo(7.5f, 17.5f)
                curveTo(8.2f, 15.2f, 9.9f, 14f, 12f, 14f)
                curveTo(14.1f, 14f, 15.8f, 15.2f, 16.5f, 17.5f)
            }
        }
    }
}
