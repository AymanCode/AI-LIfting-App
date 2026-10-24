package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.PendingReview
import kotlinx.coroutines.flow.Flow

interface PendingReviewRepository {
    val unresolved: Flow<List<PendingReview>>
    suspend fun add(rawInput: String, dateLogged: String)
    suspend fun markResolved(id: Long)
}
