package com.enil.logez.core.data.seed

import kotlinx.serialization.Serializable

/** PHASE2_PLAN.md §7.6 seed asset format — `app/src/main/assets/seed/exercises_seed.json`. */
@Serializable
data class ExerciseSeedFile(
    val seedVersion: Int,
    val exercises: List<ExerciseSeedDto>,
)

@Serializable
data class ExerciseSeedDto(
    val id: String,
    val name: String,
    val exerciseType: String,
    val primaryMuscleGroup: String,
    val secondaryMuscleGroups: List<String> = emptyList(),
    val equipment: String,
    val instructions: List<String>,
    val isBodyweightVolumeEligible: Boolean = false,
)
