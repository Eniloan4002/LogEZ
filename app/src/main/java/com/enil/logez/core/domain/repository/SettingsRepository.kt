package com.enil.logez.core.domain.repository

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed settings (PHASE2_PLAN.md §5.2 — the Settings *screen* lands in M7; this
 * repository is core plumbing so M1's calc engines and M4+ features have real settings to read
 * from day one, instead of hard-coded stand-ins that would need to be re-wired later).
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setWeightUnit(value: WeightUnit)
    suspend fun setDistanceUnit(value: DistanceUnit)
    suspend fun setFirstDayOfWeek(value: DayOfWeek)
    suspend fun setPerExerciseUnitOverride(exerciseId: String, unit: WeightUnit?)

    suspend fun setDefaultRestTimerSeconds(value: Int)
    suspend fun setTimerSound(value: Int)
    suspend fun setTimerVolume(value: Float)
    suspend fun setSetCompleteVolume(value: Float)
    suspend fun setPrVolume(value: Float)
    suspend fun setPreviousValuesMode(value: PreviousValuesMode)
    suspend fun setWarmupCalculatorEnabled(value: Boolean)
    suspend fun setWarmupMethod(value: List<WarmupStep>)
    suspend fun setIncludeWarmupsInStats(value: Boolean)
    suspend fun setKeepAwake(value: Boolean)
    suspend fun setPlateCalculatorEnabled(value: Boolean)
    suspend fun setPlateEquipment(value: PlateEquipment)
    suspend fun setRpeTrackingEnabled(value: Boolean)
    suspend fun setSmartSupersetScrolling(value: Boolean)
    suspend fun setInlineTimerEnabled(value: Boolean)
    suspend fun setLivePrNotificationEnabled(value: Boolean)
    suspend fun setShowHeatmap(value: Boolean)
    suspend fun setShowGoals(value: Boolean)
}
