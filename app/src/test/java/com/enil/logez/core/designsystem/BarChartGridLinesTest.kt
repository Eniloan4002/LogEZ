package com.enil.logez.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/** The axis dedupe rule, now covered by tests (it was extracted from BarChart unchanged for the Profile redesign, 2026-10-01). */
class BarChartGridLinesTest {
    private val rounded: (Double) -> String = { Math.round(it).toString() }

    @Test
    fun `a series with a maximum of 1 labels the top and the baseline only`() {
        // Math.round(0.5) is 1, so an undeduplicated middle line would print "1 / 1 / 0".
        assertEquals(listOf("1", "0"), barChartGridLines(1.0, rounded).map { it.second })
    }

    @Test
    fun `a roomy series keeps top, middle and baseline`() {
        assertEquals(listOf("8", "4", "0"), barChartGridLines(8.0, rounded).map { it.second })
    }

    @Test
    fun `an all-zero series keeps only the baseline`() {
        assertEquals(listOf("0"), barChartGridLines(0.0, rounded).map { it.second })
    }

    @Test
    fun `a middle label equal to the baseline's is dropped`() {
        // 0.5 rounds to 1 here, but a maximum of 0.4 makes mid 0.2 -> "0", equal to the baseline's.
        assertEquals(listOf("0"), barChartGridLines(0.4, rounded).map { it.second })
    }
}
