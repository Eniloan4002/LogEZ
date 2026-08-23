package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enil.logez.core.common.ThemeMode
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.VolumeLevel
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.defaultPlateEquipment
import com.enil.logez.core.domain.model.defaultWarmupMethod
import com.enil.logez.core.domain.repository.SettingsRepository
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** PHASE2_PLAN.md §5.2 Settings tree — every key/default exactly as specified there. */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("themeMode")
        val WEIGHT_UNIT = stringPreferencesKey("weightUnit")
        val DISTANCE_UNIT = stringPreferencesKey("distanceUnit")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("firstDayOfWeek")
        val PER_EXERCISE_UNIT_OVERRIDES = stringPreferencesKey("perExerciseUnitOverrides")

        val DEFAULT_REST_TIMER_SECONDS = intPreferencesKey("defaultRestTimerSeconds")
        val TIMER_SOUND = intPreferencesKey("timerSound")
        val TIMER_VOLUME = stringPreferencesKey("timerVolume")
        val SET_COMPLETE_VOLUME = stringPreferencesKey("setCompleteVolume")
        val PR_VOLUME = stringPreferencesKey("prVolume")
        val PREVIOUS_VALUES_MODE = stringPreferencesKey("previousValuesMode")
        val WARMUP_CALCULATOR_ENABLED = booleanPreferencesKey("warmupCalculatorEnabled")
        val WARMUP_METHOD = stringPreferencesKey("warmupMethod")
        val INCLUDE_WARMUPS_IN_STATS = booleanPreferencesKey("includeWarmupsInStats")
        val KEEP_AWAKE = booleanPreferencesKey("keepAwake")
        val PLATE_CALCULATOR_ENABLED = booleanPreferencesKey("plateCalculatorEnabled")
        val PLATE_EQUIPMENT = stringPreferencesKey("plateEquipment")
        val RPE_TRACKING_ENABLED = booleanPreferencesKey("rpeTrackingEnabled")
        val SMART_SUPERSET_SCROLLING = booleanPreferencesKey("smartSupersetScrolling")
        val INLINE_TIMER_ENABLED = booleanPreferencesKey("inlineTimerEnabled")
        val LIVE_PR_NOTIFICATION_ENABLED = booleanPreferencesKey("livePrNotificationEnabled")
    }

    private val json = Json { ignoreUnknownKeys = true }

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: defaults.themeMode,
            weightUnit = prefs[Keys.WEIGHT_UNIT]?.let { WeightUnit.valueOf(it) } ?: defaults.weightUnit,
            distanceUnit = prefs[Keys.DISTANCE_UNIT]?.let { DistanceUnit.valueOf(it) } ?: defaults.distanceUnit,
            firstDayOfWeek = prefs[Keys.FIRST_DAY_OF_WEEK]?.let { DayOfWeek.valueOf(it) } ?: defaults.firstDayOfWeek,
            perExerciseUnitOverrides = prefs[Keys.PER_EXERCISE_UNIT_OVERRIDES]?.let {
                json.decodeFromString<Map<String, WeightUnit>>(it)
            } ?: defaults.perExerciseUnitOverrides,
            defaultRestTimerSeconds = prefs[Keys.DEFAULT_REST_TIMER_SECONDS] ?: defaults.defaultRestTimerSeconds,
            timerSound = prefs[Keys.TIMER_SOUND] ?: defaults.timerSound,
            timerVolume = prefs[Keys.TIMER_VOLUME]?.let { VolumeLevel.valueOf(it) } ?: defaults.timerVolume,
            setCompleteVolume = prefs[Keys.SET_COMPLETE_VOLUME]?.let { VolumeLevel.valueOf(it) } ?: defaults.setCompleteVolume,
            prVolume = prefs[Keys.PR_VOLUME]?.let { VolumeLevel.valueOf(it) } ?: defaults.prVolume,
            previousValuesMode = prefs[Keys.PREVIOUS_VALUES_MODE]?.let { PreviousValuesMode.valueOf(it) } ?: defaults.previousValuesMode,
            warmupCalculatorEnabled = prefs[Keys.WARMUP_CALCULATOR_ENABLED] ?: defaults.warmupCalculatorEnabled,
            warmupMethod = prefs[Keys.WARMUP_METHOD]?.let { json.decodeFromString<List<WarmupStep>>(it) } ?: defaultWarmupMethod,
            includeWarmupsInStats = prefs[Keys.INCLUDE_WARMUPS_IN_STATS] ?: defaults.includeWarmupsInStats,
            keepAwake = prefs[Keys.KEEP_AWAKE] ?: defaults.keepAwake,
            plateCalculatorEnabled = prefs[Keys.PLATE_CALCULATOR_ENABLED] ?: defaults.plateCalculatorEnabled,
            plateEquipment = prefs[Keys.PLATE_EQUIPMENT]?.let { json.decodeFromString<PlateEquipment>(it) } ?: defaultPlateEquipment,
            rpeTrackingEnabled = prefs[Keys.RPE_TRACKING_ENABLED] ?: defaults.rpeTrackingEnabled,
            smartSupersetScrolling = prefs[Keys.SMART_SUPERSET_SCROLLING] ?: defaults.smartSupersetScrolling,
            inlineTimerEnabled = prefs[Keys.INLINE_TIMER_ENABLED] ?: defaults.inlineTimerEnabled,
            livePrNotificationEnabled = prefs[Keys.LIVE_PR_NOTIFICATION_ENABLED] ?: defaults.livePrNotificationEnabled,
        )
    }

    override suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }
    override suspend fun setWeightUnit(value: WeightUnit) = edit { it[Keys.WEIGHT_UNIT] = value.name }
    override suspend fun setDistanceUnit(value: DistanceUnit) = edit { it[Keys.DISTANCE_UNIT] = value.name }
    override suspend fun setFirstDayOfWeek(value: DayOfWeek) = edit { it[Keys.FIRST_DAY_OF_WEEK] = value.name }

    override suspend fun setPerExerciseUnitOverride(exerciseId: String, unit: WeightUnit?) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PER_EXERCISE_UNIT_OVERRIDES]?.let { json.decodeFromString<Map<String, WeightUnit>>(it) } ?: emptyMap()
            val updated = if (unit == null) current - exerciseId else current + (exerciseId to unit)
            prefs[Keys.PER_EXERCISE_UNIT_OVERRIDES] = json.encodeToString(updated)
        }
    }

    override suspend fun setDefaultRestTimerSeconds(value: Int) = edit { it[Keys.DEFAULT_REST_TIMER_SECONDS] = value }
    override suspend fun setTimerSound(value: Int) = edit { it[Keys.TIMER_SOUND] = value }
    override suspend fun setTimerVolume(value: VolumeLevel) = edit { it[Keys.TIMER_VOLUME] = value.name }
    override suspend fun setSetCompleteVolume(value: VolumeLevel) = edit { it[Keys.SET_COMPLETE_VOLUME] = value.name }
    override suspend fun setPrVolume(value: VolumeLevel) = edit { it[Keys.PR_VOLUME] = value.name }
    override suspend fun setPreviousValuesMode(value: PreviousValuesMode) = edit { it[Keys.PREVIOUS_VALUES_MODE] = value.name }
    override suspend fun setWarmupCalculatorEnabled(value: Boolean) = edit { it[Keys.WARMUP_CALCULATOR_ENABLED] = value }
    override suspend fun setWarmupMethod(value: List<WarmupStep>) = edit { it[Keys.WARMUP_METHOD] = json.encodeToString(value) }
    override suspend fun setIncludeWarmupsInStats(value: Boolean) = edit { it[Keys.INCLUDE_WARMUPS_IN_STATS] = value }
    override suspend fun setKeepAwake(value: Boolean) = edit { it[Keys.KEEP_AWAKE] = value }
    override suspend fun setPlateCalculatorEnabled(value: Boolean) = edit { it[Keys.PLATE_CALCULATOR_ENABLED] = value }
    override suspend fun setPlateEquipment(value: PlateEquipment) = edit { it[Keys.PLATE_EQUIPMENT] = json.encodeToString(value) }
    override suspend fun setRpeTrackingEnabled(value: Boolean) = edit { it[Keys.RPE_TRACKING_ENABLED] = value }
    override suspend fun setSmartSupersetScrolling(value: Boolean) = edit { it[Keys.SMART_SUPERSET_SCROLLING] = value }
    override suspend fun setInlineTimerEnabled(value: Boolean) = edit { it[Keys.INLINE_TIMER_ENABLED] = value }
    override suspend fun setLivePrNotificationEnabled(value: Boolean) = edit { it[Keys.LIVE_PR_NOTIFICATION_ENABLED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
