package com.enil.logez.core.domain.model

/**
 * P-211 (Owner, 2026-09-30, decisions 1-2): which scale a set's effort is shown and entered in.
 *
 * Display and entry only. Storage never changes: `WorkoutSetEntity.rpe` stays on the RPE scale
 * whatever this says, and RIR is derived from it as 10 − RPE ([RpeScale.formatRir]). Switching
 * therefore relabels every past set in both directions and needs no migration.
 *
 * Whether effort is tracked at all is still [UserSettings.rpeTrackingEnabled]; "Off" in Settings
 * only turns that off and leaves this scale as it was, so History and PREVIOUS keep showing saved
 * values in it (decision 4).
 */
enum class EffortScale {
    /** Rate of perceived exertion, 6-10 in half steps. The default, so existing users land here. */
    RPE,

    /** Reps in reserve: 0, 1, 2, 3 and 4+, entered as whole numbers and stored as RPE 10-6. */
    RIR,
}
