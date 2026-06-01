package com.ayman.ecolift.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "archived_cycle")
@Serializable
data class ArchivedCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    val endDate: String,
    val splitCount: Int,
    val totalVolumeLbs: Long,
    val totalSessions: Int,
    val archivedAt: Long,
    val schemaVersion: Int,
    val snapshotJson: String,
)
