package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** P-211 decision 9: only normal sets are numbered, 1..n per card; W, F and D keep their letters. */
class SetNumberingTest {
    private fun texts(vararg types: SetType) = SetNumbering.labels(types.toList()).map { it.text }

    @Test
    fun `a warm-up, two working sets and a failure set read W 1 F 2 in that order`() {
        assertEquals(listOf("W", "1", "F", "2"), texts(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE, SetType.NORMAL))
    }

    @Test
    fun `the bench card from the mockup reads W 1 2 F, not W 2 3 F`() {
        assertEquals(listOf("W", "1", "2", "F"), texts(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL, SetType.FAILURE))
    }

    @Test
    fun `a drop set after the normal sets does not advance the count`() {
        assertEquals(listOf("1", "2", "D"), texts(SetType.NORMAL, SetType.NORMAL, SetType.DROPSET))
        assertEquals(listOf("1", "D", "2"), texts(SetType.NORMAL, SetType.DROPSET, SetType.NORMAL))
    }

    @Test
    fun `all warm-ups are all lettered`() {
        assertEquals(listOf("W", "W", "W"), texts(SetType.WARMUP, SetType.WARMUP, SetType.WARMUP))
    }

    @Test
    fun `an empty card has no labels`() {
        assertEquals(emptyList<SetDisplayLabel>(), SetNumbering.labels(emptyList()))
    }

    @Test
    fun `a label carries a number only for a normal set`() {
        assertEquals(
            listOf(
                SetDisplayLabel(SetType.WARMUP, null),
                SetDisplayLabel(SetType.NORMAL, 1),
                SetDisplayLabel(SetType.DROPSET, null),
                SetDisplayLabel(SetType.NORMAL, 2),
            ),
            SetNumbering.labels(listOf(SetType.WARMUP, SetType.NORMAL, SetType.DROPSET, SetType.NORMAL)),
        )
    }

    @Test
    fun `the letters match the set-type menu`() {
        assertEquals("", SetNumbering.letter(SetType.NORMAL))
        assertEquals("W", SetNumbering.letter(SetType.WARMUP))
        assertEquals("F", SetNumbering.letter(SetType.FAILURE))
        assertEquals("D", SetNumbering.letter(SetType.DROPSET))
    }
}
