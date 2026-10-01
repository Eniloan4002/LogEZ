package com.enil.logez.feature.activity

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.common.PermissionDenial
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.fakes.FakeActivityResultRegistry
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * First-run plan O1h: the walk/run notification ask. Android 14 here, so POST_NOTIFICATIONS starts
 * ungranted. Android's own permission dialogs are replaced by [FakeActivityResultRegistry], which records
 * each request and answers it at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingNotificationAskTest {
    @get:Rule val rule = createComposeRule()

    private val ask = "Show your walk or run on the lock screen?"
    private val strengthPrompt = "Show your workout on the lock screen?"
    private val locationRationale = "Allow location access?"

    private val registry = FakeActivityResultRegistry()
    private val started = mutableListOf<Pair<String, String>>()
    private val denials = mutableListOf<PermissionDenial>()
    private val strengthNavigations = mutableListOf<String>()
    private lateinit var track: (String, String) -> Unit
    private lateinit var startStrength: (String) -> Unit

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val declined get() = app.getSharedPreferences("logez_ui_flags", Context.MODE_PRIVATE)
        .getBoolean("notification_prompt_declined", false)

    @Before
    fun forgetEarlierAnswers() {
        app.getSharedPreferences("logez_ui_flags", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun grantLocation() = shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    @Composable
    private fun Launchers() {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
            LogEzTheme {
                track = rememberRequestLocationForTracking(
                    onGranted = { id, title -> started += id to title },
                    onDenied = { denials += it },
                )
                startStrength = rememberStartWorkoutSession { strengthNavigations += it }
            }
        }
    }

    private fun show() = rule.setContent { Launchers() }

    @Test
    fun `with location granted the ask shows before tracking, and Not now still starts the walk`() {
        grantLocation()
        show()

        rule.runOnIdle { track("e1", "Walking") }

        rule.onAllNodesWithText(ask).assertCountEquals(1)
        rule.onNodeWithText("LogEZ shows the time and distance on the lock screen while it records.").assertExists()
        assertEquals(emptyList<Pair<String, String>>(), started)

        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()

        assertEquals(listOf("e1" to "Walking"), started)
        assertTrue(declined)
        assertEquals(emptyList<Any?>(), registry.launched)
        rule.onAllNodesWithText(ask).assertCountEquals(0)
    }

    @Test
    fun `Allow asks Android, a denial there is remembered, and the walk starts anyway`() {
        grantLocation()
        registry.notificationAnswer = false
        show()
        rule.runOnIdle { track("e1", "Walking") }

        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()

        assertEquals(listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), registry.launched)
        assertEquals(listOf("e1" to "Walking"), started)
        assertTrue(declined)
    }

    @Test
    fun `Allow granted by Android starts the walk and records no decline`() {
        grantLocation()
        registry.notificationAnswer = true
        show()
        rule.runOnIdle { track("e1", "Running") }

        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()

        assertEquals(listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), registry.launched)
        assertEquals(listOf("e1" to "Running"), started)
        assertFalse(declined)
    }

    @Test
    fun `asked once, so after Not now the next walk starts with no ask`() {
        grantLocation()
        show()
        rule.runOnIdle { track("e1", "Walking") }
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()

        rule.runOnIdle { track("e2", "Running") }

        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(listOf("e1" to "Walking", "e2" to "Running"), started)
    }

    @Test
    fun `no ask when notifications are already allowed`() {
        grantLocation()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show()

        rule.runOnIdle { track("e1", "Walking") }

        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(listOf("e1" to "Walking"), started)
        assertFalse(declined)
    }

    @Test
    fun `Not now on a walk also stops the strength prompt`() {
        grantLocation()
        show()
        rule.runOnIdle { track("e1", "Walking") }
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()

        rule.runOnIdle { startStrength("w1") }

        rule.onAllNodesWithText(strengthPrompt).assertCountEquals(0)
        assertEquals(listOf("w1"), strengthNavigations)
    }

    @Test
    fun `Not now on the strength prompt also stops the walk ask`() {
        grantLocation()
        show()
        rule.runOnIdle { startStrength("w1") }
        rule.onAllNodesWithText(strengthPrompt).assertCountEquals(1)
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()

        rule.runOnIdle { track("e1", "Walking") }

        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(listOf("e1" to "Walking"), started)
    }

    @Test
    fun `the ask comes only after location is granted`() {
        registry.locationAnswer = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to true,
            Manifest.permission.ACCESS_COARSE_LOCATION to true,
        )
        show()

        rule.runOnIdle { track("e1", "Walking") }
        rule.onAllNodesWithText(locationRationale).assertCountEquals(1)
        rule.onAllNodesWithText(ask).assertCountEquals(0)

        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText(ask).assertCountEquals(1)
        assertEquals(emptyList<Pair<String, String>>(), started)
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()
        assertEquals(listOf("e1" to "Walking"), started)
    }

    @Test
    fun `a location refusal never asks about notifications and starts nothing`() {
        show()
        rule.runOnIdle { track("e1", "Walking") }

        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(listOf(PermissionDenial.Declined), denials)
        assertEquals(emptyList<Pair<String, String>>(), started)
        assertFalse(declined)
    }

    @Test
    fun `Back on the ask counts as Not now, so the walk starts and the next one is not asked`() {
        grantLocation()
        show()
        rule.runOnIdle { track("e1", "Walking") }
        rule.onAllNodesWithText(ask).assertCountEquals(1)

        // Android's back, delivered to the ask's own dialog window.
        rule.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        rule.waitForIdle()

        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(listOf("e1" to "Walking"), started)
        assertTrue(declined)
        assertEquals(emptyList<Any?>(), registry.launched)

        rule.runOnIdle { track("e2", "Running") }
        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(listOf("e1" to "Walking", "e2" to "Running"), started)
    }

    @Test
    fun `approximate-only location from Android is a refusal, so nothing is asked or started`() {
        registry.locationAnswer = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to false,
            Manifest.permission.ACCESS_COARSE_LOCATION to true,
        )
        show()
        rule.runOnIdle { track("e1", "Walking") }

        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()

        assertEquals(listOf(PermissionDenial.ApproximateOnly), denials)
        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(emptyList<Pair<String, String>>(), started)
        assertFalse(declined)
    }

    @Test
    fun `a location denial from Android never asks about notifications and starts nothing`() {
        registry.locationAnswer = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to false,
            Manifest.permission.ACCESS_COARSE_LOCATION to false,
        )
        show()
        rule.runOnIdle { track("e1", "Walking") }

        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()

        assertEquals(listOf(PermissionDenial.Blocked), denials)
        rule.onAllNodesWithText(ask).assertCountEquals(0)
        assertEquals(emptyList<Pair<String, String>>(), started)
        assertFalse(declined)
    }

    @Test
    fun `a rotation while the ask is open still starts that walk once answered`() {
        grantLocation()
        val restoration = StateRestorationTester(rule)
        restoration.setContent { Launchers() }
        rule.runOnIdle { track("e1", "Walking") }
        rule.onAllNodesWithText(ask).assertCountEquals(1)

        restoration.emulateSavedInstanceStateRestore()

        rule.onAllNodesWithText(ask).assertCountEquals(1)
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()
        assertEquals(listOf("e1" to "Walking"), started)
    }
}

/**
 * Below Android 13 notifications need no runtime permission, so a walk never asks (API 28, the
 * plan's "API 28 never asks").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingNotificationAskBelowApi33Test {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `a walk on Android 9 starts with no ask`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val started = mutableListOf<Pair<String, String>>()
        lateinit var track: (String, String) -> Unit
        rule.setContent {
            LogEzTheme {
                track = rememberRequestLocationForTracking(onGranted = { id, title -> started += id to title }, onDenied = {})
            }
        }

        rule.runOnIdle { track("e1", "Walking") }

        rule.onAllNodesWithText("Show your walk or run on the lock screen?").assertCountEquals(0)
        assertEquals(listOf("e1" to "Walking"), started)
        assertFalse(
            app.getSharedPreferences("logez_ui_flags", Context.MODE_PRIVATE).getBoolean("notification_prompt_declined", false),
        )
    }
}
