package com.enil.logez.feature.routines

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.designsystem.EmptyState

/**
 * Workout tab (PHASE2_PLAN.md §5.1.1): folders, routines, Start Empty Workout. Stub for M0 —
 * the real routine builder and folder list land in M3.
 */
@Composable
fun WorkoutTabScreen() {
    EmptyState(
        icon = Icons.Filled.FitnessCenter,
        title = stringResource(R.string.workout_empty_title),
        subtitle = stringResource(R.string.workout_empty_subtitle),
    )
}
