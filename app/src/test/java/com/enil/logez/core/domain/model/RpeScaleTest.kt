package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PHASE2_PLAN.md §5.1.7 — RPE's exact 8-value enum, "unenterable outside it by construction". */
class RpeScaleTest {
    @Test
    fun `the scale is exactly the eight plan-specified values, in ascending order`() {
        assertEquals(listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0), RpeScale.VALUES)
    }

    @Test
    fun `every value has a non-blank reserve description`() {
        RpeScale.VALUES.forEach { assertTrue("$it has no description", RpeScale.reserveDescription(it).isNotBlank()) }
    }

    @Test
    fun `whole-number values format without a trailing decimal, half-steps keep theirs`() {
        assertEquals("6", RpeScale.format(6.0))
        assertEquals("10", RpeScale.format(10.0))
        assertEquals("7.5", RpeScale.format(7.5))
        assertEquals("8.5", RpeScale.format(8.5))
    }
}
