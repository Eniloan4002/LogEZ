package com.enil.logez.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em

/**
 * Muted mono small caps -- the quiet divider label the Achievements screen and Recent list use, and
 * Profile's in-card titles (promoted from the private copy in AchievementsScreen, 2026-10-01). It
 * is a TalkBack heading, so a screen reader can jump between sections.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(currentLocale()),
        style = LogEzMono.dataMedium.copy(letterSpacing = 0.1.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}
