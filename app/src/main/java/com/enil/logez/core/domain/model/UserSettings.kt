package com.enil.logez.core.domain.model

import com.enil.logez.core.common.ThemeMode
import java.time.DayOfWeek

/** Every value in PHASE2_PLAN.md §5.2's Settings tree, aggregated for a single observable read. */
data class UserSettings(
    // Preferences
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val perExerciseUnitOverrides: Map<String, WeightUnit> = emptyMap(),

    // Workouts
    val defaultRestTimerSeconds: Int = 90,
    val timerSound: Int = 1,
    val timerVolume: VolumeLevel = VolumeLevel.NORMAL,
    val setCompleteVolume: VolumeLevel = VolumeLevel.NORMAL,
    val prVolume: VolumeLevel = VolumeLevel.NORMAL,
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
)
