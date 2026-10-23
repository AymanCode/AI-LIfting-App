package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.web.supabase.PendingReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPendingReviewRepository(
    private val store: RoomStore,
) : PendingReviewRepository {
    override val unresolved: Flow<List<PendingReview>> =
        store.reviews.map { rows -> rows.filterNot { it.resolved } }

    override suspend fun add(rawInput: String, dateLogged: String) {
        store.write {
            insertReview(PendingReview(rawInput = rawInput, dateLogged = dateLogged))
        }
    }

    override suspend fun markResolved(id: Long) {
        store.write { markReviewResolved(id) }
    }
}
