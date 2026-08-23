package com.enil.logez.core.data.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * PHASE2_PLAN.md §7.7/§7.8 — runtime JSON seeding, version-gated so first install and every
 * later library update share one code path. New IDs insert; existing seed IDs get a
 * correction-safe field update (name/muscles/equipment/instructions/eligibility/isDeleted) —
 * `exercise_type` is never touched by an update, and custom exercises (`is_custom = 1`) are
 * never touched at all (enforced by [ExerciseDao.updateSeedFields]'s own WHERE clause).
 *
 * NOTE: `exercises_seed.json` currently ships a small placeholder set (~18 exercises spanning
 * every ExerciseType/Equipment and the 4 bodyweight-eligible seeds), proving the pipeline end to
 * end. The frozen, Owner-approved 400-exercise library (§7.9) is the next sub-step — this file
 * only needs updating with the real content; nothing about the mechanism changes.
 */
@Singleton
class SeedManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lastAppliedSeedVersionKey = intPreferencesKey("lastAppliedSeedVersion")

    suspend fun seedIfNeeded() {
        val file = readSeedFile()
        val lastApplied = dataStore.data.first()[lastAppliedSeedVersionKey] ?: 0
        if (file.seedVersion <= lastApplied) return

        val now = System.currentTimeMillis()
        val entities = file.exercises.map { it.toEntity(now) }
        val insertResults = exerciseDao.insertIgnore(entities)

        entities.forEachIndexed { index, entity ->
            if (insertResults[index] == -1L) {
                exerciseDao.updateSeedFields(
                    id = entity.id,
                    name = entity.name,
                    primaryMuscleGroup = entity.primaryMuscleGroup,
                    secondaryMuscleGroups = entity.secondaryMuscleGroups,
                    equipment = entity.equipment,
                    instructions = entity.instructions,
                    isBodyweightVolumeEligible = entity.isBodyweightVolumeEligible,
                    isDeleted = false,
                    updatedAt = now,
                )
            }
        }

        dataStore.edit { it[lastAppliedSeedVersionKey] = file.seedVersion }
    }

    private fun readSeedFile(): ExerciseSeedFile =
        context.assets.open("seed/exercises_seed.json").use { stream ->
            json.decodeFromString(ExerciseSeedFile.serializer(), stream.readBytes().decodeToString())
        }

    private fun ExerciseSeedDto.toEntity(now: Long) = ExerciseEntity(
        id = id,
        name = name,
        exerciseType = ExerciseType.valueOf(exerciseType),
        primaryMuscleGroup = MuscleGroup.valueOf(primaryMuscleGroup),
        secondaryMuscleGroups = secondaryMuscleGroups.map { MuscleGroup.valueOf(it) },
        equipment = Equipment.valueOf(equipment),
        instructions = instructions.joinToString("\n"),
        mediaPath = null,
        isCustom = false,
        isBodyweightVolumeEligible = isBodyweightVolumeEligible,
        isDeleted = false,
        createdAt = now,
        updatedAt = now,
    )
}
