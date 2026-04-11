package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow

class PendingReviewRepository(private val db: AppDatabase) {
    val unresolved: Flow<List<PendingReview>> = db.pendingReviewDao().observeUnresolved()

    suspend fun add(rawInput: String, dateLogged: String) {
        db.pendingReviewDao().insert(
            PendingReview(
                rawInput = rawInput.trim(),
                dateLogged = dateLogged,
            )
        )
    }

    suspend fun markResolved(id: Long) {
        db.pendingReviewDao().markResolved(id)
    }
}
