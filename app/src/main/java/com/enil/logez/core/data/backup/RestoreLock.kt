package com.enil.logez.core.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * One restore at a time, across the whole process (first-run plan, F14).
 *
 * Every restore unpacks into the same staging folder ([RestoreMarker.stagingDirIn]), and
 * [BackupReader.stage] empties it first. The screen's own busy guard is per ViewModel, but a
 * widget tap can open a second MainActivity with its own, and the launch-time
 * [BackupRestorer.resumeIfInterrupted] deletes that folder from a background coroutine. Without
 * this, any of them could empty a backup another had staged and was about to restore.
 *
 * A holder keeps the lock from staging through the restore or the cancel. It is held by an owner
 * object, so only the holder can release it, and acquiring again with the same owner succeeds.
 */
@Singleton
class RestoreLock @Inject constructor() {
    private var owner: Any? = null
    private val _held = MutableStateFlow(false)

    /** True while anyone holds the lock. */
    val held: StateFlow<Boolean> = _held.asStateFlow()

    /** Takes the lock for [owner] if it is free or already [owner]'s. Never waits. */
    fun tryAcquire(owner: Any): Boolean = synchronized(this) {
        when {
            this.owner == null -> {
                this.owner = owner
                _held.value = true
                true
            }
            this.owner === owner -> true
            else -> false
        }
    }

    fun isHeldBy(owner: Any): Boolean = synchronized(this) { this.owner === owner }

    /** Frees the lock if [owner] holds it; otherwise does nothing. */
    fun release(owner: Any) = synchronized(this) {
        if (this.owner === owner) {
            this.owner = null
            _held.value = false
        }
    }

    /** Waits until the lock is free, runs [block] holding it, then releases it. */
    suspend fun <T> withLock(owner: Any, block: suspend () -> T): T {
        while (!tryAcquire(owner)) held.first { !it }
        try {
            return block()
        } finally {
            release(owner)
        }
    }
}
