package com.enil.logez.core.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** First-run plan, F14: one restore at a time across the process. */
class RestoreLockTest {
    private val screenA = Any()
    private val screenB = Any()

    @Test
    fun `a second holder is refused while the first holds it`() {
        val lock = RestoreLock()
        assertTrue(lock.tryAcquire(screenA))
        assertFalse(lock.tryAcquire(screenB))
        assertTrue(lock.held.value)
    }

    @Test
    fun `the holder may take it again`() {
        val lock = RestoreLock()
        assertTrue(lock.tryAcquire(screenA))
        assertTrue(lock.tryAcquire(screenA))
        assertTrue(lock.isHeldBy(screenA))
    }

    @Test
    fun `only the holder can release it`() {
        val lock = RestoreLock()
        lock.tryAcquire(screenA)
        lock.release(screenB)
        assertTrue(lock.isHeldBy(screenA))
        assertFalse(lock.tryAcquire(screenB))

        lock.release(screenA)
        assertFalse(lock.held.value)
        assertTrue(lock.tryAcquire(screenB))
    }

    @Test
    fun `withLock waits for the holder, then runs and releases`() = runBlocking {
        withTimeout(10_000) {
            val lock = RestoreLock()
            val resume = Any()
            lock.tryAcquire(screenA)
            val events = mutableListOf<String>()

            val waiting = async(Dispatchers.Default) { lock.withLock(resume) { synchronized(events) { events += "resume ran" } } }
            delay(200)
            assertFalse(waiting.isCompleted)
            synchronized(events) { events += "screen released" }
            lock.release(screenA)
            waiting.await()

            assertEquals(listOf("screen released", "resume ran"), events)
            assertFalse(lock.held.value)
        }
    }
}
