package com.enil.logez.feature.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.width
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.wellness.HeartRateAccess
import com.enil.logez.core.wellness.HeartRateSample
import java.time.Instant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.height
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The live screen's pieces on a 360 x 640 dp phone at 200% font: nothing clips, jumps or loses its unit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", fontScale = 2.0f)
class TrackingLargeTextTest {
    @get:Rule val rule = createComposeRule()

    private fun stats(gps: GpsSignal) = LiveTrackingStats(1265, null, 369.0, gps, nowMillis = 0L)

    @Test
    fun `the note line keeps its height at 200 percent, so the card below never jumps`() {
        var gps by mutableStateOf(GpsSignal.GOOD)
        rule.setContent { LogEzTheme { DistanceHero(3460.3, DistanceUnit.KM, stats = { stats(gps) }) } }
        val good = rule.onRoot().getBoundsInRoot().height
        gps = GpsSignal.WEAK
        rule.waitForIdle()
        assertEquals(good.value, rule.onRoot().getBoundsInRoot().height.value, 0.5f)
        gps = GpsSignal.FINDING
        rule.waitForIdle()
        assertEquals(good.value, rule.onRoot().getBoundsInRoot().height.value, 0.5f)
    }

    @Test
    fun `Resume still takes Pause's place at 200 percent, and the buttons grow rather than clip`() {
        var paused by mutableStateOf(false)
        rule.setContent { LogEzTheme { ActionBar(isPaused = paused, onPause = {}, onResume = {}, onFinish = {}) } }
        val pause = rule.onNodeWithText("Pause").getBoundsInRoot()
        val rootBottom = rule.onRoot().getBoundsInRoot().bottom.value
        paused = true
        rule.waitForIdle()
        val resume = rule.onNodeWithText("Resume").getBoundsInRoot()
        val finish = rule.onNodeWithText("Finish").getBoundsInRoot()
        assertEquals(rootBottom - pause.bottom.value, rule.onRoot().getBoundsInRoot().bottom.value - resume.bottom.value, 0.5f)
        assertEquals(pause.height.value, resume.height.value, 0.5f)
        // The label fits inside its button: a button's height is a minimum, never a cap.
        assertTrue(resume.height.value >= 56f)
        assertTrue(finish.height.value >= 48f)
    }

    @Test
    fun `the strip stacks at 200 percent, so no label is broken inside a word`() {
        rule.setContent {
            LogEzTheme { Column { MapStrip(3460.3, DistanceUnit.KM, stats = { stats(GpsSignal.GOOD) }) } }
        }
        val rootWidth = rule.onRoot().getBoundsInRoot().width.value
        // One line of the label at this font is about 40dp tall; a word cut in two would be taller.
        val distance = rule.onNodeWithText("Distance (km)", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the label should sit on one line, was ${distance.height.value}dp tall", distance.height.value < 50f)
        // Each stat has the row to itself, one under the other.
        val time = rule.onNodeWithText("Time", useUnmergedTree = true).getBoundsInRoot()
        val pace = rule.onNodeWithText("Avg pace /km", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(distance.bottom <= time.top)
        assertTrue(time.bottom <= pace.top)
        assertTrue(distance.right.value <= rootWidth && pace.right.value <= rootWidth)
    }

    private val running = ActivityTrackingState(workoutId = "w-1", workoutSetId = "set-1", startedAtMillis = 1_000_000L, distanceMeters = 3460.3)
    private val noHeartRate = TrackingHeartRateInputs(HeartRateAccess.GRANTED, emptyList(), false, {}, {}, {})

    @Composable
    private fun Screen(
        state: ActivityTrackingState,
        activity: GpsActivity? = GpsActivity.RUN,
        heartRate: TrackingHeartRateInputs = noHeartRate,
    ) {
        LogEzTheme {
            ActivityTrackingContent(
                state = state,
                unit = DistanceUnit.KM,
                maxHeartRateBpm = 170,
                stats = { LiveTrackingStats(1265, if (state.isPaused) 42 else null, 369.0, GpsSignal.GOOD, nowMillis = 0L) },
                activity = activity,
                heartRate = heartRate,
                onPause = {},
                onResume = {},
                onFinish = {},
                onDiscard = {},
                onHide = {},
                routeMap = { modifier -> Box(modifier.testTag("map")) { Text("the live map") } },
            )
        }
    }

    @Test
    fun `paused on the Map view at 360 by 640 and 200 percent the map keeps at least 200dp, and the rest scrolls`() {
        rule.setContent { Screen(running.copy(isPaused = true, pausedAtMillis = 1_500_000L)) }
        rule.onNodeWithText("MAP").performClick()
        rule.waitForIdle()
        val map = rule.onNodeWithTag("map").getBoundsInRoot()
        assertTrue("the map was squeezed to ${map.height.value}dp", map.height.value >= 200f)
        // The banner is above the map, and the strip's last stat is reachable by scrolling.
        rule.onNodeWithText("PAUSED").assertExists()
        rule.onNodeWithText("Avg pace /km").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `paused on the Stats view at 200 percent the banner is part of the scrolling list and Time is reachable`() {
        rule.setContent { Screen(running.copy(isPaused = true, pausedAtMillis = 1_500_000L)) }
        rule.onNodeWithText("PAUSED").assertIsDisplayed()
        rule.onNodeWithText("Time").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the title and the GPS chip fit the width at 200 percent`() {
        rule.setContent { Screen(running) }
        val rootWidth = rule.onRoot().getBoundsInRoot().width.value
        rule.onNodeWithText("RUN").assertIsDisplayed()
        rule.onNodeWithText("GPS").assertIsDisplayed()
        assertTrue(rule.onNodeWithText("GPS").getBoundsInRoot().right.value <= rootWidth)
    }

    @Test
    fun `a live heart-rate reading keeps its number, unit and zone inside the card at 200 percent`() {
        val live = TrackingHeartRateInputs(
            HeartRateAccess.GRANTED, listOf(HeartRateSample(Instant.ofEpochMilli(0L), 142)), false, {}, {}, {},
        )
        rule.setContent { Screen(running, heartRate = live) }
        val rootWidth = rule.onRoot().getBoundsInRoot().width.value
        val bpm = rule.onNodeWithText("142").performScrollTo().getBoundsInRoot()
        val zone = rule.onNodeWithText("Zone 4").getBoundsInRoot()
        assertTrue(bpm.right.value <= rootWidth && zone.right.value <= rootWidth)
        // The reading is not squeezed into a wrapped column: its digits stay on one line.
        assertTrue("the bpm number wrapped: ${bpm.height.value}dp tall", bpm.height.value < 110f)
        assertTrue(bpm.right <= zone.left)
    }
}
