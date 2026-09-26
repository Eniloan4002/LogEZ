package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.UserDataProbe
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeUserDataProbe(var hasContent: Boolean = false) : UserDataProbe {
    /** Thrown from [hasUserContent] when set, standing in for a failed Room read. */
    var error: Exception? = null

    /** When true, [hasUserContent] suspends until cancelled, standing in for a read stuck behind the seed. */
    var neverReturns: Boolean = false

    /**
     * When set, [hasUserContent] takes this long on virtual time and ignores cancellation, standing
     * in for a Room read blocked on its thread (Room's suspend DAO calls can't return early when
     * cancelled).
     */
    var blockMillis: Long? = null

    var callCount = 0
        private set

    override suspend fun hasUserContent(): Boolean {
        callCount++
        error?.let { throw it }
        if (neverReturns) awaitCancellation()
        blockMillis?.let { withContext(NonCancellable) { delay(it) } }
        return hasContent
    }
}
