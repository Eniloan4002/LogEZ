package com.enil.logez.feature.widget

import androidx.glance.unit.ColorProvider
import com.enil.logez.core.designsystem.NeonGreen
import com.enil.logez.core.designsystem.Neutral0
import com.enil.logez.core.designsystem.Neutral400
import com.enil.logez.core.designsystem.Neutral700
import com.enil.logez.core.designsystem.Neutral900

/**
 * The widget's palette, taken from the real theme tokens rather than re-typed hex so a future
 * re-root carries through.
 *
 * These are passed explicitly at every call site instead of wrapping the composition in
 * `GlanceTheme { }`: the no-argument form applies dynamic wallpaper color on Android 12+, which
 * would silently repaint the widget in whatever colours the user's wallpaper suggests. The
 * single-argument `ColorProvider` is the fixed-colour overload and does not follow the system
 * light/dark setting, which is what a dark-only app wants.
 */
internal object WidgetColors {
    val background = ColorProvider(Neutral900)
    val accent = ColorProvider(NeonGreen)
    val primaryText = ColorProvider(Neutral0)
    val secondaryText = ColorProvider(Neutral400)

    /** Days not yet trained — visible against the card, but clearly unfilled. */
    val emptyTrack = ColorProvider(Neutral700)
}
