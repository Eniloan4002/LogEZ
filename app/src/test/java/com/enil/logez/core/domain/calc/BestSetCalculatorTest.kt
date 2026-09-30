package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P-211 decision 6: the History card's BEST SET, the README's per-type table. Each case names the
 * winning set by id, a literal; nothing is recomputed with the production ranking.
 */
class BestSetCalculatorTest {
    private fun set(
        id: String,
        order: Int,
        type: SetType = SetType.NORMAL,
        weight: Double? = null,
        reps: Int? = null,
        seconds: Int? = null,
        meters: Double? = null,
        completed: Boolean = true,
    ) = StatSet(
        setId = id,
        workoutId = "w1",
        workoutStartedAt = 0L,
        orderIndex = order,
        setType = type,
        weightKg = weight,
        reps = reps,
        durationSeconds = seconds,
        distanceMeters = meters,
        customMetric = null,
        isCompleted = completed,
    )

    private fun best(type: ExerciseType, vararg sets: StatSet, includeWarmups: Boolean = false) =
        BestSetCalculator.best(type, sets.toList(), includeWarmups)?.setId

    // ---- WEIGHT_REPS: highest estimated 1RM, earlier set on a tie ----

    @Test
    fun `the mockup's bench picks the first 80 x 8, not the warm-up or the 80 x 6`() {
        val sets = arrayOf(
            set("w", 0, SetType.WARMUP, 40.0, 10),
            set("s1", 1, weight = 80.0, reps = 8),
            set("s2", 2, weight = 80.0, reps = 8),
            set("f", 3, SetType.FAILURE, 80.0, 6),
        )
        assertEquals("s1", best(ExerciseType.WEIGHT_REPS, *sets))
    }

    @Test
    fun `a higher estimated 1RM beats a heavier single`() {
        assertEquals("b", best(ExerciseType.WEIGHT_REPS, set("a", 0, weight = 100.0, reps = 1), set("b", 1, weight = 90.0, reps = 5)))
    }

    @Test
    fun `a warm-up counts only when the stats setting includes warm-ups`() {
        val warmup = set("w", 0, SetType.WARMUP, 100.0, 5)
        val working = set("s", 1, weight = 80.0, reps = 8)
        assertEquals("s", best(ExerciseType.WEIGHT_REPS, warmup, working))
        assertEquals("w", best(ExerciseType.WEIGHT_REPS, warmup, working, includeWarmups = true))
    }

    @Test
    fun `input order does not matter, orderIndex breaks the tie`() {
        assertEquals("early", best(ExerciseType.WEIGHT_REPS, set("late", 3, weight = 80.0, reps = 8), set("early", 1, weight = 80.0, reps = 8)))
    }

    @Test
    fun `nothing qualifies, so there is no best set`() {
        assertNull(best(ExerciseType.WEIGHT_REPS, set("w", 0, SetType.WARMUP, 40.0, 10)))
        assertNull(best(ExerciseType.WEIGHT_REPS, set("x", 0, weight = 80.0, reps = 8, completed = false)))
        assertNull(best(ExerciseType.WEIGHT_REPS, set("r", 0, reps = 8)))
        assertNull(best(ExerciseType.WEIGHT_REPS))
    }

    // ---- BODYWEIGHT_WEIGHTED: heaviest added weight, more reps on a tie ----

    @Test
    fun `weighted picks the heaviest added weight, then more reps`() {
        val sets = arrayOf(set("a", 0, weight = 10.0, reps = 8), set("b", 1, weight = 10.0, reps = 10), set("c", 2, weight = 5.0, reps = 15))
        assertEquals("b", best(ExerciseType.BODYWEIGHT_WEIGHTED, *sets))
    }

    @Test
    fun `weighted with no added weight picks the most reps`() {
        assertEquals("b", best(ExerciseType.BODYWEIGHT_WEIGHTED, set("a", 0, reps = 10), set("b", 1, reps = 12)))
    }

    // ---- BODYWEIGHT_ASSISTED: most reps, less assistance on a tie ----

    @Test
    fun `assisted picks the most reps even with the most help`() {
        val sets = arrayOf(set("a", 0, weight = 20.0, reps = 8), set("b", 1, weight = 10.0, reps = 8), set("c", 2, weight = 30.0, reps = 10))
        assertEquals("c", best(ExerciseType.BODYWEIGHT_ASSISTED, *sets))
    }

    @Test
    fun `assisted breaks a reps tie toward less assistance, none at all best`() {
        assertEquals("b", best(ExerciseType.BODYWEIGHT_ASSISTED, set("a", 0, weight = 20.0, reps = 8), set("b", 1, weight = 10.0, reps = 8)))
        assertEquals("b", best(ExerciseType.BODYWEIGHT_ASSISTED, set("a", 0, weight = 10.0, reps = 8), set("b", 1, reps = 8)))
    }

    // ---- REPS_ONLY ----

    @Test
    fun `reps only picks the most reps, the earlier on a tie`() {
        assertEquals("b", best(ExerciseType.REPS_ONLY, set("a", 0, reps = 15), set("b", 1, reps = 20), set("c", 2, reps = 20)))
    }

    // ---- DURATION, FLOORS_DURATION, STEPS_DURATION ----

    @Test
    fun `timed types pick the longest time, the earlier on a tie`() {
        val sets = arrayOf(set("a", 0, seconds = 60), set("b", 1, seconds = 45), set("c", 2, seconds = 60))
        assertEquals("a", best(ExerciseType.DURATION, *sets))
        assertEquals("a", best(ExerciseType.FLOORS_DURATION, *sets))
        assertEquals("a", best(ExerciseType.STEPS_DURATION, *sets))
        assertEquals("b", best(ExerciseType.DURATION, set("a", 0, seconds = 30), set("b", 1, seconds = 90)))
    }

    // ---- WEIGHT_DURATION ----

    @Test
    fun `weight and time picks the heaviest weight, then the longer time`() {
        val sets = arrayOf(set("a", 0, weight = 20.0, seconds = 60), set("b", 1, weight = 20.0, seconds = 90), set("c", 2, weight = 15.0, seconds = 120))
        assertEquals("b", best(ExerciseType.WEIGHT_DURATION, *sets))
    }

    // ---- DISTANCE_DURATION ----

    @Test
    fun `distance and time picks the longest distance, then the shorter time`() {
        val sets = arrayOf(
            set("a", 0, meters = 3000.0, seconds = 1110),
            set("b", 1, meters = 3000.0, seconds = 1080),
            set("c", 2, meters = 2000.0, seconds = 600),
        )
        assertEquals("b", best(ExerciseType.DISTANCE_DURATION, *sets))
    }

    @Test
    fun `distance and time with no distance logged picks the longest time`() {
        assertEquals("b", best(ExerciseType.DISTANCE_DURATION, set("a", 0, seconds = 600), set("b", 1, seconds = 900), set("c", 2, meters = 0.0, seconds = 300)))
    }

    // ---- WEIGHT_DISTANCE ----

    @Test
    fun `weight and distance picks the heaviest weight, then the longer distance`() {
        val sets = arrayOf(set("a", 0, weight = 40.0, meters = 50.0), set("b", 1, weight = 40.0, meters = 60.0), set("c", 2, weight = 30.0, meters = 100.0))
        assertEquals("b", best(ExerciseType.WEIGHT_DISTANCE, *sets))
    }
}
