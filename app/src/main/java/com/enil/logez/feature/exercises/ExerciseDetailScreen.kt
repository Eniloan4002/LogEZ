package com.enil.logez.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.calc.ChartMetric
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.feature.workout.finish.labelRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class DetailTab { SUMMARY, HISTORY, HOW_TO }

private data class HistorySession(val workoutId: String, val workoutTitle: String, val sets: List<ExerciseHistoryEntry>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    onDuplicated: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(DetailTab.SUMMARY.ordinal) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Re-resolves today/zone/settings and re-reads history (the CalendarViewModel convention) —
    // an edit made from a workout opened off this screen's History tab lands here on return.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.exercise?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val exercise = uiState.exercise
                    if (exercise != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = { menuExpanded = false; onEdit(exercise.id) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { viewModel.duplicate()?.let(onDuplicated) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menuExpanded = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == DetailTab.SUMMARY.ordinal,
                    onClick = { selectedTab = DetailTab.SUMMARY.ordinal },
                    text = { Text(stringResource(R.string.exercise_detail_tab_summary)) },
                )
                Tab(
                    selected = selectedTab == DetailTab.HISTORY.ordinal,
                    onClick = { selectedTab = DetailTab.HISTORY.ordinal },
                    text = { Text(stringResource(R.string.exercise_detail_tab_history)) },
                )
                Tab(
                    selected = selectedTab == DetailTab.HOW_TO.ordinal,
                    onClick = { selectedTab = DetailTab.HOW_TO.ordinal },
                    text = { Text(stringResource(R.string.exercise_detail_tab_how_to)) },
                )
            }

            when (DetailTab.entries[selectedTab]) {
                DetailTab.SUMMARY -> SummaryTab(
                    isLoading = uiState.isLoading,
                    summary = uiState.summary,
                    onRangeSelected = viewModel::selectRange,
                    onMetricSelected = viewModel::selectMetric,
                )
                DetailTab.HISTORY -> HistoryTab(entries = uiState.history)
                DetailTab.HOW_TO -> HowToTab(instructions = uiState.exercise?.instructions.orEmpty())
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.exercise_delete_confirm_title)) },
            text = { Text(stringResource(R.string.exercise_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(onDeleted) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * §5.2 Summary tab (M6a): range selector, metric graph with a metric switcher below it, the
 * Personal Records list from the `personal_records` cache, and the collapsible Set Records table
 * ([SetRecordCalculator][com.enil.logez.core.domain.calc.SetRecordCalculator] gates it to the
 * weight×reps types itself — an empty list simply renders no section). Empty state per the plan:
 * never-logged replaces the graph area and hides PRs/Set Records entirely; an empty *range* keeps
 * the selector active so the user can widen it.
 */
@Composable
private fun SummaryTab(
    isLoading: Boolean,
    summary: SummaryUiState,
    onRangeSelected: (ChartRange) -> Unit,
    onMetricSelected: (ChartMetric) -> Unit,
) {
    // Summary is the landing tab, so the pre-load default state would otherwise flash the
    // never-logged claim on every open of an exercise that HAS history. Render nothing until the
    // first load lands — the load is a few Room point-queries, so this is a single-frame blank.
    if (isLoading) return
    if (!summary.hasAnyLoggedSets) {
        EmptyState(
            icon = Icons.Filled.BarChart,
            title = stringResource(R.string.exercise_detail_summary_empty_title),
            subtitle = stringResource(R.string.exercise_detail_summary_empty_subtitle),
        )
        return
    }

    // Selection resets whenever the plotted series changes — a stale index into a new list would
    // read out the wrong point.
    var selectedPointIndex by remember(summary.points) { mutableStateOf<Int?>(summary.points.lastIndex.takeIf { it >= 0 }) }
    var setRecordsExpanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    fun dateOf(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.md)) {
        item(key = "range") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ChartRange.entries.forEach { range ->
                    FilterChip(
                        selected = summary.selectedRange == range,
                        onClick = { onRangeSelected(range) },
                        label = { Text(stringResource(range.labelRes())) },
                    )
                }
            }
        }

        item(key = "chart") {
            Column(modifier = Modifier.padding(top = Spacing.sm)) {
                val selected = selectedPointIndex?.let { summary.points.getOrNull(it) }
                if (selected != null && summary.selectedMetric != null) {
                    Text(
                        SummaryFormatters.formatMetricValue(summary.selectedMetric, selected.value, summary.weightUnit, summary.distanceUnit),
                        style = LogEzMono.dataLarge,
                    )
                    Text(
                        dateOf(selected.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.points.isEmpty()) {
                    Text(
                        stringResource(R.string.exercise_detail_summary_no_data_in_range),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                } else if (summary.selectedMetric != null) {
                    LineChart(
                        points = summary.points.map { LineChartPoint(it.startedAt, it.value) },
                        yLabel = { SummaryFormatters.axisLabel(summary.selectedMetric, it, summary.weightUnit, summary.distanceUnit) },
                        xLabel = { dateOf(it) },
                        selectedIndex = selectedPointIndex,
                        onPointTap = { selectedPointIndex = it },
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }

        item(key = "metrics") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Spacing.sm),
            ) {
                summary.metrics.forEach { metric ->
                    FilterChip(
                        selected = summary.selectedMetric == metric,
                        onClick = { onMetricSelected(metric) },
                        label = { Text(stringResource(metric.labelRes())) },
                    )
                }
            }
        }

        if (summary.personalRecords.isNotEmpty()) {
            item(key = "prs-header") {
                Text(
                    stringResource(R.string.exercise_detail_summary_prs_header),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                )
            }
            items(items = summary.personalRecords, key = { "pr-${it.prType}" }) { pr ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(pr.prType.labelRes()), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            dateOf(pr.achievedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        SummaryFormatters.formatPrValue(pr.prType, pr.value, summary.weightUnit, summary.distanceUnit),
                        style = LogEzMono.dataMedium,
                    )
                }
            }
        }

        if (summary.setRecords.isNotEmpty()) {
            item(key = "set-records-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setRecordsExpanded = !setRecordsExpanded }
                        .padding(top = Spacing.lg, bottom = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.exercise_detail_summary_set_records_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (setRecordsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (setRecordsExpanded) {
                items(items = summary.setRecords, key = { "sr-${it.reps}" }) { record ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                        Text(
                            pluralStringResource(R.plurals.exercise_detail_summary_set_records_reps, record.reps, record.reps),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            SummaryFormatters.formatPrValue(PrType.HEAVIEST_WEIGHT, record.weightKg, summary.weightUnit, summary.distanceUnit),
                            style = LogEzMono.dataMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.LAST_30_DAYS -> R.string.chart_range_30d
    ChartRange.LAST_3_MONTHS -> R.string.chart_range_3m
    ChartRange.LAST_YEAR -> R.string.chart_range_1y
    ChartRange.ALL_TIME -> R.string.chart_range_all
}

private fun ChartMetric.labelRes(): Int = when (this) {
    ChartMetric.HEAVIEST_WEIGHT -> R.string.chart_metric_heaviest_weight
    ChartMetric.ONE_REP_MAX -> R.string.chart_metric_one_rep_max
    ChartMetric.BEST_SET_VOLUME -> R.string.chart_metric_best_set_volume
    ChartMetric.SESSION_VOLUME -> R.string.chart_metric_session_volume
    ChartMetric.TOTAL_REPS -> R.string.chart_metric_total_reps
    ChartMetric.MOST_REPS_SET -> R.string.chart_metric_most_reps_set
    ChartMetric.SESSION_REPS -> R.string.chart_metric_session_reps
    ChartMetric.BEST_TIME -> R.string.chart_metric_best_time
    ChartMetric.LONGEST_TIME -> R.string.chart_metric_longest_time
    ChartMetric.LONGEST_DISTANCE -> R.string.chart_metric_longest_distance
    ChartMetric.BEST_PACE -> R.string.chart_metric_best_pace
}

@Composable
private fun HistoryTab(entries: List<ExerciseHistoryEntry>) {
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.BarChart,
            title = stringResource(R.string.exercise_detail_history_empty_title),
            subtitle = stringResource(R.string.exercise_detail_history_empty_subtitle),
        )
        return
    }
    val sessions = entries
        .groupBy { it.workoutId }
        .map { (workoutId, sets) -> HistorySession(workoutId, sets.first().workoutTitle, sets.sortedBy { it.setOrderIndex }) }
        .sortedByDescending { it.sets.first().workoutStartedAt }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        items(items = sessions, key = { it.workoutId }) { session ->
            // Explicitly full-width, like every other card in the app: v4.0's top-edge accent spans
            // the card, so a content-hugging card would end the accent in a seam mid-row.
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(session.workoutTitle, style = MaterialTheme.typography.titleSmall)
                    session.sets.forEach { set ->
                        Text(
                            formatHistorySet(set),
                            style = LogEzMono.dataMedium,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}

private fun formatHistorySet(entry: ExerciseHistoryEntry): String {
    val parts = mutableListOf<String>()
    entry.weightKg?.let { parts.add("${it}kg") }
    entry.reps?.let { parts.add("${it} reps") }
    entry.durationSeconds?.let { parts.add("${it}s") }
    entry.distanceMeters?.let { parts.add("${it}m") }
    entry.rpe?.let { parts.add("@$it") }
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

@Composable
private fun HowToTab(instructions: String) {
    if (instructions.isBlank()) {
        EmptyState(
            icon = Icons.Filled.BarChart,
            title = stringResource(R.string.exercise_detail_how_to_empty_title),
            subtitle = stringResource(R.string.exercise_detail_how_to_empty_subtitle),
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        instructions.split("\n").filter { it.isNotBlank() }.forEachIndexed { index, step ->
            Text(
                "${index + 1}. $step",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
}
