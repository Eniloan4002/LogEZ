package com.enil.logez.feature.analytics

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWellnessRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.wellness.HealthConnectAvailability
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
import com.enil.logez.core.wellness.HealthDataType

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

    private fun exercise(id: String, muscle: MuscleGroup) = Exercise(
        id = id, name = id, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = muscle,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun workoutExercise(id: String, workoutId: String, exerciseId: String) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = 0, supersetGroup = null,
        restTimerSeconds = null, notes = null,
    )

    private fun completedSet(id: String, weId: String) = WorkoutSetEntity(
        id = id, workoutExerciseId = weId, orderIndex = 0, setType = SetType.NORMAL, weightKg = 20.0, reps = 10,
        durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun newViewModel(
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        healthMetricsSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
        wellnessRepo: FakeWellnessRepository = FakeWellnessRepository(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): ProfileViewModel {
        return ProfileViewModel(
            workoutRepo, exerciseRepo, settingsRepo, healthMetricsSource, wellnessRepo, FakeClock(currentMillis = nowMillis), savedState,
        )
            // The screen's RefreshOnResume drives the first load (no init load) — mirror it here.
            .also { it.refresh() }
    }

    // --- §5.2 Profile headline stats ---
    // (The temporary RPE toggle tests moved to SettingsViewModelTest with the M16 relocation.)

    @Test
    fun `muscleDiagramVariant reflects whatever the settings repository is seeded with`() = runTest {
        val vm = newViewModel(settingsRepo = FakeSettingsRepository(UserSettings(muscleDiagramVariant = MuscleDiagramVariant.FEMALE)))
        assertEquals(MuscleDiagramVariant.FEMALE, vm.uiState.value.muscleDiagramVariant)
    }

    @Test
    fun `a fresh install shows honest zeros`() = runTest {
        val vm = newViewModel()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.workoutCount)
        assertEquals(0, state.streakWeeks)
        assertEquals(0, state.streakDays)
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
        // None of 08-18/08-11/08-04 is today (08-22) or yesterday -- day and week streaks are
        // independent figures, and a healthy week streak here comes with zero day streak.
        assertEquals(0, state.streakDays)
        // Last-7-days window [08-16, 08-22] holds only the 08-18 workout.
        assertEquals(1, state.last7Count)
    }

    @Test
    fun `the daily streak counts consecutive days, independent of the weekly streak`() = runTest {
        // today is 08-22 -- three consecutive days ending today all land in the SAME Monday-
        // anchored week (08-17 to 08-23), so streakDays=3 but streakWeeks is still just 1.
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(
                    completedWorkout("w1", "2026-08-22"),
                    completedWorkout("w2", "2026-08-21"),
                    completedWorkout("w3", "2026-08-20"),
                ),
            ),
        )
        val state = vm.uiState.value
        assertEquals(3, state.streakDays)
        assertEquals(1, state.streakWeeks)
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

    @Test
    fun `the muscle heat-map normalizes set counts within the last 7 days and excludes older workouts`() = runTest {
        // today is 08-22 -- last7Window is [08-16, 08-22]. w1/w2 fall inside it (3 CHEST sets,
        // 1 UPPER_BACK set); w3 sits on 08-01, well outside it, and its 5 CHEST sets must not
        // count -- otherwise CHEST would wrongly read 8 sets and UPPER_BACK's share would be off.
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(
                    completedWorkout("w1", "2026-08-18"),
                    completedWorkout("w2", "2026-08-19"),
                    completedWorkout("w3", "2026-08-01"),
                ),
                exercises = listOf(
                    workoutExercise("we1", "w1", "ex-bench"),
                    workoutExercise("we2", "w2", "ex-row"),
                    workoutExercise("we3", "w3", "ex-bench"),
                ),
                sets = listOf(
                    completedSet("s1", "we1"), completedSet("s2", "we1"), completedSet("s3", "we1"),
                    completedSet("s4", "we2"),
                    completedSet("s5", "we3"), completedSet("s6", "we3"), completedSet("s7", "we3"),
                    completedSet("s8", "we3"), completedSet("s9", "we3"),
                ),
            ),
            exerciseRepo = FakeExerciseRepository(
                listOf(exercise("ex-bench", MuscleGroup.CHEST), exercise("ex-row", MuscleGroup.UPPER_BACK)),
            ),
        )
        val heat = vm.uiState.value.last7Heat
        assertEquals(1.0f, heat.getValue(MuscleGroup.CHEST), 1e-6f)
        assertEquals(1f / 3f, heat.getValue(MuscleGroup.UPPER_BACK), 1e-6f)
    }

    // --- M21e wellness (steps only) ---

    @Test
    fun `wellness section reports unavailable when Health Connect isn't usable on this device`() = runTest {
        val vm = newViewModel(healthMetricsSource = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable))
        assertEquals(HealthConnectAvailability.Unavailable, vm.uiState.value.wellnessAvailability)
        assertTrue(vm.uiState.value.wellnessGranted.isEmpty())
    }

    @Test
    fun `wellness section reports no permissions yet when Health Connect is available but ungranted`() = runTest {
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available, permissionsGranted = false),
        )
        val state = vm.uiState.value
        assertEquals(HealthConnectAvailability.Available, state.wellnessAvailability)
        assertTrue(state.wellnessGranted.isEmpty())
        assertEquals(null, state.todaySteps)
    }

    @Test
    fun `today's steps load and persist to the wellness repository once permission is granted`() = runTest {
        val wellnessRepo = FakeWellnessRepository()
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = HealthConnectAvailability.Available,
                permissionsGranted = true,
                totals = com.enil.logez.core.wellness.DailyTotals(steps = 8_432L, caloriesBurned = null),
            ),
            wellnessRepo = wellnessRepo,
        )

        val state = vm.uiState.value
        assertEquals(HealthDataType.entries.toSet(), state.wellnessGranted)
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
                totals = com.enil.logez.core.wellness.DailyTotals(steps = 8_432L, caloriesBurned = 1_842.7),
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
            totals = com.enil.logez.core.wellness.DailyTotals(steps = 100L, caloriesBurned = null),
        )
        val vm = ProfileViewModel(
            FakeWorkoutRepository(), FakeExerciseRepository(), FakeSettingsRepository(),
            healthMetricsSource, FakeWellnessRepository(), FakeClock(currentMillis = nowMillis), SavedStateHandle(),
        )
        assertEquals(true, vm.uiState.value.isLoading) // never refreshed yet -- no init load

        vm.onWellnessPermissionResult(anyGranted = false)
        assertEquals(true, vm.uiState.value.isLoading) // a denial must not trigger a load

        vm.onWellnessPermissionResult(anyGranted = true)
        assertEquals(100L, vm.uiState.value.todaySteps)
    }

    // --- Partial Health Connect grants (Play-readiness audit, 2026-09-25) ---

    @Test
    fun `a steps-only grant shows steps and caches them, with no calories and no connect card`() = runTest {
        val wellnessRepo = FakeWellnessRepository()
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = HealthConnectAvailability.Available,
                grantedTypesOverride = setOf(HealthDataType.STEPS),
                totals = com.enil.logez.core.wellness.DailyTotals(steps = 5_000L, caloriesBurned = 900.0),
            ),
            wellnessRepo = wellnessRepo,
        )

        val state = vm.uiState.value
        assertEquals(setOf(HealthDataType.STEPS), state.wellnessGranted)
        assertEquals(5_000L, state.todaySteps)
        assertEquals(null, state.todayCaloriesBurned)
        assertEquals(5_000L, wellnessRepo.all.single().steps)
    }

    @Test
    fun `a calories-only grant shows calories but never caches or shows a step count`() = runTest {
        val wellnessRepo = FakeWellnessRepository()
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = HealthConnectAvailability.Available,
                grantedTypesOverride = setOf(HealthDataType.CALORIES),
                totals = com.enil.logez.core.wellness.DailyTotals(steps = 5_000L, caloriesBurned = 900.0),
            ),
            wellnessRepo = wellnessRepo,
        )

        val state = vm.uiState.value
        assertEquals(null, state.todaySteps)
        assertEquals(900.0, state.todayCaloriesBurned)
        assertTrue(wellnessRepo.all.isEmpty())
    }

    @Test
    fun `a heart-rate-only grant hides the connect card without inventing a today section`() = runTest {
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = HealthConnectAvailability.Available,
                grantedTypesOverride = setOf(HealthDataType.HEART_RATE),
                totals = com.enil.logez.core.wellness.DailyTotals(steps = 5_000L, caloriesBurned = 900.0),
            ),
        )

        val state = vm.uiState.value
        assertEquals(setOf(HealthDataType.HEART_RATE), state.wellnessGranted)
        assertEquals(null, state.todaySteps)
        assertEquals(null, state.todayCaloriesBurned)
    }

    // --- Refusal rule (first-run plan O1f, F6) ---

    @Test
    fun `a full refusal sets the refused state, and a resume with nothing granted keeps it`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available)
        val vm = newViewModel(healthMetricsSource = source)
        assertFalse(vm.uiState.value.wellnessRefused)

        vm.onWellnessPermissionResult(anyGranted = false)
        assertTrue(vm.uiState.value.wellnessRefused)

        vm.refresh() // RefreshOnResume on the way back from Health Connect
        assertTrue(vm.uiState.value.wellnessRefused)
        assertTrue(vm.uiState.value.wellnessGranted.isEmpty())
        assertEquals(0, source.regrantedCallCount)
    }

    @Test
    fun `a refusal is kept across process death through saved state`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available)
        val savedState = SavedStateHandle()
        newViewModel(healthMetricsSource = source, savedState = savedState).onWellnessPermissionResult(anyGranted = false)

        val restored = newViewModel(healthMetricsSource = source, savedState = savedState)

        assertTrue(restored.uiState.value.wellnessRefused)
    }

    @Test
    fun `a grant made in Health Connect's settings clears the refusal on the next refresh`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available)
        val vm = newViewModel(healthMetricsSource = source)
        vm.onWellnessPermissionResult(anyGranted = false)

        source.grantedTypesOverride = setOf(HealthDataType.STEPS)
        vm.refresh()
        assertFalse(vm.uiState.value.wellnessRefused)

        // Cleared, not just hidden behind the grant: withdrawn again, Connect is offered.
        source.grantedTypesOverride = emptySet()
        vm.refresh()
        assertFalse(vm.uiState.value.wellnessRefused)
    }

    @Test
    fun `a Connect that grants something after a refusal clears it`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available)
        val vm = newViewModel(healthMetricsSource = source)
        vm.onWellnessPermissionResult(anyGranted = false)

        source.permissionsGranted = true
        vm.onWellnessPermissionResult(anyGranted = true)

        assertFalse(vm.uiState.value.wellnessRefused)
        assertEquals(1, source.regrantedCallCount)
        assertEquals(HealthDataType.entries.toSet(), vm.uiState.value.wellnessGranted)
    }
}
