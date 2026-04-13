package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleSlotDao {
    @Query("SELECT * FROM cycle_slot ORDER BY name ASC")
    fun observeAll(): Flow<List<CycleSlot>>

    @Query("SELECT * FROM cycle_slot")
    suspend fun getAll(): List<CycleSlot>

    @Upsert
    suspend fun upsert(slot: CycleSlot): Long

    @Query("DELETE FROM cycle_slot WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM cycle_slot WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CycleSlot?
}
