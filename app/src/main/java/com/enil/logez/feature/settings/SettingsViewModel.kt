package com.enil.logez.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.VolumeLevel
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * M16 Settings screen — a thin write-through layer over [SettingsRepository]: the current
 * [UserSettings] as observable state plus one persist function per editable row. Every change
 * persists instantly (no Save button; back = done), so there is deliberately no business logic,
 * no draft state, and no validation here.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    // Preferences
    fun setWeightUnit(value: WeightUnit) = write { setWeightUnit(value) }
    fun setDistanceUnit(value: DistanceUnit) = write { setDistanceUnit(value) }
    fun setFirstDayOfWeek(value: DayOfWeek) = write { setFirstDayOfWeek(value) }

    // Workouts
    fun setDefaultRestTimerSeconds(value: Int) = write { setDefaultRestTimerSeconds(value) }
    fun setPreviousValuesMode(value: PreviousValuesMode) = write { setPreviousValuesMode(value) }
    fun setKeepAwake(value: Boolean) = write { setKeepAwake(value) }
    fun setSmartSupersetScrolling(value: Boolean) = write { setSmartSupersetScrolling(value) }
    fun setInlineTimerEnabled(value: Boolean) = write { setInlineTimerEnabled(value) }
    fun setLivePrNotificationEnabled(value: Boolean) = write { setLivePrNotificationEnabled(value) }
    fun setRpeTrackingEnabled(value: Boolean) = write { setRpeTrackingEnabled(value) }
    fun setIncludeWarmupsInStats(value: Boolean) = write { setIncludeWarmupsInStats(value) }

    // Calculators
    fun setPlateCalculatorEnabled(value: Boolean) = write { setPlateCalculatorEnabled(value) }
    fun setWarmupCalculatorEnabled(value: Boolean) = write { setWarmupCalculatorEnabled(value) }

    // Sounds
    fun setTimerSound(value: Int) = write { setTimerSound(value) }
    fun setTimerVolume(value: VolumeLevel) = write { setTimerVolume(value) }
    fun setSetCompleteVolume(value: VolumeLevel) = write { setSetCompleteVolume(value) }
    fun setPrVolume(value: VolumeLevel) = write { setPrVolume(value) }

    private fun write(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }
}
