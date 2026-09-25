package com.enil.logez.core.data.backup

import android.content.Context
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.dao.BackupDao
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.TransactionRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings > Data > "Delete all data" (Play-readiness audit, 2026-09-25). Until this existed the
 * only way to erase everything was Android's own "Clear storage" or uninstalling, and the privacy
 * policy now names this action as the in-app deletion route, so it must actually leave nothing.
 *
 * Mirrors the first half of [BackupRestorer] and nothing more:
 * 1. Every backed-up table is emptied in one transaction, children first ([BackupTables.WIPE_ORDER]),
 *    so a failure leaves the data exactly as it was.
 * 2. The photo directories and the cache (shared summary images, camera temp files) are deleted.
 * 3. Settings go back to their defaults.
 * 4. The seed marker is cleared and the exercise library re-seeded, because the wipe removed the
 *    seeded exercises along with the custom ones and the app is unusable without them.
 * 5. The widget is repainted so it stops showing the erased week.
 *
 * The caller refuses to run while a workout is in progress, as restore does: an active session
 * holds a foreground service and in-memory state pointing at rows this would delete. Files the
 * user already exported, or images saved to Pictures, live outside the app and are not touched.
 */
@Singleton
class LocalDataEraser @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val transactionRunner: TransactionRunner,
    private val settingsRepository: SettingsRepository,
    private val seedManager: SeedManager,
    private val widgetRefresher: WidgetRefresher,
    private val logger: AppLogger,
) {
    suspend fun eraseEverything() {
        transactionRunner.runInTransaction {
            BackupTables.WIPE_ORDER.forEach { backupDao.wipe(it) }
        }

        withContext(Dispatchers.IO) {
            deleteMediaAndCache(context.filesDir, context.cacheDir)
        }

        settingsRepository.replaceAll(UserSettings())

        seedManager.resetSeedVersion()
        runCatching { seedManager.seedIfNeeded() }
            .onFailure { logger.e(TAG, "Re-seeding after deleting all data failed", it) }

        widgetRefresher.refresh()
    }

    private companion object {
        const val TAG = "LocalDataEraser"
    }
}

/** Deletes both photo directories and everything in the cache. Split out so it is testable on plain files. */
internal fun deleteMediaAndCache(filesDir: File, cacheDir: File) {
    listOf(BackupWriter.PROGRESS_PHOTOS_DIR, BackupWriter.EXERCISE_MEDIA_DIR).forEach { name ->
        File(filesDir, name).deleteRecursively()
    }
    cacheDir.listFiles()?.forEach { it.deleteRecursively() }
}
