package com.enil.logez.core.data.backup

import android.database.sqlite.SQLiteFullException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** First-run plan, F13: a full disk is not reported as "not a backup". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutOfSpaceTest {
    @Test
    fun `an IOException naming ENOSPC is out of space`() {
        assertTrue(isOutOfSpace(IOException("write failed: ENOSPC (No space left on device)")))
    }

    @Test
    fun `a full disk found further down the cause chain is out of space`() {
        val wrapped = IllegalStateException("staging failed", IOException("No space left on device"))
        assertTrue(isOutOfSpace(wrapped))
    }

    @Test
    fun `SQLite's full-database error is out of space`() {
        assertTrue(isOutOfSpace(SQLiteFullException("database or disk is full")))
    }

    @Test
    fun `other failures are not`() {
        assertFalse(isOutOfSpace(BackupReader.NotABackupException("This does not look like a LogEZ backup")))
        assertFalse(isOutOfSpace(IOException()))
    }
}
