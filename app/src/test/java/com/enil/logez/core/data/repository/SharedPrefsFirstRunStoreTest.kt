package com.enil.logez.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.domain.repository.FirstRunPath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsFirstRunStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val uiFlags get() = context.getSharedPreferences("logez_ui_flags", Context.MODE_PRIVATE)

    @Before
    fun clearFlags() {
        uiFlags.edit().clear().commit()
    }

    @Test
    fun `a fresh install has not passed first run and has no path`() {
        val store = SharedPrefsFirstRunStore(context)
        assertFalse(store.isDone())
        assertNull(store.path())
    }

    @Test
    fun `markDone is seen by a new instance, in the logez_ui_flags file`() = runTest {
        assertTrue(SharedPrefsFirstRunStore(context).markDone(FirstRunPath.SETUP, 1_767_603_600_000L))

        val reopened = SharedPrefsFirstRunStore(context)
        assertTrue(reopened.isDone())
        assertEquals(FirstRunPath.SETUP, reopened.path())
        assertEquals(1_767_603_600_000L, uiFlags.getLong("first_run_done_at", -1L))
        assertEquals("setup", uiFlags.getString("first_run_path", null))
    }

    @Test
    fun `each path is stored under its fixed name`() = runTest {
        val store = SharedPrefsFirstRunStore(context)
        store.markDone(FirstRunPath.RESTORE, 1L)
        assertEquals("restore", uiFlags.getString("first_run_path", null))
        store.markDone(FirstRunPath.EXISTING, 2L)
        assertEquals("existing", uiFlags.getString("first_run_path", null))
        assertEquals(FirstRunPath.EXISTING, store.path())
    }

    @Test
    fun `an unknown stored path reads as null and is left in place`() {
        uiFlags.edit().putLong("first_run_done_at", 5L).putString("first_run_path", "cloud").commit()
        val store = SharedPrefsFirstRunStore(context)

        assertTrue(store.isDone())
        assertNull(store.path())
        assertEquals("cloud", uiFlags.getString("first_run_path", null))
    }

    @Test
    fun `clear removes only the first-run keys, not the notification decline`() = runTest {
        uiFlags.edit().putBoolean("notification_prompt_declined", true).commit()
        val store = SharedPrefsFirstRunStore(context)
        store.markDone(FirstRunPath.SETUP, 9L)

        store.clear()

        assertFalse(store.isDone())
        assertNull(store.path())
        assertFalse(uiFlags.contains("first_run_done_at"))
        assertFalse(uiFlags.contains("first_run_path"))
        assertTrue(uiFlags.getBoolean("notification_prompt_declined", false))
    }
}
