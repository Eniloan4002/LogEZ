package com.enil.logez.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import java.util.Locale

/**
 * Shared row/dialog vocabulary for the M16 Settings tree — the same ListItem idiom as
 * ProfileScreen's nav rows (headline + supporting + trailing), so Settings reads like the rest of
 * the app rather than a new dialect.
 */

/** Uppercase section header ("PREFERENCES", "WORKOUTS", …) matching the app's card-title styling. */
@Composable
internal fun SettingsSectionHeader(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xs),
    )
}

/** Toggle row: label + one-sentence explainer + trailing Switch. Persists instantly on change. */
@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
    HorizontalDivider()
}

/** Value-preview row: label (+ optional explainer) + current value as trailing text + chevron. */
@Composable
internal fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                if (value.isNotEmpty()) {
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
    HorizontalDivider()
}

/**
 * Radio-list selection dialog for a value-preview row. Selecting an option persists instantly
 * (via [onSelect]) and dismisses — there is no confirm step, matching the screen's
 * no-Save-button contract.
 */
@Composable
internal fun <T> SettingsRadioDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // The rest-timer picker has ten options — scroll rather than overflow on short screens.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option); onDismiss() }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option); onDismiss() })
                        Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
