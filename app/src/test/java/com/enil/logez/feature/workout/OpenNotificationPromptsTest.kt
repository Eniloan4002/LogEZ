package com.enil.logez.feature.workout

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.designsystem.LogEzTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One notification prompt per workout in a window (first-run plan O1c's API 33 check). After a
 * process death with the Workout tab's prompt open, the restored tab shows its prompt again and
 * cold-start recovery asks to resume the same workout; that used to show a second prompt and push a
 * second logger. Android 14 here, so POST_NOTIFICATIONS starts ungranted and the prompt shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenNotificationPromptsTest {
    @get:Rule val rule = createComposeRule()

    private val prompt = "Show your workout on the lock screen?"
    private val tabNavigations = mutableListOf<String>()
    private val recoveryNavigations = mutableListOf<String>()
    private lateinit var startFromTab: (String) -> Unit
    private lateinit var resumeFromRecovery: (String) -> Unit

    @Before
    fun forgetEarlierAnswers() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("logez_ui_flags", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun show(shared: Boolean) {
        rule.setContent {
            LogEzTheme {
                val registry = remember { OpenNotificationPrompts() }
                CompositionLocalProvider(LocalOpenNotificationPrompts provides registry.takeIf { shared }) {
                    // The app root's recovery launcher and the Workout tab's, as in LogEzApp.
                    resumeFromRecovery = rememberStartWorkoutSession { recoveryNavigations += it }
                    startFromTab = rememberStartWorkoutSession { tabNavigations += it }
                }
            }
        }
    }

    @Test
    fun `recovery leaves an open prompt for the same workout to answer, and one logger opens`() {
        show(shared = true)
        rule.runOnIdle { startFromTab("w1") }
        rule.onAllNodesWithText(prompt).assertCountEquals(1)

        rule.runOnIdle { resumeFromRecovery("w1") }

        rule.onAllNodesWithText(prompt).assertCountEquals(1)
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()
        assertEquals(listOf("w1"), tabNavigations)
        assertEquals(emptyList<String>(), recoveryNavigations)
        rule.onAllNodesWithText(prompt).assertCountEquals(0)
    }

    @Test
    fun `without a shared registry both prompt, as before the fix (control)`() {
        show(shared = false)
        rule.runOnIdle { startFromTab("w1") }

        rule.runOnIdle { resumeFromRecovery("w1") }

        rule.onAllNodesWithText(prompt).assertCountEquals(2)
    }

    @Test
    fun `a prompt for another workout does not stop recovery`() {
        show(shared = true)
        rule.runOnIdle { startFromTab("w1") }

        rule.runOnIdle { resumeFromRecovery("w2") }

        rule.onAllNodesWithText(prompt).assertCountEquals(2)
    }

    @Test
    fun `once the prompt is answered the workout is no longer skipped`() {
        show(shared = true)
        rule.runOnIdle { startFromTab("w1") }
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()

        // Declined, so the next start goes straight to the logger.
        rule.runOnIdle { resumeFromRecovery("w1") }

        assertEquals(listOf("w1"), tabNavigations)
        assertEquals(listOf("w1"), recoveryNavigations)
    }
}
