package com.enil.logez.feature.analytics

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.Achievement
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.wellness.openHealthConnectInPlayStore
import com.enil.logez.core.wellness.openHealthConnectSettings
import com.enil.logez.core.wellness.rememberRequestHealthConnectPermissions
import com.enil.logez.feature.achievements.nameRes

/**
 * Profile tab (PHASE2_PLAN.md §5.2), redesigned 2026-10-01 (docs/mockups/profile-2026-10-01/final):
 * This week first, four scorecards, the six destinations, the weekly chart, the 7-day muscle map,
 * and Health Connect last. Entering the tab never flashes zeros at a user with history: until the
 * first load lands the header and the tiles are composed with invisible placeholder values over grey
 * blocks (so they reserve their real size), and the data cards are simply absent.
 *
 * A brand-new user (no workouts) sees only what can already move: This week, the Achievements
 * tile, the destinations and Health Connect, never a row of zero tiles or an empty chart.
 *
 * The day-streak tile reads [ProfileUiState.streakDays] and [ProfileUiState.longestDayStreak] only,
 * and the tiles are a list ([ScoreTileSpec]), so a later streak source or an extra tile changes
 * [rememberScoreTiles] and the view model, not this layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onExercisesClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onStatisticsClick: (TrainingMetric?) -> Unit = {},
    onMeasurementsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAchievementsClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestWellnessPermissions = rememberRequestHealthConnectPermissions(
        source = viewModel.healthMetricsSource,
        onResult = viewModel::onWellnessPermissionResult,
    )
    var chartMetric by rememberSaveable { mutableStateOf(TrainingMetric.FREQUENCY) }

    RefreshOnResume(viewModel::refresh)

    val styles = rememberProfileStyles()
    val loading = uiState.isLoading
    val hasWorkouts = uiState.workoutCount > 0
    val tiles = rememberScoreTiles(
        uiState = uiState,
        styles = styles,
        onCalendar = onCalendarClick,
        onFrequency = { onStatisticsClick(TrainingMetric.FREQUENCY) },
        onAchievements = onAchievementsClick,
    )
    val destinations = rememberDestinations(
        latestWeight = uiState.latestWeight,
        weightUnit = uiState.weightUnit,
        styles = styles,
        onStatistics = { onStatisticsClick(null) },
        onAchievements = onAchievementsClick,
        onCalendar = onCalendarClick,
        onMeasurements = onMeasurementsClick,
        onExercises = onExercisesClick,
        onSettings = onSettingsClick,
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Tab roots live inside LogEzApp's Scaffold, whose innerPadding already pushes this whole
        // NavHost below the status bar — TopAppBar's default windowInsets would re-apply the
        // status-bar inset and double the empty space above the header, so it is zeroed too.
        topBar = { TopAppBar(title = { ScreenTitle(stringResource(R.string.nav_profile)) }, windowInsets = WindowInsets(0, 0, 0, 0), colors = logEzTopAppBarColors()) },
    ) { padding ->
        // Every item always exists (empty while it has nothing to show), so a loading-to-loaded
        // transition never shifts the scroll position underneath the user.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.md, top = Spacing.xs, end = Spacing.md, bottom = Spacing.lg),
        ) {
            item(key = "this_week") {
                ThisWeekCard(
                    week = uiState.week,
                    hasWorkouts = hasWorkouts,
                    weightUnit = uiState.weightUnit,
                    styles = styles,
                    loading = loading,
                    onClick = onCalendarClick,
                )
            }
            item(key = "scorecards") {
                ScoreTileGrid(
                    // No row of zero tiles for a new user: only Achievements has something to move.
                    tiles = if (loading || hasWorkouts) tiles else tiles.filter { it.key == TILE_ACHIEVEMENTS },
                    styles = styles,
                    loading = loading,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            item(key = "destinations") {
                ProfileDestinationGrid(destinations, styles, modifier = Modifier.padding(top = Spacing.md))
            }
            item(key = "chart") {
                if (!loading && hasWorkouts) {
                    ProfileChartCard(
                        uiState = uiState,
                        metric = chartMetric,
                        styles = styles,
                        onMetricSelected = { chartMetric = it },
                        onOpenStatistics = onStatisticsClick,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
            item(key = "last7") {
                if (!loading && hasWorkouts) {
                    ProfileLast7Card(uiState, styles, modifier = Modifier.padding(top = Spacing.md))
                }
            }
            item(key = "health") {
                if (!loading) {
                    HealthSection(
                        uiState = uiState,
                        styles = styles,
                        onConnect = requestWellnessPermissions,
                        onInstall = { openHealthConnectInPlayStore(context) },
                        onOpenSettings = { openHealthConnectSettings(context) },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
        }
    }
}

internal const val TILE_WEEK_STREAK = "week_streak"
internal const val TILE_DAY_STREAK = "day_streak"
internal const val TILE_WORKOUTS = "workouts"
internal const val TILE_ACHIEVEMENTS = "achievements"

/**
 * The scorecards, in order. Numbers only in the value slot; the label, the supporting line and the
 * spoken description carry the words. While [ProfileUiState.isLoading] the specs carry placeholder
 * text that is drawn invisible, so the tiles reserve their real size.
 */
@Composable
internal fun rememberScoreTiles(
    uiState: ProfileUiState,
    styles: ProfileStyles,
    onCalendar: () -> Unit,
    onFrequency: () -> Unit,
    onAchievements: () -> Unit,
): List<ScoreTileSpec> {
    val locale = currentLocale()
    val loading = uiState.isLoading
    val emphasis = styles.monoQuiet

    // Placeholder numbers have the width of a plausible real one ("00"), so the layout choice made
    // while loading matches the one made after.
    val weekStreak = if (loading) 0 else uiState.streakWeeks
    val longestWeek = if (loading) 0 else uiState.longestWeekStreak
    val dayStreak = if (loading) 0 else uiState.streakDays
    val longestDay = if (loading) 0 else uiState.longestDayStreak
    val workoutCount = if (loading) 0 else uiState.workoutCount
    fun num(n: Int) = if (loading) "00" else n.toString()

    val weeksSpoken = pluralStringResource(R.plurals.profile_streak_weeks, weekStreak, weekStreak)
    val longestWeeksSpoken = stringResource(R.string.profile_longest, pluralStringResource(R.plurals.profile_streak_weeks, longestWeek, longestWeek))
    val daysSpoken = pluralStringResource(R.plurals.profile_day_streak_value, dayStreak, dayStreak)
    val longestDaysSpoken = stringResource(R.string.profile_longest, pluralStringResource(R.plurals.profile_day_streak_value, longestDay, longestDay))
    val weekStreakLabel = stringResource(R.string.profile_stat_streak)
    val dayStreakLabel = stringResource(R.string.profile_stat_day_streak)
    val workoutsLabel = pluralStringResource(R.plurals.profile_stat_workouts, workoutCount)

    // The year shows when the first workout was in an earlier year than today (not than the week's start,
    // which can be last December on a 1 January).
    val todayYear = uiState.week?.let { it.start.plusDays(it.todayIndex.toLong()).year }
    val includeYear = uiState.firstWorkoutDate?.let { first -> todayYear != null && first.year != todayYear } ?: false
    val sinceDate = if (loading) "00 Mmm" else uiState.firstWorkoutDate?.formatShort(locale, includeYear).orEmpty()
    val since = stringResource(R.string.profile_since, sinceDate)

    val total = if (loading) Achievement.entries.size else uiState.achievementsTotal
    val unlocked = if (loading) 0 else uiState.achievementsUnlocked
    val next = uiState.nextAchievement
    val nextText = when {
        loading -> stringResource(R.string.profile_next_achievement, stringResource(Achievement.WORKOUTS_50.nameRes()))
        next != null -> stringResource(R.string.profile_next_achievement, stringResource(next.achievement.nameRes()))
        else -> stringResource(R.string.profile_all_unlocked)
    }
    val achievementsLabel = stringResource(R.string.profile_nav_achievements)

    val workoutsCd = stringResource(R.string.profile_tile_cd, workoutsLabel, workoutCount.toString(), since)
    val achievementsCd = stringResource(
        R.string.profile_tile_cd, achievementsLabel, stringResource(R.string.profile_achievements_spoken, unlocked, total), nextText,
    )

    return listOf(
        ScoreTileSpec(
            key = TILE_WEEK_STREAK,
            value = AnnotatedString(num(weekStreak)),
            label = weekStreakLabel,
            supporting = withEmphasis(stringResource(R.string.profile_longest, num(longestWeek)), num(longestWeek), emphasis),
            progress = null,
            description = stringResource(R.string.profile_tile_cd, weekStreakLabel, weeksSpoken, longestWeeksSpoken).takeUnless { loading }.orEmpty(),
            onClick = onCalendar,
        ),
        ScoreTileSpec(
            key = TILE_DAY_STREAK,
            // Strict streak for now (a day counts only with a workout); only the source in the view
            // model changes when rest days arrive.
            value = AnnotatedString(num(dayStreak)),
            label = dayStreakLabel,
            supporting = withEmphasis(stringResource(R.string.profile_longest, num(longestDay)), num(longestDay), emphasis),
            progress = null,
            description = stringResource(R.string.profile_tile_cd, dayStreakLabel, daysSpoken, longestDaysSpoken).takeUnless { loading }.orEmpty(),
            onClick = onCalendar,
        ),
        ScoreTileSpec(
            key = TILE_WORKOUTS,
            value = AnnotatedString(num(workoutCount)),
            label = workoutsLabel,
            supporting = AnnotatedString(since),
            progress = null,
            description = workoutsCd.takeUnless { loading }.orEmpty(),
            onClick = onFrequency,
        ),
        ScoreTileSpec(
            key = TILE_ACHIEVEMENTS,
            value = buildAnnotatedString {
                append(num(unlocked))
                pushStyle(styles.tileValueSuffix)
                append("/$total")
                pop()
            },
            label = achievementsLabel,
            supporting = AnnotatedString(nextText),
            progress = if (total == 0) 0f else unlocked.toFloat() / total,
            description = achievementsCd.takeUnless { loading }.orEmpty(),
            onClick = onAchievements,
        ),
    )
}
