package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * M17 equipment-editor invariants (§5.1.5 "Manage"): entries round to the calculator's quarter-unit
 * grid, dedupe, stay sorted, and the last bar can never be removed. F9: the kg and lb sets are
 * edited independently. All expected values literal.
 */
class PlateEquipmentTest {

    @Test
    fun `default equipment is one 20 bar and the seven standard denominations`() {
        assertEquals(listOf(20.0), defaultPlateEquipment.barsKg)
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), defaultPlateEquipment.platesKg)
    }

    // --- Bars ---

    @Test
    fun `withBarAdded inserts in sorted order`() {
        val equipment = defaultPlateEquipment.withBarAdded(WeightUnit.KG, 15.0).withBarAdded(WeightUnit.KG, 25.0)
        assertEquals(listOf(15.0, 20.0, 25.0), equipment.barsKg)
    }

    @Test
    fun `withBarAdded rounds to the quarter-kg grid before storing`() {
        assertEquals(listOf(17.5, 20.0), defaultPlateEquipment.withBarAdded(WeightUnit.KG, 17.4).barsKg)
    }

    @Test
    fun `withBarAdded ignores duplicates and non-positive weights`() {
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(WeightUnit.KG, 20.0).barsKg)
        // A weight that rounds onto an existing entry is a duplicate too.
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(WeightUnit.KG, 20.1).barsKg)
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(WeightUnit.KG, 0.0).barsKg)
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(WeightUnit.KG, -7.5).barsKg)
    }

    @Test
    fun `withBarRemoved never removes the last bar`() {
        val single = defaultPlateEquipment
        assertSame(single, single.withBarRemoved(WeightUnit.KG, 20.0))
        assertEquals(listOf(20.0), single.withBarRemoved(WeightUnit.KG, 20.0).barsKg)
    }

    @Test
    fun `withBarRemoved drops the matching bar when more than one remains`() {
        val two = defaultPlateEquipment.withBarAdded(WeightUnit.KG, 15.0)
        assertEquals(listOf(20.0), two.withBarRemoved(WeightUnit.KG, 15.0).barsKg)
    }

    @Test
    fun `withBarRemoved matches on the same rounded value the add stored`() {
        // 17.4 was stored as 17.5; removing with the same raw input must find it.
        val two = defaultPlateEquipment.withBarAdded(WeightUnit.KG, 17.4)
        assertEquals(listOf(20.0), two.withBarRemoved(WeightUnit.KG, 17.4).barsKg)
    }

    // --- Plates ---

    @Test
    fun `withPlateAdded rounds, dedupes and keeps the list sorted`() {
        val added = defaultPlateEquipment.withPlateAdded(WeightUnit.KG, 0.6)
        assertEquals(listOf(0.5, 1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), added.platesKg)
        assertEquals(added.platesKg, added.withPlateAdded(WeightUnit.KG, 2.5).platesKg)
    }

    @Test
    fun `withPlateRemoved can empty the plate list entirely`() {
        var equipment = defaultPlateEquipment
        for (plate in defaultPlateEquipment.platesKg) equipment = equipment.withPlateRemoved(WeightUnit.KG, plate)
        assertEquals(emptyList<Double>(), equipment.platesKg)
        // ...while the bar invariant still holds independently.
        assertEquals(listOf(20.0), equipment.barsKg)
    }

    @Test
    fun `bar and plate operations never touch the other list`() {
        val equipment = defaultPlateEquipment.withBarAdded(WeightUnit.KG, 15.0).withPlateRemoved(WeightUnit.KG, 25.0)
        assertEquals(listOf(15.0, 20.0), equipment.barsKg)
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0), equipment.platesKg)
    }

    // --- F9: the pound set ---

    @Test
    fun `default pound equipment is one 45 bar and the six standard US plates`() {
        assertEquals(listOf(45.0), defaultPlateEquipment.barsLb)
        assertEquals(listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0), defaultPlateEquipment.platesLb)
    }

    @Test
    fun `setFor picks each unit's own set, in its own numbers`() {
        assertEquals(PlateSet(bars = listOf(20.0), plates = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0)), defaultPlateEquipment.setFor(WeightUnit.KG))
        assertEquals(PlateSet(bars = listOf(45.0), plates = listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0)), defaultPlateEquipment.setFor(WeightUnit.LB))
    }

    @Test
    fun `pound edits round to the quarter-lb grid and leave the kg set alone`() {
        val equipment = defaultPlateEquipment
            .withBarAdded(WeightUnit.LB, 35.1) // stored as 35.0
            .withPlateAdded(WeightUnit.LB, 1.3) // stored as 1.25
            .withPlateRemoved(WeightUnit.LB, 35.0)
        assertEquals(listOf(35.0, 45.0), equipment.barsLb)
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 25.0, 45.0), equipment.platesLb)
        assertEquals(listOf(20.0), equipment.barsKg)
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), equipment.platesKg)
    }

    @Test
    fun `kg edits leave the pound set alone`() {
        val equipment = defaultPlateEquipment.withBarAdded(WeightUnit.KG, 15.0).withPlateRemoved(WeightUnit.KG, 25.0)
        assertEquals(listOf(45.0), equipment.barsLb)
        assertEquals(listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0), equipment.platesLb)
    }

    @Test
    fun `the last pound bar is not removable, whatever the kg set holds`() {
        val equipment = defaultPlateEquipment.withBarAdded(WeightUnit.KG, 15.0)
        assertSame(equipment, equipment.withBarRemoved(WeightUnit.LB, 45.0))
        val twoPoundBars = equipment.withBarAdded(WeightUnit.LB, 35.0)
        assertEquals(listOf(35.0), twoPoundBars.withBarRemoved(WeightUnit.LB, 45.0).barsLb)
    }

    @Test
    fun `standard bars are 20 kg and 45 lb`() {
        assertEquals(20.0, standardBar(WeightUnit.KG), 0.0)
        assertEquals(45.0, standardBar(WeightUnit.LB), 0.0)
    }
}
