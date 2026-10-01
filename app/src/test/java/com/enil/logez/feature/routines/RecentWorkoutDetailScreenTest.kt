package com.enil.logez.feature.routines

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.feature.history.DetailExerciseBlock
import com.enil.logez.feature.history.DetailSetRow
import com.enil.logez.feature.history.DetailTableUnits
import com.enil.logez.feature.history.DetailRoundCard
import com.enil.logez.feature.history.ExerciseBlockCard
import com.enil.logez.feature.history.buildDetailRounds
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.StartResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R-1 "Recent workout detail": the screen's own rules (empty workout, circuit header, the menu, a missing
 * workout, Start asking first), the card's preview reading (superset label, a deleted exercise, in a
 * round too) and the list row's two targets. Expected text is literal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentWorkoutDetailScreenTest {
    @get:Rule val rule = createComposeRule()

    private val units = DetailTableUnits(EffortScale.RPE, WeightUnit.KG, DistanceUnit.KM)

    @Test
    fun `a superset block says Superset in the preview and not in History's record`() {
        val block = block(exercise("ex-lat", "Lat Pulldown (Cable)"), supersetGroup = 0)

        rule.setContent { LogEzTheme { ExerciseBlockCard(block, units, onEffortInfoClick = {}, onExerciseClick = {}, preview = true) } }
        rule.onNodeWithText("Superset").assertExists()
    }

    @Test
    fun `History's record of a superset block carries no Superset label`() {
        val block = block(exercise("ex-lat", "Lat Pulldown (Cable)"), supersetGroup = 0)

        rule.setContent { LogEzTheme { ExerciseBlockCard(block, units, onEffortInfoClick = {}, onExerciseClick = {}) } }
        rule.onNodeWithText("Superset").assertDoesNotExist()
    }

    @Test
    fun `a deleted exercise says Start still adds it and is not a link`() {
        val clicked = mutableListOf<String>()
        val block = block(exercise("ex-gone", "Face Pull (Cable)", isDeleted = true))

        rule.setContent { LogEzTheme { ExerciseBlockCard(block, units, onEffortInfoClick = {}, onExerciseClick = { clicked += it }, preview = true) } }
        rule.onNodeWithText("Deleted from your exercise list. Start still adds it.").assertExists()
        rule.onNodeWithText("Face Pull (Cable)").performClick()

        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun `a live exercise is a link and carries no deleted note`() {
        val clicked = mutableListOf<String>()
        val block = block(exercise("ex-bench", "Bench Press (Barbell)"))

        rule.setContent { LogEzTheme { ExerciseBlockCard(block, units, onEffortInfoClick = {}, onExerciseClick = { clicked += it }, preview = true) } }
        rule.onNodeWithText("Deleted from your exercise list. Start still adds it.").assertDoesNotExist()
        rule.onNodeWithText("Bench Press (Barbell)").performClick()

        assertEquals(listOf("ex-bench"), clicked)
    }

    @Test
    fun `the row opens the workout and its Start pill starts it, each on its own`() {
        val events = mutableListOf<String>()
        val card = RecentWorkoutCardModel("w1", "Push Day", 1_000L, 3_120, 3, isFromRoutine = true)

        rule.setContent {
            LogEzTheme {
                RecentWorkoutRow(card, compactDate = true, onOpen = { events += "open" }, onStart = { events += "start" })
            }
        }
        rule.onNodeWithText("Start").performClick()
        rule.onNodeWithText("Push Day").performClick()

        assertEquals(listOf("start", "open"), events)
        // TalkBack announces the row's action by name, so two rows' "Open" never read alike.
        rule.onNode(SemanticsMatcher("click label is Open Push Day") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open Push Day" }).assertExists()
    }

    @Test
    fun `a deleted exercise in a circuit round is muted, not a link, and the round says Start still adds it once`() {
        val clicked = mutableListOf<String>()
        val live = block(exercise("ex-bench", "Bench Press (Barbell)"))
        val gone = block(exercise("ex-gone", "Face Pull (Cable)", isDeleted = true))
        val round = buildDetailRounds(listOf(live, gone)).single()

        rule.setContent { LogEzTheme { DetailRoundCard(round, units, onEffortInfoClick = {}, onExerciseClick = { clicked += it }, preview = true) } }
        rule.onAllNodesWithText("Deleted from your exercise list. Start still adds it.").assertCountEquals(1)
        rule.onNodeWithText("Face Pull (Cable)").performClick()
        rule.onNodeWithText("Bench Press (Barbell)").performClick()

        assertEquals(listOf("ex-bench"), clicked)
    }

    @Test
    fun `History's circuit round has no deleted note`() {
        val round = buildDetailRounds(listOf(block(exercise("ex-gone", "Face Pull (Cable)", isDeleted = true)))).single()

        rule.setContent { LogEzTheme { DetailRoundCard(round, units, onEffortInfoClick = {}, onExerciseClick = {}) } }
        rule.onNodeWithText("Deleted from your exercise list. Start still adds it.").assertDoesNotExist()
    }

    @Test
    fun `an empty workout shows its note and offers neither Start nor Save as Routine`() {
        showDetail(state(blocks = emptyList()))

        rule.onNodeWithText("No exercises were logged in this workout.").assertExists()
        rule.onNodeWithText("Start this workout").assertDoesNotExist()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Save as Routine").assertDoesNotExist()
        rule.onNodeWithText("Open in History").assertExists()
    }

    @Test
    fun `a workout with exercises offers Start, Save as Routine and Open in History`() {
        val opened = mutableListOf<String>()
        val saved = mutableListOf<String>()
        showDetail(state(blocks = listOf(block(exercise("ex-bench", "Bench Press (Barbell)")))), onOpenInHistory = { opened += it }, onSavedAsRoutine = { saved += it })

        rule.onNodeWithText("Start this workout").assertExists()
        rule.onNodeWithText("No exercises were logged in this workout.").assertDoesNotExist()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Save as Routine").performClick()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Open in History").performClick()

        assertEquals(listOf("routine-1"), saved)
        assertEquals(listOf("w1"), opened)
    }

    @Test
    fun `a circuit workout shows the CIRCUIT chip and its round count`() {
        val blocks = listOf(twoSetBlock("a", exercise("ex-bench", "Bench Press (Barbell)")), twoSetBlock("b", exercise("ex-row", "Seated Cable Row (Machine)")))
        showDetail(state(blocks = blocks, isCircuit = true))

        rule.onNodeWithText("CIRCUIT").assertExists()
        rule.onNodeWithText("2 rounds").assertExists()
    }

    @Test
    fun `a circuit with nothing logged shows no chip and no round count`() {
        showDetail(state(blocks = emptyList(), isCircuit = true))

        rule.onNodeWithText("CIRCUIT").assertDoesNotExist()
        rule.onNodeWithText("1 round").assertDoesNotExist()
        rule.onNodeWithText("No exercises were logged in this workout.").assertExists()
    }

    @Test
    fun `a workout that is missing sends the user back`() {
        var backs = 0
        showDetail(RecentWorkoutDetailUiState(isLoading = false, isMissing = true), onBack = { backs++ })
        rule.waitForIdle()

        assertEquals(1, backs)
    }

    @Test
    fun `the screen asks the view model to re-read when it resumes`() {
        var refreshes = 0
        showDetail(state(blocks = emptyList()), onRefresh = { refreshes++ })
        rule.waitForIdle()

        assertTrue(refreshes >= 1)
    }

    @Test
    fun `Start asks first, and cancelling starts nothing`() {
        val actions = FakeStartActions()
        showDetail(state(blocks = listOf(block(exercise("ex-bench", "Bench Press (Barbell)")))), actions = actions)

        rule.onNodeWithText("Start this workout").performClick()
        rule.onNodeWithText("Start this workout again?").assertExists()
        assertEquals(emptyList<String>(), actions.started)
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Start this workout again?").assertDoesNotExist()

        assertEquals(emptyList<String>(), actions.started)
    }

    private fun showDetail(
        state: RecentWorkoutDetailUiState,
        actions: RecentStartActions = FakeStartActions(),
        onRefresh: () -> Unit = {},
        onBack: () -> Unit = {},
        onOpenInHistory: (String) -> Unit = {},
        onSavedAsRoutine: (String) -> Unit = {},
    ) {
        rule.setContent {
            LogEzTheme {
                RecentWorkoutDetailContent(
                    uiState = state,
                    startActions = actions,
                    onRefresh = onRefresh,
                    onSaveAsRoutine = { "routine-1" },
                    onBack = onBack,
                    onNavigateToLogger = {},
                    onNavigateToActivityTracking = {},
                    onNavigateToFinish = {},
                    onSavedAsRoutine = onSavedAsRoutine,
                    onOpenInHistory = onOpenInHistory,
                    onExerciseClick = {},
                )
            }
        }
    }

    private fun state(blocks: List<DetailExerciseBlock>, isCircuit: Boolean = false) = RecentWorkoutDetailUiState(
        isLoading = false,
        summary = RecentWorkoutCardModel("w1", "Push Day", 1_000L, 3_120, blocks.size, isFromRoutine = true),
        isCircuit = isCircuit,
        blocks = blocks,
    )

    private class FakeStartActions : RecentStartActions {
        val started = mutableListOf<String>()
        override suspend fun start(workoutId: String): StartResult { started += workoutId; return StartResult.Started("new") }
        override suspend fun discardInProgressAndStart(workoutId: String): String = "new"
        override suspend fun inProgressWorkout(): InProgressWorkout? = null
    }

    private fun twoSetBlock(id: String, exercise: Exercise) = DetailExerciseBlock(
        workoutExercise = WorkoutExerciseEntity(
            id = "we-$id", workoutId = "w1", exerciseId = exercise.id, orderIndex = 0,
            supersetGroup = null, restTimerSeconds = null, notes = null,
        ),
        exercise = exercise,
        sets = (0..1).map { index ->
            DetailSetRow(
                setId = "s$id$index", orderIndex = index, setType = SetType.NORMAL, weightKg = 55.0, reps = 10, durationSeconds = null,
                distanceMeters = null, customMetric = null, rpe = null, isCompleted = true, pr = null,
            )
        },
    )

    private fun exercise(id: String, name: String, isDeleted: Boolean = false) = Exercise(
        id = id, name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = isDeleted, createdAt = 0, updatedAt = 0,
    )

    private fun block(exercise: Exercise, supersetGroup: Int? = null) = DetailExerciseBlock(
        workoutExercise = WorkoutExerciseEntity(
            id = "we-${exercise.id}", workoutId = "w1", exerciseId = exercise.id, orderIndex = 0,
            supersetGroup = supersetGroup, restTimerSeconds = null, notes = null,
        ),
        exercise = exercise,
        sets = listOf(
            DetailSetRow(
                setId = "s1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 55.0, reps = 10, durationSeconds = null,
                distanceMeters = null, customMetric = null, rpe = null, isCompleted = true, pr = null,
            ),
        ),
    )
}
