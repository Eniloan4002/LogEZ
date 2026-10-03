package com.enil.logez.feature.activity

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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

/**
 * The pieces of the live walk/run screen with the approved mockups' sample run (README "Sample data":
 * 3.46 km at 21:05, avg pace 6:05, 9.8 km/h). Expected text is literal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingScreenPartsTest {
    @get:Rule val rule = createComposeRule()

    private fun stats(
        gps: GpsSignal = GpsSignal.GOOD,
        elapsed: Int = 1265,
        paceNow: Double? = 369.0,
        pausedFor: Int? = null,
    ) = LiveTrackingStats(elapsed, pausedFor, paceNow, gps, nowMillis = 0L)

    // ---- GPS chip ----

    @Test
    fun `the GPS chip names each state in words`() {
        var signal by mutableStateOf(GpsSignal.GOOD)
        rule.setContent { LogEzTheme { GpsChip(signal = { signal }) } }
        rule.onNodeWithText("GPS").assertIsDisplayed()

        signal = GpsSignal.FINDING
        rule.onNodeWithText("Finding GPS").assertIsDisplayed()

        signal = GpsSignal.WEAK
        rule.onNodeWithText("Weak GPS").assertIsDisplayed()
    }

    // ---- Action bar: two-step end ----

    @Test
    fun `while moving there is only Pause`() {
        rule.setContent { LogEzTheme { ActionBar(isPaused = false, onPause = {}, onResume = {}, onFinish = {}) } }
        rule.onNodeWithText("Pause").assertIsDisplayed()
        rule.onNodeWithText("Finish").assertDoesNotExist()
        rule.onNodeWithText("Resume").assertDoesNotExist()
    }

    @Test
    fun `paused shows Finish above Resume and no Pause`() {
        rule.setContent { LogEzTheme { ActionBar(isPaused = true, onPause = {}, onResume = {}, onFinish = {}) } }
        rule.onNodeWithText("Pause").assertDoesNotExist()
        val finish = rule.onNodeWithText("Finish").getBoundsInRoot()
        val resume = rule.onNodeWithText("Resume").getBoundsInRoot()
        assertTrue("Finish should sit above Resume", finish.bottom <= resume.top)
    }

    @Test
    fun `Resume takes the exact place Pause was, so a fast second tap on Pause can only resume`() {
        var paused by mutableStateOf(false)
        rule.setContent { LogEzTheme { ActionBar(isPaused = paused, onPause = {}, onResume = {}, onFinish = {}) } }
        // The bar is anchored to the bottom of the screen, so "the same place" is the same distance from
        // its bottom edge (the paused bar is taller, with Finish above) and the same height.
        val pauseBounds = rule.onNodeWithText("Pause").getBoundsInRoot()
        val pauseFromBottom = rule.onRoot().getBoundsInRoot().bottom.value - pauseBounds.bottom.value
        paused = true
        rule.waitForIdle()
        val resumeBounds = rule.onNodeWithText("Resume").getBoundsInRoot()
        val resumeFromBottom = rule.onRoot().getBoundsInRoot().bottom.value - resumeBounds.bottom.value
        assertEquals(pauseFromBottom, resumeFromBottom, 0.5f)
        assertEquals(pauseBounds.height.value, resumeBounds.height.value, 0.5f)
    }

    @Test
    fun `each button calls its own action`() {
        val calls = mutableListOf<String>()
        var paused by mutableStateOf(false)
        rule.setContent {
            LogEzTheme { ActionBar(isPaused = paused, onPause = { calls += "pause" }, onResume = { calls += "resume" }, onFinish = { calls += "finish" }) }
        }
        rule.onNodeWithText("Pause").performClick()
        paused = true
        rule.waitForIdle()
        rule.onNodeWithText("Finish").performClick()
        rule.onNodeWithText("Resume").performClick()
        assertEquals(listOf("pause", "finish", "resume"), calls)
    }

    // ---- The 500 ms Finish guard ----

    /** A state change made after the first composition, applied at once: with the clock stopped the looper that would deliver it never runs. */
    private fun setPaused(change: () -> Unit) = rule.runOnUiThread {
        change()
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `Finish is not armed while moving`() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Text(if (rememberFinishArmed(isPaused = false)) "armed" else "not armed") }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("not armed").assertExists()
    }

    @Test
    fun `Finish ignores taps for 500 ms after a pause begins, then arms`() {
        rule.mainClock.autoAdvance = false
        var paused by mutableStateOf(false)
        rule.setContent { Text(if (rememberFinishArmed(paused)) "armed" else "not armed") }
        setPaused { paused = true }
        rule.mainClock.advanceTimeBy(20)
        rule.onNodeWithText("not armed").assertExists()
        rule.mainClock.advanceTimeBy(FINISH_GUARD_MILLIS - 100)
        rule.onNodeWithText("not armed").assertExists()
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("armed").assertExists()
    }

    @Test
    fun `resuming and pausing again restarts the guard`() {
        rule.mainClock.autoAdvance = false
        var paused by mutableStateOf(true)
        rule.setContent { Text(if (rememberFinishArmed(paused)) "armed" else "not armed") }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("armed").assertExists()
        setPaused { paused = false }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("not armed").assertExists()
        setPaused { paused = true }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("not armed").assertExists()
        rule.mainClock.advanceTimeBy(FINISH_GUARD_MILLIS)
        rule.onNodeWithText("armed").assertExists()
    }

    // ---- Paused banner ----

    @Test
    fun `the paused banner says how long and offers Discard`() {
        var discards = 0
        rule.setContent { LogEzTheme { PausedBanner(pausedForSeconds = { 42 }, onDiscard = { discards++ }) } }
        rule.onNodeWithText("PAUSED").assertIsDisplayed()
        rule.onNodeWithText("for 0:42 · time and distance stopped").assertIsDisplayed()
        rule.onNodeWithText("Discard").performClick()
        assertEquals(1, discards)
    }

    // ---- Distance hero and its reserved note line ----

    @Test
    fun `good GPS shows the distance and no note`() {
        rule.setContent { LogEzTheme { DistanceHero(3460.3, DistanceUnit.KM, stats = { stats() }) } }
        rule.onNodeWithText("3.46").assertIsDisplayed()
        rule.onNodeWithText("km").assertIsDisplayed()
        rule.onNodeWithText("Distance starts when GPS finds you. Time is already running.").assertDoesNotExist()
        rule.onNodeWithText("Weak GPS signal. Distance may come up short until it's back.").assertDoesNotExist()
    }

    @Test
    fun `finding GPS says distance waits and time does not`() {
        rule.setContent { LogEzTheme { DistanceHero(0.0, DistanceUnit.KM, stats = { stats(gps = GpsSignal.FINDING) }) } }
        rule.onNodeWithText("0.00").assertIsDisplayed()
        rule.onNodeWithText("Distance starts when GPS finds you. Time is already running.").assertIsDisplayed()
    }

    @Test
    fun `weak GPS warns that distance may come up short`() {
        rule.setContent { LogEzTheme { DistanceHero(2056.0, DistanceUnit.KM, stats = { stats(gps = GpsSignal.WEAK) }) } }
        rule.onNodeWithText("2.06").assertIsDisplayed()
        rule.onNodeWithText("Weak GPS signal. Distance may come up short until it's back.").assertIsDisplayed()
    }

    @Test
    fun `miles show mi`() {
        rule.setContent { LogEzTheme { DistanceHero(1609.344, DistanceUnit.MILES, stats = { stats() }) } }
        rule.onNodeWithText("1.00").assertIsDisplayed()
        rule.onNodeWithText("mi").assertIsDisplayed()
    }

    @Test
    fun `the note line keeps its height, so the card below never jumps when the signal drops`() {
        var gps by mutableStateOf(GpsSignal.GOOD)
        rule.setContent { LogEzTheme { DistanceHero(3460.3, DistanceUnit.KM, stats = { stats(gps = gps) }) } }
        val good = rule.onRoot().getBoundsInRoot().height
        gps = GpsSignal.WEAK
        rule.waitForIdle()
        assertEquals(good.value, rule.onRoot().getBoundsInRoot().height.value, 0.5f)
        gps = GpsSignal.FINDING
        rule.waitForIdle()
        assertEquals(good.value, rule.onRoot().getBoundsInRoot().height.value, 0.5f)
    }

    // ---- Stats card ----

    @Test
    fun `the stats card shows time, pace now, average pace and average speed from the sample run`() {
        rule.setContent { LogEzTheme { StatsCard(3460.3, DistanceUnit.KM, stats = { stats() }) } }
        rule.onNodeWithText("21:05").assertIsDisplayed()
        rule.onNodeWithText("Time").assertIsDisplayed()
        rule.onNodeWithText("6:09").assertIsDisplayed() // pace now 369 s
        rule.onNodeWithText("Pace now /km").assertIsDisplayed()
        rule.onNodeWithText("6:05").assertIsDisplayed() // 1265 s / 3.4603 km, truncated like the summary's
        rule.onNodeWithText("Avg pace /km").assertIsDisplayed()
        rule.onNodeWithText("9.8").assertIsDisplayed()
        rule.onNodeWithText("Avg speed km/h").assertIsDisplayed()
    }

    @Test
    fun `pace now reads a dash while there is nothing to show, and so do the averages before 50 m`() {
        rule.setContent { LogEzTheme { StatsCard(0.0, DistanceUnit.KM, stats = { stats(elapsed = 9, paceNow = null) }) } }
        rule.onNodeWithText("0:09").assertIsDisplayed()
        // Pace now, average pace and average speed.
        assertEquals(3, rule.onAllNodesWithText("—").fetchSemanticsNodes().size)
    }

    @Test
    fun `the stats card in miles uses the mile labels`() {
        rule.setContent { LogEzTheme { StatsCard(1609.344, DistanceUnit.MILES, stats = { stats(elapsed = 600, paceNow = 600.0) }) } }
        rule.onNodeWithText("Pace now /mi").assertIsDisplayed()
        rule.onNodeWithText("Avg pace /mi").assertIsDisplayed()
        rule.onNodeWithText("Avg speed mph").assertIsDisplayed()
        // Time 600 s reads 10:00, as do pace now (600 s/mi) and the average pace (1609 m in 600 s).
        assertEquals(3, rule.onAllNodesWithText("10:00").fetchSemanticsNodes().size)
    }

    // ---- Map strip ----

    @Test
    fun `the map strip shows distance, time and average pace`() {
        rule.setContent { LogEzTheme { MapStrip(3460.3, DistanceUnit.KM, stats = { stats() }) } }
        rule.onNodeWithText("3.46").assertIsDisplayed()
        rule.onNodeWithText("Distance (km)").assertIsDisplayed()
        rule.onNodeWithText("21:05").assertIsDisplayed()
        rule.onNodeWithText("6:05").assertIsDisplayed()
        rule.onNodeWithText("Avg pace /km").assertIsDisplayed()
    }
}
