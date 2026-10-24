package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayman.ecolift.data.room.SwapAlternativeQueueRow

@Dao
interface SwapAlternativeQueueDao {
    @Query("SELECT * FROM swap_alternative_queue ORDER BY rowid")
    suspend fun all(): List<SwapAlternativeQueueRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SwapAlternativeQueueRow>)

    @Query("DELETE FROM swap_alternative_queue")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM swap_alternative_queue WHERE splitId = :splitId AND sourceId = :sourceId AND alternativeId = :alternativeId",
    )
    suspend fun delete(splitId: Long, sourceId: Long, alternativeId: Long)
}
