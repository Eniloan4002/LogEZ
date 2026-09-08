package com.enil.logez.core.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** M20b: the stored file's long edge is capped here so a 12+ MP camera photo lands at a few
 *  hundred KB, not tens of megabytes — Coil's own decode-time downsampling ([LocalImage]) still
 *  applies on top of this at each call site's own smaller display size. */
private const val STORED_LONG_EDGE_TARGET_PX = 1024
private const val STORED_JPEG_QUALITY = 85

@Singleton
class ExerciseMediaStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExerciseMediaStore {
    override suspend fun copyToAppStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "exercise_media").apply { mkdirs() }
        val relativePath = "exercise_media/${UUID.randomUUID()}.jpg"
        val destination = File(context.filesDir, relativePath)
        runCatching {
            val bitmap = decodeDownsampled(uri) ?: return@withContext null
            try {
                var encoded = false
                destination.outputStream().use { output ->
                    // M20b: re-encoded as JPEG regardless of the source format (the destination
                    // name already promised ".jpg" before this milestone) -- quality 85 is the
                    // standard "visually lossless, meaningfully smaller" compromise. compress()
                    // returns false (rather than throwing) on encoder failure -- checked below so
                    // a bad encode doesn't get treated as a saved photo.
                    // NOT handled: EXIF orientation. The bitmap is stored unrotated; Coil's
                    // AsyncImage reads EXIF on *decode* of the original picked bytes only, so this
                    // step -- which re-encodes into a fresh file with no EXIF block -- would show a
                    // rotated portrait photo sideways. androidx.exifinterface is not on this
                    // project's classpath, and adding it is a second new dependency this milestone
                    // didn't get sign-off for; a future pass should either add it or rotate the
                    // Bitmap in memory using the source's EXIF orientation before compressing.
                    encoded = bitmap.compress(Bitmap.CompressFormat.JPEG, STORED_JPEG_QUALITY, output)
                }
                if (!encoded) {
                    destination.delete()
                    return@withContext null
                }
            } finally {
                bitmap.recycle()
            }
        }.onFailure {
            destination.delete()
            return@withContext null
        }
        relativePath
    }

    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream(..., inJustDecodeBounds = true) always returns null by contract -- it only
        // has the side effect of populating bounds.outWidth/outHeight -- so the stream's own
        // nullability must be checked separately, never via the decode call's return value.
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = inSampleSizeFor(bounds.outWidth, bounds.outHeight, STORED_LONG_EDGE_TARGET_PX)
        }
        val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
        return decodeStream.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
