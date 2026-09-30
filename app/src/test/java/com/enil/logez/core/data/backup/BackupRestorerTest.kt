package com.enil.logez.core.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.data.repository.ExerciseRepositoryImpl
import com.enil.logez.core.data.repository.MeasurementRepositoryImpl
import com.enil.logez.core.data.repository.PersonalRecordsRepositoryImpl
import com.enil.logez.core.data.repository.RoomTransactionRunner
import com.enil.logez.core.data.repository.WorkoutRepositoryImpl
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWidgetRefresher
import com.enil.logez.fakes.GatedTransactionRunner
import com.enil.logez.core.domain.repository.TransactionRunner
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real restorer, with the real transaction runner, the real record rebuild and a real
 * preferences store — everything the DAO-level round trip deliberately bypassed. Run under a
 * timeout so a deadlock inside the transaction reads as a failure rather than a hung build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestorerTest : RoomDatabaseTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val seedVersionKey = intPreferencesKey("lastAppliedSeedVersion")

    private fun filesDir(): File = File(context.filesDir, "restorer_${System.nanoTime()}").apply { mkdirs() }

    private fun dataStore(dir: File) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) { File(dir, "settings.preferences_pb") }

    private fun exercise(id: String) = ExerciseEntity(
        id = id, name = "Exercise $id", exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
        equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false,
        createdAt = 1, updatedAt = 1, muscleHeads = emptyList(), deprecatedPrimaryMuscleHead = null,
    )

    private suspend fun seedOneWorkout() {
        val dao = database.backupDao()
        dao.insertExercises(listOf(exercise("e1")))
        dao.insertWorkoutsBulk(
            listOf(
                WorkoutEntity(
                    "w1", null, "Push Day", null, WorkoutStatus.COMPLETED, 100, 200, 3600, 100, 200,
                    WorkoutStructure.REGULAR, WorkoutKind.STRENGTH,
                ),
            ),
        )
        dao.insertWorkoutExercisesBulk(listOf(WorkoutExerciseEntity("we1", "w1", "e1", 0, null, null, null)))
        dao.insertWorkoutSetsBulk(
            listOf(WorkoutSetEntity("ws1", "we1", 0, SetType.NORMAL, 100.0, 5, null, null, null, null, true, 150)),
        )
    }

    private fun restorer(
        settings: FakeSettingsRepository,
        seedManager: SeedManager,
        widget: FakeWidgetRefresher,
        lock: RestoreLock = RestoreLock(),
        transactionRunner: TransactionRunner = RoomTransactionRunner(database),
    ) = BackupRestorer(
        backupDao = database.backupDao(),
        reader = BackupReader(),
        transactionRunner = transactionRunner,
        personalRecordsUpdater = PersonalRecordsUpdater(
            WorkoutRepositoryImpl(database.workoutDao(), database.analyticsDao()),
            ExerciseRepositoryImpl(database.exerciseDao(), FakeClock()),
            PersonalRecordsRepositoryImpl(database.recordsDao()),
            MeasurementRepositoryImpl(database.measurementDao()),
            settings,
        ),
        settingsRepository = settings,
        seedManager = seedManager,
        widgetRefresher = widget,
        restoreLock = lock,
        logger = AppLogger.NoOp,
    )

    @Test
    fun `restore brings back a deleted workout, rebuilds its records, and cleans up after itself`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository(UserSettings(weeklyActiveDayTarget = 6))
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
            val widget = FakeWidgetRefresher()

            seedOneWorkout()
            val writer = BackupWriter(database.backupDao(), settings, seedManager, FakeClock())
            val archive = ByteArrayOutputStream().also { writer.write(it, dir, "0.1.0", 1) }.toByteArray()

            // Lose the workout, then change a setting, so the restore has something to undo.
            database.workoutDao().deleteWorkoutById("w1")
            settings.setWeeklyActiveDayTarget(2)
            assertNull(database.workoutDao().getById("w1"))

            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)
            restorer(settings, seedManager, widget).restore(staging, dir)

            assertEquals("Push Day", database.workoutDao().getById("w1")?.title)
            assertTrue(database.recordsDao().getForExercise("e1").isNotEmpty())
            assertEquals(6, settings.settings.first().weeklyActiveDayTarget)
            assertFalse(File(dir, "restore.marker").exists())
            assertFalse(staging.exists())
            assertTrue(widget.refreshCount >= 1)
        }
    }

    @Test
    fun `restore resets the seed marker so an older library cannot strand the device`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)

            seedOneWorkout()
            val archive = ByteArrayOutputStream()
                .also { BackupWriter(database.backupDao(), settings, seedManager, FakeClock()).write(it, dir, "0.1.0", 1) }
                .toByteArray()

            // The device believes it is on a newer seed than the backup carries.
            store.edit { it[seedVersionKey] = 99 }

            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)
            restorer(settings, seedManager, FakeWidgetRefresher()).restore(staging, dir)

            // Re-seeding ran: the marker is no longer the stale 99, it is whatever the asset says.
            val after = store.data.first()[seedVersionKey]
            assertTrue("seed marker should have been reset and re-applied, was $after", after != 99)
        }
    }

    /**
     * 2026-09-25 review: a restore interrupted after its photo swap but before the marker was
     * cleared used to move the (already restored) live photos aside and delete them on resume,
     * because nothing was left staged. Staging now always creates both media folders, so a
     * missing one means "already swapped" and the live folder is left alone.
     */
    @Test
    fun `resuming after the photo swap already happened keeps the restored photos`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)

            // State after swapMedia moved both staged folders into place: live photos present,
            // the staging dir left with no media folders, the marker still at MEDIA.
            val livePhoto = File(dir, "progress_photos/p1.jpg").apply { parentFile!!.mkdirs(); writeText("restored") }
            File(dir, "exercise_media").mkdirs()
            val staging = RestoreMarker.stagingDirIn(dir).apply { mkdirs() }
            RestoreMarker(dir).write(RestoreMarker.Phase.MEDIA, staging)

            restorer(settings, seedManager, FakeWidgetRefresher()).resumeIfInterrupted(dir)

            assertTrue("restored photo must survive the resume", livePhoto.isFile)
            assertEquals("restored", livePhoto.readText())
            assertFalse(File(dir, "restore.marker").exists())
        }
    }

    @Test
    fun `staging always creates both media folders, even for a backup without photos`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
            seedOneWorkout()
            val archive = ByteArrayOutputStream()
                .also { BackupWriter(database.backupDao(), settings, seedManager, FakeClock()).write(it, dir, "0.1.0", 1) }
                .toByteArray()

            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)

            assertTrue(File(staging, "media/progress_photos").isDirectory)
            assertTrue(File(staging, "media/exercise_media").isDirectory)
        }
    }

    @Test
    fun `a leftover staged restore with no marker is deleted at launch`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
            val staging = RestoreMarker.stagingDirIn(dir).apply { mkdirs() }
            File(staging, "media/progress_photos/p1.jpg").apply { parentFile!!.mkdirs(); writeText("x") }

            restorer(settings, seedManager, FakeWidgetRefresher()).resumeIfInterrupted(dir)

            assertFalse(staging.exists())
        }
    }

    // ---- First-run plan O1d ----

    private fun seedManager(dir: File) =
        SeedManager(context, database.exerciseDao(), dataStore(dir), RoomTransactionRunner(database), AppLogger.NoOp)

    private suspend fun archiveOf(settings: FakeSettingsRepository, seedManager: SeedManager, dir: File): ByteArray =
        ByteArrayOutputStream()
            .also { BackupWriter(database.backupDao(), settings, seedManager, FakeClock()).write(it, dir, "0.1.0", 1) }
            .toByteArray()

    /** w2 is IN_PROGRESS and has a child row in every table that hangs off a workout. */
    private suspend fun seedOneUnfinishedWorkout() {
        val dao = database.backupDao()
        dao.insertWorkoutsBulk(
            listOf(
                WorkoutEntity(
                    "w2", null, "Evening walk", null, WorkoutStatus.IN_PROGRESS, 300, null, 0, 300, 300,
                    WorkoutStructure.REGULAR, WorkoutKind.GPS_TRACKED,
                ),
            ),
        )
        dao.insertWorkoutExercisesBulk(listOf(WorkoutExerciseEntity("we2", "w2", "e1", 0, null, null, null)))
        dao.insertWorkoutSetsBulk(
            listOf(WorkoutSetEntity("ws2", "we2", 0, SetType.NORMAL, null, null, 600, 1200.0, null, null, false, null)),
        )
        dao.insertActivityTracks(listOf(ActivityTrackEntity("t2", "ws2", "_p~iF~ps|U", 2, 4.0, routeTimes = null)))
        dao.insertHeartRateSamples(listOf(WorkoutHeartRateSampleEntity("hr2", "w2", 310, 120)))
    }

    @Test
    fun `unfinished workouts in a backup are counted at staging, then left out with every child row`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val settings = FakeSettingsRepository()
            val seedManager = seedManager(dir)
            seedOneWorkout()
            seedOneUnfinishedWorkout()
            val archive = archiveOf(settings, seedManager, dir)

            val staging = RestoreMarker.stagingDirIn(dir)
            val staged = BackupReader().stage(ByteArrayInputStream(archive), staging)
            assertEquals(1, staged.unfinishedWorkoutCount)
            assertEquals(2, staged.manifest.workoutCount)
            assertEquals(1, staged.completedWorkoutCount)

            val result = restorer(settings, seedManager, FakeWidgetRefresher()).restore(staging, dir)

            assertEquals(RestoreResult(unfinishedWorkoutsLeftOut = 1), result)
            val dao = database.backupDao()
            assertNull(database.workoutDao().getById("w2"))
            assertEquals("Push Day", database.workoutDao().getById("w1")?.title)
            assertEquals(1, dao.countWorkouts())
            assertEquals(1, dao.countWorkoutExercises())
            assertEquals(1, dao.countWorkoutSets())
            assertEquals(0, dao.countActivityTracks())
            assertEquals(0, dao.countHeartRateSamples())
        }
    }

    @Test
    fun `a backup with no unfinished workouts reports none left out`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val settings = FakeSettingsRepository()
            val seedManager = seedManager(dir)
            seedOneWorkout()
            val archive = archiveOf(settings, seedManager, dir)

            val staging = RestoreMarker.stagingDirIn(dir)
            val staged = BackupReader().stage(ByteArrayInputStream(archive), staging)
            assertEquals(0, staged.unfinishedWorkoutCount)
            assertTrue(staged.hasSettings)

            val result = restorer(settings, seedManager, FakeWidgetRefresher()).restore(staging, dir)
            assertEquals(RestoreResult(unfinishedWorkoutsLeftOut = 0), result)
        }
    }

    @Test
    fun `a settings failure after the commit is reported as post-commit, and the next launch finishes it`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val settings = FakeSettingsRepository(UserSettings(weeklyActiveDayTarget = 6))
            val seedManager = seedManager(dir)
            seedOneWorkout()
            val archive = archiveOf(settings, seedManager, dir)
            database.workoutDao().deleteWorkoutById("w1")
            settings.setWeeklyActiveDayTarget(2)

            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)
            settings.replaceAllError = IllegalStateException("preferences store unavailable")
            val restorer = restorer(settings, seedManager, FakeWidgetRefresher())

            try {
                restorer.restore(staging, dir)
                fail("expected PostCommitRestoreException")
            } catch (e: PostCommitRestoreException) {
                assertEquals("preferences store unavailable", e.cause?.message)
            }
            // The database committed: the workout is back, so "your data is unchanged" would be false.
            assertEquals("Push Day", database.workoutDao().getById("w1")?.title)
            assertEquals(RestoreMarker.Phase.MEDIA, RestoreMarker(dir).read())
            assertTrue(staging.isDirectory)

            settings.replaceAllError = null
            restorer.resumeIfInterrupted(dir)

            assertEquals(6, settings.settings.first().weeklyActiveDayTarget)
            assertNull(RestoreMarker(dir).read())
            assertFalse(staging.exists())
        }
    }

    @Test
    fun `a photo swap failure after the commit is reported as post-commit`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val settings = FakeSettingsRepository()
            val seedManager = seedManager(dir)
            seedOneWorkout()
            val archive = archiveOf(settings, seedManager, dir)
            database.workoutDao().deleteWorkoutById("w1")

            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)
            File(staging, "media/progress_photos/p1.jpg").writeText("photo")
            // The marker file exists beforehand, so it can still be rewritten once the folder is
            // read-only; the swap then can't create the live photo folder and throws.
            RestoreMarker(dir).write(RestoreMarker.Phase.DATABASE, staging)
            // Root ignores the read-only bit, so the swap would not fail there.
            assumeFalse("root ignores file permissions", System.getProperty("user.name") == "root")
            assertTrue(dir.setWritable(false))
            try {
                try {
                    restorer(settings, seedManager, FakeWidgetRefresher()).restore(staging, dir)
                    fail("expected PostCommitRestoreException")
                } catch (e: PostCommitRestoreException) {
                    // expected
                }
            } finally {
                dir.setWritable(true)
            }
            assertEquals("Push Day", database.workoutDao().getById("w1")?.title)
            assertEquals(RestoreMarker.Phase.MEDIA, RestoreMarker(dir).read())
            // It was the swap that failed: the photo never reached the live folder, and it is still
            // staged for the next launch to move.
            assertFalse(File(dir, "progress_photos/p1.jpg").exists())
            assertTrue(File(staging, "media/progress_photos/p1.jpg").isFile)
        }
    }

    @Test
    fun `settings with a value this build does not know are refused before anything is replaced`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val settings = FakeSettingsRepository(UserSettings(weeklyActiveDayTarget = 3))
            val seedManager = seedManager(dir)
            seedOneWorkout()
            val archive = archiveOf(settings, seedManager, dir)
            database.workoutDao().deleteWorkoutById("w1")

            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)
            val settingsFile = File(staging, BackupFormat.SETTINGS_ENTRY)
            settingsFile.writeText(settingsFile.readText().replace("\"weight_unit\":\"KG\"", "\"weight_unit\":\"STONE\""))

            try {
                restorer(settings, seedManager, FakeWidgetRefresher()).restore(staging, dir)
                fail("expected UnknownSettingValueException")
            } catch (e: UnknownSettingValueException) {
                assertEquals("weight_unit", e.field)
                assertEquals("STONE", e.value)
            }
            // Nothing was replaced, and nothing is left for the next launch to retry.
            assertNull(database.workoutDao().getById("w1"))
            assertEquals(3, settings.settings.first().weeklyActiveDayTarget)
            assertNull(RestoreMarker(dir).read())
            assertFalse(staging.exists())
        }
    }

    @Test
    fun `resuming with staged settings this build does not know skips them and stops retrying`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val settings = FakeSettingsRepository(UserSettings(weeklyActiveDayTarget = 5))
            val seedManager = seedManager(dir)
            // Left by an older build: the database committed, the marker is at MEDIA, and the
            // staged settings name a unit this build lacks.
            val staging = RestoreMarker.stagingDirIn(dir).apply { mkdirs() }
            File(staging, BackupFormat.SETTINGS_ENTRY).writeText("""{"weight_unit":"STONE","weekly_active_day_target":2}""")
            RestoreMarker(dir).write(RestoreMarker.Phase.MEDIA, staging)

            restorer(settings, seedManager, FakeWidgetRefresher()).resumeIfInterrupted(dir)

            assertEquals(5, settings.settings.first().weeklyActiveDayTarget)
            assertNull(RestoreMarker(dir).read())
            assertFalse(staging.exists())
        }
    }

    @Test
    fun `the launch-time resume waits for a restore holding the lock before clearing staging`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val lock = RestoreLock()
            val screen = Any()
            assertTrue(lock.tryAcquire(screen))
            val staging = RestoreMarker.stagingDirIn(dir).apply { mkdirs() }
            File(staging, BackupFormat.MANIFEST_ENTRY).writeText("{}")

            val resume = async(Dispatchers.IO) {
                restorer(FakeSettingsRepository(), seedManager(dir), FakeWidgetRefresher(), lock).resumeIfInterrupted(dir)
            }
            delay(300)
            assertTrue("staging must survive while another restore holds the lock", staging.isDirectory)
            assertFalse(resume.isCompleted)

            lock.release(screen)
            resume.await()
            assertFalse(staging.exists())
            assertFalse(lock.held.value)
        }
    }

    /**
     * The first-launch seed and a restore, overlapping (plan: "Restore seconds after first launch").
     * The backup holds a custom exercise and a workout that used it, but no seeded rows; the device
     * is a fresh install with nothing seeded yet. Returns the staged copy, the seed store and the
     * settings the two orders below share.
     */
    private suspend fun freshInstallWithStagedBackup(dir: File): Triple<File, DataStore<Preferences>, FakeSettingsRepository> {
        val store = dataStore(dir)
        val settings = FakeSettingsRepository()
        val dao = database.backupDao()
        dao.insertExercises(listOf(exercise("c1").copy(name = "My Cable Curl", isCustom = true)))
        dao.insertWorkoutsBulk(
            listOf(
                WorkoutEntity(
                    "w1", null, "Arms", null, WorkoutStatus.COMPLETED, 100, 200, 3600, 100, 200,
                    WorkoutStructure.REGULAR, WorkoutKind.STRENGTH,
                ),
            ),
        )
        dao.insertWorkoutExercisesBulk(listOf(WorkoutExerciseEntity("we1", "w1", "c1", 0, null, null, null)))
        dao.insertWorkoutSetsBulk(
            listOf(WorkoutSetEntity("ws1", "we1", 0, SetType.NORMAL, 20.0, 12, null, null, null, null, true, 150)),
        )
        val archive = archiveOf(settings, SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp), dir)

        database.runInTransactionForTest { BackupTables.WIPE_ORDER.forEach { dao.wipe(it) } }
        val staging = RestoreMarker.stagingDirIn(dir)
        BackupReader().stage(ByteArrayInputStream(archive), staging)
        return Triple(staging, store, settings)
    }

    private suspend fun assertBackupAndFullLibrary(store: DataStore<Preferences>) {
        assertEquals("Arms", database.workoutDao().getById("w1")?.title)
        val custom = database.exerciseDao().getById("c1")
        assertEquals("My Cable Curl", custom?.name)
        assertEquals(true, custom?.isCustom)
        assertEquals(false, custom?.isDeleted)
        assertEquals(400, database.exerciseDao().seedCount())
        assertEquals(3, store.data.first()[seedVersionKey])
    }

    @Test
    fun `a first seed that commits before the restore is wiped, then re-seeded by the restore`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val (staging, store, settings) = freshInstallWithStagedBackup(dir)
            // The first seed commits its 400 rows, then pauses before recording its version.
            val seedRunner = GatedTransactionRunner(RoomTransactionRunner(database))
            val seedManager = SeedManager(context, database.exerciseDao(), store, seedRunner, AppLogger.NoOp)
            val restoreRunner = GatedTransactionRunner(RoomTransactionRunner(database), holdAfterCommit = false)
            val restorer = restorer(settings, seedManager, FakeWidgetRefresher(), transactionRunner = restoreRunner)

            val firstSeed = async(Dispatchers.IO) { seedManager.seedIfNeeded() }
            seedRunner.committed.await()
            assertEquals(400, database.exerciseDao().seedCount())

            val restore = async(Dispatchers.IO) { restorer.restore(staging, dir) }
            restoreRunner.committed.await()
            // The restore's wipe took the seeded rows with it.
            assertEquals(0, database.exerciseDao().seedCount())

            seedRunner.proceed.complete(Unit)
            awaitAll(firstSeed, restore)

            assertBackupAndFullLibrary(store)
        }
    }

    @Test
    fun `a first seed that runs after the restore's commit leaves the backup and the library in place`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val (staging, store, settings) = freshInstallWithStagedBackup(dir)
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
            // The restore commits, then pauses before its photo swap, settings and re-seed.
            val restoreRunner = GatedTransactionRunner(RoomTransactionRunner(database))
            val restorer = restorer(settings, seedManager, FakeWidgetRefresher(), transactionRunner = restoreRunner)

            val restore = async(Dispatchers.IO) { restorer.restore(staging, dir) }
            restoreRunner.committed.await()
            assertEquals(0, database.exerciseDao().seedCount())

            seedManager.seedIfNeeded()
            assertEquals(400, database.exerciseDao().seedCount())

            restoreRunner.proceed.complete(Unit)
            restore.await()

            assertBackupAndFullLibrary(store)
        }
    }

    @Test
    fun `a staged copy that lost a table before the restore is refused, and nothing is replaced`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val settings = FakeSettingsRepository()
            val seedManager = seedManager(dir)
            seedOneWorkout()
            val archive = archiveOf(settings, seedManager, dir)
            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)
            database.workoutDao().deleteWorkoutById("w1")
            database.backupDao().insertWorkoutsBulk(
                listOf(
                    WorkoutEntity(
                        "w7", null, "Kept", null, WorkoutStatus.COMPLETED, 100, 200, 3600, 100, 200,
                        WorkoutStructure.REGULAR, WorkoutKind.STRENGTH,
                    ),
                ),
            )
            // As if a cancel's cleanup had deleted part of the staged copy under the restore.
            assertTrue(File(staging, BackupTables.tableEntry(BackupTables.WORKOUTS)).delete())

            try {
                restorer(settings, seedManager, FakeWidgetRefresher()).restore(staging, dir)
                fail("expected NotABackupException")
            } catch (e: BackupReader.NotABackupException) {
                // expected
            }
            assertEquals("Kept", database.workoutDao().getById("w7")?.title)
            assertNull(database.workoutDao().getById("w1"))
            assertNull(RestoreMarker(dir).read())
        }
    }

    private suspend fun com.enil.logez.core.data.LogEzDatabase.runInTransactionForTest(block: suspend () -> Unit) =
        RoomTransactionRunner(this).runInTransaction(block)
}
