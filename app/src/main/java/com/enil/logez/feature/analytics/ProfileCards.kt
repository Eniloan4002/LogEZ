package com.enil.logez.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.BarChart
import com.enil.logez.core.designsystem.BarChartEntry
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.MetricChip
import com.enil.logez.core.designsystem.PARTIAL_BAR_ALPHA
import com.enil.logez.core.designsystem.SectionLabel
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.canInstallOrUpdate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/*
 * The cards below the destinations on the Profile tab (docs/mockups/profile-2026-10-01): the weekly
 * chart, the last-7-days muscle map and the Today / Health Connect card.
 */

/** Profile's own chip order (PHASE2_PLAN §5.2 region 4), not [TrainingMetric.entries]. */
internal val ProfileChartMetrics = listOf(TrainingMetric.FREQUENCY, TrainingMetric.VOLUME, TrainingMetric.REPS, TrainingMetric.DURATION)

@Composable
private fun chartTitle(metric: TrainingMetric): String = stringResource(
    when (metric) {
        TrainingMetric.FREQUENCY -> R.string.profile_chart_title_frequency
        TrainingMetric.VOLUME -> R.string.profile_chart_title_volume
        TrainingMetric.REPS -> R.string.profile_chart_title_reps
        TrainingMetric.DURATION -> R.string.profile_chart_title_duration
    },
)

// ---------------------------------------------------------------------------------------------
// Chart
// ---------------------------------------------------------------------------------------------

/**
 * The weekly chart: Statistics' own metric chips switch what the bars count, the current week is
 * drawn lighter ("this week so far") and "Statistics" (or any bar) opens that metric.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileChartCard(
    uiState: ProfileUiState,
    metric: TrainingMetric,
    styles: ProfileStyles,
    onMetricSelected: (TrainingMetric) -> Unit,
    onOpenStatistics: (TrainingMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bars = uiState.quickCharts[metric].orEmpty()
    // The series always ends on the current week; the lighter bar and its key only make sense then.
    val currentWeekShown = bars.isNotEmpty() && bars.last().weekStart == uiState.week?.start
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md, bottom = Spacing.xs)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(chartTitle(metric))
                Text(stringResource(R.string.profile_chart_range), style = styles.meta, color = muted)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ProfileChartMetrics.forEach { m ->
                    key(m) { MetricChip(trainingMetricLabel(m), selected = m == metric, onClick = { onMetricSelected(m) }) }
                }
            }
            Box(modifier = Modifier.padding(top = Spacing.xxs)) {
                if (bars.isEmpty()) {
                    Text(
                        stringResource(R.string.analytics_empty_period),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        modifier = Modifier.padding(vertical = Spacing.md),
                    )
                } else {
                    BarChart(
                        entries = bars.map { BarChartEntry(weekLabel(it.weekStart), it.value) },
                        yLabel = { AnalyticsFormatters.axisLabel(metric, it, uiState.weightUnit) },
                        selectedIndex = null,
                        onBarTap = { onOpenStatistics(metric) },
                        chartHeight = 120.dp,
                        partialLastBar = currentWeekShown,
                        growWithFontScale = true,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentWeekShown) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = PARTIAL_BAR_ALPHA), RoundedCornerShape(3.dp)),
                        )
                        Text(stringResource(R.string.profile_chart_this_week), style = styles.tileSupporting, color = muted)
                    }
                } else {
                    Box(modifier = Modifier)
                }
                TextButton(onClick = { onOpenStatistics(metric) }, contentPadding = PaddingValues(start = Spacing.xs, end = 0.dp)) {
                    Text(stringResource(R.string.profile_nav_statistics), style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Last 7 days
// ---------------------------------------------------------------------------------------------

/** The rolling seven days: the muscle map beside the workout count and "x/8 regions trained". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileLast7Card(
    uiState: ProfileUiState,
    styles: ProfileStyles,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val regionTotal = BodyRegion.entries.size
    val missing = uiState.last7RegionsMissing.map { bodyRegionLabel(it) }.joinToString(", ")
    val spokenRegions = stringResource(R.string.profile_regions_cd, uiState.last7RegionsTrained, regionTotal)
    val spokenMissing = when {
        // Sets that map to no region (cardio, full body, other) are still sets: say which of the two it is.
        uiState.last7RegionsTrained == 0 ->
            stringResource(if (uiState.last7SetCount > 0) R.string.profile_regions_unmapped else R.string.profile_regions_none)
        uiState.last7RegionsMissing.isEmpty() -> ""
        else -> stringResource(R.string.profile_regions_missing, missing)
    }
    val spokenWorkouts = pluralStringResource(R.plurals.profile_last7_workouts_cd, uiState.last7Count, uiState.last7Count)
    // Today is the week's start plus today's index; the window is the six days before it, and today.
    val start = uiState.week?.let { it.start.plusDays((it.todayIndex - 6).toLong()) }

    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(R.string.profile_last7_title))
                if (start != null) {
                    Text(
                        stringResource(R.string.profile_last7_range, dateFormat.format(start)),
                        style = styles.meta,
                        color = muted,
                    )
                }
            }
            // Stacked (map above the numbers) at 2x text, or when the card is too narrow to leave the numbers
            // column ~100 dp beside the 168 dp map: 280 dp inside the card keeps a 360 dp phone side by side.
            val stackAt = 2f
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                val stacked = fontScale >= stackAt || maxWidth < 280.dp
                val diagram: @Composable () -> Unit = {
                    Box(modifier = Modifier.size(168.dp).clearAndSetSemantics { }) {
                        BodyDiagram(intensity = uiState.last7Heat, variant = uiState.muscleDiagramVariant, modifier = Modifier.size(168.dp))
                    }
                }
                val numbers: @Composable (Modifier) -> Unit = { m ->
                    Column(
                        modifier = m.clearAndSetSemantics {
                            contentDescription = buildString {
                                append(spokenWorkouts).append(". ")
                                append(spokenRegions)
                                if (spokenMissing.isNotEmpty()) append(". ").append(spokenMissing)
                            }
                        },
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column {
                            Text(uiState.last7Count.toString(), style = styles.tileValue)
                            Text(pluralStringResource(R.plurals.profile_last7_workouts, uiState.last7Count), style = styles.tileLabel)
                        }
                        Column {
                            Text(
                                buildAnnotatedString {
                                    append(uiState.last7RegionsTrained.toString())
                                    pushStyle(styles.tileValueSuffix)
                                    append("/$regionTotal")
                                    pop()
                                },
                                style = styles.tileValue,
                            )
                            Text(stringResource(R.string.profile_regions_label), style = styles.tileLabel)
                            if (spokenMissing.isNotEmpty()) {
                                Text(
                                    spokenMissing,
                                    style = styles.tileSupporting,
                                    color = muted,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                            }
                        }
                    }
                }
                if (stacked) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        diagram()
                        numbers(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        diagram()
                        numbers(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Today / Health Connect
// ---------------------------------------------------------------------------------------------

/**
 * Health Connect, at the bottom of the tab. Exactly one of: the Today card (steps and/or calories
 * with a figure), the heart-rate-only card, the Connect card (or its refusal variant), the Install /
 * Update card, or nothing (not available on this phone, or granted steps/calories with nothing to show).
 */
@Composable
internal fun HealthSection(
    uiState: ProfileUiState,
    styles: ProfileStyles,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availability = uiState.wellnessAvailability
    when {
        availability.canInstallOrUpdate() -> {
            val updating = availability == HealthConnectAvailability.UpdateRequired
            HealthPromptCard(
                title = stringResource(if (updating) R.string.wellness_update_title else R.string.wellness_install_title),
                body = stringResource(if (updating) R.string.wellness_update_body else R.string.profile_wellness_install_body),
                modifier = modifier,
            ) {
                OutlinedButton(onClick = onInstall, colors = limeText(), modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(R.string.wellness_install_action))
                }
            }
        }
        availability != HealthConnectAvailability.Available -> Unit
        // Shown until the user grants at least one type. A partial grant is a working choice, not an
        // unfinished one; Settings > Data > Health Connect reaches the rest.
        uiState.wellnessGranted.isEmpty() -> {
            HealthPromptCard(
                title = stringResource(R.string.wellness_connect_title),
                // After a full refusal Health Connect answers Connect at once without showing anything,
                // so the card says what happened and opens its settings instead. One body Text for both,
                // so the refusal replaces its words in place, and a polite live region reads it out.
                body = stringResource(if (uiState.wellnessRefused) R.string.wellness_connect_refused else R.string.profile_wellness_connect_body),
                polite = true,
                modifier = modifier,
            ) {
                if (uiState.wellnessRefused) {
                    TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = Spacing.xs).offset(x = (-12).dp)) {
                        Text(stringResource(R.string.activity_tracking_heart_rate_open_settings))
                    }
                } else {
                    OutlinedButton(onClick = onConnect, colors = limeText(), modifier = Modifier.padding(top = Spacing.sm)) {
                        Text(stringResource(R.string.wellness_connect_action))
                    }
                }
            }
        }
        uiState.todaySteps != null || uiState.todayCaloriesBurned != null ->
            TodayCard(
                steps = uiState.todaySteps,
                calories = uiState.todayCaloriesBurned,
                heartRateGranted = HealthDataType.HEART_RATE in uiState.wellnessGranted,
                styles = styles,
                modifier = modifier,
            )
        // Heart rate has nothing to show here (it appears during workouts and walk/run tracking), and with
        // no steps or calories figure there is no Today card either. Health Connect's settings is the way
        // to add the rest, so the card offers it rather than reading as finished.
        HealthDataType.HEART_RATE in uiState.wellnessGranted ->
            HealthPromptCard(
                title = stringResource(R.string.wellness_heart_rate_title),
                body = stringResource(R.string.wellness_heart_rate_body),
                modifier = modifier,
            ) {
                TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = Spacing.xs).offset(x = (-12).dp)) {
                    Text(stringResource(R.string.activity_tracking_heart_rate_open_settings))
                }
            }
    }
}

/** Lime button text on the outline-only prompt buttons (lime marks actions). */
@Composable
private fun limeText() = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)

/** A prompt card: a quiet grey heart (an optional prompt does not compete with the user's data), title, body, action. */
@Composable
private fun HealthPromptCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    polite: Boolean = false,
    action: @Composable () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = muted, modifier = Modifier.size(24.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier
                    .padding(top = Spacing.xxs)
                    .let { if (polite) it.semantics { liveRegion = LiveRegionMode.Polite } else it },
            )
            action()
        }
    }
}

private fun formatSteps(steps: Long): String = "%,d".format(Locale.ROOT, steps)

private fun formatCalories(calories: Double): String = "%,d".format(Locale.ROOT, calories.roundToLong())

private class TodayStat(val key: String, val value: String, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(
    steps: Long?,
    calories: Double?,
    heartRateGranted: Boolean,
    styles: ProfileStyles,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val stats = buildList {
        // Absent when Health Connect has no figure for today (null, not zero): this app never shows a
        // stat it isn't actually tracking.
        if (steps != null) add(TodayStat("steps", formatSteps(steps), stringResource(R.string.wellness_steps_label)))
        if (calories != null) add(TodayStat("calories", formatCalories(calories), stringResource(R.string.wellness_calories_label)))
    }
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(R.string.wellness_today_title))
                Text(stringResource(R.string.wellness_source_label), style = styles.meta, color = muted)
            }
            TodayStats(stats, styles, Modifier.padding(top = Spacing.sm))
            if (heartRateGranted) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = Spacing.sm))
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.wellness_heart_rate_note), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
        }
    }
}

/**
 * Two figures side by side while each whole value and each single label word fits its half, else
 * one row per figure (label left, value right). One figure fills the card.
 */
@Composable
private fun TodayStats(stats: List<TodayStat>, styles: ProfileStyles, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val halfPx = constraints.maxWidth / 2f - with(density) { 16.dp.toPx() }
        val sideBySide = stats.size < 2 || stats.all {
            measurer.widthOf(AnnotatedString(it.value), styles.tileValue) <= halfPx &&
                measurer.wordsFit(AnnotatedString(it.label), styles.tileLabel, halfPx)
        }
        when {
            stats.size == 1 -> TodayFigure(stats.first(), styles, Modifier.fillMaxWidth())
            sideBySide -> Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                stats.forEachIndexed { index, stat ->
                    key(stat.key) {
                        if (index > 0) {
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        TodayFigure(
                            stat, styles,
                            Modifier.weight(1f).padding(start = if (index > 0) Spacing.md else 0.dp, end = if (index == 0) Spacing.sm else 0.dp),
                        )
                    }
                }
            }
            else -> Column(modifier = Modifier.fillMaxWidth()) {
                stats.forEachIndexed { index, stat ->
                    key(stat.key) {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stat.label, style = styles.tileLabel, modifier = Modifier.weight(1f))
                            Text(stat.value, style = styles.tileValue, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayFigure(stat: TodayStat, styles: ProfileStyles, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(stat.value, style = styles.tileValue, maxLines = 1, softWrap = false)
        Text(stat.label, style = styles.tileLabel)
    }
}
