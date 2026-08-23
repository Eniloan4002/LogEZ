package com.enil.logez.feature.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import kotlinx.coroutines.launch

private enum class DetailTab { SUMMARY, HISTORY, HOW_TO }

private data class HistorySession(val workoutId: String, val workoutTitle: String, val sets: List<ExerciseHistoryEntry>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    onDuplicated: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(DetailTab.HISTORY.ordinal) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.exercise?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val exercise = uiState.exercise
                    if (exercise != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (exercise.isCustom) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_edit)) },
                                    onClick = { menuExpanded = false; onEdit(exercise.id) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { viewModel.duplicate()?.let(onDuplicated) }
                                },
                            )
                            if (exercise.isCustom) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = { menuExpanded = false; showDeleteConfirm = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == DetailTab.SUMMARY.ordinal,
                    onClick = { selectedTab = DetailTab.SUMMARY.ordinal },
                    text = { Text(stringResource(R.string.exercise_detail_tab_summary)) },
                )
                Tab(
                    selected = selectedTab == DetailTab.HISTORY.ordinal,
                    onClick = { selectedTab = DetailTab.HISTORY.ordinal },
                    text = { Text(stringResource(R.string.exercise_detail_tab_history)) },
                )
                Tab(
                    selected = selectedTab == DetailTab.HOW_TO.ordinal,
                    onClick = { selectedTab = DetailTab.HOW_TO.ordinal },
                    text = { Text(stringResource(R.string.exercise_detail_tab_how_to)) },
                )
            }

            when (DetailTab.entries[selectedTab]) {
                DetailTab.SUMMARY -> SummaryTabStub()
                DetailTab.HISTORY -> HistoryTab(entries = uiState.history)
                DetailTab.HOW_TO -> HowToTab(instructions = uiState.exercise?.instructions.orEmpty())
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.exercise_delete_confirm_title)) },
            text = { Text(stringResource(R.string.exercise_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(onDeleted) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** §6 checklist: Summary tab (charts, PRs, Set Records) is M6 — this milestone ships How-to + History only. */
@Composable
private fun SummaryTabStub() {
    EmptyState(
        icon = Icons.Filled.BarChart,
        title = stringResource(R.string.exercise_detail_summary_stub_title),
        subtitle = stringResource(R.string.exercise_detail_summary_stub_subtitle),
    )
}

@Composable
private fun HistoryTab(entries: List<ExerciseHistoryEntry>) {
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.BarChart,
            title = stringResource(R.string.exercise_detail_history_empty_title),
            subtitle = stringResource(R.string.exercise_detail_history_empty_subtitle),
        )
        return
    }
    val sessions = entries
        .groupBy { it.workoutId }
        .map { (workoutId, sets) -> HistorySession(workoutId, sets.first().workoutTitle, sets.sortedBy { it.setOrderIndex }) }
        .sortedByDescending { it.sets.first().workoutStartedAt }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        items(items = sessions, key = { it.workoutId }) { session ->
            Card(modifier = Modifier.padding(bottom = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(session.workoutTitle, style = MaterialTheme.typography.titleSmall)
                    session.sets.forEach { set ->
                        Text(
                            formatHistorySet(set),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}

private fun formatHistorySet(entry: ExerciseHistoryEntry): String {
    val parts = mutableListOf<String>()
    entry.weightKg?.let { parts.add("${it}kg") }
    entry.reps?.let { parts.add("${it} reps") }
    entry.durationSeconds?.let { parts.add("${it}s") }
    entry.distanceMeters?.let { parts.add("${it}m") }
    entry.rpe?.let { parts.add("@$it") }
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

@Composable
private fun HowToTab(instructions: String) {
    if (instructions.isBlank()) {
        EmptyState(
            icon = Icons.Filled.BarChart,
            title = stringResource(R.string.exercise_detail_how_to_empty_title),
            subtitle = stringResource(R.string.exercise_detail_how_to_empty_subtitle),
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        instructions.split("\n").filter { it.isNotBlank() }.forEachIndexed { index, step ->
            Text(
                "${index + 1}. $step",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
}
