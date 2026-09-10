package com.enil.logez.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scoped to [Formatting.distanceKm] only -- the one new formatter this diff added, and the only
 * one with zero direct or indirect coverage (adversarial review, 2026-09-10). The pre-existing
 * [Formatting.weightKg]/[Formatting.mmSs] have a separately-tracked, unfixed locale bug (they
 * format with the default locale, not `Locale.ROOT`) that a passing test here shouldn't paper over.
 */
class FormattingTest {
    @Test
    fun `a whole number of kilometers has no decimal point`() {
        assertEquals("2km", Formatting.distanceKm(2000.0))
        assertEquals("0km", Formatting.distanceKm(0.0))
    }

    @Test
    fun `a fractional distance shows two decimal places`() {
        assertEquals("2.35km", Formatting.distanceKm(2350.0))
        assertEquals("0.15km", Formatting.distanceKm(150.0))
    }

    @Test
    fun `float drift just under a whole km still rounds to a clean value, not a garbled one`() {
        // 2999.999999m is a hair under 3km -- the whole-vs-decimal branch picks the decimal path
        // here (2.999999999 != 2.0), but the rounded *value* must still read as a sane "3.00km",
        // not an artifact of the drift (e.g. "2.99km" from truncation, or a trailing-digit glitch).
        assertEquals("3.00km", Formatting.distanceKm(2999.999999))
    }

    @Test
    fun `formatDistanceKm the top-level alias delegates to the same result`() {
        assertEquals(Formatting.distanceKm(1234.0), formatDistanceKm(1234.0))
    }
}
