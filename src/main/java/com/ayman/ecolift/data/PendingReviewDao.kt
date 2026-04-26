package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingReviewDao {
    @Insert
    suspend fun insert(review: PendingReview): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<PendingReview>)

    @Query("SELECT * FROM pending_review ORDER BY dateLogged DESC, id DESC")
    suspend fun getAll(): List<PendingReview>

    @Query("SELECT * FROM pending_review WHERE resolved = 0 ORDER BY dateLogged DESC, id DESC")
    fun observeUnresolved(): Flow<List<PendingReview>>

    @Query("UPDATE pending_review SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)
}
