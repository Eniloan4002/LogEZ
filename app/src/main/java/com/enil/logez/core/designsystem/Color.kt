package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * logEZ palette v3.0 "Neon Lab" — dark-only (Owner directive, unchanged since launch). Replaces
 * the MP076 palette (v2.x, docs/adr/0002-color-rebrand.md) with a bolder, maximalist direction the
 * Owner explicitly asked for: a "mad scientist" identity executed through the clinical/industrial
 * half of that aesthetic (surgical steel, hazard signage, glass specimen tubes) rather than
 * cartoon-mascot camp — see docs/adr/0003-neon-lab-rebrand.md for the full rationale, the approved
 * mockup, and every contrast number. Settled interactively via a design-canvas mockup before any
 * code was written; the Owner asked for one swap from that mockup (tertiary violet -> blue).
 */

// Neutrals — near-black base, deliberately deeper than v2.x's Neutral950 for more contrast against
// the neon accents below (a "lab at night" backdrop, not a navy-tinted dark mode).
val Neutral0 = Color(0xFFEAF2E9) // primary text — a faint green-white tint, not pure white
val Neutral400 = Color(0xFF7C8A82) // secondary text / onSurfaceVariant
val Neutral600 = Color(0xFF3A444B) // outline — stronger borders (dividers that must read as a boundary)
val Neutral700 = Color(0xFF232B30) // outlineVariant — card hairlines, quieter than Neutral600
val Neutral800 = Color(0xFF1C2225) // surfaceVariant — chip fills, subtly raised surfaces
val Neutral900 = Color(0xFF12161A) // surface — cards
val Neutral950 = Color(0xFF0A0D0F) // background

// Brand accents. All three share similar saturation/glow character so they read as one considered
// system rather than one hero color plus afterthoughts.
val NeonGreen = Color(0xFF39FF6E) // primary — the signature "glowing specimen" accent, used boldly
val HazardAmber = Color(0xFFF5C518) // secondary — filled banner/container surfaces, warning-adjacent
val SpecimenBlue = Color(0xFF2E9FFF) // tertiary — selection/highlight accent, kept visually distinct
                                      // from primary so a "selected" state reads as a second accent

// Semantic — unrelated to brand identity.
val Danger500 = Color(0xFFFF5A50)
val Warning500 = Color(0xFFF5C518) // aliased to HazardAmber -- one hazard-yellow, not two near-identical ambers

/**
 * Superset group colors (PHASE2_PLAN.md §5.1.2) — indexed by `supersetGroup % SupersetPalette.size`.
 * Deliberately left untouched by the Neon Lab rebrand: kept distinct from [NeonGreen]/[HazardAmber]/
 * [SpecimenBlue] so a superset badge never reads as a brand or semantic color, the same reasoning
 * that has applied since the palette was first introduced.
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
