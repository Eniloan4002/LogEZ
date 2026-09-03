package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * logEZ palette v6.0 "Neon Lab, root #73FF00" — dark-only (Owner directive, unchanged since launch).
 * v4.0 narrowed the palette to black, white and green only; v5.0 (2026-09-03) re-rooted that green
 * on `#CAFF00`; v6.0 (Owner directive, 2026-09-04) re-roots it again on [NeonGreen] = `#73FF00` — a
 * truer green (H≈93°) than v5.0's yellow-green. Every other green in the file — the two derived
 * accents, the muted warm-up tone, and the nine-step superset ramp — is rebuilt as a lightness/
 * saturation variant of that same hue rather than left pointing at v5.0's old hue, so "every green
 * in the app" still traces back to one root. Hierarchy is still carried by vibrancy and lightness
 * within green: the more vibrant/lighter, the more primary the thing wearing it. See
 * docs/adr/0007-root-green-73ff00.md for the full rationale and every contrast number (computed via
 * WCAG relative-luminance contrast and CIE76 deltaE, not picked by eye).
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
// hue (H≈93°) at a different lightness/saturation, verified against computed WCAG contrast on
// Neutral950 rather than picked by eye (see the ADR's v6.0 table).
val NeonGreen = Color(0xFF73FF00) // primary — the root: CTAs, active states, chart fills (14.88:1)
val SpringGreen = Color(0xFFB1FF70) // tertiary — selection/highlight, a paler tint of the root (16.15:1)
val DeepGreen = Color(0xFF459504) // secondary — filled banners; recedes on purpose (5.17:1)

// Semantic — deliberately NOT part of the brand palette.
/** Destructive actions only. Stays red on purpose: "delete is red" is a safety convention, not branding. */
val Danger500 = Color(0xFFFF5A50)

/**
 * Warm-up set badges. A muted, low-vibrancy tint of the root hue — deliberately less vibrant than
 * [DeepGreen] so the hierarchy rule holds: a warm-up set is a lesser set, and now it literally
 * reads as a lesser green (4.90:1).
 */
val Warning500 = Color(0xFF68894D)

/**
 * Superset group colors (PHASE2_PLAN.md §5.1.2) — indexed by `supersetGroup % SupersetPalette.size`.
 *
 * v6.0 rebuilds these as tints/shades of the v6.0 [NeonGreen] root hue (Owner directive, 2026-09-04:
 * every green in the app "falls under" the root — same standing rule v5.0 established). These have
 * one hard functional requirement — group A must be tellable from group B at badge size — so the
 * nine steps were derived by optimizing for *maximum minimum pairwise CIE deltaE* within a narrow
 * band around the root hue (88°-162°, yellow-green through spring-green, staying clear of both true
 * yellow and anything that reads as cyan), subject to every step clearing 4.5:1 on [Neutral950] and
 * every step reading as visibly distinct from the root itself (deltaE > 8 from `#73FF00`). Result:
 * worst pair deltaE 25.31 (v5.0 scored 28.77, v4.0 scored 30.4 — comparable separation each time).
 * Ordered lightest-first so lower group indices — the ones a typical routine actually uses — are
 * the most vibrant, matching the palette's own hierarchy rule.
 */
val SupersetPalette = listOf(
    Color(0xFFC1FF7A), // pale yellow-green
    Color(0xFFA5FF3D), // vivid yellow-green
    Color(0xFF14FFA5), // spring green
    Color(0xFF4BFF14), // pure green
    Color(0xFFA8E6D1), // pale mint
    Color(0xFF88DD92), // sage green
    Color(0xFF10C638), // grass
    Color(0xFF518E0B), // olive-green
    Color(0xFF0B8E62), // deep teal-green
)
