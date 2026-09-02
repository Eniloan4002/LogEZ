package com.enil.logez.feature.routines

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.Danger500
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SetTable
import com.enil.logez.core.designsystem.SupersetPalette
import com.enil.logez.core.designsystem.Warning500
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.TargetField
import com.enil.logez.core.domain.model.targetFields

/**
 * One `routine_exercises` card (PHASE2_PLAN.md §5.1.2): header, notes, rest timer, set table.
 * [isCircuit] (M11) locks per-exercise structure to the routine-level round count: the SET column
 * reads ROUND, per-exercise add/remove-set and superset controls disappear, and WARMUP leaves the
 * set-type menu (a warm-up row would break the row-index == round invariant).
 */
@Composable
internal fun RoutineExerciseCard(
    exercise: RoutineExerciseDraft,
    isCircuit: Boolean,
    defaultRestTimerSeconds: Int,
    reorderModeActive: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    supersetSelectionActive: Boolean,
    isSupersetSource: Boolean,
    viewModel: RoutineBuilderViewModel,
    onExerciseClick: () -> Unit,
    onOpenReplacePicker: () -> Unit,
    onRestTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val supersetColor = exercise.supersetGroup?.let { SupersetPalette[it % SupersetPalette.size] }

    LogEzCard(
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // No separate click target during superset selection — a tap anywhere on the
                    // card (including the name) must confirm the pairing, not navigate away.
                    modifier = Modifier.weight(1f).let { m ->
                        if (supersetSelectionActive) m else m.clickable(onClick = onExerciseClick)
                    },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_reorder)) }, onClick = { menuExpanded = false; viewModel.toggleReorderMode() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_replace)) }, onClick = { menuExpanded = false; onOpenReplacePicker() })
                        // M11: no superset controls inside a circuit — the circuit IS the sequence.
                        if (!isCircuit) {
                            if (exercise.supersetGroup == null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_add_to_superset)) }, onClick = { menuExpanded = false; viewModel.startSupersetSelection(exercise.id) })
                            } else {
                                DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_from_superset)) }, onClick = { menuExpanded = false; viewModel.removeFromSuperset(exercise.id) })
                            }
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_exercise)) }, onClick = { menuExpanded = false; viewModel.removeExercise(exercise.id) })
                    }
                }
            }

            var notesText by remember(exercise.id) { mutableStateOf(exercise.notes.orEmpty()) }
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it; viewModel.updateExerciseNotes(exercise.id, it) },
                placeholder = { Text(stringResource(R.string.routine_builder_notes_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )

            val restLabel = when (val seconds = exercise.restTimerSeconds) {
                null -> stringResource(R.string.rest_timer_default_option, formatMmSs(defaultRestTimerSeconds))
                0 -> stringResource(R.string.rest_timer_off_option)
                else -> formatMmSs(seconds)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRestTimerClick).padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.routine_builder_rest_timer_label, restLabel), style = MaterialTheme.typography.bodyMedium)
            }

            SetTable(exercise = exercise, isCircuit = isCircuit, viewModel = viewModel)

            // M11: per-exercise + Add Set is meaningless in a circuit — the routine-level rounds
            // stepper is the only way set counts change, keeping every exercise in lockstep.
            if (!isCircuit) {
                TextButton(onClick = { viewModel.addSet(exercise.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
                    Text(stringResource(R.string.routine_builder_add_set))
                }
            }
        }
    }
}

@Composable
private fun SetTable(exercise: RoutineExerciseDraft, isCircuit: Boolean, viewModel: RoutineBuilderViewModel) {
    val fields = exercise.exerciseType.targetFields()
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(stringResource(if (isCircuit) R.string.routine_builder_col_round else R.string.routine_builder_col_set), width = SetTable.setCell, textAlign = TextAlign.Center)
            if (TargetField.WEIGHT in fields) HeaderCell(weightHeaderLabel(exercise.exerciseType), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (TargetField.REPS in fields) {
                HeaderCell(
                    stringResource(R.string.routine_builder_col_reps),
                    modifier = Modifier.weight(1f).clickable { viewModel.toggleRepRangeMode(exercise.id) },
                    textAlign = TextAlign.Center,
                )
            }
            if (TargetField.DURATION in fields) HeaderCell(stringResource(R.string.routine_builder_col_time), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (TargetField.DISTANCE in fields) HeaderCell(stringResource(R.string.routine_builder_col_distance), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.width(SetTable.checkCell))
        }
        exercise.sets.forEachIndexed { index, set ->
            SetRow(
                index = index,
                set = set,
                fields = fields,
                isCircuit = isCircuit,
                isRepRangeMode = exercise.isRepRangeMode,
                onSetTypeChange = { type -> viewModel.updateSetType(exercise.id, set.id, type) },
                onRemove = { viewModel.removeSet(exercise.id, set.id) },
                onWeightChange = { viewModel.updateWeight(exercise.id, set.id, it) },
                onRepsChange = { viewModel.updateReps(exercise.id, set.id, it) },
                onRepRangeMinChange = { viewModel.updateRepRangeMin(exercise.id, set.id, it) },
                onRepRangeMaxChange = { viewModel.updateRepRangeMax(exercise.id, set.id, it) },
                onDurationChange = { viewModel.updateDuration(exercise.id, set.id, it) },
                onDistanceChange = { viewModel.updateDistance(exercise.id, set.id, it) },
            )
        }
    }
}

@Composable
private fun HeaderCell(label: String, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp? = null, textAlign: TextAlign? = null) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        // Cells are sized so every label fits whole (SetTable); backstop so a future regression
        // ellipsizes instead of breaking mid-word.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (width != null) modifier.width(width) else modifier,
    )
}

@Composable
private fun SetRow(
    index: Int,
    set: RoutineSetDraft,
    fields: Set<TargetField>,
    isCircuit: Boolean,
    isRepRangeMode: Boolean,
    onSetTypeChange: (SetType) -> Unit,
    onRemove: () -> Unit,
    onWeightChange: (Double?) -> Unit,
    onRepsChange: (Int?) -> Unit,
    onRepRangeMinChange: (Int?) -> Unit,
    onRepRangeMaxChange: (Int?) -> Unit,
    onDurationChange: (Int?) -> Unit,
    onDistanceChange: (Double?) -> Unit,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(SetTable.setCell), contentAlignment = Alignment.Center) {
            SetBadge(setType = set.setType, position = index + 1, onClick = { typeMenuExpanded = true })
            DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_normal)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.NORMAL) })
                // M11: no WARMUP row inside a circuit (breaks row-index == round), and no per-row
                // delete — rounds are removed for every exercise at once via the stepper.
                if (!isCircuit) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.set_type_warmup)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.WARMUP) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_failure)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.FAILURE) })
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_dropset)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.DROPSET) })
                if (!isCircuit) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { typeMenuExpanded = false; onRemove() })
                }
            }
        }
        if (TargetField.WEIGHT in fields) {
            NumberCell(value = set.targetWeightKg, onValueChange = onWeightChange, modifier = Modifier.weight(1f))
        }
        if (TargetField.REPS in fields) {
            if (isRepRangeMode) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    IntCell(value = set.targetRepRangeMin, onValueChange = onRepRangeMinChange, modifier = Modifier.weight(1f))
                    IntCell(value = set.targetRepRangeMax, onValueChange = onRepRangeMaxChange, modifier = Modifier.weight(1f))
                }
            } else {
                IntCell(value = set.targetReps, onValueChange = onRepsChange, modifier = Modifier.weight(1f))
            }
        }
        if (TargetField.DURATION in fields) {
            IntCell(value = set.targetDurationSeconds, onValueChange = onDurationChange, modifier = Modifier.weight(1f))
        }
        if (TargetField.DISTANCE in fields) {
            NumberCell(value = set.targetDistanceMeters, onValueChange = onDistanceChange, modifier = Modifier.weight(1f))
        }
        if (isCircuit) {
            // Keep the column grid aligned with the regular layout's trailing delete slot.
            Spacer(modifier = Modifier.width(SetTable.checkCell))
        } else {
            IconButton(onClick = onRemove, modifier = Modifier.width(SetTable.checkCell)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(16.dp))
            }
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
private fun NumberCell(value: Double?, onValueChange: (Double?) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value?.let { formatTargetNumber(it) }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            onValueChange(new.toDoubleOrNull())
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.padding(horizontal = Spacing.xxs),
    )
}

@Composable
private fun IntCell(value: Int?, onValueChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            onValueChange(new.toIntOrNull())
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.padding(horizontal = Spacing.xxs),
    )
}

private fun formatTargetNumber(value: Double): String = com.enil.logez.core.designsystem.formatTargetNumber(value)

@Composable
private fun weightHeaderLabel(exerciseType: ExerciseType): String = when (exerciseType) {
    ExerciseType.BODYWEIGHT_WEIGHTED -> stringResource(R.string.routine_builder_col_weight_added)
    ExerciseType.BODYWEIGHT_ASSISTED -> stringResource(R.string.routine_builder_col_weight_assisted)
    else -> stringResource(R.string.routine_builder_col_weight)
}
