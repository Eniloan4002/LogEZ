package com.enil.logez.feature.analytics

import android.app.Application
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWellnessRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Profile's Health Connect card after a full refusal (first-run plan, O1f: the same rule as setup). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileScreenHealthConnectTest {
    @get:Rule val rule = createComposeRule()

    private val refusedText = "Nothing was allowed. You can allow it in Health Connect's settings."
    private val cardTitle = "Connect Health Connect"

    private fun showProfile(): ProfileViewModel {
        val vm = ProfileViewModel(
            FakeWorkoutRepository(),
            FakeExerciseRepository(),
            FakeSettingsRepository(),
            FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available),
            FakeWellnessRepository(),
            FakePersonalRecordsRepository(),
            FakeMeasurementRepository(),
            FakeClock(),
            SavedStateHandle(),
        )
        rule.setContent { LogEzTheme { ProfileScreen(viewModel = vm) } }
        rule.waitForIdle()
        rule.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(cardTitle))
        return vm
    }

    @Test
    fun `before a refusal the card offers Connect`() {
        showProfile()

        rule.onNodeWithText("Connect").assertExists()
        rule.onNodeWithText(refusedText).assertDoesNotExist()
        rule.onNodeWithText("Open Health Connect settings").assertDoesNotExist()
    }

    @Test
    fun `after a full refusal the card says so and opens Health Connect's settings instead of Connect`() {
        val vm = showProfile()

        vm.onWellnessPermissionResult(anyGranted = false)
        rule.waitForIdle()

        rule.onNodeWithText(refusedText)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithText("Connect").assertDoesNotExist()
        rule.onNodeWithText("Open Health Connect settings").performClick()
        assertEquals(
            "android.health.connect.action.HEALTH_HOME_SETTINGS",
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity.action,
        )
    }
}
