package com.enil.logez.feature.analytics

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Noon UTC 2026-08-22 (a Saturday) — the §8.7 vector's `today`. */
    private val nowMillis = 1_787_400_000_000L

    private fun millisOn(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L

    private fun completedWorkout(id: String, date: String) = WorkoutEntity(
        id = id, routineId = null, title = "W$id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = millisOn(date), endedAt = millisOn(date) + 3_600_000L, durationSeconds = 3600,
        createdAt = millisOn(date), updatedAt = millisOn(date),
    )

    private fun newViewModel(
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
    ): ProfileViewModel {
        return ProfileViewModel(workoutRepo, exerciseRepo, settingsRepo, FakeClock(currentMillis = nowMillis))
            // The screen's RefreshOnResume drives the first load (no init load) — mirror it here.
            .also { it.refresh() }
    }

    // --- §5.2 Profile headline stats ---
    // (The temporary RPE toggle tests moved to SettingsViewModelTest with the M16 relocation.)

    @Test
    fun `a fresh install shows honest zeros`() = runTest {
        val vm = newViewModel()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.workoutCount)
        assertEquals(0, state.streakWeeks)
        assertEquals(0, state.last7Count)
        assertTrue(state.last7Heat.isEmpty())
    }

    @Test
    fun `workout count and the weekly streak come from completed workouts`() = runTest {
        // §8.7 vector row 1: workouts on 08-18, 08-11, 08-04 with MONDAY weeks = streak 3.
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(
                    completedWorkout("w1", "2026-08-18"),
                    completedWorkout("w2", "2026-08-11"),
                    completedWorkout("w3", "2026-08-04"),
                ),
            ),
        )
        val state = vm.uiState.value
        assertEquals(3, state.workoutCount)
        assertEquals(3, state.streakWeeks)
        // Last-7-days window [08-16, 08-22] holds only the 08-18 workout.
        assertEquals(1, state.last7Count)
    }

    @Test
    fun `quick charts carry a 3-month weekly series per metric`() = runTest {
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(workouts = listOf(completedWorkout("w1", "2026-08-18"))),
        )
        val frequency = vm.uiState.value.quickCharts.getValue(TrainingMetric.FREQUENCY)
        assertEquals(1.0, frequency.last().value, 1e-9)
        assertEquals(TrainingMetric.entries.size, vm.uiState.value.quickCharts.size)
    }
}
