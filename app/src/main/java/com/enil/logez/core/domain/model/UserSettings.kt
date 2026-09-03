package com.enil.logez.core.domain.model

import java.time.DayOfWeek

/** Every value in PHASE2_PLAN.md §5.2's Settings tree, aggregated for a single observable read. Theme is dark-only (Owner directive) — no theme-mode setting. */
data class UserSettings(
    // Preferences
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val perExerciseUnitOverrides: Map<String, WeightUnit> = emptyMap(),

    // Workouts
    val defaultRestTimerSeconds: Int = 90,
    val timerSound: Int = 1,
    // Owner, 2026-09-03: every audio setting is a continuous 0f-1f slider now, not a discrete
    // Off/Quiet/Normal/Loud step — 0f means silent (the same "off" the old enum's OFF meant).
    val timerVolume: Float = 0.66f,
    val setCompleteVolume: Float = 0.66f,
    val prVolume: Float = 0.66f,
    val previousValuesMode: PreviousValuesMode = PreviousValuesMode.ANY_WORKOUT,
    val warmupCalculatorEnabled: Boolean = true,
    val warmupMethod: List<WarmupStep> = defaultWarmupMethod,
    val includeWarmupsInStats: Boolean = false,
    val keepAwake: Boolean = true,
    val plateCalculatorEnabled: Boolean = true,
    val plateEquipment: PlateEquipment = defaultPlateEquipment,
    val rpeTrackingEnabled: Boolean = false,
    val smartSupersetScrolling: Boolean = true,
    val inlineTimerEnabled: Boolean = true,
    val livePrNotificationEnabled: Boolean = true,
    /** Owner, 2026-09-03: hides the Workout tab's Progress heatmap when off. Default on — nothing is hidden today. */
    val showHeatmap: Boolean = true,
    /** Owner, 2026-09-03: hides the Workout tab's Goals card when off. Default on — nothing is hidden today. */
    val showGoals: Boolean = true,
)
