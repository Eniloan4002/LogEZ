package com.enil.logez.feature.workout.finish

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.feature.history.formatCardDateTime
import com.enil.logez.feature.workout.finish.share.ShareCardData
import com.enil.logez.feature.workout.finish.share.ShareSummarySheet

/**
 * PHASE2_PLAN.md §5.1.8(c) post-save summary, plus the local-only summary-card export: the share
 * button renders the summary as a branded PNG and hands it to the system share sheet — no network,
 * no social SDKs; the image leaves the device only through the target the user picks there. Back
 * is intercepted to mean Done: the workout is already saved, so re-entering the finish screen
 * behind it would offer to save something that no longer exists.
 */
@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Saveable so a rotation with the sheet open re-opens it instead of silently swallowing the tap.
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onDone)

    Scaffold { padding ->
        if (uiState.isLoading) return@Scaffold

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.summary_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(uiState.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.xs))
            Text(
                stringResource(R.string.summary_workout_ordinal, uiState.workoutOrdinal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xxs),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell(stringResource(R.string.summary_volume), formatVolume(uiState.totalVolumeKg))
                StatCell(stringResource(R.string.summary_sets), uiState.completedSetCount.toString())
                StatCell(stringResource(R.string.summary_duration), formatDuration(uiState.durationSeconds))
            }

            if (uiState.weeklyStreak > 0) {
                Text(
                    "${stringResource(R.string.summary_streak)}: " +
                        pluralStringResource(R.plurals.summary_streak_value, uiState.weeklyStreak, uiState.weeklyStreak),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }

            if (uiState.prMedals.isNotEmpty()) {
                Text(
                    stringResource(R.string.summary_prs_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm).fillMaxWidth(),
                )
                uiState.prMedals.forEach { medal -> PrMedalCard(medal) }
            }

            OutlinedButton(
                onClick = { showShareSheet = true },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            ) {
                Text(stringResource(R.string.summary_share))
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Text(stringResource(R.string.summary_done))
            }
        }

        if (showShareSheet) {
            ShareSummarySheet(
                data = ShareCardData(
                    title = uiState.title,
                    dateLine = formatCardDateTime(uiState.startedAtMillis),
                    durationText = formatDuration(uiState.durationSeconds),
                    volumeText = formatVolume(uiState.totalVolumeKg),
                    setsText = uiState.completedSetCount.toString(),
                    workoutOrdinal = uiState.workoutOrdinal,
                    weeklyStreak = uiState.weeklyStreak,
                    prs = uiState.prMedals,
                    exerciseLines = uiState.exerciseLines.map { "${it.setCount} × ${it.name}" },
                ),
                onDismiss = { showShareSheet = false },
            )
        }
    }
}

/**
 * Highlights a personal record on the post-workout summary. Scoped to exactly this one
 * celebratory moment, not applied anywhere else a PR could appear (the live in-session PR
 * banner is untouched).
 */
@Composable
private fun PrMedalCard(medal: PrMedal) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(medal.exerciseName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(medal.prType.labelRes()), style = MaterialTheme.typography.bodySmall)
            }
            Text(formatPrValue(medal), style = LogEzMono.dataLarge)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = LogEzMono.dataLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatVolume(kg: Double): String =
    if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(kg)

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Reps-based records are whole numbers; time is m:ss; everything else carries a unit. */
private fun formatPrValue(medal: PrMedal): String = when (medal.prType) {
    com.enil.logez.core.domain.model.PrType.MOST_REPS_SET,
    com.enil.logez.core.domain.model.PrType.MOST_SESSION_REPS,
    -> medal.value.toInt().toString()
    com.enil.logez.core.domain.model.PrType.BEST_TIME,
    com.enil.logez.core.domain.model.PrType.LONGEST_TIME,
    -> "%d:%02d".format(medal.value.toInt() / 60, medal.value.toInt() % 60)
    com.enil.logez.core.domain.model.PrType.LONGEST_DISTANCE -> "%.0fm".format(medal.value)
    else -> formatVolume(medal.value)
}
