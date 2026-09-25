package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** "Programs" are folders of routines (PHASE2_PLAN.md §3.2). New folders insert at index 0, so the newest sits on top. */
@Entity(tableName = "routine_folders")
data class RoutineFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
