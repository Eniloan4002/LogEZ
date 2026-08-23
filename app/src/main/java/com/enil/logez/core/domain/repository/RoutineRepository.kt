package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import kotlinx.coroutines.flow.Flow

/**
 * M1 exposes CRUD + relationship reads only; the routine builder's diff-based structural update
 * (only touching what actually changed) is M3 work, added to this interface alongside the
 * builder UI it serves rather than guessed at now.
 */
interface RoutineRepository {
    fun observeFolders(): Flow<List<RoutineFolderEntity>>
    suspend fun getFolderById(id: String): RoutineFolderEntity?
    suspend fun upsertFolder(folder: RoutineFolderEntity)
    suspend fun deleteFolder(folder: RoutineFolderEntity)

    fun observeAllRoutines(): Flow<List<RoutineEntity>>
    fun observeRoutinesInFolder(folderId: String?): Flow<List<RoutineEntity>>
    fun observeRoutineById(id: String): Flow<RoutineEntity?>
    suspend fun getRoutineById(id: String): RoutineEntity?
    suspend fun deleteRoutineById(id: String)

    fun observeExercisesForRoutine(routineId: String): Flow<List<RoutineExerciseEntity>>
    suspend fun getSetsForRoutineExercise(routineExerciseId: String): List<RoutineSetEntity>

    suspend fun insertFullRoutine(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    )
}
