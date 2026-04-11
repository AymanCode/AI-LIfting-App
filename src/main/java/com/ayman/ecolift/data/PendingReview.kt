package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_review")
data class PendingReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawInput: String,
    val dateLogged: String,
    val resolved: Boolean = false,
)
