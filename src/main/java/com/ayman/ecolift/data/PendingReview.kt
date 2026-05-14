package com.ayman.ecolift.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "pending_review")
@Serializable
@Immutable
data class PendingReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawInput: String,
    val dateLogged: String,
    val resolved: Boolean = false,
)
