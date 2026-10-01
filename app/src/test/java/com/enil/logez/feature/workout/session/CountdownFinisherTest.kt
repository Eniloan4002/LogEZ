package com.enil.logez.feature.workout.session

import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.feature.workout.audio.WorkoutAudioPlayer
import com.enil.logez.feature.workout.audio.WorkoutHapticsPlayer
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** What happens at 0:00: the full time is written (set not checked), then sound, buzz and the heads-up, in that order. */
class CountdownFinisherTest {
    private class RecordingAudio : WorkoutAudioPlayer {
        val timerSounds = mutableListOf<Pair<Int, Float>>()
        override fun playTimerSound(soundId: Int, volume: Float) { timerSounds += soundId to volume }
        override fun playSetCompleteSound(volume: Float) = Unit
        override fun playPrFanfare(volume: Float) = Unit
        override fun release() = Unit
    }

    private class RecordingHaptics : WorkoutHapticsPlayer {
        var restEndBuzzes = 0
        override fun vibrateRestEnd() { restEndBuzzes++ }
        override fun vibrateSetComplete() = Unit
    }

    private fun aSet(id: String, seconds: Int?) = WorkoutSetEntity(
        id = id, workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null,
        durationSeconds = seconds, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null,
    )

    @Test
    fun `finishing writes the full time once, keeps the set unchecked, plays the timer sound and buzz, then announces`() = runTest {
        val workouts = FakeWorkoutRepository(sets = listOf(aSet("s1", 38)))
        val audio = RecordingAudio()
        val haptics = RecordingHaptics()
        val announced = mutableListOf<InlineTimerLog>()
        val finisher = CountdownFinisher(workouts, FakeSettingsRepository(UserSettings(timerSound = 3, timerVolume = 0.5f)), audio, haptics)

        // The set held 38 s of a 90 s countdown that nobody stopped: 0:00 writes the full 90.
        finisher.finish(InlineTimerLog("we1", "s1", 90)) { announced += it }

        val set = workouts.getSetsForWorkoutExercise("we1").single()
        assertEquals(90, set.durationSeconds)
        assertEquals(false, set.isCompleted)
        assertEquals(listOf(3 to 0.5f), audio.timerSounds)
        assertEquals(1, haptics.restEndBuzzes)
        assertEquals(listOf(InlineTimerLog("we1", "s1", 90)), announced)
    }

    @Test
    fun `a countdown already past its deadline when the service starts still writes and alerts, with no flow subscription needed`() = runTest {
        // Rehydrate of an expired countdown, then the watcher fires at once: the log comes straight
        // from finishCountdown() into the finisher.
        val repo = com.enil.logez.fakes.FakeActiveSessionRepository()
        val clock = com.enil.logez.fakes.FakeClock(currentMillis = 1_000_000L)
        val elapsed = com.enil.logez.fakes.FakeElapsedRealtimeClock(currentMillis = 50_000L)
        val first = WorkoutSessionController(repo, clock, elapsed, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher()))
        first.startSession("w1")
        first.startInlineTimer("we1", "s1", com.enil.logez.core.domain.model.TimerMode.COUNTDOWN, 60)
        clock.currentMillis = 1_900_000L
        elapsed.currentMillis = 950_000L
        val second = WorkoutSessionController(repo, clock, elapsed, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher()))
        second.rehydrate()

        val workouts = FakeWorkoutRepository(sets = listOf(aSet("s1", 60)))
        val audio = RecordingAudio()
        val haptics = RecordingHaptics()
        var announced = 0
        val finisher = CountdownFinisher(workouts, FakeSettingsRepository(), audio, haptics)

        second.finishCountdown()?.let { finisher.finish(it) { announced++ } }

        assertEquals(60, workouts.getSetsForWorkoutExercise("we1").single().durationSeconds)
        assertEquals(1, audio.timerSounds.size)
        assertEquals(1, haptics.restEndBuzzes)
        assertEquals(1, announced)
    }
}
