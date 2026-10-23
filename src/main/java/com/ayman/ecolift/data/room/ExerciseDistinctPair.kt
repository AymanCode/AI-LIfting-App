package com.ayman.ecolift.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "exercise_distinct_pair",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseIdA"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseIdB"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exerciseIdA", "exerciseIdB"], unique = true),
        Index("exerciseIdB"),
    ],
)
@Serializable
data class ExerciseDistinctPair(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseIdA: Long,
    val exerciseIdB: Long,
)
