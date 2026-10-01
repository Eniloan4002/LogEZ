package com.enil.logez.feature.analytics

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
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
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The whole Profile tab over a real view model with fakes: what a new user and a first-workout user see. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class ProfileScreenTest {
    @get:Rule val rule = createComposeRule()

    /** Noon UTC 2026-08-22 (a Saturday). */
    private val nowMillis = 1_787_400_000_000L

    private fun millisOn(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L

    private fun viewModel(withWorkout: Boolean): ProfileViewModel {
        val workoutRepo = if (withWorkout) {
            val at = millisOn("2026-08-22")
            FakeWorkoutRepository(
                workouts = listOf(
                    WorkoutEntity(
                        id = "w1", routineId = null, title = "Push", notes = null, status = WorkoutStatus.COMPLETED,
                        startedAt = at, endedAt = at + 3_600_000L, durationSeconds = 3600, createdAt = at, updatedAt = at,
                    ),
                ),
                exercises = listOf(WorkoutExerciseEntity("we1", "w1", "ex-bench", 0, null, null, null)),
                sets = listOf(
                    WorkoutSetEntity(
                        "s1", "we1", 0, SetType.NORMAL, 20.0, 10, null, null, null, null, true, 1L,
                    ),
                ),
            )
        } else {
            FakeWorkoutRepository()
        }
        val exercise = Exercise(
            id = "ex-bench", name = "Bench", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
            isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
        )
        return ProfileViewModel(
            workoutRepo, FakeExerciseRepository(listOf(exercise)), FakeSettingsRepository(), FakeHealthMetricsSource(),
            FakeWellnessRepository(), FakePersonalRecordsRepository(), FakeMeasurementRepository(),
            FakeClock(currentMillis = nowMillis), SavedStateHandle(),
        )
    }

    private fun show(vm: ProfileViewModel, onStatistics: (TrainingMetric?) -> Unit = {}) {
        rule.setContent { LogEzTheme { ProfileScreen(onStatisticsClick = onStatistics, viewModel = vm) } }
        rule.waitForIdle()
    }

    @Test
    fun `a new user sees This week, one Achievements tile and no zero tiles or empty chart`() {
        show(viewModel(withWorkout = false))

        rule.onNodeWithText("This week".uppercase()).assertExists()
        rule.onNodeWithContentDescription("Achievements, 0 of 17. Next: First Workout.").assertExists()
        // A lone tile is a full-width row (412 dp screen less the 16 dp gutters), not half a row.
        assertEquals(380.dp, rule.onNodeWithContentDescription("Achievements, 0 of 17. Next: First Workout.").getUnclippedBoundsInRoot().width)
        rule.onAllNodesWithContentDescription("Week streak", substring = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Day streak", substring = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Workouts", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Workouts per week".uppercase()).assertCountEquals(0)
        rule.onAllNodesWithText("Last 7 days".uppercase()).assertCountEquals(0)
    }

    @Test
    fun `a first-workout user sees the four scorecards with honest singular labels`() {
        show(viewModel(withWorkout = true))

        rule.onNodeWithContentDescription("Week streak, 1 week. Longest 1 week.").assertExists()
        rule.onNodeWithContentDescription("Day streak, 1 day. Longest 1 day.").assertExists()
        rule.onNodeWithContentDescription("Workout, 1. Since 22 Aug.").assertExists()
        rule.onNodeWithContentDescription("Achievements, 1 of 17. Next: 7-Day Streak.").assertExists()
    }

    @Test
    fun `the Workouts scorecard opens Statistics on Frequency`() {
        var opened: TrainingMetric? = null
        show(viewModel(withWorkout = true)) { opened = it }

        rule.onNodeWithContentDescription("Workout, 1. Since 22 Aug.").performClick()
        assertEquals(TrainingMetric.FREQUENCY, opened)
    }
}
