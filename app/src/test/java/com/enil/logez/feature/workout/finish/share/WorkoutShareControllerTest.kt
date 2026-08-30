package com.enil.logez.feature.workout.finish.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Export contract only. The GraphicsLayer capture itself isn't exercised here — recording and
 * rasterizing a composition's layer under Robolectric is not reliable — so the tests feed the
 * controller a synthetic bitmap at the exact SQUARE export resolution instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutShareControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = WorkoutShareController(context)
    private val sharedImagesDir = File(context.cacheDir, "shared_images")

    // One test method on purpose: FileProvider caches its path strategy in a static map keyed by
    // authority, while Robolectric gives every test method a fresh temp dataDir — a second method
    // calling exportPng would resolve files against the first method's (now stale) cached root
    // and throw. Both halves of the contract therefore run as phases of the same export sequence.
    @Test
    fun `export writes a decodable 1080px png behind a content uri and prunes only stale prior exports`() = runTest {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF39FF6E.toInt()) }

        val uri = controller.exportPng(bitmap, ShareCardFormat.SQUARE)

        assertEquals("content", uri.scheme)
        assertEquals("com.enil.logez.fileprovider", uri.authority)
        val firstFile = sharedImagesDir.listFiles()!!.single()
        val signature = firstFile.inputStream().use { it.readNBytes(8) }
        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            signature,
        )
        val decoded = BitmapFactory.decodeFile(firstFile.path)!!
        assertEquals(1080, decoded.width)
        assertEquals(1080, decoded.height)

        // Second export: the fresh prior export survives (a share target may still hold a lazy
        // read grant on it), while one past the grace window is pruned.
        val stale = File(sharedImagesDir, "logez_workout_square_0.png").apply {
            writeBytes(byteArrayOf(1))
            assertTrue(setLastModified(1L))
        }
        controller.exportPng(bitmap, ShareCardFormat.STORY)
        val names = sharedImagesDir.listFiles()!!.map { it.name }
        assertEquals(2, names.size)
        assertTrue(names.contains(firstFile.name))
        assertTrue(names.any { it.startsWith("logez_workout_story_") })
        assertFalse(names.contains(stale.name))
    }
}
