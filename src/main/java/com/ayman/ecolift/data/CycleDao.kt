package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycle WHERE id = 1 LIMIT 1")
    fun observeCycle(): Flow<Cycle?>

    @Query("SELECT * FROM cycle WHERE id = 1 LIMIT 1")
    suspend fun getCycle(): Cycle?

    @Upsert
    suspend fun upsert(cycle: Cycle)
}
