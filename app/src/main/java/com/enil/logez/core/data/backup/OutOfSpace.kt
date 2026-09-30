package com.enil.logez.core.data.backup

import android.database.sqlite.SQLiteFullException

/**
 * True when [t], or anything that caused it, says the device's storage is full (first-run plan,
 * F13). A restore that runs out of space used to be reported as "That file isn't a LogEZ backup",
 * which sends the user looking for a different file instead of freeing space.
 *
 * Android reports a full disk as an `ErrnoException` whose message names ENOSPC, usually wrapped
 * in an IOException carrying the same text, and SQLite as [SQLiteFullException].
 */
fun isOutOfSpace(t: Throwable): Boolean =
    generateSequence(t) { it.cause }.take(MAX_CAUSE_DEPTH).any { e ->
        val message = e.message.orEmpty()
        e is SQLiteFullException ||
            message.contains("ENOSPC") ||
            message.contains("No space left on device", ignoreCase = true)
    }

/** Guards against a cause chain that loops back on itself. */
private const val MAX_CAUSE_DEPTH = 16
