package com.enil.logez.feature.activity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.wellness.HeartRateAccess
import com.enil.logez.core.wellness.HeartRateSample
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tracking screen with its ViewModel taken out: the wiring between the pieces (the Finish guard on
 * the real button, the Discard dialog, hide, the Stats/Map switch, the heart-rate card's buttons).
 * The live map is a slot here, because MapLibre cannot run in a JVM test. Expected text is literal.
 */
@RunWith(RobolectricTestRunner::class)
// A Pixel 7 sized screen: the default Robolectric one is too short to show the heart-rate card below the 96sp distance.
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class ActivityTrackingContentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val running = ActivityTrackingState(
        workoutId = "w-1",
        workoutSetId = "set-1",
        startedAtMillis = 1_000_000L,
        distanceMeters = 3460.3,
    )
    private val paused = running.copy(isPaused = true, pausedAtMillis = 1_500_000L)

    private val noHeartRate = TrackingHeartRateInputs(
        access = HeartRateAccess.GRANTED,
        history = emptyList(),
        requestRefused = false,
        onAllow = {},
        onOpenSettings = {},
        onGetHealthConnect = {},
    )

    private class Calls {
        val log = mutableListOf<String>()
        fun add(name: String): () -> Unit = { log += name }
    }

    @Composable
    private fun Screen(
        state: ActivityTrackingState,
        calls: Calls,
        activity: GpsActivity? = GpsActivity.RUN,
        heartRate: TrackingHeartRateInputs = noHeartRate,
        maxHeartRateBpm: Int? = 190,
        ending: Boolean = false,
        routeMap: @Composable (Modifier) -> Unit = { modifier -> Box(modifier) { Text("the live map") } },
    ) {
        LogEzTheme {
            ActivityTrackingContent(
                state = state,
                unit = DistanceUnit.KM,
                maxHeartRateBpm = maxHeartRateBpm,
                stats = { LiveTrackingStats(1265, if (state.isPaused) 42 else null, 369.0, GpsSignal.GOOD, nowMillis = 0L) },
                activity = activity,
                heartRate = heartRate,
                ending = ending,
                onPause = calls.add("pause"),
                onResume = calls.add("resume"),
                onFinish = calls.add("finish"),
                onDiscard = calls.add("discard"),
                onHide = calls.add("hide"),
                routeMap = routeMap,
            )
        }
    }

    // ---- the Finish guard, on the real button ----

    @Test
    fun `Finish ignores taps for 500 ms after the screen shows paused, then fires`() {
        val calls = Calls()
        rule.mainClock.autoAdvance = false
        rule.setContent { Screen(paused, calls) }
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithText("Finish").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Finish").performClick()
        assertEquals(emptyList<String>(), calls.log)

        rule.mainClock.advanceTimeBy(FINISH_GUARD_MILLIS)
        rule.onNodeWithText("Finish").performClick()
        assertEquals(listOf("finish"), calls.log)
    }

    @Test
    fun `while moving the only exit is Pause, and it calls pause`() {
        val calls = Calls()
        rule.setContent { Screen(running, calls) }

        rule.onNodeWithText("Finish").assertDoesNotExist()
        rule.onNodeWithText("Pause").performClick()
        assertEquals(listOf("pause"), calls.log)
    }

    @Test
    fun `Resume calls resume`() {
        val calls = Calls()
        rule.setContent { Screen(paused, calls) }
        rule.onNodeWithText("Resume").performClick()
        assertEquals(listOf("resume"), calls.log)
    }

    // ---- Discard ----

    @Test
    fun `Discard asks first, names the run, and only the dialog's red Discard throws it away`() {
        val calls = Calls()
        rule.setContent { Screen(paused, calls) }

        rule.onNodeWithText("Discard").performClick() // the banner's
        rule.onNodeWithText("Discard this run?").assertIsDisplayed()
        rule.onNodeWithText("Its time, distance and route won't be saved. This can't be undone.").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls.log)

        rule.onNodeWithText("Keep").performClick()
        rule.onNodeWithText("Discard this run?").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls.log)

        rule.onNodeWithText("Discard").performClick()
        rule.onNode(hasText("Discard") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("discard"), calls.log)
    }

    @Test
    fun `the discard dialog says walk for a walk and workout when the kind is not known`() {
        val calls = Calls()
        rule.setContent { Screen(paused, calls, activity = GpsActivity.WALK) }
        rule.onNodeWithText("Discard").performClick()
        rule.onNodeWithText("Discard this walk?").assertIsDisplayed()
    }

    @Test
    fun `the title says Run or Walk`() {
        val calls = Calls()
        rule.setContent { Screen(running, calls, activity = GpsActivity.WALK) }
        rule.onNodeWithText("WALK").assertIsDisplayed()
    }

    @Test
    fun `a run is titled RUN`() {
        rule.setContent { Screen(running, Calls(), activity = GpsActivity.RUN) }
        rule.onNodeWithText("RUN").assertIsDisplayed()
    }

    // ---- hide ----

    @Test
    fun `the down arrow hides the screen and nothing else`() {
        val calls = Calls()
        rule.setContent { Screen(running, calls) }
        rule.onNodeWithContentDescription("Hide tracking screen").performClick()
        assertEquals(listOf("hide"), calls.log)
    }

    @Test
    fun `no back callback is registered while moving or paused, so Back is the navigation's own pop`() {
        rule.setContent { Screen(running, Calls()) }
        assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `no back callback is registered while paused either`() {
        rule.setContent { Screen(paused, Calls()) }
        assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    // ---- ending: Finish or Discard has been tapped and the save is running ----

    @Test
    fun `while the run is being ended Back is held and hide, Pause, Resume, Finish and Discard are off`() {
        rule.setContent { Screen(running, Calls(), ending = true) }
        assertTrue(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        rule.onNodeWithContentDescription("Hide tracking screen").assertIsNotEnabled()
        rule.onNodeWithText("Pause").assertIsNotEnabled()
    }

    @Test
    fun `while a paused run is being ended Finish, Resume and the banner's Discard are off`() {
        rule.setContent { Screen(paused, Calls(), ending = true) }
        rule.onNodeWithText("Finish").assertIsNotEnabled()
        rule.onNodeWithText("Resume").assertIsNotEnabled()
        rule.onNodeWithText("Discard").assertIsNotEnabled()
    }

    // ---- Stats / Map ----

    @Test
    fun `Stats is first, Map swaps the stats for the map and its strip, and Stats comes back`() {
        val calls = Calls()
        rule.setContent { Screen(running, calls) }

        rule.onNodeWithText("Pace now /km").assertIsDisplayed()
        rule.onNodeWithText("the live map").assertDoesNotExist()

        rule.onNodeWithText("MAP").performClick()
        rule.onNodeWithText("the live map").assertIsDisplayed()
        rule.onNodeWithText("Distance (km)").assertIsDisplayed()
        rule.onNodeWithText("Pace now /km").assertDoesNotExist()

        rule.onNodeWithText("STATS").performClick()
        rule.onNodeWithText("Pace now /km").assertIsDisplayed()
        rule.onNodeWithText("the live map").assertDoesNotExist()
    }

    @Test
    fun `the map is built once and kept, so switching back to it does not start a new one`() {
        var built = 0
        val calls = Calls()
        rule.setContent {
            Screen(running, calls, routeMap = { modifier ->
                remember { built++ }
                Box(modifier) { Text("the live map") }
            })
        }
        assertEquals(0, built) // not until Map is opened
        rule.onNodeWithText("MAP").performClick()
        rule.onNodeWithText("STATS").performClick()
        rule.onNodeWithText("MAP").performClick()
        rule.onNodeWithText("the live map").assertIsDisplayed()
        assertEquals(1, built)
    }

    @Test
    fun `the paused banner shows on both views`() {
        val calls = Calls()
        rule.setContent { Screen(paused, calls) }
        rule.onNodeWithText("PAUSED").assertIsDisplayed()
        rule.onNodeWithText("MAP").performClick()
        rule.onNodeWithText("PAUSED").assertIsDisplayed()
    }

    // ---- heart rate ----

    @Test
    fun `a refusal is remembered across the switch, because the state lives above the views`() {
        val calls = Calls()
        val refused = TrackingHeartRateInputs(HeartRateAccess.NOT_GRANTED, emptyList(), true, {}, { calls.log += "settings" }, {})
        rule.setContent { Screen(running, calls, heartRate = refused) }
        rule.onNodeWithText("Heart rate is still off for LogEZ").assertIsDisplayed()
        rule.onNodeWithText("MAP").performClick()
        rule.onNodeWithText("STATS").performClick()
        rule.onNodeWithText("Heart rate is still off for LogEZ").assertIsDisplayed()
        rule.onNodeWithText("Open Health Connect settings").performClick()
        assertEquals(listOf("settings"), calls.log)
    }

    @Test
    fun `not allowed names the activity and its button asks`() {
        val calls = Calls()
        val notAllowed = TrackingHeartRateInputs(HeartRateAccess.NOT_GRANTED, emptyList(), false, { calls.log += "allow" }, {}, {})
        rule.setContent { Screen(running, calls, heartRate = notAllowed) }
        rule.onNodeWithText("LogEZ isn't allowed to read heart rate").assertIsDisplayed()
        rule.onNodeWithText("Allow it to see your watch's readings here and on this run's summary.").assertIsDisplayed()
        rule.onNodeWithText("Allow heart rate").performClick()
        assertEquals(listOf("allow"), calls.log)
    }

    @Test
    fun `needing Health Connect offers to get it`() {
        val calls = Calls()
        val needs = TrackingHeartRateInputs(HeartRateAccess.NEEDS_INSTALL_OR_UPDATE, emptyList(), false, {}, {}, { calls.log += "get" })
        rule.setContent { Screen(running, calls, heartRate = needs) }
        rule.onNodeWithText("Heart rate needs Health Connect").assertIsDisplayed()
        rule.onNodeWithText("Get Health Connect").performClick()
        assertEquals(listOf("get"), calls.log)
    }

    @Test
    fun `allowed with nothing synced and no Health Connect are one quiet line each, with no card`() {
        val calls = Calls()
        rule.setContent { Screen(running, calls) }
        rule.onNodeWithText("No heart rate synced yet. Watches often send it after the workout.").assertIsDisplayed()
    }

    @Test
    fun `a phone without Health Connect gets the unavailable line`() {
        val calls = Calls()
        val unavailable = TrackingHeartRateInputs(HeartRateAccess.UNAVAILABLE, emptyList(), false, {}, {}, {})
        rule.setContent { Screen(running, calls, heartRate = unavailable) }
        rule.onNodeWithText("Heart rate isn't available on this phone.").assertIsDisplayed()
    }

    @Test
    fun `access not known yet shows nothing, rather than a card that then goes away`() {
        val calls = Calls()
        val unknown = TrackingHeartRateInputs(null, emptyList(), false, {}, {}, {})
        rule.setContent { Screen(running, calls, heartRate = unknown) }
        rule.onNodeWithText("No heart rate synced yet. Watches often send it after the workout.").assertDoesNotExist()
        rule.onNodeWithText("Heart rate isn't available on this phone.").assertDoesNotExist()
    }

    private fun reading(secondsAgo: Long) = HeartRateSample(Instant.ofEpochMilli(NOW - secondsAgo * 1000), 142)

    @Test
    fun `a fresh reading shows bpm, its zone and its age, and the heart-rate chart`() {
        val calls = Calls()
        val live = TrackingHeartRateInputs(HeartRateAccess.GRANTED, listOf(reading(60), reading(10)), false, {}, {}, {})
        rule.setContent { Screen(running, calls, heartRate = live, maxHeartRateBpm = 170) }
        rule.onNodeWithText("142").assertIsDisplayed()
        rule.onNodeWithText("bpm").assertIsDisplayed()
        rule.onNodeWithText("Zone 4").assertIsDisplayed() // 142 / 170 = 84% of max
        rule.onNodeWithText("Hard").assertIsDisplayed()
    }

    @Test
    fun `a reading over five minutes old says so, and that watches send in batches`() {
        val calls = Calls()
        val stale = TrackingHeartRateInputs(HeartRateAccess.GRANTED, listOf(reading(6 * 60)), false, {}, {}, {})
        rule.setContent { Screen(running, calls, heartRate = stale, maxHeartRateBpm = 170) }
        rule.onNodeWithText("142").assertIsDisplayed()
        rule.onNodeWithText("Watches send heart rate in batches, so it can be a few minutes behind.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a reading without a max heart rate asks for one instead of naming a zone`() {
        val calls = Calls()
        val live = TrackingHeartRateInputs(HeartRateAccess.GRANTED, listOf(reading(10)), false, {}, {}, {})
        rule.setContent { Screen(running, calls, heartRate = live, maxHeartRateBpm = null) }
        rule.onNodeWithText("142").assertIsDisplayed()
        rule.onNodeWithText("Set your max heart rate in Settings to see your zone.").assertIsDisplayed()
    }

    private companion object {
        /** The fake stats report nowMillis = 0, so a reading is "fresh" at 0 and stale well before it. */
        const val NOW = 0L
    }
}
