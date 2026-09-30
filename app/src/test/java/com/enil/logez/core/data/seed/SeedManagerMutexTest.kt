package com.enil.logez.core.data.seed

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.repository.RoomTransactionRunner
import com.enil.logez.fakes.GatedTransactionRunner
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan O1d, the seed overlap: a restore resets the seed version and re-seeds. If the
 * first seed's version write could land between that reset and the re-seed's read, the re-seed
 * was skipped and the library the restore wiped never came back. [SeedManager] makes a seed pass
 * one step as far as a reset is concerned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedManagerMutexTest : RoomDatabaseTestBase() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val seedVersionKey = intPreferencesKey("lastAppliedSeedVersion")

    @Test
    fun `a reset waits for a running seed pass, so the seed's version write cannot land after it`() = runBlocking {
        withTimeout(60_000) {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
                File(context.filesDir, "seed_mutex_${System.nanoTime()}.preferences_pb")
            }
            // The seed commits its rows, then pauses before writing its version.
            val runner = GatedTransactionRunner(RoomTransactionRunner(database))
            val seedManager = SeedManager(context, database.exerciseDao(), store, runner, AppLogger.NoOp)

            val seed = async(Dispatchers.IO) { seedManager.seedIfNeeded() }
            runner.committed.await()
            val reset = async(Dispatchers.IO) { seedManager.resetSeedVersion() }

            // Bounded so a reset that does not wait fails here rather than hanging the build.
            assertNull(withTimeoutOrNull(500) { reset.await() })
            assertFalse(reset.isCompleted)

            runner.proceed.complete(Unit)
            seed.await()
            reset.await()

            // The reset came after the seed's write, so the next seed pass runs in full.
            assertNull(store.data.first()[seedVersionKey])
            assertEquals(0, seedManager.lastAppliedSeedVersion())
        }
    }
}
