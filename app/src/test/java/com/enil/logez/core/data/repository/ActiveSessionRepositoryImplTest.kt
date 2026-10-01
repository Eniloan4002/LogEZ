package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enil.logez.core.domain.model.ActiveInlineTimerSnapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real DataStore-backed active-session store over a real preferences file: what carries a running set timer across process death. */
class ActiveSessionRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ActiveSessionRepositoryImpl

    private val countdown = ActiveInlineTimerSnapshot(
        exerciseId = "we1", setId = "s2", mode = "COUNTDOWN", startWallMillis = 1_700_000_000_000L, startElapsedRealtimeMillis = 5_000_000L, targetSeconds = 90,
    )
    private val stopwatch = ActiveInlineTimerSnapshot(
        exerciseId = "we3", setId = "s9", mode = null, startWallMillis = 1_700_000_100_000L, startElapsedRealtimeMillis = 6_000_000L, targetSeconds = null,
    )

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(tmp.root, "active.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = ActiveSessionRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun blocking(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun storedKeyNames(): Set<String> = dataStore.data.first().asMap().keys.map { it.name }.toSet()

    @Test
    fun `a countdown round-trips with its mode and target`() = blocking {
        repository.startSession("w1", 1_000L, false)
        repository.updateInlineTimer(countdown)
        assertEquals(countdown, repository.getSnapshot().inlineTimer)
    }

    @Test
    fun `a stopwatch round-trips with no mode and no target`() = blocking {
        repository.startSession("w1", 1_000L, false)
        repository.updateInlineTimer(stopwatch)
        assertEquals(stopwatch, repository.getSnapshot().inlineTimer)
        assertEquals(
            setOf("workoutId", "isPaused", "accumulatedActiveSeconds", "isEmptyWorkoutTimerMode", "lastResumedAtMillis", "inlineTimerExerciseId", "inlineTimerSetId", "inlineTimerStartWallMillis", "inlineTimerStartElapsedRealtimeMillis"),
            storedKeyNames(),
        )
    }

    @Test
    fun `saving a stopwatch over a countdown removes the old mode and target keys`() = blocking {
        repository.updateInlineTimer(countdown)
        repository.updateInlineTimer(stopwatch)
        assertEquals(stopwatch, repository.getSnapshot().inlineTimer)
        assertEquals(false, "inlineTimerMode" in storedKeyNames())
        assertEquals(false, "inlineTimerTargetSeconds" in storedKeyNames())
    }

    @Test
    fun `saving null removes every timer key`() = blocking {
        repository.updateInlineTimer(countdown)
        repository.updateInlineTimer(null)
        assertNull(repository.getSnapshot().inlineTimer)
        assertEquals(emptySet<String>(), storedKeyNames())
    }

    @Test
    fun `a timer missing one of its required keys reads back as no timer`() = blocking {
        dataStore.edit { it[stringPreferencesKey("inlineTimerExerciseId")] = "we1" }
        assertNull(repository.getSnapshot().inlineTimer)
    }

    @Test
    fun `starting a new session and clearing the session both wipe the timer`() = blocking {
        repository.updateInlineTimer(countdown)
        repository.startSession("w2", 2_000L, false)
        assertNull(repository.getSnapshot().inlineTimer)

        repository.updateInlineTimer(countdown)
        repository.clearSession()
        assertNull(repository.getSnapshot().inlineTimer)
        assertNull(repository.getSnapshot().workoutId)
    }
}
