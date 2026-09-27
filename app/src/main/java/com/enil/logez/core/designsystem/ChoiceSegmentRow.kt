package com.enil.logez.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One choice out of a few, as a wrapping row of pills (first-run setup's units and week start).
 *
 * Modelled on the Statistics screen's quiet range chip: a green tint and border when selected,
 * the outline colour when not, inside a tap area at least 48 dp tall. The differences:
 * - body-size text, shown as written rather than uppercased;
 * - a check icon on the selected pill, its space reserved on every pill so nothing shifts when
 *   the selection changes (selection never shows through colour alone);
 * - each pill is a radio button inside a selectable group, so TalkBack reads "selected" and the
 *   position in the group.
 *
 * The pills wrap ([FlowRow]) instead of clipping, so "Saturday" at 200% font moves to a new line.
 * Nothing animates: a tap changes the selection at once.
 *
 * @param label the visible text of each option.
 * @param description what TalkBack reads instead of [label] ("Kilograms (kg)" for "kg"), or null
 *   to read the label itself. With a description the visible label is left out of the semantics,
 *   so it is not read as well.
 */
@Composable
fun <T> ChoiceSegmentRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    description: @Composable (T) -> String? = { null },
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEach { option ->
            ChoicePill(
                text = label(option),
                description = description(option),
                selected = option == selected,
                enabled = enabled,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun ChoicePill(
    text: String,
    description: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.pill)
    val primary = MaterialTheme.colorScheme.primary
    val contentColor = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .heightIn(min = TOUCH_HEIGHT)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = PILL_HEIGHT)
                .clip(shape)
                .background(if (selected) primary.copy(alpha = 0.14f) else Color.Transparent)
                .border(
                    1.dp,
                    if (selected) primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                )
                .padding(start = PILL_START_PADDING, end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Always laid out, drawn only when selected: the pill keeps one width in both states.
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = if (selected) primary else Color.Transparent,
                modifier = Modifier.size(CHECK_SIZE),
            )
            Spacer(Modifier.width(CHECK_GAP))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                // With a description, the pill says only that: the symbol would otherwise be read
                // after it ("Kilograms (kg), kg").
                modifier = Modifier
                    .padding(vertical = Spacing.xs)
                    .then(if (description != null) Modifier.clearAndSetSemantics {} else Modifier),
            )
        }
    }
}

private val TOUCH_HEIGHT = 48.dp
private val PILL_HEIGHT = 40.dp
private val PILL_START_PADDING = 10.dp
private val CHECK_SIZE = 18.dp
private val CHECK_GAP = 6.dp
