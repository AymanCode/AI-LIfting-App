package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

const val UNCLASSIFIED_MUSCLE_GROUPS = ""
const val LEGACY_DEFAULT_MUSCLE_GROUPS = "CHEST · TRICEPS"

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"], unique = true)],
)
@Serializable
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroups: String = UNCLASSIFIED_MUSCLE_GROUPS,
    val isBodyweight: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
