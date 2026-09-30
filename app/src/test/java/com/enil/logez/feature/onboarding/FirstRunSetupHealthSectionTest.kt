package com.enil.logez.feature.onboarding

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.backup.RestoreLock
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeRegionDefaults
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeUserDataProbe
import com.enil.logez.fakes.FakeWidgetRefresher
import org.robolectric.Shadows.shadowOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.wellness.HealthDataType
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Setup's optional Health Connect section, in each of its six states (first-run plan, O1f). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FirstRunSetupHealthSectionTest {
    @get:Rule val rule = createComposeRule()

    private val enPh = SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY)
    private var connects = 0
    private var settingsOpened = 0
    private var playOpened = 0

    private fun show(health: SetupHealth, working: Boolean = false) {
        rule.setContent {
            LogEzTheme {
                FirstRunSetupScreen(
                    preselected = enPh,
                    regionNoteVisible = true,
                    working = working,
                    onContinue = {},
                    health = SetupHealthBinding(
                        health = health,
                        onConnect = { connects++ },
                        onOpenSettings = { settingsOpened++ },
                        onOpenPlay = { playOpened++ },
                    ),
                )
            }
        }
    }

    private val header = "HEALTH CONNECT (OPTIONAL)"
    private val shortBody = "LogEZ can read steps and calories burned for your daily totals, and heart rate for your " +
        "workouts. Heart rate needs a watch and often syncs after the workout. What LogEZ reads stays on this phone."
    private val backupNote = "LogEZ has no cloud copy of your data. To keep a copy off this phone, make a Full backup " +
        "(.zip) in Profile > Settings > Export & backup and save the file somewhere else."

    @Test
    fun `hidden shows no section at all`() {
        show(SetupHealth.Hidden)

        rule.onNodeWithText(header).assertDoesNotExist()
        rule.onNodeWithTag(FirstRunTestTags.CONNECT).assertDoesNotExist()
        rule.onNodeWithText("Open Google Play").assertDoesNotExist()
        rule.onNodeWithText(backupNote).assertExists()
    }

    @Test
    fun `available with nothing granted shows the short text and Connect, which requests access`() {
        show(SetupHealth.CanConnect)

        rule.onNode(hasText(header) and isHeading()).assertExists()
        rule.onNodeWithText(shortBody).assertExists()
        rule.onNodeWithTag(FirstRunTestTags.CONNECT).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, connects)
        rule.onNodeWithText("Connect").assertExists()
    }

    @Test
    fun `the section sits between the settings note and the backup note`() {
        show(SetupHealth.CanConnect)

        val footnote = rule.onNodeWithText("Change these any time in Profile > Settings.").getUnclippedBoundsInRoot()
        val heading = rule.onNodeWithText(header).getUnclippedBoundsInRoot()
        val connect = rule.onNodeWithTag(FirstRunTestTags.CONNECT).getUnclippedBoundsInRoot()
        val backup = rule.onNodeWithText(backupNote).getUnclippedBoundsInRoot()
        assertTrue("footnote ${footnote.top}, heading ${heading.top}", footnote.top < heading.top)
        assertTrue("heading ${heading.top}, connect ${connect.top}", heading.top < connect.top)
        assertTrue("connect ${connect.top}, backup ${backup.top}", connect.top < backup.top)
    }

    @Test
    fun `Connect has at least a 48 dp touch target`() {
        show(SetupHealth.CanConnect)

        val node = rule.onNodeWithTag(FirstRunTestTags.CONNECT).fetchSemanticsNode()
        val touchHeight = with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() }
        assertTrue("Connect's touch target is $touchHeight tall", touchHeight >= 48.dp)
    }

    @Test
    fun `Open Health Connect settings is at least 48 dp tall`() {
        show(SetupHealth.Refused)
        rule.onNodeWithText("Open Health Connect settings").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `every type granted reads as all allowed, with no action`() {
        show(SetupHealth.Readable(HealthDataType.entries.toSet()))

        rule.onNodeWithText("LogEZ can read: Steps, Calories burned, Heart rate").assertExists()
        rule.onNodeWithTag(FirstRunTestTags.CONNECT).assertDoesNotExist()
        rule.onNodeWithText("Open Health Connect settings").assertDoesNotExist()
        rule.onNodeWithText("Open Google Play").assertDoesNotExist()
    }

    @Test
    fun `a partial grant names what is allowed and what is not, in the app's type order`() {
        show(SetupHealth.Readable(setOf(HealthDataType.HEART_RATE, HealthDataType.STEPS)))

        rule.onNodeWithText("LogEZ can read: Steps, Heart rate. Not allowed: Calories burned").assertExists()
        rule.onNodeWithTag(FirstRunTestTags.CONNECT).assertDoesNotExist()
    }

    @Test
    fun `a full refusal says so and opens Health Connect's settings instead of Connect`() {
        show(SetupHealth.Refused)

        rule.onNodeWithText("Nothing was allowed. You can allow it in Health Connect's settings.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithTag(FirstRunTestTags.CONNECT).assertDoesNotExist()
        rule.onNodeWithText("Open Health Connect settings").performScrollTo().performClick()
        assertEquals(1, settingsOpened)
        assertEquals(0, connects)
    }

    @Test
    fun `an update required opens Google Play`() {
        show(SetupHealth.UpdateRequired)

        rule.onNodeWithText(
            "Health Connect needs an update from Google Play before LogEZ can show your steps, calories burned and heart rate.",
        ).assertExists()
        rule.onNodeWithText("Open Google Play").performScrollTo().performClick()
        assertEquals(1, playOpened)
    }

    @Test
    fun `not installed on Android 9 to 13 opens Google Play`() {
        show(SetupHealth.NotInstalled)

        rule.onNodeWithText(
            "LogEZ can show your steps, calories burned and heart rate from a watch or fitness app through Health " +
                "Connect. Install it from Google Play to turn this on.",
        ).assertExists()
        rule.onNodeWithText("Open Google Play").performScrollTo().performClick()
        assertEquals(1, playOpened)
        assertEquals(0, connects)
    }

    private fun actionOf(health: SetupHealth) = when (health) {
        SetupHealth.CanConnect -> rule.onNodeWithTag(FirstRunTestTags.CONNECT)
        SetupHealth.Refused -> rule.onNodeWithText("Open Health Connect settings")
        else -> rule.onNodeWithText("Open Google Play")
    }

    private val statesWithAnAction =
        listOf(SetupHealth.CanConnect, SetupHealth.Refused, SetupHealth.UpdateRequired, SetupHealth.NotInstalled)

    @Test
    fun `every section action is disabled while Continue or a restore result is being written`() {
        var health by mutableStateOf<SetupHealth>(SetupHealth.CanConnect)
        rule.setContent {
            LogEzTheme {
                FirstRunSetupScreen(
                    preselected = enPh,
                    regionNoteVisible = true,
                    working = true,
                    onContinue = {},
                    health = SetupHealthBinding(health, onConnect = {}, onOpenSettings = {}, onOpenPlay = {}),
                )
            }
        }
        for (state in statesWithAnAction) {
            health = state
            rule.waitForIdle()
            actionOf(state).assertIsNotEnabled()
        }
    }

    @Test
    fun `every section action is disabled while a restore is under way`() {
        var health by mutableStateOf<SetupHealth>(SetupHealth.CanConnect)
        rule.setContent {
            LogEzTheme {
                FirstRunSetupScreen(
                    preselected = enPh,
                    regionNoteVisible = true,
                    working = false,
                    onContinue = {},
                    restore = SetupRestoreBinding(SetupRestoreUi(busy = true), onRestore = {}, onConfirm = {}, onCancel = {}),
                    health = SetupHealthBinding(health, onConnect = {}, onOpenSettings = {}, onOpenPlay = {}),
                )
            }
        }
        for (state in statesWithAnAction) {
            health = state
            rule.waitForIdle()
            actionOf(state).assertIsNotEnabled()
        }
    }

    @Test
    fun `the section changes in place when the state changes`() {
        var health by mutableStateOf<SetupHealth>(SetupHealth.CanConnect)
        rule.setContent {
            LogEzTheme {
                FirstRunSetupScreen(
                    preselected = enPh,
                    regionNoteVisible = true,
                    working = false,
                    onContinue = {},
                    health = SetupHealthBinding(health, onConnect = {}, onOpenSettings = {}, onOpenPlay = {}),
                )
            }
        }
        rule.onNodeWithTag(FirstRunTestTags.CONNECT).assertExists()

        health = SetupHealth.Refused
        rule.waitForIdle()

        rule.onNodeWithTag(FirstRunTestTags.CONNECT).assertDoesNotExist()
        rule.onNodeWithText("Nothing was allowed. You can allow it in Health Connect's settings.").assertExists()
    }

    // ---- The gate's own wiring (rememberSetupHealth), with a gate built from fakes ----

    private val quietLogger = object : AppLogger {
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }

    private fun gate(health: FakeHealthMetricsSource, sdkInt: Int = 34) = FirstRunGateViewModel(
        FakeFirstRunStore(), FakeUserDataProbe(), FakeSettingsRepository(), FakeRegionDefaults(), FakeWidgetRefresher(),
        RestoreLock(), FakeClock(), quietLogger, health, SavedStateHandle(), sdkInt,
    )

    private fun showGate(vm: FirstRunGateViewModel) {
        rule.setContent {
            LogEzTheme {
                val state by vm.state.collectAsState()
                FirstRunHost(
                    state = state,
                    onContinue = {},
                    onHandOff = {},
                    onResume = vm::onResume,
                    setupHealth = { rememberSetupHealth(vm) },
                ) { Text("History underneath") }
            }
        }
    }

    private fun nextStartedActivity() =
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity

    @Test
    fun `the gate's Connect asks Health Connect for steps, calories burned and heart rate`() {
        val permissions = setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_TOTAL_CALORIES_BURNED",
            "android.permission.health.READ_HEART_RATE",
        )
        val vm = gate(
            FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available, requiredPermissions = permissions),
        )
        showGate(vm)

        rule.onNodeWithTag(FirstRunTestTags.CONNECT).performScrollTo().performClick()

        // On Android 14 Health Connect's permissions are platform runtime permissions, so the contract
        // goes through the system's own permission request.
        val intent = nextStartedActivity()
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", intent.action)
        assertEquals(permissions, intent.getStringArrayExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES")?.toSet())
    }

    @Test
    fun `after the gate records a refusal, its button opens Health Connect's settings`() {
        val vm = gate(FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available))
        vm.onHealthConnectResult(anyGranted = false)
        showGate(vm)

        rule.onNodeWithText("Open Health Connect settings").performScrollTo().performClick()

        assertEquals("android.health.connect.action.HEALTH_HOME_SETTINGS", nextStartedActivity().action)
    }

    @Test
    fun `on Android 9 to 13 without Health Connect, the gate's button opens its Google Play listing`() {
        val vm = gate(FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable), sdkInt = 33)
        showGate(vm)

        rule.onNodeWithText("Open Google Play").performScrollTo().performClick()

        val intent = nextStartedActivity()
        assertEquals("com.android.vending", intent.`package`)
        assertEquals(
            "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding",
            intent.dataString,
        )
    }

    @Test
    fun `the overlay hands the gate's Health Connect section to setup`() {
        rule.setContent {
            LogEzTheme {
                FirstRunHost(
                    state = FirstRunGateState.ShowSetup(preselected = enPh, regionNoteVisible = true),
                    onContinue = {},
                    onHandOff = {},
                    onResume = {},
                    setupHealth = {
                        SetupHealthBinding(SetupHealth.CanConnect, onConnect = { connects++ }, onOpenSettings = {}, onOpenPlay = {})
                    },
                ) { Text("History underneath") }
            }
        }

        rule.onNodeWithTag(FirstRunTestTags.CONNECT).performScrollTo().performClick()
        assertEquals(1, connects)
    }
}
