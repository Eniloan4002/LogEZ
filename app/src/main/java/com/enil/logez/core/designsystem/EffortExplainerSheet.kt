package com.enil.logez.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.RpeScale

/**
 * P-211 (Owner, 2026-09-30) §6: "What is RIR?" / "What is RPE?", opened from the ⓘ on any effort
 * column header ([EffortHeaderLabel]), the legend's effort line ([SetLegend]) or a picker's link
 * ([EffortInfoLink]). Title and text for the active [scale], a two-row strip mapping the scales
 * (the active one in primary, in its own picker's order), the Settings hint, and Got it.
 *
 * Opens fully expanded: the strip and the hint are the point, and a half-open sheet would hide them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffortExplainerSheet(scale: EffortScale, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        EffortExplainerContent(scale = scale, onGotIt = onDismiss)
    }
}

/** The explainer's body, without the sheet (the same padding as the logger's picker sheet). */
@Composable
fun EffortExplainerContent(scale: EffortScale, onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(Spacing.md)) {
        Text(
            stringResource(scale.explainerTitleRes()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(scale.explainerBodyRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        ScaleStrip(scale = scale, modifier = Modifier.padding(top = Spacing.md))
        Text(
            stringResource(scale.explainerSwitchHintRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.End) {
            Button(onClick = onGotIt) { Text(stringResource(R.string.effort_explainer_got_it)) }
        }
    }
}

/**
 * RIR 0 · 1 · 2 · 3 · 4+ over RPE 10 · 9 · 8 · 7 · 6 in RIR mode; RPE 6 → 10 over RIR 4+ → 0 in
 * RPE mode. Both rows come from [RpeScale.RIR_CHIP_VALUES] through the same formatters every
 * other screen uses, so the strip can never disagree with them.
 */
@Composable
private fun ScaleStrip(scale: EffortScale, modifier: Modifier = Modifier) {
    val values = if (scale == EffortScale.RIR) RpeScale.RIR_CHIP_VALUES else RpeScale.RIR_CHIP_VALUES.reversed()
    val rows = listOf(scale, if (scale == EffortScale.RIR) EffortScale.RPE else EffortScale.RIR)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(Radius.md))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        rows.forEach { rowScale ->
            val active = rowScale == scale
            val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            // One announcement per row ("RIR, 0, 1, 2, 3, 4+"), not ten separate stops.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = STRIP_ROW_HEIGHT).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(rowScale.labelRes()),
                    style = LogEzMono.dataMedium.copy(fontWeight = FontWeight.SemiBold, color = color),
                    modifier = Modifier.width(Spacing.xxl),
                )
                values.forEach { rpe ->
                    Text(
                        RpeScale.format(rpe, rowScale),
                        style = LogEzMono.dataMedium.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, color = color),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.effort_explainer_strip_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/**
 * An effort column's header: "RPE ⓘ" / "RIR ⓘ" in the logger's HeaderCell style (labelSmall,
 * onSurfaceVariant, one line), the whole cell a button that opens [EffortExplainerSheet]
 * via [onInfoClick].
 *
 * The ⓘ is drawn at 12dp. The cell keeps its text height so the header row stays 16dp: Compose
 * enforces the 48dp minimum touch target on its own for a clickable smaller than that
 * (ViewConfiguration.minimumTouchTargetSize, the rule Material3's minimumInteractiveComponentSize
 * KDoc points to), so the tap area is 48dp tall without pushing the table down.
 *
 * Above [dropIconAboveFontScale] the ⓘ is left out and the label stays tappable: at about 1.3×
 * "RIR ⓘ" no longer fits the logger's fixed 44dp cell (README "Large text"). Pass null where the
 * column widens with its text instead, as the History tables do.
 */
@Composable
fun EffortHeaderLabel(
    scale: EffortScale,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    dropIconAboveFontScale: Float? = EFFORT_INFO_ICON_MAX_FONT_SCALE,
) {
    val label = stringResource(scale.labelRes())
    val showIcon = dropIconAboveFontScale == null || LocalDensity.current.fontScale <= dropIconAboveFontScale
    Row(
        modifier = modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.effort_info_click_a11y, label),
            onClick = onInfoClick,
        ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showIcon) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(EFFORT_INFO_ICON_SIZE),
            )
        }
    }
}

/**
 * The pickers' "What's RIR?" / "What's RPE?" link (README §5): an ⓘ and the words in a
 * TextButton, which keeps its own 12dp content padding so it lines up with Clear below it.
 */
@Composable
fun EffortInfoLink(scale: EffortScale, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(stringResource(scale.pickerLinkRes()))
    }
}

/** Above this font scale an effort header drops its ⓘ (see [EffortHeaderLabel]). */
const val EFFORT_INFO_ICON_MAX_FONT_SCALE = 1.3f

/** The ⓘ beside an effort header's label. */
private val EFFORT_INFO_ICON_SIZE = 12.dp

private val STRIP_ROW_HEIGHT = 28.dp
