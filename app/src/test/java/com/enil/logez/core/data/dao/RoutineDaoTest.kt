package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteConstraintException

// Robolectric 4.13's max supported SDK is 34; this app targets 36 (Fiterval precedent — same pin).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutineDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.routineDao()
    private val exerciseDao get() = database.exerciseDao()

    private suspend fun anExercise(id: String = "ex-1") {
        exerciseDao.insertIgnore(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = "Bench Press (Barbell)",
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    primaryMuscleGroup = MuscleGroup.CHEST,
                    secondaryMuscleGroups = emptyList(),
                    equipment = Equipment.BARBELL,
                    instructions = "x",
                    mediaPath = null,
                    isCustom = false,
                    isBodyweightVolumeEligible = false,
                    isDeleted = false,
                    createdAt = 0,
                    updatedAt = 0,
                ),
            ),
        )
    }

    @Test
    fun `deleting a folder SETs NULL on its routines, not cascade`() = runTest {
        val folder = RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)
        dao.upsertFolder(folder)
        dao.upsertRoutine(RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))

        dao.deleteFolder(folder)

        val routine = dao.getRoutineById("r1")
        assertEquals("r1", routine?.id) // routine survives
        assertNull(routine?.folderId) // FK SET_NULL, not deleted
    }

    @Test
    fun `deleting a routine cascades to its exercises and sets`() = runTest {
        anExercise()
        dao.insertFullRoutine(
            routine = RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            exercises = listOf(
                RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                RoutineSetEntity(id = "rs1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 100.0, targetReps = 5, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null),
            ),
        )

        dao.deleteRoutineById("r1")

        assertTrue(dao.getExercisesForRoutine("r1").isEmpty())
        assertEquals(0, dao.getSetsForRoutineExercise("re1").size)
    }

    @Test
    fun `deleting a referenced exercise is rejected by the schema (FK RESTRICT)`() = runTest {
        anExercise()
        dao.insertFullRoutine(
            routine = RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            exercises = listOf(
                RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = emptyList(),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            rawDb.execSQL("DELETE FROM exercises WHERE id = 'ex-1'")
        }
    }

    @Test
    fun `folders and routines observe in orderIndex order`() = runTest {
        dao.upsertFolder(RoutineFolderEntity(id = "f2", name = "Second", orderIndex = 1, createdAt = 0, updatedAt = 0))
        dao.upsertFolder(RoutineFolderEntity(id = "f1", name = "First", orderIndex = 0, createdAt = 0, updatedAt = 0))

        val folders = dao.observeFolders().first()
        assertEquals(listOf("f1", "f2"), folders.map { it.id })
    }
}
