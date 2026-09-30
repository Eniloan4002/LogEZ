package com.enil.logez.core.data.backup

/**
 * What [BackupReader.stage] found in an archive, beyond its manifest: enough for the confirm
 * dialog to say truthfully what a restore will and won't bring back (first-run plan, F4 and F5).
 *
 * @property unfinishedWorkoutCount IN_PROGRESS workouts in the backup. A restore leaves them out,
 *   because a restored session can't resume properly: its timer is stuck at 0:00 and a GPS one
 *   raises "Tracking was interrupted" spanning the days since the backup.
 * @property hasSettings false when the backup has no settings.json, or one that can't be parsed;
 *   the restore then keeps the settings this device has.
 * @property settingsTooNew the settings name a value this build doesn't know, such as a newer
 *   unit. Refused as "too new" rather than coerced to a default and re-saved.
 */
data class StagedBackup(
    val manifest: BackupManifest,
    val unfinishedWorkoutCount: Int = 0,
    val hasSettings: Boolean = true,
    val settingsTooNew: Boolean = false,
) {
    /** The workouts a restore brings back: the manifest counts unfinished ones too. */
    val completedWorkoutCount: Int get() = (manifest.workoutCount - unfinishedWorkoutCount).coerceAtLeast(0)

    fun compatibility(currentRoomVersion: Int): BackupManifest.Compatibility =
        if (settingsTooNew) BackupManifest.Compatibility.TooNew else manifest.compatibility(currentRoomVersion)
}
