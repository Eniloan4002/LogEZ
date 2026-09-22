package com.enil.logez.feature.workout.finish

import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryFormattingTest {
    @Test fun `summary numbers omit trailing zeroes and cap fractions at hundredths`() {
        assertEquals("100", formatSummaryNumber(100.0))
        assertEquals("12.5", formatSummaryNumber(12.5))
        assertEquals("12.35", formatSummaryNumber(12.345))
    }

    @Test fun `summary units use the same two-decimal rule`() {
        assertEquals("1234.57kg", formatSummaryVolume(1234.567, WeightUnit.KG))
        assertEquals("2.35km", formatSummaryDistance(2345.0))
    }

    @Test fun `summary volume converts to pounds for a pounds user`() {
        // 100 kg is 220.46 lb; the stored figure is always kg, only the text changes.
        assertEquals("220.46lb", formatSummaryVolume(100.0, WeightUnit.LB))
        assertEquals("0lb", formatSummaryVolume(0.0, WeightUnit.LB))
    }
}
