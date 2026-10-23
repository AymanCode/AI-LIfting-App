package com.ayman.ecolift.data

import com.ayman.ecolift.data.room.CycleSlot as RoomCycleSlot

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleSlotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<RoomCycleSlot>)

    @Query("SELECT * FROM cycle_slot ORDER BY orderIndex ASC, id ASC")
    fun observeAll(): Flow<List<RoomCycleSlot>>

    @Query("SELECT * FROM cycle_slot ORDER BY orderIndex ASC, id ASC")
    suspend fun getAll(): List<RoomCycleSlot>

    @Upsert
    suspend fun upsert(slot: RoomCycleSlot): Long

    @Query("DELETE FROM cycle_slot WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM cycle_slot")
    suspend fun deleteAll()

    @Query("SELECT * FROM cycle_slot WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoomCycleSlot?

    @Query("UPDATE cycle_slot SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE cycle_slot SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrder(id: Long, orderIndex: Int)

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM cycle_slot")
    suspend fun getMaxOrderIndex(): Int

    @Transaction
    suspend fun applyOrder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { i, id -> updateOrder(id, i) }
    }
}
