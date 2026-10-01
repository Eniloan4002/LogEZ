package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.feature.workout.session.TimedSetNotificationContent
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ongoing notification follows the first exercise with an open set, except while a set timer
 * runs: then it follows the timed set, wherever it is in the workout (mockup N0 against N1, Bench
 * Press above Plank).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutNotificationContentBuilderTest {
    private val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
    private val builder = WorkoutNotificationContentBuilder(controller)

    private val bench = WorkoutExerciseUiModel(
        id = "we-bench", exerciseId = "ex-bench", exerciseName = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS,
        sets = listOf(
            WorkoutSetUiModel(id = "b1", weightKg = 80.0, reps = 8, isCompleted = true),
            WorkoutSetUiModel(id = "b2", weightKg = 80.0, reps = 8, isCompleted = true),
            WorkoutSetUiModel(id = "b3", weightKg = 80.0, reps = 8),
        ),
    )
    private val plank = WorkoutExerciseUiModel(
        id = "we-plank", exerciseId = "ex-plank", exerciseName = "Plank", exerciseType = ExerciseType.DURATION, timerMode = "COUNTDOWN",
        sets = listOf(
            WorkoutSetUiModel(id = "p1", durationSeconds = 60, isCompleted = true),
            WorkoutSetUiModel(id = "p2", durationSeconds = 60),
            WorkoutSetUiModel(id = "p3", durationSeconds = 60),
        ),
    )

    @Test
    fun `with no timer running it follows the first exercise with an open set`() {
        builder.push(listOf(bench, plank), restingExerciseId = null, weightUnit = WeightUnit.KG)

        val content = controller.state.value.notificationContent!!
        assertEquals("Bench Press (Barbell) · set 3 of 3", content.title)
        assertEquals("we-bench", content.actionableExerciseId)
        assertEquals("b3", content.actionableSetId)
        assertNull(content.timedSet)
    }

    @Test
    fun `while a set timer runs it names the timed set, set 2 of 3 of the Plank below the Bench`() {
        builder.push(listOf(bench, plank), restingExerciseId = null, weightUnit = WeightUnit.KG, timedSet = "we-plank" to "p2")

        assertEquals(
            TimedSetNotificationContent(exerciseId = "we-plank", setId = "p2", title = "Plank · set 2 of 3", label = "Plank · set 2"),
            controller.state.value.notificationContent!!.timedSet,
        )
    }

    @Test
    fun `a timed set that is no longer in the workout falls back to the ordinary content`() {
        builder.push(listOf(bench, plank), restingExerciseId = null, weightUnit = WeightUnit.KG, timedSet = "we-plank" to "gone")

        assertNull(controller.state.value.notificationContent!!.timedSet)
    }
}
