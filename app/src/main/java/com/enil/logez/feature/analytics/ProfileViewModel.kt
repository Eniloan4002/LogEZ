package com.enil.logez.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the temporary RPE testing toggle on the Profile tab (see [[decisions]] 2026-08-24 — no
 * Settings screen exists yet to host `rpeTrackingEnabled`, so this stands in until M7 builds one).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val rpeTrackingEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.rpeTrackingEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setRpeTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRpeTrackingEnabled(enabled) }
    }
}
