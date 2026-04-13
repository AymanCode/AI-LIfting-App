package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycle_slot")
data class CycleSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
