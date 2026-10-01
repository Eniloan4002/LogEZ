package com.enil.logez.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.TimerMode
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The logger's 32dp timer column (Owner, 2026-10-01): every TIME and DISTANCE field sits centred
 * under its header whether its row is open or checked, on an exercise card and in a circuit round,
 * at the test emulator's 392dp and the Pixel 7's 412dp. Plus the mode glyph, the countdown line, the
 * menu item and the live readout. Expected strings are literal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutLoggerTimerColumnTest {
    @get:Rule val rule = createComposeRule()

    private val modeChanges = mutableListOf<Pair<String, TimerMode>>()

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
        onSetTimerMode = { id, mode -> modeChanges += id to mode },
        onOpenPlateCalculator = { _, _, _ -> },
    )

    private fun plank(timerMode: String? = null) = WorkoutExerciseUiModel(
        id = "we1", exerciseId = "ex-plank", exerciseName = "Plank", exerciseType = ExerciseType.DURATION, timerMode = timerMode,
        sets = listOf(
            WorkoutSetUiModel(id = "s1", durationSeconds = 64, isCompleted = true, previousLabel = "1:00", previousDurationSeconds = 60),
            WorkoutSetUiModel(id = "s2", durationSeconds = 60, previousLabel = "1:00", previousDurationSeconds = 60),
            WorkoutSetUiModel(id = "s3", durationSeconds = null, previousLabel = "—"),
        ),
    )

    private fun treadmill() = WorkoutExerciseUiModel(
        id = "we2", exerciseId = "ex-run", exerciseName = "Running (Treadmill)", exerciseType = ExerciseType.DISTANCE_DURATION,
        sets = listOf(
            WorkoutSetUiModel(id = "r1", durationSeconds = 300, distanceMeters = 800.0, isCompleted = true, previousLabel = "0.8 km / 5:00"),
            WorkoutSetUiModel(id = "r2", durationSeconds = 1110, distanceMeters = 3000.0, previousLabel = "3 km / 18:30"),
        ),
    )

    private fun showCard(
        exercise: WorkoutExerciseUiModel,
        widthDp: Int = 412,
        config: WorkoutLoggerDisplayConfig = WorkoutLoggerDisplayConfig(),
        runningSetId: String? = null,
        liveSeconds: Int? = null,
        timeHintSetId: String? = null,
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            LogEzTheme {
              CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.width(widthDp.dp)) {
                    WorkoutExerciseCard(
                        exercise = exercise,
                        dragHandle = {},
                        isDragging = false,
                        supersetSelectionActive = false,
                        isSupersetSource = false,
                        callbacks = callbacks,
                        onExerciseClick = {},
                        onOpenReplacePicker = {},
                        inlineTimerSetId = runningSetId,
                        inlineTimerSecondsFlow = flowOf(liveSeconds),
                        timeHintSetId = timeHintSetId,
                        config = config,
                    )
                }
              }
            }
        }
    }

    private fun showRound(exercises: List<WorkoutExerciseUiModel>, widthDp: Int = 412, config: WorkoutLoggerDisplayConfig = WorkoutLoggerDisplayConfig()) {
        val round = buildCircuitRounds(exercises).first()
        rule.setContent {
            LogEzTheme {
                Box(Modifier.width(widthDp.dp)) {
                    CircuitRoundCard(
                        round = round,
                        callbacks = callbacks,
                        onExerciseClick = {},
                        onOpenReplacePicker = {},
                        onRemoveRound = {},
                        inlineTimerExerciseId = null,
                        inlineTimerSetId = null,
                        inlineTimerSecondsFlow = flowOf(null),
                        config = config,
                    )
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.centreX(): Dp = getUnclippedBoundsInRoot().let { (it.left + it.right) / 2 }

    private fun SemanticsNodeInteraction.boxWidth(): Dp = getUnclippedBoundsInRoot().let { it.right - it.left }

    private fun assertCentred(field: SemanticsNodeInteraction, header: SemanticsNodeInteraction, what: String) {
        val gap = abs((field.centreX() - header.centreX()).value)
        assertTrue("$what: the field's centre is ${gap}dp off its header", gap <= 1f)
    }

    private fun assertPlankAligned(widthDp: Int) {
        showCard(plank(), widthDp = widthDp)
        val header = rule.onNodeWithText("TIME")
        assertCentred(rule.onNodeWithContentDescription("Set 1, seconds"), header, "checked row at ${widthDp}dp")
        assertCentred(rule.onNodeWithContentDescription("Set 2, seconds"), header, "open row at ${widthDp}dp")
        assertEquals(
            rule.onNodeWithContentDescription("Set 1, seconds").boxWidth().value,
            rule.onNodeWithContentDescription("Set 2, seconds").boxWidth().value,
            0.5f,
        )
    }

    private fun assertTreadmillAligned(widthDp: Int) {
        showCard(treadmill(), widthDp = widthDp)
        for (set in listOf("Set 1", "Set 2")) {
            assertCentred(rule.onNodeWithContentDescription("$set, seconds"), rule.onNodeWithText("TIME"), "$set TIME at ${widthDp}dp")
            assertCentred(rule.onNodeWithContentDescription("$set, metres"), rule.onNodeWithText("DISTANCE"), "$set DISTANCE at ${widthDp}dp")
        }
    }

    @Test
    fun `Plank's open and checked TIME fields sit under TIME and are as wide as each other, at 412dp`() = assertPlankAligned(412)

    @Test
    fun `Plank's open and checked TIME fields sit under TIME and are as wide as each other, at 392dp`() = assertPlankAligned(392)

    @Test
    fun `Running's TIME and DISTANCE fields sit under their headers on open and checked rows, at 412dp`() = assertTreadmillAligned(412)

    @Test
    fun `Running's TIME and DISTANCE fields sit under their headers on open and checked rows, at 392dp`() = assertTreadmillAligned(392)

    private fun dumbbell(id: String, name: String, setId: String) = WorkoutExerciseUiModel(
        id = id, exerciseId = "ex-$id", exerciseName = name, exerciseType = ExerciseType.WEIGHT_REPS, equipment = Equipment.DUMBBELL,
        sets = listOf(WorkoutSetUiModel(id = setId, weightKg = 22.5, reps = 12, previousLabel = "20 kg × 12")),
    )

    @Test
    fun `a circuit round's KG and REPS fields sit under their headers, the REPS header using the row's weight`() {
        showRound(listOf(dumbbell("we1", "Goblet Squat (Dumbbell)", "g1"), dumbbell("we2", "Shoulder Press (Dumbbell)", "p1")), widthDp = 412)

        // Identical column sets share one header, so each header text exists once.
        assertCentred(rule.onAllNodesWithContentDescription("Set 1, reps")[0], rule.onNodeWithText("REPS"), "circuit REPS")
        assertCentred(rule.onAllNodesWithContentDescription("Set 1, weight in kilograms")[0], rule.onNodeWithText("KG"), "circuit KG")
    }

    @Test
    fun `two timed circuit stations share one header with the timer cell, and their TIME fields sit under it`() {
        val first = plank()
        val second = first.copy(id = "we3", exerciseId = "ex-wall", exerciseName = "Wall Sit")
        showRound(listOf(first, second), widthDp = 412)

        rule.onAllNodesWithContentDescription("Stopwatch").assertCountEquals(1)
        assertCentred(rule.onAllNodesWithContentDescription("Set 1, seconds")[0], rule.onNodeWithText("TIME"), "circuit TIME")
    }

    @Test
    fun `a round mixing a stopwatch and a countdown gets one header, and so one glyph, per exercise`() {
        val first = plank(timerMode = "COUNTDOWN")
        val second = first.copy(id = "we3", exerciseId = "ex-wall", exerciseName = "Wall Sit", timerMode = null)
        showRound(listOf(first, second), widthDp = 412)

        rule.onAllNodesWithContentDescription("Countdown timer").assertCountEquals(1)
        rule.onAllNodesWithContentDescription("Stopwatch").assertCountEquals(1)
        // The countdown line sits above the countdown station's name.
        rule.onAllNodesWithText("Countdown timer · this workout only").assertCountEquals(1)
    }

    @Test
    fun `a circuit exercise's menu offers the mode switch`() {
        showRound(listOf(plank()), widthDp = 412)
        // [0] is the round header's menu, [1] the first station's.
        rule.onAllNodesWithContentDescription("More options")[1].performClick()
        rule.onNodeWithText("Use countdown timer").assertIsEnabled().performClick()
        assertEquals(listOf("we1" to TimerMode.COUNTDOWN), modeChanges)
    }

    @Test
    fun `a stopwatch card says Stopwatch in the timer header and shows no countdown line`() {
        showCard(plank())

        rule.onNodeWithContentDescription("Stopwatch").assertExists()
        rule.onAllNodesWithText("Countdown timer · this workout only").assertCountEquals(0)
        rule.onNodeWithContentDescription("Set 2, start stopwatch").assertExists()
    }

    @Test
    fun `a countdown card shows the hourglass header, the line above the name, and reads the target to a screen reader`() {
        showCard(plank(timerMode = "COUNTDOWN"))

        rule.onNodeWithContentDescription("Countdown timer").assertExists()
        rule.onNodeWithText("Countdown timer · this workout only").assertExists()
        rule.onNodeWithContentDescription("Set 2, start countdown from 1:00").assertExists()
        // Set 3 has no TIME and no PREVIOUS time: there is no target to name.
        rule.onNodeWithContentDescription("Set 3, start countdown").assertExists()
    }

    @Test
    fun `with the Inline timer off the glyph, the countdown line and the menu item are gone`() {
        showCard(plank(timerMode = "COUNTDOWN"), config = WorkoutLoggerDisplayConfig(inlineTimerEnabled = false))

        rule.onAllNodesWithContentDescription("Countdown timer").assertCountEquals(0)
        rule.onAllNodesWithText("Countdown timer · this workout only").assertCountEquals(0)
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onAllNodesWithText("Use stopwatch").assertCountEquals(0)
        rule.onAllNodesWithText("Use countdown timer").assertCountEquals(0)
    }

    @Test
    fun `the menu offers the countdown on a stopwatch card`() {
        showCard(plank())
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Use countdown timer").assertIsEnabled().performClick()
        assertEquals(listOf("we1" to TimerMode.COUNTDOWN), modeChanges)
    }

    @Test
    fun `the menu item flips to Use stopwatch on a countdown card`() {
        showCard(plank(timerMode = "COUNTDOWN"))
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Use stopwatch").assertIsEnabled().performClick()
        assertEquals(listOf("we1" to TimerMode.STOPWATCH), modeChanges)
    }

    @Test
    fun `a card with no duration column has no timer item in its menu`() {
        val bench = dumbbell("we9", "Bench Press", "b1")
        showCard(bench)
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onAllNodesWithText("Use countdown timer").assertCountEquals(0)
    }

    @Test
    fun `the menu item is disabled while one of the exercise's sets is being timed`() {
        showCard(plank(), runningSetId = "s2", liveSeconds = 38)
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Use countdown timer").assertIsNotEnabled()
    }

    @Test
    fun `a running stopwatch reads m ss in the TIME cell and offers Stop`() {
        showCard(plank(), runningSetId = "s2", liveSeconds = 38)
        rule.onNodeWithContentDescription("Set 2, seconds, 0:38").assertExists()
        rule.onNodeWithContentDescription("Set 2, stop timer").assertExists()
    }

    @Test
    fun `a running countdown reads time left to a screen reader`() {
        showCard(plank(timerMode = "COUNTDOWN"), runningSetId = "s2", liveSeconds = 41)
        rule.onNodeWithContentDescription("Set 2, seconds, 0:41 left").assertExists()
    }

    @Test
    fun `an hour or more reads h mm ss`() {
        showCard(plank(), runningSetId = "s2", liveSeconds = 3600)
        rule.onNodeWithContentDescription("Set 2, seconds, 1:00:00").assertExists()
    }

    @Test
    fun `a countdown with nothing to count down from shows the hint under its row`() {
        showCard(plank(timerMode = "COUNTDOWN"), timeHintSetId = "s3")
        rule.onNodeWithText("Type a time to count down from.").assertExists()
    }

    @Test
    fun `no hint shows until a countdown asked for one`() {
        showCard(plank(timerMode = "COUNTDOWN"))
        rule.onAllNodesWithText("Type a time to count down from.").assertCountEquals(0)
    }

    // --- review fixes: width, large text, other column sets, focus ---

    private fun assertHeaderWhole(label: String, what: String) {
        val results = mutableListOf<TextLayoutResult>()
        val node = rule.onNodeWithText(label).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertEquals("$what: no text layout for $label", 1, results.size)
        assertTrue("$what: the $label header is cut", !results.single().didOverflowWidth)
    }

    private fun assertHeadersWhole(exercise: WorkoutExerciseUiModel, labels: List<String>, widthDp: Int, fontScale: Float) {
        showCard(exercise, widthDp = widthDp, fontScale = fontScale)
        for (label in labels) assertHeaderWhole(label, "${exercise.exerciseName} at ${widthDp}dp, font $fontScale")
    }

    // Robolectric's text metrics are approximate, so these cannot prove a header fits a real
    // phone's font: they fail only on a gross overflow. The real check was on emulator-5556
    // (docs/verification/tracking-timer-recent-2026-10-01/T1/).
    @Test
    fun `Running's TIME, DISTANCE and PREVIOUS headers are whole at 360dp`() = assertHeadersWhole(treadmill(), listOf("TIME", "DISTANCE", "PREVIOUS"), 360, 1f)

    @Test
    fun `Running's TIME, DISTANCE and PREVIOUS headers are whole at 392dp and 1_3x text`() = assertHeadersWhole(treadmill(), listOf("TIME", "DISTANCE", "PREVIOUS"), 392, 1.3f)

    @Test
    fun `Running's TIME, DISTANCE and PREVIOUS headers are whole at 360dp and 2x text`() = assertHeadersWhole(treadmill(), listOf("TIME", "DISTANCE", "PREVIOUS"), 360, 2f)

    @Test
    fun `Plank's headers are whole at 360dp and 2x text`() = assertHeadersWhole(plank(), listOf("TIME", "PREVIOUS"), 360, 2f)

    private fun barbellHold() = WorkoutExerciseUiModel(
        id = "we4", exerciseId = "ex-hold", exerciseName = "Barbell Hold", exerciseType = ExerciseType.WEIGHT_DURATION, equipment = Equipment.BARBELL,
        sets = listOf(
            WorkoutSetUiModel(id = "h1", weightKg = 60.0, durationSeconds = 30, isCompleted = true, previousLabel = "60 kg / 0:30"),
            WorkoutSetUiModel(id = "h2", weightKg = 60.0, durationSeconds = 30, previousLabel = "60 kg / 0:30"),
        ),
    )

    private fun stairMachine() = WorkoutExerciseUiModel(
        id = "we5", exerciseId = "ex-stairs", exerciseName = "Stair Machine", exerciseType = ExerciseType.FLOORS_DURATION,
        sets = listOf(
            WorkoutSetUiModel(id = "f1", customMetric = 40.0, durationSeconds = 600, isCompleted = true, previousLabel = "40 / 10:00"),
            WorkoutSetUiModel(id = "f2", customMetric = 40.0, durationSeconds = 600, previousLabel = "40 / 10:00"),
        ),
    )

    private fun assertBarbellHoldAligned(widthDp: Int) {
        showCard(barbellHold(), widthDp = widthDp)
        for (set in listOf("Set 1", "Set 2")) {
            assertCentred(rule.onNodeWithContentDescription("$set, seconds"), rule.onNodeWithText("TIME"), "$set TIME, plate calculator row, ${widthDp}dp")
            assertCentred(rule.onNodeWithContentDescription("$set, weight in kilograms"), rule.onNodeWithText("KG"), "$set KG, plate calculator row, ${widthDp}dp")
        }
        assertHeaderWhole("TIME", "Barbell Hold at ${widthDp}dp")
    }

    @Test
    fun `Barbell Hold keeps TIME and KG under their headers beside the plate calculator and the timer cell, at 392dp`() = assertBarbellHoldAligned(392)

    @Test
    fun `Barbell Hold keeps TIME and KG under their headers beside the plate calculator and the timer cell, at 412dp`() = assertBarbellHoldAligned(412)

    private fun assertStairMachineAligned(widthDp: Int) {
        showCard(stairMachine(), widthDp = widthDp)
        for (set in listOf("Set 1", "Set 2")) {
            assertCentred(rule.onNodeWithContentDescription("$set, seconds"), rule.onNodeWithText("TIME"), "$set TIME, COUNT row, ${widthDp}dp")
            assertCentred(rule.onNodeWithContentDescription("$set, value"), rule.onNodeWithText("COUNT"), "$set COUNT, ${widthDp}dp")
        }
        assertHeaderWhole("COUNT", "Stair Machine at ${widthDp}dp")
    }

    @Test
    fun `Stair Machine keeps COUNT and TIME under their headers, at 392dp`() = assertStairMachineAligned(392)

    @Test
    fun `Stair Machine keeps COUNT and TIME under their headers, at 412dp`() = assertStairMachineAligned(412)

    @Test
    fun `a focused TIME field stays under its header`() {
        showCard(plank(), widthDp = 392)
        rule.onNodeWithContentDescription("Set 2, seconds").requestFocus()
        rule.waitForIdle()
        assertCentred(rule.onNodeWithContentDescription("Set 2, seconds"), rule.onNodeWithText("TIME"), "focused row at 392dp")
    }

    @Test
    fun `a checked circuit row keeps its TIME field under the shared header`() {
        showRound(listOf(plank(), plank().copy(id = "we3", exerciseId = "ex-wall", exerciseName = "Wall Sit")), widthDp = 392)
        // Set 1 of each station is checked in the fixture; the field is still centred under the one TIME header.
        assertCentred(rule.onAllNodesWithContentDescription("Set 1, seconds")[0], rule.onNodeWithText("TIME"), "checked circuit row at 392dp")
    }

    @Test
    fun `the time hint is read out when it appears`() {
        showCard(plank(timerMode = "COUNTDOWN"), timeHintSetId = "s3")
        val node = rule.onNodeWithText("Type a time to count down from.").fetchSemanticsNode()
        assertTrue(node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.LiveRegion))
    }

    @Test
    fun `a checked row shows no time hint`() {
        showCard(plank(timerMode = "COUNTDOWN"), timeHintSetId = "s1")
        rule.onAllNodesWithText("Type a time to count down from.").assertCountEquals(0)
    }
}
