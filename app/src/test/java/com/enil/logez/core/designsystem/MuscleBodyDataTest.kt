package com.enil.logez.core.designsystem

import com.enil.logez.core.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-data checks on the traced region table (see that file's KDoc for provenance) — the actual
 * SVG geometry/rendering is Compose-only and unit-testable only indirectly through this data.
 */
class MuscleBodyDataTest {
    @Test
    fun `every diagrammable MuscleGroup has at least one mapped region`() {
        val mapped = MUSCLE_BODY_REGIONS.mapNotNull { it.group }.toSet()
        assertEquals(BodyDiagramRegions.MAPPABLE, mapped)
    }

    @Test
    fun `every region has non-blank id and path data`() {
        MUSCLE_BODY_REGIONS.forEach { region ->
            assertTrue("blank id", region.id.isNotBlank())
            assertTrue("blank pathData for ${region.id}", region.pathData.isNotBlank())
        }
    }

    @Test
    fun `region ids are unique`() {
        val ids = MUSCLE_BODY_REGIONS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `both sides are represented`() {
        val sides = MUSCLE_BODY_REGIONS.map { it.side }.toSet()
        assertEquals(setOf(BodySide.FRONT, BodySide.BACK), sides)
    }

    @Test
    fun `mappable set excludes the three non-anatomical groups`() {
        assertTrue(MuscleGroup.CARDIO !in BodyDiagramRegions.MAPPABLE)
        assertTrue(MuscleGroup.FULL_BODY !in BodyDiagramRegions.MAPPABLE)
        assertTrue(MuscleGroup.OTHER !in BodyDiagramRegions.MAPPABLE)
    }
}
