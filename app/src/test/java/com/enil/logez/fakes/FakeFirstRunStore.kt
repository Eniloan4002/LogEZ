package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.FirstRunStore

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

    var markDoneCallCount = 0
        private set

    override fun isDone(): Boolean {
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
}
