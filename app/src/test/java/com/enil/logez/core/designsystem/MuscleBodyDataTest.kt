package com.enil.logez.core.designsystem

import com.enil.logez.core.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-data checks on the traced region tables (see that file's KDoc for provenance) — the actual
 * SVG geometry/rendering is Compose-only and unit-testable only indirectly through this data.
 */
class MuscleBodyDataTest {
    @Test
    fun `every diagrammable MuscleGroup has at least one mapped region, in both genders`() {
        assertEquals(BodyDiagramRegions.MAPPABLE, MALE_MUSCLE_REGIONS.mapNotNull { it.group }.toSet())
        assertEquals(BodyDiagramRegions.MAPPABLE, FEMALE_MUSCLE_REGIONS.mapNotNull { it.group }.toSet())
    }

    @Test
    fun `every region has non-blank id and path data, in both genders`() {
        (MALE_MUSCLE_REGIONS + FEMALE_MUSCLE_REGIONS).forEach { region ->
            assertTrue("blank id", region.id.isNotBlank())
            assertTrue("blank pathData for ${region.id}", region.pathData.isNotBlank())
        }
    }

    @Test
    fun `region ids are unique within each gender`() {
        val maleIds = MALE_MUSCLE_REGIONS.map { it.id }
        assertEquals(maleIds.size, maleIds.toSet().size)
        val femaleIds = FEMALE_MUSCLE_REGIONS.map { it.id }
        assertEquals(femaleIds.size, femaleIds.toSet().size)
    }

    @Test
    fun `male and female region lists have the same size and the same id set`() {
        assertEquals(MALE_MUSCLE_REGIONS.size, FEMALE_MUSCLE_REGIONS.size)
        assertEquals(MALE_MUSCLE_REGIONS.map { it.id }.toSet(), FEMALE_MUSCLE_REGIONS.map { it.id }.toSet())
    }

    @Test
    fun `mappable set excludes the three non-anatomical groups`() {
        assertTrue(MuscleGroup.CARDIO !in BodyDiagramRegions.MAPPABLE)
        assertTrue(MuscleGroup.FULL_BODY !in BodyDiagramRegions.MAPPABLE)
        assertTrue(MuscleGroup.OTHER !in BodyDiagramRegions.MAPPABLE)
    }
}
