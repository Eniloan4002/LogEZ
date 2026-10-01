package com.enil.logez.feature.workout.session

import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.audio.WorkoutAudioPlayer
import com.enil.logez.feature.workout.audio.WorkoutHapticsPlayer
import kotlinx.coroutines.flow.first

/**
 * What happens when a set countdown reaches 0:00, apart from posting the heads-up (which needs the
 * Service's notification APIs, so the caller passes it as [announce]): the full time is written
 * into the set's TIME (the set is NOT checked), then the timer sound and the rest-end double buzz.
 *
 * It takes the [InlineTimerLog] that [WorkoutSessionController.finishCountdown] returned, rather than
 * listening on the controller's countdownFinished flow: that flow has no replay, so a countdown that
 * was already past its deadline when the Service started could finish before anything subscribed and
 * lose the write for good. Plain class with injected collaborators so it is unit-tested with fakes.
 */
class CountdownFinisher(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val audioPlayer: WorkoutAudioPlayer,
    private val hapticsPlayer: WorkoutHapticsPlayer,
) {
    suspend fun finish(log: InlineTimerLog, announce: suspend (InlineTimerLog) -> Unit) {
        workoutRepository.updateWorkoutSetDuration(log.setId, log.seconds)
        val settings = settingsRepository.settings.first()
        audioPlayer.playTimerSound(settings.timerSound, settings.timerVolume)
        hapticsPlayer.vibrateRestEnd()
        announce(log)
    }
}
