package com.enil.logez.feature.workout.finish.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform side-effect component for the summary-card share flow ([com.enil.logez.feature.workout.audio.WorkoutAudioPlayer]
 * precedent: never injected into a ViewModel — the screen captures the card and hands the bitmap
 * straight here). Local-only posture holds: the PNG lands in this app's own cache and leaves the
 * device only through the share target the user picks in the system chooser.
 */
@Singleton
class WorkoutShareController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * PNG-encodes [bitmap] into `cacheDir/shared_images/` under a unique per-export filename and
     * returns a grantable content Uri for it. Prior exports are NOT deleted immediately: a share
     * target picked in the chooser may read its granted Uri lazily (an email draft attaches at
     * send time), and deleting or overwriting the file behind it would break that read. Instead,
     * exports older than [STALE_EXPORT_MAX_AGE_MS] are pruned here — and this all lives in the
     * cache dir, which the OS may purge on its own regardless.
     */
    suspend fun exportPng(bitmap: Bitmap, format: ShareCardFormat): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARED_IMAGES_DIR)
        dir.mkdirs()
        val cutoff = System.currentTimeMillis() - STALE_EXPORT_MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(
            dir,
            "logez_workout_${format.name.lowercase(Locale.US)}_${System.currentTimeMillis()}.png",
        )
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed")
        }
        FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    /**
     * Generic ACTION_SEND chooser only — no Instagram story intent (that path requires a Meta app
     * registration). `clipData` mirrors the stream extra so targets can render a thumbnail;
     * [launchContext] is the activity context, since a chooser launched from the app UI should
     * stay in its task.
     */
    fun launchShareChooser(launchContext: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchContext.startActivity(chooser)
    }

    companion object {
        private const val AUTHORITY = "com.enil.logez.fileprovider"
        private const val SHARED_IMAGES_DIR = "shared_images"
        private const val MIME_TYPE_PNG = "image/png"

        /** Grace window before a prior export's file may be pruned (see [exportPng]). */
        private const val STALE_EXPORT_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

/** Composable access without routing an android.graphics type through the ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkoutShareEntryPoint {
    fun workoutShareController(): WorkoutShareController
}
