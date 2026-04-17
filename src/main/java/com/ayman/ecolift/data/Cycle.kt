package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycle")
data class Cycle(
    @PrimaryKey val id: Int = 1,
    val numTypes: Int = 3,
    val isActive: Boolean = false,
    val nextSessionType: Int? = null,
)
