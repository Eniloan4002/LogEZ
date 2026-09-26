package com.enil.logez.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The one honest-empty-state component every screen in the app uses (PHASE2_PLAN.md: "empty
 * states are honest — they never render zeroed charts, sample data, or fabricated metrics").
 * A screen with no CTA yet (nothing to route to before M2/M3 land) omits [ctaLabel].
 *
 * [ctaStyle] defaults to [EmptyStateCtaStyle.Filled], so every screen whose empty state is its
 * only action keeps the solid button. A screen that already pins its own filled primary action
 * (the Workout tab's Start Empty Workout) passes [EmptyStateCtaStyle.Outlined], so the pinned
 * action stays the one filled button in view.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCtaClick: () -> Unit = {},
    ctaStyle: EmptyStateCtaStyle = EmptyStateCtaStyle.Filled,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(Spacing.xl)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
        if (ctaLabel != null) {
            val ctaModifier = Modifier.padding(top = Spacing.lg)
            // Centred like the title and subtitle, so a label that wraps at large font sizes
            // doesn't hug the button's start edge. A no-op for a one-line label.
            when (ctaStyle) {
                EmptyStateCtaStyle.Filled -> Button(onClick = onCtaClick, modifier = ctaModifier) {
                    Text(ctaLabel, textAlign = TextAlign.Center)
                }
                EmptyStateCtaStyle.Outlined -> OutlinedButton(onClick = onCtaClick, modifier = ctaModifier) {
                    Text(ctaLabel, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** How [EmptyState] draws its call to action. */
enum class EmptyStateCtaStyle { Filled, Outlined }
