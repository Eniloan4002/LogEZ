package com.enil.logez.feature.settings

/** Route strings for the Workout Settings tree (M16/M17). All but the root are sub-screens — no bottom bar. */
object SettingsRoutes {
    const val SETTINGS = "settings"
    const val SOUNDS = "settings_sounds"

    /** M17: the Plate Calculator's bars-and-plates editor (§5.1.5 "Manage", relocated to Settings). */
    const val PLATE_EQUIPMENT = "settings_plate_equipment"

    /** M18: the Warm-up Calculator's percent × reps ladder editor (§5.1.6 "Warmup Method"). */
    const val WARMUP_SETS = "settings_warmup_sets"
    const val DATA = "settings_data"

    /** Settings > About > Open-source licences (Play-readiness audit, 2026-09-25). */
    const val LICENSES = "settings_licenses"
}
