package com.enil.logez.core.data.media

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ExerciseMediaStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExerciseMediaStore {
    override suspend fun copyToAppStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "exercise_media").apply { mkdirs() }
        val relativePath = "exercise_media/${UUID.randomUUID()}.jpg"
        val destination = File(context.filesDir, relativePath)
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
        }.onFailure { return@withContext null }
        relativePath
    }

    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)
}
