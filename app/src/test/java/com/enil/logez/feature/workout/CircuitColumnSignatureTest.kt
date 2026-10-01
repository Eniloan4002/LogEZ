package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.TimerMode
import com.enil.logez.core.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A circuit round shares one header only while its exercises share one column set, the timer column and its mode included. */
class CircuitColumnSignatureTest {
    private fun plank(timerMode: String? = null) = WorkoutExerciseUiModel(
        id = "we-plank", exerciseId = "ex-plank", exerciseName = "Plank", exerciseType = ExerciseType.DURATION, timerMode = timerMode,
    )

    private fun wallSit(timerMode: String? = null) = WorkoutExerciseUiModel(
        id = "we-wall", exerciseId = "ex-wall", exerciseName = "Wall Sit", exerciseType = ExerciseType.DURATION, timerMode = timerMode,
    )

    private fun sig(e: WorkoutExerciseUiModel, timerOn: Boolean = true) = columnSignature(e, plateCalculatorEnabled = true, inlineTimerEnabled = timerOn)

    @Test
    fun `two stopwatch stations share one header, with the stopwatch glyph`() {
        assertEquals(sig(plank()), sig(wallSit()))
        assertEquals(TimerMode.STOPWATCH, sig(plank()).timerMode)
    }

    @Test
    fun `a stopwatch and a countdown in one round fall back to one header per exercise`() {
        assertNotEquals(sig(plank(timerMode = "COUNTDOWN")), sig(wallSit()))
        assertEquals(TimerMode.COUNTDOWN, sig(plank(timerMode = "COUNTDOWN")).timerMode)
    }

    @Test
    fun `two countdown stations share one header`() {
        assertEquals(sig(plank(timerMode = "COUNTDOWN")), sig(wallSit(timerMode = "COUNTDOWN")))
    }

    @Test
    fun `with the Inline timer off there is no timer column, whatever mode was chosen`() {
        assertEquals(null, sig(plank(timerMode = "COUNTDOWN"), timerOn = false).timerMode)
        assertEquals(sig(plank(timerMode = "COUNTDOWN"), timerOn = false), sig(wallSit(), timerOn = false))
    }

    @Test
    fun `a mode this app does not know reads as a stopwatch, so it never splits a round on its own`() {
        assertEquals(sig(plank(timerMode = "INTERVALS")), sig(wallSit()))
    }
}
