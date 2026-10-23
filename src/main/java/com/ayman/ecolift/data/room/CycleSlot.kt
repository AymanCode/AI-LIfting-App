package com.ayman.ecolift.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "cycle_slot")
@Serializable
data class CycleSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val orderIndex: Int = 0,
    val archivedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val learned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val seedRevision: Int = 0,
    val anchorUpdatedAt: Long? = null,
    val suppressedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val colorIndex: Int = 0,
)
