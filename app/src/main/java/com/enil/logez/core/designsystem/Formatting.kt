package com.enil.logez.core.designsystem

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
        if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(kg)

    /**
     * Formats a weight in kg with the "kg" suffix — identical to [weightKg].
     * Named distinctly for call sites that previously used `formatVolumeShort`.
     */
    fun weightKgShort(kg: Double): String = weightKg(kg)

    /**
     * Formats total seconds as `M:SS` (e.g., `90` → `"1:30"`, `0` → `"0:00"`).
     * Used for rest timers, duration displays, and time cells.
     */
    fun mmSs(totalSeconds: Int): String =
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Convenience top-level aliases so call sites read naturally. */
fun formatTargetNumber(value: Double): String = Formatting.wholeOrOneDecimal(value)
fun formatWeightKg(kg: Double): String = Formatting.weightKg(kg)
fun formatWeightKgShort(kg: Double): String = Formatting.weightKgShort(kg)
fun formatMmSs(totalSeconds: Int): String = Formatting.mmSs(totalSeconds)
