package com.enil.logez.feature.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.Achievement
import com.enil.logez.core.domain.calc.AchievementCategory
import com.enil.logez.core.domain.calc.AchievementProgress
import java.text.NumberFormat

/**
 * P-208 Achievements — the approved mockup (`docs/mockups/workout-features-2026-09-30/achievements.png`)
 * with plain fitness names and the Owner's step achievements added. Every trophy is the same
 * Material glyph for now; the stable [Achievement] ids are what a later custom-badge pass keys off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.achievements_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.isLoading) return@Scaffold
        val byCategory = state.progress.groupBy { it.achievement.category }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl),
        ) {
            item { SummaryCard(unlocked = state.progress.count { it.unlocked }, total = state.progress.size) }

            AchievementCategory.entries.forEach { category ->
                val items = byCategory[category].orEmpty()
                if (items.isEmpty()) return@forEach
                val isSteps = category == AchievementCategory.DAILY_STEPS || category == AchievementCategory.STEP_STREAKS
                // Nothing to show progress against until Health Connect has supplied steps.
                val stepsUnknown = isSteps && !state.stepsConnected && items.all { it.current == 0L }

                item(key = "label_${category.name}") {
                    SectionLabel(stringResource(category.labelRes()))
                }
                if (category == AchievementCategory.DAILY_STEPS && stepsUnknown) {
                    item(key = "steps_hint") {
                        Text(
                            stringResource(R.string.achievements_steps_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                }
                items.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
                    item(key = "row_${category.name}_$rowIndex") {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            row.forEach { progress ->
                                AchievementCard(
                                    progress = progress,
                                    showProgress = !stepsUnknown,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                            repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(unlocked: Int, total: Int) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.achievements_unlocked_label),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(unlocked.toString(), style = LogEzMono.dataLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    " / $total",
                    style = LogEzMono.dataMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else unlocked.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

/** The shared quiet divider label, with this screen's spacing around it. */
@Composable
private fun SectionLabel(text: String) {
    com.enil.logez.core.designsystem.SectionLabel(text, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm))
}

@Composable
private fun AchievementCard(progress: AchievementProgress, showProgress: Boolean, modifier: Modifier = Modifier) {
    val unlocked = progress.unlocked
    val name = stringResource(progress.achievement.nameRes())
    val description = stringResource(progress.achievement.descriptionRes())
    val numbers = NumberFormat.getIntegerInstance(currentLocale())
    val progressText = stringResource(R.string.achievements_progress, numbers.format(progress.current), numbers.format(progress.target))
    val spoken = if (unlocked) {
        stringResource(R.string.achievements_cd_unlocked, name, description)
    } else {
        stringResource(R.string.achievements_cd_locked, name, description, progressText)
    }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LogEzCard(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!unlocked) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(14.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (unlocked) primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = if (unlocked) primary else muted.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface else muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    if (unlocked || !showProgress) description else progressText,
                    style = LogEzMono.dataSmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
    }
}

private const val COLUMNS = 3

private fun AchievementCategory.labelRes(): Int = when (this) {
    AchievementCategory.WORKOUTS -> R.string.achievements_category_workouts
    AchievementCategory.CONSISTENCY -> R.string.achievements_category_consistency
    AchievementCategory.STRENGTH -> R.string.achievements_category_strength
    AchievementCategory.DAILY_STEPS -> R.string.achievements_category_daily_steps
    AchievementCategory.STEP_STREAKS -> R.string.achievements_category_step_streaks
}

internal fun Achievement.nameRes(): Int = when (this) {
    Achievement.FIRST_WORKOUT -> R.string.achievement_first_workout
    Achievement.WORKOUTS_10 -> R.string.achievement_workouts_10
    Achievement.WORKOUTS_50 -> R.string.achievement_workouts_50
    Achievement.DAY_STREAK_7 -> R.string.achievement_day_streak_7
    Achievement.DAY_STREAK_30 -> R.string.achievement_day_streak_30
    Achievement.WEEK_STREAK_52 -> R.string.achievement_week_streak_52
    Achievement.FIRST_PR -> R.string.achievement_first_pr
    Achievement.PRS_10 -> R.string.achievement_prs_10
    Achievement.FULL_BODY_WEEK -> R.string.achievement_full_body_week
    Achievement.STEPS_DAY_20K -> R.string.achievement_steps_day_20k
    Achievement.STEPS_DAY_30K -> R.string.achievement_steps_day_30k
    Achievement.STEPS_DAY_40K -> R.string.achievement_steps_day_40k
    Achievement.STEPS_DAY_50K -> R.string.achievement_steps_day_50k
    Achievement.STEPS_DAY_75K -> R.string.achievement_steps_day_75k
    Achievement.STEPS_DAY_100K -> R.string.achievement_steps_day_100k
    Achievement.STEP_STREAK_30 -> R.string.achievement_step_streak_30
    Achievement.STEP_STREAK_365 -> R.string.achievement_step_streak_365
}

private fun Achievement.descriptionRes(): Int = when (this) {
    Achievement.FIRST_WORKOUT -> R.string.achievement_first_workout_desc
    Achievement.WORKOUTS_10 -> R.string.achievement_workouts_10_desc
    Achievement.WORKOUTS_50 -> R.string.achievement_workouts_50_desc
    Achievement.DAY_STREAK_7 -> R.string.achievement_day_streak_7_desc
    Achievement.DAY_STREAK_30 -> R.string.achievement_day_streak_30_desc
    Achievement.WEEK_STREAK_52 -> R.string.achievement_week_streak_52_desc
    Achievement.FIRST_PR -> R.string.achievement_first_pr_desc
    Achievement.PRS_10 -> R.string.achievement_prs_10_desc
    Achievement.FULL_BODY_WEEK -> R.string.achievement_full_body_week_desc
    Achievement.STEPS_DAY_20K -> R.string.achievement_steps_day_20k_desc
    Achievement.STEPS_DAY_30K -> R.string.achievement_steps_day_30k_desc
    Achievement.STEPS_DAY_40K -> R.string.achievement_steps_day_40k_desc
    Achievement.STEPS_DAY_50K -> R.string.achievement_steps_day_50k_desc
    Achievement.STEPS_DAY_75K -> R.string.achievement_steps_day_75k_desc
    Achievement.STEPS_DAY_100K -> R.string.achievement_steps_day_100k_desc
    Achievement.STEP_STREAK_30 -> R.string.achievement_step_streak_30_desc
    Achievement.STEP_STREAK_365 -> R.string.achievement_step_streak_365_desc
}
