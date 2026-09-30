package com.enil.logez.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Export & backup: the working label must be on screen while a long job runs, even when
 * the list has been scrolled. It used to be the list's first item and was inserted above the viewport.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataScreenLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the working label is displayed when a job starts with the list scrolled`() {
        var working by mutableStateOf<DataJob.Working?>(null)
        rule.setContent {
            LogEzTheme {
                DataScreenLayout(working = working, modifier = Modifier.fillMaxSize()) {
                    items(count = 40, key = { "row_$it" }) {
                        Text("Row $it", modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
        rule.onNode(hasScrollAction()).performScrollToIndex(3)

        working = DataJob.Working(R.string.data_restore_reading)
        rule.waitForIdle()

        rule.onNodeWithText("Reading the backup…").assertIsDisplayed()
        rule.onNodeWithText("Row 3").assertIsDisplayed()
    }
}
