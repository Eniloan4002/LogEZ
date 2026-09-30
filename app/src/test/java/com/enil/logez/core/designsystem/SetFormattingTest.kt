package com.enil.logez.core.designsystem

import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** P-211: how History reads a set (10a distance, 10i time) and the BEST SET text for every type. */
class SetFormattingTest {
    private val reps: (Int) -> String = { if (it == 1) "1 rep" else "$it reps" }

    private fun set(weight: Double? = null, reps: Int? = null, seconds: Int? = null, meters: Double? = null) = StatSet(
        setId = "s",
        workoutId = "w",
        workoutStartedAt = 0L,
        orderIndex = 0,
        setType = SetType.NORMAL,
        weightKg = weight,
        reps = reps,
        durationSeconds = seconds,
        distanceMeters = meters,
        customMetric = null,
        isCompleted = true,
    )

    private fun best(type: ExerciseType, set: StatSet?, weightUnit: WeightUnit = WeightUnit.KG, distanceUnit: DistanceUnit = DistanceUnit.KM) =
        SetFormatting.bestSet(type, set, weightUnit, distanceUnit, reps)

    @Test
    fun `set times read like the Duration stat, down to seconds`() {
        assertEquals("18m 30s", SetFormatting.setDuration(1110))
        assertEquals("1m", SetFormatting.setDuration(60))
        assertEquals("45s", SetFormatting.setDuration(45))
        assertEquals("1h 5m 30s", SetFormatting.setDuration(3930))
        assertEquals("1h", SetFormatting.setDuration(3600))
        assertEquals("1h 30s", SetFormatting.setDuration(3630))
        assertEquals("0s", SetFormatting.setDuration(0))
        assertEquals("0s", SetFormatting.setDuration(-5))
    }

    @Test
    fun `table weights are bare numbers in the user's unit, one decimal for a converted weight`() {
        assertEquals("80", SetFormatting.weightValue(80.0, WeightUnit.KG))
        assertEquals("77.5", SetFormatting.weightValue(77.5, WeightUnit.KG))
        assertEquals("176.4", SetFormatting.weightValue(80.0, WeightUnit.LB))
        assertEquals("352.7", SetFormatting.weightValue(160.0, WeightUnit.LB))
        assertEquals("24.8", SetFormatting.weightValue(11.25, WeightUnit.LB))
        assertEquals("83.9", SetFormatting.weightValue(WeightDisplay.toKg(185.0, WeightUnit.LB), WeightUnit.KG))
        assertEquals("80kg", SetFormatting.weight(80.0, WeightUnit.KG))
        assertEquals("176.4lb", SetFormatting.weight(80.0, WeightUnit.LB))
    }

    /** Code review, 2026-09-30: History showed 11.25 kg as "11.3" while the logger said "11.25". */
    @Test
    fun `a weight logged in the user's unit keeps its two decimals`() {
        assertEquals("11.25", SetFormatting.weightValue(11.25, WeightUnit.KG))
        assertEquals("11.25kg", SetFormatting.weight(11.25, WeightUnit.KG))
        assertEquals("185", SetFormatting.weightValue(WeightDisplay.toKg(185.0, WeightUnit.LB), WeightUnit.LB))
        assertEquals("186.25", SetFormatting.weightValue(WeightDisplay.toKg(186.25, WeightUnit.LB), WeightUnit.LB))
        assertEquals("187.5lb", SetFormatting.weight(WeightDisplay.toKg(187.5, WeightUnit.LB), WeightUnit.LB))
    }

    @Test
    fun `table distances follow the distance unit, not hardcoded metres`() {
        assertEquals("3km", SetFormatting.distance(3000.0, DistanceUnit.KM))
        assertEquals("1.86mi", SetFormatting.distance(3000.0, DistanceUnit.MILES))
        assertEquals("0.05km", SetFormatting.distance(50.0, DistanceUnit.KM))
    }

    @Test
    fun `no best set reads as an em dash`() {
        assertEquals("—", best(ExerciseType.WEIGHT_REPS, null))
    }

    @Test
    fun `weight and reps`() {
        assertEquals("80kg × 8", best(ExerciseType.WEIGHT_REPS, set(weight = 80.0, reps = 8)))
        assertEquals("176.4lb × 8", best(ExerciseType.WEIGHT_REPS, set(weight = 80.0, reps = 8), weightUnit = WeightUnit.LB))
        assertEquals("77.5kg × 7", best(ExerciseType.WEIGHT_REPS, set(weight = 77.5, reps = 7)))
        assertEquals("11.25kg × 10", best(ExerciseType.WEIGHT_REPS, set(weight = 11.25, reps = 10)))
    }

    @Test
    fun `weighted bodyweight shows the added weight with a plus, or reps alone`() {
        assertEquals("+10kg × 8", best(ExerciseType.BODYWEIGHT_WEIGHTED, set(weight = 10.0, reps = 8)))
        assertEquals("12 reps", best(ExerciseType.BODYWEIGHT_WEIGHTED, set(reps = 12)))
        assertEquals("12 reps", best(ExerciseType.BODYWEIGHT_WEIGHTED, set(weight = 0.0, reps = 12)))
    }

    @Test
    fun `assisted bodyweight shows the help with a minus sign, or reps alone`() {
        assertEquals("−20kg × 8", best(ExerciseType.BODYWEIGHT_ASSISTED, set(weight = 20.0, reps = 8)))
        assertEquals("−44.1lb × 8", best(ExerciseType.BODYWEIGHT_ASSISTED, set(weight = 20.0, reps = 8), weightUnit = WeightUnit.LB))
        assertEquals("8 reps", best(ExerciseType.BODYWEIGHT_ASSISTED, set(reps = 8)))
        assertEquals("1 rep", best(ExerciseType.BODYWEIGHT_ASSISTED, set(reps = 1)))
    }

    @Test
    fun `reps only`() {
        assertEquals("15 reps", best(ExerciseType.REPS_ONLY, set(reps = 15)))
    }

    @Test
    fun `timed types show the time`() {
        assertEquals("1m", best(ExerciseType.DURATION, set(seconds = 60)))
        assertEquals("45s", best(ExerciseType.FLOORS_DURATION, set(seconds = 45)))
        assertEquals("2m 5s", best(ExerciseType.STEPS_DURATION, set(seconds = 125)))
    }

    @Test
    fun `weight and time`() {
        assertEquals("20kg × 1m", best(ExerciseType.WEIGHT_DURATION, set(weight = 20.0, seconds = 60)))
    }

    @Test
    fun `distance and time`() {
        assertEquals("3km · 18m 30s", best(ExerciseType.DISTANCE_DURATION, set(meters = 3000.0, seconds = 1110)))
        assertEquals("1.86mi · 18m 30s", best(ExerciseType.DISTANCE_DURATION, set(meters = 3000.0, seconds = 1110), distanceUnit = DistanceUnit.MILES))
        assertEquals("15m", best(ExerciseType.DISTANCE_DURATION, set(seconds = 900)))
        assertEquals("3km", best(ExerciseType.DISTANCE_DURATION, set(meters = 3000.0)))
    }

    @Test
    fun `weight and distance`() {
        assertEquals("40kg × 0.05km", best(ExerciseType.WEIGHT_DISTANCE, set(weight = 40.0, meters = 50.0)))
    }
}
