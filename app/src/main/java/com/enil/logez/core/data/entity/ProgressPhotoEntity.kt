package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One private progress photo (PHASE2_PLAN.md §3.2, §9.1). [filePath] is relative to `filesDir`
 * (`progress_photos/<uuid>.jpg`) so backup/restore and app moves never break absolute paths.
 * One per calendar [date], enforced in the repository (replace-with-confirm), not the schema.
 */
@Entity(tableName = "progress_photos", indices = [Index("date")])
data class ProgressPhotoEntity(
    @PrimaryKey val id: String,
    val date: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
