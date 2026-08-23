package com.enil.logez.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Danger500
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SupersetPalette
import com.enil.logez.core.designsystem.Warning500
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.feature.routines.TargetField
import com.enil.logez.feature.routines.targetFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** One `workout_exercises` card (PHASE2_PLAN.md §5.1.3): header, notes, PREVIOUS-aware set table with check-off. */
@Composable
internal fun WorkoutExerciseCard(
    exercise: WorkoutExerciseUiModel,
    reorderModeActive: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    supersetSelectionActive: Boolean,
    isSupersetSource: Boolean,
    viewModel: WorkoutLoggerViewModel,
    onExerciseClick: () -> Unit,
    onOpenReplacePicker: () -> Unit,
    showRestTimer: Boolean = false,
    restRemainingMillisFlow: Flow<Long?> = emptyFlow(),
    onRestAdjust: (Int) -> Unit = {},
    onRestSkip: () -> Unit = {},
    inlineTimerEnabled: Boolean = true,
    inlineTimerSetId: String? = null,
    inlineTimerSecondsFlow: Flow<Int?> = emptyFlow(),
    onStartInlineTimer: (setId: String) -> Unit = {},
    onStopInlineTimer: (setId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val supersetColor = exercise.supersetGroup?.let { SupersetPalette[it % SupersetPalette.size] }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm)
            .let { m -> if (supersetSelectionActive && !isSupersetSource) m.clickable { viewModel.confirmSupersetTarget(exercise.id) } else m },
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            if (supersetColor != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
                    Box(modifier = Modifier.size(10.dp).background(supersetColor, CircleShape))
                    Text(
                        stringResource(R.string.routine_builder_superset_chip),
                        style = MaterialTheme.typography.labelSmall,
                        color = supersetColor,
                        modifier = Modifier.padding(start = Spacing.xxs),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reorderModeActive) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.workout_move_up))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.workout_move_down))
                    }
                }
                Text(
                    exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).let { m -> if (supersetSelectionActive) m else m.clickable(onClick = onExerciseClick) },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_reorder)) }, onClick = { menuExpanded = false; viewModel.toggleReorderMode() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_replace)) }, onClick = { menuExpanded = false; onOpenReplacePicker() })
                        if (exercise.supersetGroup == null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_add_to_superset)) }, onClick = { menuExpanded = false; viewModel.startSupersetSelection(exercise.id) })
                        } else {
                            DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_from_superset)) }, onClick = { menuExpanded = false; viewModel.removeFromSuperset(exercise.id) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_exercise)) }, onClick = { menuExpanded = false; viewModel.removeExercise(exercise.id) })
                    }
                }
            }

            var notesText by remember(exercise.id) { mutableStateOf(exercise.notes) }
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it; viewModel.updateExerciseNotes(exercise.id, it) },
                placeholder = { Text(stringResource(R.string.routine_builder_notes_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )

            if (showRestTimer) {
                RestTimerBar(remainingMillisFlow = restRemainingMillisFlow, onMinus15 = { onRestAdjust(-15) }, onPlus15 = { onRestAdjust(15) }, onSkip = onRestSkip)
            }

            SetTable(
                exercise = exercise,
                viewModel = viewModel,
                inlineTimerEnabled = inlineTimerEnabled,
                inlineTimerSetId = inlineTimerSetId,
                inlineTimerSecondsFlow = inlineTimerSecondsFlow,
                onStartInlineTimer = onStartInlineTimer,
                onStopInlineTimer = onStopInlineTimer,
            )

            TextButton(onClick = { viewModel.addSet(exercise.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
                Text(stringResource(R.string.routine_builder_add_set))
            }
        }
    }
}

/**
 * §5.1.4 — countdown bar below the notes area, inside the triggering exercise's card. Leaf
 * composable (spine rule): only this bar recomposes every second, driven by its own flow
 * collection — the parent Card/Screen never reads a per-second value.
 */
@Composable
private fun RestTimerBar(remainingMillisFlow: Flow<Long?>, onMinus15: () -> Unit, onPlus15: () -> Unit, onSkip: () -> Unit) {
    val remainingMillis by remainingMillisFlow.collectAsStateWithLifecycle(null)
    val remainingSeconds = remainingMillis?.let { (it + 999) / 1000 } ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${stringResource(R.string.workout_rest_timer_label)} ${"%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMinus15) { Text(stringResource(R.string.workout_rest_timer_minus_15)) }
            TextButton(onClick = onPlus15) { Text(stringResource(R.string.workout_rest_timer_plus_15)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.workout_rest_timer_skip)) }
        }
        val fraction = ((remainingSeconds.coerceAtMost(300)) / 300f).coerceIn(0f, 1f)
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SetTable(
    exercise: WorkoutExerciseUiModel,
    viewModel: WorkoutLoggerViewModel,
    inlineTimerEnabled: Boolean,
    inlineTimerSetId: String?,
    inlineTimerSecondsFlow: Flow<Int?>,
    onStartInlineTimer: (setId: String) -> Unit,
    onStopInlineTimer: (setId: String) -> Unit,
) {
    val fields = exercise.exerciseType.targetFields()
    val showInlineTimer = inlineTimerEnabled && TargetField.DURATION in fields
    val showCustomMetric = exercise.exerciseType == ExerciseType.FLOORS_DURATION || exercise.exerciseType == ExerciseType.STEPS_DURATION
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(stringResource(R.string.routine_builder_col_set), width = 36.dp)
            HeaderCell(stringResource(R.string.workout_col_previous), width = 76.dp)
            if (showCustomMetric) HeaderCell(stringResource(R.string.workout_col_custom_metric), modifier = Modifier.weight(1f))
            if (TargetField.WEIGHT in fields) HeaderCell(stringResource(R.string.routine_builder_col_weight), modifier = Modifier.weight(1f))
            if (TargetField.REPS in fields) HeaderCell(stringResource(R.string.routine_builder_col_reps), modifier = Modifier.weight(1f))
            if (TargetField.DURATION in fields) HeaderCell(stringResource(R.string.routine_builder_col_time), modifier = Modifier.weight(1f))
            if (TargetField.DISTANCE in fields) HeaderCell(stringResource(R.string.routine_builder_col_distance), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(40.dp))
        }
        exercise.sets.forEachIndexed { index, set ->
            Column {
                SetRow(
                    index = index,
                    set = set,
                    fields = fields,
                    showCustomMetric = showCustomMetric,
                    onSetTypeChange = { type -> viewModel.updateSetType(exercise.id, set.id, type) },
                    onRemove = { viewModel.removeSet(exercise.id, set.id) },
                    onWeightChange = { viewModel.updateWeight(exercise.id, set.id, it) },
                    onRepsChange = { viewModel.updateReps(exercise.id, set.id, it) },
                    onDurationChange = { viewModel.updateDuration(exercise.id, set.id, it) },
                    onDistanceChange = { viewModel.updateDistance(exercise.id, set.id, it) },
                    onCustomMetricChange = { viewModel.updateCustomMetric(exercise.id, set.id, it) },
                    onToggleCheck = { viewModel.toggleCheck(exercise.id, set.id) },
                    showInlineTimer = showInlineTimer,
                    inlineTimerRunning = inlineTimerSetId == set.id,
                    inlineTimerSecondsFlow = inlineTimerSecondsFlow,
                    onStartInlineTimer = { onStartInlineTimer(set.id) },
                    onStopInlineTimer = { onStopInlineTimer(set.id) },
                )
                if (set.failureError) {
                    Text(
                        stringResource(R.string.workout_failure_error),
                        color = Danger500,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 44.dp, bottom = Spacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(label: String, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp? = null) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (width != null) modifier.width(width) else modifier,
    )
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkoutSetUiModel,
    fields: Set<TargetField>,
    showCustomMetric: Boolean,
    onSetTypeChange: (SetType) -> Unit,
    onRemove: () -> Unit,
    onWeightChange: (Double?) -> Unit,
    onRepsChange: (Int?) -> Unit,
    onDurationChange: (Int?) -> Unit,
    onDistanceChange: (Double?) -> Unit,
    onCustomMetricChange: (Double?) -> Unit,
    onToggleCheck: () -> Unit,
    showInlineTimer: Boolean = false,
    inlineTimerRunning: Boolean = false,
    inlineTimerSecondsFlow: Flow<Int?> = emptyFlow(),
    onStartInlineTimer: () -> Unit = {},
    onStopInlineTimer: () -> Unit = {},
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val rowBackground = if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent

    Row(
        modifier = Modifier.fillMaxWidth().background(rowBackground).padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(36.dp)) {
            SetBadge(setType = set.setType, position = index + 1, onClick = { typeMenuExpanded = true })
            DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_normal)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.NORMAL) })
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_warmup)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.WARMUP) })
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_failure)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.FAILURE) })
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_dropset)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.DROPSET) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { typeMenuExpanded = false; onRemove() })
            }
        }
        Text(
            set.previousLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        if (showCustomMetric) {
            NumberCell(value = set.customMetric, onValueChange = onCustomMetricChange, enabled = !set.isCompleted, modifier = Modifier.weight(1f))
        }
        if (TargetField.WEIGHT in fields) {
            NumberCell(value = set.weightKg, onValueChange = onWeightChange, enabled = !set.isCompleted, modifier = Modifier.weight(1f))
        }
        if (TargetField.REPS in fields) {
            IntCell(value = set.reps, onValueChange = onRepsChange, enabled = !set.isCompleted, modifier = Modifier.weight(1f))
        }
        if (TargetField.DURATION in fields) {
            // Leaf-scoped (spine rule): only collected/ticking while this exact row is the running inline timer.
            val liveInlineSeconds by (if (inlineTimerRunning) inlineTimerSecondsFlow else emptyFlow()).collectAsStateWithLifecycle(null)
            IntCell(
                value = if (inlineTimerRunning) liveInlineSeconds else set.durationSeconds,
                onValueChange = onDurationChange,
                enabled = !set.isCompleted && !inlineTimerRunning,
                modifier = Modifier.weight(1f),
            )
            if (showInlineTimer && !set.isCompleted) {
                IconButton(onClick = if (inlineTimerRunning) onStopInlineTimer else onStartInlineTimer, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (inlineTimerRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (inlineTimerRunning) R.string.workout_inline_timer_stop else R.string.workout_inline_timer_start),
                    )
                }
            }
        }
        if (TargetField.DISTANCE in fields) {
            NumberCell(value = set.distanceMeters, onValueChange = onDistanceChange, enabled = !set.isCompleted, modifier = Modifier.weight(1f))
        }
        IconButton(onClick = onToggleCheck, modifier = Modifier.width(40.dp)) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.workout_check_set),
                tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetBadge(setType: SetType, position: Int, onClick: () -> Unit) {
    val (label, color) = when (setType) {
        SetType.NORMAL -> position.toString() to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> "W" to Warning500
        SetType.FAILURE -> "F" to Danger500
        SetType.DROPSET -> "D" to SupersetPalette[4]
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (setType == SetType.NORMAL) Color.Transparent else color.copy(alpha = 0.15f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NumberCell(value: Double?, onValueChange: (Double?) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value?.let { formatTargetNumber(it) }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new -> text = new; onValueChange(new.toDoubleOrNull()) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.padding(horizontal = Spacing.xxs),
    )
}

@Composable
private fun IntCell(value: Int?, onValueChange: (Int?) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new -> text = new; onValueChange(new.toIntOrNull()) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.padding(horizontal = Spacing.xxs),
    )
}

private fun formatTargetNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
