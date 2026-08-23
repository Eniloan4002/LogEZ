package com.enil.logez.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.enil.logez.core.common.ThemeMode

private val LightColors = lightColorScheme(
    primary = Accent600,
    onPrimary = Neutral0,
    primaryContainer = Accent400,
    onPrimaryContainer = Neutral950,
    background = Neutral0,
    onBackground = Neutral900,
    surface = Neutral0,
    onSurface = Neutral900,
    surfaceVariant = Neutral50,
    onSurfaceVariant = Neutral600,
    outline = Neutral200,
    error = Danger500,
)

private val DarkColors = darkColorScheme(
    primary = Accent400,
    onPrimary = Neutral950,
    primaryContainer = Accent600,
    onPrimaryContainer = Neutral0,
    background = Neutral950,
    onBackground = Neutral0,
    surface = Neutral900,
    onSurface = Neutral0,
    surfaceVariant = Neutral800,
    onSurfaceVariant = Neutral400,
    outline = Neutral600,
    error = Danger500,
)

/**
 * Resolves [ThemeMode] to light/dark (SYSTEM follows [isSystemInDarkTheme]) and applies the
 * design-system color scheme + typography. All screens must be composed under this.
 */
@Composable
fun LogEzTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = LogEzTypography,
        content = content,
    )
}
