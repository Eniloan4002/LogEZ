package com.enil.logez.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The gate's message in the app's snackbar after a restore from setup (O1e). Robolectric runs en-US. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunMessageSnackbarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val hostState = SnackbarHostState()
    private var message by mutableStateOf<FirstRunMessage?>(null)
    private var composed by mutableStateOf(true)
    private var shown = 0

    /** The window's lifecycle, moved by hand: started unless a test hides the window. */
    private val window = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun show(initial: FirstRunMessage, windowState: Lifecycle.State = Lifecycle.State.RESUMED) {
        message = initial
        window.registry.currentState = windowState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LogEzTheme {
                SnackbarHost(hostState)
                if (composed) {
                    CompositionLocalProvider(LocalLifecycleOwner provides window) {
                        FirstRunMessageSnackbar(message, hostState, onShown = {
                            shown++
                            message = null
                        })
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    @Test
    fun `the left-out count is spelled out, stays up long, and is cleared only once it has gone`() {
        show(FirstRunMessage(R.plurals.data_restore_done_left_out, count = 1))

        rule.onNodeWithText("Restored. 1 unfinished workout in the backup was left out.").assertExists()
        assertEquals(SnackbarDuration.Long, hostState.currentSnackbarData?.visuals?.duration)
        assertEquals(0, shown)

        rule.mainClock.advanceTimeBy(11_000L)
        rule.waitForIdle()

        assertEquals(1, shown)
        rule.onNodeWithText("Restored", substring = true).assertDoesNotExist()
    }

    @Test
    fun `plain Restored is short, and a restore that did not finish stays up long`() {
        show(FirstRunMessage(R.string.data_restore_done))

        rule.onNodeWithText("Restored").assertExists()
        assertEquals(SnackbarDuration.Short, hostState.currentSnackbarData?.visuals?.duration)
        assertEquals(
            SnackbarDuration.Long,
            firstRunMessageDuration(FirstRunMessage(R.string.data_restore_incomplete)),
        )
        assertEquals(
            SnackbarDuration.Long,
            firstRunMessageDuration(FirstRunMessage(R.string.data_restore_blocked_in_progress)),
        )
    }

    @Test
    fun `a window that goes while the message shows leaves it held for the next one`() {
        show(FirstRunMessage(R.string.data_restore_incomplete))
        rule.onNodeWithText("Restore did not finish. LogEZ will try to finish it the next time it starts.").assertExists()

        // The old window's effect is cancelled, as a rotation does.
        composed = false
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()

        assertEquals(0, shown)
        assertEquals(FirstRunMessage(R.string.data_restore_incomplete), message)
    }

    @Test
    fun `a restore that ends while the window is hidden shows its message once the window is back`() {
        // A widget tap opened a second window over this one, which is stopped.
        show(FirstRunMessage(R.string.data_restore_done), windowState = Lifecycle.State.CREATED)

        rule.mainClock.advanceTimeBy(11_000L)
        rule.waitForIdle()
        rule.onNodeWithText("Restored").assertDoesNotExist()
        assertEquals(0, shown)
        assertEquals(FirstRunMessage(R.string.data_restore_done), message)

        window.registry.currentState = Lifecycle.State.RESUMED
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onNodeWithText("Restored").assertExists()

        rule.mainClock.advanceTimeBy(5_000L)
        rule.waitForIdle()
        assertEquals(1, shown)
        assertNull(message)
    }

    @Test
    fun `a window hidden while the message shows takes it down and shows it again on return`() {
        show(FirstRunMessage(R.string.data_restore_incomplete))
        val text = "Restore did not finish. LogEZ will try to finish it the next time it starts."
        rule.onNodeWithText(text).assertExists()

        window.registry.currentState = Lifecycle.State.CREATED
        rule.mainClock.advanceTimeBy(11_000L)
        rule.waitForIdle()
        rule.onNodeWithText(text).assertDoesNotExist()
        assertEquals(0, shown)

        window.registry.currentState = Lifecycle.State.RESUMED
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onNodeWithText(text).assertExists()

        rule.mainClock.advanceTimeBy(11_000L)
        rule.waitForIdle()
        assertEquals(1, shown)
    }
}
