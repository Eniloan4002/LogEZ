package com.enil.logez.core.data

import androidx.room.TypeConverter
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Explicit enum-as-string and JSON-list converters (PHASE2_PLAN.md §10.2 DAO-test requirement:
 * "TypeConverters (enum ↔ string, secondaryMuscleGroups JSON-list, LocalDate)"). Written out
 * explicitly rather than relying on Room's native enum support, so the wire format (`.name`,
 * never `.ordinal`) is unambiguous and independent of enum declaration order.
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromSetType(value: SetType): String = value.name

    @TypeConverter
    fun toSetType(value: String): SetType = SetType.valueOf(value)

    @TypeConverter
    fun fromExerciseType(value: ExerciseType): String = value.name

    @TypeConverter
    fun toExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)

    @TypeConverter
    fun fromMuscleGroup(value: MuscleGroup): String = value.name

    @TypeConverter
    fun toMuscleGroup(value: String): MuscleGroup = MuscleGroup.valueOf(value)

    @TypeConverter
    fun fromEquipment(value: Equipment): String = value.name

    @TypeConverter
    fun toEquipment(value: String): Equipment = Equipment.valueOf(value)

    @TypeConverter
    fun fromPrType(value: PrType): String = value.name

    @TypeConverter
    fun toPrType(value: String): PrType = PrType.valueOf(value)

    @TypeConverter
    fun fromWorkoutStatus(value: WorkoutStatus): String = value.name

    @TypeConverter
    fun toWorkoutStatus(value: String): WorkoutStatus = WorkoutStatus.valueOf(value)

    @TypeConverter
    fun fromGoalMetric(value: GoalMetric): String = value.name

    @TypeConverter
    fun toGoalMetric(value: String): GoalMetric = GoalMetric.valueOf(value)

    @TypeConverter
    fun fromGoalPeriod(value: GoalPeriod): String = value.name

    @TypeConverter
    fun toGoalPeriod(value: String): GoalPeriod = GoalPeriod.valueOf(value)

    @TypeConverter
    fun fromMuscleGroupList(value: List<MuscleGroup>): String =
        json.encodeToString(value.map { it.name })

    @TypeConverter
    fun toMuscleGroupList(value: String): List<MuscleGroup> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString<List<String>>(value).map { MuscleGroup.valueOf(it) }

    /** A checklist, not a single pick (M8e revision — Owner feedback) -- empty for every exercise that doesn't specify any. */
    @TypeConverter
    fun fromMuscleHeadList(value: List<MuscleHead>): String =
        json.encodeToString(value.map { it.name })

    @TypeConverter
    fun toMuscleHeadList(value: String): List<MuscleHead> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString<List<String>>(value).map { MuscleHead.valueOf(it) }
}
