package com.enil.logez.feature.workout

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan F9: the plate calculator sheet solves in the user's unit with that unit's own
 * plates. A pounds user sees a 45 lb bar and pound plates, and "Use" still writes canonical kg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class PlateCalculatorSheetUnitTest {
    @get:Rule val rule = createComposeRule()

    private var appliedKg: Double? = null

    private fun show(unit: WeightUnit, initialWeightKg: Double?, equipment: PlateEquipment = PlateEquipment()) {
        rule.setContent {
            LogEzTheme {
                PlateCalculatorSheet(
                    setId = "s1",
                    initialWeightKg = initialWeightKg,
                    weightUnit = unit,
                    equipment = equipment,
                    onApply = { appliedKg = it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `225 lb loads two 45 lb plates per side on a 45 lb bar`() {
        // 225 lb is stored as 102.05828325 kg; the prefill shows it back as 225.
        show(WeightUnit.LB, initialWeightKg = 102.05828325)

        rule.onNodeWithText("Per side: 45 · 45").assertIsDisplayed()
        rule.onNodeWithText("Total: 225 lb").assertIsDisplayed()
    }

    @Test
    fun `an off-grid pound target offers the closest pound total and applies its exact kg`() {
        show(WeightUnit.LB, initialWeightKg = null)
        rule.onNode(hasSetTextAction()).performTextReplacement("231")

        // 231 − 45 = 186 → 93 per side; the closest loadable is 92.5 = 45 + 45 + 2.5 → 230 lb.
        rule.onNodeWithText("Per side: 45 · 45 · 2.5").assertIsDisplayed()
        // The sheet sits in its own window, where Robolectric does not route injected touches, so
        // the button's click action is invoked through semantics instead of a touch.
        rule.onNodeWithText("Use 230 lb").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        // 230 lb × 0.45359237 = 104.3262451 kg.
        assertNotNull(appliedKg)
        assertEquals(104.3262451, appliedKg!!, 1e-9)
    }

    @Test
    fun `a pound target below the bar says the 45 lb bar alone`() {
        show(WeightUnit.LB, initialWeightKg = null)
        rule.onNode(hasSetTextAction()).performTextReplacement("40")

        rule.onNodeWithText("Bar alone weighs 45 lb").assertIsDisplayed()
    }

    @Test
    fun `the pound sheet uses the custom pound set`() {
        // Only 10 lb plates on a 35 lb bar: 75 lb = 35 + 2×(10 + 10).
        show(
            WeightUnit.LB,
            initialWeightKg = 34.01942775, // 75 lb
            equipment = PlateEquipment(barsLb = listOf(35.0), platesLb = listOf(10.0)),
        )
        rule.onNodeWithText("Per side: 10 · 10").assertIsDisplayed()
        rule.onNodeWithText("Total: 75 lb").assertIsDisplayed()
    }

    @Test
    fun `an empty pound bar list falls back to the 45 lb bar`() {
        // A hand-edited backup could carry no pound bars; the sheet still loads a 45 lb bar.
        show(WeightUnit.LB, initialWeightKg = 102.05828325, equipment = PlateEquipment(barsLb = emptyList()))

        rule.onNodeWithText("Per side: 45 · 45").assertIsDisplayed()
        rule.onNodeWithText("Total: 225 lb").assertIsDisplayed()
    }

    @Test
    fun `the same pound bar twice still shows the bar choice`() {
        // Two equal bars must not crash the bar row with a duplicate key.
        show(WeightUnit.LB, initialWeightKg = 102.05828325, equipment = PlateEquipment(barsLb = listOf(45.0, 45.0)))

        rule.onAllNodesWithText("45 lb").assertCountEquals(2)
        rule.onNodeWithText("Per side: 45 · 45").assertIsDisplayed()
    }

    @Test
    fun `kg users are unchanged - 102_5 kg loads 25, 15 and 1_25 per side`() {
        show(WeightUnit.KG, initialWeightKg = 102.5)

        rule.onNodeWithText("Per side: 25 · 15 · 1.25").assertIsDisplayed()
        rule.onNodeWithText("Total: 102.5 kg").assertIsDisplayed()
    }
}
