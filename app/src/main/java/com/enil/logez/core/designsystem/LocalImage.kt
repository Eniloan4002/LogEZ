package com.enil.logez.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.File

/**
 * Renders an app-private local file (custom-exercise photos, PHASE2_PLAN.md §9.1) via Coil 3
 * (M20b). All LogEZ media is local-only (no network fetcher is registered on the app-wide
 * `ImageLoader` in [com.enil.logez.LogEzApplication]; map tiles are the app's only network use)
 * -- Coil is used here for its size-targeted decoding and memory/disk cache, not for loading. A
 * full-resolution user photo would otherwise be re-decoded at full size on every list-row recycle;
 * Coil infers the target size from [modifier]'s constraints and downsamples the decode to match.
 */
@Composable
fun LocalImage(
    absolutePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = File(absolutePath),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
