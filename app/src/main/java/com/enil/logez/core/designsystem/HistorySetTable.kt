package com.enil.logez.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.RpeScale
import com.enil.logez.core.domain.model.SetDisplayLabel
import com.enil.logez.core.domain.model.SetNumbering
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit

/**
 * One logged set as a History table draws it. Both History screens map their own rows onto this:
 * the workout detail's `DetailSetRow` and Exercise history's `ExerciseHistoryEntry`.
 *
 * [isCompleted] false draws every value cell as "—" (the detail's old rule for a set that never
 * finished). [prLabelRes] is the record this set holds (`PrType.labelRes()`); null draws no trophy.
 */
data class HistorySetTableRow(
    val setType: SetType,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val customMetric: Double? = null,
    val rpe: Double? = null,
    val isCompleted: Boolean = true,
    @StringRes val prLabelRes: Int? = null,
)

/**
 * A value column of a History table. The effort column is separate: it depends on the values, not
 * the type.
 *
 * [ADDED_WEIGHT] and [ASSISTANCE] are the weight column of a weighted or assisted bodyweight
 * exercise, headed "+KG" / "−KG" as in the routine builder. Under a plain "KG" an assisted set's
 * 40 read as 40 kg lifted, while the History card beside it said "−40lb × 8" (P-211 device QA,
 * 2026-09-30).
 */
enum class HistoryColumn { COUNT, WEIGHT, ADDED_WEIGHT, ASSISTANCE, REPS, TIME, DISTANCE }

/**
 * The logger's value columns for [type], in the logger's order (`SetTable` in
 * WorkoutExerciseCard.kt): COUNT for floors/steps, then KG, REPS, TIME, DISTANCE as the type logs
 * them. SET | TIME | DISTANCE for a run, COUNT | TIME for floors. A missing exercise (null) has none.
 *
 * Two exercises with equal lists can share one header, which is how a circuit round decides
 * between one header under "ROUND N" and one per exercise. Bench press and an assisted pull-up
 * don't: one is headed KG and the other −KG.
 */
fun historyColumnsFor(type: ExerciseType?): List<HistoryColumn> = when (type) {
    null -> emptyList()
    ExerciseType.WEIGHT_REPS -> listOf(HistoryColumn.WEIGHT, HistoryColumn.REPS)
    ExerciseType.BODYWEIGHT_WEIGHTED -> listOf(HistoryColumn.ADDED_WEIGHT, HistoryColumn.REPS)
    ExerciseType.BODYWEIGHT_ASSISTED -> listOf(HistoryColumn.ASSISTANCE, HistoryColumn.REPS)
    ExerciseType.REPS_ONLY -> listOf(HistoryColumn.REPS)
    ExerciseType.DURATION -> listOf(HistoryColumn.TIME)
    ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> listOf(HistoryColumn.COUNT, HistoryColumn.TIME)
    ExerciseType.WEIGHT_DURATION -> listOf(HistoryColumn.WEIGHT, HistoryColumn.TIME)
    ExerciseType.DISTANCE_DURATION -> listOf(HistoryColumn.TIME, HistoryColumn.DISTANCE)
    ExerciseType.WEIGHT_DISTANCE -> listOf(HistoryColumn.WEIGHT, HistoryColumn.DISTANCE)
}

/**
 * P-211 (Owner, 2026-09-30) §2/§3: the headed set table that replaces the "Set 2: 80kg · 8 reps ·
 * @8.0" lines on the workout detail and in Exercise history.
 *
 * - Header: SET, then [columns] in the logger's words (KG/LBS, REPS, TIME, DISTANCE, COUNT; the
 *   builder's +KG/−KG for weighted and assisted bodyweight), then
 *   "RPE ⓘ" / "RIR ⓘ" ([EffortHeaderLabel], a button that calls [onEffortInfoClick]).
 * - The effort column appears only when [showEffort]: by default, when some row in THIS table has
 *   a value. An empty cell in a table that has the column reads "—".
 * - SET shows [labels], by default [SetNumbering] (decision 9: W, 1, 2, F). A circuit round
 *   passes its own round-based labels.
 * - A trailing trophy for a row with [HistorySetTableRow.prLabelRes].
 * - Values in [valueColor] (the detail: onSurface; Exercise history keeps its primary).
 *
 * Value columns are 80dp at the default font size, so KG and REPS line up card to card with or
 * without the effort column. At larger sizes a column widens to its widest text, one line each,
 * and when they no longer fit the row they share it in proportion to what each needs, like the
 * logger's KG and REPS (README "Large text, lb and long names").
 *
 * [aboveRow] draws something above row i, inside the table (a circuit round's exercise name).
 * The caller spaces the table from what is above it (the mockups use [Spacing.sm]).
 */
@Composable
fun HistorySetTable(
    exerciseType: ExerciseType?,
    rows: List<HistorySetTableRow>,
    effortScale: EffortScale,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
    onEffortInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    columns: List<HistoryColumn> = historyColumnsFor(exerciseType),
    labels: List<SetDisplayLabel> = SetNumbering.labels(rows.map { it.setType }),
    showEffort: Boolean = rows.any { it.rpe != null },
    showHeader: Boolean = true,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    aboveRow: (@Composable (index: Int) -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    val headerLabels = columns.map { stringResource(it.headerRes(weightUnit)) }
    val setHeader = stringResource(R.string.routine_builder_col_set)
    val effortLabel = stringResource(effortScale.labelRes())
    val cells: List<List<String?>> = rows.map { row -> columns.map { row.cell(it, weightUnit, distanceUnit) } }
    val effortCells: List<String?> = rows.map { row -> row.rpe?.takeIf { row.isCompleted }?.let { RpeScale.format(it, effortScale) } }
    val hasPr = rows.any { it.prLabelRes != null }

    val headerStyle = MaterialTheme.typography.labelSmall
    val valueStyle = LogEzMono.dataMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widths = remember(headerLabels, setHeader, effortLabel, cells, effortCells, showEffort, density, headerStyle, valueStyle) {
        fun textWidth(text: String, style: TextStyle): Dp =
            with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }
        fun columnWidth(header: Dp, values: List<String?>): Dp =
            maxOf(header, values.maxOfOrNull { textWidth(it ?: SetFormatting.NONE, valueStyle) } ?: 0.dp) + VALUE_CELL_GUTTER
        ColumnWidths(
            set = maxOf(BADGE_SIZE, textWidth(setHeader, headerStyle)),
            values = columns.indices.map { i -> columnWidth(textWidth(headerLabels[i], headerStyle), cells.map { it[i] }) },
            effort = if (showEffort) columnWidth(textWidth(effortLabel, headerStyle) + INFO_ICON_WITH_GAP, effortCells) else null,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val trailing = if (hasPr) PR_ICON_SIZE + Spacing.xxs else 0.dp
        val natural = widths.values.map { maxOf(it, VALUE_COLUMN_MIN) } + listOfNotNull(widths.effort?.let { maxOf(it, VALUE_COLUMN_MIN) })
        // Share the row by need only when the natural widths overflow it (large font sizes).
        val shared = widths.set + natural.fold(0.dp) { a, b -> a + b } + trailing > maxWidth
        val needs = widths.values + listOfNotNull(widths.effort)
        val layout = TableLayout(setWidth = widths.set, natural = natural, needs = needs, shared = shared, hasPr = hasPr)

        Column {
            if (showHeader) {
                TableRow(layout, prSlot = {}) { cell ->
                    Text(setHeader, style = headerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center, modifier = cell(-1))
                    headerLabels.forEachIndexed { i, label ->
                        Text(
                            label,
                            style = headerStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = cell(i),
                        )
                    }
                    if (showEffort) {
                        Box(modifier = cell(columns.size), contentAlignment = Alignment.Center) {
                            EffortHeaderLabel(scale = effortScale, onInfoClick = onEffortInfoClick, dropIconAboveFontScale = null)
                        }
                    }
                }
            }
            rows.forEachIndexed { index, row ->
                val topGap = when {
                    index == 0 && showHeader -> Spacing.xs
                    index == 0 -> 0.dp
                    else -> Spacing.sm
                }
                if (aboveRow != null) {
                    Box(modifier = Modifier.padding(top = topGap)) { aboveRow(index) }
                }
                val label = labels.getOrElse(index) { SetDisplayLabel(row.setType, null) }
                val description = rowDescription(label, row, columns, cells[index], effortCells[index], showEffort, effortScale, weightUnit)
                TableRow(
                    layout = layout,
                    modifier = Modifier
                        .padding(top = if (aboveRow != null) Spacing.xxs else topGap)
                        .heightIn(min = BADGE_SIZE)
                        .clearAndSetSemantics { contentDescription = description },
                    prSlot = {
                        if (row.prLabelRes != null) {
                            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Gold500, modifier = Modifier.size(PR_ICON_SIZE))
                        }
                    },
                ) { cell ->
                    Box(modifier = cell(-1), contentAlignment = Alignment.Center) { HistorySetBadge(label) }
                    cells[index].forEachIndexed { i, value -> ValueText(value, valueStyle, valueColor, cell(i)) }
                    if (showEffort) ValueText(effortCells[index], valueStyle, valueColor, cell(columns.size))
                }
            }
        }
    }
}

/** Per-column widths from the measured text: SET, each value column, the effort column (null when hidden). */
private data class ColumnWidths(val set: Dp, val values: List<Dp>, val effort: Dp?)

/**
 * How one table lays its cells out: fixed [natural] widths with the trophy pushed to the end, or,
 * when [shared], weights in proportion to [needs] so every column keeps a share of a row too
 * narrow for all of them.
 */
private class TableLayout(val setWidth: Dp, val natural: List<Dp>, val needs: List<Dp>, val shared: Boolean, val hasPr: Boolean)

/**
 * One header or set row. [content] gets a modifier factory per cell: -1 is SET, 0 until the
 * value columns' count is a value column, the last index the effort column.
 */
@Composable
private fun TableRow(
    layout: TableLayout,
    modifier: Modifier = Modifier,
    prSlot: @Composable () -> Unit,
    content: @Composable RowScope.(cell: (Int) -> Modifier) -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val cell: (Int) -> Modifier = { i ->
            when {
                i < 0 -> Modifier.width(layout.setWidth)
                layout.shared -> Modifier.weight(layout.needs[i].value.coerceAtLeast(1f))
                else -> Modifier.width(layout.natural[i])
            }
        }
        content(cell)
        if (layout.hasPr) {
            if (!layout.shared) Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.padding(start = Spacing.xxs).size(PR_ICON_SIZE), contentAlignment = Alignment.Center) { prSlot() }
        }
    }
}

@Composable
private fun ValueText(value: String?, style: TextStyle, color: Color, modifier: Modifier) {
    Text(
        value ?: SetFormatting.NONE,
        style = style,
        color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}

/**
 * What a screen reader says for one row, e.g. "Set 1, weight in kilograms 80, reps 8, RPE 8,
 * Best set volume": the set, each logged value with its field's spoken name, then the record.
 */
@Composable
private fun rowDescription(
    label: SetDisplayLabel,
    row: HistorySetTableRow,
    columns: List<HistoryColumn>,
    values: List<String?>,
    effort: String?,
    showEffort: Boolean,
    effortScale: EffortScale,
    weightUnit: WeightUnit,
): String {
    val parts = mutableListOf(setLabelA11y(label))
    columns.forEachIndexed { i, column ->
        values[i]?.let { parts += stringResource(R.string.history_set_cell_a11y, stringResource(column.spokenRes(weightUnit)), it) }
    }
    if (showEffort && effort != null) parts += stringResource(R.string.effort_value_line, stringResource(effortScale.labelRes()), effort)
    row.prLabelRes?.let { parts += stringResource(it) }
    return parts.joinToString(", ")
}

private fun HistorySetTableRow.cell(column: HistoryColumn, weightUnit: WeightUnit, distanceUnit: DistanceUnit): String? {
    if (!isCompleted) return null
    return when (column) {
        HistoryColumn.COUNT -> customMetric?.let { SetFormatting.customMetric(it) }
        HistoryColumn.WEIGHT, HistoryColumn.ADDED_WEIGHT, HistoryColumn.ASSISTANCE -> weightKg?.let { SetFormatting.weightValue(it, weightUnit) }
        HistoryColumn.REPS -> reps?.toString()
        HistoryColumn.TIME -> durationSeconds?.let { SetFormatting.setDuration(it) }
        HistoryColumn.DISTANCE -> distanceMeters?.let { SetFormatting.distance(it, distanceUnit) }
    }
}

/**
 * The logger's header word for a column (KG flips to LBS for a pounds user, as weightHeaderRes
 * does), and the routine builder's +KG/−KG (`weightHeaderLabel` in RoutineExerciseCard.kt).
 */
@StringRes
private fun HistoryColumn.headerRes(weightUnit: WeightUnit): Int = when (this) {
    HistoryColumn.COUNT -> R.string.workout_col_custom_metric
    HistoryColumn.WEIGHT -> if (weightUnit == WeightUnit.LB) R.string.routine_builder_col_weight_lbs else R.string.routine_builder_col_weight
    HistoryColumn.ADDED_WEIGHT ->
        if (weightUnit == WeightUnit.LB) R.string.routine_builder_col_weight_added_lbs else R.string.routine_builder_col_weight_added
    HistoryColumn.ASSISTANCE ->
        if (weightUnit == WeightUnit.LB) R.string.routine_builder_col_weight_assisted_lbs else R.string.routine_builder_col_weight_assisted
    HistoryColumn.REPS -> R.string.routine_builder_col_reps
    HistoryColumn.TIME -> R.string.routine_builder_col_time
    HistoryColumn.DISTANCE -> R.string.routine_builder_col_distance
}

/** The column's spoken name: the logger's field labels where it has them. */
@StringRes
private fun HistoryColumn.spokenRes(weightUnit: WeightUnit): Int = when (this) {
    HistoryColumn.COUNT -> R.string.workout_field_value
    HistoryColumn.WEIGHT -> if (weightUnit == WeightUnit.LB) R.string.workout_field_weight_lb else R.string.workout_field_weight_kg
    HistoryColumn.ADDED_WEIGHT -> if (weightUnit == WeightUnit.LB) R.string.history_field_added_weight_lb else R.string.history_field_added_weight_kg
    HistoryColumn.ASSISTANCE -> if (weightUnit == WeightUnit.LB) R.string.history_field_assistance_lb else R.string.history_field_assistance_kg
    HistoryColumn.REPS -> R.string.workout_field_reps
    HistoryColumn.TIME -> R.string.history_field_time
    HistoryColumn.DISTANCE -> R.string.history_field_distance
}

/** A value column's width at the default font size (README §2), and its floor at every size. */
private val VALUE_COLUMN_MIN = 80.dp

/** Room either side of a column's widest text, so neighbours never touch when a column widens. */
private val VALUE_CELL_GUTTER = Spacing.xs

private val BADGE_SIZE = 28.dp
private val PR_ICON_SIZE = 18.dp

/** The effort header's 2dp gap and 12dp ⓘ ([EffortHeaderLabel]). */
private val INFO_ICON_WITH_GAP = 14.dp
