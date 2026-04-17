package com.ayman.ecolift.agent.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_log")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: String,
    val timestamp: Long,
    val serializedPatches: String,   // forward patches as JSON
    val serializedInverse: String,   // inverse patches for undo as JSON
    val userConfirmed: Boolean,
    val isUndo: Boolean = false
)
