package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviousValueResolverTest {
    private fun stat(setId: String, workoutId: String, routineId: String?, orderIndex: Int, reps: Int) = StatSet(
        setId = setId, workoutId = workoutId, workoutStartedAt = 0, orderIndex = orderIndex, setType = SetType.NORMAL,
        weightKg = 50.0, reps = reps, durationSeconds = null, distanceMeters = null, customMetric = null,
        isCompleted = true, rpe = null, routineId = routineId,
    )

    @Test
    fun `ANY_WORKOUT returns the most recent workout's sets, ignoring routineId`() {
        // Rows already ordered most-recent-workout-first, as WorkoutDao.getStatRowsForExercise guarantees.
        val rows = listOf(
            stat("s1", "w2", "r-other", 0, 8),
            stat("s2", "w2", "r-other", 1, 7),
            stat("s3", "w1", "r-target", 0, 10),
        )

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.ANY_WORKOUT, currentRoutineId = "r-target")

        assertEquals(listOf("s1", "s2"), result.map { it.setId })
    }

    @Test
    fun `SAME_ROUTINE returns the most recent workout sharing the current routineId`() {
        val rows = listOf(
            stat("s1", "w3", "r-other", 0, 8), // most recent overall, but different routine
            stat("s2", "w2", "r-target", 0, 9),
            stat("s3", "w1", "r-target", 0, 10),
        )

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.SAME_ROUTINE, currentRoutineId = "r-target")

        assertEquals(listOf("s2"), result.map { it.setId })
    }

    @Test
    fun `SAME_ROUTINE with no matching routine returns empty, never falls back to ANY_WORKOUT`() {
        val rows = listOf(stat("s1", "w1", "r-other", 0, 8))

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.SAME_ROUTINE, currentRoutineId = "r-target")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty input returns empty regardless of mode`() {
        assertTrue(resolvePreviousWorkoutSets(emptyList(), PreviousValuesMode.ANY_WORKOUT, null).isEmpty())
    }
}
