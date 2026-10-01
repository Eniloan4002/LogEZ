package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.calc.WarmupCalculator.WarmupSetPlan
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.defaultWarmupMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M18 §5.1.6: the warm-up ladder generator, checked with literal hand-computed values. */
class WarmupCalculatorTest {

    private val defaultEquipment = PlateEquipment() // 20 kg bar; 1.25/2.5/5/10/15/20/25 plates

    @Test
    fun `100 kg barbell with the default method yields the canonical 40-60-80 ladder`() {
        // 40% = 40 kg = bar 20 + 2×10 (exact) · 60% = 60 = 20 + 2×20 · 80% = 80 = 20 + 2×(25+5).
        val plan = WarmupCalculator.generate(100.0, defaultWarmupMethod, barbell = true, equipment = defaultEquipment, unit = WeightUnit.KG)
        assertEquals(
            listOf(
                WarmupSetPlan(weightKg = 40.0, reps = 5),
                WarmupSetPlan(weightKg = 60.0, reps = 5),
                WarmupSetPlan(weightKg = 80.0, reps = 3),
            ),
            plan,
        )
    }

    @Test
    fun `a barbell step the plates cannot load lands on the closest loadable weight`() {
        // Equipment: 20 kg bar, ONLY 5 kg plates → loadable totals are 20, 30, 40, 50, ...
        // Working 103 kg at 40% = 41.2 kg. Candidates: 40 (off by 1.2) vs 50 (off by 8.8) → 40.
        val sparse = PlateEquipment(barsKg = listOf(20.0), platesKg = listOf(5.0))
        val plan = WarmupCalculator.generate(103.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = sparse, unit = WeightUnit.KG)
        assertEquals(listOf(WarmupSetPlan(weightKg = 40.0, reps = 5)), plan)
    }

    @Test
    fun `a barbell step below the bar clamps to the bar itself`() {
        // Working 40 kg at 40% = 16 kg < the 20 kg bar → the empty bar is the warm-up weight.
        val plan = WarmupCalculator.generate(40.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = defaultEquipment, unit = WeightUnit.KG)
        assertEquals(listOf(WarmupSetPlan(weightKg = 20.0, reps = 5)), plan)
    }

    @Test
    fun `non-barbell steps round to the 2 point 5 kg grid`() {
        // Working 41 kg: 40% = 16.4 → 17.5 (16.4/2.5 = 6.56 → 7 increments) ·
        // 60% = 24.6 → 25 (9.84 → 10) · 80% = 32.8 → 32.5 (13.12 → 13).
        val plan = WarmupCalculator.generate(41.0, defaultWarmupMethod, barbell = false, equipment = null, unit = WeightUnit.KG)
        assertEquals(
            listOf(
                WarmupSetPlan(weightKg = 17.5, reps = 5),
                WarmupSetPlan(weightKg = 25.0, reps = 5),
                WarmupSetPlan(weightKg = 32.5, reps = 3),
            ),
            plan,
        )
    }

    @Test
    fun `a non-barbell step that would round to zero keeps its raw percent weight`() {
        // Working 2 kg at 40% = 0.8 → nearest 2.5 increment is 0, which is meaningless — the raw
        // 0.8 kg is the documented answer (see the engine's class doc).
        val plan = WarmupCalculator.generate(2.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = false, equipment = null, unit = WeightUnit.KG)
        assertEquals(1, plan.size)
        assertEquals(0.8, plan[0].weightKg, 1e-12)
    }

    @Test
    fun `a barbell exercise with no equipment falls back to the increment grid`() {
        // 100 kg at 40% = 40, already on the 2.5 grid.
        val plan = WarmupCalculator.generate(100.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = null, unit = WeightUnit.KG)
        assertEquals(listOf(WarmupSetPlan(weightKg = 40.0, reps = 5)), plan)
    }

    @Test
    fun `an empty method yields no sets`() {
        assertTrue(WarmupCalculator.generate(100.0, emptyList(), barbell = true, equipment = defaultEquipment, unit = WeightUnit.KG).isEmpty())
    }

    @Test
    fun `a non-positive or NaN working weight yields no sets`() {
        assertTrue(WarmupCalculator.generate(0.0, defaultWarmupMethod, barbell = false, equipment = null, unit = WeightUnit.KG).isEmpty())
        assertTrue(WarmupCalculator.generate(-10.0, defaultWarmupMethod, barbell = false, equipment = null, unit = WeightUnit.KG).isEmpty())
        assertTrue(WarmupCalculator.generate(Double.NaN, defaultWarmupMethod, barbell = false, equipment = null, unit = WeightUnit.KG).isEmpty())
    }

    @Test
    fun `reps come from the method's own rows, per step`() {
        val method = listOf(WarmupStep(percent = 0.50, reps = 8), WarmupStep(percent = 0.75, reps = 2))
        val plan = WarmupCalculator.generate(100.0, method, barbell = false, equipment = null, unit = WeightUnit.KG)
        // 50 and 75 are both on the 2.5 grid, so the weights are exact.
        assertEquals(listOf(WarmupSetPlan(50.0, 8), WarmupSetPlan(75.0, 2)), plan)
    }

    // --- F9: pounds. Working weights are stored in kg, so each test feeds the kg value of a round
    // pound number (lb × 0.45359237) and reads the ladder back in pounds. ---

    private fun inPounds(plan: List<WarmupSetPlan>): List<Double> = plan.map { it.weightKg / 0.45359237 }

    @Test
    fun `225 lb barbell with the default method yields 90-135-180 lb on pound plates`() {
        // 225 lb = 102.05828325 kg. 40% = 90 = 45 + 2×(10+10+2.5) · 60% = 135 = 45 + 2×45 ·
        // 80% = 180 = 45 + 2×(45+10+10+2.5). All exact on the default pound set.
        val plan = WarmupCalculator.generate(102.05828325, defaultWarmupMethod, barbell = true, equipment = defaultEquipment, unit = WeightUnit.LB)
        assertEquals(listOf(5, 5, 3), plan.map { it.reps })
        val pounds = inPounds(plan)
        assertEquals(90.0, pounds[0], 1e-9)
        assertEquals(135.0, pounds[1], 1e-9)
        assertEquals(180.0, pounds[2], 1e-9)
        // And the stored kg values are the exact kg of those pound weights.
        assertEquals(40.8233133, plan[0].weightKg, 1e-9)
        assertEquals(61.23496995, plan[1].weightKg, 1e-9)
        assertEquals(81.6466266, plan[2].weightKg, 1e-9)
    }

    @Test
    fun `a pound barbell step lands on the closest 5 lb total, never a kg plate weight`() {
        // 200 lb = 90.718474 kg. 40% = 80 lb → per side (80 − 45) / 2 = 17.5 = 10 + 5 + 2.5 → 80.
        // 60% = 120 → 37.5 per side = 35 + 2.5 → 120. 80% = 160 → 57.5 = 45 + 10 + 2.5 → 160.
        // Before F9 these were kg loadings shown in pounds (a 44.09 lb bar and 5.51 lb steps).
        val plan = WarmupCalculator.generate(90.718474, defaultWarmupMethod, barbell = true, equipment = defaultEquipment, unit = WeightUnit.LB)
        val pounds = inPounds(plan)
        assertEquals(80.0, pounds[0], 1e-9)
        assertEquals(120.0, pounds[1], 1e-9)
        assertEquals(160.0, pounds[2], 1e-9)
    }

    @Test
    fun `an off-grid pound step rounds to the nearest loadable pound total`() {
        // 213 lb = 96.61517481 kg. 40% = 85.2 lb → per side 20.1: 20 (total 85, 0.2 away) beats
        // 22.5 (total 90) → 85 lb, as 10 + 10 per side.
        val plan = WarmupCalculator.generate(96.61517481, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = defaultEquipment, unit = WeightUnit.LB)
        assertEquals(85.0, inPounds(plan)[0], 1e-9)
    }

    @Test
    fun `a pound barbell step below the 45 lb bar clamps to the bar`() {
        // 100 lb = 45.359237 kg. 40% = 40 lb < 45 → the empty 45 lb bar.
        val plan = WarmupCalculator.generate(45.359237, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = defaultEquipment, unit = WeightUnit.LB)
        assertEquals(45.0, inPounds(plan)[0], 1e-9)
        assertEquals(20.41165665, plan[0].weightKg, 1e-9)
    }

    @Test
    fun `a pound barbell ladder uses the custom pound set, not the kg set`() {
        // Only 10 lb plates on a 35 lb bar: totals 35, 55, 75, 95 ... 135 lb = 61.23496995 kg.
        // 40% = 54 lb → 55 (1 away; 35 is 19 away).
        val custom = PlateEquipment(barsLb = listOf(35.0), platesLb = listOf(10.0))
        val plan = WarmupCalculator.generate(61.23496995, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = custom, unit = WeightUnit.LB)
        assertEquals(55.0, inPounds(plan)[0], 1e-9)
    }

    @Test
    fun `a pound barbell with no pound bars falls back to the 5 lb grid`() {
        // A hand-edited backup could carry no pound bars. 100 lb = 45.359237 kg: 40/60/80 lb land
        // on the 5 lb grid as they are (a 45 lb bar would have clamped the first step to 45).
        val noBars = PlateEquipment(barsLb = emptyList())
        val pounds = inPounds(WarmupCalculator.generate(45.359237, defaultWarmupMethod, barbell = true, equipment = noBars, unit = WeightUnit.LB))
        assertEquals(40.0, pounds[0], 1e-9)
        assertEquals(60.0, pounds[1], 1e-9)
        assertEquals(80.0, pounds[2], 1e-9)
    }

    @Test
    fun `non-barbell pound steps round to the 5 lb grid`() {
        // 47 lb = 21.31884139 kg. 40% = 18.8 → 20 (3.76 increments → 4) ·
        // 60% = 28.2 → 30 (5.64 → 6) · 80% = 37.6 → 40 (7.52 → 8).
        val plan = WarmupCalculator.generate(21.31884139, defaultWarmupMethod, barbell = false, equipment = null, unit = WeightUnit.LB)
        val pounds = inPounds(plan)
        assertEquals(20.0, pounds[0], 1e-9)
        assertEquals(30.0, pounds[1], 1e-9)
        assertEquals(40.0, pounds[2], 1e-9)
    }

    @Test
    fun `a non-barbell pound step that would round to zero keeps its raw pound weight`() {
        // 5 lb = 2.26796185 kg. 40% = 2 lb → nearest 5 lb increment is 0 → the raw 2 lb stays.
        val plan = WarmupCalculator.generate(2.26796185, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = false, equipment = null, unit = WeightUnit.LB)
        assertEquals(2.0, inPounds(plan)[0], 1e-9)
    }
}
