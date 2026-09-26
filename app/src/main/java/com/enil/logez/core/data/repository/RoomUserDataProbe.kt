package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.BackupDao
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.domain.repository.UserDataProbe
import javax.inject.Inject

/**
 * [UserDataProbe] over the Room tables, stopping at the first table that has a row.
 *
 * The exercise signal is the single statement [ExerciseDao.customCount], never
 * `count() - seedCount()`: on a fresh install the probe can run while the first 400-row seed
 * transaction is committing, and two separate SELECTs can land on either side of that commit
 * (WAL lets a reader see the old snapshot, then the new one). The difference would then read as
 * 400 custom exercises, and a fresh install would be taken for an existing one and never shown
 * setup. One statement always sees one snapshot, and seed rows are never custom in either.
 */
class RoomUserDataProbe @Inject constructor(
    private val backupDao: BackupDao,
    private val exerciseDao: ExerciseDao,
) : UserDataProbe {
    override suspend fun hasUserContent(): Boolean =
        // Unfiltered COUNT: an IN_PROGRESS workout counts, so setup can never meet recovery.
        backupDao.countWorkouts() > 0 ||
            backupDao.countRoutines() > 0 ||
            backupDao.countRoutineFolders() > 0 ||
            backupDao.countBodyMeasurements() > 0 ||
            backupDao.countProgressPhotos() > 0 ||
            backupDao.countGoals() > 0 ||
            exerciseDao.customCount() > 0
}
