package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val canonicalName: String,
    var aliases: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
