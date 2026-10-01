package com.enil.logez.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWidgetRefresher
import com.enil.logez.feature.onboarding.TipsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** First-run plan (O1g): Settings > Workouts ends with "Show tips again", which works on any install. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowTipsAgainRowTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `Show tips again re-enables the tips and says so`() {
        // The debug install over existing data skipped setup, so it has no tips until this row.
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.EXISTING)
        rule.setContent {
            LogEzTheme {
                SettingsScreen(
                    onBack = {}, onSoundsClick = {}, onPlateEquipmentClick = {}, onWarmupSetsClick = {}, onDataClick = {},
                    viewModel = SettingsViewModel(FakeSettingsRepository(), FakeWidgetRefresher()),
                    tipsViewModel = TipsViewModel(store, AppLogger.NoOp),
                )
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Show tips again"))
        rule.onNodeWithText("The picker, logger and routine builder tips show once more.").assertIsDisplayed()

        rule.onNodeWithText("Show tips again").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Tips will show again.").assertIsDisplayed()
        assertTrue(store.reenabled)
        assertEquals(FirstRunPath.EXISTING, store.storedPath)
    }
}
