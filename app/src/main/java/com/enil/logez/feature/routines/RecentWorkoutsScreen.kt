package com.enil.logez.feature.routines

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * P-205 "See all" — every finished strength workout (routine or ad hoc), grouped into This week / Last
 * week / Older by rolling 7-day windows from today (not calendar-week boundaries — a simpler v1
 * bucketing than the Calendar tab's Monday-start weeks; revisit if that reads as inconsistent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentWorkoutsScreen(
    onBack: () -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    viewModel: RecentWorkoutsViewModel = hiltViewModel(),
) {
    val recent by viewModel.uiState.collectAsState()
    val start = rememberRecentStartHandler(viewModel, onNavigateToLogger, onNavigateToActivityTracking, onNavigateToFinish)
    val today = LocalDate.now(ZoneId.systemDefault())
    val groups = recent.groupBy { card -> recentGroupOf(card.startedAtMillis, today, ZoneId.systemDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.workout_recent_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(bottom = Spacing.lg),
        ) {
            item {
                Text(
                    stringResource(R.string.workout_recent_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
            RecentGroup.entries.forEach { group ->
                val cards = groups[group].orEmpty()
                if (cards.isEmpty()) return@forEach
                item {
                    // Muted mono small caps, as in the approved mockup (recent-routines.png) -- a
                    // quiet divider label, not a lime section header competing with the Start pills.
                    Text(
                        stringResource(group.labelRes).uppercase(),
                        style = LogEzMono.dataMedium.copy(letterSpacing = 0.1.em),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                    )
                }
                item {
                    LogEzCard(modifier = Modifier.fillMaxSize().padding(bottom = Spacing.md)) {
                        cards.forEachIndexed { index, card ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            RecentWorkoutRow(
                                card = card,
                                compactDate = false,
                                onStart = { start(card.workoutId) },
                                modifier = Modifier.padding(horizontal = Spacing.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rolling 7-day buckets from [today] -- see this file's KDoc for why not calendar weeks. */
internal fun recentGroupOf(startedAtMillis: Long, today: LocalDate, zone: ZoneId): RecentGroup {
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(startedAtMillis).atZone(zone).toLocalDate(), today)
    return when {
        days < 7 -> RecentGroup.THIS_WEEK
        days < 14 -> RecentGroup.LAST_WEEK
        else -> RecentGroup.OLDER
    }
}

internal enum class RecentGroup(val labelRes: Int) {
    THIS_WEEK(R.string.workout_recent_section_this_week),
    LAST_WEEK(R.string.workout_recent_section_last_week),
    OLDER(R.string.workout_recent_section_older),
}
