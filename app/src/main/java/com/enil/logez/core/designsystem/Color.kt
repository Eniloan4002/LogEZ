package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * logEZ palette v4.0 "Neon Lab, mono-green" — dark-only (Owner directive, unchanged since launch).
 * Narrows v3.x's three-hue accent set (green + amber + blue) to **black, white and green only**
 * (Owner directive 2026-08-27). Hierarchy is now carried by *vibrancy and lightness within green*
 * rather than by hue: the more vibrant/lighter a green, the more primary the thing wearing it.
 * See docs/adr/0003-neon-lab-rebrand.md for the full rationale and every contrast number.
 */

// Neutrals — the "black and white" half of the palette. Unchanged from v3.x.
val Neutral0 = Color(0xFFEAF2E9) // primary text — a faint green-white tint, not pure white
val Neutral400 = Color(0xFF7C8A82) // secondary text / onSurfaceVariant
val Neutral600 = Color(0xFF3A444B) // outline — stronger borders (dividers that must read as a boundary)
val Neutral700 = Color(0xFF232B30) // outlineVariant — card hairlines, quieter than Neutral600
val Neutral800 = Color(0xFF1C2225) // surfaceVariant — chip fills, subtly raised surfaces
val Neutral900 = Color(0xFF12161A) // surface — cards
val Neutral950 = Color(0xFF0A0D0F) // background

// Brand greens, ordered by vibrancy = ordered by prominence. Every value below was verified against
// computed WCAG contrast on Neutral950, and against CIE deltaE separation from its siblings, rather
// than picked by eye (see the ADR's v4.0 table).
val NeonGreen = Color(0xFF39FF6E) // primary — the hero: CTAs, active states, chart fills (14.58:1)
val SpringGreen = Color(0xFFB8FF5C) // tertiary — selection/highlight, a lighter yellow-green (16.24:1)
val DeepGreen = Color(0xFF12A65A) // secondary — filled banners; recedes on purpose (6.15:1)

// Semantic — deliberately NOT part of the brand palette.
/** Destructive actions only. Stays red on purpose: "delete is red" is a safety convention, not branding. */
val Danger500 = Color(0xFFFF5A50)

/**
 * Warm-up set badges. Was a hazard-amber in v3.x; with yellow removed it becomes a *muted, low-vibrancy*
 * green, which fits the new hierarchy rule better than the amber ever did — a warm-up set is a lesser
 * set, and now it literally reads as a lesser green (5.04:1).
 */
val Warning500 = Color(0xFF5E8C6A)

/**
 * Superset group colors (PHASE2_PLAN.md §5.1.2) — indexed by `supersetGroup % SupersetPalette.size`.
 *
 * v4.0 rebuilds these as a green-only ramp (Owner directive: no blue/amber/coral/plum anywhere).
 * These have one hard functional requirement — group A must be tellable from group B at badge size —
 * so the nine steps were derived by optimizing for *maximum minimum pairwise CIE deltaE* within a
 * green-only hue band (100°-158°, i.e. yellow-green through spring-green, deliberately stopping short
 * of anything that reads as cyan), subject to every step clearing 4.5:1 on [Neutral950]. Result:
 * worst pair deltaE 30.4 (a naive lightness-only ramp scored 12.9, well into "these two look the
 * same" territory). Ordered lightest-first so lower group indices — the ones a typical routine
 * actually uses — are the most vibrant, matching the palette's own hierarchy rule.
 */
val SupersetPalette = listOf(
    Color(0xFFADFF83), // yellow-green
    Color(0xFF06FF8C), // spring green
    Color(0xFFBCE6D7), // pale sage
    Color(0xFF0AFF05), // pure green
    Color(0xFF4FF1B6), // mint
    Color(0xFFA5D48E), // sage
    Color(0xFF38C412), // grass
    Color(0xFF47A33E), // forest
    Color(0xFF3B9B78), // deep emerald
)
