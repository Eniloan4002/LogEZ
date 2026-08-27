package com.enil.logez.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Neutral950,
    primaryContainer = Neutral900,
    onPrimaryContainer = NeonGreen,
    // Dark text on every green fill: light-on-DeepGreen measures 2.77:1 (fails), dark-on 6.15:1.
    secondary = DeepGreen,
    onSecondary = Neutral950,
    secondaryContainer = DeepGreen,
    onSecondaryContainer = Neutral950,
    tertiary = SpringGreen,
    onTertiary = Neutral950,
    tertiaryContainer = Neutral900,
    onTertiaryContainer = SpringGreen,
    background = Neutral950,
    onBackground = Neutral0,
    surface = Neutral900,
    onSurface = Neutral0,
    surfaceVariant = Neutral800,
    onSurfaceVariant = Neutral400,
    outline = Neutral600,
    outlineVariant = Neutral700,
    error = Danger500,
    onError = Neutral950,
)

/** Dark-only (Owner directive) — applies the design-system color scheme + typography. All screens must be composed under this. */
@Composable
fun LogEzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = LogEzTypography,
        content = content,
    )
}
