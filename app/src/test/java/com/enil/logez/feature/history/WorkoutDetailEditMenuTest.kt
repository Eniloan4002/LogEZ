package com.enil.logez.feature.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The History detail screen's overflow menu: a GPS-tracked run has no Edit, and everything else
 * does. Every way into a workout (History list, Calendar, Recent's Open in History, the summary)
 * lands on this one screen, and it is the only place that navigates to the editor, so this is
 * what makes them all behave the same.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutDetailEditMenuTest {
    @get:Rule val rule = createComposeRule()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the menu of a GPS-tracked run lists Copy, Save as Routine and Delete but not Edit`() {
        showDetail(kind = WorkoutKind.GPS_TRACKED, withTrack = true)

        rule.onNodeWithContentDescription("More options").performClick()

        rule.onNodeWithText("Edit Workout").assertDoesNotExist()
        rule.onNodeWithText("Copy Workout").assertExists()
        rule.onNodeWithText("Save as Routine").assertExists()
        rule.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun `the menu of a GPS run saved with time only has no Edit either`() {
        showDetail(kind = WorkoutKind.GPS_TRACKED, withTrack = false)

        rule.onNodeWithContentDescription("More options").performClick()

        rule.onNodeWithText("Edit Workout").assertDoesNotExist()
    }

    @Test
    fun `the menu of a strength workout offers Edit and tapping it opens the editor for that workout`() {
        val edited = mutableListOf<String>()
        showDetail(kind = WorkoutKind.STRENGTH, withTrack = false, onEdit = { edited += it })

        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Edit Workout").performClick()

        assertEquals(listOf("w1"), edited)
    }

    @Test
    fun `the menu of a hand-logged treadmill run offers Edit`() {
        showDetail(kind = WorkoutKind.STRENGTH, withTrack = false, distanceMeters = 5_000.0)

        rule.onNodeWithContentDescription("More options").performClick()

        rule.onNodeWithText("Edit Workout").assertExists()
    }

    private fun showDetail(
        kind: WorkoutKind,
        withTrack: Boolean,
        distanceMeters: Double? = null,
        onEdit: (String) -> Unit = {},
    ) {
        val workout = WorkoutEntity(
            id = "w1", routineId = null, title = "Evening run", notes = null, status = WorkoutStatus.COMPLETED,
            startedAt = 1_000L, endedAt = 2_000L, durationSeconds = 60, createdAt = 1_000L, updatedAt = 1_000L, kind = kind,
        )
        val exercise = WorkoutExerciseEntity(
            id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null,
        )
        val set = WorkoutSetEntity(
            id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL,
            weightKg = null, reps = null, durationSeconds = null, distanceMeters = distanceMeters, rpe = null,
            customMetric = null, isCompleted = true, completedAt = 1L,
        )
        // No recorded point, so no map: MapLibre's native library cannot load under Robolectric. The
        // view model tests cover a track with a route; this one is still a GPS run's track row.
        val track = ActivityTrackEntity(
            id = "track-s1", workoutSetId = "s1", routePolyline = null, pointCount = 0, avgAccuracyM = null,
        )
        val vm = buildWorkoutDetailViewModel(
            dispatcher = dispatcher,
            workoutRepo = FakeWorkoutRepository(workouts = listOf(workout), exercises = listOf(exercise), sets = listOf(set)),
            trackRepo = FakeActivityTrackRepository(if (withTrack) listOf(track) else emptyList()),
        )
        rule.setContent {
            LogEzTheme {
                WorkoutDetailScreen(
                    onBack = {}, onEdit = onEdit, onSavedAsRoutine = {}, onNavigateToLogger = {}, onExerciseClick = {},
                    onNavigateToActivityTracking = {}, onNavigateToFinish = {}, viewModel = vm,
                )
            }
        }
    }
}
