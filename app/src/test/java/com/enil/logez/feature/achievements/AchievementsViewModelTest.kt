package com.enil.logez.feature.achievements

import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.Achievement
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWellnessRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P-208: how the screen assembles its inputs -- above all the steps merge and write-back. */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 30)
    private val clock = FakeClock(today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())

    private fun connectedSource(history: List<DailyStepCount>) = FakeHealthMetricsSource(
        availabilityValue = HealthConnectAvailability.Available,
        permissionsGranted = true,
        stepsHistory = history,
    )

    @Test
    fun `steps merge cache and Health Connect, keeping the larger day`() = runTest {
        val wellness = FakeWellnessRepository(
            listOf(
                DailyWellnessTotal("2026-09-20", steps = 25_000L, caloriesBurned = 2100.0, updatedAt = 1L),
                // Far older than the 400-day read window: only the cache knows it.
                DailyWellnessTotal("2024-05-01", steps = 31_000L, caloriesBurned = null, updatedAt = 1L),
            ),
        )
        val source = connectedSource(
            listOf(DailyStepCount(LocalDate.of(2026, 9, 20), 18_000L), DailyStepCount(LocalDate.of(2026, 9, 21), 22_000L)),
        )
        val state = viewModel(wellness = wellness, source = source).uiState.value

        assertTrue(state.stepsConnected)
        val best = state.progress.single { it.achievement == Achievement.STEPS_DAY_40K }.current
        assertEquals(31_000L, best)
        assertTrue(state.progress.single { it.achievement == Achievement.STEPS_DAY_30K }.unlocked)
    }

    @Test
    fun `finished days from Health Connect are written back, calories kept, today left alone`() = runTest {
        val wellness = FakeWellnessRepository(
            listOf(DailyWellnessTotal("2026-09-20", steps = 5_000L, caloriesBurned = 1800.0, updatedAt = 1L)),
        )
        val source = connectedSource(
            listOf(
                DailyStepCount(LocalDate.of(2026, 9, 20), 14_000L),
                DailyStepCount(LocalDate.of(2026, 9, 21), 9_000L),
                DailyStepCount(today, 3_000L),
            ),
        )
        viewModel(wellness = wellness, source = source)

        val sep20 = wellness.getByDate("2026-09-20")!!
        assertEquals(14_000L, sep20.steps)
        assertEquals(1800.0, sep20.caloriesBurned!!, 1e-9)
        assertEquals(9_000L, wellness.getByDate("2026-09-21")!!.steps)
        // Today belongs to the Workout tab / Profile refresh, which also writes calories.
        assertNull(wellness.getByDate("2026-09-30"))
    }

    @Test
    fun `history is read in 90-day windows covering the last 400 days`() = runTest {
        val source = connectedSource(emptyList())
        viewModel(source = source)

        assertEquals(5, source.queriedStepsRanges.size)
        assertEquals(LocalDate.of(2025, 8, 27), source.queriedStepsRanges.first().first)
        assertEquals(today, source.queriedStepsRanges.last().second)
    }

    @Test
    fun `without Health Connect, only the cache counts and nothing is queried`() = runTest {
        val wellness = FakeWellnessRepository(listOf(DailyWellnessTotal("2026-09-01", steps = 21_000L, caloriesBurned = null, updatedAt = 1L)))
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable)
        val state = viewModel(wellness = wellness, source = source).uiState.value

        assertFalse(state.stepsConnected)
        assertTrue(source.queriedStepsRanges.isEmpty())
        assertTrue(state.progress.single { it.achievement == Achievement.STEPS_DAY_20K }.unlocked)
    }

    @Test
    fun `workouts, records and trained muscle groups feed their achievements`() = runTest {
        val workouts = FakeWorkoutRepository(
            workouts = listOf(completed("w1", LocalDate.of(2026, 9, 28)), completed("w2", LocalDate.of(2026, 9, 29))),
            exercises = listOf(workoutExercise("we1", "w1", "bench"), workoutExercise("we2", "w2", "squat")),
            sets = listOf(aSet("s1", "we1"), aSet("s2", "we2")),
        )
        val exercises = FakeExerciseRepository(listOf(exercise("bench", MuscleGroup.CHEST), exercise("squat", MuscleGroup.QUADRICEPS)))
        // Two record types on bench (one set makes several) still count bench once.
        val records = FakePersonalRecordsRepository(
            listOf(record("r1", "bench", PrType.HEAVIEST_WEIGHT), record("r2", "bench", PrType.BEST_1RM), record("r3", "squat", PrType.HEAVIEST_WEIGHT)),
        )
        val state = viewModel(workouts = workouts, exercises = exercises, records = records).uiState.value

        fun current(a: Achievement) = state.progress.single { it.achievement == a }.current
        assertEquals(2L, current(Achievement.WORKOUTS_10))
        assertEquals(2L, current(Achievement.DAY_STREAK_7))
        assertEquals(2L, current(Achievement.PRS_10))
        // Chest and quads, both in the week of Monday 28 Sep.
        assertEquals(2L, current(Achievement.FULL_BODY_WEEK))
    }

    // --- fixtures ---

    private fun viewModel(
        workouts: FakeWorkoutRepository = FakeWorkoutRepository(),
        exercises: FakeExerciseRepository = FakeExerciseRepository(),
        records: FakePersonalRecordsRepository = FakePersonalRecordsRepository(),
        wellness: FakeWellnessRepository = FakeWellnessRepository(),
        source: FakeHealthMetricsSource = FakeHealthMetricsSource(),
    ) = AchievementsViewModel(
        workoutRepository = workouts,
        exerciseRepository = exercises,
        personalRecordsRepository = records,
        wellnessRepository = wellness,
        healthMetricsSource = source,
        settingsRepository = FakeSettingsRepository(),
        clock = clock,
    )

    private fun completed(id: String, date: LocalDate): WorkoutEntity {
        val at = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        return WorkoutEntity(
            id = id, routineId = null, title = id, notes = null, status = WorkoutStatus.COMPLETED,
            startedAt = at, endedAt = at + 3_600_000L, durationSeconds = 3600, createdAt = at, updatedAt = at,
        )
    }

    private fun workoutExercise(id: String, workoutId: String, exerciseId: String) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun aSet(id: String, workoutExerciseId: String) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = 0, setType = SetType.NORMAL,
        weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null,
        customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun exercise(id: String, muscle: MuscleGroup) = Exercise(
        id = id, name = id, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = muscle,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun record(id: String, exerciseId: String, type: PrType) = PersonalRecordEntity(
        id = id, exerciseId = exerciseId, workoutId = "w1", workoutSetId = null, prType = type,
        value = 60.0, achievedAt = 1_000L,
    )
}
