package com.enil.logez.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.TipId
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

    @Test
    fun `a fresh install has seen no tip and has not re-enabled tips`() {
        val store = SharedPrefsFirstRunStore(context)
        assertFalse(store.isTipSeen(TipId.PICKER))
        assertFalse(store.isTipSeen(TipId.LOGGER))
        assertFalse(store.isTipSeen(TipId.BUILDER))
        assertFalse(store.tipsReenabled())
    }

    @Test
    fun `markTipSeen writes tip_seen_ plus the tip's fixed name, and a new instance sees it`() {
        SharedPrefsFirstRunStore(context).markTipSeen(TipId.LOGGER)

        assertTrue(uiFlags.getBoolean("tip_seen_logger", false))
        assertFalse(uiFlags.contains("tip_seen_picker"))
        val reopened = SharedPrefsFirstRunStore(context)
        assertTrue(reopened.isTipSeen(TipId.LOGGER))
        assertFalse(reopened.isTipSeen(TipId.PICKER))
    }

    @Test
    fun `the tip names on disk never change`() {
        assertEquals("picker", TipId.PICKER.storedName)
        assertEquals("logger", TipId.LOGGER.storedName)
        assertEquals("builder", TipId.BUILDER.storedName)
    }

    @Test
    fun `showTipsAgain clears every seen-tip key and sets tips_reenabled, and nothing else`() = runTest {
        uiFlags.edit()
            .putBoolean("notification_prompt_declined", true)
            .putBoolean("tip_seen_future_tip", true)
            .commit()
        val store = SharedPrefsFirstRunStore(context)
        store.markDone(FirstRunPath.EXISTING, 7L)
        store.markTipSeen(TipId.PICKER)
        store.markTipSeen(TipId.BUILDER)

        store.showTipsAgain()

        assertFalse(uiFlags.contains("tip_seen_picker"))
        assertFalse(uiFlags.contains("tip_seen_builder"))
        assertFalse(uiFlags.contains("tip_seen_future_tip"))
        assertTrue(uiFlags.getBoolean("tips_reenabled", false))
        assertTrue(store.tipsReenabled())
        assertEquals(7L, uiFlags.getLong("first_run_done_at", -1L))
        assertEquals("existing", uiFlags.getString("first_run_path", null))
        assertTrue(uiFlags.getBoolean("notification_prompt_declined", false))
    }

    @Test
    fun `clear leaves the tip keys, so Delete all data brings setup back but not the tips`() = runTest {
        val store = SharedPrefsFirstRunStore(context)
        store.markDone(FirstRunPath.SETUP, 3L)
        store.markTipSeen(TipId.LOGGER)
        store.showTipsAgain()
        store.markTipSeen(TipId.PICKER)

        store.clear()

        assertTrue(uiFlags.getBoolean("tip_seen_picker", false))
        assertTrue(uiFlags.getBoolean("tips_reenabled", false))
    }
}
