package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("exerciseId"),
        Index("date"),
    ]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val date: String,
    val setNumber: Int,
    val weightLbs: Int? = null,
    val reps: Int? = null,
    val isBodyweight: Boolean = false,
    val completed: Boolean = false,
    val restTimeSeconds: Int? = null, // Track how long you rested BEFORE this set
)
