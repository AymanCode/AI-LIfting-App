package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "temp_session_swap",
    indices = [
        Index("weekStartDate"),
        Index("sourceSlotType"),
        Index("targetSlotType"),
    ],
)
@Serializable
data class TempSessionSwap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartDate: String,
    val sourceSlotType: Int,
    val sourceExerciseId: Long,
    val targetSlotType: Int,
    val targetExerciseId: Long,
    val resolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
