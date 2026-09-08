package com.enil.logez.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogPanel
import com.composeunstyled.DialogProperties
import com.composeunstyled.UnstyledDialog
import com.composeunstyled.UnstyledSlider
import com.enil.logez.R
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import java.util.Locale
import kotlin.math.roundToInt

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

/**
 * Continuous 0f-1f slider row (Owner, 2026-09-03: replaces the old discrete Off/Quiet/Normal/Loud
 * picker for every audio setting). Dragging updates only local state — smooth, no DataStore writes
 * mid-drag — and [onValueChangeFinished] fires once on release with the local value, for both
 * persisting and a live preview sound, mirroring the old radio dialog's onSelect-does-both pattern.
 */
@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
) {
    var localValue by remember(value) { mutableStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (localValue <= 0f) stringResource(R.string.settings_volume_off) else "${(localValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // M20e: on-brand renderless slider (compose-unstyled) — a flat outlineVariant track with a
        // primary fill to the current fraction, a plain primary thumb circle. No shadow, no halo,
        // matching the app's flat-chrome taste (§2.6 reference: ShareSummaryDialog's own tokens).
        UnstyledSlider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChangeFinished(localValue) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            track = { state ->
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                    val fraction = (state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)
                    Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                }
            },
            thumb = {
                Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            },
        )
    }
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
/**
 * M20e: rebuilt on compose-unstyled's renderless `UnstyledDialog`/`DialogPanel` — the panel is the
 * app's own "on-brand dialog chrome" reference tokens (§2.6: `ShareSummaryDialog.kt` —
 * `Radius.md` + `colorScheme.surface` + a 1.dp `outlineVariant` border), not Material3's default
 * dialog shape/elevation. Behaviour unchanged: selecting an option persists instantly and
 * dismisses (no confirm step); outside-tap or back dismisses via [onDismiss], same as before.
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
    // KNOWN LIMITATION (compose-unstyled 2.9.0): the system back gesture does not dismiss this
    // dialog on-device, confirmed with both DialogProperties.dismissOnBackPress = true AND an
    // explicit Compose BackHandler (below) -- neither reaches the popup. Outside-tap and Cancel
    // both dismiss correctly (also confirmed on-device), so the dialog is never actually stuck,
    // just missing one of the platform's three conventional dismiss gestures. BackHandler is kept
    // in case a future compose-unstyled release wires the popup into the back-press dispatcher.
    BackHandler(onBack = onDismiss)
    UnstyledDialog(
        visible = true,
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        DialogPanel(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.md))
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(Radius.md))
                .padding(Spacing.md),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                // The rest-timer picker has ten options — scroll rather than overflow on short screens.
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.sm)) {
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
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
        }
    }
}
