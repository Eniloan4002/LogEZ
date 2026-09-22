package com.enil.logez.core.designsystem

import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.WeightUnit
import java.util.Locale

/**
 * Shared formatting utilities for the UI layer.
 *
 * Consolidates the 4× duplicated `formatTargetNumber`, 3× duplicated `formatVolume`,
 * and 2× duplicated `formatMmSs` functions that previously lived as private functions
 * in individual screens and ViewModels.
 */
object Formatting {
    /**
     * Formats a [Double] as a whole number when possible, otherwise with one decimal place.
     * Used for weight cells, target values, and set-table displays.
     *
     * Examples: `152.0` → `"152"`, `152.5` → `"152.5"`, `0.0` → `"0"`
     */
    fun wholeOrOneDecimal(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /**
     * Formats a weight in kg with the "kg" suffix.
     * Whole numbers display without decimals; non-whole display one decimal.
     *
     * Examples: `152.0` → `"152kg"`, `152.5` → `"152.5kg"`
     */
    fun weightKg(kg: Double): String =
        if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(Locale.ROOT, kg)

    /**
     * Formats a weight in kg with the "kg" suffix — identical to [weightKg].
     * Named distinctly for call sites that previously used `formatVolumeShort`.
     */
    fun weightKgShort(kg: Double): String = weightKg(kg)

    /**
     * A weight in the user's display unit, suffixed. Storage is always kg; this is where a stored
     * figure becomes text a user reads. A "kg" suffix on a value shown to a pounds user was a 2.2x
     * discrepancy that read as a wrong total — worst in the logger, where the set cells already
     * showed pounds two rows below a header still in kilograms.
     */
    fun weight(kg: Double, unit: WeightUnit): String =
        WeightDisplay.format(WeightDisplay.toDisplay(kg, unit)) + if (unit == WeightUnit.KG) "kg" else "lb"

    /**
     * Formats total seconds as `M:SS` (e.g., `90` → `"1:30"`, `0` → `"0:00"`).
     * Used for rest timers, duration displays, and time cells.
     */
    fun mmSs(totalSeconds: Int): String =
        "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)

    /**
     * Formats a distance in meters as kilometers with a "km" suffix, `Locale.ROOT`-safe (unlike
     * [weightKg]/[mmSs] above, which format with the default locale and can render non-ASCII
     * digits on some locales — a known, separately-tracked issue, not repeated here).
     *
     * Examples: `2000.0` -> `"2km"`, `2350.0` -> `"2.35km"`
     */
    fun distanceKm(meters: Double): String {
        val km = meters / 1000.0
        return if (km == km.toLong().toDouble()) "${km.toLong()}km" else "%.2fkm".format(Locale.ROOT, km)
    }

    /** `M:SS` per km/mile from [PaceCalculator.paceSecondsPerUnit] -- reuses [mmSs]'s exact shape. */
    fun pace(secondsPerUnit: Double): String = mmSs(secondsPerUnit.toLong().toInt())
}

/** Convenience top-level aliases so call sites read naturally. */
fun formatTargetNumber(value: Double): String = Formatting.wholeOrOneDecimal(value)
fun formatWeightKg(kg: Double): String = Formatting.weightKg(kg)
fun formatWeightKgShort(kg: Double): String = Formatting.weightKgShort(kg)
fun formatWeight(kg: Double, unit: WeightUnit): String = Formatting.weight(kg, unit)
fun formatMmSs(totalSeconds: Int): String = Formatting.mmSs(totalSeconds)
fun formatDistanceKm(meters: Double): String = Formatting.distanceKm(meters)
fun formatPace(secondsPerUnit: Double): String = Formatting.pace(secondsPerUnit)
