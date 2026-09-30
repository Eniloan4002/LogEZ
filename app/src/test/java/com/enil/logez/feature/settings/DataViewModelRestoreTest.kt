package com.enil.logez.feature.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.R
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.backup.BackupReader
import com.enil.logez.core.data.backup.BackupRestorer
import com.enil.logez.core.data.backup.BackupWriter
import com.enil.logez.core.data.backup.LocalDataEraser
import com.enil.logez.core.data.backup.PostCommitRestoreException
import com.enil.logez.core.data.backup.RestoreLock
import com.enil.logez.core.data.backup.RestoreMarker
import com.enil.logez.core.data.backup.UnknownSettingValueException
import com.enil.logez.core.data.backup.ZipEntryNames
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.data.export.CsvExporter
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
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.wellness.HealthConnectDisconnector
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeRegionDefaults
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWellnessRepository
import com.enil.logez.fakes.FakeWidgetRefresher
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Export & backup, first-run plan O1d: the full-backup refusal before the picker, the
 * restore lock, the left-out count and the failure messages that must say what is true. Real
 * reader, writer, restorer and Room; fakes for everything else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataViewModelRestoreTest : RoomDatabaseTestBase() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val stagingDir get() = RestoreMarker.stagingDirIn(context.filesDir)

    private lateinit var settings: FakeSettingsRepository
    private lateinit var seedManager: SeedManager
    private lateinit var lock: RestoreLock
    private lateinit var widget: FakeWidgetRefresher

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settings = FakeSettingsRepository()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(context.filesDir, "vm_${System.nanoTime()}.preferences_pb")
        }
        seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
        lock = RestoreLock()
        widget = FakeWidgetRefresher()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stagingDir.deleteRecursively()
        RestoreMarker(context.filesDir).clear()
    }

    private fun viewModel(): DataViewModel {
        val workoutRepository = WorkoutRepositoryImpl(database.workoutDao(), database.analyticsDao())
        val health = FakeHealthMetricsSource()
        val restorer = BackupRestorer(
            backupDao = database.backupDao(),
            reader = BackupReader(),
            transactionRunner = RoomTransactionRunner(database),
            personalRecordsUpdater = PersonalRecordsUpdater(
                workoutRepository,
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
        return DataViewModel(
            context = context,
            csvExporter = CsvExporter(database.workoutDao(), database.measurementDao()),
            backupWriter = BackupWriter(database.backupDao(), settings, seedManager, FakeClock()),
            backupReader = BackupReader(),
            backupRestorer = restorer,
            workoutRepository = workoutRepository,
            healthMetricsSource = health,
            healthConnectDisconnector = HealthConnectDisconnector(
                health, FakeWellnessRepository(), FakeWorkoutHeartRateSampleRepository(), widget, AppLogger.NoOp,
            ),
            localDataEraser = LocalDataEraser(
                context, database.backupDao(), RoomTransactionRunner(database), settings, seedManager, widget,
                FakeRegionDefaults(), FakeFirstRunStore(), AppLogger.NoOp,
            ),
            restoreLock = lock,
            logger = AppLogger.NoOp,
        )
    }

    private fun workout(id: String, status: WorkoutStatus) = WorkoutEntity(
        id, null, "Session $id", null, status, 100, if (status == WorkoutStatus.COMPLETED) 200 else null,
        if (status == WorkoutStatus.COMPLETED) 3600 else 0, 100, 200, WorkoutStructure.REGULAR, WorkoutKind.STRENGTH,
    )

    /** A completed workout w1 and, when asked, an unfinished w2 with one set. */
    private suspend fun seed(withUnfinished: Boolean) {
        val dao = database.backupDao()
        dao.insertExercises(
            listOf(
                ExerciseEntity(
                    id = "e1", name = "Bench Press", exerciseType = ExerciseType.WEIGHT_REPS,
                    primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
                    equipment = Equipment.BARBELL, instructions = "", mediaPath = null, isCustom = false,
                    isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 1, updatedAt = 1,
                    muscleHeads = emptyList(), deprecatedPrimaryMuscleHead = null,
                ),
            ),
        )
        dao.insertWorkoutsBulk(listOfNotNull(workout("w1", WorkoutStatus.COMPLETED), workout("w2", WorkoutStatus.IN_PROGRESS).takeIf { withUnfinished }))
        dao.insertWorkoutExercisesBulk(listOf(WorkoutExerciseEntity("we1", "w1", "e1", 0, null, null, null)))
        dao.insertWorkoutSetsBulk(listOf(WorkoutSetEntity("ws1", "we1", 0, SetType.NORMAL, 100.0, 5, null, null, null, null, true, 150)))
        if (withUnfinished) {
            dao.insertWorkoutExercisesBulk(listOf(WorkoutExerciseEntity("we2", "w2", "e1", 0, null, null, null)))
            dao.insertWorkoutSetsBulk(listOf(WorkoutSetEntity("ws2", "we2", 0, SetType.NORMAL, 60.0, 8, null, null, null, null, true, 350)))
        }
    }

    /** Writes a backup of the database as it is now to a file, and returns its Uri. */
    private suspend fun backupFile(): Uri {
        val bytes = ByteArrayOutputStream()
            .also { BackupWriter(database.backupDao(), settings, seedManager, FakeClock()).write(it, context.filesDir, "0.1.0", 1) }
            .toByteArray()
        val file = File(context.cacheDir, "backup_${System.nanoTime()}.zip").apply { writeBytes(bytes) }
        return Uri.fromFile(file)
    }

    /** A ViewModel owned by [store], so a test can clear it as its activity finishing would. */
    private fun viewModelIn(store: ViewModelStore): DataViewModel =
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel() as T
            },
        )[DataViewModel::class.java]

    private suspend fun awaitLockFree() = withTimeout(30_000) { lock.held.first { !it } }

    private suspend fun completedWorkout(id: String) =
        database.backupDao().insertWorkoutsBulk(listOf(workout(id, WorkoutStatus.COMPLETED)))

    private suspend fun DataViewModel.awaitJob(predicate: (DataJob) -> Boolean): DataJob =
        withTimeout(30_000) { uiState.first { predicate(it.job) }.job }

    private suspend fun DataViewModel.awaitSettled(): DataJob =
        awaitJob { it is DataJob.Done || it is DataJob.Failed || it is DataJob.ConfirmRestore }

    @Test
    fun `a full backup is refused before the picker while a workout is in progress`() = runBlocking {
        database.backupDao().insertWorkoutsBulk(listOf(workout("w9", WorkoutStatus.IN_PROGRESS)))
        val vm = viewModel()

        assertFalse(vm.backupAllowed())
        assertEquals(DataJob.Failed(R.string.data_backup_blocked_in_progress), vm.uiState.value.job)
    }

    @Test
    fun `a full backup may open the picker when no workout is in progress`() = runBlocking {
        seed(withUnfinished = false)
        val vm = viewModel()

        assertTrue(vm.backupAllowed())
        assertEquals(DataJob.Idle, vm.uiState.value.job)
    }

    @Test
    fun `a workout started while the backup picker was open still refuses the backup, and nothing is written`() = runBlocking {
        seed(withUnfinished = false)
        val vm = viewModel()
        assertTrue(vm.backupAllowed())
        val chosen = File(context.cacheDir, "chosen_${System.nanoTime()}.zip").apply { writeBytes(ByteArray(0)) }

        database.backupDao().insertWorkoutsBulk(listOf(workout("w9", WorkoutStatus.IN_PROGRESS)))
        vm.export(ExportKind.BACKUP_ZIP, Uri.fromFile(chosen))

        assertEquals(DataJob.Failed(R.string.data_backup_blocked_in_progress), vm.awaitJob { it is DataJob.Done || it is DataJob.Failed })
        assertEquals(0L, chosen.length())
    }

    @Test
    fun `a full backup with no workout in progress is written`() = runBlocking {
        seed(withUnfinished = false)
        val vm = viewModel()
        val chosen = File(context.cacheDir, "chosen_${System.nanoTime()}.zip").apply { writeBytes(ByteArray(0)) }

        vm.export(ExportKind.BACKUP_ZIP, Uri.fromFile(chosen))

        assertEquals(DataJob.Done(R.string.data_export_done), vm.awaitJob { it is DataJob.Done || it is DataJob.Failed })
        assertTrue(chosen.length() > 0L)
    }

    @Test
    fun `a backup with an unfinished workout names it in the confirm and reports it left out`() = runBlocking {
        seed(withUnfinished = true)
        val uri = backupFile()
        // Restore is refused during a session, so the device's own unfinished workout goes first.
        database.workoutDao().deleteWorkoutById("w2")
        val vm = viewModel()

        vm.prepareRestore(uri)
        val confirm = vm.awaitSettled() as DataJob.ConfirmRestore
        assertEquals(1, confirm.staged.unfinishedWorkoutCount)
        assertEquals(1, confirm.staged.completedWorkoutCount)
        assertTrue(lock.held.value)

        vm.confirmRestore()
        val done = vm.awaitJob { it is DataJob.Done || it is DataJob.Failed }

        assertEquals(
            DataJob.Done(
                R.plurals.data_restore_done_left_out,
                count = 1,
                restore = RestoreOutcome(unfinishedWorkoutsLeftOut = 1, backupHadSettings = true),
            ),
            done,
        )
        assertEquals(null, database.workoutDao().getById("w2"))
        assertFalse(lock.held.value)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `a restore with nothing left out says Restored`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()

        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore
        vm.confirmRestore()

        assertEquals(
            DataJob.Done(R.string.data_restore_done, restore = RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = true)),
            vm.awaitJob { it is DataJob.Done || it is DataJob.Failed },
        )
    }

    @Test
    fun `a restore that fails after the commit says it did not finish, not that nothing changed`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore

        settings.replaceAllError = IllegalStateException("preferences store unavailable")
        vm.confirmRestore()

        assertEquals(
            DataJob.Failed(R.string.data_restore_incomplete, afterConfirm = true, backupHadSettings = true),
            vm.awaitJob { it is DataJob.Done || it is DataJob.Failed },
        )
        assertFalse(lock.held.value)
    }

    @Test
    fun `a restore is refused while another screen holds the lock, and its staging is left alone`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val otherScreen = Any()
        lock.tryAcquire(otherScreen)
        val theirs = File(stagingDir, "manifest.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        val vm = viewModel()

        vm.prepareRestore(uri)

        assertEquals(DataJob.Failed(R.string.data_restore_busy), vm.awaitSettled())
        assertTrue(theirs.isFile)
        assertTrue(lock.isHeldBy(otherScreen))
    }

    @Test
    fun `a backup whose settings this build can't read is refused as too new, and the lock is released`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        // Hand-edit the archive's settings to name a unit this build lacks.
        val original = File(uri.path!!)
        val edited = File(context.cacheDir, "edited_${System.nanoTime()}.zip")
        java.util.zip.ZipInputStream(original.inputStream()).use { input ->
            java.util.zip.ZipOutputStream(edited.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    var bytes = input.readBytes()
                    if (entry.name == "settings.json") {
                        bytes = bytes.decodeToString().replace("\"weight_unit\":\"KG\"", "\"weight_unit\":\"STONE\"").toByteArray()
                    }
                    output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        val vm = viewModel()

        vm.prepareRestore(Uri.fromFile(edited))

        assertEquals(DataJob.Failed(R.string.data_restore_too_new), vm.awaitSettled())
        assertFalse(lock.held.value)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `cancelling the confirm deletes the staged copy and releases the lock`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore
        assertTrue(stagingDir.isDirectory)

        vm.cancelRestore()
        // The dialog goes at once; the staged copy is deleted off the main thread after it.
        assertEquals(DataJob.Idle, vm.uiState.value.job)
        awaitLockFree()

        assertFalse(stagingDir.exists())
        assertFalse(lock.held.value)
    }

    @Test
    fun `a workout started while the confirm is open blocks the restore and frees the lock`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore

        database.backupDao().insertWorkoutsBulk(listOf(workout("w9", WorkoutStatus.IN_PROGRESS)))
        vm.confirmRestore()

        assertEquals(
            DataJob.Failed(R.string.data_restore_blocked_in_progress, afterConfirm = true, backupHadSettings = true),
            vm.awaitJob { it is DataJob.Failed || it is DataJob.Done },
        )
        assertEquals("Session w9", database.workoutDao().getById("w9")?.title)
        assertFalse(lock.held.value)
        assertFalse(stagingDir.exists())
    }

    /** A copy of the backup at [uri] with each entry passed through [edit]; null drops the entry. */
    private fun editedBackup(uri: Uri, edit: (name: String, bytes: ByteArray) -> ByteArray?): Uri {
        val edited = File(context.cacheDir, "edited_${System.nanoTime()}.zip")
        java.util.zip.ZipInputStream(File(uri.path!!).inputStream()).use { input ->
            java.util.zip.ZipOutputStream(edited.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = edit(entry.name, input.readBytes()) ?: continue
                    output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        return Uri.fromFile(edited)
    }

    @Test
    fun `a restored backup without settings says so, for first-run setup to write its own choices`() = runBlocking {
        seed(withUnfinished = false)
        val uri = editedBackup(backupFile()) { name, bytes -> bytes.takeIf { name != "settings.json" } }
        val vm = viewModel()

        vm.prepareRestore(uri)
        val confirm = vm.awaitSettled() as DataJob.ConfirmRestore
        assertFalse(confirm.staged.hasSettings)
        vm.confirmRestore()

        assertEquals(
            DataJob.Done(R.string.data_restore_done, restore = RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = false)),
            vm.awaitJob { it is DataJob.Done || it is DataJob.Failed },
        )
    }

    @Test
    fun `a confirmed restore of a backup without settings that fails says the backup had none`() = runBlocking {
        seed(withUnfinished = false)
        val uri = editedBackup(backupFile()) { name, bytes -> bytes.takeIf { name != "settings.json" } }
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore

        database.backupDao().insertWorkoutsBulk(listOf(workout("w9", WorkoutStatus.IN_PROGRESS)))
        vm.confirmRestore()

        assertEquals(
            DataJob.Failed(R.string.data_restore_blocked_in_progress, afterConfirm = true, backupHadSettings = false),
            vm.awaitJob { it is DataJob.Failed || it is DataJob.Done },
        )
    }

    @Test
    fun `choosing a file shows Reading the backup at once, before any check suspends`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()

        vm.prepareRestore(uri)

        // First-run setup reads this to turn Continue and Restore off; Idle here left them on
        // while the checks and a cancelled confirm's cleanup ran.
        assertEquals(DataJob.Working(R.string.data_restore_reading), vm.uiState.value.job)
        assertTrue(vm.awaitSettled() is DataJob.ConfirmRestore)
    }

    @Test
    fun `a staging failure is not marked as after the confirm`() = runBlocking {
        val notABackup = File(context.cacheDir, "notes_${System.nanoTime()}.txt").apply { writeText("shopping list") }
        val vm = viewModel()

        vm.prepareRestore(Uri.fromFile(notABackup))

        assertEquals(DataJob.Failed(R.string.data_restore_unreadable, afterConfirm = false), vm.awaitSettled())
    }

    @Test
    fun `restoreFailureHandled keeps the message but clears the after-confirm mark, and leaves other jobs alone`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore
        settings.replaceAllError = IllegalStateException("preferences store unavailable")
        vm.confirmRestore()
        vm.awaitJob { it is DataJob.Done || it is DataJob.Failed }

        vm.restoreFailureHandled()
        assertEquals(
            DataJob.Failed(R.string.data_restore_incomplete, afterConfirm = false, backupHadSettings = true),
            vm.uiState.value.job,
        )

        vm.dismissJob()
        vm.restoreFailureHandled()
        assertEquals(DataJob.Idle, vm.uiState.value.job)
    }

    @Test
    fun `staging failures name the real cause`() {
        assertEquals(R.string.data_restore_too_large, stagingFailureMessage(ZipEntryNames.ArchiveTooLargeException("over 2 GB")))
        assertEquals(R.string.data_restore_no_space, stagingFailureMessage(IOException("write failed: ENOSPC (No space left on device)")))
        assertEquals(R.string.data_restore_unreadable, stagingFailureMessage(BackupReader.NotABackupException("not a zip")))
    }

    @Test
    fun `restore failures say unchanged only when nothing was committed`() {
        assertEquals(R.string.data_restore_incomplete, restoreFailureMessage(PostCommitRestoreException(IOException("ENOSPC"))))
        assertEquals(R.string.data_restore_too_new, restoreFailureMessage(UnknownSettingValueException("weight_unit", "STONE")))
        assertEquals(R.string.data_restore_no_space, restoreFailureMessage(IOException("No space left on device")))
        assertEquals(R.string.data_restore_failed, restoreFailureMessage(IllegalStateException("constraint failed")))
    }

    @Test
    fun `cancel then Replace everything straight after runs no restore`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        completedWorkout("w5")
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore

        // The dialog's two buttons, tapped back to back while the staged copy is being deleted.
        vm.cancelRestore()
        assertEquals(DataJob.Idle, vm.uiState.value.job)
        vm.confirmRestore()
        awaitLockFree()

        assertEquals(DataJob.Idle, vm.uiState.value.job)
        assertEquals("Session w5", database.workoutDao().getById("w5")?.title)
        assertEquals(0, widget.refreshCount)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `a double tap on Replace everything runs one restore`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore

        vm.confirmRestore()
        // The dialog is gone before the restore's first suspension, so the second tap has no target.
        assertEquals(DataJob.Working(R.string.data_restore_restoring), vm.uiState.value.job)
        vm.confirmRestore()

        assertEquals(
            DataJob.Done(R.string.data_restore_done, restore = RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = true)),
            vm.awaitJob { it is DataJob.Done || it is DataJob.Failed },
        )
        awaitLockFree()
        assertEquals(1, widget.refreshCount)
    }

    @Test
    fun `a new staging waits for the cancelled one's cleanup instead of reporting a restore running`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore

        vm.cancelRestore()
        vm.prepareRestore(uri)

        assertTrue(vm.awaitSettled() is DataJob.ConfirmRestore)
        assertTrue(stagingDir.isDirectory)
        assertTrue(lock.isHeldBy(vm))
    }

    @Test
    fun `leaving the screen while a backup is being read still frees the lock once reading stops`() = runBlocking {
        val reading = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val slow = object : InputStream() {
            override fun read(): Int {
                reading.countDown()
                unblock.await(30, TimeUnit.SECONDS)
                throw IOException("the provider went away")
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int = read()
        }
        val uri = Uri.parse("content://com.enil.logez.test/slow.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, slow)
        val store = ViewModelStore()
        val vm = viewModelIn(store)

        vm.prepareRestore(uri)
        assertTrue(reading.await(30, TimeUnit.SECONDS))
        store.clear()
        // Still reading into the staging folder, so the lock must not be free yet.
        assertTrue(lock.held.value)

        unblock.countDown()
        awaitLockFree()
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `leaving the screen with the confirm open frees the lock`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val store = ViewModelStore()
        val vm = viewModelIn(store)
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore
        assertTrue(lock.held.value)

        store.clear()

        assertFalse(lock.held.value)
    }

    @Test
    fun `after a restore that did not finish, another restore is refused and the pending one is kept`() = runBlocking {
        seed(withUnfinished = false)
        val uri = backupFile()
        val vm = viewModel()
        vm.prepareRestore(uri)
        vm.awaitSettled() as DataJob.ConfirmRestore
        settings.replaceAllError = IllegalStateException("preferences store unavailable")
        vm.confirmRestore()
        assertEquals(
            DataJob.Failed(R.string.data_restore_incomplete, afterConfirm = true, backupHadSettings = true),
            vm.awaitJob { it is DataJob.Done || it is DataJob.Failed },
        )
        vm.dismissJob()

        vm.prepareRestore(backupFile())

        assertEquals(DataJob.Failed(R.string.data_restore_incomplete), vm.awaitSettled())
        // What the next launch needs to finish the first restore is still there.
        assertEquals(RestoreMarker.Phase.MEDIA, RestoreMarker(context.filesDir).read())
        assertTrue(File(stagingDir, "settings.json").isFile)
        assertFalse(lock.held.value)
    }

    @Test
    fun `Delete all data is refused while another screen's restore holds the lock, and nothing is deleted`() = runBlocking {
        seed(withUnfinished = false)
        val otherScreen = Any()
        assertTrue(lock.tryAcquire(otherScreen))
        val vm = viewModel()

        vm.deleteAllData()

        assertEquals(DataJob.Failed(R.string.data_restore_busy), vm.awaitSettled())
        assertEquals("Session w1", database.workoutDao().getById("w1")?.title)
        assertTrue(lock.isHeldBy(otherScreen))
    }

    @Test
    fun `export failures name the real cause`() {
        assertEquals(R.string.data_backup_too_large, exportFailureMessage(BackupWriter.BackupTooLargeException("over 2 GB")))
        assertEquals(R.string.data_backup_blocked_in_progress, exportFailureMessage(DataViewModel.BackupBlockedInProgress()))
        assertEquals(R.string.data_export_failed, exportFailureMessage(IOException("EIO")))
    }
}
