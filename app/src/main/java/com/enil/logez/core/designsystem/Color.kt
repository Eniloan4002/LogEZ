package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * logEZ palette v5.0 "Neon Lab, root #CAFF00" — dark-only (Owner directive, unchanged since launch).
 * v4.0 narrowed the palette to black, white and green only; v5.0 (Owner directive, 2026-09-03)
 * re-roots that green on [NeonGreen] = `#CAFF00` and rebuilds every other green in the file — the
 * two derived accents, the muted warm-up tone, and the nine-step superset ramp — as lightness/
 * saturation variants of that same hue (H≈72.5°) instead of the old independently-hand-picked
 * values, so "every green in the app" now traces back to one root. Hierarchy is still carried by
 * vibrancy and lightness within green: the more vibrant/lighter, the more primary the thing
 * wearing it. See docs/adr/0006-root-green-caff00.md for the full rationale and every contrast
 * number (computed via WCAG relative-luminance contrast and CIE76 deltaE, not picked by eye).
 */

// Neutrals — the "black and white" half of the palette. Unchanged from v3.x.
val Neutral0 = Color(0xFFEAF2E9) // primary text — a faint green-white tint, not pure white
val Neutral400 = Color(0xFF7C8A82) // secondary text / onSurfaceVariant
val Neutral600 = Color(0xFF3A444B) // outline — stronger borders (dividers that must read as a boundary)
val Neutral700 = Color(0xFF232B30) // outlineVariant — card hairlines, quieter than Neutral600
val Neutral800 = Color(0xFF1C2225) // surfaceVariant — chip fills, subtly raised surfaces
val Neutral900 = Color(0xFF12161A) // surface — cards
val Neutral950 = Color(0xFF0A0D0F) // background

// Brand greens, ordered by vibrancy = ordered by prominence. Every value below is [NeonGreen]'s own
// hue (H≈72.5°) at a different lightness/saturation, verified against computed WCAG contrast on
// Neutral950 rather than picked by eye (see the ADR's v5.0 table).
val NeonGreen = Color(0xFFCAFF00) // primary — the root: CTAs, active states, chart fills (16.54:1)
val SpringGreen = Color(0xFFE1FF70) // tertiary — selection/highlight, a paler tint of the root (17.40:1)
val DeepGreen = Color(0xFF779504) // secondary — filled banners; recedes on purpose (5.66:1)

// Semantic — deliberately NOT part of the brand palette.
/** Destructive actions only. Stays red on purpose: "delete is red" is a safety convention, not branding. */
val Danger500 = Color(0xFFFF5A50)

/**
 * Warm-up set badges. A muted, low-vibrancy tint of the root hue — deliberately less vibrant than
 * [DeepGreen] so the hierarchy rule holds: a warm-up set is a lesser set, and now it literally
 * reads as a lesser green (5.16:1).
 */
val Warning500 = Color(0xFF7D894D)

/**
 * Superset group colors (PHASE2_PLAN.md §5.1.2) — indexed by `supersetGroup % SupersetPalette.size`.
 *
 * v5.0 rebuilds these as tints/shades of the [NeonGreen] root hue (Owner directive, 2026-09-03:
 * every green in the app "falls under" the root). These have one hard functional requirement —
 * group A must be tellable from group B at badge size — so the nine steps were derived by
 * optimizing for *maximum minimum pairwise CIE deltaE* within a narrow band around the root hue
 * (68°-142°, yellow-green through spring-green, staying clear of both true yellow and anything
 * that reads as cyan), subject to every step clearing 4.5:1 on [Neutral950] and every step reading
 * as visibly distinct from the root itself (deltaE > 8 from `#CAFF00`). Result: worst pair deltaE
 * 28.77 (comparable to v4.0's 30.4). Ordered lightest-first so lower group indices — the ones a
 * typical routine actually uses — are the most vibrant, matching the palette's own hierarchy rule.
 */
val SupersetPalette = listOf(
    Color(0xFFF0FF8F), // pale yellow-green
    Color(0xFFE0FF14), // vivid yellow-green
    Color(0xFF66FF99), // mint
    Color(0xFF04FF00), // pure green
    Color(0xFF90F04C), // yellow-green
    Color(0xFFA8E6BD), // pale sage
    Color(0xFF8DB30F), // olive-green
    Color(0xFF009914), // forest
    Color(0xFF7A8627), // deep olive
)
