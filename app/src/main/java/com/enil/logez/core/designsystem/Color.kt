package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * logEZ palette v2.1 — dark-only (Owner directive, unchanged since launch), rebranded onto a
 * 6-color palette the Owner supplied (brand board "MP076", credited Alex Cristache
 * @AlexCristache — see docs/adr/0002-color-rebrand.md and the vault branding reference). The
 * neutral ramp is a heavily-desaturated derivative of Midnight Mirage (dark end) → Praxeti White
 * (light end) at a fixed ~210° hue: v2.0 ran the ramp at the brand hex's own near-100% saturation,
 * which read as a dense "navy blue app" rather than a dark app with color accents (Owner
 * feedback — "too dark", "overall feel") — v2.1 caps saturation around 10-22%, matching the
 * *original* near-black palette's own subtlety, and lets the accent colors below carry all the
 * saturation instead. Every step and every accent pairing was re-verified against computed WCAG
 * contrast after the rework (see the ADR), not chosen by eye.
 */

// Neutrals — fixed-hue, low-saturation ramp between a desaturated Midnight Mirage (950) and
// Praxeti White (0). Named for their brand-hue source, but 950-700 are deliberately NOT the
// literal brand hex — see the KDoc above.
val Neutral0 = Color(0xFFF6F7ED) // Praxeti White (literal)
val Neutral400 = Color(0xFFA0A8B1)
val Neutral600 = Color(0xFF5E6B78)
val Neutral700 = Color(0xFF424C57) // outlineVariant only — quieter than Neutral600's outline
val Neutral800 = Color(0xFF283039)
val Neutral900 = Color(0xFF1A2026)
val Neutral950 = Color(0xFF11161A) // desaturated Midnight Mirage

// Brand accents.
val Mantis = Color(0xFF74C365) // primary — the palette's most brand-forward color
val FirstColorsOfSpring = Color(0xFFDBE64C) // tertiary — selection/highlight accent, kept visually
                                             // distinct from Mantis so a "selected" state reads as
                                             // a second accent, not just "more primary"
val NuitBlanche = Color(0xFF1E488F) // secondary — filled banner/container surfaces

// Semantic — unrelated to brand identity, unchanged by the rebrand.
val Danger500 = Color(0xFFD8564B)
val Warning500 = Color(0xFFE0A63C)

/**
 * Superset group colors (PHASE2_PLAN.md §5.1.2) — indexed by `supersetGroup % SupersetPalette.size`.
 * Kept distinct from [Mantis]/[FirstColorsOfSpring]/[NuitBlanche] so a superset badge never reads
 * as a brand or semantic color. Picture Book Green appended as a 9th entry for the rebrand — every
 * existing index 0-7 is unchanged (only groups 8+ start seeing the new color, wrapping at 9
 * instead of 8), so no existing superset's color shifts.
 */
val SupersetPalette = listOf(
    Color(0xFF4FAE7F), // green
    Color(0xFF4C8FE0), // blue
    Color(0xFFE0A63C), // amber
    Color(0xFFD8564B), // coral
    Color(0xFF8C4570), // plum
    Color(0xFF3BB5B0), // teal
    Color(0xFFE07FB0), // pink
    Color(0xFF8C9C3B), // olive
    Color(0xFF00804C), // Picture Book Green
)
