package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_day")
data class WorkoutDay(
    @PrimaryKey val date: String,
    val cycleSlotType: Int? = null, // Deprecated, use cycleSlotId
    val cycleSlotOccurrence: Int? = null,
    val alternativeForDate: String? = null, // If this is a swap, which date was it originally for?
    val cycleSlotId: Long? = null
)
