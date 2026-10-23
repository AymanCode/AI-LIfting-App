package com.ayman.ecolift.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "exercise_swap_receipt")
@Serializable
data class ExerciseSwapReceiptRow(
    @PrimaryKey val requestId: String,
    val date: String,
    val sourceId: Long,
    val targetId: Long,
    val splitId: Long?,
    val beforeSourceSetsJson: String,
    val beforeCardsJson: String,
    val targetExisted: Boolean,
    val afterRevision: Long,
    val undone: Boolean,
)
