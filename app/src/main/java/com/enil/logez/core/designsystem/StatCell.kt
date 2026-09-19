package com.enil.logez.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A value stacked over a label — the shape every stat readout in the app already used
 * independently five times (History's card stats, the Workout Summary/Detail screens, Profile's
 * headline cards, the share card), each with only styling differences. Consolidated here
 * (2026-09-19 debt audit) with every call site's exact prior appearance preserved via explicit
 * parameters rather than adopting one look as a shared default — this is a dedup, not a redesign.
 *
 * [icon], when non-null, replaces the value [Text] entirely (the Workout Detail screen's PR-medal
 * cell: a trophy icon instead of a number). Card/background wrapping (Profile's [LogEzCard]) stays
 * at the call site rather than becoming a flag here, since Compose already composes that naturally.
 */
@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    valueStyle: TextStyle = LogEzMono.dataMedium,
    valueColor: Color = Color.Unspecified,
    valueTextAlign: TextAlign = TextAlign.Unspecified,
    valueMaxLines: Int = Int.MAX_VALUE,
    valueOverflow: TextOverflow = TextOverflow.Clip,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall,
    labelColor: Color = Color.Unspecified,
    labelTextAlign: TextAlign = TextAlign.Unspecified,
    labelMaxLines: Int = Int.MAX_VALUE,
    labelOverflow: TextOverflow = TextOverflow.Clip,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint)
        } else {
            Text(
                value,
                style = valueStyle,
                color = valueColor,
                textAlign = valueTextAlign,
                maxLines = valueMaxLines,
                overflow = valueOverflow,
            )
        }
        Text(
            label,
            style = labelStyle,
            color = labelColor,
            textAlign = labelTextAlign,
            maxLines = labelMaxLines,
            overflow = labelOverflow,
        )
    }
}
