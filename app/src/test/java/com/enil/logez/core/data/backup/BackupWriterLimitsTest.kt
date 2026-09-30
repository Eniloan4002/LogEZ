package com.enil.logez.core.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.repository.RoomTransactionRunner
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeSettingsRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan, O1d: LogEZ must not write a backup its own reader would refuse. The limits are
 * passed in small; the app uses [ZipEntryNames]' 2 GB and 50,000 files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupWriterLimitsTest : RoomDatabaseTestBase() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun filesDir(): File = File(context.filesDir, "writer_${System.nanoTime()}").apply { mkdirs() }

    private fun writer(dir: File): BackupWriter {
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(dir, "settings.preferences_pb")
        }
        val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
        return BackupWriter(database.backupDao(), FakeSettingsRepository(), seedManager, FakeClock())
    }

    /** One progress photo of [bytes] bytes, referenced by its row. */
    private suspend fun onePhoto(dir: File, bytes: Int) {
        File(dir, "progress_photos/p1.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }
        database.backupDao().insertProgressPhotos(listOf(ProgressPhotoEntity("p1", "2026-09-22", "progress_photos/p1.jpg", 1)))
    }

    private fun assertRefused(block: () -> Unit) {
        try {
            block()
            fail("expected BackupTooLargeException")
        } catch (e: BackupWriter.BackupTooLargeException) {
            // expected
        }
    }

    @Test
    fun `photos over the size limit are refused before anything is written`() = runBlocking {
        val dir = filesDir()
        onePhoto(dir, bytes = 5_000)
        val out = ByteArrayOutputStream()
        assertRefused { runBlocking { writer(dir).write(out, dir, "0.1.0", 1, maxTotalBytes = 4_000) } }
        assertEquals(0, out.size())
    }

    @Test
    fun `more files than the reader accepts are refused before anything is written`() = runBlocking {
        val dir = filesDir()
        onePhoto(dir, bytes = 10)
        val out = ByteArrayOutputStream()
        // The manifest, the settings and 14 tables make 16 entries; the photo makes 17.
        assertRefused { runBlocking { writer(dir).write(out, dir, "0.1.0", 1, maxEntries = 16) } }
        assertEquals(0, out.size())
    }

    /** [count] completed workouts, so the rows are a real share of the backup's bytes. */
    private suspend fun workouts(count: Int) {
        database.backupDao().insertWorkoutsBulk(
            (1..count).map { i ->
                WorkoutEntity(
                    "w$i", null, "Session $i", null, WorkoutStatus.COMPLETED, 100L + i, 200L + i, 3600, 100L + i, 200L + i,
                    WorkoutStructure.REGULAR, WorkoutKind.STRENGTH,
                )
            },
        )
    }

    /** Every byte the reader unpacks from [writer]'s unlimited archive: what the writer must count. */
    private suspend fun unpackedTotal(writer: BackupWriter, dir: File): Long {
        val unlimited = ByteArrayOutputStream().also { writer.write(it, dir, "0.1.0", 1) }
        val staging = File(dir, "staging_${System.nanoTime()}")
        BackupReader().stage(ByteArrayInputStream(unlimited.toByteArray()), staging)
        return staging.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    @Test
    fun `rows that take the total one byte over the limit are refused while writing`(): Unit = runBlocking {
        val dir = filesDir()
        // No photos, so the up-front check passes and only the rows' own count can refuse it.
        workouts(200)
        val writer = writer(dir)
        val total = unpackedTotal(writer, dir)

        assertRefused { runBlocking { writer.write(ByteArrayOutputStream(), dir, "0.1.0", 1, maxTotalBytes = total - 1) } }
        writer.write(ByteArrayOutputStream(), dir, "0.1.0", 1, maxTotalBytes = total)
    }

    @Test
    fun `a photo that takes the total one byte over the limit is refused`(): Unit = runBlocking {
        val dir = filesDir()
        onePhoto(dir, bytes = 10)
        workouts(3)
        val writer = writer(dir)
        val total = unpackedTotal(writer, dir)

        // The photo alone is far under the limit, so only the running count can refuse it.
        assertRefused { runBlocking { writer.write(ByteArrayOutputStream(), dir, "0.1.0", 1, maxTotalBytes = total - 1) } }
        writer.write(ByteArrayOutputStream(), dir, "0.1.0", 1, maxTotalBytes = total)
    }

    @Test
    fun `a backup exactly at the limits is written, and the reader accepts it`() = runBlocking {
        val dir = filesDir()
        onePhoto(dir, bytes = 10)
        val writer = writer(dir)
        val unlimited = ByteArrayOutputStream().also { writer.write(it, dir, "0.1.0", 1) }
        val staged = BackupReader().stage(ByteArrayInputStream(unlimited.toByteArray()), File(dir, "staging"))
        val total = File(dir, "staging").walkTopDown().filter { it.isFile }.sumOf { it.length() }

        val out = ByteArrayOutputStream()
        writer.write(out, dir, "0.1.0", 1, maxTotalBytes = total, maxEntries = 17)
        assertEquals(1, staged.manifest.mediaFileCount)
    }
}
