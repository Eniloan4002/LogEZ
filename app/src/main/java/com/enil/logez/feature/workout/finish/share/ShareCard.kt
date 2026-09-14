package com.enil.logez.feature.workout.finish.share

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.workout.finish.PrMedal
import com.enil.logez.feature.workout.finish.labelRes
import java.util.Locale

/**
 * Everything [ShareCard] renders, pre-formatted by the summary screen — the card itself is a pure
 * function of this so preview and export can never disagree with the on-screen summary.
 */
data class ShareCardData(
    val title: String,
    val dateLine: String,
    val durationText: String,
    /** Null when this workout never logged that field at all (e.g. a GPS-tracked walk has no
     * weight/reps concept) -- [ShareCard] omits the cell entirely rather than show a fake zero. */
    val volumeText: String?,
    val setsText: String,
    val repsText: String?,
    val distanceText: String? = null,
    val workoutOrdinal: Int,
    val weeklyStreak: Int,
    val dailyStreak: Int,
    val prs: List<PrMedal>,
    /**
     * REGULAR: History's "3 × Bench Press (Barbell)" shape. CIRCUIT: bare names in sequence order
     * — the per-block set count is redundant with the card's own "CIRCUIT · N rounds" line.
     */
    val exerciseLines: List<String>,
    /** M11: CIRCUIT cards add the rounds line; REGULAR cards are visually unchanged. */
    val structure: WorkoutStructure,
    val rounds: Int,
)

/**
 * The exported summary card. Fixed-size ([Modifier.requiredSize], never the caller's constraints)
 * and self-themed, because it is composed under a fixed export density and captured to a PNG —
 * nothing about the host screen may leak into its layout. Story (9:16) is the only format
 * (Owner directive — square retired); the detail block clips inside a weighted column so a
 * worst-case card (long 2-line title + PRs + streak) degrades by dropping list rows, never by
 * pushing the wordmark off-canvas.
 */
@Composable
fun ShareCard(data: ShareCardData, format: ShareCardFormat, modifier: Modifier = Modifier) {
    LogEzTheme {
        val sectionGap = Spacing.md
        Column(
            modifier = modifier
                .requiredSize(format.width, format.height)
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.lg),
        ) {
            Text(
                stringResource(R.string.summary_title).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.14.em),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                data.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            Text(
                data.dateLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            if (data.structure == WorkoutStructure.CIRCUIT) {
                // M11: header metadata, not a stat — the workout's shape sits with title/date, and
                // the exercise list below drops its per-block set counts in favour of this line.
                Text(
                    (
                        stringResource(R.string.routine_structure_chip_circuit) + " · " +
                            pluralStringResource(R.plurals.routine_rounds_count, data.rounds, data.rounds)
                        ).uppercase(Locale.getDefault()),
                    style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            Hairline(Modifier.padding(vertical = sectionGap))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CardStat(R.string.summary_duration, data.durationText)
                data.volumeText?.let { CardStat(R.string.summary_volume, it) }
                CardStat(R.string.summary_sets, data.setsText)
                data.repsText?.let { CardStat(R.string.summary_reps, it) }
                data.distanceText?.let { CardStat(R.string.summary_distance, it) }
            }

            Row(modifier = Modifier.padding(top = sectionGap), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.summary_workout_ordinal, data.workoutOrdinal),
                    style = LogEzMono.dataMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (data.dailyStreak > 0) {
                    Text(
                        " · " + pluralStringResource(R.plurals.calendar_day_streak_banner, data.dailyStreak, data.dailyStreak),
                        style = LogEzMono.dataMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (data.weeklyStreak > 0) {
                    Text(
                        " · " + pluralStringResource(R.plurals.calendar_streak_banner, data.weeklyStreak, data.weeklyStreak),
                        style = LogEzMono.dataMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).clipToBounds()) {
                val detailStyle = MaterialTheme.typography.bodyMedium
                if (data.prs.isNotEmpty()) {
                    SectionHeader(
                        pluralStringResource(R.plurals.share_card_prs_header, data.prs.size),
                        modifier = Modifier.padding(top = sectionGap, bottom = Spacing.xxs),
                    )
                    val maxPrs = 3
                    data.prs.take(maxPrs).forEach { pr -> PrLine(pr, detailStyle) }
                    val morePrs = data.prs.size - maxPrs
                    if (morePrs > 0) MoreLine(stringResource(R.string.share_card_more_prs, morePrs))
                }
                if (data.exerciseLines.isNotEmpty()) {
                    val maxExercises = 5
                    Column(modifier = Modifier.padding(top = sectionGap)) {
                        data.exerciseLines.take(maxExercises).forEach { line ->
                            Text(line, style = detailStyle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val more = data.exerciseLines.size - maxExercises
                        if (more > 0) MoreLine(pluralStringResource(R.plurals.share_card_more_exercises, more, more))
                    }
                }
            }

            Hairline(Modifier.padding(top = Spacing.xs, bottom = Spacing.xs))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.06.em),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
}

@Composable
private fun CardStat(@StringRes labelRes: Int, value: String) {
    Column {
        Text(value, style = LogEzMono.dataLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(
            stringResource(labelRes).uppercase(Locale.getDefault()),
            style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun PrLine(pr: PrMedal, style: TextStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            pr.exerciseName,
            style = style,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(pr.prType.labelRes()),
            style = style,
            // Primary, not tertiary: the card's palette is white + black + exactly ONE green.
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
private fun MoreLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
}
