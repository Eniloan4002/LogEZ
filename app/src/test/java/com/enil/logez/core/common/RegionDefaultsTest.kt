package com.enil.logez.core.common

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure region mapping. ICU and `LocalePreferences` answers are passed in, so these results
 * don't depend on the ICU data of the JVM or Robolectric running them.
 */
class RegionDefaultsTest {
    private fun locale(tag: String): Locale = Locale.forLanguageTag(tag)

    // ---- measurement system, API 26-27 path (no ICU answer: the country decides) ----

    @Test
    fun `US and Liberia get lb, miles and inches from the country code`() {
        for (tag in listOf("en-US", "en-LR")) {
            val s = regionSuggestion(locale(tag), icuSystem = null, weekStartCode = "sun")
            assertEquals(tag, WeightUnit.LB, s.weightUnit)
            assertEquals(tag, DistanceUnit.MILES, s.distanceUnit)
            assertEquals(tag, LengthUnit.IN, s.lengthUnit)
        }
    }

    @Test
    fun `the UK and Myanmar get kg with miles and cm from the country code`() {
        for (tag in listOf("en-GB", "my-MM")) {
            val s = regionSuggestion(locale(tag), icuSystem = null, weekStartCode = "mon")
            assertEquals(tag, WeightUnit.KG, s.weightUnit)
            assertEquals(tag, DistanceUnit.MILES, s.distanceUnit)
            assertEquals(tag, LengthUnit.CM, s.lengthUnit)
        }
    }

    @Test
    fun `the Philippines and a locale with no country are metric`() {
        for (tag in listOf("en-PH", "fil-PH", "en")) {
            val s = regionSuggestion(locale(tag), icuSystem = null, weekStartCode = "sun")
            assertEquals(tag, WeightUnit.KG, s.weightUnit)
            assertEquals(tag, DistanceUnit.KM, s.distanceUnit)
            assertEquals(tag, LengthUnit.CM, s.lengthUnit)
        }
    }

    @Test
    fun `an Android 14 locale with regional-preference extensions still reads as its country`() {
        val s = regionSuggestion(locale("en-US-u-fw-sun-mu-celsius"), icuSystem = null, weekStartCode = "sun")
        assertEquals(WeightUnit.LB, s.weightUnit)
        assertEquals(DistanceUnit.MILES, s.distanceUnit)
    }

    @Test
    fun `measurementSystemForCountry ignores case and defaults to metric`() {
        assertEquals(MeasurementSystem.US, measurementSystemForCountry("us"))
        assertEquals(MeasurementSystem.UK, measurementSystemForCountry("gb"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForCountry(""))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForCountry("AU"))
    }

    // ---- measurement system, API 28+ path (ICU's answer wins over the country) ----

    @Test
    fun `an ICU answer is used as given, even where the country would say otherwise`() {
        val us = regionSuggestion(locale("en-PH"), icuSystem = MeasurementSystem.US, weekStartCode = "sun")
        assertEquals(WeightUnit.LB, us.weightUnit)
        assertEquals(DistanceUnit.MILES, us.distanceUnit)
        assertEquals(LengthUnit.IN, us.lengthUnit)

        val uk = regionSuggestion(locale("en-US"), icuSystem = MeasurementSystem.UK, weekStartCode = "mon")
        assertEquals(WeightUnit.KG, uk.weightUnit)
        assertEquals(DistanceUnit.MILES, uk.distanceUnit)
        assertEquals(LengthUnit.CM, uk.lengthUnit)

        val metric = regionSuggestion(locale("en-US"), icuSystem = MeasurementSystem.METRIC, weekStartCode = "sun")
        assertEquals(WeightUnit.KG, metric.weightUnit)
        assertEquals(DistanceUnit.KM, metric.distanceUnit)
        assertEquals(LengthUnit.CM, metric.lengthUnit)
    }

    // ---- week start ----

    @Test
    fun `mon, sat and sun pass through unclamped`() {
        val expected = mapOf("mon" to DayOfWeek.MONDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY)
        for ((code, day) in expected) {
            val s = regionSuggestion(locale("en-PH"), icuSystem = null, weekStartCode = code)
            assertEquals(code, day, s.firstDayOfWeek)
            assertFalse(code, s.weekStartClamped)
        }
    }

    @Test
    fun `any other week start becomes Monday and is marked clamped`() {
        for (code in listOf("fri", "tue", "wed", "thu", "", "garbage")) {
            val s = regionSuggestion(locale("en-PH"), icuSystem = null, weekStartCode = code)
            assertEquals("'$code'", DayOfWeek.MONDAY, s.firstDayOfWeek)
            assertTrue("'$code'", s.weekStartClamped)
        }
    }
}
