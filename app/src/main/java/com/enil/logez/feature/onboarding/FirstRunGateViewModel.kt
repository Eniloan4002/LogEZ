package com.enil.logez.feature.onboarding

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.R
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.RegionDefaults
import com.enil.logez.core.common.RegionSuggestion
import com.enil.logez.core.data.backup.RestoreLock
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.StoredSetupValues
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.FirstRunStore
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.UserDataProbe
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.feature.settings.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the first-run gate shows above the app. */
sealed interface FirstRunGateState {
    /**
     * Deciding. An opaque cover over the app, bounded by [FirstRunGateViewModel.RESOLVE_TIMEOUT_MS]
     * even when a Room read is blocked on its thread (see [FirstRunGateViewModel]).
     */
    data object Loading : FirstRunGateState

    /**
     * The setup screen, composed only once its preselected values are known, so it never shows a
     * default and then jumps.
     *
     * @param regionNoteVisible whether "Suggested from your phone's region." is true: every
     *   preselected value matches the region's suggestion, and the region's week start was one the
     *   app offers.
     * @param working Continue, or the end of a restore, is being written, or the database is being
     *   re-checked after a failed restore; Continue and Restore are disabled.
     */
    data class ShowSetup(
        val preselected: SetupChoices,
        val regionNoteVisible: Boolean,
        val working: Boolean = false,
    ) : FirstRunGateState

    /**
     * Continue has been written. The overlay stays up while the app navigates to the Workout tab,
     * so History never flashes; the app then calls [FirstRunGateViewModel.handoffDone].
     *
     * @param setup the setup that was showing, so the hand-off frames draw the same screen (disabled)
     *   even in an Activity recreated mid-hand-off, instead of a blank cover.
     */
    data class HandOff(val setup: ShowSetup) : FirstRunGateState

    /**
     * A restore still held [RestoreLock] when [FirstRunGateViewModel.RESOLVE_TIMEOUT_MS] ran out
     * (P-229, FX1): typically one started from setup in another window, which a widget tap opened
     * this one beside. Setup's "A restore is already running." covers the app instead of an empty,
     * usable one; once the lock is free the gate decides again, from the start.
     */
    data object RestoreBusy : FirstRunGateState

    /** The app as it is today. */
    data object ShowApp : FirstRunGateState
}

/**
 * A one-time message for the app's snackbar once the gate opens the app after a restore from setup.
 *
 * @param count when set, [messageRes] is a plurals resource shown for this count.
 */
data class FirstRunMessage(val messageRes: Int, val count: Int? = null)

/**
 * Decides whether a launch shows first-run setup, and records how first run ended.
 *
 * Setup shows only when the first-run flag is absent and the database holds nothing the user made.
 * If the flag is absent but content exists, the flag is written silently (path `existing`) and the
 * app opens as it does today. Everything fails open: a read that throws or takes longer than
 * [RESOLVE_TIMEOUT_MS] opens the app and leaves the flag unwritten, so setup is offered again at
 * the next launch.
 *
 * The one exception is a restore that still holds [RestoreLock] at the timeout (P-229, FX1): the
 * database is being replaced, so an open app would be empty and usable (a workout started there
 * would even stop the restore). The gate shows [FirstRunGateState.RestoreBusy] instead, waits for
 * the lock with no timeout, since every holder frees it in a `finally` or when its screen goes,
 * and then decides again with a fresh timeout. A flag written meanwhile opens the app at once.
 * That second decision never writes the flag itself: restored rows open the app with it unwritten,
 * and the window that restored writes path `restore` (or the next launch writes `existing`).
 *
 * The state is a plain [MutableStateFlow] set from the constructor, not a `stateIn` over an endless
 * Flow: a launch that has already passed setup starts at [FirstRunGateState.ShowApp] with no
 * loading frame and no Room query.
 *
 * The timeout guards only the wait for the answer, not the reads themselves. Room's suspend DAO
 * calls run a blocking SQLite call inside `withContext`, which cannot return early when cancelled,
 * so a timeout wrapped straight around the probe would last as long as a read stuck behind the
 * seed transaction or a migration. The reads therefore run in their own coroutine, and only the
 * cancellable `await()` is timed.
 *
 * @param sdkInt the device's API level, which decides whether a missing Health Connect can be
 *   installed ([setupHealthFor]); a parameter so tests can run the Android 9-13 path.
 */
@HiltViewModel
class FirstRunGateViewModel internal constructor(
    private val firstRunStore: FirstRunStore,
    private val userDataProbe: UserDataProbe,
    private val settingsRepository: SettingsRepository,
    private val regionDefaults: RegionDefaults,
    private val widgetRefresher: WidgetRefresher,
    private val restoreLock: RestoreLock,
    private val clock: Clock,
    private val logger: AppLogger,
    /** Public for the screen's permission launcher, which needs the exact permissions to request. */
    val healthMetricsSource: HealthMetricsSource,
    private val savedStateHandle: SavedStateHandle,
    private val sdkInt: Int,
) : ViewModel() {
    @Inject constructor(
        firstRunStore: FirstRunStore,
        userDataProbe: UserDataProbe,
        settingsRepository: SettingsRepository,
        regionDefaults: RegionDefaults,
        widgetRefresher: WidgetRefresher,
        restoreLock: RestoreLock,
        clock: Clock,
        logger: AppLogger,
        healthMetricsSource: HealthMetricsSource,
        savedStateHandle: SavedStateHandle,
    ) : this(
        firstRunStore, userDataProbe, settingsRepository, regionDefaults, widgetRefresher, restoreLock, clock, logger,
        healthMetricsSource, savedStateHandle, Build.VERSION.SDK_INT,
    )

    private val _state = MutableStateFlow(
        when (readFlag()) {
            // A flag that can't be read opens the app (fail open) and is left as it is.
            true, null -> FirstRunGateState.ShowApp
            false -> FirstRunGateState.Loading
        },
    )
    val state: StateFlow<FirstRunGateState> = _state.asStateFlow()

    private val _message = MutableStateFlow<FirstRunMessage?>(null)

    /**
     * The snackbar message a restore from setup leaves for the app, until [messageShown]. Held as
     * state rather than sent once, so a rotation while it shows (which recreates the snackbar host)
     * shows it again instead of losing it.
     */
    val message: StateFlow<FirstRunMessage?> = _message.asStateFlow()

    /** The app has shown [message] for its full time, or the user dismissed it. */
    fun messageShown() {
        _message.value = null
    }

    private val _health = MutableStateFlow<SetupHealth>(SetupHealth.Hidden)

    /**
     * Setup's Health Connect section (first-run plan, O1f). Read with the setup values, so the
     * section is there on setup's first frame, and again on every resume while setup shows: the
     * Google Play link returns the user to Health Connect's own onboarding, not to LogEZ, and a
     * grant can also be made in Health Connect's settings.
     */
    val health: StateFlow<SetupHealth> = _health.asStateFlow()

    /**
     * A Connect came back with nothing granted. Kept in saved state, so it outlives process death
     * while setup shows; only a later read that finds a grant clears it.
     */
    private var healthRefused: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_HEALTH_REFUSED) ?: false
        set(value) {
            savedStateHandle[KEY_HEALTH_REFUSED] = value
        }

    /** Only the newest Health Connect read may set [health]; an older one finishing late is dropped. */
    private var healthReadGeneration = 0

    init {
        if (_state.value == FirstRunGateState.Loading) {
            viewModelScope.launch { decide() }
        }
    }

    /**
     * [resolve], and again each time it ends on a restore that is still running: the busy message
     * shows until the lock is free.
     *
     * The window that restored writes the flag only after it frees the lock (and after counting the
     * restored rows), so the re-check usually finds the restored rows with the flag still unwritten.
     * It then opens the app without writing `existing`: a flag written here would make that window's
     * resume open its app before its restore is handed to [restored], losing the screen's choices
     * for a backup without settings, the "Restored" message and path `restore`.
     */
    private suspend fun decide() {
        var afterRestore = false
        while (true) {
            val decided = resolve(recordExisting = !afterRestore)
            _state.value = decided
            if (decided != FirstRunGateState.RestoreBusy) return
            restoreLock.held.first { held -> !held }
            if (readFlag(FLAG_READ_FAILED_AFTER_RESTORE) == true) {
                _state.value = FirstRunGateState.ShowApp
                return
            }
            afterRestore = true
        }
    }

    /** @param recordExisting whether content found writes the flag with path `existing`. */
    private suspend fun resolve(recordExisting: Boolean): FirstRunGateState {
        // Health Connect is read alongside the probe, not after it, so its answer is usually in by
        // the time setup is decided. Not a child of the timed block either (readHealth never throws).
        val healthRead = viewModelScope.async { readHealth() }
        // Not a child of the timed block: see the class comment. viewModelScope's SupervisorJob keeps
        // a failure here from cancelling the scope, and `async` hands it to await() instead.
        // Set once readFirstRun is past the restore lock; read only on the main thread, like it.
        var pastRestoreLock = false
        val work = viewModelScope.async { readFirstRun(recordExisting, onRestoreLockFree = { pastRestoreLock = true }) }
        var resolved: FirstRunGateState? = null
        try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                val answer = work.await()
                resolved = answer
                // Setup is decided. It waits for Health Connect at most HEALTH_READ_TIMEOUT_MS, and
                // never past RESOLVE_TIMEOUT_MS: that timeout then ends only this wait, and setup
                // still shows, with the section hidden until setup's first resume reads again.
                if (answer is FirstRunGateState.ShowSetup) {
                    withTimeoutOrNull(HEALTH_READ_TIMEOUT_MS) { healthRead.await() }?.let { _health.value = it }
                }
            }
        } catch (e: CancellationException) {
            work.cancel()
            healthRead.cancel()
            throw e
        } catch (e: Exception) {
            healthRead.cancel()
            logger.e(TAG, "First-run check failed; opening the app", e)
            return FirstRunGateState.ShowApp
        }
        // Finished or not, the first read is over: a late answer is dropped, since the resume
        // re-check (or nothing, if the app opened) takes over.
        healthRead.cancel()
        if (resolved == null) {
            // The reads finish on their own thread and their answer is dropped; readFirstRun checks
            // for this cancellation before writing, so the flag stays unwritten (plan: fail open).
            work.cancel()
            // Still waiting on a restore: not a failure, and the app must not open empty (FX1).
            if (!pastRestoreLock) return FirstRunGateState.RestoreBusy
            logger.e(TAG, "First-run check took longer than ${RESOLVE_TIMEOUT_MS} ms; opening the app")
        }
        return resolved ?: FirstRunGateState.ShowApp
    }

    private suspend fun readFirstRun(recordExisting: Boolean, onRestoreLockFree: () -> Unit): FirstRunGateState {
        // A restore in flight (the launch-time resume, or one started from another window) is
        // replacing the database, so a probe now could offer setup over data that is about to
        // arrive (first-run plan, F14). Loading holds until it ends, within RESOLVE_TIMEOUT_MS;
        // past it, the gate shows RestoreBusy rather than opening the app (FX1).
        restoreLock.held.first { held -> !held }
        onRestoreLockFree()
        return if (userDataProbe.hasUserContent()) {
            // A probe that outlived the timeout must not write: the app already opened without it.
            currentCoroutineContext().ensureActive()
            if (recordExisting && !firstRunStore.markDone(FirstRunPath.EXISTING, clock.now().toEpochMilliseconds())) {
                logger.e(TAG, "Could not record first run for an install that already has data")
            }
            FirstRunGateState.ShowApp
        } else {
            val stored = settingsRepository.readStoredSetupValues()
            setupStateFor(stored, regionDefaults.suggest())
        }
    }

    /** The synchronous flag read; null when it throws, which is logged with [failureMessage]. */
    private fun readFlag(failureMessage: String = FLAG_READ_FAILED): Boolean? = try {
        firstRunStore.isDone()
    } catch (e: Exception) {
        logger.e(TAG, failureMessage, e)
        null
    }

    /**
     * Continue: writes the four setup settings, repaints the widget (one can be placed before the
     * app is ever opened, and it counts weeks from the first day), then writes the flag last, so a
     * process death in between shows setup again with the chosen values preselected. A failed
     * write still opens the app; setup then returns at the next launch.
     *
     * If another window has finished first run while this setup showed (for example a restore
     * there, whose rows the content check doesn't count, such as settings or step totals only),
     * nothing is written over it: this window opens the app, as [onResume] would.
     */
    fun complete(choices: SetupChoices) {
        val current = _state.value as? FirstRunGateState.ShowSetup ?: return
        if (current.working) return
        if (readFlag(FLAG_READ_FAILED_BEFORE_SETUP) == true) {
            _state.value = FirstRunGateState.ShowApp
            return
        }
        _state.value = current.copy(working = true)
        viewModelScope.launch {
            try {
                settingsRepository.applySetupChoices(choices)
                refreshWidget()
                if (!firstRunStore.markDone(FirstRunPath.SETUP, clock.now().toEpochMilliseconds())) {
                    logger.e(TAG, "Could not record first run after setup")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Saving the setup choices failed; opening the app", e)
            }
            _state.value = FirstRunGateState.HandOff(current)
        }
    }

    /**
     * A restore from setup finished (first-run plan, O1e). A backup without settings kept this
     * device's settings, so the choices on the screen are written, as Continue would; a backup with
     * settings has already replaced them, and nothing is written over them. Then the flag (path
     * `restore`), and the app opens on History, which setup left as the only screen, with
     * "Restored" or the left-out variant.
     *
     * The flag is written even when another window has written one meanwhile: the database now holds
     * this backup, so `restore` is what happened, whatever that window saw. A window that waited out
     * this restore behind [FirstRunGateState.RestoreBusy] writes nothing; one whose check ran before
     * the restore took the lock, or ended within [RESOLVE_TIMEOUT_MS] of it, may have written
     * `existing` or finished setup, and this replaces it.
     */
    fun restored(outcome: RestoreOutcome, choices: SetupChoices) {
        val current = _state.value as? FirstRunGateState.ShowSetup ?: return
        if (current.working) return
        _state.value = current.copy(working = true)
        viewModelScope.launch {
            finishRestore(backupHadSettings = outcome.backupHadSettings, choices = choices)
            val leftOut = outcome.unfinishedWorkoutsLeftOut
            _message.value = if (leftOut > 0) {
                FirstRunMessage(R.plurals.data_restore_done_left_out, count = leftOut)
            } else {
                FirstRunMessage(R.string.data_restore_done)
            }
            _state.value = FirstRunGateState.ShowApp
        }
    }

    /**
     * A confirmed restore from setup failed with [messageRes] (first-run plan, F13). Continue must
     * never start fresh over a database that already holds the backup.
     *
     * - A failure past the commit ([R.string.data_restore_incomplete]): the database holds the backup,
     *   whatever a content check would say (it doesn't count every restored table, such as daily
     *   step totals), so no check runs. As after a success, a backup without settings gets the
     *   screen's choices, since the next launch's resume has none to apply; the flag is written with
     *   path `restore`, and the app opens on History saying the restore did not finish.
     * - Any other failure: nothing of the backup arrived, so the database is checked again.
     *   - Content found: it was made elsewhere, such as a workout started in a second window. The
     *     flag is written with path `existing` unless another window already wrote one, and the app
     *     opens with the failure's own message.
     *   - No content: setup stays, and the screen keeps showing the failure.
     *   - The check throws or times out: the app opens with the failure's message and the flag stays
     *     unwritten, so the next launch decides again (fail open).
     *
     * @param backupHadSettings whether the backup carried settings; only read after the commit.
     * @param choices the choices on the screen when the restore failed.
     */
    fun restoreFailed(messageRes: Int, backupHadSettings: Boolean, choices: SetupChoices) {
        val current = _state.value as? FirstRunGateState.ShowSetup ?: return
        if (current.working) return
        _state.value = current.copy(working = true)
        viewModelScope.launch {
            if (messageRes == R.string.data_restore_incomplete) {
                finishRestore(backupHadSettings = backupHadSettings, choices = choices)
                _message.value = FirstRunMessage(messageRes)
                _state.value = FirstRunGateState.ShowApp
                return@launch
            }
            when (probeWithinTimeout()) {
                false -> _state.value = current
                true -> {
                    // A second window may already have finished first run; its path stays.
                    if (readFlag() != true) {
                        markDone(FirstRunPath.EXISTING, "Could not record first run after a failed restore")
                    }
                    _message.value = FirstRunMessage(messageRes)
                    _state.value = FirstRunGateState.ShowApp
                }
                null -> {
                    _message.value = FirstRunMessage(messageRes)
                    _state.value = FirstRunGateState.ShowApp
                }
            }
        }
    }

    /**
     * The writes after a restore that reached the database: the screen's choices when the backup
     * carried no settings, the widget, then the flag (path `restore`). A failed write is logged and
     * the app still opens; the restored data is in place, and the settings can be changed later.
     */
    private suspend fun finishRestore(backupHadSettings: Boolean, choices: SetupChoices) {
        if (!backupHadSettings) {
            try {
                settingsRepository.applySetupChoices(choices)
                refreshWidget()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Saving the setup choices after a restore failed", e)
            }
        }
        markDone(FirstRunPath.RESTORE, "Could not record first run after a restore")
    }

    /**
     * The content check on its own, bounded by [RESOLVE_TIMEOUT_MS] the way [resolve] is: the read
     * runs in its own coroutine and only the wait is timed. Null when it throws or times out.
     */
    private suspend fun probeWithinTimeout(): Boolean? {
        val work = viewModelScope.async { userDataProbe.hasUserContent() }
        val found = try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { work.await() }
        } catch (e: CancellationException) {
            work.cancel()
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Content check after a failed restore failed; opening the app", e)
            return null
        }
        if (found == null) {
            work.cancel()
            logger.e(TAG, "Content check after a failed restore took longer than ${RESOLVE_TIMEOUT_MS} ms; opening the app")
        }
        return found
    }

    private suspend fun markDone(path: FirstRunPath, failureMessage: String) {
        val written = try {
            firstRunStore.markDone(path, clock.now().toEpochMilliseconds())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, failureMessage, e)
            return
        }
        if (!written) logger.e(TAG, failureMessage)
    }

    /** The app has navigated to the Workout tab under the overlay; remove the overlay. */
    fun handoffDone() {
        if (_state.value is FirstRunGateState.HandOff) _state.value = FirstRunGateState.ShowApp
    }

    /**
     * Called on every resume while setup shows. A second `MainActivity` (a widget tap uses the
     * standard launch mode) may have finished setup meanwhile; this one then opens the app.
     * Skipped while this window's own Continue is being written: its flag write makes [FirstRunStore.isDone]
     * true before the hand-off, and switching to the app then would flash History under the overlay.
     * A flag that can't be read counts as not done.
     *
     * It also re-asks the region: a language or region change while setup shows recreates the
     * Activity but keeps this ViewModel. The preselected values stay as they were, but the region
     * note only stays while they are still what the region suggests.
     */
    fun onResume() {
        val current = _state.value
        if (current !is FirstRunGateState.ShowSetup || current.working) return
        if (readFlag() == true) {
            _state.value = FirstRunGateState.ShowApp
            return
        }
        refreshHealth()
        val suggestion = try {
            regionDefaults.suggest()
        } catch (e: Exception) {
            logger.e(TAG, "Region lookup on resume failed; keeping the region note as it was", e)
            return
        }
        val noteVisible = regionNoteVisible(current.preselected, suggestion)
        if (noteVisible != current.regionNoteVisible) {
            _state.value = current.copy(regionNoteVisible = noteVisible)
        }
    }

    /**
     * Health Connect's permission screen answered Connect. Any grant ends a disconnect made earlier
     * in this process ([HealthMetricsSource.onPermissionsRegranted]), as on Profile. Nothing granted
     * records a refusal and shows it at once, since Health Connect won't show its screen again.
     * Either way the grants are then read again.
     */
    fun onHealthConnectResult(anyGranted: Boolean) {
        if (anyGranted) {
            healthMetricsSource.onPermissionsRegranted()
        } else {
            healthRefused = true
            if (_health.value == SetupHealth.CanConnect) _health.value = SetupHealth.Refused
        }
        refreshHealth()
    }

    private fun refreshHealth() {
        val generation = ++healthReadGeneration
        viewModelScope.launch {
            val read = readHealth() ?: return@launch
            if (generation == healthReadGeneration) _health.value = read
        }
    }

    /**
     * The section's state now; null when Health Connect can't be read, which keeps the section as it
     * was. A refusal is cleared only by a read that finds a grant (first-run plan, O1f).
     *
     * When Health Connect is usable but its grants can't be read (it can fail while its app has just
     * been installed or updated), a section that still says it is missing or out of date, or no
     * section at all, would be wrong; it offers Connect (or the refusal) instead, since Connect's own
     * request reports the grants anyway. A section that already shows Connect, a refusal or the
     * grants keeps them.
     */
    private suspend fun readHealth(): SetupHealth? {
        val availability: HealthConnectAvailability
        val granted: Set<HealthDataType>?
        try {
            availability = healthMetricsSource.availability()
            granted = if (availability == HealthConnectAvailability.Available) {
                healthMetricsSource.grantedTypesOrNull()
            } else {
                emptySet()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, HEALTH_READ_FAILED, e)
            return null
        }
        if (granted == null) {
            // The source has logged why; a failed read is not "nothing granted".
            logger.e(TAG, HEALTH_READ_FAILED)
            return when (_health.value) {
                SetupHealth.Hidden, SetupHealth.NotInstalled, SetupHealth.UpdateRequired ->
                    setupHealthFor(availability, emptySet(), refused = healthRefused, sdkInt = sdkInt)
                else -> null
            }
        }
        if (granted.isNotEmpty()) healthRefused = false
        return setupHealthFor(availability, granted, refused = healthRefused, sdkInt = sdkInt)
    }

    private suspend fun refreshWidget() {
        try {
            widgetRefresher.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The widget repaints itself at midnight anyway; this must not stop the flag write.
            logger.e(TAG, "Widget refresh after setup failed", e)
        }
    }

    companion object {
        private const val TAG = "FirstRunGate"

        /** The starting point from the plan; O1c measures the real time to setup and tunes it. */
        const val RESOLVE_TIMEOUT_MS = 3_000L

        /**
         * How long setup, once decided, may still wait for the first Health Connect read, which
         * started with the probe. Never past [RESOLVE_TIMEOUT_MS]: a slow Health Connect can delay
         * setup a little, but never skip it.
         */
        const val HEALTH_READ_TIMEOUT_MS = 1_000L

        private const val HEALTH_READ_FAILED = "Reading Health Connect for setup failed"

        private const val FLAG_READ_FAILED = "Could not read the first-run flag; opening the app"
        private const val FLAG_READ_FAILED_AFTER_RESTORE =
            "Could not read the first-run flag after a restore; deciding again from the database"
        private const val FLAG_READ_FAILED_BEFORE_SETUP = "Could not read the first-run flag before saving setup; saving it"

        private const val KEY_HEALTH_REFUSED = "first_run_health_refused"
    }
}

/**
 * Stored values win over the region's suggestion, one by one. A stored week start the app doesn't
 * offer (only reachable by hand-editing a backup) can't be shown as selected, so it gives way to the
 * region too. Body measurements always follow the weight unit shown.
 */
internal fun setupStateFor(stored: StoredSetupValues, suggestion: RegionSuggestion): FirstRunGateState.ShowSetup {
    val choices = SetupChoices(
        weightUnit = stored.weightUnit ?: suggestion.weightUnit,
        distanceUnit = stored.distanceUnit ?: suggestion.distanceUnit,
        firstDayOfWeek = stored.firstDayOfWeek?.takeIf { it in SetupChoices.OFFERED_FIRST_DAYS }
            ?: suggestion.firstDayOfWeek,
    )
    return FirstRunGateState.ShowSetup(
        preselected = choices,
        regionNoteVisible = regionNoteVisible(choices, suggestion),
    )
}

/**
 * "Suggested from your phone's region." is true only when every preselected value is the region's
 * suggestion and the region's week start was one the app offers.
 */
private fun regionNoteVisible(preselected: SetupChoices, suggestion: RegionSuggestion): Boolean =
    preselected.weightUnit == suggestion.weightUnit &&
        preselected.distanceUnit == suggestion.distanceUnit &&
        preselected.firstDayOfWeek == suggestion.firstDayOfWeek &&
        !suggestion.weekStartClamped
