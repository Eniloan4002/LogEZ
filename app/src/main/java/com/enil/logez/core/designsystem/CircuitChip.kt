package com.enil.logez.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.enil.logez.R

/**
 * M11: the non-interactive "CIRCUIT" marker in the v4.0 pill/mono-caps chip vocabulary — shared by
 * the routine card, routine detail and history card so a circuit reads the same everywhere. A
 * status label, not a control: primary-tinted fill like the logger's rest chip, never clickable.
 */
@Composable
fun CircuitChip(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.routine_structure_chip_circuit),
            style = LogEzMono.dataSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.08.em),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}
