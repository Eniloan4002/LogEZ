package com.enil.logez.core.domain.repository

import com.enil.logez.core.domain.model.ActiveInlineTimerSnapshot
import com.enil.logez.core.domain.model.ActiveSessionSnapshot

/** PHASE2_PLAN.md §9.5 — persistence seam for [ActiveSessionSnapshot], backed by a dedicated DataStore (never Room). */
interface ActiveSessionRepository {
    suspend fun getSnapshot(): ActiveSessionSnapshot
    suspend fun startSession(workoutId: String, lastResumedAtMillis: Long?, isEmptyWorkoutTimerMode: Boolean)
    suspend fun clearSession()
    suspend fun updateDurationBookkeeping(isPaused: Boolean, accumulatedActiveSeconds: Long, lastResumedAtMillis: Long?)
    suspend fun updateRestTimer(deadlineElapsedRealtimeMillis: Long?, exerciseId: String?)

    /** The running set timer (a stopwatch or a countdown), or null once it stops. Saved so it survives process death. */
    suspend fun updateInlineTimer(timer: ActiveInlineTimerSnapshot?)
}
