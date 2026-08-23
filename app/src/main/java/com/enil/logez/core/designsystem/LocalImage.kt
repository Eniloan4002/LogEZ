package com.enil.logez.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes an app-private local file (custom-exercise / progress photos, PHASE2_PLAN.md §9.1) off
 * the main thread. All logEZ media is local-only (no network permission exists anywhere in this
 * app), so a full async image-loading library is unneeded — this covers the one real case.
 */
@Composable
fun LocalImage(
    absolutePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(absolutePath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
