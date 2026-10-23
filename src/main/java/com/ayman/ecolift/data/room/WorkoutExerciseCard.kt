package com.ayman.ecolift.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "workout_exercise_card",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["date", "exerciseId"], unique = true),
        Index(value = ["date", "position"], unique = true),
        Index("exerciseId"),
    ],
)
@Serializable
data class WorkoutExerciseCard(
    @PrimaryKey val id: String,
    val date: String,
    val exerciseId: Long,
    val position: Int,
    val displayMode: String,
    val origin: String,
    val sourcePlanId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val rowRevision: Long = 1,
    @ColumnInfo(defaultValue = "''")
    val createdAt: String = "",
    @ColumnInfo(defaultValue = "''")
    val updatedAt: String = "",
)
