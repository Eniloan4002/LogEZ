package com.enil.logez.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * System sans-serif for M0 (no custom font decided for logEZ yet — Fiterval bundles Montserrat,
 * but that is Fiterval's brand choice, not logEZ's; pick a font deliberately as a Phase 4/polish
 * decision rather than defaulting to Fiterval's asset by accident). Never below FontWeight.Normal
 * for UI text (Refactoring UI rule) — de-emphasize with color, not thinner weight.
 */
private val baseFontFamily = FontFamily.Default

val LogEzTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = baseFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = baseFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = baseFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = baseFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = baseFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
