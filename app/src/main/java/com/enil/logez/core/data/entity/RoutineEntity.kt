package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
)
