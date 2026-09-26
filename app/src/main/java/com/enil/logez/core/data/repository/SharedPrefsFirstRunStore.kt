package com.enil.logez.core.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.FirstRunStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [FirstRunStore] in the `logez_ui_flags` SharedPreferences file, beside the notification prompt's
 * "declined" flag. Like that flag, it is one-off UI memory rather than a preference: it is not part
 * of the settings DataStore, so a zip backup never carries it and a restore from Settings never
 * changes it. Android 12+ device transfer copies it together with the data it describes.
 */
// UseKtx: the core-ktx `edit { }` helper discards commit()'s result, and markDone must report it.
@SuppressLint("UseKtx")
@Singleton
class SharedPrefsFirstRunStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : FirstRunStore {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun isDone(): Boolean = prefs.contains(KEY_DONE_AT)

    override fun path(): FirstRunPath? = FirstRunPath.fromStoredName(prefs.getString(KEY_PATH, null))

    override suspend fun markDone(path: FirstRunPath, atEpochMillis: Long): Boolean = withContext(Dispatchers.IO) {
        prefs.edit()
            .putLong(KEY_DONE_AT, atEpochMillis)
            .putString(KEY_PATH, path.storedName)
            .commit()
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(KEY_DONE_AT).remove(KEY_PATH).commit()
        }
    }

    companion object {
        /** Shared with the notification prompt's memory in `WorkoutSessionLauncher.kt`. */
        const val PREFS_FILE = "logez_ui_flags"
        const val KEY_DONE_AT = "first_run_done_at"
        const val KEY_PATH = "first_run_path"
    }
}
