package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TempSessionSwapDao {
    @Insert
    suspend fun insert(swap: TempSessionSwap): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(swaps: List<TempSessionSwap>)

    @Query("SELECT * FROM temp_session_swap ORDER BY createdAt DESC")
    suspend fun getAll(): List<TempSessionSwap>

    @Query(
        """
        SELECT * FROM temp_session_swap
        WHERE weekStartDate = :weekStartDate AND resolved = 0
        ORDER BY createdAt DESC
        """
    )
    fun observeActiveForWeek(weekStartDate: String): Flow<List<TempSessionSwap>>

    @Query(
        """
        SELECT * FROM temp_session_swap
        WHERE weekStartDate = :weekStartDate AND resolved = 0
        ORDER BY createdAt DESC
        """
    )
    suspend fun getActiveForWeek(weekStartDate: String): List<TempSessionSwap>

    @Query(
        """
        UPDATE temp_session_swap
        SET resolved = 1
        WHERE id = :id
        """
    )
    suspend fun markResolved(id: Long)
}
