package com.enil.logez.feature.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.Exercise
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P-211 (Owner, 2026-09-30) on the History screens: the workout detail's exercise and circuit
 * round cards, and the History card's EXERCISE / BEST SET line. Expected text is literal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryClarityScreensTest {
    @get:Rule val rule = createComposeRule()

    private val kgUnits = DetailTableUnits(EffortScale.RPE, WeightUnit.KG, DistanceUnit.KM)

    @Test
    fun `an exercise card numbers normal sets only and marks the record set`() {
        val block = block(
            "we-bench", exercise("ex-bench", "Bench Press (Barbell)", ExerciseType.WEIGHT_REPS),
            listOf(
                row("s1", 0, SetType.WARMUP, weightKg = 40.0, reps = 10),
                row("s2", 1, weightKg = 80.0, reps = 8, rpe = 8.0, prType = PrType.BEST_SET_VOLUME),
                row("s3", 2, weightKg = 80.0, reps = 8, rpe = 8.5),
                row("s4", 3, SetType.FAILURE, weightKg = 80.0, reps = 6, rpe = 10.0),
            ),
        )
        rule.setContent { LogEzTheme { ExerciseBlockCard(block, kgUnits, onEffortInfoClick = {}, onExerciseClick = {}) } }

        rule.onNodeWithText("Bench Press (Barbell)").assertExists()
        rule.onNodeWithContentDescription("Warm-up set, weight in kilograms 40, reps 10").assertExists()
        rule.onNodeWithContentDescription("Set 1, weight in kilograms 80, reps 8, RPE 8, Best set volume").assertExists()
        rule.onNodeWithContentDescription("Set 2, weight in kilograms 80, reps 8, RPE 8.5").assertExists()
        rule.onNodeWithContentDescription("Failure set, weight in kilograms 80, reps 6, RPE 10").assertExists()
    }

    @Test
    fun `a superset exercise card draws its table beside the stripe`() {
        // Regression guard: the stripe used to sit in an IntrinsicSize.Min row, and the table's
        // BoxWithConstraints throws when asked for an intrinsic size.
        val block = block(
            "we-row", exercise("ex-row", "Seated Cable Row (Cable)", ExerciseType.WEIGHT_REPS),
            listOf(row("s1", 0, weightKg = 50.0, reps = 12)),
            supersetGroup = 0,
        )
        rule.setContent { LogEzTheme { ExerciseBlockCard(block, kgUnits, onEffortInfoClick = {}, onExerciseClick = {}) } }

        rule.onNodeWithContentDescription("Set 1, weight in kilograms 50, reps 12").assertExists()
    }

    @Test
    fun `a run reads its time and distance in the distance unit`() {
        val block = block(
            "we-run", exercise("ex-run", "Running (Treadmill)", ExerciseType.DISTANCE_DURATION),
            listOf(row("s1", 0, durationSeconds = 1_110, distanceMeters = 3_000.0)),
        )
        rule.setContent { LogEzTheme { ExerciseBlockCard(block, kgUnits, onEffortInfoClick = {}, onExerciseClick = {}) } }

        rule.onNodeWithText("TIME").assertExists()
        rule.onNodeWithText("DISTANCE").assertExists()
        rule.onNodeWithContentDescription("Set 1, time 18m 30s, distance 3km").assertExists()
    }

    @Test
    fun `a round whose exercises share columns has one header, with each name above its row`() {
        val swing = block("we-swing", exercise("ex-swing", "Kettlebell Swing", ExerciseType.WEIGHT_REPS), listOf(row("a2", 1, weightKg = 16.0, reps = 15)))
        val squat = block("we-squat", exercise("ex-squat", "Goblet Squat", ExerciseType.WEIGHT_REPS), listOf(row("b2", 1, weightKg = 20.0, reps = 12)))
        val round = DetailRound(roundNumber = 2, entries = listOf(DetailRoundEntry(swing, swing.sets[0]), DetailRoundEntry(squat, squat.sets[0])))
        rule.setContent { LogEzTheme { DetailRoundCard(round, kgUnits, onEffortInfoClick = {}, onExerciseClick = {}) } }

        rule.onNodeWithText("ROUND 2").assertExists()
        rule.onAllNodesWithText("SET").assertCountEquals(1)
        rule.onNodeWithText("Kettlebell Swing").assertExists()
        rule.onNodeWithText("Goblet Squat").assertExists()
        rule.onNodeWithContentDescription("Set 2, weight in kilograms 16, reps 15").assertExists()
        rule.onNodeWithContentDescription("Set 2, weight in kilograms 20, reps 12").assertExists()
    }

    @Test
    fun `a mixed round gets a header per exercise`() {
        val pushUp = block("we-push", exercise("ex-push", "Push Up", ExerciseType.REPS_ONLY), listOf(row("a1", 0, reps = 15)))
        val plank = block("we-plank", exercise("ex-plank", "Plank", ExerciseType.DURATION), listOf(row("b1", 0, durationSeconds = 60)))
        val round = DetailRound(roundNumber = 1, entries = listOf(DetailRoundEntry(pushUp, pushUp.sets[0]), DetailRoundEntry(plank, plank.sets[0])))
        rule.setContent { LogEzTheme { DetailRoundCard(round, kgUnits, onEffortInfoClick = {}, onExerciseClick = {}) } }

        rule.onAllNodesWithText("SET").assertCountEquals(2)
        rule.onNodeWithText("REPS").assertExists()
        rule.onNodeWithText("TIME").assertExists()
        rule.onNodeWithContentDescription("Set 1, reps 15").assertExists()
        rule.onNodeWithContentDescription("Set 1, time 1m").assertExists()
    }

    @Test
    fun `an exercise skipped in a round still shows its name, with a row of dashes`() {
        val swing = block("we-swing", exercise("ex-swing", "Kettlebell Swing", ExerciseType.WEIGHT_REPS), listOf(row("a1", 0, weightKg = 16.0, reps = 15)))
        val squat = block("we-squat", exercise("ex-squat", "Goblet Squat", ExerciseType.WEIGHT_REPS), emptyList())
        val round = DetailRound(roundNumber = 1, entries = listOf(DetailRoundEntry(swing, swing.sets[0]), DetailRoundEntry(squat, null)))
        rule.setContent { LogEzTheme { DetailRoundCard(round, kgUnits, onEffortInfoClick = {}, onExerciseClick = {}) } }

        rule.onAllNodesWithText("SET").assertCountEquals(1)
        rule.onNodeWithText("Goblet Squat").assertExists()
        rule.onNodeWithContentDescription("Set 1, weight in kilograms 16, reps 15").assertExists()
        rule.onNodeWithContentDescription("Set 1").assertExists()
    }

    @Test
    fun `a History line shows the set count, the name and the best set`() {
        val line = ExerciseSummaryLine("Bench Press (Barbell)", 3, ExerciseType.WEIGHT_REPS, statSet(weightKg = 80.0, reps = 8))
        rule.setContent { LogEzTheme { ExerciseSummaryRow(line, WeightUnit.KG, DistanceUnit.KM) } }

        rule.onNodeWithText("3 × Bench Press (Barbell)").assertExists()
        rule.onNodeWithText("80kg × 8").assertExists()
    }

    @Test
    fun `a pounds user's best set reads in pounds with one decimal`() {
        val line = ExerciseSummaryLine("Bench Press (Barbell)", 3, ExerciseType.WEIGHT_REPS, statSet(weightKg = 80.0, reps = 8))
        rule.setContent { LogEzTheme { ExerciseSummaryRow(line, WeightUnit.LB, DistanceUnit.MILES) } }

        rule.onNodeWithText("176.4lb × 8").assertExists()
    }

    @Test
    fun `a line with no best set, or no exercise, reads a dash`() {
        rule.setContent {
            LogEzTheme {
                androidx.compose.foundation.layout.Column {
                    ExerciseSummaryRow(ExerciseSummaryLine("Bench Press (Barbell)", 0, ExerciseType.WEIGHT_REPS, null), WeightUnit.KG, DistanceUnit.KM)
                    ExerciseSummaryRow(ExerciseSummaryLine("", 1, null, null), WeightUnit.KG, DistanceUnit.KM)
                }
            }
        }

        rule.onAllNodesWithText("—").assertCountEquals(2)
    }

    // --- fixture ---

    private fun exercise(id: String, name: String, type: ExerciseType) = Exercise(
        id = id, name = name, exerciseType = type, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun block(id: String, exercise: Exercise, sets: List<DetailSetRow>, supersetGroup: Int? = null) = DetailExerciseBlock(
        workoutExercise = WorkoutExerciseEntity(
            id = id, workoutId = "w1", exerciseId = exercise.id, orderIndex = 0, supersetGroup = supersetGroup, restTimerSeconds = null, notes = null,
        ),
        exercise = exercise,
        sets = sets,
    )

    private fun row(
        id: String,
        orderIndex: Int,
        setType: SetType = SetType.NORMAL,
        weightKg: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
        rpe: Double? = null,
        prType: PrType? = null,
    ) = DetailSetRow(
        setId = id, orderIndex = orderIndex, setType = setType, weightKg = weightKg, reps = reps,
        durationSeconds = durationSeconds, distanceMeters = distanceMeters, customMetric = null, rpe = rpe, isCompleted = true,
        pr = prType?.let {
            PersonalRecordEntity(id = "pr-$id", exerciseId = "ex", workoutId = "w1", workoutSetId = id, prType = it, value = 640.0, achievedAt = 1L)
        },
    )

    private fun statSet(weightKg: Double, reps: Int) = StatSet(
        setId = "s1", workoutId = "w1", workoutStartedAt = 1L, orderIndex = 0, setType = SetType.NORMAL,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true,
    )
}
