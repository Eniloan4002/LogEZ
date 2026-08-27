package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * M9b (Neon Lab, docs/adr/0003-neon-lab-rebrand.md) — custom lab-signage-styled line icons for the
 * app's nav tabs, replacing default Material icons. Deliberately scoped: universal UI chrome
 * (back arrows, close, check, chevrons, search, ...) stays default Material — those are
 * conventions users already read instantly, and redesigning them buys no brand payoff. Personal
 * records use the default Material trophy (`Icons.Filled.EmojiEvents`), not a custom glyph.
 * Per-[com.enil.logez.core.domain.model.MuscleGroup] illustrations remain the separate,
 * already-deferred §7.5 placeholder ([muscleGroupIcon]) -- not touched here.
 *
 * `Icon(imageVector = ..., tint = ...)` recolors whatever is drawn regardless of the stroke color
 * set below, so these use a plain black stroke -- what matters is that each path is drawn as a
 * STROKE (fill = null), not a filled shape, so the icon reads as line art at any tint.
 */
object LogEzIcons {
    private const val STROKE_WIDTH = 1.8f

    /** Replaces every `Icons.Filled.FitnessCenter` call site (nav tab, empty states, generic exercise fallback). */
    val Workout: ImageVector by lazy {
        ImageVector.Builder(name = "LogEzWorkout", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // left plate
                moveTo(2f, 7f); lineTo(6f, 7f); lineTo(6f, 17f); lineTo(2f, 17f); close()
            }
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // right plate
                moveTo(18f, 7f); lineTo(22f, 7f); lineTo(22f, 17f); lineTo(18f, 17f); close()
            }
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round) {
                // bar
                moveTo(6f, 12f); lineTo(18f, 12f)
            }
        }.build()
    }

    /** Replaces every `Icons.Filled.History` call site (nav tab, History screen header). */
    val History: ImageVector by lazy {
        ImageVector.Builder(name = "LogEzHistory", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // clip tab
                moveTo(9f, 2f); lineTo(15f, 2f); lineTo(15f, 4.5f); lineTo(9f, 4.5f); close()
            }
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // clipboard body
                moveTo(5f, 3.5f); lineTo(19f, 3.5f); lineTo(19f, 21f); lineTo(5f, 21f); close()
            }
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round) {
                // log lines
                moveTo(8f, 9f); lineTo(16f, 9f)
                moveTo(8f, 13f); lineTo(16f, 13f)
                moveTo(8f, 17f); lineTo(13f, 17f)
            }
        }.build()
    }

    /** Replaces `Icons.Filled.Person` (Profile nav tab). */
    val Profile: ImageVector by lazy {
        ImageVector.Builder(name = "LogEzProfile", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // ID badge outline
                moveTo(4f, 3f); lineTo(20f, 3f); lineTo(20f, 21f); lineTo(4f, 21f); close()
            }
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // head
                moveTo(9f, 9f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 15f, y1 = 9f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 9f, y1 = 9f)
                close()
            }
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // shoulders
                moveTo(7f, 19f); lineTo(9f, 14.5f); lineTo(15f, 14.5f); lineTo(17f, 19f)
            }
        }.build()
    }
}
