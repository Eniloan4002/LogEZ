package com.enil.logez.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.FirstRunStore
import com.enil.logez.core.domain.repository.TipId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * First-run plan (O1g): which one-time tips may still show, and "Show tips again".
 *
 * A tip may show only on an install that passed through setup (`first_run_path = setup`) or after
 * "Show tips again", and only until it is marked seen. The screens decide when a tip counts as
 * seen: once it has actually been on screen, or when "Got it" is tapped ([rememberOneTimeTip]).
 * A seen tip stays on screen for the rest of that visit, which the screen remembers, not this.
 *
 * The reads are synchronous SharedPreferences reads (the first-run gate has already loaded the
 * file at launch), so the first value is the real one and no screen draws a tip it then removes.
 */
@HiltViewModel
class TipsViewModel @Inject constructor(
    private val firstRunStore: FirstRunStore,
    private val logger: AppLogger,
) : ViewModel() {
    private val _unseen = MutableStateFlow(readUnseen())

    /** The tips that may still show. Empty on installs that skipped setup, until "Show tips again". */
    val unseen: StateFlow<Set<TipId>> = _unseen.asStateFlow()

    private val _tipsShownAgain = MutableStateFlow(false)

    /** True once "Show tips again" has finished on this screen visit, for the Settings row's subtitle. */
    val tipsShownAgain: StateFlow<Boolean> = _tipsShownAgain.asStateFlow()

    /** Reads the store again, so a screen kept under Settings picks up "Show tips again" on return. */
    fun refresh() {
        _unseen.value = readUnseen()
    }

    /** Records [tip] as seen. It leaves [unseen] at once; a screen that showed it keeps it for the visit. */
    fun markSeen(tip: TipId) {
        if (tip !in _unseen.value) return
        _unseen.update { it - tip }
        runCatching { firstRunStore.markTipSeen(tip) }
            .onFailure { logger.e(TAG, "Recording a seen tip failed", it) }
    }

    /** "Show tips again" in Settings > Workouts. */
    fun showTipsAgain() {
        viewModelScope.launch {
            try {
                firstRunStore.showTipsAgain()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Showing tips again failed", e)
                return@launch
            }
            _unseen.value = readUnseen()
            _tipsShownAgain.value = true
        }
    }

    private fun readUnseen(): Set<TipId> = runCatching {
        val allowed = firstRunStore.path() == FirstRunPath.SETUP || firstRunStore.tipsReenabled()
        if (allowed) TipId.entries.filterNot(firstRunStore::isTipSeen).toSet() else emptySet()
    }.getOrElse {
        logger.e(TAG, "Reading the tip flags failed", it)
        emptySet()
    }

    private companion object {
        const val TAG = "TipsViewModel"
    }
}
