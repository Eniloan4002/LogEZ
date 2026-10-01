package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PlateSet
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.setFor

/**
 * PHASE2_PLAN.md §5.1.6 — the Warm-up Calculator's ladder generator (M18). Pure math, no Android
 * imports: the logger feeds it a working weight, the persisted Warmup Method, the user's weight
 * unit and (for barbells) the user's plate equipment, and inserts what comes back as WARMUP rows.
 *
 * Unit (F9): the ladder is worked out in the user's weight unit and converted back to canonical kg
 * only at the end. A pounds user's steps land on pound weights (a 45 lb bar with pound plates, or
 * the 5 lb grid), never on kilogram weights shown in pounds (the old 5.51 lb steps). For KG the
 * conversions are the identity, so the kg ladder is exactly what it was before F9.
 *
 * Rounding rules (§5.1.6 "rounding preferences"):
 *  - Barbell: each percent weight is rounded to the NEAREST total the equipment for the unit can
 *    actually load (bar + 2 × a sum of owned plate denominations). This delegates to
 *    [PlateCalculator.solve] — the same closest-achievable DP the Plate Calculator sheet uses — so
 *    a warm-up weight is never one the user would then have to plate-calculate around. A step
 *    below the bar clamps to the bar itself (solve's belowBar result already reports the bar as
 *    the achieved weight). The bar used is the FIRST (lightest) owned bar of that unit — the same
 *    default the Plate Calculator sheet opens with, so the two calculators agree about what
 *    "loadable" means out of the box.
 *  - Non-barbell: round to the nearest [NON_BARBELL_INCREMENT_KG] (2.5 kg — dumbbell racks' usual
 *    step, §5.1.6's default) or, for pounds, [NON_BARBELL_INCREMENT_LB] (5 lb, the usual US
 *    dumbbell step). When rounding would hit 0 the RAW percent weight is returned unrounded
 *    instead: a 0 warm-up row is meaningless, and forcing a full increment could exceed a very
 *    light working weight — the tiny raw value is the honest answer.
 *
 * Quarter-unit discipline: the barbell path runs entirely inside [PlateCalculator]'s integer
 * quarter-unit arithmetic (raw percents are rounded onto that grid on entry, results come back off
 * it), and 2.5 and 5 are themselves quarter-unit multiples, so every emitted weight composes
 * exactly with the Plate Calculator — no Double error can make a warm-up weight unloadable. The
 * one conversion back to kg is undone exactly by the display conversion (WeightDisplay), so a
 * 135 lb step shows as 135.
 */
object WarmupCalculator {

    /** §5.1.6 default dumbbell/other rounding increment. */
    const val NON_BARBELL_INCREMENT_KG = 2.5

    /** F9: the pound equivalent, the usual US dumbbell step. */
    const val NON_BARBELL_INCREMENT_LB = 5.0

    /** One generated warm-up row: the rounded weight (canonical kg) plus the method step's reps. */
    data class WarmupSetPlan(val weightKg: Double, val reps: Int)

    /**
     * @param workingWeightKg the first working (non-WARMUP) set's weight; non-positive → empty.
     * @param method the persisted Warmup Method (percent × reps rows); empty → empty.
     * @param barbell whether the exercise's equipment is a barbell (plate-loadable).
     * @param equipment owned bars/plates — used only when [barbell]; null falls back to the
     *   non-barbell increment (no equipment to define "loadable").
     * @param unit the user's weight unit: which equipment set and which increment to round to.
     */
    fun generate(
        workingWeightKg: Double,
        method: List<WarmupStep>,
        barbell: Boolean,
        equipment: PlateEquipment?,
        unit: WeightUnit,
    ): List<WarmupSetPlan> {
        if (workingWeightKg <= 0.0 || workingWeightKg.isNaN()) return emptyList()
        val working = WeightDisplay.toDisplay(workingWeightKg, unit)
        return method.map { step ->
            val raw = working * step.percent
            val rounded = if (barbell && equipment != null) {
                roundToLoadable(raw, equipment.setFor(unit), unit)
            } else {
                roundToIncrement(raw, unit)
            }
            WarmupSetPlan(weightKg = WeightDisplay.toKg(rounded, unit), reps = step.reps)
        }
    }

    /**
     * Nearest bar + 2×plates total, in [unit]. [PlateCalculator.solve]'s achieved total IS that
     * answer: for a below-bar target it reports the bar alone (the clamp), otherwise the closest
     * achievable total at or around the target — DP-optimal, not greedy.
     */
    private fun roundToLoadable(raw: Double, set: PlateSet, unit: WeightUnit): Double {
        val bar = set.bars.firstOrNull() ?: return roundToIncrement(raw, unit)
        return PlateCalculator.solve(raw, bar, set.plates).achieved
    }

    private fun roundToIncrement(raw: Double, unit: WeightUnit): Double {
        val increment = if (unit == WeightUnit.LB) NON_BARBELL_INCREMENT_LB else NON_BARBELL_INCREMENT_KG
        val rounded = Math.round(raw / increment) * increment
        // Documented choice: a step that rounds to zero keeps its raw (tiny) weight — see class doc.
        return if (rounded <= 0.0) raw else rounded
    }
}
