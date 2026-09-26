package com.enil.logez.core.domain.model

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupChoicesTest {
    @Test
    fun `body measurements follow the weight unit`() {
        assertEquals(LengthUnit.CM, SetupChoices(WeightUnit.KG, DistanceUnit.MILES, DayOfWeek.MONDAY).lengthUnit)
        assertEquals(LengthUnit.IN, SetupChoices(WeightUnit.LB, DistanceUnit.KM, DayOfWeek.MONDAY).lengthUnit)
    }

    @Test
    fun `applyTo changes the four setup values and nothing else`() {
        val before = UserSettings(lengthUnit = LengthUnit.CM, defaultRestTimerSeconds = 120, weeklyActiveDayTarget = 5)

        val after = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY).applyTo(before)

        assertEquals(
            before.copy(
                weightUnit = WeightUnit.LB,
                distanceUnit = DistanceUnit.MILES,
                lengthUnit = LengthUnit.IN,
                firstDayOfWeek = DayOfWeek.SUNDAY,
            ),
            after,
        )
    }

    @Test
    fun `the offered week starts are Monday, Saturday and Sunday`() {
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), SetupChoices.OFFERED_FIRST_DAYS)
    }
}
