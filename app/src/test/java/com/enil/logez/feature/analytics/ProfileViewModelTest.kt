package com.enil.logez.feature.analytics

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWellnessRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.wellness.HealthConnectAvailability
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
        healthMetricsSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
        wellnessRepo: FakeWellnessRepository = FakeWellnessRepository(),
    ): ProfileViewModel {
        return ProfileViewModel(
            workoutRepo, exerciseRepo, settingsRepo, healthMetricsSource, wellnessRepo, FakeClock(currentMillis = nowMillis),
        )
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

    // --- M21e wellness (steps only) ---

    @Test
    fun `wellness section reports unavailable when Health Connect isn't usable on this device`() = runTest {
        val vm = newViewModel(healthMetricsSource = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable))
        assertEquals(HealthConnectAvailability.Unavailable, vm.uiState.value.wellnessAvailability)
        assertFalse(vm.uiState.value.hasWellnessPermissions)
    }

    @Test
    fun `wellness section reports no permissions yet when Health Connect is available but ungranted`() = runTest {
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available, permissionsGranted = false),
        )
        val state = vm.uiState.value
        assertEquals(HealthConnectAvailability.Available, state.wellnessAvailability)
        assertFalse(state.hasWellnessPermissions)
        assertEquals(null, state.todaySteps)
    }

    @Test
    fun `today's steps load and persist to the wellness repository once permission is granted`() = runTest {
        val wellnessRepo = FakeWellnessRepository()
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = HealthConnectAvailability.Available,
                permissionsGranted = true,
                totals = com.enil.logez.feature.wellness.DailyTotals(steps = 8_432L, caloriesBurned = null),
            ),
            wellnessRepo = wellnessRepo,
        )

        val state = vm.uiState.value
        assertTrue(state.hasWellnessPermissions)
        assertEquals(8_432L, state.todaySteps)
        assertEquals(null, state.todayCaloriesBurned)
        assertEquals(1, wellnessRepo.all.size)
        assertEquals(8_432L, wellnessRepo.all.single().steps)
    }

    @Test
    fun `today's calories load and persist alongside steps once permission is granted`() = runTest {
        val wellnessRepo = FakeWellnessRepository()
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = HealthConnectAvailability.Available,
                permissionsGranted = true,
                totals = com.enil.logez.feature.wellness.DailyTotals(steps = 8_432L, caloriesBurned = 1_842.7),
            ),
            wellnessRepo = wellnessRepo,
        )

        val state = vm.uiState.value
        assertEquals(1_842.7, state.todayCaloriesBurned)
        assertEquals(1, wellnessRepo.all.size)
        assertEquals(1_842.7, wellnessRepo.all.single().caloriesBurned)
    }

    @Test
    fun `onWellnessPermissionResult re-refreshes only when the grant actually succeeded`() = runTest {
        val healthMetricsSource = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            totals = com.enil.logez.feature.wellness.DailyTotals(steps = 100L, caloriesBurned = null),
        )
        val vm = ProfileViewModel(
            FakeWorkoutRepository(), FakeExerciseRepository(), FakeSettingsRepository(),
            healthMetricsSource, FakeWellnessRepository(), FakeClock(currentMillis = nowMillis),
        )
        assertEquals(true, vm.uiState.value.isLoading) // never refreshed yet -- no init load

        vm.onWellnessPermissionResult(granted = false)
        assertEquals(true, vm.uiState.value.isLoading) // a denial must not trigger a load

        vm.onWellnessPermissionResult(granted = true)
        assertEquals(100L, vm.uiState.value.todaySteps)
    }
}
