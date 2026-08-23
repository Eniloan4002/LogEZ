package com.enil.logez.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.designsystem.EmptyState

/**
 * Landing tab (PHASE2_PLAN.md §5.2 "History tab"). Stub for M0 — the real feed (paged workout
 * cards from Room) lands in M5. This is the honest empty state every fresh install shows until
 * a workout exists, not a placeholder to be swapped for something else later.
 */
@Composable
fun HistoryScreen() {
    EmptyState(
        icon = Icons.Filled.History,
        title = stringResource(R.string.history_empty_title),
        subtitle = stringResource(R.string.history_empty_subtitle),
    )
}
