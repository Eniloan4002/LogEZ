package com.enil.logez.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.HeatmapGrid
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import kotlinx.coroutines.launch

private sealed class ReorderTarget {
    object Folders : ReorderTarget()
    data class Routines(val folderId: String?) : ReorderTarget()
}

/** What "Discard & start new" should start, once the in-progress conflict is resolved (§5.1.1 edge case). */
private sealed class PendingStart {
    object Empty : PendingStart()
    data class Routine(val routineId: String) : PendingStart()
}

/** PHASE2_PLAN.md §5.1.1 — folders + routines home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTabScreen(
    onRoutineClick: (routineId: String) -> Unit,
    onCreateRoutine: (folderId: String?) -> Unit,
    onEditRoutine: (routineId: String) -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    viewModel: WorkoutTabViewModel = hiltViewModel(),
    goalsViewModel: GoalsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val goalsUiState by goalsViewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(goalsViewModel::refresh)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var collapsedFolders by rememberSaveable { mutableStateOf(setOf<String>()) }
    var reorderTarget by remember { mutableStateOf<ReorderTarget?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<RoutineFolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<RoutineFolderEntity?>(null) }
    var deletingRoutineId by remember { mutableStateOf<String?>(null) }
    var movingRoutineId by remember { mutableStateOf<String?>(null) }
    var pendingStart by remember { mutableStateOf<PendingStart?>(null) }
    var inProgressWorkoutId by remember { mutableStateOf<String?>(null) }
    val startSession = rememberStartWorkoutSession(onNavigateToLogger)

    fun startEmpty() = scope.launch {
        when (val result = viewModel.startEmptyWorkout()) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { pendingStart = PendingStart.Empty; inProgressWorkoutId = result.workoutId }
        }
    }

    fun startRoutine(routineId: String) = scope.launch {
        when (val result = viewModel.startRoutine(routineId)) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { pendingStart = PendingStart.Routine(routineId); inProgressWorkoutId = result.workoutId }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workout_tab_title)) },
                actions = {
                    IconButton(onClick = { showCreateFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.workout_new_folder))
                    }
                    IconButton(onClick = { onCreateRoutine(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.workout_new_routine))
                    }
                },
            )
        },
    ) { padding ->
        // One scrollable list for the whole tab (Owner feedback: the heatmap/Goals/Start button
        // used to be a fixed, non-scrolling header sitting outside the folder/routine LazyColumn
        // — scrolling the routines left them permanently on screen, eating vertical space). Now
        // every section is a LazyColumn item, so the whole tab scrolls as one.
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                // M8c: progress heatmap at the very top, before any workout feature — a passive
                // overview, not something the user acts on, so it never competes with
                // Start/routines for the first tap.
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            stringResource(R.string.workout_heatmap_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HeatmapGrid(
                            countsByDate = uiState.heatmapCounts,
                            today = uiState.heatmapToday,
                            firstDayOfWeek = uiState.heatmapFirstDayOfWeek,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        )
                    }
                }
            }

            item {
                // M8d: Goals card, right below the heatmap — both are "progress" widgets, kept
                // together above the actionable Start/routines content.
                GoalsSection(
                    uiState = goalsUiState,
                    onCreateGoal = goalsViewModel::createGoal,
                    onDeleteGoal = goalsViewModel::deleteGoal,
                )
            }

            item {
                // M4b: the global WorkoutMiniBar (docked above the bottom tab bar on every tab,
                // §5.1.3) now covers "in-progress workout, tap to resume" — this tab's own banner
                // would just duplicate it whenever the user is actually on this tab.
                FilledTonalButton(
                    onClick = { startEmpty() },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                ) {
                    Text(stringResource(R.string.workout_start_empty))
                }
            }

            if (uiState.isLoading) return@LazyColumn

            if (uiState.folders.isEmpty() && uiState.rootRoutines.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.FitnessCenter,
                        title = stringResource(R.string.workout_empty_title),
                        subtitle = stringResource(R.string.workout_empty_subtitle),
                        ctaLabel = stringResource(R.string.workout_create_first_routine),
                        onCtaClick = { onCreateRoutine(null) },
                    )
                }
                return@LazyColumn
            }

            items(items = uiState.folders, key = { it.folder.id }) { section ->
                FolderHeaderRow(
                    folder = section.folder,
                    isCollapsed = section.folder.id in collapsedFolders,
                    isReordering = reorderTarget == ReorderTarget.Folders,
                    onToggleCollapse = {
                        collapsedFolders = if (section.folder.id in collapsedFolders) collapsedFolders - section.folder.id else collapsedFolders + section.folder.id
                    },
                    onRename = { renamingFolder = section.folder },
                    onAddRoutine = { onCreateRoutine(section.folder.id) },
                    onReorder = { reorderTarget = ReorderTarget.Folders },
                    onDelete = { deletingFolder = section.folder },
                    onMoveUp = {
                        val ids = uiState.folders.map { it.folder.id }.toMutableList()
                        val i = ids.indexOf(section.folder.id)
                        if (i > 0) { ids[i] = ids[i - 1].also { ids[i - 1] = ids[i] }; viewModel.reorderFolders(ids) }
                    },
                    onMoveDown = {
                        val ids = uiState.folders.map { it.folder.id }.toMutableList()
                        val i = ids.indexOf(section.folder.id)
                        if (i < ids.lastIndex) { ids[i] = ids[i + 1].also { ids[i + 1] = ids[i] }; viewModel.reorderFolders(ids) }
                    },
                )
                if (section.folder.id !in collapsedFolders) {
                    section.routines.forEach { card ->
                        RoutineCard(
                            card = card,
                            isReordering = reorderTarget == ReorderTarget.Routines(section.folder.id),
                            onClick = { onRoutineClick(card.routine.id) },
                            onStart = { startRoutine(card.routine.id) },
                            onEdit = { onEditRoutine(card.routine.id) },
                            onDuplicate = { scope.launch { viewModel.duplicateRoutine(card.routine.id) } },
                            onMove = { movingRoutineId = card.routine.id },
                            onReorder = { reorderTarget = ReorderTarget.Routines(section.folder.id) },
                            onDelete = { deletingRoutineId = card.routine.id },
                            onMoveUp = {
                                val ids = section.routines.map { it.routine.id }.toMutableList()
                                val i = ids.indexOf(card.routine.id)
                                if (i > 0) { ids[i] = ids[i - 1].also { ids[i - 1] = ids[i] }; viewModel.reorderRoutines(ids) }
                            },
                            onMoveDown = {
                                val ids = section.routines.map { it.routine.id }.toMutableList()
                                val i = ids.indexOf(card.routine.id)
                                if (i < ids.lastIndex) { ids[i] = ids[i + 1].also { ids[i + 1] = ids[i] }; viewModel.reorderRoutines(ids) }
                            },
                            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
                        )
                    }
                }
            }

            if (uiState.rootRoutines.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.workout_my_routines_header),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
                items(items = uiState.rootRoutines, key = { it.routine.id }) { card ->
                    RoutineCard(
                        card = card,
                        isReordering = reorderTarget == ReorderTarget.Routines(null),
                        onClick = { onRoutineClick(card.routine.id) },
                        onStart = { startRoutine(card.routine.id) },
                        onEdit = { onEditRoutine(card.routine.id) },
                        onDuplicate = { scope.launch { viewModel.duplicateRoutine(card.routine.id) } },
                        onMove = { movingRoutineId = card.routine.id },
                        onReorder = { reorderTarget = ReorderTarget.Routines(null) },
                        onDelete = { deletingRoutineId = card.routine.id },
                        onMoveUp = {
                            val ids = uiState.rootRoutines.map { it.routine.id }.toMutableList()
                            val i = ids.indexOf(card.routine.id)
                            if (i > 0) { ids[i] = ids[i - 1].also { ids[i - 1] = ids[i] }; viewModel.reorderRoutines(ids) }
                        },
                        onMoveDown = {
                            val ids = uiState.rootRoutines.map { it.routine.id }.toMutableList()
                            val i = ids.indexOf(card.routine.id)
                            if (i < ids.lastIndex) { ids[i] = ids[i + 1].also { ids[i + 1] = ids[i] }; viewModel.reorderRoutines(ids) }
                        },
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
            }

            if (reorderTarget != null) {
                item {
                    TextButton(onClick = { reorderTarget = null }, modifier = Modifier.padding(Spacing.md)) {
                        Text(stringResource(R.string.routine_builder_reorder_done))
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        TextInputDialog(
            title = stringResource(R.string.workout_new_folder),
            initialValue = "",
            onConfirm = { viewModel.createFolder(it); showCreateFolder = false },
            onDismiss = { showCreateFolder = false },
        )
    }
    renamingFolder?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.workout_rename_folder),
            initialValue = folder.name,
            onConfirm = { viewModel.renameFolder(folder.id, it); renamingFolder = null },
            onDismiss = { renamingFolder = null },
        )
    }
    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(stringResource(R.string.workout_delete_folder_title)) },
            text = { Text(stringResource(R.string.workout_delete_folder_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFolder(folder); deletingFolder = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deletingRoutineId?.let { routineId ->
        AlertDialog(
            onDismissRequest = { deletingRoutineId = null },
            title = { Text(stringResource(R.string.workout_delete_routine_title)) },
            text = { Text(stringResource(R.string.workout_delete_routine_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRoutine(routineId); deletingRoutineId = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingRoutineId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    movingRoutineId?.let { routineId ->
        MoveToFolderDialog(
            folders = uiState.folders.map { it.folder },
            onSelect = { folderId -> viewModel.moveRoutineToFolder(routineId, folderId); movingRoutineId = null },
            onDismiss = { movingRoutineId = null },
        )
    }

    pendingStart?.let { pending ->
        val existingId = inProgressWorkoutId
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text(stringResource(R.string.workout_resume_title)) },
            text = { Text(stringResource(R.string.workout_resume_body)) },
            confirmButton = {
                TextButton(onClick = { pendingStart = null; existingId?.let(startSession) }) {
                    Text(stringResource(R.string.workout_resume_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingStart = null
                    scope.launch {
                        val newId = when (pending) {
                            is PendingStart.Empty -> viewModel.discardInProgressAndStartEmpty()
                            is PendingStart.Routine -> viewModel.discardInProgressAndStartRoutine(pending.routineId)
                        }
                        startSession(newId)
                    }
                }) {
                    Text(stringResource(R.string.workout_resume_discard_action))
                }
            },
        )
    }
}

@Composable
private fun FolderHeaderRow(
    folder: RoutineFolderEntity,
    isCollapsed: Boolean,
    isReordering: Boolean,
    onToggleCollapse: () -> Unit,
    onRename: () -> Unit,
    onAddRoutine: () -> Unit,
    onReorder: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isReordering, onClick = onToggleCollapse).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, contentDescription = null)
        Text(folder.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = Spacing.xs))
        if (isReordering) {
            IconButton(onClick = onMoveUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.workout_move_up)) }
            IconButton(onClick = onMoveDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.workout_move_down)) }
        } else {
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.workout_add_routine_to_folder)) }, onClick = { menuExpanded = false; onAddRoutine() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.workout_reorder)) }, onClick = { menuExpanded = false; onReorder() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    card: RoutineCardModel,
    isReordering: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onReorder: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth().clickable(enabled = !isReordering, onClick = onClick)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(card.routine.name, style = MaterialTheme.typography.titleMedium)
            if (card.exercisePreview.isNotBlank()) {
                Text(card.exercisePreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xxs))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStart) { Text(stringResource(R.string.workout_start_routine)) }
                Box(modifier = Modifier.weight(1f))
                if (isReordering) {
                    IconButton(onClick = onMoveUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.workout_move_up)) }
                    IconButton(onClick = onMoveDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.workout_move_down)) }
                } else {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menuExpanded = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menuExpanded = false; onDuplicate() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.workout_move_to_folder)) }, onClick = { menuExpanded = false; onMove() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.workout_reorder)) }, onClick = { menuExpanded = false; onReorder() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuExpanded = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(title: String, initialValue: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MoveToFolderDialog(folders: List<RoutineFolderEntity>, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_move_to_folder)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.workout_my_routines_header),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = Spacing.sm),
                )
                folders.forEach { folder ->
                    Text(folder.name, modifier = Modifier.fillMaxWidth().clickable { onSelect(folder.id) }.padding(vertical = Spacing.sm))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
