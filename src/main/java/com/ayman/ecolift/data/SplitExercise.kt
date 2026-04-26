package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "split_exercise",
    foreignKeys = [
        ForeignKey(
            entity = CycleSlot::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("splitId"),
        Index("exerciseId"),
        Index(value = ["splitId", "exerciseId"], unique = true),
    ],
)
@Serializable
data class SplitExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val exerciseId: Long,
    val orderIndex: Int = 0,
)
