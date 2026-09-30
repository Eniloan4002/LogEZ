package com.enil.logez.core.designsystem

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.enil.logez.R
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P-211 shared pieces: the headed History set table, the legend and the effort explainer, with
 * the mockups' sample data (README "Sample data"). Expected text is literal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryClarityComposablesTest {
    @get:Rule val rule = createComposeRule()

    private var infoClicks = 0

    private val bench = listOf(
        HistorySetTableRow(SetType.WARMUP, weightKg = 40.0, reps = 10),
        HistorySetTableRow(SetType.NORMAL, weightKg = 80.0, reps = 8, rpe = 8.0, prLabelRes = R.string.pr_type_best_set_volume),
        HistorySetTableRow(SetType.NORMAL, weightKg = 80.0, reps = 8, rpe = 8.5),
        HistorySetTableRow(SetType.FAILURE, weightKg = 80.0, reps = 6, rpe = 10.0),
    )

    private fun table(
        rows: List<HistorySetTableRow>,
        type: ExerciseType = ExerciseType.WEIGHT_REPS,
        scale: EffortScale = EffortScale.RPE,
        weightUnit: WeightUnit = WeightUnit.KG,
    ) {
        rule.setContent {
            LogEzTheme {
                HistorySetTable(
                    exerciseType = type,
                    rows = rows,
                    effortScale = scale,
                    weightUnit = weightUnit,
                    distanceUnit = DistanceUnit.KM,
                    onEffortInfoClick = { infoClicks++ },
                )
            }
        }
    }

    @Test
    fun `the bench table numbers normal sets only and reads each row with its fields`() {
        table(bench)

        rule.onNodeWithText("SET").assertExists()
        rule.onNodeWithText("KG").assertExists()
        rule.onNodeWithText("REPS").assertExists()
        rule.onNodeWithContentDescription("Warm-up set, weight in kilograms 40, reps 10").assertExists()
        rule.onNodeWithContentDescription("Set 1, weight in kilograms 80, reps 8, RPE 8, Best set volume").assertExists()
        rule.onNodeWithContentDescription("Set 2, weight in kilograms 80, reps 8, RPE 8.5").assertExists()
        rule.onNodeWithContentDescription("Failure set, weight in kilograms 80, reps 6, RPE 10").assertExists()
    }

    @Test
    fun `in RIR the same stored values read as RIR`() {
        table(bench, scale = EffortScale.RIR)

        rule.onNodeWithText("RIR").assertExists()
        rule.onNodeWithContentDescription("Set 1, weight in kilograms 80, reps 8, RIR 2, Best set volume").assertExists()
        rule.onNodeWithContentDescription("Set 2, weight in kilograms 80, reps 8, RIR 1–2").assertExists()
        rule.onNodeWithContentDescription("Failure set, weight in kilograms 80, reps 6, RIR 0").assertExists()
    }

    @Test
    fun `the effort header opens the explainer`() {
        table(bench)

        rule.onNodeWithText("RPE").performClick()

        assertEquals(1, infoClicks)
    }

    @Test
    fun `a table with no effort value has no effort column`() {
        table(
            listOf(
                HistorySetTableRow(SetType.NORMAL, weightKg = 25.0, reps = 12),
                HistorySetTableRow(SetType.NORMAL, weightKg = 25.0, reps = 10),
                HistorySetTableRow(SetType.DROPSET, weightKg = 15.0, reps = 10),
            ),
        )

        rule.onAllNodesWithText("RPE").assertCountEquals(0)
        rule.onNodeWithContentDescription("Dropset, weight in kilograms 15, reps 10").assertExists()
    }

    @Test
    fun `a run shows TIME and DISTANCE in the distance unit`() {
        table(listOf(HistorySetTableRow(SetType.NORMAL, durationSeconds = 1110, distanceMeters = 3000.0)), type = ExerciseType.DISTANCE_DURATION)

        rule.onNodeWithText("TIME").assertExists()
        rule.onNodeWithText("DISTANCE").assertExists()
        rule.onNodeWithContentDescription("Set 1, time 18m 30s, distance 3km").assertExists()
    }

    @Test
    fun `a pounds user sees LBS and one-decimal weights`() {
        table(listOf(HistorySetTableRow(SetType.NORMAL, weightKg = 80.0, reps = 8)), weightUnit = WeightUnit.LB)

        rule.onNodeWithText("LBS").assertExists()
        rule.onNodeWithContentDescription("Set 1, weight in pounds 176.4, reps 8").assertExists()
    }

    /** Device QA, 2026-09-30: an assisted set's 40 sat under a plain LBS, as if 40 lb were lifted. */
    @Test
    fun `an assisted exercise heads its weight column with the routine builder's minus`() {
        table(
            listOf(HistorySetTableRow(SetType.NORMAL, weightKg = WeightDisplay.toKg(40.0, WeightUnit.LB), reps = 8)),
            type = ExerciseType.BODYWEIGHT_ASSISTED,
            weightUnit = WeightUnit.LB,
        )

        rule.onNodeWithText("−LBS").assertExists()
        rule.onAllNodesWithText("LBS").assertCountEquals(0)
        rule.onNodeWithContentDescription("Set 1, assistance in pounds 40, reps 8").assertExists()
    }

    @Test
    fun `a weighted bodyweight exercise heads its weight column with a plus`() {
        table(listOf(HistorySetTableRow(SetType.NORMAL, weightKg = 10.0, reps = 8)), type = ExerciseType.BODYWEIGHT_WEIGHTED)

        rule.onNodeWithText("+KG").assertExists()
        rule.onNodeWithContentDescription("Set 1, added weight in kilograms 10, reps 8").assertExists()
    }

    @Test
    fun `a lift and an assisted exercise don't share a header`() {
        assertEquals(listOf(HistoryColumn.WEIGHT, HistoryColumn.REPS), historyColumnsFor(ExerciseType.WEIGHT_REPS))
        assertEquals(listOf(HistoryColumn.ADDED_WEIGHT, HistoryColumn.REPS), historyColumnsFor(ExerciseType.BODYWEIGHT_WEIGHTED))
        assertEquals(listOf(HistoryColumn.ASSISTANCE, HistoryColumn.REPS), historyColumnsFor(ExerciseType.BODYWEIGHT_ASSISTED))
    }

    @Test
    fun `the legend lists only the badge types present, and its effort line opens the explainer`() {
        rule.setContent {
            LogEzTheme {
                SetLegend(
                    setTypes = setOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE),
                    showPersonalRecord = true,
                    effortScale = EffortScale.RPE,
                    onEffortInfoClick = { infoClicks++ },
                )
            }
        }

        rule.onNodeWithText("Warm-up").assertExists()
        rule.onNodeWithText("Failure").assertExists()
        rule.onAllNodesWithText("Dropset").assertCountEquals(0)
        rule.onNodeWithText("Personal record").assertExists()
        rule.onNodeWithText("How hard it felt · 10 = no reps left").performClick()
        assertEquals(1, infoClicks)
    }

    @Test
    fun `the RIR explainer maps the scales and points to the other one`() {
        var gotIt = 0
        rule.setContent { LogEzTheme { EffortExplainerContent(scale = EffortScale.RIR, onGotIt = { gotIt++ }) } }

        rule.onNodeWithText("What is RIR?").assertExists()
        rule.onNodeWithText("4+").assertExists()
        rule.onNodeWithText("Prefer RPE? Switch in Settings › Effort tracking.").assertExists()
        rule.onNodeWithText("Got it").performClick()
        assertEquals(1, gotIt)
    }
}
