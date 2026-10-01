package com.enil.logez.core.domain.repository

/**
 * Whether this install has passed first-run setup, and how.
 *
 * The read is synchronous on purpose: the first-run gate reads it while it is being created, so a
 * launch that has already passed setup opens the app with no loading frame and no database query.
 */
interface FirstRunStore {
    /** True once any path through first run has been recorded. */
    fun isDone(): Boolean

    /**
     * How the install passed first run, or null if it has not, or if the stored name is one this
     * build does not know. An unknown name is left in place, never rewritten.
     */
    fun path(): FirstRunPath?

    /**
     * Records that first run is over. Written with `commit()` off the main thread, so the result
     * is known before the caller moves on. Returns false if the write did not reach disk; the
     * caller then opens the app anyway and setup shows again at the next launch.
     */
    suspend fun markDone(path: FirstRunPath, atEpochMillis: Long): Boolean

    /**
     * Removes the first-run keys only; every other UI flag in the same file stays, including the
     * one-time tip keys (Delete all data brings setup back, not the tips).
     */
    suspend fun clear()

    /** Whether the one-time [tip] has been shown (or dismissed) on this install. */
    fun isTipSeen(tip: TipId): Boolean

    /**
     * Records that [tip] has been on screen or dismissed, so it never shows again by itself. Not
     * suspending: the change is in memory at once and reaches disk in the background, so leaving
     * the screen straight after (which cancels the caller's scope) can't lose it.
     */
    fun markTipSeen(tip: TipId)

    /** True once "Show tips again" has been used, so tips show on an install that skipped setup. */
    fun tipsReenabled(): Boolean

    /**
     * "Show tips again": clears every seen-tip key and records that tips are re-enabled. Never
     * touches the first-run keys or any other UI flag, such as the notification prompt's decline.
     */
    suspend fun showTipsAgain()
}

/** A one-time tip. [storedName] is part of its key on disk and must never change. */
enum class TipId(val storedName: String) {
    /** The exercise picker's search tip. */
    PICKER("picker"),

    /** The live logger's "Logging tips" card. */
    LOGGER("logger"),

    /** The routine builder's REPS and Rest Timer tip. */
    BUILDER("builder"),
}

/** How an install passed first run. [storedName] is what goes on disk and must never change. */
enum class FirstRunPath(val storedName: String) {
    /** Continue on the setup screen. */
    SETUP("setup"),

    /** A backup restored from the setup screen. */
    RESTORE("restore"),

    /** The app found user content already there, so setup was skipped silently. */
    EXISTING("existing"),
    ;

    companion object {
        fun fromStoredName(name: String?): FirstRunPath? = entries.firstOrNull { it.storedName == name }
    }
}
