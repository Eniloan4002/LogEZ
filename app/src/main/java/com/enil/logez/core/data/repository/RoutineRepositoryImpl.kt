package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.RoutineDao
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.repository.RoutineRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class RoutineRepositoryImpl @Inject constructor(
    private val dao: RoutineDao,
) : RoutineRepository {
    override fun observeFolders(): Flow<List<RoutineFolderEntity>> = dao.observeFolders()
    override suspend fun getFolderById(id: String) = dao.getFolderById(id)
    override suspend fun upsertFolder(folder: RoutineFolderEntity) = dao.upsertFolder(folder)
    override suspend fun deleteFolder(folder: RoutineFolderEntity) = dao.deleteFolder(folder)

    override fun observeAllRoutines(): Flow<List<RoutineEntity>> = dao.observeAllRoutines()
    override fun observeRoutinesInFolder(folderId: String?): Flow<List<RoutineEntity>> = dao.observeRoutinesInFolder(folderId)
    override fun observeRoutineById(id: String): Flow<RoutineEntity?> = dao.observeRoutineById(id)
    override suspend fun getRoutineById(id: String) = dao.getRoutineById(id)
    override suspend fun deleteRoutineById(id: String) = dao.deleteRoutineById(id)

    override fun observeExercisesForRoutine(routineId: String): Flow<List<RoutineExerciseEntity>> =
        dao.observeExercisesForRoutine(routineId)

    override suspend fun getSetsForRoutineExercise(routineExerciseId: String): List<RoutineSetEntity> =
        dao.getSetsForRoutineExercise(routineExerciseId)

    override suspend fun insertFullRoutine(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    ) = dao.insertFullRoutine(routine, exercises, sets)
}
