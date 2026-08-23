package com.enil.logez.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    onBack: () -> Unit,
    onEdit: (routineId: String) -> Unit,
    viewModel: RoutineDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoonMessage = stringResource(R.string.workout_live_logging_coming_soon)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.routine?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    uiState.routine?.let { routine ->
                        Button(onClick = { onEdit(routine.id) }) { Text(stringResource(R.string.action_edit)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = { scope.launch { snackbarHostState.showSnackbar(comingSoonMessage) } },
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) {
                Text(stringResource(R.string.workout_start_routine))
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
                items(items = uiState.exercises, key = { it.routineExercise.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(row.exercise?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            row.sets.forEachIndexed { index, set ->
                                Text(
                                    formatDetailSetTargets(index + 1, set),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDetailSetTargets(position: Int, set: com.enil.logez.core.data.entity.RoutineSetEntity): String {
    val parts = mutableListOf<String>()
    set.targetWeightKg?.let { parts.add("${formatNum(it)}kg") }
    if (set.targetRepRangeMin != null) {
        parts.add("${set.targetRepRangeMin}-${set.targetRepRangeMax} reps")
    } else {
        set.targetReps?.let { parts.add("$it reps") }
    }
    set.targetDurationSeconds?.let { parts.add(formatMmSs(it)) }
    set.targetDistanceMeters?.let { parts.add("${formatNum(it)}m") }
    val body = if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    return "Set $position: $body"
}

private fun formatNum(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
