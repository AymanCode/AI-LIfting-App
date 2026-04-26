package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"], unique = true)],
)
@Serializable
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroups: String = "CHEST · TRICEPS",
    val isBodyweight: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
