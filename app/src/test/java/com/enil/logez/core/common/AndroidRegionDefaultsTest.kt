package com.enil.logez.core.common

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real ICU and `LocalePreferences` calls on sdk 34 (the API 28+ branch). Robolectric's ICU data
 * is not a phone's, so only locales whose data has been stable for years are used. The API 26-27
 * branch (country-code fallback) can't be selected here and is checked on an API 26 emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidRegionDefaultsTest {
    private lateinit var original: Locale

    @Before
    fun rememberLocale() {
        original = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `en-US suggests lb, miles and a Sunday week start`() {
        Locale.setDefault(Locale.forLanguageTag("en-US"))
        val s = AndroidRegionDefaults(AppLogger.NoOp).suggest()
        assertEquals(WeightUnit.LB, s.weightUnit)
        assertEquals(DistanceUnit.MILES, s.distanceUnit)
        assertEquals(DayOfWeek.SUNDAY, s.firstDayOfWeek)
        assertFalse(s.weekStartClamped)
    }

    @Test
    fun `de-DE suggests kg, km and a Monday week start`() {
        Locale.setDefault(Locale.forLanguageTag("de-DE"))
        val s = AndroidRegionDefaults(AppLogger.NoOp).suggest()
        assertEquals(WeightUnit.KG, s.weightUnit)
        assertEquals(DistanceUnit.KM, s.distanceUnit)
        assertEquals(DayOfWeek.MONDAY, s.firstDayOfWeek)
        assertFalse(s.weekStartClamped)
    }

    @Test
    fun `the ICU branch runs - a US locale with a UK region override suggests kg and miles`() {
        // ICU honours the -u-rg- override; the country-code fallback would read US and give lb.
        // This fails if the API 28+ branch is skipped or its ICU call throws.
        Locale.setDefault(Locale.forLanguageTag("en-US-u-rg-gbzzzz"))
        val s = AndroidRegionDefaults(AppLogger.NoOp).suggest()
        assertEquals(WeightUnit.KG, s.weightUnit)
        assertEquals(DistanceUnit.MILES, s.distanceUnit)
    }

    @Test
    fun `ICU's three measurement systems map to the app's`() {
        assertEquals(MeasurementSystem.US, IcuMeasurementSystem.of(Locale.US))
        assertEquals(MeasurementSystem.UK, IcuMeasurementSystem.of(Locale.UK))
        assertEquals(MeasurementSystem.METRIC, IcuMeasurementSystem.of(Locale.GERMANY))
    }
}
