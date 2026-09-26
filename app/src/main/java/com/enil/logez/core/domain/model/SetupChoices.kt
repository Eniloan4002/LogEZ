package com.enil.logez.core.domain.model

import java.time.DayOfWeek

/**
 * What the first-run setup screen asks for, and all that its Continue writes: the two units the
 * first session shows, the body-measurement unit that follows the weight unit, and the first day
 * of the week. Every other setting keeps its default.
 *
 * [lengthUnit] is derived rather than stored, so it can never disagree with [weightUnit]: the
 * setup screen tells the user that body measurements use cm with kg and inches with lb, and that
 * they can change it separately later in Settings.
 */
data class SetupChoices(
    val weightUnit: WeightUnit,
    val distanceUnit: DistanceUnit,
    val firstDayOfWeek: DayOfWeek,
) {
    val lengthUnit: LengthUnit get() = lengthUnitFor(weightUnit)

    /** The four setup keys applied over [settings]; everything else is left as it is. */
    fun applyTo(settings: UserSettings): UserSettings = settings.copy(
        weightUnit = weightUnit,
        distanceUnit = distanceUnit,
        lengthUnit = lengthUnit,
        firstDayOfWeek = firstDayOfWeek,
    )

    companion object {
        /**
         * The only week starts the app offers, the same three as the Settings and Calendar
         * pickers. A day outside this list could be stored but never shown as selected.
         */
        val OFFERED_FIRST_DAYS: List<DayOfWeek> = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun lengthUnitFor(weightUnit: WeightUnit): LengthUnit = when (weightUnit) {
            WeightUnit.KG -> LengthUnit.CM
            WeightUnit.LB -> LengthUnit.IN
        }
    }
}

/**
 * Which of the setup values the settings store actually holds, as opposed to the defaults the
 * `settings` Flow fills in for a missing key. A null field means the key is absent, or holds a name
 * this build's enum does not have; either way there is no stored choice to preselect, and nothing
 * is rewritten until the user presses Continue on a screen that shows the value.
 */
data class StoredSetupValues(
    val weightUnit: WeightUnit? = null,
    val distanceUnit: DistanceUnit? = null,
    val firstDayOfWeek: DayOfWeek? = null,
)
