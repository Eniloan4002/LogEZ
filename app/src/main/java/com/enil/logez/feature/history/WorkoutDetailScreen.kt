package com.enil.logez.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Danger500
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzIcons
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SupersetPalette
import com.enil.logez.core.designsystem.Warning500
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.finish.labelRes
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** PHASE2_PLAN.md §5.2 "Workout Detail": read-only record of one completed workout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onEdit: (workoutId: String) -> Unit,
    onSavedAsRoutine: (routineId: String) -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onRoutineClick: (routineId: String) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val startSession = rememberStartWorkoutSession(onNavigateToLogger)

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var conflictingWorkoutId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.isMissing) { if (uiState.isMissing) onBack() }

    // Edit mode saves in place and pops straight back here, so the data this screen loaded in
    // init is stale the moment it returns. Re-read on every RESUME rather than on first
    // composition only.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun copyWorkout() = scope.launch {
        menuExpanded = false
        when (val result = viewModel.startCopy()) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { showResumeDialog = true; conflictingWorkoutId = result.workoutId }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.workout?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val loadedWorkoutId = uiState.workout?.id
                    if (loadedWorkoutId != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_edit)) },
                                onClick = { menuExpanded = false; onEdit(loadedWorkoutId) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_copy)) },
                                onClick = { copyWorkout() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_save_as_routine)) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { viewModel.saveAsRoutine()?.let(onSavedAsRoutine) }
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
        val workout = uiState.workout
        if (workout == null || uiState.isLoading) return@Scaffold

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md)) {
            item {
                Column(modifier = Modifier.padding(top = Spacing.md)) {
                    Text(
                        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                            .format(Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val routineId = workout.routineId
                    val routineName = uiState.routineName
                    if (routineId != null && routineName != null) {
                        Text(
                            routineName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.xxs).clickable { onRoutineClick(routineId) },
                        )
                    }
                    if (!workout.notes.isNullOrBlank()) {
                        Text(workout.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DetailStatCell(stringResource(R.string.summary_duration), formatDetailDuration(uiState.durationSeconds))
                        DetailStatCell(stringResource(R.string.summary_volume), formatDetailVolume(uiState.volumeKg))
                        DetailStatCell(stringResource(R.string.summary_sets), uiState.completedSetCount.toString())
                        if (uiState.hasRecords) DetailStatCell(stringResource(R.string.summary_prs_header), "", icon = LogEzIcons.PersonalRecord)
                    }
                }
            }

            items(items = uiState.exerciseBlocks, key = { it.workoutExercise.id }) { block ->
                ExerciseBlockCard(block, onExerciseClick)
            }
        }
    }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(stringResource(R.string.workout_resume_title)) },
            text = { Text(stringResource(R.string.workout_resume_body)) },
            confirmButton = {
                TextButton(onClick = { showResumeDialog = false; conflictingWorkoutId?.let(startSession) }) {
                    Text(stringResource(R.string.workout_resume_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    scope.launch { startSession(viewModel.discardInProgressAndStartCopy()) }
                }) { Text(stringResource(R.string.workout_resume_discard_action)) }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_detail_delete_title)) },
            text = { Text(stringResource(R.string.history_detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(onBack) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailStatCell(label: String, value: String, icon: ImageVector? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Text(value, style = LogEzMono.dataLarge)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseBlockCard(block: DetailExerciseBlock, onExerciseClick: (String) -> Unit) {
    val supersetColor = block.workoutExercise.supersetGroup?.let { SupersetPalette[it % SupersetPalette.size] }
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (supersetColor != null) {
                Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(supersetColor))
            }
            Column(modifier = Modifier.padding(Spacing.md)) {
                val exercise = block.exercise
                Text(
                    exercise?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = if (exercise != null) Modifier.clickable { onExerciseClick(exercise.id) } else Modifier,
                )
                if (!block.workoutExercise.notes.isNullOrBlank()) {
                    Text(
                        block.workoutExercise.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
                block.sets.forEachIndexed { index, set ->
                    DetailSetRowView(index + 1, set, block.exercise?.exerciseType)
                }
            }
        }
    }
}

@Composable
private fun DetailSetRowView(position: Int, set: DetailSetRow, exerciseType: ExerciseType?) {
    val (badgeLabel, badgeColor) = when (set.setType) {
        SetType.NORMAL -> position.toString() to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> "W" to Warning500
        SetType.FAILURE -> "F" to Danger500
        SetType.DROPSET -> "D" to SupersetPalette[4]
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (set.setType == SetType.NORMAL) Color.Transparent else badgeColor.copy(alpha = 0.15f),
        ) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Text(badgeLabel, color = badgeColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            formatDetailSetValue(position, set, exerciseType),
            style = LogEzMono.dataMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.sm).weight(1f),
        )
        if (set.pr != null) {
            Icon(
                LogEzIcons.PersonalRecord,
                contentDescription = stringResource(set.pr.prType.labelRes()),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatDetailSetValue(position: Int, set: DetailSetRow, exerciseType: ExerciseType?): String {
    if (!set.isCompleted) return "—"
    val parts = mutableListOf<String>()
    when (exerciseType) {
        ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_WEIGHTED, ExerciseType.BODYWEIGHT_ASSISTED -> {
            set.weightKg?.let { parts.add("${formatDetailNum(it)}kg") }
            set.reps?.let { parts.add("$it reps") }
        }
        ExerciseType.REPS_ONLY -> set.reps?.let { parts.add("$it reps") }
        ExerciseType.DURATION, ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> {
            set.durationSeconds?.let { parts.add(formatDetailMmSs(it)) }
            set.customMetric?.let { parts.add(formatDetailNum(it)) }
        }
        ExerciseType.WEIGHT_DURATION -> {
            set.weightKg?.let { parts.add("${formatDetailNum(it)}kg") }
            set.durationSeconds?.let { parts.add(formatDetailMmSs(it)) }
        }
        ExerciseType.DISTANCE_DURATION -> {
            set.distanceMeters?.let { parts.add("${formatDetailNum(it)}m") }
            set.durationSeconds?.let { parts.add(formatDetailMmSs(it)) }
        }
        ExerciseType.WEIGHT_DISTANCE -> {
            set.weightKg?.let { parts.add("${formatDetailNum(it)}kg") }
            set.distanceMeters?.let { parts.add("${formatDetailNum(it)}m") }
        }
        null -> Unit
    }
    set.rpe?.let { parts.add("@$it") }
    return if (parts.isEmpty()) "—" else "Set $position: ${parts.joinToString(" · ")}"
}

private fun formatDetailNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun formatDetailMmSs(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatDetailDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatDetailVolume(kg: Double): String =
    if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(kg)
