package com.enil.logez.core.data.media

import android.net.Uri

/**
 * PHASE2_PLAN.md §9.1: custom-exercise images live at `filesDir/exercise_media/<uuid>.jpg`,
 * referenced by `exercises.mediaPath` as a path RELATIVE to `filesDir` (never absolute — so
 * backup/restore and app moves never break). An interface (not a concrete class) so ViewModel
 * tests can supply a fake instead of a real Context, matching the project's established
 * "fakes, not mocks" convention.
 */
interface ExerciseMediaStore {
    /** Copies the picked image into app-private storage; returns the relative path to store on the exercise, or null on failure. */
    suspend fun copyToAppStorage(uri: Uri): String?
}
