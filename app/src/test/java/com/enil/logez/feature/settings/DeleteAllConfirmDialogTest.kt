package com.enil.logez.feature.settings

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.enil.logez.core.designsystem.LogEzTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Delete everything?" (first-run plan, Decision 7): its longer body scrolls, so at 200% font in
 * landscape the backup warning at its end can still be reached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp", fontScale = 2.0f)
class DeleteAllConfirmDialogTest {
    @get:Rule val rule = createComposeRule()

    private var confirms = 0
    private var dismisses = 0

    private fun show() {
        rule.setContent {
            LogEzTheme {
                DeleteAllConfirmDialog(onConfirm = { confirms++ }, onDismiss = { dismisses++ })
            }
        }
    }

    @Test
    fun `the body says setup comes back and sits in a scrolling container`() {
        show()

        rule.onNode(
            hasScrollAction() and
                hasAnyDescendant(hasText("LogEZ then shows its setup screen the next time it opens.", substring = true)),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `Delete everything confirms and Cancel dismisses`() {
        show()

        rule.onNodeWithText("Delete everything").performClick()
        rule.onNodeWithText("Cancel").performClick()

        assertEquals(1, confirms)
        assertEquals(1, dismisses)
    }
}
