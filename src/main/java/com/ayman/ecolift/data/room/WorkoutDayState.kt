package com.ayman.ecolift.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "workout_day_state")
@Serializable
data class WorkoutDayState(
    @PrimaryKey val date: String,
    val revision: Long,
)
