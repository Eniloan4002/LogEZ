package com.enil.logez.feature.workout.finish

import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.WeightUnit
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Recap numbers: no trailing zeroes and never more than two fractional digits. */
internal fun formatSummaryNumber(value: Double): String = DecimalFormat(
    "0.##",
    DecimalFormatSymbols.getInstance(Locale.ROOT),
).apply { roundingMode = RoundingMode.HALF_UP }.format(value)

internal fun formatSummaryVolume(kg: Double, unit: WeightUnit): String =
    formatSummaryNumber(WeightDisplay.toDisplay(kg, unit)) + if (unit == WeightUnit.KG) "kg" else "lb"

internal fun formatSummaryDistance(meters: Double): String = "${formatSummaryNumber(meters / 1_000.0)}km"
