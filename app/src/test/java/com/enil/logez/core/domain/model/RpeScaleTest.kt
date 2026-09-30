package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PHASE2_PLAN.md §5.1.7 — RPE's exact 8-value enum, "unenterable outside it by construction".
 * P-211 (2026-09-30): RIR as a display/entry layer over the same stored RPE. Every expected value
 * is a literal from the README's tables, never recomputed.
 */
class RpeScaleTest {
    @Test
    fun `the scale is exactly the eight plan-specified values, in ascending order`() {
        assertEquals(listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0), RpeScale.VALUES)
    }

    @Test
    fun `whole-number values format without a trailing decimal, half-steps keep theirs`() {
        assertEquals("6", RpeScale.format(6.0))
        assertEquals("10", RpeScale.format(10.0))
        assertEquals("7.5", RpeScale.format(7.5))
        assertEquals("8.5", RpeScale.format(8.5))
    }

    // ---- P-211 ----

    @Test
    fun `every picker value reads as the README's RIR`() {
        assertEquals("0", RpeScale.formatRir(10.0))
        assertEquals("0–1", RpeScale.formatRir(9.5))
        assertEquals("1", RpeScale.formatRir(9.0))
        assertEquals("1–2", RpeScale.formatRir(8.5))
        assertEquals("2", RpeScale.formatRir(8.0))
        assertEquals("2–3", RpeScale.formatRir(7.5))
        assertEquals("3", RpeScale.formatRir(7.0))
        assertEquals("4+", RpeScale.formatRir(6.0))
    }

    @Test
    fun `off-scale values from a hand-edited backup still format, rounded to a half step`() {
        assertEquals("3–4", RpeScale.formatRir(6.5))
        assertEquals("4+", RpeScale.formatRir(5.0))
        assertEquals("4+", RpeScale.formatRir(6.2))
        assertEquals("1–2", RpeScale.formatRir(8.25))
        assertEquals("0", RpeScale.formatRir(10.5))
        assertEquals("8.5", RpeScale.format(8.25))
        assertEquals("8", RpeScale.format(8.2))
        assertEquals("5", RpeScale.format(5.0))
        assertEquals("6.5", RpeScale.format(6.5))
    }

    @Test
    fun `the scale-aware formatter picks the scale`() {
        assertEquals("8.5", RpeScale.format(8.5, EffortScale.RPE))
        assertEquals("1–2", RpeScale.format(8.5, EffortScale.RIR))
        assertEquals("10", RpeScale.format(10.0, EffortScale.RPE))
        assertEquals("0", RpeScale.format(10.0, EffortScale.RIR))
    }

    @Test
    fun `the RIR chips are 0 1 2 3 4+ and store RPE 10 9 8 7 6`() {
        assertEquals(listOf(10.0, 9.0, 8.0, 7.0, 6.0), RpeScale.RIR_CHIP_VALUES)
        assertEquals(listOf("0", "1", "2", "3", "4+"), RpeScale.RIR_CHIP_VALUES.map { RpeScale.formatRir(it) })
        assertEquals(listOf(10.0, 9.0, 8.0, 7.0, 6.0), RpeScale.chipValues(EffortScale.RIR))
        assertEquals(listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0), RpeScale.chipValues(EffortScale.RPE))
    }

    @Test
    fun `a stored value selects its chip only when the picker has that exact value`() {
        assertEquals(9.0, RpeScale.selectedChip(9.0, EffortScale.RIR))
        assertEquals(9.0, RpeScale.selectedChip(9.0, EffortScale.RPE))
        assertNull(RpeScale.selectedChip(8.5, EffortScale.RIR))
        assertEquals(8.5, RpeScale.selectedChip(8.5, EffortScale.RPE))
        assertNull(RpeScale.selectedChip(6.5, EffortScale.RPE))
        assertNull(RpeScale.selectedChip(5.0, EffortScale.RIR))
        assertNull(RpeScale.selectedChip(null, EffortScale.RPE))
    }

    @Test
    fun `half-step rounding goes to the nearest 0_5, a tie upward`() {
        assertEquals(8.5, RpeScale.roundToHalfStep(8.25), 0.0)
        assertEquals(8.0, RpeScale.roundToHalfStep(8.2), 0.0)
        assertEquals(9.0, RpeScale.roundToHalfStep(8.75), 0.0)
        assertEquals(7.0, RpeScale.roundToHalfStep(7.0), 0.0)
    }

    @Test
    fun `every value has a caption step inside 6 to 10`() {
        assertEquals(10.0, RpeScale.captionStep(10.0), 0.0)
        assertEquals(10.0, RpeScale.captionStep(11.0), 0.0)
        assertEquals(9.5, RpeScale.captionStep(9.5), 0.0)
        assertEquals(6.5, RpeScale.captionStep(6.5), 0.0)
        assertEquals(6.0, RpeScale.captionStep(4.0), 0.0)
        assertEquals(8.5, RpeScale.captionStep(8.3), 0.0)
    }
}
