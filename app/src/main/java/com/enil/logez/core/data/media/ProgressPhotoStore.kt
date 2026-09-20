package com.enil.logez.core.data.media

import android.net.Uri

/**
 * PHASE2_PLAN.md §3.2, §9.1: progress photos live at `filesDir/progress_photos/<uuid>.jpg`,
 * referenced by `progress_photos.filePath` as a path RELATIVE to `filesDir` (never absolute — so
 * backup/restore and app moves never break). An interface (not a concrete class) so ViewModel
 * tests can supply a fake instead of a real Context, mirroring [ExerciseMediaStore]'s own reason
 * for existing as a seam.
 */
interface ProgressPhotoStore {
    /** Copies the captured/picked image into app-private storage; returns the relative path to store on the photo row, or null on failure. */
    suspend fun copyToAppStorage(uri: Uri): String?
}
