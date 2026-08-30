package com.enil.logez.feature.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.BuildConfig
import com.enil.logez.R
import com.enil.logez.core.designsystem.BarChart
import com.enil.logez.core.designsystem.BarChartEntry
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzIcons
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import java.util.Locale

/**
 * Profile tab (PHASE2_PLAN.md §5.2 "Profile tab"): headline stats (lifetime Workouts + Streak,
 * real zeros for a fresh install — honest, never faked), the last-7-days strip with a mini muscle
 * heat-map, the swipeable quick chart card (tap → Statistics with that chart focused), and
 * navigation rows. The Calendar entry stays a nav row rather than an inline grid — Owner-confirmed
 * permanent M5c trim.
 *
 * Also carries the temporary RPE-tracking toggle (2026-08-24): §5.1.7's RPE picker is gated
 * behind `rpeTrackingEnabled`, but no Settings screen exists until M7. Remove this row once the
 * real Settings tree lands with its own row for the same setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onExercisesClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onStatisticsClick: (TrainingMetric?) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val rpeTrackingEnabled by viewModel.rpeTrackingEnabled.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSeedingDemoData by viewModel.isSeedingDemoData.collectAsStateWithLifecycle()

    RefreshOnResume(viewModel::refresh)

    Scaffold(
        topBar = { TopAppBar(title = { ScreenTitle(stringResource(R.string.nav_profile)) }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            navItems(
                onExercisesClick = onExercisesClick,
                onCalendarClick = onCalendarClick,
                onStatisticsClick = onStatisticsClick,
                rpeTrackingEnabled = rpeTrackingEnabled,
                onRpeToggle = viewModel::setRpeTrackingEnabled,
                isSeedingDemoData = isSeedingDemoData,
                onSeedDemoData = viewModel::seedDemoData,
                onClearDemoData = viewModel::clearDemoData,
            )
            profileStatsItems(uiState, onStatisticsClick)
        }
    }
}

/**
 * The three data-backed items (headline stats, 7-day strip, quick charts). Their *content* is
 * rendered only once the first load lands, so entering the tab never flashes "0 Workouts / No
 * active streak" at a user with real history (the M6a empty-state-flash lesson) — but the items
 * themselves always exist rather than being conditionally inserted, so a loading→loaded transition
 * never shifts scroll position underneath the user.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.profileStatsItems(
    uiState: ProfileUiState,
    onStatisticsClick: (TrainingMetric?) -> Unit,
) {
        item(key = "headline") {
            if (uiState.isLoading) return@item
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                HeadlineStat(
                    label = stringResource(R.string.profile_stat_workouts),
                    value = uiState.workoutCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                HeadlineStat(
                    label = stringResource(R.string.profile_stat_streak),
                    value = if (uiState.streakWeeks > 0) {
                        pluralStringResource(R.plurals.profile_streak_weeks, uiState.streakWeeks, uiState.streakWeeks)
                    } else {
                        stringResource(R.string.profile_no_streak)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "last7") {
            if (uiState.isLoading) return@item
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.profile_last7_title).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.last7Count == 0) {
                        // §5.2 region 2: the strip hides behind an honest hint when the window is empty.
                        Text(
                            stringResource(R.string.profile_last7_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            pluralStringResource(R.plurals.profile_last7_count, uiState.last7Count, uiState.last7Count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        BodyDiagram(intensity = uiState.last7Heat)
                    }
                }
            }
        }

        item(key = "quick_charts") {
            if (uiState.isLoading) return@item
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.profile_quick_charts_title).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // §5.2 region 4's stated order, not the enum's declaration order.
                    val metrics = listOf(TrainingMetric.FREQUENCY, TrainingMetric.VOLUME, TrainingMetric.REPS, TrainingMetric.DURATION)
                    val pagerState = rememberPagerState(pageCount = { metrics.size })
                    HorizontalPager(state = pagerState) { page ->
                        val metric = metrics[page]
                        val bars = uiState.quickCharts[metric].orEmpty()
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { onStatisticsClick(metric) },
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                trainingMetricLabel(metric),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (bars.isEmpty()) {
                                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
                            } else {
                                BarChart(
                                    entries = bars.map { BarChartEntry(weekLabel(it.weekStart), it.value) },
                                    yLabel = { AnalyticsFormatters.axisLabel(metric, it, uiState.weightUnit) },
                                    selectedIndex = null,
                                    onBarTap = { onStatisticsClick(metric) },
                                    chartHeight = 120.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

}

private fun androidx.compose.foundation.lazy.LazyListScope.navItems(
    onExercisesClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onStatisticsClick: (TrainingMetric?) -> Unit,
    rpeTrackingEnabled: Boolean,
    onRpeToggle: (Boolean) -> Unit,
    isSeedingDemoData: Boolean,
    onSeedDemoData: () -> Unit,
    onClearDemoData: () -> Unit,
) {
        item(key = "nav_statistics") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onStatisticsClick(null) },
                leadingContent = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.profile_nav_statistics)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
        item(key = "nav_calendar") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCalendarClick),
                leadingContent = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.profile_calendar_row)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
        item(key = "nav_exercises") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onExercisesClick),
                leadingContent = { Icon(LogEzIcons.Workout, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.profile_nav_exercises)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
        item(key = "rpe_toggle") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onRpeToggle(!rpeTrackingEnabled) },
                headlineContent = { Text(stringResource(R.string.profile_rpe_toggle_title)) },
                supportingContent = { Text(stringResource(R.string.profile_rpe_toggle_subtitle)) },
                trailingContent = { Switch(checked = rpeTrackingEnabled, onCheckedChange = onRpeToggle) },
            )
            HorizontalDivider()
        }
        // Dev-only tooling (seeding/clearing sample data) must never reach a release build — a real
        // tester tapping "Clear demo data" would wipe their own logged workouts by mistake.
        if (BuildConfig.DEBUG) {
            item(key = "seed_demo_data") {
                ListItem(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !isSeedingDemoData, onClick = onSeedDemoData),
                    headlineContent = { Text(stringResource(R.string.profile_seed_demo_data_title)) },
                    supportingContent = { Text(stringResource(R.string.profile_seed_demo_data_subtitle)) },
                    trailingContent = { if (isSeedingDemoData) CircularProgressIndicator(modifier = Modifier.size(20.dp)) },
                )
                HorizontalDivider()
            }
            item(key = "clear_demo_data") {
                ListItem(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !isSeedingDemoData, onClick = onClearDemoData),
                    headlineContent = { Text(stringResource(R.string.profile_clear_demo_data_title)) },
                    supportingContent = { Text(stringResource(R.string.profile_clear_demo_data_subtitle)) },
                )
                HorizontalDivider()
            }
        }
}

@Composable
private fun HeadlineStat(label: String, value: String, modifier: Modifier = Modifier) {
    LogEzCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(value, style = LogEzMono.dataLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
