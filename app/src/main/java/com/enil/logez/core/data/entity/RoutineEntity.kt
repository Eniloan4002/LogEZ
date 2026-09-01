package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enil.logez.core.domain.model.WorkoutStructure

/**
 * A saved routine template (PHASE2_PLAN.md §3.2). `folderId = null` means the implicit
 * "My Routines" bucket; deleting a folder SETs NULL rather than cascading, per spine.
 */
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = RoutineFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folder_id")],
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    val name: String,
    val notes: String?,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** M11 circuits: structure discriminator, immutable after creation. Defaulted so existing
     * named-arg construction keeps compiling; `defaultValue` must match `MIGRATION_4_5`'s
     * `DEFAULT 'REGULAR'` exactly or Room's post-migration validation rejects the live table
     * (the `debug13.9` lesson — see [ExerciseEntity.muscleHeads]). */
    @ColumnInfo(name = "structure", defaultValue = "'REGULAR'") val structure: WorkoutStructure = WorkoutStructure.REGULAR,
)
