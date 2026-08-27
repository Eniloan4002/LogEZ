package com.enil.logez.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import java.util.Locale

/**
 * v4.0's shared TopAppBar title (Owner: every tab root's header should read the same way) —
 * uppercase, bold, letterspaced Chakra Petch (`titleLarge` already carries the display font from
 * Type.kt). No glow here: that's scoped to genuinely dashboard-flavored content (a live readout, a
 * CTA), not chrome every screen repeats.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
