package com.enil.logez.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWidgetRefresher
import com.enil.logez.feature.onboarding.TipsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** First-run plan F9: the plate equipment editor shows and edits the set for the user's unit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class PlateEquipmentUnitTest {
    @get:Rule val rule = createComposeRule()

    private fun show(repository: SettingsRepository) {
        rule.setContent {
            LogEzTheme {
                PlateEquipmentScreen(onBack = {}, viewModel = SettingsViewModel(repository, FakeWidgetRefresher()))
            }
        }
    }

    private fun show(unit: WeightUnit): FakeSettingsRepository =
        FakeSettingsRepository(UserSettings(weightUnit = unit)).also { show(it) }

    /** Holds back the stored settings until [open] is set, standing in for DataStore's first read. */
    private class SlowSettingsRepository(private val inner: FakeSettingsRepository) : SettingsRepository by inner {
        val open = MutableStateFlow(false)
        override val settings: Flow<UserSettings> =
            inner.settings.combine(open) { s, isOpen -> s.takeIf { isOpen } }.filterNotNull()
    }

    @Test
    fun `a pounds user sees the pound set and the note that the kg set is kept`() {
        show(WeightUnit.LB)

        rule.onNodeWithText(
            "Used while weights are in pounds. Your kilogram bars and plates are kept for when you switch to kilograms.",
        ).assertIsDisplayed()
        // 45 lb is both the bar and the heaviest plate.
        rule.onAllNodesWithText("45 lb").assertCountEquals(2)
        rule.onNodeWithText("2.5 lb").assertIsDisplayed()
        rule.onNodeWithText("35 lb").assertIsDisplayed()
        rule.onAllNodesWithText("20 kg").assertCountEquals(0)
    }

    @Test
    fun `a kg user still sees the kg set`() {
        show(WeightUnit.KG)

        rule.onNodeWithText(
            "Used while weights are in kilograms. Your pound bars and plates are kept for when you switch to pounds.",
        ).assertIsDisplayed()
        rule.onAllNodesWithText("20 kg").assertCountEquals(2)
        rule.onNodeWithText("1.25 kg").assertIsDisplayed()
        rule.onAllNodesWithText("45 lb").assertCountEquals(0)
    }

    @Test
    fun `removing a pound plate on the screen changes only the pound plates`() {
        val repository = show(WeightUnit.LB)

        // The single 45 lb bar has no remove control, so the plates' controls run 2.5, 5, 10, 25,
        // 35, 45 lb: the fifth one is 35 lb.
        val removes = rule.onAllNodesWithContentDescription("Remove")
        removes.assertCountEquals(6)
        removes[4].performClick()
        rule.waitForIdle()

        val equipment = repository.settings.value.plateEquipment
        assertEquals(listOf(2.5, 5.0, 10.0, 25.0, 45.0), equipment.platesLb)
        assertEquals(listOf(45.0), equipment.barsLb)
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), equipment.platesKg)
        assertEquals(listOf(20.0), equipment.barsKg)
        rule.onAllNodesWithText("35 lb").assertCountEquals(0)
    }

    @Test
    fun `before the stored settings arrive the editor shows no set, so a pounds user never sees the kg set`() {
        val repository = SlowSettingsRepository(FakeSettingsRepository(UserSettings(weightUnit = WeightUnit.LB)))
        show(repository)

        rule.onAllNodesWithText("20 kg").assertCountEquals(0)
        rule.onAllNodesWithText("45 lb").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Remove").assertCountEquals(0)

        repository.open.value = true
        rule.waitForIdle()

        rule.onAllNodesWithText("45 lb").assertCountEquals(2)
        rule.onAllNodesWithText("20 kg").assertCountEquals(0)
    }

    @Test
    fun `a restored list with the same bar twice still shows`() {
        // Nothing tidies a restored list, so a hand-edited backup can carry a duplicate weight.
        val repository = FakeSettingsRepository(
            UserSettings(weightUnit = WeightUnit.LB, plateEquipment = PlateEquipment(barsLb = listOf(45.0, 45.0))),
        )
        show(repository)

        // Two bars plus the 45 lb plate.
        rule.onAllNodesWithText("45 lb").assertCountEquals(3)
    }

    private fun showSettings(unit: WeightUnit) {
        rule.setContent {
            LogEzTheme {
                SettingsScreen(
                    onBack = {}, onSoundsClick = {}, onPlateEquipmentClick = {}, onWarmupSetsClick = {}, onDataClick = {},
                    viewModel = SettingsViewModel(FakeSettingsRepository(UserSettings(weightUnit = unit)), FakeWidgetRefresher()),
                    tipsViewModel = TipsViewModel(FakeFirstRunStore(), AppLogger.NoOp),
                )
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Plate equipment"))
    }

    @Test
    fun `the Settings row counts the pound set for a pounds user`() {
        showSettings(WeightUnit.LB)
        rule.onNodeWithText("1 bar · 6 plates").assertIsDisplayed()
    }

    @Test
    fun `the Settings row counts the kg set for a kg user`() {
        showSettings(WeightUnit.KG)
        rule.onNodeWithText("1 bar · 7 plates").assertIsDisplayed()
    }
}
