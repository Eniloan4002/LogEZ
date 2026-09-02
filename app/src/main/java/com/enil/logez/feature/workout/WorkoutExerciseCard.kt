package com.enil.logez.feature.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.SetTable
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SupersetPalette
import com.enil.logez.core.designsystem.Warning500
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.RpeScale
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.TargetField
import com.enil.logez.core.domain.model.targetFields
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
    callbacks: WorkoutCallbacks,
    onExerciseClick: () -> Unit,
    onOpenReplacePicker: () -> Unit,
    rpeTrackingEnabled: Boolean = false,
    onRpeChange: (setId: String, rpe: Double?) -> Unit = { _, _ -> },
    showRestTimer: Boolean = false,
    restRemainingMillisFlow: Flow<Long?> = emptyFlow(),
    onRestAdjust: (Int) -> Unit = {},
    onRestSkip: () -> Unit = {},
    inlineTimerEnabled: Boolean = true,
    inlineTimerSetId: String? = null,
    inlineTimerSecondsFlow: Flow<Int?> = emptyFlow(),
    onStartInlineTimer: (setId: String) -> Unit = {},
    onStopInlineTimer: (setId: String) -> Unit = {},
    isEditMode: Boolean = false,
    plateCalculator: PlateCalculatorConfig = PlateCalculatorConfig(),
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val supersetColor = exercise.supersetGroup?.let { SupersetPalette[it % SupersetPalette.size] }

    LogEzCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm)
            .let { m -> if (supersetSelectionActive && !isSupersetSource) m.clickable { callbacks.onConfirmSupersetTarget(exercise.id) } else m },
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
                    modifier = Modifier.weight(1f).let { m -> if (supersetSelectionActive) m else m.clickable(onClick = onExerciseClick) },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_reorder)) }, onClick = { menuExpanded = false; callbacks.onToggleReorderMode() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_replace)) }, onClick = { menuExpanded = false; onOpenReplacePicker() })
                        if (exercise.supersetGroup == null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_add_to_superset)) }, onClick = { menuExpanded = false; callbacks.onStartSupersetSelection(exercise.id) })
                        } else {
                            DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_from_superset)) }, onClick = { menuExpanded = false; callbacks.onRemoveFromSuperset(exercise.id) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_exercise)) }, onClick = { menuExpanded = false; callbacks.onRemoveExercise(exercise.id) })
                    }
                }
            }

            var notesText by remember(exercise.id) { mutableStateOf(exercise.notes) }
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it; callbacks.onUpdateNotes(exercise.id, it) },
                placeholder = { Text(stringResource(R.string.routine_builder_notes_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )

            if (showRestTimer) {
                RestTimerBar(remainingMillisFlow = restRemainingMillisFlow, onMinus15 = { onRestAdjust(-15) }, onPlus15 = { onRestAdjust(15) }, onSkip = onRestSkip)
            }

            SetTable(
                exercise = exercise,
                callbacks = callbacks,
                inlineTimerEnabled = inlineTimerEnabled,
                inlineTimerSetId = inlineTimerSetId,
                inlineTimerSecondsFlow = inlineTimerSecondsFlow,
                onStartInlineTimer = onStartInlineTimer,
                onStopInlineTimer = onStopInlineTimer,
                isEditMode = isEditMode,
                rpeTrackingEnabled = rpeTrackingEnabled,
                onRpeChange = onRpeChange,
                plateCalculator = plateCalculator,
            )

            TextButton(onClick = { callbacks.onAddSet(exercise.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
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
internal fun RestTimerBar(remainingMillisFlow: Flow<Long?>, onMinus15: () -> Unit, onPlus15: () -> Unit, onSkip: () -> Unit) {
    val remainingMillis by remainingMillisFlow.collectAsStateWithLifecycle(null)
    val remainingSeconds = remainingMillis?.let { (it + 999) / 1000 } ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${stringResource(R.string.workout_rest_timer_label)} ${"%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)}",
                style = LogEzMono.dataMedium,
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
    callbacks: WorkoutCallbacks,
    inlineTimerEnabled: Boolean,
    inlineTimerSetId: String?,
    inlineTimerSecondsFlow: Flow<Int?>,
    onStartInlineTimer: (setId: String) -> Unit,
    onStopInlineTimer: (setId: String) -> Unit,
    isEditMode: Boolean,
    rpeTrackingEnabled: Boolean,
    onRpeChange: (setId: String, rpe: Double?) -> Unit,
    plateCalculator: PlateCalculatorConfig = PlateCalculatorConfig(),
) {
    val fields = exercise.exerciseType.targetFields()
    val showInlineTimer = inlineTimerEnabled && TargetField.DURATION in fields
    val showCustomMetric = exercise.exerciseType == ExerciseType.FLOORS_DURATION || exercise.exerciseType == ExerciseType.STEPS_DURATION
    // §5.1.7: "the RPE column appears only when the setting is on and only for rep-based types" —
    // never for the routine builder (which shares none of this UI) and never for duration/distance
    // types, where RPE doesn't apply.
    val showRpe = rpeTrackingEnabled && TargetField.REPS in fields
    // §5.1.5: the Plate Calculator affordance exists only for BARBELL exercises with the setting
    // on — "assisted/weighted bodyweight exercises never show the button (equipment ≠ BARBELL)".
    val showPlateCalculator = plateCalculator.enabled && exercise.equipment == Equipment.BARBELL && TargetField.WEIGHT in fields
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(stringResource(R.string.routine_builder_col_set), width = SetTable.setCell, textAlign = TextAlign.Center)
            HeaderCell(stringResource(R.string.workout_col_previous), width = SetTable.previousCell)
            if (showCustomMetric) HeaderCell(stringResource(R.string.workout_col_custom_metric), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (TargetField.WEIGHT in fields) HeaderCell(stringResource(R.string.routine_builder_col_weight), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            // Mirrors the row's trailing calculator button so the KG header stays over its cell.
            if (showPlateCalculator) Spacer(modifier = Modifier.width(SetTable.plateCalcCell))
            if (TargetField.REPS in fields) HeaderCell(stringResource(R.string.routine_builder_col_reps), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (TargetField.DURATION in fields) HeaderCell(stringResource(R.string.routine_builder_col_time), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (TargetField.DISTANCE in fields) HeaderCell(stringResource(R.string.routine_builder_col_distance), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (showRpe) HeaderCell(stringResource(R.string.workout_col_rpe), width = SetTable.rpeCell, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.width(SetTable.checkCell))
        }
        exercise.sets.forEachIndexed { index, set ->
            Column {
                SetRow(
                    index = index,
                    set = set,
                    fields = fields,
                    showCustomMetric = showCustomMetric,
                    onSetTypeChange = { type -> callbacks.onUpdateSetType(exercise.id, set.id, type) },
                    onRemove = { callbacks.onRemoveSet(exercise.id, set.id) },
                    onWeightChange = { callbacks.onUpdateWeight(exercise.id, set.id, it) },
                    onRepsChange = { callbacks.onUpdateReps(exercise.id, set.id, it) },
                    onDurationChange = { callbacks.onUpdateDuration(exercise.id, set.id, it) },
                    onDistanceChange = { callbacks.onUpdateDistance(exercise.id, set.id, it) },
                    onCustomMetricChange = { callbacks.onUpdateCustomMetric(exercise.id, set.id, it) },
                    onToggleCheck = { callbacks.onToggleCheck(exercise.id, set.id) },
                    showInlineTimer = showInlineTimer,
                    inlineTimerRunning = inlineTimerSetId == set.id,
                    inlineTimerSecondsFlow = inlineTimerSecondsFlow,
                    onStartInlineTimer = { onStartInlineTimer(set.id) },
                    onStopInlineTimer = { onStopInlineTimer(set.id) },
                    isEditMode = isEditMode,
                    showRpe = showRpe,
                    onRpeChange = { rpe -> onRpeChange(set.id, rpe) },
                    showPlateCalculator = showPlateCalculator,
                    plateCalculatorConfig = plateCalculator,
                )
                if (set.failureError) {
                    Text(
                        stringResource(R.string.workout_failure_error),
                        color = Danger500,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = SetTable.setCell, bottom = Spacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HeaderCell(label: String, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp? = null, textAlign: TextAlign? = null) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        // Cells are sized so every label fits whole (SetTable); this is the backstop that turns a
        // future regression into an ellipsis instead of a mid-word break ("PREVIO/US").
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (width != null) modifier.width(width) else modifier,
    )
}

@Composable
internal fun SetRow(
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
    isEditMode: Boolean = false,
    showRpe: Boolean = false,
    onRpeChange: (Double?) -> Unit = {},
    /** M11 circuit rows: WARMUP leaves the badge menu (breaks row-index == round) ... */
    allowWarmup: Boolean = true,
    /** ... and so does per-row Delete (rounds are removed whole via the round header). */
    allowDelete: Boolean = true,
    /** M17 §5.1.5: true only for BARBELL rows with the setting on; the caller's header row adds a matching spacer. */
    showPlateCalculator: Boolean = false,
    plateCalculatorConfig: PlateCalculatorConfig = PlateCalculatorConfig(),
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var showRpeSheet by remember { mutableStateOf(false) }
    var showPlateSheet by remember { mutableStateOf(false) }
    // Live logging locks a set's values once it is checked off — the check is the commit. Editing a
    // PAST workout inverts that: every set in a COMPLETED workout is checked, so the same rule
    // would make the whole point of edit mode (§5.1.10: "All values and structure are editable
    // exactly as in live logging") impossible — every field would be read-only.
    val fieldsEnabled = isEditMode || !set.isCompleted
    val rowBackground = if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent

    Row(
        modifier = Modifier.fillMaxWidth().background(rowBackground).padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same width as the header's ROUND/SET cell — a 36-vs-48 mismatch here shifted every
        // value cell 12dp off its header. Badge centered so the number sits under the label.
        Box(modifier = Modifier.width(SetTable.setCell), contentAlignment = Alignment.Center) {
            SetBadge(setType = set.setType, position = index + 1, onClick = { typeMenuExpanded = true })
            DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_normal)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.NORMAL) })
                if (allowWarmup) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.set_type_warmup)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.WARMUP) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_failure)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.FAILURE) })
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_dropset)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.DROPSET) })
                if (allowDelete) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { typeMenuExpanded = false; onRemove() })
                }
            }
        }
        Text(
            set.previousLabel,
            style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(SetTable.previousCell),
        )
        if (showCustomMetric) {
            NumberCell(value = set.customMetric, onValueChange = onCustomMetricChange, enabled = fieldsEnabled, modifier = Modifier.weight(1f))
        }
        if (TargetField.WEIGHT in fields) {
            NumberCell(value = set.weightKg, onValueChange = onWeightChange, enabled = fieldsEnabled, modifier = Modifier.weight(1f))
            if (showPlateCalculator) {
                // Trails the KG cell inside the same fixed width the header row spaces over, so
                // the M15 column alignment holds with or without the button.
                IconButton(
                    onClick = { showPlateSheet = true },
                    enabled = fieldsEnabled,
                    modifier = Modifier.size(SetTable.plateCalcCell),
                ) {
                    Icon(
                        Icons.Filled.Calculate,
                        contentDescription = stringResource(R.string.workout_plate_calc_open),
                        tint = if (fieldsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (TargetField.REPS in fields) {
            IntCell(value = set.reps, onValueChange = onRepsChange, enabled = fieldsEnabled, modifier = Modifier.weight(1f))
        }
        if (TargetField.DURATION in fields) {
            // Leaf-scoped (spine rule): only collected/ticking while this exact row is the running inline timer.
            val liveInlineSeconds by (if (inlineTimerRunning) inlineTimerSecondsFlow else emptyFlow()).collectAsStateWithLifecycle(null)
            IntCell(
                value = if (inlineTimerRunning) liveInlineSeconds else set.durationSeconds,
                onValueChange = onDurationChange,
                enabled = fieldsEnabled && !inlineTimerRunning,
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
            NumberCell(value = set.distanceMeters, onValueChange = onDistanceChange, enabled = fieldsEnabled, modifier = Modifier.weight(1f))
        }
        if (showRpe) {
            RpeCell(value = set.rpe, enabled = fieldsEnabled, onClick = { showRpeSheet = true }, modifier = Modifier.width(SetTable.rpeCell))
        }
        IconButton(onClick = onToggleCheck, modifier = Modifier.width(SetTable.checkCell)) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.workout_check_set),
                tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showRpeSheet) {
        RpePickerSheet(
            initialRpe = set.rpe,
            onDismiss = { showRpeSheet = false },
            onConfirm = { rpe -> onRpeChange(rpe); showRpeSheet = false },
        )
    }

    if (showPlateSheet) {
        PlateCalculatorSheet(
            initialWeightKg = set.weightKg,
            weightUnit = plateCalculatorConfig.weightUnit,
            equipment = plateCalculatorConfig.equipment,
            // "Use X kg" writes the closest ACHIEVED total into the set — same canonical-kg path
            // as typing into the cell, so it behaves identically in live and edit modes.
            onApply = onWeightChange,
            onDismiss = { showPlateSheet = false },
        )
    }
}

@Composable
internal fun SetBadge(setType: SetType, position: Int, onClick: () -> Unit) {
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
internal fun NumberCell(value: Double?, onValueChange: (Double?) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
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
internal fun IntCell(value: Int?, onValueChange: (Int?) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
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

private fun formatTargetNumber(value: Double): String = com.enil.logez.core.designsystem.formatTargetNumber(value)

/** §5.1.7 entry point: "tap the RPE cell". A small tappable pill, not a text field — RPE is never free text. */
@Composable
private fun RpeCell(value: Double?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (value != null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (value == null) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(
                value?.let { RpeScale.format(it) } ?: "—",
                style = LogEzMono.dataSmall.copy(
                    color = if (value != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * §5.1.7 "Log Set RPE": single-select row of the eight allowed values, a label + reserve-reps
 * description that updates with the selection, Clear (writes null — blank is a valid RPE, e.g.
 * warm-ups), and Done. Tapping a value only updates the pending selection so the description is
 * visible before committing; Clear and Done are the only actions that actually write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RpePickerSheet(initialRpe: Double?, onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var selected by remember { mutableStateOf(initialRpe) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(
                stringResource(R.string.workout_rpe_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(modifier = Modifier.padding(top = Spacing.md)) {
                items(items = RpeScale.VALUES, key = { it }) { rpe ->
                    FilterChip(
                        selected = selected == rpe,
                        onClick = { selected = rpe },
                        label = { Text(RpeScale.format(rpe)) },
                        modifier = Modifier.padding(end = Spacing.xs),
                    )
                }
            }
            Text(
                selected?.let { "${stringResource(R.string.workout_rpe_label_prefix)} ${RpeScale.format(it)} — ${RpeScale.reserveDescription(it)}" }
                    ?: stringResource(R.string.workout_rpe_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.workout_rpe_clear)) }
                Button(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}
