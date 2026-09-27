package com.enil.logez.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.RegionDefaults
import com.enil.logez.core.common.RegionSuggestion
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.StoredSetupValues
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.FirstRunStore
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.UserDataProbe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * @param working Continue is being written; Continue (and later Restore) are disabled.
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

    /** The app as it is today. */
    data object ShowApp : FirstRunGateState
}

/**
 * Decides whether a launch shows first-run setup, and records how first run ended.
 *
 * Setup shows only when the first-run flag is absent and the database holds nothing the user made.
 * If the flag is absent but content exists, the flag is written silently (path `existing`) and the
 * app opens as it does today. Everything fails open: a read that throws or takes longer than
 * [RESOLVE_TIMEOUT_MS] opens the app and leaves the flag unwritten, so setup is offered again at
 * the next launch.
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
 */
@HiltViewModel
class FirstRunGateViewModel @Inject constructor(
    private val firstRunStore: FirstRunStore,
    private val userDataProbe: UserDataProbe,
    private val settingsRepository: SettingsRepository,
    private val regionDefaults: RegionDefaults,
    private val widgetRefresher: WidgetRefresher,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(
        when (readFlag()) {
            // A flag that can't be read opens the app (fail open) and is left as it is.
            true, null -> FirstRunGateState.ShowApp
            false -> FirstRunGateState.Loading
        },
    )
    val state: StateFlow<FirstRunGateState> = _state.asStateFlow()

    init {
        if (_state.value == FirstRunGateState.Loading) {
            viewModelScope.launch { _state.value = resolve() }
        }
    }

    private suspend fun resolve(): FirstRunGateState {
        // Not a child of the timed block: see the class comment. viewModelScope's SupervisorJob keeps
        // a failure here from cancelling the scope, and `async` hands it to await() instead.
        val work = viewModelScope.async { readFirstRun() }
        val resolved = try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { work.await() }
        } catch (e: CancellationException) {
            work.cancel()
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "First-run check failed; opening the app", e)
            return FirstRunGateState.ShowApp
        }
        if (resolved == null) {
            // The reads finish on their own thread and their answer is dropped; readFirstRun checks
            // for this cancellation before writing, so the flag stays unwritten (plan: fail open).
            work.cancel()
            logger.e(TAG, "First-run check took longer than ${RESOLVE_TIMEOUT_MS} ms; opening the app")
        }
        return resolved ?: FirstRunGateState.ShowApp
    }

    private suspend fun readFirstRun(): FirstRunGateState =
        if (userDataProbe.hasUserContent()) {
            // A probe that outlived the timeout must not write: the app already opened without it.
            currentCoroutineContext().ensureActive()
            if (!firstRunStore.markDone(FirstRunPath.EXISTING, clock.now().toEpochMilliseconds())) {
                logger.e(TAG, "Could not record first run for an install that already has data")
            }
            FirstRunGateState.ShowApp
        } else {
            val stored = settingsRepository.readStoredSetupValues()
            setupStateFor(stored, regionDefaults.suggest())
        }

    /** The synchronous flag read; null when it throws, which is logged. */
    private fun readFlag(): Boolean? = try {
        firstRunStore.isDone()
    } catch (e: Exception) {
        logger.e(TAG, "Could not read the first-run flag; opening the app", e)
        null
    }

    /**
     * Continue: writes the four setup settings, repaints the widget (one can be placed before the
     * app is ever opened, and it counts weeks from the first day), then writes the flag last, so a
     * process death in between shows setup again with the chosen values preselected. A failed
     * write still opens the app; setup then returns at the next launch.
     */
    fun complete(choices: SetupChoices) {
        val current = _state.value as? FirstRunGateState.ShowSetup ?: return
        if (current.working) return
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
