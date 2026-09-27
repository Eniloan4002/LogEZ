package com.enil.logez.feature.onboarding

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** First-run setup's screen (first-run plan, O1c). Robolectric runs in en-US, so days read in English. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FirstRunSetupScreenTest {
    @get:Rule val rule = createComposeRule()

    private val enPh = SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY)
    private val continued = mutableListOf<SetupChoices>()

    private fun show(
        preselected: SetupChoices = enPh,
        regionNoteVisible: Boolean = true,
        working: Boolean = false,
    ) {
        rule.setContent {
            LogEzTheme {
                FirstRunSetupScreen(
                    preselected = preselected,
                    regionNoteVisible = regionNoteVisible,
                    working = working,
                    onContinue = { continued += it },
                )
            }
        }
    }

    @Test
    fun `the preselected pills show as selected, and the others do not`() {
        show(preselected = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY))

        rule.onNodeWithContentDescription("Pounds (lb)").assertIsSelected()
        rule.onNodeWithContentDescription("Kilograms (kg)").assertIsNotSelected()
        rule.onNodeWithContentDescription("Miles (mi)").assertIsSelected()
        rule.onNodeWithContentDescription("Kilometers (km)").assertIsNotSelected()
        rule.onNodeWithText("Sunday").assertIsSelected()
        rule.onNodeWithText("Monday").assertIsNotSelected()
        rule.onNodeWithText("Saturday").assertIsNotSelected()
    }

    @Test
    fun `Continue without touching anything reports the preselected choices`() {
        show()

        rule.onNodeWithTag("firstrun_continue").performClick()

        assertEquals(listOf(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY)), continued)
    }

    @Test
    fun `tapping pills changes the choices that Continue reports`() {
        show()

        rule.onNodeWithContentDescription("Pounds (lb)").performClick()
        rule.onNodeWithContentDescription("Miles (mi)").performClick()
        rule.onNodeWithText("Monday").performScrollTo().performClick()

        rule.onNodeWithContentDescription("Pounds (lb)").assertIsSelected()
        rule.onNodeWithContentDescription("Kilograms (kg)").assertIsNotSelected()
        rule.onNodeWithText("Monday").assertIsSelected()
        rule.onNodeWithText("Sunday").assertIsNotSelected()
        rule.onNodeWithTag("firstrun_continue").performClick()
        assertEquals(listOf(SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.MONDAY)), continued)
    }

    @Test
    fun `unit pills read their full names to TalkBack as radio buttons`() {
        show()

        rule.onNodeWithContentDescription("Kilograms (kg)").assertIsSelected()
        rule.onNodeWithContentDescription("Pounds (lb)").assertIsNotSelected()
        rule.onNodeWithContentDescription("Kilometers (km)").assertIsSelected()
        rule.onNodeWithContentDescription("Miles (mi)").assertIsNotSelected()
        rule.onNodeWithContentDescription("Pounds (lb)")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `a unit pill is read as its full name only, not its full name and then its symbol`() {
        show()

        rule.onNodeWithContentDescription("Pounds (lb)")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        rule.onNodeWithText("lb").assertDoesNotExist()
        rule.onNodeWithText("lb", useUnmergedTree = true).assertDoesNotExist()
        // Day pills have no description, so their visible name is what TalkBack reads.
        rule.onNodeWithText("Saturday").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `every pill and Continue are at least 48 dp tall`() {
        show()

        listOf("Kilograms (kg)", "Pounds (lb)", "Kilometers (km)", "Miles (mi)").forEach {
            rule.onNodeWithContentDescription(it).assertHeightIsAtLeast(48.dp)
        }
        listOf("Monday", "Saturday", "Sunday").forEach {
            rule.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithTag("firstrun_continue").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `each row of pills is a selectable group`() {
        show()

        rule.onNodeWithContentDescription("Pounds (lb)").onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        rule.onNodeWithContentDescription("Miles (mi)").onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        rule.onNodeWithText("Saturday").onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1000dp-h800dp")
    fun `on a wide screen the content is capped at 720 dp and centred`() {
        show()

        val root = rule.onRoot().getBoundsInRoot()
        val cont = rule.onNodeWithTag("firstrun_continue").getBoundsInRoot()
        assertTrue("Continue is ${cont.width} wide", cont.width <= 720.dp)
        val leftGap = cont.left - root.left
        val rightGap = root.right - cont.right
        assertTrue("left $leftGap, right $rightGap", (leftGap - rightGap).value in -1f..1f)
        // (1000 - 720) / 2 = 140 dp each side, plus the button's own 16 dp margin.
        assertEquals(156f, leftGap.value, 1f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h411dp", fontScale = 2.0f)
    fun `at 200 percent font in landscape the content scrolls and Continue stays on screen`() {
        show()

        rule.onNodeWithTag("firstrun_continue").assertIsDisplayed()
        rule.onNodeWithText("Saturday").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "LogEZ has no cloud copy of your data. To keep a copy off this phone, make a Full backup (.zip) " +
                "in Profile > Settings > Export & backup and save the file somewhere else.",
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("firstrun_continue").assertIsDisplayed()
    }

    @Test
    fun `the title and the section headers are headings`() {
        show()

        rule.onNode(hasText("BEFORE YOU START") and isHeading()).assertExists()
        rule.onNode(hasText("UNITS") and isHeading()).assertExists()
        rule.onNode(hasText("FIRST DAY OF THE WEEK") and isHeading()).assertExists()
    }

    @Test
    fun `the screen announces itself with a pane title`() {
        show()

        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "LogEZ setup")).assertExists()
    }

    @Test
    fun `the copy and both notes show`() {
        show()

        rule.onNodeWithText("No account and no ads. Everything you log stays on your phone.").assertExists()
        rule.onNodeWithText(
            "Body measurements will use cm with kg, or inches with lb. You can change them separately in Settings.",
        ).assertExists()
        rule.onNodeWithText("Change these any time in Profile > Settings.").assertExists()
        rule.onNodeWithText(
            "LogEZ has no cloud copy of your data. To keep a copy off this phone, make a Full backup (.zip) " +
                "in Profile > Settings > Export & backup and save the file somewhere else.",
        ).assertExists()
    }

    @Test
    fun `the region note shows when the preselection is the region's`() {
        show(regionNoteVisible = true)

        rule.onNodeWithText("Suggested from your phone's region.").assertExists()
    }

    @Test
    fun `the region note hides when a value was stored or the week start was clamped`() {
        show(regionNoteVisible = false)

        rule.onNodeWithText("Suggested from your phone's region.").assertDoesNotExist()
    }

    @Test
    fun `the region note stays when a value is changed, so nothing under the pills moves`() {
        // The plan ties the note to the preselected values only. Hiding it on a change shifted the
        // content under it, and at 200% font at the bottom of the scroll the tapped row jumped
        // (seen on emulator-5556, O1c fix pass).
        show(regionNoteVisible = true)
        val before = rule.onNodeWithText("Change these any time in Profile > Settings.").getBoundsInRoot().top

        rule.onNodeWithText("Saturday").performClick()
        rule.onNodeWithContentDescription("Pounds (lb)").performClick()

        rule.onNodeWithText("Suggested from your phone's region.").assertExists()
        assertEquals(before, rule.onNodeWithText("Change these any time in Profile > Settings.").getBoundsInRoot().top)
    }

    @Test
    fun `while Continue is being written, Continue and the pills are disabled`() {
        show(working = true)

        rule.onNodeWithTag("firstrun_continue").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Pounds (lb)").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Pounds (lb)").performClick()
        rule.onNodeWithContentDescription("Kilograms (kg)").assertIsSelected()
    }

    @Test
    fun `Continue is enabled and on screen when setup is idle`() {
        show()

        rule.onNodeWithTag("firstrun_continue").assertIsEnabled().assertIsDisplayed()
        rule.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun `the choices survive an Activity recreation or process death`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            LogEzTheme {
                FirstRunSetupScreen(
                    preselected = enPh,
                    regionNoteVisible = true,
                    working = false,
                    onContinue = { continued += it },
                )
            }
        }
        rule.onNodeWithContentDescription("Pounds (lb)").performClick()
        rule.onNodeWithText("Saturday").performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithContentDescription("Pounds (lb)").assertIsSelected()
        rule.onNodeWithText("Saturday").assertIsSelected()
        rule.onNodeWithTag("firstrun_continue").performClick()
        assertEquals(listOf(SetupChoices(WeightUnit.LB, DistanceUnit.KM, DayOfWeek.SATURDAY)), continued)
    }
}
