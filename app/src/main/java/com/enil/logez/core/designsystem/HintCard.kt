package com.enil.logez.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enil.logez.R

/**
 * First-run plan (O1g): a one-time tip, drawn as an ordinary [LogEzCard] in the list it explains.
 * No overlay, popup or animation, and no live region: it is plain text in reading order.
 *
 * Compact, per Decision 12:
 * - With a [title], a decorative Info icon sits beside the title only, and [lines] run the card's
 *   full width underneath.
 * - Without one, [lines] fill the card.
 * - "Got it" sits at the left, like "+ Add Set" in the exercise cards.
 * - [singleRow] puts one line and "Got it" side by side (the exercise picker's tip).
 */
@Composable
fun HintCard(
    lines: List<String>,
    onGotIt: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    singleRow: Boolean = false,
) {
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        if (singleRow) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lines.joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                GotItButton(onGotIt)
            }
        } else {
            Column(modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs)) {
                if (title != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            // A heading, like the setup screen's titles, for TalkBack's heading navigation.
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
                lines.forEachIndexed { index, line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (index == 0 && title == null) 0.dp else Spacing.xs),
                    )
                }
                GotItButton(onGotIt, Modifier.padding(top = Spacing.xxs))
            }
        }
    }
}

@Composable
private fun GotItButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // TextButton keeps Material's 48dp minimum touch target; its label is the button's name.
    TextButton(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.tip_got_it))
    }
}
