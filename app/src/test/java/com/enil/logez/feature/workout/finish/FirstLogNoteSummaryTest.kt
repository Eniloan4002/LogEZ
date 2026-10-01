package com.enil.logez.feature.workout.finish

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.wellness.WorkoutHeartRateBackfill
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan (O1g, Decision 13): the note is drawn above Personal records on both the strength
 * summary and the walk/run summary when [WorkoutSummaryUiState.showFirstLogNote] is true, and not
 * otherwise. (When the flag is true is WorkoutSummaryViewModelTest's job.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstLogNoteSummaryTest {
    @get:Rule val rule = createComposeRule()

    private val note = "The first time you log an exercise sets its starting records. Later sessions that beat them set new ones."

    /** Unclipped positions: a node scrolled out of view reports clipped bounds in root. */
    private fun assertNoteAbove(heading: String) {
        val noteNode = rule.onNodeWithText(note).fetchSemanticsNode()
        val noteBottom = noteNode.positionInRoot.y + noteNode.size.height
        val headingTop = rule.onNodeWithText(heading).fetchSemanticsNode().positionInRoot.y
        assertTrue("note ends at $noteBottom px, heading starts at $headingTop px", noteBottom <= headingTop)
    }

    // --- The walk/run summary ---

    private fun setWalkSummary(showNote: Boolean) {
        val uiState = WorkoutSummaryUiState(
            isLoading = false,
            isGpsTracked = true,
            gpsActivity = GpsActivity.WALK,
            title = "Walk",
            durationSeconds = 1_200,
            // No route, so no MapLibre map under Robolectric.
            routePoints = emptyList(),
            prMedals = listOf(PrMedal(exerciseName = "Walking (Outdoor)", prType = PrType.LONGEST_DISTANCE, value = 2_000.0)),
            showFirstLogNote = showNote,
        )
        rule.setContent { LogEzTheme { GpsWorkoutSummary(uiState = uiState, onOpenMap = {}, onShare = {}, onDone = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun `a first walk's summary shows the note above Personal records`() {
        setWalkSummary(showNote = true)

        rule.onNodeWithText(note).performScrollTo().assertIsDisplayed()
        assertNoteAbove("PERSONAL RECORDS")
    }

    @Test
    fun `a later walk's summary has medals but no note`() {
        setWalkSummary(showNote = false)

        rule.onNodeWithText("PERSONAL RECORDS").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(note).assertDoesNotExist()
    }

    // --- The strength summary ---

    private val bench = Exercise(
        id = "ex-1", name = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun workout(id: String, startedAt: Long) = WorkoutEntity(
        id = id, routineId = null, title = "Session $id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + 60_000L, durationSeconds = 60,
        createdAt = startedAt, updatedAt = startedAt, kind = WorkoutKind.STRENGTH,
    )

    /** The strength summary of "w1", with Bench Press logged in each of [workouts]. */
    private fun setStrengthSummary(workouts: List<WorkoutEntity>) {
        val exercises = workouts.map { w ->
            WorkoutExerciseEntity(id = "we-${w.id}", workoutId = w.id, exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)
        }
        val sets = exercises.map { we ->
            WorkoutSetEntity(
                id = "s-${we.id}", workoutExerciseId = we.id, orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8,
                durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L,
            )
        }
        val heartRates = FakeWorkoutHeartRateSampleRepository()
        val vm = WorkoutSummaryViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepository = FakeWorkoutRepository(workouts = workouts, exercises = exercises, sets = sets),
            exerciseRepository = FakeExerciseRepository(listOf(bench)),
            personalRecordsRepository = FakePersonalRecordsRepository(
                listOf(
                    PersonalRecordEntity(
                        id = "pr-1", exerciseId = "ex-1", workoutId = "w1", workoutSetId = "s-we-w1",
                        prType = PrType.HEAVIEST_WEIGHT, value = 60.0, achievedAt = 5_000L,
                    ),
                ),
            ),
            settingsRepository = FakeSettingsRepository(),
            activityTrackRepository = FakeActivityTrackRepository(),
            heartRateSampleRepository = heartRates,
            heartRateBackfill = WorkoutHeartRateBackfill(FakeHealthMetricsSource(), heartRates, AppLogger.NoOp),
        )
        rule.setContent { LogEzTheme { WorkoutSummaryScreen(onDone = {}, viewModel = vm) } }
        rule.waitForIdle()
    }

    @Test
    fun `a strength summary with a first log's medals shows the note above Personal records`() {
        setStrengthSummary(listOf(workout("w1", startedAt = 5_000L)))

        rule.onNodeWithText(note).performScrollTo().assertIsDisplayed()
        assertNoteAbove("Personal records")
    }

    @Test
    fun `a strength summary whose medals follow an earlier log shows no note`() {
        setStrengthSummary(listOf(workout("w0", startedAt = 1_000L), workout("w1", startedAt = 5_000L)))

        rule.onNodeWithText("Personal records").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(note).assertDoesNotExist()
    }
}
