package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_day")
data class WorkoutDay(
    @PrimaryKey val date: String,
    val cycleSlotType: Int? = null,
    val cycleSlotOccurrence: Int? = null,
)
