package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Original logEZ palette — dark-only (Owner directive), a cool near-black neutral base with a
 * single restrained violet accent, no gradients, no decoration for its own sake (Owner taste,
 * established on PSS + Fiterval: clean, compact, no ornament). Shades hand-picked per
 * Refactoring UI's HSL method, not lighten()/darken() at runtime.
 */

// Neutrals (cool-tinted greys) — only the dark-end steps are needed now that light theme is gone.
val Neutral0 = Color(0xFFFFFFFF)
val Neutral400 = Color(0xFF9AA1AE)
val Neutral600 = Color(0xFF5B6270)
val Neutral800 = Color(0xFF2B2F38)
val Neutral900 = Color(0xFF1B1F27)
val Neutral950 = Color(0xFF12151B)

// Accent — a restrained violet/indigo (Owner directive, #7063BF as the base tone).
val Accent400 = Color(0xFF9089D4)
val Accent500 = Color(0xFF7063BF)
val Accent600 = Color(0xFF564A94)

// Semantic
val Danger500 = Color(0xFFD8564B)
val Warning500 = Color(0xFFE0A63C)

/** Superset group colors (PHASE2_PLAN.md §5.1.2) — indexed by `supersetGroup % 8`, kept distinct from the violet accent. */
val SupersetPalette = listOf(
    Color(0xFF4FAE7F), // green
    Color(0xFF4C8FE0), // blue
    Color(0xFFE0A63C), // amber
    Color(0xFFD8564B), // coral
    Color(0xFF8C4570), // plum
    Color(0xFF3BB5B0), // teal
    Color(0xFFE07FB0), // pink
    Color(0xFF8C9C3B), // olive
)
