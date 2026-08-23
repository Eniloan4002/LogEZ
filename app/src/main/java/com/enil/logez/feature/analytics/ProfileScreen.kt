package com.enil.logez.feature.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.common.ThemeMode
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.Spacing

/**
 * Profile tab (PHASE2_PLAN.md §5.2 "Profile tab"): headline stats, calendar preview, quick
 * charts, Statistics/Measurements/Exercises navigation. Real content lands progressively — M2
 * adds the Exercises row (below); the rest (stats, calendar, Measurements, Statistics) is M6/M7.
 *
 * [themeMode]/[onThemeModeChange] are an M0-only debug affordance for that milestone's "theme
 * switches" on-device check — NOT the real Settings screen (that is M7, DataStore-backed).
 * State is hoisted to [com.enil.logez.LogEzApp] so the segmented control reflects the theme
 * actually applied, and must be deleted the moment M7 lands.
 */
@Composable
fun ProfileScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onExercisesClick: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(Spacing.md)) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                ) {
                    Text(mode.name)
                }
            }
        }
        HorizontalDivider()

        ListItem(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onExercisesClick),
            leadingContent = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.profile_nav_exercises)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
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
