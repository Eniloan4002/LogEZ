package com.enil.logez.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the first-run gate draws above the app in each state, the hand-off, and the hidden app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FirstRunOverlayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val setup = FirstRunGateState.ShowSetup(
        preselected = SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY),
        regionNoteVisible = true,
    )
    private var handOffs = 0
    private var resumes = 0
    private val continued = mutableListOf<SetupChoices>()
    private var state by mutableStateOf<FirstRunGateState>(FirstRunGateState.Loading)

    private var appClicks = 0

    /** The real host, with a clickable stand-in for the app underneath. */
    private fun show(initial: FirstRunGateState) {
        state = initial
        rule.setContent {
            LogEzTheme {
                FirstRunHost(
                    state = state,
                    onContinue = { continued += it },
                    onHandOff = { handOffs++ },
                    onResume = { resumes++ },
                ) { hidden ->
                    Box(hidden.fillMaxSize().clickable { appClicks++ }) {
                        Text("History underneath")
                    }
                }
            }
        }
    }

    /** Taps the middle of the screen, where the app underneath would take it. */
    private fun tapMiddle() {
        rule.onRoot().performTouchInput { click(center) }
        rule.waitForIdle()
    }

    @Test
    fun `Loading covers the app without showing setup`() {
        show(FirstRunGateState.Loading)

        rule.onNodeWithText("BEFORE YOU START").assertDoesNotExist()
        rule.onNodeWithTag("firstrun_continue").assertDoesNotExist()
        assertEquals(0, handOffs)
    }

    @Test
    fun `ShowSetup shows the setup screen and Continue reports the choices`() {
        show(setup)

        rule.onNodeWithText("BEFORE YOU START").assertExists()
        rule.onNodeWithTag("firstrun_continue").performClick()
        assertEquals(listOf(setup.preselected), continued)
    }

    @Test
    fun `setup's restore reads the same choices the screen's pills change`() {
        var restoreChoices: SetupChoicesState? = null
        state = setup
        rule.setContent {
            LogEzTheme {
                FirstRunHost(
                    state = state,
                    onContinue = { continued += it },
                    onHandOff = { handOffs++ },
                    onResume = { resumes++ },
                    setupRestore = { choices ->
                        restoreChoices = choices
                        SetupRestoreBinding.Inert
                    },
                ) { hidden -> Box(hidden.fillMaxSize()) }
            }
        }

        rule.onNodeWithContentDescription("Pounds (lb)").performClick()
        rule.waitForIdle()

        assertEquals(
            SetupChoices(WeightUnit.LB, DistanceUnit.KM, DayOfWeek.SUNDAY),
            restoreChoices?.choices,
        )
    }

    @Test
    fun `ShowApp draws nothing over the app`() {
        show(FirstRunGateState.ShowApp)

        rule.onNodeWithText("BEFORE YOU START").assertDoesNotExist()
        rule.onNodeWithText("History underneath").assertExists()
    }

    @Test
    fun `the hand-off runs once and keeps setup on screen, disabled and with the user's choices`() {
        show(setup)
        rule.onNodeWithContentDescription("Pounds (lb)").performClick()

        state = setup.copy(working = true)
        rule.waitForIdle()
        state = FirstRunGateState.HandOff(setup)
        rule.waitForIdle()

        assertEquals(1, handOffs)
        rule.onNodeWithText("BEFORE YOU START").assertExists()
        rule.onNodeWithContentDescription("Pounds (lb)").assertIsSelected()
        rule.onNodeWithTag("firstrun_continue").assertIsNotEnabled()

        state = FirstRunGateState.ShowApp
        rule.waitForIdle()
        rule.onNodeWithText("BEFORE YOU START").assertDoesNotExist()
        assertEquals(1, handOffs)
    }

    @Test
    fun `setup rechecks the flag every time it resumes`() {
        show(setup)
        assertEquals(1, resumes)

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        assertEquals(2, resumes)
    }

    // ---- what reaches the app underneath ----

    @Test
    fun `Loading takes touches and hides the app from TalkBack`() {
        show(FirstRunGateState.Loading)

        tapMiddle()

        assertEquals(0, appClicks)
        rule.onNodeWithText("History underneath").assertDoesNotExist()
    }

    @Test
    fun `setup takes touches and hides the app from TalkBack`() {
        show(setup)

        tapMiddle()

        assertEquals(0, appClicks)
        rule.onNodeWithText("History underneath").assertDoesNotExist()
    }

    @Test
    fun `the hand-off frames take touches and hide the app from TalkBack`() {
        show(FirstRunGateState.HandOff(setup))

        tapMiddle()

        assertEquals(0, appClicks)
        rule.onNodeWithText("History underneath").assertDoesNotExist()
        rule.onNodeWithText("BEFORE YOU START").assertExists()
    }

    @Test
    fun `a hand-off in a recreated Activity draws setup, not a blank cover`() {
        // No ShowSetup was ever composed here, as after a rotation during the hand-off frames.
        show(FirstRunGateState.HandOff(setup))

        rule.onNodeWithText("BEFORE YOU START").assertExists()
        rule.onNodeWithTag("firstrun_continue").assertIsNotEnabled()
        assertEquals(1, handOffs)
    }

    @Test
    fun `once the app shows, touches and TalkBack reach it`() {
        show(FirstRunGateState.ShowApp)

        tapMiddle()

        assertEquals(1, appClicks)
        rule.onNodeWithText("History underneath").assertExists()
    }

    @Test
    fun `the host exposes test tags as resource ids`() {
        show(setup)

        rule.onNode(SemanticsMatcher.expectValue(SemanticsPropertiesAndroid.TestTagsAsResourceId, true))
            .assertExists()
    }

    @Test
    fun `the app is not rechecked on resume once it is showing`() {
        show(FirstRunGateState.ShowApp)

        assertEquals(0, resumes)
    }

    // ---- the app under the overlay ----

    @Test
    fun `hidden app content is left out of the semantics tree`() {
        rule.setContent {
            Box(Modifier.hiddenBehindFirstRun(hidden = true)) { Text("Start Empty Workout") }
        }

        rule.onNodeWithText("Start Empty Workout").assertDoesNotExist()
    }

    @Test
    fun `visible app content stays in the semantics tree`() {
        rule.setContent {
            Box(Modifier.hiddenBehindFirstRun(hidden = false)) { Text("Start Empty Workout") }
        }

        rule.onNodeWithText("Start Empty Workout").assertExists()
    }

    @Test
    fun `keyboard focus never enters the hidden app`() {
        val focus = moveFocusThrough(hidden = true)

        assertFalse("focus reached the hidden app", focus.appFocused)
        assertTrue("focus never moved at all", focus.overlayFocused)
    }

    @Test
    fun `keyboard focus reaches the app once it is no longer hidden`() {
        val focus = moveFocusThrough(hidden = false)

        assertTrue(focus.appFocused)
    }

    private class FocusSeen {
        var appFocused = false
        var overlayFocused = false
    }

    /** Tabs forwards through an app item and an overlay item, recording which ever get focus. */
    private fun moveFocusThrough(hidden: Boolean): FocusSeen {
        val seen = FocusSeen()
        lateinit var focusManager: FocusManager
        rule.setContent {
            focusManager = LocalFocusManager.current
            Column {
                Box(Modifier.hiddenBehindFirstRun(hidden)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .onFocusChanged { if (it.isFocused) seen.appFocused = true }
                            .focusable(),
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .onFocusChanged { if (it.isFocused) seen.overlayFocused = true }
                        .focusable(),
                )
            }
        }
        repeat(4) {
            rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }
        }
        rule.waitForIdle()
        return seen
    }
}
