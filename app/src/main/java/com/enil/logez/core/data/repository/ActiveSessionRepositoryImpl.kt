package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enil.logez.core.di.ActiveSessionDataStore
import com.enil.logez.core.domain.model.ActiveInlineTimerSnapshot
import com.enil.logez.core.domain.model.ActiveSessionSnapshot
import com.enil.logez.core.domain.repository.ActiveSessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** DataStore-backed, deliberately separate from [SettingsRepositoryImpl]'s file (§9.5 — its own store, not Room). */
class ActiveSessionRepositoryImpl @Inject constructor(
    @ActiveSessionDataStore private val dataStore: DataStore<Preferences>,
) : ActiveSessionRepository {
    private object Keys {
        val WORKOUT_ID = stringPreferencesKey("workoutId")
        val IS_PAUSED = booleanPreferencesKey("isPaused")
        val ACCUMULATED_ACTIVE_SECONDS = longPreferencesKey("accumulatedActiveSeconds")
        val LAST_RESUMED_AT_MILLIS = longPreferencesKey("lastResumedAtMillis")
        val IS_EMPTY_WORKOUT_TIMER_MODE = booleanPreferencesKey("isEmptyWorkoutTimerMode")
        val REST_DEADLINE_ELAPSED_REALTIME_MILLIS = longPreferencesKey("restDeadlineElapsedRealtimeMillis")
        val REST_EXERCISE_ID = stringPreferencesKey("restExerciseId")
        val TIMER_EXERCISE_ID = stringPreferencesKey("inlineTimerExerciseId")
        val TIMER_SET_ID = stringPreferencesKey("inlineTimerSetId")
        val TIMER_MODE = stringPreferencesKey("inlineTimerMode")
        val TIMER_START_WALL_MILLIS = longPreferencesKey("inlineTimerStartWallMillis")
        val TIMER_START_ELAPSED_REALTIME_MILLIS = longPreferencesKey("inlineTimerStartElapsedRealtimeMillis")
        val TIMER_TARGET_SECONDS = longPreferencesKey("inlineTimerTargetSeconds")
    }

    override suspend fun getSnapshot(): ActiveSessionSnapshot {
        val prefs = dataStore.data.first()
        return ActiveSessionSnapshot(
            workoutId = prefs[Keys.WORKOUT_ID],
            isPaused = prefs[Keys.IS_PAUSED] ?: false,
            accumulatedActiveSeconds = prefs[Keys.ACCUMULATED_ACTIVE_SECONDS] ?: 0L,
            lastResumedAtMillis = prefs[Keys.LAST_RESUMED_AT_MILLIS],
            isEmptyWorkoutTimerMode = prefs[Keys.IS_EMPTY_WORKOUT_TIMER_MODE] ?: false,
            restDeadlineElapsedRealtimeMillis = prefs[Keys.REST_DEADLINE_ELAPSED_REALTIME_MILLIS],
            restExerciseId = prefs[Keys.REST_EXERCISE_ID],
            inlineTimer = readInlineTimer(prefs),
        )
    }

    private fun readInlineTimer(prefs: Preferences): ActiveInlineTimerSnapshot? {
        val exerciseId = prefs[Keys.TIMER_EXERCISE_ID] ?: return null
        val setId = prefs[Keys.TIMER_SET_ID] ?: return null
        val startWall = prefs[Keys.TIMER_START_WALL_MILLIS] ?: return null
        val startElapsed = prefs[Keys.TIMER_START_ELAPSED_REALTIME_MILLIS] ?: return null
        return ActiveInlineTimerSnapshot(
            exerciseId = exerciseId,
            setId = setId,
            mode = prefs[Keys.TIMER_MODE],
            startWallMillis = startWall,
            startElapsedRealtimeMillis = startElapsed,
            targetSeconds = prefs[Keys.TIMER_TARGET_SECONDS]?.toInt(),
        )
    }

    override suspend fun updateInlineTimer(timer: ActiveInlineTimerSnapshot?) {
        dataStore.edit { prefs ->
            if (timer == null) {
                prefs.remove(Keys.TIMER_EXERCISE_ID)
                prefs.remove(Keys.TIMER_SET_ID)
                prefs.remove(Keys.TIMER_MODE)
                prefs.remove(Keys.TIMER_START_WALL_MILLIS)
                prefs.remove(Keys.TIMER_START_ELAPSED_REALTIME_MILLIS)
                prefs.remove(Keys.TIMER_TARGET_SECONDS)
            } else {
                prefs[Keys.TIMER_EXERCISE_ID] = timer.exerciseId
                prefs[Keys.TIMER_SET_ID] = timer.setId
                if (timer.mode != null) prefs[Keys.TIMER_MODE] = timer.mode else prefs.remove(Keys.TIMER_MODE)
                prefs[Keys.TIMER_START_WALL_MILLIS] = timer.startWallMillis
                prefs[Keys.TIMER_START_ELAPSED_REALTIME_MILLIS] = timer.startElapsedRealtimeMillis
                if (timer.targetSeconds != null) prefs[Keys.TIMER_TARGET_SECONDS] = timer.targetSeconds.toLong() else prefs.remove(Keys.TIMER_TARGET_SECONDS)
            }
        }
    }

    override suspend fun startSession(workoutId: String, lastResumedAtMillis: Long?, isEmptyWorkoutTimerMode: Boolean) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.WORKOUT_ID] = workoutId
            prefs[Keys.IS_PAUSED] = lastResumedAtMillis == null
            prefs[Keys.ACCUMULATED_ACTIVE_SECONDS] = 0L
            prefs[Keys.IS_EMPTY_WORKOUT_TIMER_MODE] = isEmptyWorkoutTimerMode
            if (lastResumedAtMillis != null) prefs[Keys.LAST_RESUMED_AT_MILLIS] = lastResumedAtMillis
        }
    }

    override suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }

    override suspend fun updateDurationBookkeeping(isPaused: Boolean, accumulatedActiveSeconds: Long, lastResumedAtMillis: Long?) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_PAUSED] = isPaused
            prefs[Keys.ACCUMULATED_ACTIVE_SECONDS] = accumulatedActiveSeconds
            if (lastResumedAtMillis != null) prefs[Keys.LAST_RESUMED_AT_MILLIS] = lastResumedAtMillis else prefs.remove(Keys.LAST_RESUMED_AT_MILLIS)
        }
    }

    override suspend fun updateRestTimer(deadlineElapsedRealtimeMillis: Long?, exerciseId: String?) {
        dataStore.edit { prefs ->
            if (deadlineElapsedRealtimeMillis != null) prefs[Keys.REST_DEADLINE_ELAPSED_REALTIME_MILLIS] = deadlineElapsedRealtimeMillis else prefs.remove(Keys.REST_DEADLINE_ELAPSED_REALTIME_MILLIS)
            if (exerciseId != null) prefs[Keys.REST_EXERCISE_ID] = exerciseId else prefs.remove(Keys.REST_EXERCISE_ID)
        }
    }
}
