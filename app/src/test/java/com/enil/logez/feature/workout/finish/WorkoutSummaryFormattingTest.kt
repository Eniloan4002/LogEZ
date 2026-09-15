package com.enil.logez.feature.workout.finish

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryFormattingTest {
    @Test fun `summary numbers omit trailing zeroes and cap fractions at hundredths`() {
        assertEquals("100", formatSummaryNumber(100.0))
        assertEquals("12.5", formatSummaryNumber(12.5))
        assertEquals("12.35", formatSummaryNumber(12.345))
    }

    @Test fun `summary units use the same two-decimal rule`() {
        assertEquals("1234.57kg", formatSummaryVolume(1234.567))
        assertEquals("2.35km", formatSummaryDistance(2345.0))
    }
}
