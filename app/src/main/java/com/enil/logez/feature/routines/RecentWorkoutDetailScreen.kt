package com.enil.logez.feature.routines

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.CircuitChip
import com.enil.logez.core.designsystem.SetLegend
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.feature.history.DetailTableUnits
import com.enil.logez.feature.history.DetailRoundCard
import com.enil.logez.feature.history.ExerciseBlockCard
import com.enil.logez.feature.history.buildDetailRounds
import kotlinx.coroutines.launch

/**
 * R-1 (2026-10-01): a Recent workout opened like a routine -- the exercise blocks and sets Start
 * would fill in, in History's headed tables (the same [ExerciseBlockCard] and [DetailRoundCard]),
 * with "Start this workout" on top. Start asks first (R-2, the Owner's 2026-09-30 rule), through the
 * same handler as the Start pill on the list row. Reached from a Recent row on the Workout tab or
 * on "See all".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentWorkoutDetailScreen(
    onBack: () -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    onSavedAsRoutine: (routineId: String) -> Unit,
    onOpenInHistory: (workoutId: String) -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    viewModel: RecentWorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecentWorkoutDetailContent(
        uiState = uiState,
        startActions = viewModel,
        onRefresh = viewModel::refresh,
        onSaveAsRoutine = viewModel::saveAsRoutine,
        onBack = onBack,
        onNavigateToLogger = onNavigateToLogger,
        onNavigateToActivityTracking = onNavigateToActivityTracking,
        onNavigateToFinish = onNavigateToFinish,
        onSavedAsRoutine = onSavedAsRoutine,
        onOpenInHistory = onOpenInHistory,
        onExerciseClick = onExerciseClick,
    )
}

/** The screen without its ViewModel: what [RecentWorkoutDetailScreen] draws for a given state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentWorkoutDetailContent(
    uiState: RecentWorkoutDetailUiState,
    startActions: RecentStartActions,
    onRefresh: () -> Unit,
    onSaveAsRoutine: suspend () -> String?,
    onBack: () -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    onSavedAsRoutine: (routineId: String) -> Unit,
    onOpenInHistory: (workoutId: String) -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val start = rememberRecentStartHandler(startActions, onNavigateToLogger, onNavigateToActivityTracking, onNavigateToFinish)
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isMissing) { if (uiState.isMissing) onBack() }

    // Open in History can edit or delete this workout, and this is the screen that returns.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val summary = uiState.summary
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { Text(summary?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (summary != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            // An empty workout has nothing to turn into a routine.
                            if (uiState.blocks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_detail_save_as_routine)) },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch { onSaveAsRoutine()?.let(onSavedAsRoutine) }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recent_detail_open_in_history)) },
                                onClick = { menuExpanded = false; onOpenInHistory(summary.workoutId) },
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (summary == null || uiState.isLoading) return@Scaffold
        val units = DetailTableUnits(uiState.effortScale, uiState.weightUnit, uiState.distanceUnit)
        val rounds = if (uiState.isCircuit) buildDetailRounds(uiState.blocks) else emptyList()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(bottom = Spacing.lg),
        ) {
            item(key = "header") {
                Text(
                    recentSubtitle(summary, compact = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
                uiState.notes?.let { notes ->
                    Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
                }
                if (rounds.isNotEmpty()) {
                    // Same line as the routine detail's circuit header: the chip, then "N rounds". A circuit
                    // with no logged sets has no rounds to count, so it shows neither.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                        CircuitChip()
                        Text(
                            pluralStringResource(R.plurals.routine_rounds_count, rounds.size, rounds.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
                if (uiState.blocks.isEmpty()) {
                    Text(
                        stringResource(R.string.recent_detail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                } else {
                    Button(
                        onClick = { start(summary.workoutId) },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    ) {
                        Text(stringResource(R.string.recent_detail_start))
                    }
                    Text(
                        stringResource(R.string.recent_detail_start_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    // The key to the W / F / D badges, only for the types this workout holds. No
                    // trophy and no effort line: the rows carry neither.
                    SetLegend(
                        setTypes = uiState.blocks.flatMapTo(mutableSetOf()) { block -> block.sets.map { it.setType } },
                        showPersonalRecord = false,
                        effortScale = null,
                        onEffortInfoClick = {},
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
            if (uiState.isCircuit) {
                items(items = rounds, key = { it.roundNumber }) { round ->
                    DetailRoundCard(round, units, onEffortInfoClick = {}, onExerciseClick = onExerciseClick, preview = true)
                }
            } else {
                items(items = uiState.blocks, key = { it.workoutExercise.id }) { block ->
                    ExerciseBlockCard(block, units, onEffortInfoClick = {}, onExerciseClick = onExerciseClick, preview = true)
                }
            }
        }
    }
}
