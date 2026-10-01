package com.enil.logez.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The Plate Calculator's available equipment (PHASE2_PLAN.md §5.1.5, §5.2): two independent sets,
 * one in kilograms and one in pounds. Both calculators use the set for the user's weight unit, so
 * a pounds user loads a 45 lb bar with pound plates instead of a 44.09 lb bar with 22.05 lb plates
 * (first-run plan F9).
 *
 * Why two stored sets rather than one kg list converted for display: pound plates are physical
 * pound denominations, and a kg-only store cannot hold them exactly (45 lb is 20.41165665 kg), so
 * the maths would drift off the 0.25 lb grid. Each set is stored in its own unit and solved in it.
 * Keeping both means switching units never loses the custom equipment of the other unit.
 *
 * Storage and backups: the lb fields were added after the kg ones. A stored value or backup from
 * before them has no lb keys and decodes with the lb defaults below, so an older backup restores
 * with a sensible pound set. An older app reading a newer value ignores the lb keys (both readers
 * set ignoreUnknownKeys). There is no enum here, so nothing needs refusing.
 *
 * Downgrades: an older app that edits plate equipment (or restores a backup) writes the whole
 * value back without the lb keys, so the user's custom pound set resets to the defaults below.
 * Play does not allow downgrades; this only matters for a sideloaded older build.
 */
@Serializable
data class PlateEquipment(
    val barsKg: List<Double> = listOf(20.0),
    val platesKg: List<Double> = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0),
    /** F9: the conventional US gym set, one 45 lb bar. */
    val barsLb: List<Double> = listOf(45.0),
    /** F9: the conventional US gym plates, 2.5 to 45 lb. */
    val platesLb: List<Double> = listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0),
)

val defaultPlateEquipment = PlateEquipment()

/** One unit's bars and plates, in that unit's own numbers (kg for KG, lb for LB). */
data class PlateSet(val bars: List<Double>, val plates: List<Double>)

/** The set both calculators use for [unit]. */
fun PlateEquipment.setFor(unit: WeightUnit): PlateSet = when (unit) {
    WeightUnit.KG -> PlateSet(bars = barsKg, plates = platesKg)
    WeightUnit.LB -> PlateSet(bars = barsLb, plates = platesLb)
}

/**
 * The standard bar in [unit], for the defensive case of a stored set with no bars (the editor
 * never saves one, but a hand-edited backup could carry it).
 */
fun standardBar(unit: WeightUnit): Double = when (unit) {
    WeightUnit.KG -> 20.0
    WeightUnit.LB -> 45.0
}

/*
 * M17 equipment-editor operations, per unit (F9). All entries are rounded to the nearest quarter
 * of the unit (0.25 kg or 0.25 lb, the calculator's integer unit; see PlateCalculator's
 * float-discipline doc), deduplicated, and kept sorted ascending; a non-positive weight is a
 * no-op. Removal matches on the same rounded value the add stored, so the two are always
 * symmetric. An edit to one unit's set never touches the other unit's set.
 */

fun PlateEquipment.withBarAdded(unit: WeightUnit, weight: Double): PlateEquipment =
    withBars(unit, addWeight(setFor(unit).bars, weight))

/** The last bar is not removable — a plate calculator without a bar is meaningless (>=1 invariant). */
fun PlateEquipment.withBarRemoved(unit: WeightUnit, weight: Double): PlateEquipment {
    val bars = setFor(unit).bars
    return if (bars.size <= 1) this else withBars(unit, removeWeight(bars, weight))
}

fun PlateEquipment.withPlateAdded(unit: WeightUnit, weight: Double): PlateEquipment =
    withPlates(unit, addWeight(setFor(unit).plates, weight))

fun PlateEquipment.withPlateRemoved(unit: WeightUnit, weight: Double): PlateEquipment =
    withPlates(unit, removeWeight(setFor(unit).plates, weight))

private fun PlateEquipment.withBars(unit: WeightUnit, bars: List<Double>): PlateEquipment = when (unit) {
    WeightUnit.KG -> if (bars == barsKg) this else copy(barsKg = bars)
    WeightUnit.LB -> if (bars == barsLb) this else copy(barsLb = bars)
}

private fun PlateEquipment.withPlates(unit: WeightUnit, plates: List<Double>): PlateEquipment = when (unit) {
    WeightUnit.KG -> if (plates == platesKg) this else copy(platesKg = plates)
    WeightUnit.LB -> if (plates == platesLb) this else copy(platesLb = plates)
}

private fun addWeight(list: List<Double>, weight: Double): List<Double> {
    val rounded = roundToQuarter(weight)
    if (rounded <= 0.0 || rounded in list) return list
    return (list + rounded).sorted()
}

private fun removeWeight(list: List<Double>, weight: Double): List<Double> {
    val rounded = roundToQuarter(weight)
    return list.filterNot { it == rounded }
}

private fun roundToQuarter(weight: Double): Double =
    if (weight.isNaN() || weight <= 0.0) 0.0 else Math.round(weight * 4.0) / 4.0
