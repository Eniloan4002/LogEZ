package com.enil.logez.feature.routines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzIcons
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.rememberClockTimeFormatter
import com.enil.logez.feature.activity.InterruptedTrackingDialog
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * P-205: inline "Recent" card on the Workout tab — the 3 most recently completed workouts (routine
 * or ad hoc), each one tap from running again. Absent entirely when there's no history yet, same
 * honest-empty-state rule every other card on this tab already follows (no "nothing here" card).
 */
@Composable
fun RecentSection(
    onNavigateToLogger: (workoutId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    onSeeAll: () -> Unit,
    onOpenWorkout: (workoutId: String) -> Unit,
    viewModel: RecentWorkoutsViewModel = hiltViewModel(),
) {
    val recent by viewModel.cardState.collectAsState()
    if (recent.isEmpty()) return
    val start = rememberRecentStartHandler(viewModel, onNavigateToLogger, onNavigateToActivityTracking, onNavigateToFinish)

    LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xxs, top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.workout_recent_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.workout_recent_see_all))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        // The card's query already carries LIMIT 3.
        recent.forEachIndexed { index, card ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RecentWorkoutRow(
                card = card,
                compactDate = true,
                onOpen = { onOpenWorkout(card.workoutId) },
                onStart = { start(card.workoutId) },
            )
        }
        Spacer(Modifier.size(Spacing.xs))
    }
}

/**
 * Shared row: [RecentSection]'s inline card and [RecentWorkoutsScreen]'s full list render the
 * identical row. The row opens the workout's detail ([onOpen], R-1); the Start pill stays the
 * one-tap quick start ([onStart]).
 */
@Composable
internal fun RecentWorkoutRow(
    card: RecentWorkoutCardModel,
    compactDate: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.workout_recent_open_a11y, card.title), onClick = onOpen)
            // After the click target, so the ripple and the tap reach both card edges.
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The full list marks routine vs ad hoc with a leading icon; the compact inline card
        // leaves it out, as in the approved mockups (workout-tab.png vs recent-routines.png).
        if (!compactDate) {
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (card.isFromRoutine) Icons.Outlined.Folder else LogEzIcons.Workout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                card.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                recentSubtitle(card, compactDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Primary-tinted pill with a primary hairline -- the same selected-chip vocabulary as the
        // range chips, not primaryContainer (Neutral900, identical to the card, so no visible fill).
        val primary = MaterialTheme.colorScheme.primary
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = primary.copy(alpha = 0.14f), contentColor = primary),
            border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
            modifier = Modifier.padding(start = Spacing.xs),
        ) {
            Text(stringResource(R.string.action_start))
        }
    }
}

@Composable
internal fun recentSubtitle(card: RecentWorkoutCardModel, compact: Boolean): String {
    val zoned = Instant.ofEpochMilli(card.startedAtMillis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now(ZoneId.systemDefault())
    val days = java.time.temporal.ChronoUnit.DAYS.between(zoned.toLocalDate(), today)
    val dayPart = when {
        days <= 0L -> stringResource(R.string.history_card_today)
        days == 1L -> stringResource(R.string.history_card_yesterday)
        else -> pluralStringResource(R.plurals.workout_recent_days_ago, days.toInt(), days.toInt())
    }
    val duration = formatRecentDuration(card.durationSeconds)
    val exercises = pluralStringResource(R.plurals.routine_exercises_count, card.exerciseCount, card.exerciseCount)
    return if (compact) {
        "$dayPart · $duration · $exercises"
    } else {
        val time = zoned.format(rememberClockTimeFormatter())
        stringResource(R.string.workout_recent_subtitle_full, dayPart, time, duration, exercises)
    }
}

internal fun formatRecentDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * Confirm-then-start for any Recent entry, shared by the inline card and the "See all" screen.
 * Owner directive (2026-09-30): a confirmation popup before starting a Recent workout, same as
 * "Start Empty Workout" now asks first — this is the one new step [WorkoutStarter.startFromWorkout]
 * itself doesn't need to know about. On an in-progress conflict, falls back to the same
 * resume-or-discard choice every other start path in the app already offers
 * ([InProgressWorkoutResolver]-driven, matching `WorkoutDetailScreen`'s "Copy Workout").
 */
@Composable
internal fun rememberRecentStartHandler(
    viewModel: RecentStartActions,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val startSession = rememberStartWorkoutSession(onNavigateToLogger)
    var pendingWorkoutId by remember { mutableStateOf<String?>(null) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var conflictingWorkoutId by remember { mutableStateOf<String?>(null) }
    var interruptedRun by remember { mutableStateOf<Pair<String, Long>?>(null) }

    pendingWorkoutId?.let { workoutId ->
        ConfirmDialog(
            onDismissRequest = { pendingWorkoutId = null },
            title = stringResource(R.string.workout_recent_start_confirm_title),
            body = stringResource(R.string.workout_recent_start_confirm_body),
            confirmLabel = stringResource(R.string.action_start),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                scope.launch {
                    when (val result = viewModel.start(workoutId)) {
                        is StartResult.Started -> startSession(result.workoutId)
                        is StartResult.AlreadyInProgress -> { showResumeDialog = true; conflictingWorkoutId = workoutId }
                    }
                }
            },
        )
    }

    interruptedRun?.let { (workoutId, startedAt) ->
        InterruptedTrackingDialog(
            workoutId = workoutId,
            startedAt = startedAt,
            onKeptTime = { id -> interruptedRun = null; onNavigateToFinish(id) },
            onDiscarded = { interruptedRun = null },
        )
    }

    if (showResumeDialog) {
        val sourceWorkoutId = conflictingWorkoutId
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(stringResource(R.string.workout_resume_title)) },
            text = { Text(stringResource(R.string.workout_resume_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    scope.launch {
                        when (val inProgress = viewModel.inProgressWorkout()) {
                            null -> Unit
                            is InProgressWorkout.Strength -> startSession(inProgress.id)
                            is InProgressWorkout.LiveGpsRun -> onNavigateToActivityTracking()
                            is InProgressWorkout.InterruptedGpsRun -> interruptedRun = inProgress.id to inProgress.startedAt
                        }
                    }
                }) { Text(stringResource(R.string.workout_resume_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    val id = sourceWorkoutId
                    if (id != null) scope.launch { startSession(viewModel.discardInProgressAndStart(id)) }
                }) { Text(stringResource(R.string.workout_resume_discard_action)) }
            },
        )
    }

    return { workoutId -> pendingWorkoutId = workoutId }
}
