package com.enil.logez.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.designsystem.BarChart
import com.enil.logez.core.designsystem.BarChartEntry
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.StatBucket
import com.enil.logez.core.domain.model.MuscleGroup
import androidx.compose.material.icons.filled.BarChart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PHASE2_PLAN.md §5.2 "Analytics dashboard" — scrollable column of stat cards. Every §8-owned
 * number comes from the calc engines via [AnalyticsViewModel]; empty periods say so honestly
 * with the range selector still active (never sample data, never zeroed charts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    onMuscleClick: (MuscleGroup) -> Unit,
    onMonthlyReportClick: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The screen's single load trigger (no init load in the ViewModel): first entry, back from a
    // child screen, tab return, and foregrounding each refresh exactly once — workouts logged
    // meanwhile and midnight/zone changes land fresh (the frozen-field convention).
    RefreshOnResume(viewModel::refresh)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) return@Scaffold
        if (!uiState.hasAnyWorkouts) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.BarChart,
                    title = stringResource(R.string.profile_empty_title),
                    subtitle = stringResource(R.string.profile_empty_subtitle),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "training") { TrainingCard(uiState, viewModel) }
            item(key = "distribution") { DistributionCard(uiState, viewModel, onMuscleClick) }
            item(key = "body") { BodyCard(uiState, viewModel) }
            item(key = "set_counts") { SetCountCard(uiState, viewModel, onMuscleClick) }
            item(key = "main_exercises") { MainExercisesCard(uiState, viewModel, onExerciseClick) }
            item(key = "monthly_report") { MonthlyReportEntryCard(onMonthlyReportClick) }
        }
    }
}

// --- shared bits ---

@Composable
internal fun RangeChips(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ChartRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(rangeLabel(range)) },
            )
        }
    }
}

@Composable
internal fun rangeLabel(range: ChartRange): String = when (range) {
    ChartRange.LAST_30_DAYS -> stringResource(R.string.chart_range_30d)
    ChartRange.LAST_3_MONTHS -> stringResource(R.string.chart_range_3m)
    ChartRange.LAST_YEAR -> stringResource(R.string.chart_range_1y)
    ChartRange.ALL_TIME -> stringResource(R.string.chart_range_all)
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

private val weekLabelFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
internal fun weekLabel(week: LocalDate): String = weekLabelFormatter.format(week)

private val monthAxisFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val monthReadoutFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

/** Axis label for one set-count bucket — a MONTH bucket is a month, never a "1 Jun" day label. */
internal fun bucketAxisLabel(bucketStart: LocalDate, bucket: StatBucket): String = when (bucket) {
    StatBucket.WEEK -> weekLabel(bucketStart)
    StatBucket.MONTH -> monthAxisFormatter.format(bucketStart)
}

/** Readout label: a WEEK bucket is a range ("Week of 10 Aug"), never a bare day date. */
@Composable
internal fun bucketReadoutLabel(bucketStart: LocalDate, bucket: StatBucket): String = when (bucket) {
    StatBucket.WEEK -> stringResource(R.string.analytics_body_week_prefix, weekLabel(bucketStart))
    StatBucket.MONTH -> monthReadoutFormatter.format(bucketStart)
}

/** A share row: name, proportional bar, count + percent — used by both distribution surfaces. */
@Composable
internal fun ShareRow(share: MuscleStatsCalculator.GroupShare, maxCount: Int, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            muscleGroupLabel(share.group),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
        )
        Box(
            modifier = Modifier.weight(1f).height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = if (maxCount == 0) 0f else share.setCount.toFloat() / maxCount)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            "${share.setCount} · ${share.sharePercent}%",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

// --- card 1: training charts ---

@Composable
private fun TrainingCard(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    val card = uiState.training
    Card {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_training_title))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(TrainingMetric.entries) { metric ->
                    FilterChip(
                        selected = metric == card.metric,
                        onClick = { viewModel.selectTrainingMetric(metric) },
                        label = { Text(trainingMetricLabel(metric)) },
                    )
                }
            }
            RangeChips(card.range, viewModel::selectTrainingRange)
            if (card.bars.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else {
                card.selectedBar?.let { i ->
                    val bar = card.bars[i]
                    Text(
                        stringResource(R.string.analytics_body_week_prefix, weekLabel(bar.weekStart)) +
                            " — " + AnalyticsFormatters.metricValue(card.metric, bar.value, uiState.weightUnit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                BarChart(
                    entries = card.bars.map { BarChartEntry(weekLabel(it.weekStart), it.value) },
                    yLabel = { AnalyticsFormatters.axisLabel(card.metric, it, uiState.weightUnit) },
                    selectedIndex = card.selectedBar,
                    onBarTap = { viewModel.selectTrainingBar(it) },
                )
            }
        }
    }
}

@Composable
internal fun trainingMetricLabel(metric: TrainingMetric): String = when (metric) {
    TrainingMetric.VOLUME -> stringResource(R.string.analytics_metric_volume)
    TrainingMetric.REPS -> stringResource(R.string.analytics_metric_reps)
    TrainingMetric.DURATION -> stringResource(R.string.analytics_metric_duration)
    TrainingMetric.FREQUENCY -> stringResource(R.string.analytics_metric_frequency)
}

// --- card 2: muscle distribution (chart) ---

@Composable
private fun DistributionCard(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onMuscleClick: (MuscleGroup) -> Unit,
) {
    val card = uiState.distribution
    Card {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_distribution_title))
            RangeChips(card.range, viewModel::selectDistributionRange)
            PeriodTiles(card.totals, uiState)
            if (card.current.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else {
                val maxCount = card.current.maxOf { it.setCount }
                card.current.forEach { share ->
                    ShareRow(share, maxCount, onClick = { onMuscleClick(share.group) })
                }
                card.previous?.let { previous ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                    Text(
                        stringResource(R.string.analytics_distribution_previous),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    previous.forEach { share ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                            Text(
                                muscleGroupLabel(share.group),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(110.dp),
                            )
                            Text(
                                "${share.setCount} · ${share.sharePercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodTiles(totals: com.enil.logez.core.domain.calc.DashboardAggregator.PeriodTotals, uiState: AnalyticsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
        Tile(stringResource(R.string.analytics_tile_workouts), totals.workouts.toString(), Modifier.weight(1f))
        Tile(stringResource(R.string.analytics_tile_duration), AnalyticsFormatters.durationHoursMinutes(totals.durationSeconds), Modifier.weight(1f))
        Tile(stringResource(R.string.analytics_tile_volume), AnalyticsFormatters.volume(totals.volumeKg, uiState.weightUnit), Modifier.weight(1f))
        Tile(stringResource(R.string.analytics_tile_sets), totals.sets.toString(), Modifier.weight(1f))
    }
}

@Composable
internal fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- card 3: muscle distribution (body) ---

@Composable
private fun BodyCard(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    val card = uiState.body
    Card {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_body_title))
            if (card.weeks.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(card.weeks) { week ->
                    FilterChip(
                        selected = week == card.selectedWeek,
                        onClick = { viewModel.selectBodyWeek(week) },
                        label = { Text(weekLabel(week)) },
                    )
                }
            }
            BodyDiagram(intensity = card.intensities)
            card.counts.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                    Text(muscleGroupLabel(row.group), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        pluralStringResource(R.plurals.analytics_set_count, row.setCount, row.setCount),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

// --- card 4: set count per muscle group ---

@Composable
private fun SetCountCard(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onMuscleClick: (MuscleGroup) -> Unit,
) {
    val card = uiState.setCounts
    Card {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_set_count_title))
            RangeChips(card.range, viewModel::selectSetCountRange)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilterChip(
                    selected = card.bucket == StatBucket.WEEK,
                    onClick = { viewModel.selectSetCountBucket(StatBucket.WEEK) },
                    label = { Text(stringResource(R.string.analytics_bucket_week)) },
                )
                FilterChip(
                    selected = card.bucket == StatBucket.MONTH,
                    onClick = { viewModel.selectSetCountBucket(StatBucket.MONTH) },
                    label = { Text(stringResource(R.string.analytics_bucket_month)) },
                )
            }
            if (card.muscles.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            if (card.bars.isNotEmpty()) {
                // §5.2 interactions: every bar taps through to its exact value + date range.
                var selectedBar by remember(card.bars) { mutableStateOf<Int?>(null) }
                selectedBar?.let { i ->
                    val bar = card.bars[i]
                    Text(
                        bucketReadoutLabel(bar.bucketStart, card.bucket) + " — " +
                            pluralStringResource(R.plurals.analytics_set_count, bar.setCount, bar.setCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                BarChart(
                    entries = card.bars.map { BarChartEntry(bucketAxisLabel(it.bucketStart, card.bucket), it.setCount.toDouble()) },
                    yLabel = { it.toInt().toString() },
                    selectedIndex = selectedBar,
                    onBarTap = { selectedBar = it },
                    chartHeight = 140.dp,
                )
            }
            // §5.2 card 4: select/deselect directly on the body diagram; rows toggle too, and a
            // long-press... keeping it simple: row tap toggles inclusion, the trailing arrow opens
            // the filtered library (the plan's tap-through lives on the arrow).
            BodyDiagram(
                intensity = card.diagramIntensities,
                selected = card.selectedMuscles,
                onRegionTap = { viewModel.toggleMuscle(it) },
            )
            card.muscles.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { viewModel.toggleMuscle(row.group) }
                        .padding(vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        muscleGroupLabel(row.group),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        pluralStringResource(R.plurals.analytics_set_count, row.setCount, row.setCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                    IconButton(onClick = { onMuscleClick(row.group) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = muscleGroupLabel(row.group),
                            modifier = Modifier.width(16.dp),
                        )
                    }
                }
            }
        }
    }
}

// --- card 5: main exercises ---

@Composable
private fun MainExercisesCard(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onExerciseClick: (String) -> Unit,
) {
    val card = uiState.mainExercises
    Card {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_main_exercises_title))
            RangeChips(card.range, viewModel::selectMainRange)
            if (card.rows.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else {
                card.rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onExerciseClick(row.exerciseId) }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.exerciseName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            pluralStringResource(R.plurals.analytics_exercise_times, row.workoutCount, row.workoutCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// --- card 6: monthly report entry ---

@Composable
private fun MonthlyReportEntryCard(onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(R.string.analytics_monthly_report_title))
                Text(
                    stringResource(R.string.analytics_monthly_report_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}
