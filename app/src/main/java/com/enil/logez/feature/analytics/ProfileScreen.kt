package com.enil.logez.feature.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.EmptyState

/**
 * Profile tab (PHASE2_PLAN.md §5.2 "Profile tab"): headline stats, calendar preview, quick
 * charts, Statistics/Measurements/Exercises navigation. Real content lands progressively — M2
 * added the Exercises row, M5c the Calendar row; headline stats, the last-7-days strip, quick
 * charts, Statistics and Measurements are M6/M7.
 *
 * The plan describes the calendar entry point as an inline current-month grid that opens the full
 * screen on tap. It ships here as a navigation row instead: an inline grid on a tab that is
 * otherwise an honest empty state would be the one piece of real content on the screen, and the
 * same grid is one tap away. It becomes a preview when M6 gives it neighbours to sit among.
 *
 * Also carries a temporary RPE-tracking toggle (2026-08-24): §5.1.7's RPE picker is gated behind
 * `rpeTrackingEnabled`, but no Settings screen exists yet anywhere in the app to switch it on —
 * that's M7 territory. This row is a real, persisted toggle (not a debug hack) standing in until
 * then; remove it once the actual Settings tree lands with its own row for the same setting.
 */
@Composable
fun ProfileScreen(
    onExercisesClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val rpeTrackingEnabled by viewModel.rpeTrackingEnabled.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onCalendarClick),
            leadingContent = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.profile_calendar_row)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        )
        HorizontalDivider()
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onExercisesClick),
            leadingContent = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.profile_nav_exercises)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        )
        HorizontalDivider()
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.setRpeTrackingEnabled(!rpeTrackingEnabled) },
            headlineContent = { Text(stringResource(R.string.profile_rpe_toggle_title)) },
            supportingContent = { Text(stringResource(R.string.profile_rpe_toggle_subtitle)) },
            trailingContent = { Switch(checked = rpeTrackingEnabled, onCheckedChange = viewModel::setRpeTrackingEnabled) },
        )
        HorizontalDivider()

        EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.profile_empty_title),
            subtitle = stringResource(R.string.profile_empty_subtitle),
            modifier = Modifier.weight(1f),
        )
    }
}
