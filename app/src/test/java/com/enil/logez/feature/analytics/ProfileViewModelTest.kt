package com.enil.logez.feature.analytics

import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.Achievement
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.model.PrType
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
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
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
        recordsRepo: FakePersonalRecordsRepository = FakePersonalRecordsRepository(),
        measurementRepo: FakeMeasurementRepository = FakeMeasurementRepository(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): ProfileViewModel {
        return ProfileViewModel(
            workoutRepo, exerciseRepo, settingsRepo, healthMetricsSource, wellnessRepo, recordsRepo, measurementRepo,
            FakeClock(currentMillis = nowMillis), savedState,
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

    // --- Profile redesign (2026-10-01): This week, longest streaks, records, achievements, weight, regions ---

    private fun record(id: String, exerciseId: String, date: String) = PersonalRecordEntity(
        id = id, exerciseId = exerciseId, workoutId = "w", workoutSetId = null, prType = PrType.HEAVIEST_WEIGHT,
        value = 100.0, achievedAt = millisOn(date),
    )

    @Test
    fun `before the first load the week is null so the screen can reserve its layout`() = runTest {
        val vm = ProfileViewModel(
            FakeWorkoutRepository(), FakeExerciseRepository(), FakeSettingsRepository(), FakeHealthMetricsSource(),
            FakeWellnessRepository(), FakePersonalRecordsRepository(), FakeMeasurementRepository(),
            FakeClock(currentMillis = nowMillis), SavedStateHandle(),
        )
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(null, vm.uiState.value.week)
    }

    @Test
    fun `this week counts active days, marks each day, and totals volume and sets so far`() = runTest {
        // today is Sat 08-22, the week Mon 08-17 to Sun 08-23. Two sessions on 08-22 are one day.
        // w0 (08-10) is last week and must not count toward this week's totals.
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(
                    completedWorkout("w0", "2026-08-10"),
                    completedWorkout("w1", "2026-08-17"),
                    completedWorkout("w2", "2026-08-20"),
                    completedWorkout("w3", "2026-08-22"),
                    completedWorkout("w4", "2026-08-22"),
                ),
                exercises = listOf(
                    workoutExercise("we0", "w0", "ex-bench"),
                    workoutExercise("we1", "w1", "ex-bench"),
                    workoutExercise("we3", "w3", "ex-bench"),
                ),
                sets = listOf(
                    completedSet("s0", "we0"),
                    completedSet("s1", "we1"), completedSet("s2", "we1"),
                    completedSet("s3", "we3"),
                ),
            ),
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-bench", MuscleGroup.CHEST))),
        )
        val week = vm.uiState.value.week!!
        assertEquals(LocalDate.of(2026, 8, 17), week.start)
        assertEquals(LocalDate.of(2026, 8, 23), week.end)
        assertEquals(3, week.activeDays)
        assertEquals(4, week.targetDays)
        assertEquals(5, week.todayIndex)
        assertEquals(
            listOf(
                WeekDayMark.TRAINED, WeekDayMark.MISSED, WeekDayMark.MISSED, WeekDayMark.TRAINED,
                WeekDayMark.MISSED, WeekDayMark.TRAINED, WeekDayMark.UPCOMING,
            ),
            week.days.map { it.mark },
        )
        // 3 working sets of 20 kg x 10 reps.
        assertEquals(3, week.setsSoFar)
        assertEquals(600.0, week.volumeKgSoFar, 1e-6)
    }

    @Test
    fun `a day with no workout yet today is marked as today, not missed`() = runTest {
        val week = newViewModel().uiState.value.week!!
        assertEquals(0, week.activeDays)
        assertEquals(
            listOf(
                WeekDayMark.MISSED, WeekDayMark.MISSED, WeekDayMark.MISSED, WeekDayMark.MISSED,
                WeekDayMark.MISSED, WeekDayMark.TODAY, WeekDayMark.UPCOMING,
            ),
            week.days.map { it.mark },
        )
    }

    @Test
    fun `records this week counts distinct exercises, not rows, and ignores older weeks`() = runTest {
        val vm = newViewModel(
            recordsRepo = FakePersonalRecordsRepository(
                listOf(
                    record("r1", "ex-bench", "2026-08-18"), record("r2", "ex-bench", "2026-08-18"),
                    record("r3", "ex-row", "2026-08-19"),
                    record("r4", "ex-old", "2026-08-10"),
                ),
            ),
        )
        assertEquals(2, vm.uiState.value.week!!.recordsThisWeek)
    }

    @Test
    fun `the longest streaks and the first workout date come from the whole history`() = runTest {
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(
                    completedWorkout("a", "2026-08-01"), completedWorkout("b", "2026-08-02"),
                    completedWorkout("c", "2026-08-03"), completedWorkout("d", "2026-08-04"),
                    completedWorkout("e", "2026-08-21"), completedWorkout("f", "2026-08-22"),
                ),
            ),
        )
        val state = vm.uiState.value
        assertEquals(2, state.streakDays)
        assertEquals(4, state.longestDayStreak)
        // Monday weeks: 07-27, 08-03 and 08-17. The first two are consecutive; the current run is 1.
        assertEquals(1, state.streakWeeks)
        assertEquals(2, state.longestWeekStreak)
        assertEquals(LocalDate.of(2026, 8, 1), state.firstWorkoutDate)
        assertEquals(6, state.workoutCount)
    }

    @Test
    fun `the achievements headline counts unlocked ones and names the closest locked one`() = runTest {
        val none = newViewModel().uiState.value
        assertEquals(0, none.achievementsUnlocked)
        assertEquals(17, none.achievementsTotal)
        assertEquals(Achievement.FIRST_WORKOUT, none.nextAchievement?.achievement)

        val one = newViewModel(
            workoutRepo = FakeWorkoutRepository(workouts = listOf(completedWorkout("w1", "2026-08-22"))),
        ).uiState.value
        assertEquals(1, one.achievementsUnlocked)
        assertEquals(Achievement.DAY_STREAK_7, one.nextAchievement?.achievement)
    }

    @Test
    fun `the latest weight is the newest entry on or before today, with its date`() = runTest {
        val vm = newViewModel(
            measurementRepo = FakeMeasurementRepository(
                weightsByDate = mapOf("2026-08-10" to 78.4, "2026-08-01" to 80.0, "2026-09-30" to 99.0),
            ),
        )
        assertEquals(LatestWeight(78.4, LocalDate.of(2026, 8, 10)), vm.uiState.value.latestWeight)
        assertEquals(null, newViewModel().uiState.value.latestWeight)
    }

    @Test
    fun `the muscle map counts trained body regions in the last 7 days and names the missing ones`() = runTest {
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(completedWorkout("w1", "2026-08-18"), completedWorkout("w2", "2026-08-19")),
                exercises = listOf(workoutExercise("we1", "w1", "ex-bench"), workoutExercise("we2", "w2", "ex-squat")),
                sets = listOf(completedSet("s1", "we1"), completedSet("s2", "we2")),
            ),
            exerciseRepo = FakeExerciseRepository(
                listOf(exercise("ex-bench", MuscleGroup.CHEST), exercise("ex-squat", MuscleGroup.QUADRICEPS)),
            ),
        )
        val state = vm.uiState.value
        assertEquals(2, state.last7RegionsTrained)
        assertEquals(
            listOf(
                BodyRegion.BACK, BodyRegion.SHOULDERS, BodyRegion.ARMS, BodyRegion.CORE,
                BodyRegion.HAMSTRINGS_GLUTES, BodyRegion.LOWER_LEG,
            ),
            state.last7RegionsMissing,
        )
    }

    @Test
    fun `sets that map to no body region count as sets but train no region`() = runTest {
        // Full-body work in the window: last7SetCount is 1, so the card can say "maps to no region", not "no sets".
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(completedWorkout("w1", "2026-08-20")),
                exercises = listOf(workoutExercise("we1", "w1", "ex-burpee")),
                sets = listOf(completedSet("s1", "we1")),
            ),
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-burpee", MuscleGroup.FULL_BODY))),
        )
        val state = vm.uiState.value
        assertEquals(1, state.last7Count)
        assertEquals(1, state.last7SetCount)
        assertEquals(0, state.last7RegionsTrained)
    }

    @Test
    fun `a window with workouts older than 7 days has no sets in it`() = runTest {
        val vm = newViewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(completedWorkout("w1", "2026-08-01")),
                exercises = listOf(workoutExercise("we1", "w1", "ex-bench")),
                sets = listOf(completedSet("s1", "we1")),
            ),
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-bench", MuscleGroup.CHEST))),
        )
        assertEquals(0, vm.uiState.value.last7SetCount)
        assertEquals(0, vm.uiState.value.last7RegionsTrained)
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
            healthMetricsSource, FakeWellnessRepository(), FakePersonalRecordsRepository(), FakeMeasurementRepository(),
            FakeClock(currentMillis = nowMillis), SavedStateHandle(),
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
