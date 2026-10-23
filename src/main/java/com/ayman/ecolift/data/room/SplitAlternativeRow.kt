package com.ayman.ecolift.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "split_alternative",
    primaryKeys = ["splitId", "sourceId", "alternativeId"],
    foreignKeys = [
        ForeignKey(
            entity = CycleSlot::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["alternativeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index("alternativeId"),
        Index("splitId"),
    ],
)
@Serializable
data class SplitAlternativeRow(
    val splitId: Long,
    val sourceId: Long,
    val alternativeId: Long,
    val replacesSlot: Boolean,
    val createdAt: Long,
)
