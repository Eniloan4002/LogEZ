package com.enil.logez.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P-211 (Owner, 2026-09-30) in the live logger: set numbering (decision 9), the effort column,
 * PREVIOUS line 2 and the pickers in each scale. The bench is the README's "Sample data": last
 * session was W 40×10, 80×8 at RPE 8, 80×8 at RPE 8.5 and F 80×6 at RPE 10. Expected text is
 * literal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutLoggerClarityTest {
    @get:Rule val rule = createComposeRule()

    private val rpeChanges = mutableListOf<Pair<String, Double?>>()

    private val callbacks = WorkoutCallbacks(
        onStartSupersetSelection = {},
        onConfirmSupersetTarget = {},
        onRemoveFromSuperset = {},
        onRemoveExercise = {},
        onUpdateNotes = { _, _ -> },
        onAddSet = {},
        onAddWarmupSets = {},
        onUpdateSetType = { _, _, _ -> },
        onRemoveSet = { _, _ -> },
        onUpdateWeight = { _, _, _ -> },
        onUpdateReps = { _, _, _ -> },
        onUpdateDuration = { _, _, _ -> },
        onUpdateDistance = { _, _, _ -> },
        onUpdateCustomMetric = { _, _, _ -> },
        onToggleCheck = { _, _ -> true },
        onUpdateRpe = { _, _, _ -> },
        onStartInlineTimer = { _, _ -> },
        onStopInlineTimer = { _, _ -> },
        onSetTimerMode = { _, _ -> },
        onOpenPlateCalculator = { _, _, _ -> },
    )

    private fun bench(set2Rpe: Double? = null) = WorkoutExerciseUiModel(
        id = "we1",
        exerciseId = "ex-1",
        exerciseName = "Bench Press (Barbell)",
        exerciseType = ExerciseType.WEIGHT_REPS,
        equipment = Equipment.BARBELL,
        sets = listOf(
            WorkoutSetUiModel(id = "w", setType = SetType.WARMUP, weightKg = 40.0, reps = 10, isCompleted = true, previousLabel = "40 kg × 10"),
            WorkoutSetUiModel(id = "s1", weightKg = 82.5, reps = 8, rpe = 8.0, isCompleted = true, previousLabel = "80 kg × 8", previousRpe = 8.0),
            WorkoutSetUiModel(id = "s2", weightKg = 82.5, reps = 8, rpe = set2Rpe, previousLabel = "80 kg × 8", previousRpe = 8.5),
            WorkoutSetUiModel(id = "f", setType = SetType.FAILURE, weightKg = 82.5, previousLabel = "80 kg × 6", previousRpe = 10.0),
        ),
    )

    private var config by mutableStateOf(WorkoutLoggerDisplayConfig(rpeTrackingEnabled = true, effortScale = EffortScale.RIR))

    private fun show(exercise: WorkoutExerciseUiModel = bench()) {
        rule.setContent {
            LogEzTheme {
                WorkoutExerciseCard(
                    exercise = exercise,
                    dragHandle = {},
                    isDragging = false,
                    supersetSelectionActive = false,
                    isSupersetSource = false,
                    callbacks = callbacks,
                    onExerciseClick = {},
                    onOpenReplacePicker = {},
                    onRpeChange = { id, rpe -> rpeChanges += id to rpe },
                    config = config,
                )
            }
        }
    }

    private fun chip(label: String) = rule.onNode(hasText(label) and isSelectable())

    /** A tap inside a bottom sheet: Robolectric doesn't route injected touches into the sheet's own window. */
    private fun SemanticsNodeInteraction.tapInSheet() = performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `only normal sets are numbered, W 1 2 F, and a screen reader hears the same`() {
        show()

        rule.onNodeWithContentDescription("Warm-up set").assertExists()
        rule.onNodeWithContentDescription("Set 1").assertExists()
        rule.onNodeWithContentDescription("Set 2").assertExists()
        rule.onNodeWithContentDescription("Failure set").assertExists()
        rule.onNodeWithContentDescription("Warm-up set, reps").assertExists()
        rule.onNodeWithContentDescription("Set 2, reps").assertExists()
        rule.onNodeWithContentDescription("Failure set, weight in kilograms").assertExists()
        // The old positional labels: the second working set was "Set 3".
        rule.onAllNodesWithContentDescription("Set 3, reps").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Set 4").assertCountEquals(0)
    }

    @Test
    fun `in RIR the header, the effort cells and PREVIOUS line 2 all read RIR`() {
        show()

        rule.onNodeWithText("RIR").assertExists()
        rule.onNodeWithText("RIR 2").assertExists()
        rule.onNodeWithText("RIR 1–2").assertExists()
        rule.onNodeWithText("RIR 0").assertExists()
        rule.onAllNodesWithText("80 kg × 8").assertCountEquals(2)
        rule.onNodeWithContentDescription("Set 1, RIR 2").assertExists()
        rule.onNodeWithContentDescription("Set 2, RIR not set").assertExists()
        rule.onNodeWithContentDescription("Warm-up set, RIR not set").assertExists()
    }

    @Test
    fun `switching the scale mid-workout relabels PREVIOUS and the effort cells at once`() {
        show()

        config = config.copy(effortScale = EffortScale.RPE)
        rule.waitForIdle()

        rule.onNodeWithText("RPE").assertExists()
        rule.onNodeWithText("RPE 8").assertExists()
        rule.onNodeWithText("RPE 8.5").assertExists()
        rule.onNodeWithText("RPE 10").assertExists()
        rule.onAllNodesWithText("RIR 1–2").assertCountEquals(0)
        rule.onNodeWithContentDescription("Set 1, RPE 8").assertExists()
    }

    @Test
    fun `with tracking Off PREVIOUS keeps the saved effort but the entry column is gone`() {
        config = WorkoutLoggerDisplayConfig(rpeTrackingEnabled = false, effortScale = EffortScale.RIR)
        show()

        rule.onAllNodesWithText("RIR").assertCountEquals(0)
        rule.onNodeWithText("RIR 1–2").assertExists()
        rule.onAllNodesWithContentDescription("Set 2, RIR not set").assertCountEquals(0)
    }

    @Test
    fun `the RIR picker offers 0 1 2 3 4+ and entering RIR 2 stores RPE 8`() {
        show()

        rule.onNodeWithContentDescription("Set 2, RIR not set").performClick()

        rule.onNodeWithText("Log Set RIR").assertExists()
        rule.onNodeWithText("How many more reps could you have done?").assertExists()
        rule.onAllNodes(isSelectable()).assertCountEquals(5)
        listOf("0", "1", "2", "3", "4+").forEach { chip(it).assertExists() }
        rule.onAllNodes(isSelectable() and isSelected()).assertCountEquals(0)

        chip("2").tapInSheet()
        chip("2").assertIsSelected()
        rule.onNodeWithText("RIR 2 — Could have done 2 more reps").assertExists()
        rule.onNodeWithText("Done").tapInSheet()

        assertEquals(listOf("s2" to 8.0), rpeChanges)
    }

    @Test
    fun `a half step logged in RPE selects no RIR chip, reads as a range and is kept by Done`() {
        show(bench(set2Rpe = 8.5))

        rule.onNodeWithContentDescription("Set 2, RIR 1–2").performClick()

        rule.onAllNodes(isSelectable() and isSelected()).assertCountEquals(0)
        rule.onNodeWithText("RIR 1–2 — Could have done 1 or 2 more reps").assertExists()
        rule.onNodeWithText("Done").tapInSheet()

        assertEquals(listOf("s2" to 8.5), rpeChanges)
    }

    @Test
    fun `the RPE picker keeps its eight values, in 2 rows of 4, with the same plain words`() {
        config = config.copy(effortScale = EffortScale.RPE)
        show()

        rule.onNodeWithContentDescription("Set 2, RPE not set").performClick()

        rule.onNodeWithText("Log Set RPE").assertExists()
        rule.onNodeWithText("How hard was it? 10 = no reps left.").assertExists()
        rule.onAllNodes(isSelectable()).assertCountEquals(8)
        val firstRowTop = chip("6").getUnclippedBoundsInRoot().top
        assertEquals(firstRowTop, chip("8").getUnclippedBoundsInRoot().top)
        assertNotEquals(firstRowTop, chip("8.5").getUnclippedBoundsInRoot().top)
        assertEquals(chip("8.5").getUnclippedBoundsInRoot().top, chip("10").getUnclippedBoundsInRoot().top)

        chip("9").tapInSheet()
        rule.onNodeWithText("RPE 9 — Could have done 1 more rep").assertExists()
        rule.onNodeWithText("Clear").tapInSheet()

        assertEquals(listOf("s2" to null), rpeChanges)
    }

    @Test
    fun `a circuit round keeps its round number, letters F and D, and shares one RIR header`() {
        val pushUps = WorkoutExerciseUiModel(
            id = "we1", exerciseId = "ex-1", exerciseName = "Push-up", exerciseType = ExerciseType.REPS_ONLY,
            sets = listOf(WorkoutSetUiModel(id = "p1"), WorkoutSetUiModel(id = "p2", reps = 12, previousLabel = "12 reps", previousRpe = 8.5)),
        )
        val dips = WorkoutExerciseUiModel(
            id = "we2", exerciseId = "ex-2", exerciseName = "Dip", exerciseType = ExerciseType.REPS_ONLY,
            sets = listOf(WorkoutSetUiModel(id = "d1"), WorkoutSetUiModel(id = "d2", setType = SetType.FAILURE)),
        )
        val round2 = CircuitRound(roundNumber = 2, entries = listOf(CircuitRoundEntry(pushUps, pushUps.sets[1]), CircuitRoundEntry(dips, dips.sets[1])))
        rule.setContent {
            LogEzTheme {
                CircuitRoundCard(
                    round = round2,
                    callbacks = callbacks,
                    onExerciseClick = {},
                    onOpenReplacePicker = {},
                    onRemoveRound = {},
                    inlineTimerExerciseId = null,
                    inlineTimerSetId = null,
                    config = config,
                )
            }
        }

        rule.onAllNodesWithText("RIR").assertCountEquals(1)
        rule.onNodeWithText("RIR 1–2").assertExists()
        rule.onNodeWithContentDescription("Set 2, reps").assertExists()
        rule.onNodeWithContentDescription("Failure set, reps").assertExists()
        rule.onNodeWithContentDescription("Set 2, RIR not set").assertExists()

        rule.onNodeWithText("RIR").performClick()
        rule.onNodeWithText("What is RIR?").assertExists()
    }

    @Test
    fun `PREVIOUS gives up 8dp only while the effort column shows (small fix 10e)`() {
        show()
        rule.onNodeWithText("PREVIOUS").assertWidthIsEqualTo(88.dp)

        config = config.copy(rpeTrackingEnabled = false)
        rule.waitForIdle()

        rule.onNodeWithText("PREVIOUS").assertWidthIsEqualTo(96.dp)
    }

    @Test
    fun `the effort header and the picker's link both open the explainer`() {
        show()

        rule.onNodeWithText("RIR").performClick()
        rule.onNodeWithText("What is RIR?").assertExists()
        rule.onNodeWithText("Got it").tapInSheet()
        rule.onAllNodesWithText("What is RIR?").assertCountEquals(0)

        rule.onNodeWithContentDescription("Set 2, RIR not set").performClick()
        rule.onNodeWithText("What's RIR?").tapInSheet()
        rule.onNodeWithText("What is RIR?").assertExists()
    }
}
