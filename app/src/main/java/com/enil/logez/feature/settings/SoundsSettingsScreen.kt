package com.enil.logez.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.domain.model.VolumeLevel
import com.enil.logez.feature.workout.audio.WorkoutAudioPlayer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The audio player is a platform side-effect component that must never enter a ViewModel (spine
 * testing rule — see [WorkoutAudioPlayer]'s doc), so the preview taps reach the singleton through
 * an entry point from the UI layer instead. Never released here: the same instance serves the
 * live workout session service.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SettingsAudioEntryPoint {
    fun workoutAudioPlayer(): WorkoutAudioPlayer
}

/** Which selection dialog is open, if any. */
private enum class SoundsDialog { TIMER_SOUND, TIMER_VOLUME, SET_COMPLETE_VOLUME, PR_VOLUME }

/** The audio layer ships exactly five timer tones (SoundPoolWorkoutAudioPlayer ids 1..5). */
private val TIMER_SOUND_IDS = (1..5).toList()

/**
 * M16 Sounds sub-page: timer tone selection plus the three volume rows. Selecting a tone or a
 * volume level plays a preview through the same player the live session uses, so what you hear
 * here is exactly what the logger will play.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var openDialog by remember { mutableStateOf<SoundsDialog?>(null) }

    val context = LocalContext.current
    val audioPlayer = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsAudioEntryPoint::class.java)
            .workoutAudioPlayer()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle(stringResource(R.string.settings_sounds_row)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "timer_sound") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_timer_sound),
                    subtitle = stringResource(R.string.settings_timer_sound_subtitle),
                    value = stringResource(R.string.settings_timer_sound_option, settings.timerSound),
                    onClick = { openDialog = SoundsDialog.TIMER_SOUND },
                )
            }
            item(key = "timer_volume") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_timer_volume),
                    value = volumeLabel(settings.timerVolume),
                    onClick = { openDialog = SoundsDialog.TIMER_VOLUME },
                )
            }
            item(key = "set_complete_volume") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_set_complete_volume),
                    value = volumeLabel(settings.setCompleteVolume),
                    onClick = { openDialog = SoundsDialog.SET_COMPLETE_VOLUME },
                )
            }
            item(key = "pr_volume") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_pr_volume),
                    value = volumeLabel(settings.prVolume),
                    onClick = { openDialog = SoundsDialog.PR_VOLUME },
                )
            }
        }
    }

    when (openDialog) {
        SoundsDialog.TIMER_SOUND -> SettingsRadioDialog(
            title = stringResource(R.string.settings_timer_sound),
            options = TIMER_SOUND_IDS,
            selected = settings.timerSound,
            optionLabel = { stringResource(R.string.settings_timer_sound_option, it) },
            onSelect = { soundId ->
                viewModel.setTimerSound(soundId)
                // Preview at the configured timer volume; if that is Off, preview at Normal so
                // the pick is still audible — the honest alternative (silence) reads as broken.
                val previewVolume = if (settings.timerVolume == VolumeLevel.OFF) VolumeLevel.NORMAL else settings.timerVolume
                audioPlayer.playTimerSound(soundId, previewVolume)
            },
            onDismiss = { openDialog = null },
        )
        SoundsDialog.TIMER_VOLUME -> SettingsRadioDialog(
            title = stringResource(R.string.settings_timer_volume),
            options = VolumeLevel.entries,
            selected = settings.timerVolume,
            optionLabel = { volumeLabel(it) },
            onSelect = { volume ->
                viewModel.setTimerVolume(volume)
                audioPlayer.playTimerSound(settings.timerSound, volume)
            },
            onDismiss = { openDialog = null },
        )
        SoundsDialog.SET_COMPLETE_VOLUME -> SettingsRadioDialog(
            title = stringResource(R.string.settings_set_complete_volume),
            options = VolumeLevel.entries,
            selected = settings.setCompleteVolume,
            optionLabel = { volumeLabel(it) },
            onSelect = { volume ->
                viewModel.setSetCompleteVolume(volume)
                audioPlayer.playSetCompleteSound(volume)
            },
            onDismiss = { openDialog = null },
        )
        SoundsDialog.PR_VOLUME -> SettingsRadioDialog(
            title = stringResource(R.string.settings_pr_volume),
            options = VolumeLevel.entries,
            selected = settings.prVolume,
            optionLabel = { volumeLabel(it) },
            onSelect = { volume ->
                viewModel.setPrVolume(volume)
                audioPlayer.playPrFanfare(volume)
            },
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun volumeLabel(volume: VolumeLevel): String = when (volume) {
    VolumeLevel.OFF -> stringResource(R.string.settings_volume_off)
    VolumeLevel.LOW -> stringResource(R.string.settings_volume_low)
    VolumeLevel.NORMAL -> stringResource(R.string.settings_volume_normal)
    VolumeLevel.HIGH -> stringResource(R.string.settings_volume_high)
}
