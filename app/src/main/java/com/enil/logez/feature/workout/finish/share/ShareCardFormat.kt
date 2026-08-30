package com.enil.logez.feature.workout.finish.share

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The two social-canvas shapes the summary card exports to. Cards are laid out in dp at these
 * design sizes and composed for capture under a *fixed* [ShareCardExportDensity], so the encoded
 * PNG is exactly 1080×1080 (SQUARE) / 1080×1920 (STORY) px on every device — output resolution
 * must not depend on whatever screen density the phone happens to have.
 */
enum class ShareCardFormat(val width: Dp, val height: Dp) {
    SQUARE(360.dp, 360.dp),
    STORY(360.dp, 640.dp),
}

/** 3× the 360dp design width → the 1080px-wide canvas social feeds natively expect. */
internal val ShareCardExportDensity = Density(density = 3f)
