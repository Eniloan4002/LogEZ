package com.enil.logez.core.common

import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.text.util.LocalePreferences
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import java.util.Locale
import javax.inject.Inject

/**
 * The units and week start the phone's region suggests, for first-run setup to preselect. Only a
 * suggestion: stored settings beat it, and nothing here changes `UserSettings()`'s own defaults,
 * which existing installs that never set a unit still read.
 */
interface RegionDefaults {
    fun suggest(): RegionSuggestion
}

data class RegionSuggestion(
    val weightUnit: WeightUnit,
    val distanceUnit: DistanceUnit,
    val lengthUnit: LengthUnit,
    val firstDayOfWeek: DayOfWeek,
    /**
     * True when the region's week start is not one the app offers (Monday, Saturday or Sunday),
     * so [firstDayOfWeek] fell back to Monday. The setup screen then does not claim its choices
     * came from the region.
     */
    val weekStartClamped: Boolean,
)

/** CLDR's three measurement systems, as far as the app's units go. */
enum class MeasurementSystem { METRIC, US, UK }

/**
 * Reads the default locale outside composition (the `NonObservableLocale` lint only concerns
 * composables; day names on screen still use `currentLocale()`).
 */
// No default for the logger: with every parameter defaulted, Kotlin would add a second, no-argument
// constructor carrying the same @Inject, which Dagger rejects.
class AndroidRegionDefaults @Inject constructor(
    private val logger: AppLogger,
) : RegionDefaults {
    override fun suggest(): RegionSuggestion {
        val locale = Locale.getDefault()
        // Literal SDK_INT check, not a helper: NewApi is fatal in this build, and lint only
        // accepts the guard when it can see it around the call.
        val icuSystem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { IcuMeasurementSystem.of(locale) }
                .onFailure { logger.e(TAG, "ICU measurement system lookup failed; using the country code", it) }
                .getOrNull()
        } else {
            null
        }
        val weekStartCode = runCatching { LocalePreferences.getFirstDayOfWeek(locale) }
            .onFailure { logger.e(TAG, "Week start lookup failed; suggesting Monday", it) }
            .getOrDefault("")
        return regionSuggestion(locale, icuSystem, weekStartCode)
    }

    private companion object {
        const val TAG = "RegionDefaults"
    }
}

@RequiresApi(Build.VERSION_CODES.P)
internal object IcuMeasurementSystem {
    fun of(locale: Locale): MeasurementSystem? =
        when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
            LocaleData.MeasurementSystem.US -> MeasurementSystem.US
            LocaleData.MeasurementSystem.UK -> MeasurementSystem.UK
            LocaleData.MeasurementSystem.SI -> MeasurementSystem.METRIC
            else -> null
        }
}

/**
 * The pure mapping behind [AndroidRegionDefaults], with the two platform lookups passed in as
 * their results so tests don't depend on the ICU data of whichever runtime runs them.
 *
 * @param icuSystem ICU's answer (API 28+), or null where ICU can't be asked (API 26-27) or gave
 *   no answer. Null falls back to the locale's country code.
 * @param weekStartCode `LocalePreferences.getFirstDayOfWeek`'s answer: "mon", "sat", "sun" and so
 *   on, or "" when unknown.
 */
fun regionSuggestion(locale: Locale, icuSystem: MeasurementSystem?, weekStartCode: String): RegionSuggestion {
    // The country, not the whole Locale: Android 14 appends -u-fw and -u-mu extensions to the
    // default locale, and an en-US-u-fw-sun phone is still a US phone.
    val system = icuSystem ?: measurementSystemForCountry(locale.country)
    val (weight, distance, length) = when (system) {
        MeasurementSystem.US -> Triple(WeightUnit.LB, DistanceUnit.MILES, LengthUnit.IN)
        MeasurementSystem.UK -> Triple(WeightUnit.KG, DistanceUnit.MILES, LengthUnit.CM)
        MeasurementSystem.METRIC -> Triple(WeightUnit.KG, DistanceUnit.KM, LengthUnit.CM)
    }
    val weekStart = weekStartFor(weekStartCode)
    return RegionSuggestion(
        weightUnit = weight,
        distanceUnit = distance,
        lengthUnit = length,
        firstDayOfWeek = weekStart ?: DayOfWeek.MONDAY,
        weekStartClamped = weekStart == null,
    )
}

/**
 * The API 26-27 fallback, from CLDR 48's measurementSystem data: the US and Liberia use US units,
 * the UK and Myanmar use kg with miles, and everywhere else is metric.
 */
fun measurementSystemForCountry(country: String): MeasurementSystem = when (country.uppercase(Locale.ROOT)) {
    "US", "LR" -> MeasurementSystem.US
    "GB", "MM" -> MeasurementSystem.UK
    else -> MeasurementSystem.METRIC
}

/** Only the three week starts the app offers; null for anything else, including "". */
private fun weekStartFor(code: String): DayOfWeek? = when (code) {
    LocalePreferences.FirstDayOfWeek.MONDAY -> DayOfWeek.MONDAY
    LocalePreferences.FirstDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
    LocalePreferences.FirstDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
    else -> null
}
