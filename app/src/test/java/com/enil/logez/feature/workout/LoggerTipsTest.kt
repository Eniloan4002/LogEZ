package com.enil.logez.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.WorkoutStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan (O1g) in the live logger: when the "Logging tips" card may show, its copy, and the
 * timer's 48dp tap target (Finding 5) that the timer line points at.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoggerTipsTest {
    @get:Rule val rule = createComposeRule()

    private val bench = WorkoutExerciseUiModel(
        id = "we1",
        exerciseId = "ex-1",
        exerciseName = "Bench Press (Barbell)",
        exerciseType = ExerciseType.WEIGHT_REPS,
        equipment = Equipment.BARBELL,
        sets = emptyList(),
    )

    @Test
    fun `the logging tips show in a live workout once it has an exercise`() {
        assertTrue(loggingTipsVisible(tipCanShow = true, uiState = WorkoutLoggerUiState(isLoading = false, exercises = listOf(bench))))
    }

    @Test
    fun `the logging tips wait for the first exercise`() {
        assertFalse(loggingTipsVisible(tipCanShow = true, uiState = WorkoutLoggerUiState(isLoading = false, exercises = emptyList())))
    }

    @Test
    fun `the logging tips never show while editing a saved workout`() {
        assertFalse(loggingTipsVisible(tipCanShow = true, uiState = WorkoutLoggerUiState(isLoading = false, isEditMode = true, exercises = listOf(bench))))
    }

    @Test
    fun `the logging tips never show in a circuit, which has no superset and its own body`() {
        assertFalse(
            loggingTipsVisible(
                tipCanShow = true,
                uiState = WorkoutLoggerUiState(isLoading = false, structure = WorkoutStructure.CIRCUIT, exercises = listOf(bench)),
            ),
        )
    }

    @Test
    fun `the logging tips stay hidden once seen or dismissed`() {
        assertFalse(loggingTipsVisible(tipCanShow = false, uiState = WorkoutLoggerUiState(isLoading = false, exercises = listOf(bench))))
    }

    @Test
    fun `the logging tips card quotes the real menu labels, with Got it`() {
        var gotIt = 0
        rule.setContent { LogEzTheme { LoggingTipsCard(onGotIt = { gotIt++ }) } }

        rule.onNodeWithText("Logging tips").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText("Tap a set's number to mark it as a Warm-up Set, Failure Set or Dropset, or to Delete it.").assertIsDisplayed()
        rule.onNodeWithText("To pair two exercises in a superset, open the three-dot menu on one, choose Add to Superset, then tap the other.").assertIsDisplayed()
        rule.onNodeWithText("To pause the workout timer, tap it at the top, then Pause Workout Timer.").assertIsDisplayed()
        rule.onNodeWithText("PREVIOUS shows what you logged last time. It fills in from your second session with an exercise.").assertIsDisplayed()
        rule.onNodeWithText("Got it").performClick()
        assertEquals(1, gotIt)
    }

    // --- The timer's tap target ---

    private var timerClicks = 0

    private fun setTimer(enabled: Boolean = true) {
        rule.setContent {
            LogEzTheme {
                Column {
                    Spacer(Modifier.height(100.dp))
                    TimerTapTarget(enabled = enabled, onClick = { timerClicks++ }) {
                        Text("0:41", modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    /** Taps [dpAboveBottom] above the timer text's bottom edge, at its horizontal centre. */
    private fun tapAboveTimerBottom(dpAboveBottom: Float) {
        val bounds = rule.onNodeWithText("0:41").getBoundsInRoot()
        val x = (bounds.left + bounds.right) / 2
        val y = bounds.bottom - dpAboveBottom.dp
        rule.onRoot().performTouchInput { click(Offset(x.toPx(), y.toPx())) }
        rule.waitForIdle()
    }

    @Test
    fun `the timer keeps its own size, so nothing on the bar moves`() {
        setTimer()
        rule.onNodeWithText("0:41").assertHeightIsEqualTo(16.dp)
    }

    @Test
    fun `a tap up to 48dp above the timer's bottom edge opens its menu`() {
        setTimer()
        tapAboveTimerBottom(40f)
        assertEquals(1, timerClicks)
    }

    @Test
    fun `a tap on the timer text itself still opens its menu`() {
        setTimer()
        tapAboveTimerBottom(8f)
        assertEquals(1, timerClicks)
    }

    @Test
    fun `a tap beyond 48dp above the timer does nothing`() {
        setTimer()
        tapAboveTimerBottom(60f)
        assertEquals(0, timerClicks)
    }

    /** Taps [dpFromStart] to the end of the timer text's start edge, at its vertical centre. */
    private fun tapFromTimerStart(dpFromStart: Float) {
        val bounds = rule.onNodeWithText("0:41").getBoundsInRoot()
        val x = bounds.left + dpFromStart.dp
        val y = (bounds.top + bounds.bottom) / 2
        rule.onRoot().performTouchInput { click(Offset(x.toPx(), y.toPx())) }
        rule.waitForIdle()
    }

    @Test
    fun `a tap past the timer text's end, within 48dp of its start, opens its menu`() {
        setTimer()
        val bounds = rule.onNodeWithText("0:41").getBoundsInRoot()
        assertTrue("the text is ${bounds.right - bounds.left} wide", bounds.right - bounds.left < 40.dp)
        tapFromTimerStart(44f)
        assertEquals(1, timerClicks)
    }

    @Test
    fun `a tap beyond 48dp from the timer's start does nothing`() {
        setTimer()
        tapFromTimerStart(60f)
        assertEquals(0, timerClicks)
    }

    @Test
    fun `the timer takes no taps before the first exercise`() {
        setTimer(enabled = false)
        tapAboveTimerBottom(40f)
        tapAboveTimerBottom(8f)
        assertEquals(0, timerClicks)
    }

    @Test
    fun `TalkBack finds one timer button, with its text`() {
        setTimer()
        rule.onAllNodesWithText("0:41").assertCountEquals(1)
        rule.onNodeWithText("0:41")
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, timerClicks)
        rule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }
}
