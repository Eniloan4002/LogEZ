package com.enil.logez.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Constrained spacing/shape scale (Refactoring UI, ~25%+ step growth) — the vault's distilled
 * rule that ambiguous spacing is the classic amateur tell. Never invent an in-between value:
 * jump to the next step instead.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp
}

object Radius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val pill: Dp = 999.dp
}

/** M9b (Neon Lab) — real elevation for [LogEzCard], replacing the fully-flat pre-rebrand cards. */
object Elevation {
    val card: Dp = 4.dp
}

/**
 * Shared column geometry for every set-logging table (regular logger card, circuit round card,
 * routine builder) — one source so the three tables can't drift. Each fixed cell is sized so its
 * widest header word fits WHOLE on one line ("ROUND" for the set cell; "152.5 kg × 12" for the
 * PREVIOUS value column): a label that breaks mid-word ("ROU/ND") is a rendering bug, not wrapping.
 */
object SetTable {
    /** SET/ROUND number cell — "ROUND" is the widest label sharing this slot. */
    val setCell: Dp = 48.dp

    /** PREVIOUS column (Owner, 2026-09-03: widened from 84dp — "10 reps @…" was cutting off before
     the RPE suffix). Wide enough for typical values with an RPE suffix ("10 reps @ 8.5", "50 kg ×
     10 @ 8") to render un-broken; the reclaimed width comes out of the weighted value cells
     (REPS/KG/TIME/DISTANCE), which is fine at typical digit counts. Only pathological cases
     (fractional weight + RPE, e.g. "102.5 kg × 10 @ 8.5") still ellipsize inside the box. */
    val previousCell: Dp = 112.dp

    val rpeCell: Dp = 44.dp

    /** Trailing check/action column (also the header row's spacer over it). */
    val checkCell: Dp = 40.dp

    /**
     * M17: the plate-calculator tap target that trails a barbell row's KG cell. The header rows
     * add a spacer of the same width whenever the affordance renders, so the KG header stays
     * centered over the (cell + button) pair and the M15 column alignment holds.
     */
    val plateCalcCell: Dp = 28.dp

    /**
     * M18 (Owner: uniform Hevy-style boxed cells): every set-row cell renders as a box of this
     * height. 56dp is Material3's OutlinedTextField min height — the KG/REPS/TIME fields already
     * render at it, so SET, PREVIOUS, and RPE boxes match the fields rather than the other way
     * round (widths stay the M15 SetTable values; only the fixed cells' chrome changed).
     */
    val cellHeight: Dp = 56.dp
}
