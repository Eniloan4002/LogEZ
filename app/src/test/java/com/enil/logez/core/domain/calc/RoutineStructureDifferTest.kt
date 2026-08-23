package com.enil.logez.core.domain.calc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §5.1.8(b): the Update-Routine prompt fires on structural change only. The negative cases matter
 * as much as the positive ones — a differ that over-fires trains the user to dismiss the prompt.
 */
class RoutineStructureDifferTest {
    @Test
    fun `identical shape is not a structural change`() {
        val shape = listOf(ExerciseShape("bench", 3), ExerciseShape("squat", 3))
        assertFalse(RoutineStructureDiffer.isStructurallyChanged(shape, shape))
    }

    @Test
    fun `adding an exercise is structural`() {
        assertTrue(
            RoutineStructureDiffer.isStructurallyChanged(
                listOf(ExerciseShape("bench", 3)),
                listOf(ExerciseShape("bench", 3), ExerciseShape("squat", 3)),
            ),
        )
    }

    @Test
    fun `removing an exercise is structural`() {
        assertTrue(
            RoutineStructureDiffer.isStructurallyChanged(
                listOf(ExerciseShape("bench", 3), ExerciseShape("squat", 3)),
                listOf(ExerciseShape("bench", 3)),
            ),
        )
    }

    @Test
    fun `adding a set to an existing exercise is structural`() {
        assertTrue(
            RoutineStructureDiffer.isStructurallyChanged(
                listOf(ExerciseShape("bench", 3)),
                listOf(ExerciseShape("bench", 4)),
            ),
        )
    }

    @Test
    fun `reordering the same exercises is structural`() {
        assertTrue(
            RoutineStructureDiffer.isStructurallyChanged(
                listOf(ExerciseShape("bench", 3), ExerciseShape("squat", 3)),
                listOf(ExerciseShape("squat", 3), ExerciseShape("bench", 3)),
            ),
        )
    }

    @Test
    fun `swapping one exercise for another is structural`() {
        assertTrue(
            RoutineStructureDiffer.isStructurallyChanged(
                listOf(ExerciseShape("bench", 3)),
                listOf(ExerciseShape("incline", 3)),
            ),
        )
    }
}
