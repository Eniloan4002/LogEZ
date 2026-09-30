package com.enil.logez.feature.onboarding

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.data.backup.BackupManifest
import com.enil.logez.core.data.backup.StagedBackup
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.feature.settings.DataJob
import com.enil.logez.feature.settings.DataViewModel
import com.enil.logez.feature.settings.RESTORE_MIME_TYPES
import com.enil.logez.feature.settings.RestoreOutcome
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The choices on the setup screen. Held above the screen so a finished restore can read them: a
 * backup without settings keeps the choices shown (first-run plan, O1e). Saved as enum names, so
 * they survive rotation and process death as the screen's own `rememberSaveable` values did.
 */
@Stable
class SetupChoicesState(initial: SetupChoices) {
    var choices: SetupChoices by mutableStateOf(initial)

    companion object {
        val Saver = listSaver<SetupChoicesState, String>(
            save = { listOf(it.choices.weightUnit.name, it.choices.distanceUnit.name, it.choices.firstDayOfWeek.name) },
            restore = { saved ->
                SetupChoicesState(
                    SetupChoices(
                        weightUnit = WeightUnit.valueOf(saved[0]),
                        distanceUnit = DistanceUnit.valueOf(saved[1]),
                        firstDayOfWeek = DayOfWeek.valueOf(saved[2]),
                    ),
                )
            },
        )
    }
}

/** Setup's choices, starting at [preselected]; the preselection is only the starting point. */
@Composable
fun rememberSetupChoicesState(preselected: SetupChoices): SetupChoicesState =
    rememberSaveable(saver = SetupChoicesState.Saver) { SetupChoicesState(preselected) }

/** The line at the top of setup's column while a restore reads, restores or has failed. */
sealed interface SetupRestoreStatus {
    /** "Reading the backup…" or "Restoring…", with the progress bar. */
    data class Working(val labelRes: Int) : SetupRestoreStatus

    /** A restore that did not happen, in the existing wording. Continue starts fresh from here. */
    data class Error(val messageRes: Int) : SetupRestoreStatus
}

/**
 * What setup shows of a restore.
 *
 * @param confirm the backup read and checked, waiting for "Restore this backup?".
 * @param busy Continue and Restore are disabled: a restore is reading, restoring, or handing its
 *   result to the gate. Not while the confirm is open: the dialog is modal, and the approved mockup
 *   (restore-confirm.png) draws the screen under its scrim unchanged, Continue still lime.
 */
data class SetupRestoreUi(
    val status: SetupRestoreStatus? = null,
    val confirm: StagedBackup? = null,
    val busy: Boolean = false,
)

/** Setup's restore: what it shows, and what its buttons do. */
class SetupRestoreBinding(
    val ui: SetupRestoreUi,
    val onRestore: () -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
) {
    companion object {
        /** No restore behind the buttons: previews and tests that are not about restore. */
        val Inert = SetupRestoreBinding(SetupRestoreUi(), onRestore = {}, onConfirm = {}, onCancel = {})
    }
}

/** What setup shows for each state of its [DataViewModel]'s job. */
internal fun setupRestoreUi(job: DataJob): SetupRestoreUi = when (job) {
    DataJob.Idle -> SetupRestoreUi()
    is DataJob.Working -> SetupRestoreUi(status = SetupRestoreStatus.Working(job.labelRes), busy = true)
    is DataJob.ConfirmRestore -> SetupRestoreUi(confirm = job.staged)
    // A confirmed restore's failure is being checked by the gate; the buttons stay off until it has.
    is DataJob.Failed -> SetupRestoreUi(status = SetupRestoreStatus.Error(job.messageRes), busy = job.afterConfirm)
    // A finished restore is on its way to the gate, which opens the app.
    is DataJob.Done -> SetupRestoreUi(busy = job.restore != null)
}

/**
 * Setup's restore, run by its own Activity-scoped [DataViewModel] (first-run plan, O1e): the same
 * staging, confirm and restore as Settings > Export & backup, so a rotation mid-restore keeps the
 * job. A finished restore goes to [onRestored] with the choices in [choices] at that moment; a
 * confirmed restore that failed goes to [onRestoreFailed], once, while its message stays on screen.
 * A cancelled file picker changes nothing. See [SetupRestoreHandOff] for when the result is handed
 * over.
 */
@Composable
fun rememberSetupRestore(
    choices: SetupChoicesState,
    onRestored: (RestoreOutcome, SetupChoices) -> Unit,
    onRestoreFailed: (messageRes: Int, backupHadSettings: Boolean, SetupChoices) -> Unit,
    viewModel: DataViewModel = hiltViewModel(),
): SetupRestoreBinding {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val job = uiState.job
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareRestore(uri)
    }
    val jobs = remember(viewModel) { viewModel.uiState.map { it.job } }
    val activity = LocalActivity.current

    SetupRestoreHandOff(
        jobs = jobs,
        choices = choices,
        onRestored = onRestored,
        onRestoreFailed = onRestoreFailed,
        onRestoredHandled = viewModel::dismissJob,
        onFailureHandled = viewModel::restoreFailureHandled,
        onConfirmAbandoned = viewModel::cancelRestore,
        changingConfigurations = { activity?.isChangingConfigurations == true },
    )

    return SetupRestoreBinding(
        ui = setupRestoreUi(job),
        onRestore = { openDocument.launch(RESTORE_MIME_TYPES) },
        onConfirm = viewModel::confirmRestore,
        onCancel = viewModel::cancelRestore,
    )
}

/**
 * Hands a finished or failed restore from setup's [DataViewModel] to the gate.
 *
 * - [DataJob.Done] with a restore outcome: [onRestored] with the choices on screen now, then
 *   [onRestoredHandled] (the job goes back to Idle, so a recreated window does not hand it over
 *   again).
 * - [DataJob.Failed] after the confirm: [onRestoreFailed], then [onFailureHandled] (the message
 *   stays, the after-confirm mark goes).
 *
 * [jobs] is collected for as long as setup is composed, not only while the Activity is started:
 * a restore that ends while the app is in the background is handed over at once. Waiting for the
 * next start used to lose it twice over: a process death in between left the flag unwritten, and a
 * second window that wrote the flag meanwhile made this one's resume open the app first, so the
 * outcome, the "Restored" message and a settings-less backup's choices were never written.
 *
 * Setup leaving the screen while "Restore this backup?" is open (a second window finished first run)
 * cancels the confirm through [onConfirmAbandoned], which frees the restore lock and the staged
 * copy; a configuration change, which keeps the dialog, does not.
 */
@Composable
internal fun SetupRestoreHandOff(
    jobs: Flow<DataJob>,
    choices: SetupChoicesState,
    onRestored: (RestoreOutcome, SetupChoices) -> Unit,
    onRestoreFailed: (messageRes: Int, backupHadSettings: Boolean, SetupChoices) -> Unit,
    onRestoredHandled: () -> Unit,
    onFailureHandled: () -> Unit,
    onConfirmAbandoned: () -> Unit,
    changingConfigurations: () -> Boolean,
) {
    val latestOnRestored by rememberUpdatedState(onRestored)
    val latestOnRestoreFailed by rememberUpdatedState(onRestoreFailed)
    val latestOnRestoredHandled by rememberUpdatedState(onRestoredHandled)
    val latestOnFailureHandled by rememberUpdatedState(onFailureHandled)
    val latestOnConfirmAbandoned by rememberUpdatedState(onConfirmAbandoned)
    val latestChangingConfigurations by rememberUpdatedState(changingConfigurations)
    // The last job seen, for the dispose check below. Written only by the collector.
    val lastJob = remember { arrayOf<DataJob>(DataJob.Idle) }

    LaunchedEffect(jobs) {
        jobs.distinctUntilChanged().collect { job ->
            lastJob[0] = job
            when {
                job is DataJob.Done && job.restore != null -> {
                    latestOnRestored(job.restore, choices.choices)
                    latestOnRestoredHandled()
                }
                job is DataJob.Failed && job.afterConfirm -> {
                    latestOnRestoreFailed(job.messageRes, job.backupHadSettings ?: true, choices.choices)
                    latestOnFailureHandled()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (lastJob[0] is DataJob.ConfirmRestore && !latestChangingConfigurations()) latestOnConfirmAbandoned()
        }
    }
}

/**
 * The restore status at the top of setup's column: the label and progress bar while working, or
 * the failure, with the install hint after "too new". The text is a polite live region; the
 * progress bar keeps its own semantics.
 */
@Composable
internal fun SetupRestoreStatusBlock(status: SetupRestoreStatus, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = Spacing.xs)) {
        when (status) {
            is SetupRestoreStatus.Working -> {
                Text(
                    stringResource(status.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(vertical = Spacing.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is SetupRestoreStatus.Error -> Column(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    stringResource(status.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
                if (status.messageRes == R.string.data_restore_too_new) {
                    Text(
                        stringResource(R.string.first_run_restore_too_new_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
            }
        }
    }
}

/**
 * "Restore this backup?", used only by setup. Setup shows only when there is no user content, so
 * nothing of the user's is replaced: unlike the Settings dialog, Restore is in the primary colour,
 * not red, and there is no exercise count, which would include about 400 seeded exercises. The
 * text scrolls, so it doesn't clip at 200% font in landscape.
 */
@Composable
internal fun SetupRestoreConfirmDialog(
    staged: StagedBackup,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val m = staged.manifest
    val locale = currentLocale()
    val madeOn = remember(m.exportedAtEpochMillis, m.exportedAtZoneId, locale) { backupMadeOnDate(m, locale) }
    // Unfinished workouts are left out (F5), so the count is the workouts that come back.
    val workouts = staged.completedWorkoutCount
    val counts = arrayOf(
        pluralStringResource(R.plurals.data_count_workouts, workouts, workouts),
        pluralStringResource(R.plurals.data_count_routines, m.routineCount, m.routineCount),
        pluralStringResource(R.plurals.data_count_measurements, m.measurementCount, m.measurementCount),
        pluralStringResource(R.plurals.data_count_goals, m.goalCount, m.goalCount),
        pluralStringResource(R.plurals.data_count_photos, m.mediaFileCount, m.mediaFileCount),
    )
    val contents = if (madeOn != null) {
        stringResource(R.string.first_run_restore_confirm_contents, madeOn, *counts)
    } else {
        stringResource(R.string.first_run_restore_confirm_contents_undated, *counts)
    }
    val leftOut = staged.unfinishedWorkoutCount
    val leftOutLine = if (leftOut > 0) {
        pluralStringResource(R.plurals.data_restore_confirm_left_out, leftOut, leftOut)
    } else {
        null
    }
    val note = stringResource(
        if (staged.hasSettings) R.string.first_run_restore_confirm_note else R.string.first_run_restore_confirm_note_no_settings,
    )

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.first_run_restore_confirm_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(listOfNotNull(contents, leftOutLine, note).joinToString("\n\n"))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.first_run_restore_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * The day the backup was made, as "24 Sep 2026", in the time zone it was made in (the device's own
 * when the manifest names none, or one this device doesn't know). Null when the manifest has no
 * export time.
 */
internal fun backupMadeOnDate(manifest: BackupManifest, locale: Locale): String? {
    if (manifest.exportedAtEpochMillis <= 0L) return null
    val zone = try {
        ZoneId.of(manifest.exportedAtZoneId)
    } catch (e: DateTimeException) {
        ZoneId.systemDefault()
    }
    return Instant.ofEpochMilli(manifest.exportedAtEpochMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
}
