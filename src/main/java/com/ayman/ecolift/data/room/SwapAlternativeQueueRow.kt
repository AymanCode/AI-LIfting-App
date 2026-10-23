package com.ayman.ecolift.data.room

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Entity(
    tableName = "swap_alternative_queue",
    primaryKeys = ["splitId", "sourceId", "alternativeId"],
)
@Serializable
data class SwapAlternativeQueueRow(
    val splitId: Long,
    val sourceId: Long,
    val alternativeId: Long,
)
