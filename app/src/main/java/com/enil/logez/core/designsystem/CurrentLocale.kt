package com.enil.logez.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The locale the UI is currently rendering in, read through [LocalConfiguration] so a composable
 * that uppercases a label or names a weekday recomposes when the user changes the system language.
 *
 * `Locale.getDefault()` returns the same value but is not observable state: a screen already on
 * the back stack kept its old-language weekday names and uppercase rules until it was rebuilt.
 * Compose lint flags that as NonObservableLocale, and it fails the release lint run.
 *
 * Deliberately built on [LocalConfiguration] rather than Compose UI's newer `LocalLocale`: the
 * configuration route works on every Compose UI version this project can resolve, so the helper
 * does not depend on which Material3 build a transitive dependency happens to pull in.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    // The system always supplies at least one locale; ROOT only guards an empty list, and falling
    // back to Locale.getDefault() here would reintroduce the very read this helper exists to avoid.
    return if (locales.isEmpty) Locale.ROOT else locales[0]
}
