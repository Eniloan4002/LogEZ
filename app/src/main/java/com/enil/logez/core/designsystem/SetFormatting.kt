package com.enil.logez.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.enil.logez.R
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.WeightUnit
import kotlin.math.abs

/**
 * P-211 (Owner, 2026-09-30): how a logged set's values read on the History screens: the headed
 * set tables (workout detail, exercise history) and the History card's BEST SET column.
 *
 * Weights are in the user's unit: exactly as logged when that is what they are, otherwise one
 * decimal (see [weightValue]). Distances use [Formatting.distance] ("3km"), the same form as the
 * stat row above them (small fix 10a; the detail used to hardcode "3000m"). Times use [setDuration].
 */
object SetFormatting {
    /**
     * A set's time in the Duration stat's style, extended to seconds (small fix 10i): "18m 30s",
     * "1m", "45s", "1h 5m 30s", "0s". Never "18:30", which next to "Today, 18:05" on a 24-hour
     * phone reads as a clock time.
     */
    fun setDuration(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val parts = buildList {
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0 || isEmpty()) add("${s}s")
        }
        return parts.joinToString(" ")
    }

    /**
     * A table cell's weight, a bare number in the user's unit: "80", "77.5", "11.25", "176.4".
     *
     * A weight logged in this unit reads exactly as the logger shows it, up to its two decimals
     * ([WeightDisplay.format]): 11.25 kg stays "11.25", as the History screens wrote it before
     * P-211. Only a weight logged in the other unit, whose conversion has a long tail, stops at one
     * decimal (the Statistics-page convention, [Formatting.wholeOrOneDecimal]): 80 kg reads "176.4"
     * for a pounds user, as the README shows, not "176.37" (P-211 code review, 2026-09-30).
     */
    fun weightValue(kg: Double, unit: WeightUnit): String {
        val display = WeightDisplay.toDisplay(kg, unit)
        val hundredths = Math.round(display * 100.0) / 100.0
        return if (abs(display - hundredths) < AS_LOGGED_TOLERANCE) WeightDisplay.format(hundredths) else Formatting.wholeOrOneDecimal(display)
    }

    /** [weightValue] with the unit, no space, as the History card writes volume: "80kg", "176.4lb". */
    fun weight(kg: Double, unit: WeightUnit): String = weightValue(kg, unit) + if (unit == WeightUnit.KG) "kg" else "lb"

    /** "3km" / "1.86mi": [Formatting.distance], the stat row's form. */
    fun distance(meters: Double, unit: DistanceUnit): String = Formatting.distance(meters, unit)

    /** A floors/steps COUNT cell: whole or up to two decimals. */
    fun customMetric(value: Double): String = Formatting.twoDecimals(value)

    /**
     * The History card's BEST SET text for [set], the pick of
     * [com.enil.logez.core.domain.calc.BestSetCalculator.best] (P-211 decision 6): "80kg × 8",
     * "+10kg × 8", "−20kg × 8" (the routine builder's "−KG" minus), "15 reps", "1m",
     * "20kg × 1m", "3km · 18m 30s", "40kg × 0.05km". Null reads "—".
     *
     * [repsText] renders a bare rep count ("12 reps"); callers pass the `set_reps` plural, see
     * [bestSetText]. Weighted with no added weight, or assisted with no assistance, reads as reps
     * alone.
     */
    fun bestSet(
        type: ExerciseType,
        set: StatSet?,
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
        repsText: (Int) -> String,
    ): String {
        if (set == null) return NONE
        val weight = set.weightKg?.let { weight(it, weightUnit) }
        val time = set.durationSeconds?.let { setDuration(it) }
        val text = when (type) {
            ExerciseType.WEIGHT_REPS -> joinTimes(weight, set.reps?.toString())
            ExerciseType.BODYWEIGHT_WEIGHTED ->
                if ((set.weightKg ?: 0.0) > 0.0) joinTimes("+$weight", set.reps?.toString()) else set.reps?.let(repsText)
            ExerciseType.BODYWEIGHT_ASSISTED ->
                if ((set.weightKg ?: 0.0) > 0.0) joinTimes("−$weight", set.reps?.toString()) else set.reps?.let(repsText)
            ExerciseType.REPS_ONLY -> set.reps?.let(repsText)
            ExerciseType.DURATION, ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> time
            ExerciseType.WEIGHT_DURATION -> joinTimes(weight, time)
            ExerciseType.DISTANCE_DURATION -> {
                val distance = set.distanceMeters?.takeIf { it > 0.0 }?.let { distance(it, distanceUnit) }
                listOfNotNull(distance, time).joinToString(" · ").ifEmpty { null }
            }
            ExerciseType.WEIGHT_DISTANCE -> joinTimes(weight, set.distanceMeters?.let { distance(it, distanceUnit) })
        }
        return text ?: NONE
    }

    /** What an empty cell or a missing best set reads, the app's usual em dash. */
    const val NONE = "—"

    /**
     * How far from a whole hundredth a displayed weight may be and still count as typed in this
     * unit. A pounds entry converts to kg and back within about 1e-13 ("185" stays 185), while a
     * kg entry shown in pounds is off by far more (80 kg is 176.3698 lb).
     */
    private const val AS_LOGGED_TOLERANCE = 1e-6

    private fun joinTimes(left: String?, right: String?): String? = listOfNotNull(left, right).joinToString(" × ").ifEmpty { null }
}

/** [SetFormatting.bestSet] with the `set_reps` plural ("1 rep", "12 reps"). */
@Composable
fun bestSetText(type: ExerciseType, set: StatSet?, weightUnit: WeightUnit, distanceUnit: DistanceUnit): String {
    val resources = LocalResources.current
    return SetFormatting.bestSet(type, set, weightUnit, distanceUnit) { resources.getQuantityString(R.plurals.set_reps, it, it) }
}
