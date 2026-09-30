package com.enil.logez.feature.onboarding

import com.enil.logez.R
import com.enil.logez.core.data.backup.BackupManifest
import com.enil.logez.core.data.backup.StagedBackup
import com.enil.logez.feature.settings.DataJob
import com.enil.logez.feature.settings.RestoreOutcome
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What setup shows for each restore job, and the confirm's "Made on" date (first-run plan, O1e). */
class SetupRestoreUiTest {
    private val staged = StagedBackup(BackupManifest(exportedAtEpochMillis = 1_790_215_200_000L))

    @Test
    fun `idle shows nothing and leaves the buttons on`() {
        assertEquals(SetupRestoreUi(), setupRestoreUi(DataJob.Idle))
    }

    @Test
    fun `reading and restoring show their label and turn the buttons off`() {
        assertEquals(
            SetupRestoreUi(status = SetupRestoreStatus.Working(R.string.data_restore_reading), busy = true),
            setupRestoreUi(DataJob.Working(R.string.data_restore_reading)),
        )
        assertEquals(
            SetupRestoreUi(status = SetupRestoreStatus.Working(R.string.data_restore_restoring), busy = true),
            setupRestoreUi(DataJob.Working(R.string.data_restore_restoring)),
        )
    }

    @Test
    fun `a checked backup asks for the confirm and leaves the buttons drawn on under it`() {
        // The dialog is modal; the mockup (restore-confirm.png) shows Continue lime under the scrim.
        assertEquals(
            SetupRestoreUi(confirm = staged),
            setupRestoreUi(DataJob.ConfirmRestore(staged)),
        )
    }

    @Test
    fun `a failure shows its message, and the buttons stay off only while the gate re-checks a confirmed one`() {
        assertEquals(
            SetupRestoreUi(status = SetupRestoreStatus.Error(R.string.data_restore_too_new)),
            setupRestoreUi(DataJob.Failed(R.string.data_restore_too_new)),
        )
        assertEquals(
            SetupRestoreUi(status = SetupRestoreStatus.Error(R.string.data_restore_incomplete), busy = true),
            setupRestoreUi(DataJob.Failed(R.string.data_restore_incomplete, afterConfirm = true)),
        )
    }

    @Test
    fun `a finished restore keeps the buttons off until the gate opens the app`() {
        assertEquals(
            SetupRestoreUi(busy = true),
            setupRestoreUi(DataJob.Done(R.string.data_restore_done, restore = RestoreOutcome(0, backupHadSettings = true))),
        )
        assertEquals(SetupRestoreUi(), setupRestoreUi(DataJob.Done(R.string.data_export_done)))
    }

    @Test
    fun `the backup's date is the day in the zone it was made in`() {
        // 17:00 UTC on 24 Sep 2026 is already 25 Sep in Manila.
        val manifest = BackupManifest(exportedAtEpochMillis = 1_790_269_200_000L, exportedAtZoneId = "Asia/Manila")

        assertEquals("25 Sep 2026", backupMadeOnDate(manifest, Locale.US))
    }

    @Test
    fun `a backup with no zone, or one this device doesn't know, uses the device's zone`() {
        val before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals(
                "24 Sep 2026",
                backupMadeOnDate(BackupManifest(exportedAtEpochMillis = 1_790_269_200_000L, exportedAtZoneId = ""), Locale.US),
            )
            assertEquals(
                "24 Sep 2026",
                backupMadeOnDate(BackupManifest(exportedAtEpochMillis = 1_790_269_200_000L, exportedAtZoneId = "Mars/Olympus"), Locale.US),
            )
        } finally {
            TimeZone.setDefault(before)
        }
    }

    @Test
    fun `a backup with no export time has no date`() {
        assertNull(backupMadeOnDate(BackupManifest(exportedAtEpochMillis = 0L), Locale.US))
    }
}
