package com.enil.logez.feature.routines

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.enil.logez.core.designsystem.LogEzTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Recent row at a narrow width and the largest font scale: both targets stay on screen and at least 48dp tall. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", fontScale = 2.0f)
class RecentWorkoutRowLargeTextTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the title and the Start pill are both shown and tall enough to hit, and the row is labelled`() {
        val card = RecentWorkoutCardModel("w1", "Push Day", 1_000L, 3_120, 3, isFromRoutine = true)

        rule.setContent { LogEzTheme { RecentWorkoutRow(card, compactDate = true, onOpen = {}, onStart = {}) } }

        rule.onNodeWithText("Start").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Push Day").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNode(SemanticsMatcher("click label is Open Push Day") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open Push Day" }).assertExists()
    }
}
