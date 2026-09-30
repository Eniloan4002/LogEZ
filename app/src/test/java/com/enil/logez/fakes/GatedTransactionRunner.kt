package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.TransactionRunner
import kotlinx.coroutines.CompletableDeferred

/**
 * Wraps a real [TransactionRunner] so a test can order two writers deterministically. The first
 * transaction commits through [delegate], then signals [committed] and, when [holdAfterCommit] is
 * true, waits for [proceed] before returning to its caller. Later transactions pass straight through.
 */
class GatedTransactionRunner(
    private val delegate: TransactionRunner,
    private val holdAfterCommit: Boolean = true,
) : TransactionRunner {
    val committed = CompletableDeferred<Unit>()
    val proceed = CompletableDeferred<Unit>()
    private var gated = false

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        val result = delegate.runInTransaction(block)
        val first = synchronized(this) { !gated.also { gated = true } }
        if (first) {
            committed.complete(Unit)
            if (holdAfterCommit) proceed.await()
        }
        return result
    }
}
