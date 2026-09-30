package com.enil.logez.core.domain.model

import kotlin.math.floor

/**
 * PHASE2_PLAN.md §5.1.7 — RPE (Rate of Perceived Exertion) is restricted to eight half-step
 * values, entered live-logging only via a picker, never as a free-text field and never as a
 * routine target (`routine_sets` carries no RPE column at all).
 *
 * [VALUES] is the sole source of what's enterable: a UI built from this list can never produce a
 * value outside it by construction, so there is no separate runtime validator to keep in sync.
 *
 * The reserve-reps mapping ([formatRir], and the picker captions in strings.xml) is the standard
 * RPE/RIR correlation used across strength training generally (Renaissance Periodization and
 * others publish the same table), phrased in our own words rather than any app's exact wording.
 *
 * P-211 (Owner, 2026-09-30, decision 1): RIR is a display and entry layer on this same stored
 * value, RIR = 10 − RPE. Nothing stores RIR. Every screen that prints an effort goes through
 * [format] with the user's [EffortScale], so a set reads the same everywhere and the "@8.0" form
 * is gone. A restored backup does not range-check `rpe`, so every formatter here is defined for
 * any Double: it rounds to the nearest half step first ([roundToHalfStep]).
 */
object RpeScale {
    val VALUES: List<Double> = listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

    /**
     * P-211 decision 3: the RIR picker's five chips in the order they are drawn (0, 1, 2, 3, 4+),
     * as the RPE each one stores. "4+" is stored as 6, the bottom of [VALUES].
     */
    val RIR_CHIP_VALUES: List<Double> = listOf(10.0, 9.0, 8.0, 7.0, 6.0)

    /** The picker chips for [scale], as stored RPE values, in drawing order. */
    fun chipValues(scale: EffortScale): List<Double> = when (scale) {
        EffortScale.RPE -> VALUES
        EffortScale.RIR -> RIR_CHIP_VALUES
    }

    /**
     * The chip [rpe] selects on [scale]'s picker, or null when none matches exactly. An RPE
     * half step (8.5) selects no RIR chip, and an off-scale value from a hand-edited backup (6.5,
     * 5) selects no chip on either picker, so opening the picker never silently rewrites it.
     */
    fun selectedChip(rpe: Double?, scale: EffortScale): Double? = rpe?.takeIf { it in chipValues(scale) }

    /** Rounds to the nearest 0.5 (a tie goes up): 8.25 → 8.5, 8.2 → 8.0. */
    fun roundToHalfStep(rpe: Double): Double = Math.round(rpe * 2.0) / 2.0

    /** "6" / "7.5" / "10" — no trailing ".0" on the whole-number values; any value rounds to a half step first. */
    fun format(rpe: Double): String {
        val r = roundToHalfStep(rpe)
        return if (r == floor(r)) r.toLong().toString() else r.toString()
    }

    /**
     * RIR for a stored RPE (P-211 decision 1): "0" / "2" / "4+", and a half step as a range with
     * an en dash, "1–2", never "1.5". RPE 6 or below reads "4+"; above 10 reads "0".
     */
    fun formatRir(rpe: Double): String {
        val r = roundToHalfStep(rpe)
        if (r <= RIR_FLOOR_RPE) return "4+"
        val rir = 10.0 - r.coerceAtMost(10.0)
        return if (rir == floor(rir)) rir.toLong().toString() else "${(rir - 0.5).toLong()}–${(rir + 0.5).toLong()}"
    }

    /** The one effort formatter every display uses: [rpe] in the user's [scale]. */
    fun format(rpe: Double, scale: EffortScale): String = when (scale) {
        EffortScale.RPE -> format(rpe)
        EffortScale.RIR -> formatRir(rpe)
    }

    /**
     * Which plain-words caption describes [rpe] (P-211 decision 10j): the value rounded to a half
     * step and held to 6..10, so any stored value has one. Both scales share the captions, so one
     * effort is described one way everywhere.
     */
    fun captionStep(rpe: Double): Double = roundToHalfStep(rpe).coerceIn(RIR_FLOOR_RPE, 10.0)

    /** RPE 6 is RIR "4+": the bottom of both scales. */
    private const val RIR_FLOOR_RPE = 6.0
}
