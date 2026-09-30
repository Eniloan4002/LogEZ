package com.enil.logez.core.data.backup

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.dao.BackupDao
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.core.domain.repository.SettingsRepository
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.first

/**
 * Writes a backup archive into a stream.
 *
 * Takes an [OutputStream] rather than anything Android-shaped, so the whole format — including the
 * full export-then-restore round trip — is testable without a document provider.
 *
 * Rows are written a page at a time as JSON Lines rather than as one document per table, so
 * neither this nor a restore has to hold a whole table in memory. The manifest goes first so a
 * restore can show the user real numbers after reading one small entry.
 */
@Singleton
class BackupWriter @Inject constructor(
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val seedManager: SeedManager,
    private val clock: Clock,
) {
    /**
     * A backup this app's own reader would refuse: more than [ZipEntryNames.MAX_TOTAL_UNCOMPRESSED_BYTES]
     * unpacked, or more than [ZipEntryNames.MAX_ENTRIES] files. Refused rather than written, since
     * a backup that can't be restored is worse than none (first-run plan, O1d).
     */
    class BackupTooLargeException(message: String) : IOException(message)

    /**
     * @param mediaRoot the app's filesDir; photo paths in the rows are relative to it.
     * @param maxTotalBytes and [maxEntries] are the reader's limits; tests pass smaller ones.
     * @throws BackupTooLargeException before anything is written when the photos alone exceed the
     *   limits, or part-way through when the rows take the total over. The caller deletes the file.
     */
    suspend fun write(
        out: OutputStream,
        mediaRoot: File,
        appVersionName: String,
        appVersionCode: Long,
        maxTotalBytes: Long = ZipEntryNames.MAX_TOTAL_UNCOMPRESSED_BYTES,
        maxEntries: Int = ZipEntryNames.MAX_ENTRIES,
    ): BackupManifest {
        val counts = countsPerTable()
        // Only files a row still references (2026-09-25). Zipping the whole directory also shipped
        // every photo the user had deleted, since deleting a row used to leave its file behind.
        val referenced = backupDao.referencedMediaPaths().toSet()
        val mediaFiles = mediaDirectories(mediaRoot).flatMap { dir ->
            dir.listFiles().orEmpty()
                .filter { it.isFile && "${dir.name}/${it.name}" in referenced }
                .map { dir.name to it }
        }

        // Checked first, on the same measures BackupReader.stage applies: the entry count and the
        // unpacked bytes. Photos are nearly all of a backup's size and their sizes are known now;
        // the rows are counted as they are written, below.
        val entryCount = FIXED_ENTRY_COUNT + mediaFiles.size
        val mediaBytes = mediaFiles.sumOf { (_, file) -> file.length() }
        if (entryCount > maxEntries) {
            throw BackupTooLargeException("A backup would hold $entryCount files; the limit is $maxEntries")
        }
        if (mediaBytes > maxTotalBytes) {
            throw BackupTooLargeException("Photos alone come to $mediaBytes bytes; the limit is $maxTotalBytes")
        }
        val budget = ByteBudget(maxTotalBytes)

        val manifest = BackupManifest(
            backupSchemaVersion = BackupFormat.SCHEMA_VERSION,
            roomSchemaVersion = ROOM_SCHEMA_VERSION,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            seedVersion = seedManager.lastAppliedSeedVersion(),
            exportedAtEpochMillis = clock.now().toEpochMilliseconds(),
            exportedAtZoneId = ZoneId.systemDefault().id,
            tables = BackupTables.EXPORT_ORDER.map { BackupManifest.TableEntry(it, counts.getValue(it)) },
            mediaFileCount = mediaFiles.size,
        )

        ZipOutputStream(out.buffered()).use { zip ->
            zip.writeText(BackupFormat.MANIFEST_ENTRY, BackupFormat.jsonWrite.encodeToString(manifest), budget)

            BackupTables.EXPORT_ORDER.forEach { table ->
                zip.putNextEntry(ZipEntry(BackupTables.tableEntry(table)))
                writeTable(zip, table, budget)
                zip.closeEntry()
            }

            zip.writeText(
                BackupFormat.SETTINGS_ENTRY,
                BackupFormat.jsonWrite.encodeToString(settingsRepository.settings.first().toDto()),
                budget,
            )

            mediaFiles.forEach { (dirName, file) ->
                budget.add(file.length())
                zip.putNextEntry(ZipEntry("${BackupFormat.MEDIA_PREFIX}$dirName/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return manifest
    }

    /** Unpacked bytes so far, refused past the reader's limit before they are written. */
    private class ByteBudget(private val max: Long) {
        private var used = 0L

        fun add(bytes: Long) {
            used += bytes
            if (used > max) throw BackupTooLargeException("A backup would unpack to more than $max bytes")
        }
    }

    private fun ZipOutputStream.writeText(entryName: String, text: String, budget: ByteBudget) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        budget.add(bytes.size.toLong())
        putNextEntry(ZipEntry(entryName))
        write(bytes)
        closeEntry()
    }

    /** One JSON object per line, so a restore can decode without holding the table. */
    private suspend fun writeTable(zip: ZipOutputStream, table: String, budget: ByteBudget) {
        val json = BackupFormat.jsonWrite
        forEachPage(table) { rows ->
            val text = buildString {
                rows.forEach { row ->
                    append(encodeRow(json, table, row))
                    append('\n')
                }
            }
            val bytes = text.toByteArray(Charsets.UTF_8)
            budget.add(bytes.size.toLong())
            zip.write(bytes)
        }
    }

    private fun encodeRow(json: kotlinx.serialization.json.Json, table: String, row: Any): String = when (table) {
        BackupTables.EXERCISES -> json.encodeToString((row as com.enil.logez.core.data.entity.ExerciseEntity).toDto())
        BackupTables.ROUTINE_FOLDERS -> json.encodeToString((row as com.enil.logez.core.data.entity.RoutineFolderEntity).toDto())
        BackupTables.ROUTINES -> json.encodeToString((row as com.enil.logez.core.data.entity.RoutineEntity).toDto())
        BackupTables.ROUTINE_EXERCISES -> json.encodeToString((row as com.enil.logez.core.data.entity.RoutineExerciseEntity).toDto())
        BackupTables.ROUTINE_SETS -> json.encodeToString((row as com.enil.logez.core.data.entity.RoutineSetEntity).toDto())
        BackupTables.WORKOUTS -> json.encodeToString((row as com.enil.logez.core.data.entity.WorkoutEntity).toDto())
        BackupTables.WORKOUT_EXERCISES -> json.encodeToString((row as com.enil.logez.core.data.entity.WorkoutExerciseEntity).toDto())
        BackupTables.WORKOUT_SETS -> json.encodeToString((row as com.enil.logez.core.data.entity.WorkoutSetEntity).toDto())
        BackupTables.ACTIVITY_TRACKS -> json.encodeToString((row as com.enil.logez.core.data.entity.ActivityTrackEntity).toDto())
        BackupTables.WORKOUT_HEART_RATE_SAMPLES -> json.encodeToString((row as com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity).toDto())
        BackupTables.BODY_MEASUREMENTS -> json.encodeToString((row as com.enil.logez.core.data.entity.BodyMeasurementEntity).toDto())
        BackupTables.PROGRESS_PHOTOS -> json.encodeToString((row as com.enil.logez.core.data.entity.ProgressPhotoEntity).toDto())
        BackupTables.GOAL_DEFINITIONS -> json.encodeToString((row as com.enil.logez.core.data.entity.GoalDefinitionEntity).toDto())
        BackupTables.DAILY_WELLNESS_TOTALS -> json.encodeToString((row as com.enil.logez.core.data.entity.DailyWellnessTotalEntity).toDto())
        else -> error("no exporter for table $table")
    }

    /** Keyset paging: each page resumes from the last key seen, so nothing is rescanned. */
    private suspend fun forEachPage(table: String, consume: (List<Any>) -> Unit) {
        var after = ""
        while (true) {
            val page: List<Any> = readPage(table, after)
            if (page.isEmpty()) return
            consume(page)
            after = lastKey(table, page.last())
            if (page.size < BackupFormat.PAGE_SIZE) return
        }
    }

    private suspend fun readPage(table: String, after: String): List<Any> {
        val n = BackupFormat.PAGE_SIZE
        return when (table) {
            BackupTables.EXERCISES -> backupDao.pageExercises(after, n)
            BackupTables.ROUTINE_FOLDERS -> backupDao.pageRoutineFolders(after, n)
            BackupTables.ROUTINES -> backupDao.pageRoutines(after, n)
            BackupTables.ROUTINE_EXERCISES -> backupDao.pageRoutineExercises(after, n)
            BackupTables.ROUTINE_SETS -> backupDao.pageRoutineSets(after, n)
            BackupTables.WORKOUTS -> backupDao.pageWorkouts(after, n)
            BackupTables.WORKOUT_EXERCISES -> backupDao.pageWorkoutExercises(after, n)
            BackupTables.WORKOUT_SETS -> backupDao.pageWorkoutSets(after, n)
            BackupTables.ACTIVITY_TRACKS -> backupDao.pageActivityTracks(after, n)
            BackupTables.WORKOUT_HEART_RATE_SAMPLES -> backupDao.pageHeartRateSamples(after, n)
            BackupTables.BODY_MEASUREMENTS -> backupDao.pageBodyMeasurements(after, n)
            BackupTables.PROGRESS_PHOTOS -> backupDao.pageProgressPhotos(after, n)
            BackupTables.GOAL_DEFINITIONS -> backupDao.pageGoals(after, n)
            BackupTables.DAILY_WELLNESS_TOTALS -> backupDao.pageWellnessTotals(after, n)
            else -> error("no reader for table $table")
        }
    }

    /** The two date-keyed tables page on `date`; everything else pages on `id`. */
    private fun lastKey(table: String, row: Any): String = when (table) {
        BackupTables.BODY_MEASUREMENTS -> (row as com.enil.logez.core.data.entity.BodyMeasurementEntity).date
        BackupTables.DAILY_WELLNESS_TOTALS -> (row as com.enil.logez.core.data.entity.DailyWellnessTotalEntity).date
        BackupTables.EXERCISES -> (row as com.enil.logez.core.data.entity.ExerciseEntity).id
        BackupTables.ROUTINE_FOLDERS -> (row as com.enil.logez.core.data.entity.RoutineFolderEntity).id
        BackupTables.ROUTINES -> (row as com.enil.logez.core.data.entity.RoutineEntity).id
        BackupTables.ROUTINE_EXERCISES -> (row as com.enil.logez.core.data.entity.RoutineExerciseEntity).id
        BackupTables.ROUTINE_SETS -> (row as com.enil.logez.core.data.entity.RoutineSetEntity).id
        BackupTables.WORKOUTS -> (row as com.enil.logez.core.data.entity.WorkoutEntity).id
        BackupTables.WORKOUT_EXERCISES -> (row as com.enil.logez.core.data.entity.WorkoutExerciseEntity).id
        BackupTables.WORKOUT_SETS -> (row as com.enil.logez.core.data.entity.WorkoutSetEntity).id
        BackupTables.ACTIVITY_TRACKS -> (row as com.enil.logez.core.data.entity.ActivityTrackEntity).id
        BackupTables.WORKOUT_HEART_RATE_SAMPLES -> (row as com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity).id
        BackupTables.PROGRESS_PHOTOS -> (row as com.enil.logez.core.data.entity.ProgressPhotoEntity).id
        BackupTables.GOAL_DEFINITIONS -> (row as com.enil.logez.core.data.entity.GoalDefinitionEntity).id
        else -> error("no key for table $table")
    }

    private suspend fun countsPerTable(): Map<String, Int> = mapOf(
        BackupTables.EXERCISES to backupDao.countExercises(),
        BackupTables.ROUTINE_FOLDERS to backupDao.countRoutineFolders(),
        BackupTables.ROUTINES to backupDao.countRoutines(),
        BackupTables.ROUTINE_EXERCISES to backupDao.countRoutineExercises(),
        BackupTables.ROUTINE_SETS to backupDao.countRoutineSets(),
        BackupTables.WORKOUTS to backupDao.countWorkouts(),
        BackupTables.WORKOUT_EXERCISES to backupDao.countWorkoutExercises(),
        BackupTables.WORKOUT_SETS to backupDao.countWorkoutSets(),
        BackupTables.ACTIVITY_TRACKS to backupDao.countActivityTracks(),
        BackupTables.WORKOUT_HEART_RATE_SAMPLES to backupDao.countHeartRateSamples(),
        BackupTables.BODY_MEASUREMENTS to backupDao.countBodyMeasurements(),
        BackupTables.PROGRESS_PHOTOS to backupDao.countProgressPhotos(),
        BackupTables.GOAL_DEFINITIONS to backupDao.countGoals(),
        BackupTables.DAILY_WELLNESS_TOTALS to backupDao.countWellnessTotals(),
    )

    companion object {
        /** [com.enil.logez.core.data.LogEzDatabase]'s version, by reference so the two can't drift. */
        const val ROOM_SCHEMA_VERSION = com.enil.logez.core.data.LogEzDatabase.VERSION

        /** The manifest, the settings and one entry per table; photos add one each. */
        private val FIXED_ENTRY_COUNT = 2 + BackupTables.EXPORT_ORDER.size

        const val PROGRESS_PHOTOS_DIR = "progress_photos"
        const val EXERCISE_MEDIA_DIR = "exercise_media"

        fun mediaDirectories(filesDir: File): List<File> = listOf(
            File(filesDir, PROGRESS_PHOTOS_DIR),
            File(filesDir, EXERCISE_MEDIA_DIR),
        ).filter { it.isDirectory }
    }
}
