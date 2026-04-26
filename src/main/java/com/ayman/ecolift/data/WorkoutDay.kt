package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "workout_day")
@Serializable
data class WorkoutDay(
    @PrimaryKey val date: String,
    val cycleSlotType: Int? = null, // Deprecated, use cycleSlotId
    val cycleSlotOccurrence: Int? = null,
    val alternativeForDate: String? = null, // If this is a swap, which date was it originally for?
    val cycleSlotId: Long? = null
)
