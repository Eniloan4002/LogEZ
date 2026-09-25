package com.enil.logez.core.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The file half of Settings > Data > Delete all data. The table half is the same WIPE_ORDER
 * transaction BackupRestorerTest already covers against a real database.
 */
class LocalDataEraserFilesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `deletes both photo directories and the whole cache, and nothing else`() {
        val filesDir = temp.newFolder("files")
        val cacheDir = temp.newFolder("cache")
        File(filesDir, "progress_photos").mkdirs(); File(filesDir, "progress_photos/a.jpg").writeText("x")
        File(filesDir, "exercise_media").mkdirs(); File(filesDir, "exercise_media/b.jpg").writeText("x")
        File(cacheDir, "shared_images").mkdirs(); File(cacheDir, "shared_images/summary.png").writeText("x")
        File(cacheDir, "capture.jpg").writeText("x")
        val unrelated = File(filesDir, "datastore/settings.preferences_pb").apply { parentFile!!.mkdirs(); writeText("x") }

        deleteMediaAndCache(filesDir, cacheDir)

        assertFalse(File(filesDir, "progress_photos").exists())
        assertFalse(File(filesDir, "exercise_media").exists())
        assertEquals(0, cacheDir.listFiles()!!.size)
        assertTrue("the cache directory itself stays", cacheDir.isDirectory)
        assertTrue("only media and cache are deleted", unrelated.exists())
    }
}
