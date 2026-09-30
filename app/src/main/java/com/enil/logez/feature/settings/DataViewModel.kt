package com.enil.logez.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.BuildConfig
import com.enil.logez.R
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.backup.BackupManifest
import com.enil.logez.core.data.backup.BackupReader
import com.enil.logez.core.data.backup.BackupRestorer
import com.enil.logez.core.data.backup.BackupWriter
import com.enil.logez.core.data.backup.LocalDataEraser
import com.enil.logez.core.data.backup.PostCommitRestoreException
import com.enil.logez.core.data.backup.RestoreLock
import com.enil.logez.core.data.backup.RestoreMarker
import com.enil.logez.core.data.backup.StagedBackup
import com.enil.logez.core.data.backup.UnknownSettingValueException
import com.enil.logez.core.data.backup.ZipEntryNames
import com.enil.logez.core.data.backup.isOutOfSpace
import com.enil.logez.core.data.export.CsvExporter
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthConnectDisconnector
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class ExportKind { WORKOUTS_CSV, MEASUREMENTS_CSV, BACKUP_ZIP }

sealed interface DataJob {
    data object Idle : DataJob
    data class Working(val labelRes: Int) : DataJob

    /** Staged and verified; nothing has been replaced yet. The user decides from here. */
    data class ConfirmRestore(val staged: StagedBackup) : DataJob

    /** @param count when set, [messageRes] is a plurals resource shown for this count. */
    data class Done(val messageRes: Int, val count: Int? = null) : DataJob
    data class Failed(val messageRes: Int) : DataJob
}

data class DataUiState(
    val workoutSetCount: Int? = null,
    val measurementCount: Int? = null,
    /** Gates the "Manage Health Connect access" row; the disconnect-and-delete row always shows. */
    val healthConnectAvailable: Boolean = false,
    /**
     * What LogEZ may read right now, shown under the manage row (2026-09-26). Nothing in the app
     * used to show whether heart rate itself was allowed: Profile shows steps and calories, so a
     * grant without heart rate looked complete. Null until first loaded.
     */
    val grantedHealthTypes: Set<HealthDataType>? = null,
    /** Disconnected this session: access ends when LogEZ restarts, reconnect from Profile. */
    val healthDisconnectedThisSession: Boolean = false,
    val job: DataJob = DataJob.Idle,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val csvExporter: CsvExporter,
    private val backupWriter: BackupWriter,
    private val backupReader: BackupReader,
    private val backupRestorer: BackupRestorer,
    private val workoutRepository: WorkoutRepository,
    private val healthMetricsSource: HealthMetricsSource,
    private val healthConnectDisconnector: HealthConnectDisconnector,
    private val localDataEraser: LocalDataEraser,
    private val restoreLock: RestoreLock,
    private val logger: AppLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private val stagingDir get() = RestoreMarker.stagingDirIn(context.filesDir)

    /**
     * One data job at a time (2026-09-25 review): a backup racing a delete-all exported a zip whose
     * row counts no longer matched its contents, and a delete-all racing a restore let restored
     * photos survive the delete. The screen also disables its rows and back arrow while busy.
     */
    private val busy: Boolean get() = _uiState.value.job is DataJob.Working

    /**
     * True while one of this ViewModel's coroutines holds [RestoreLock] and will release it itself.
     * [onCleared] releases the lock only when this is false. Read and written on the main thread.
     */
    private var lockWorkInFlight = false

    /** The staged copy a cancelled confirm is still deleting; the next staging waits for it. */
    private var stagingCleanup: Job? = null

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    workoutSetCount = csvExporter.workoutSetCount(),
                    measurementCount = csvExporter.measurementCount(),
                    healthConnectAvailable = healthMetricsSource.availability() == HealthConnectAvailability.Available,
                )
            }
        }
    }

    /** Re-reads the grants; the screen calls this on resume, e.g. back from Health Connect's settings. */
    fun refreshHealthAccess() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    grantedHealthTypes = healthMetricsSource.grantedTypes(),
                    healthDisconnectedThisSession = healthMetricsSource.accessEndsOnRestart,
                )
            }
        }
    }

    /**
     * Asks before the full-backup picker opens (first-run plan, F5). The picker creates the file the
     * moment it returns, so refusing afterwards would leave an empty file behind. A backup made
     * mid-workout would carry the unfinished session, which can't be restored properly.
     *
     * @return true when the screen may open the picker; otherwise the refusal is shown.
     */
    suspend fun backupAllowed(): Boolean {
        if (busy) return false
        if (workoutRepository.getInProgress() != null) {
            _uiState.update { it.copy(job = DataJob.Failed(R.string.data_backup_blocked_in_progress)) }
            return false
        }
        return true
    }

    fun export(kind: ExportKind, uri: Uri) {
        if (busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(job = DataJob.Working(labelRes(kind))) }
            try {
                // Checked again with the file in hand, in case a workout started while the picker
                // was open; the catch below deletes the file.
                if (kind == ExportKind.BACKUP_ZIP && workoutRepository.getInProgress() != null) {
                    throw BackupBlockedInProgress()
                }
                // Off the main thread (F3): a full backup copies every photo.
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("the chosen location is not writable")
                    stream.use { out ->
                        when (kind) {
                            ExportKind.WORKOUTS_CSV -> csvExporter.exportWorkouts(out)
                            ExportKind.MEASUREMENTS_CSV -> csvExporter.exportMeasurements(out)
                            ExportKind.BACKUP_ZIP -> backupWriter.write(
                                out = out,
                                mediaRoot = context.filesDir,
                                appVersionName = BuildConfig.VERSION_NAME,
                                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                            )
                        }
                    }
                }
                _uiState.update { it.copy(job = DataJob.Done(R.string.data_export_done)) }
            } catch (t: Throwable) {
                // The picker created the document the moment it returned, so without this a failed
                // export leaves a broken empty file wherever the user chose to put it. A cancelled
                // one (the screen's ViewModel went mid-write) leaves a truncated file, so it goes too.
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                if (t is CancellationException) throw t
                logger.e(TAG, "Export failed for $kind", t)
                _uiState.update { it.copy(job = DataJob.Failed(exportFailureMessage(t))) }
            }
        }
    }

    /** A workout started while the full-backup picker was open. */
    internal class BackupBlockedInProgress : IllegalStateException("a workout is in progress")

    /**
     * Unpacks and checks the archive, then stops and asks. Nothing the user has is touched until
     * they confirm — staging is deliberately the whole first half of a restore.
     *
     * Holds [RestoreLock] from here until the restore or the cancel (F14): another screen staging
     * meanwhile would empty the shared staging folder under this one.
     */
    fun prepareRestore(uri: Uri) {
        if (busy || _uiState.value.job is DataJob.ConfirmRestore) return
        viewModelScope.launch {
            // A cancel's cleanup may still be deleting the last staged copy.
            stagingCleanup?.join()
            if (workoutRepository.getInProgress() != null) {
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_blocked_in_progress)) }
                return@launch
            }
            if (!restoreLock.tryAcquire(this@DataViewModel)) {
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_busy)) }
                return@launch
            }
            lockWorkInFlight = true
            // A restore that failed after its commit (F13) still needs the staged copy the marker
            // points at; the next launch finishes it from there. Staging now would empty that folder,
            // and its photos and settings would never be applied.
            val restorePending = try {
                withContext(Dispatchers.IO) { RestoreMarker(context.filesDir).read() != null }
            } catch (t: Throwable) {
                restoreLock.release(this@DataViewModel)
                lockWorkInFlight = false
                throw t
            }
            if (restorePending) {
                restoreLock.release(this@DataViewModel)
                lockWorkInFlight = false
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_incomplete)) }
                return@launch
            }
            _uiState.update { it.copy(job = DataJob.Working(R.string.data_restore_reading)) }
            var confirming = false
            // Cleaned up before the result shows, so a retry straight after it finds the lock free.
            val job: DataJob = try {
                // Off the main thread (F3): this unpacks up to 2 GB.
                val staged = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("the chosen file could not be opened")
                    input.use { backupReader.stage(it, stagingDir) }
                }
                when (staged.compatibility(BackupWriter.ROOM_SCHEMA_VERSION)) {
                    BackupManifest.Compatibility.TooNew -> DataJob.Failed(R.string.data_restore_too_new)
                    BackupManifest.Compatibility.Ok -> {
                        confirming = true
                        DataJob.ConfirmRestore(staged)
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                logger.e(TAG, "Reading the backup failed", t)
                DataJob.Failed(stagingFailureMessage(t))
            } finally {
                if (!confirming) discardStagingAndRelease()
                lockWorkInFlight = false
            }
            _uiState.update { it.copy(job = job) }
        }
    }

    /** The destructive half. Only reachable from [DataJob.ConfirmRestore]. */
    fun confirmRestore() {
        if (busy) return
        if (_uiState.value.job !is DataJob.ConfirmRestore) return
        // Out of ConfirmRestore before anything suspends, so the dialog goes at once: a second tap,
        // or a cancel deleting the staged copy, can no longer run alongside this restore.
        _uiState.update { it.copy(job = DataJob.Working(R.string.data_restore_restoring)) }
        // Held since staging. Only onCleared could have freed it, and then the staged copy is no
        // longer this screen's to restore.
        if (!restoreLock.isHeldBy(this)) {
            _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_busy)) }
            return
        }
        lockWorkInFlight = true
        viewModelScope.launch {
            // NonCancellable: leaving the screen mid-restore used to cancel it between the database
            // commit and the photo swap. Off the main thread (F3): it copies every photo. The lock
            // is released in here, so it is freed even when this screen goes first.
            val job = withContext(NonCancellable + Dispatchers.IO) {
                try {
                    // A workout started meanwhile, e.g. from a second window, would have its rows wiped.
                    if (workoutRepository.getInProgress() != null) {
                        stagingDir.deleteRecursively()
                        DataJob.Failed(R.string.data_restore_blocked_in_progress)
                    } else {
                        runCatching { backupRestorer.restore(stagingDir, context.filesDir) }.fold(
                            onSuccess = { result ->
                                val leftOut = result.unfinishedWorkoutsLeftOut
                                if (leftOut > 0) {
                                    DataJob.Done(R.plurals.data_restore_done_left_out, count = leftOut)
                                } else {
                                    DataJob.Done(R.string.data_restore_done)
                                }
                            },
                            onFailure = { t ->
                                logger.e(TAG, "Restore failed", t)
                                DataJob.Failed(restoreFailureMessage(t))
                            },
                        )
                    }
                } finally {
                    restoreLock.release(this@DataViewModel)
                }
            }
            lockWorkInFlight = false
            _uiState.update {
                it.copy(
                    job = job,
                    workoutSetCount = csvExporter.workoutSetCount(),
                    measurementCount = csvExporter.measurementCount(),
                )
            }
        }
    }

    fun cancelRestore() {
        if (_uiState.value.job !is DataJob.ConfirmRestore) return
        // Idle at once, before the delete below, so the dialog's "Replace everything" can't be
        // tapped while the staged copy is half deleted.
        _uiState.update { it.copy(job = DataJob.Idle) }
        // Only the holder may clear the staging folder; anyone else's is not ours to delete.
        if (!restoreLock.isHeldBy(this)) return
        lockWorkInFlight = true
        stagingCleanup = viewModelScope.launch {
            discardStagingAndRelease()
            lockWorkInFlight = false
        }
    }

    /**
     * Deletes the staged copy off the main thread, then frees the lock. Both happen even when this
     * ViewModel is cleared meanwhile: a release placed after a NonCancellable block does not run
     * once the coroutine is cancelled, and the lock would stay held for the life of the process.
     */
    private suspend fun discardStagingAndRelease() = withContext(NonCancellable + Dispatchers.IO) {
        try {
            stagingDir.deleteRecursively()
        } finally {
            restoreLock.release(this@DataViewModel)
        }
    }

    /**
     * A confirm dialog left open when this screen's ViewModel goes (its activity finished) must
     * not keep every other restore out. The staged copy is removed by the next staging or launch.
     * While staging, a restore or a cleanup is running, that coroutine releases the lock itself
     * when it ends: releasing here would let another restore use the staging folder under it.
     */
    override fun onCleared() {
        if (!lockWorkInFlight) restoreLock.release(this)
        super.onCleared()
    }

    /**
     * Revokes Health Connect access and deletes every step, calorie and heart-rate reading LogEZ
     * saved from it. Workouts, routes and measurements are untouched. Confirmed by the screen.
     */
    fun disconnectHealthConnect() {
        if (busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(job = DataJob.Working(R.string.data_health_disconnecting)) }
            try {
                withContext(NonCancellable) { healthConnectDisconnector.disconnectAndDelete() }
                _uiState.update { it.copy(job = DataJob.Done(R.string.data_health_disconnected)) }
                refreshHealthAccess()
            } catch (t: Throwable) {
                logger.e(TAG, "Deleting Health Connect data failed", t)
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_health_disconnect_failed)) }
            }
        }
    }

    /**
     * Erases everything LogEZ holds and restores default settings. Refused while a workout is in
     * progress, for the same reason restore is: the live session points at rows this deletes.
     */
    fun deleteAllData() {
        if (busy) return
        viewModelScope.launch {
            if (workoutRepository.getInProgress() != null) {
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_delete_all_blocked_in_progress)) }
                return@launch
            }
            // It deletes the staging folder too, so it must not run inside another screen's restore.
            stagingCleanup?.join()
            if (!restoreLock.tryAcquire(this@DataViewModel)) {
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_busy)) }
                return@launch
            }
            _uiState.update { it.copy(job = DataJob.Working(R.string.data_delete_all_working)) }
            try {
                try {
                    localDataEraser.eraseEverything()
                } finally {
                    // Not suspending, so it runs even when the screen's scope is cancelled.
                    restoreLock.release(this@DataViewModel)
                }
                _uiState.update {
                    it.copy(
                        job = DataJob.Done(R.string.data_delete_all_done),
                        workoutSetCount = csvExporter.workoutSetCount(),
                        measurementCount = csvExporter.measurementCount(),
                    )
                }
            } catch (t: Throwable) {
                logger.e(TAG, "Deleting all data failed", t)
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_delete_all_failed)) }
            }
        }
    }

    /** The picker was dismissed — nothing was created, nothing to clean up. */
    fun pickerCancelled() = _uiState.update { it.copy(job = DataJob.Idle) }

    fun dismissJob() = _uiState.update { it.copy(job = DataJob.Idle) }

    private fun labelRes(kind: ExportKind) = when (kind) {
        ExportKind.WORKOUTS_CSV, ExportKind.MEASUREMENTS_CSV -> R.string.data_exporting
        ExportKind.BACKUP_ZIP -> R.string.data_backing_up
    }

    private companion object {
        const val TAG = "DataViewModel"
    }
}

/** The message for an export that failed. Its file has already been deleted. */
internal fun exportFailureMessage(t: Throwable): Int = when (t) {
    is DataViewModel.BackupBlockedInProgress -> R.string.data_backup_blocked_in_progress
    is BackupWriter.BackupTooLargeException -> R.string.data_backup_too_large
    else -> R.string.data_export_failed
}

/**
 * The message for a backup that could not be staged (F13). "Not a backup" used to cover a
 * too-large archive and a full disk as well, which sent the user looking for another file.
 */
internal fun stagingFailureMessage(t: Throwable): Int = when {
    t is ZipEntryNames.ArchiveTooLargeException -> R.string.data_restore_too_large
    isOutOfSpace(t) -> R.string.data_restore_no_space
    else -> R.string.data_restore_unreadable
}

/**
 * The message for a confirmed restore that failed (F13). Only a failure before the database commit
 * leaves the data unchanged; after it the restore is finished at the next launch.
 */
internal fun restoreFailureMessage(t: Throwable): Int = when {
    t is PostCommitRestoreException -> R.string.data_restore_incomplete
    t is UnknownSettingValueException -> R.string.data_restore_too_new
    isOutOfSpace(t) -> R.string.data_restore_no_space
    else -> R.string.data_restore_failed
}
