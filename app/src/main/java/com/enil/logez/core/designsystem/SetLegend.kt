package com.enil.logez.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.SetDisplayLabel
import com.enil.logez.core.domain.model.SetType

/**
 * P-211 decision 5 (Owner, 2026-09-30): the key under the workout detail's stats and at the top of
 * Exercise history.
 *
 * Line 1 lists only the badge types present in [setTypes] (W Warm-up, F Failure, D Dropset, in
 * that order; NORMAL is ignored), then the trophy when [showPersonalRecord]. "Dropset" is the
 * set-type menu's own word. Line 2, when [effortScale] is non-null (pass it only when some set on
 * the screen has an effort value), reads "RPE  How hard it felt · 10 = no reps left ⓘ" and is one
 * button that calls [onEffortInfoClick] to open [EffortExplainerSheet]. Draws nothing when there is
 * nothing to explain.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetLegend(
    setTypes: Set<SetType>,
    showPersonalRecord: Boolean,
    effortScale: EffortScale?,
    onEffortInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lettered = LEGEND_ORDER.filter { it in setTypes }
    val hasFirstLine = lettered.isNotEmpty() || showPersonalRecord
    if (!hasFirstLine && effortScale == null) return

    // Owner, 2026-10-01: the key sits in its own hairline-bordered box (the cards' outlineVariant
    // hairline) so it reads as a key, not as more of the stats above or the table below.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        if (hasFirstLine) {
            // sm between entries and xxs inside one (the mockup's 14dp and 6dp, on the token
            // scale): the whole key fits one line at 412dp, as drawn, and wraps below that.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                lettered.forEach { type ->
                    LegendItem(text = stringResource(type.legendNameRes())) {
                        HistorySetBadge(SetDisplayLabel(type, null), compact = true, modifier = Modifier.clearAndSetSemantics { })
                    }
                }
                if (showPersonalRecord) {
                    LegendItem(text = stringResource(R.string.legend_personal_record)) {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Gold500, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (effortScale != null) {
            EffortLegendLine(
                scale = effortScale,
                onClick = onEffortInfoClick,
                modifier = if (hasFirstLine) Modifier.padding(top = Spacing.xs) else Modifier,
            )
        }
    }
}

/**
 * One key entry: a mark (badge or trophy) and its name, read as the name alone. Every entry is at
 * least the badge's height, so the 16dp trophy centres on the same line as the 20dp badges.
 */
@Composable
private fun LegendItem(text: String, mark: @Composable () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 20.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        mark()
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "RIR  Reps you had left · 0 = none left ⓘ". The key sits in a 28dp-min slot so it lines up
 * with the badges above it. Kept at its text height; Compose enforces the 48dp touch target
 * (see [EffortHeaderLabel]).
 *
 * The ⓘ is drawn inside the text, after a no-break space, so when large text wraps the line it
 * stays after the last word. As a separate icon it was pushed to the row's far edge once the
 * wrapped text filled the row (P-211 code review, 2026-09-30).
 */
@Composable
private fun EffortLegendLine(scale: EffortScale, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(scale.labelRes())
    val legend = stringResource(scale.legendRes())
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = with(LocalDensity.current) { LEGEND_INFO_ICON_SIZE.toSp() }
    val text = buildAnnotatedString {
        append(legend)
        append(NO_BREAK_SPACE)
        appendInlineContent(LEGEND_INFO_ICON_ID, "ⓘ")
    }
    val inlineContent = mapOf(
        LEGEND_INFO_ICON_ID to InlineTextContent(Placeholder(iconSize, iconSize, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
        },
    )
    Row(
        modifier = modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.effort_info_click_a11y, label),
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            inlineContent = inlineContent,
            // Read as the words alone, as before; the icon is decoration.
            modifier = Modifier.padding(start = Spacing.xs).clearAndSetSemantics { this.text = AnnotatedString(legend) },
        )
    }
}

/** The legend line's ⓘ, 14dp at every font size as before. */
private val LEGEND_INFO_ICON_SIZE = 14.dp
private const val LEGEND_INFO_ICON_ID = "info"
private const val NO_BREAK_SPACE = "\u00A0"

private val LEGEND_ORDER = listOf(SetType.WARMUP, SetType.FAILURE, SetType.DROPSET)

private fun SetType.legendNameRes(): Int = when (this) {
    SetType.WARMUP -> R.string.legend_warmup
    SetType.FAILURE -> R.string.legend_failure
    SetType.DROPSET -> R.string.legend_dropset
    SetType.NORMAL -> error("NORMAL has no legend entry")
}
