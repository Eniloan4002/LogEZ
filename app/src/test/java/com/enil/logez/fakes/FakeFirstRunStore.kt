package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.FirstRunStore
import com.enil.logez.core.domain.repository.TipId

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2) of the `logez_ui_flags` first-run keys. */
class FakeFirstRunStore(
    var doneAt: Long? = null,
    var storedPath: FirstRunPath? = null,
) : FirstRunStore {
    /** What [markDone] reports; false stands in for a `commit()` that did not reach disk. */
    var markDoneSucceeds: Boolean = true

    /** Runs inside [markDone] before anything is recorded, so a test can check what was already written by then. */
    var onMarkDone: () -> Unit = {}

    /** Thrown from [isDone] when set, standing in for preferences that fail to load. */
    var isDoneError: Exception? = null

    /** Runs at the start of every [isDone], so a test can change things at that exact point. */
    var onIsDone: () -> Unit = {}

    var markDoneCallCount = 0
        private set

    override fun isDone(): Boolean {
        onIsDone()
        isDoneError?.let { throw it }
        return doneAt != null
    }

    override fun path(): FirstRunPath? = storedPath

    override suspend fun markDone(path: FirstRunPath, atEpochMillis: Long): Boolean {
        markDoneCallCount++
        onMarkDone()
        if (!markDoneSucceeds) return false
        doneAt = atEpochMillis
        storedPath = path
        return true
    }

    override suspend fun clear() {
        doneAt = null
        storedPath = null
    }

    /** The `tip_seen_*` keys, by tip. */
    val seenTips: MutableSet<TipId> = mutableSetOf()

    /** The `tips_reenabled` key. */
    var reenabled: Boolean = false

    var markTipSeenCallCount = 0
        private set

    override fun isTipSeen(tip: TipId): Boolean = tip in seenTips

    override fun markTipSeen(tip: TipId) {
        markTipSeenCallCount++
        seenTips += tip
    }

    override fun tipsReenabled(): Boolean = reenabled

    override suspend fun showTipsAgain() {
        seenTips.clear()
        reenabled = true
    }
}
