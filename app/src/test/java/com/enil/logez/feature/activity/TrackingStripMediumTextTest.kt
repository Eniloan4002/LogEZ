package com.enil.logez.feature.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** At 130% the three-column map strip already broke "Distance (km)" and "Avg pace /km" mid-phrase on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w392dp-h800dp", fontScale = 1.3f)
class TrackingStripMediumTextTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the strip stacks at 130 percent, so no label is squeezed into a third of the width`() {
        rule.setContent {
            LogEzTheme {
                Column { MapStrip(3460.3, DistanceUnit.KM, stats = { LiveTrackingStats(1265, null, 369.0, GpsSignal.GOOD, nowMillis = 0L) }) }
            }
        }
        val distance = rule.onNodeWithText("Distance (km)", useUnmergedTree = true).getBoundsInRoot()
        val time = rule.onNodeWithText("Time", useUnmergedTree = true).getBoundsInRoot()
        val pace = rule.onNodeWithText("Avg pace /km", useUnmergedTree = true).getBoundsInRoot()
        // Side by side, the three tops would be equal; stacked, each stat sits under the one before.
        assertTrue(distance.bottom <= time.top)
        assertTrue(time.bottom <= pace.top)
    }
}
