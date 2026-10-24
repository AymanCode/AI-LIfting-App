package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.ayman.ecolift.data.room.Cycle as RoomCycle

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycle WHERE id = 1 LIMIT 1")
    fun observeCycle(): Flow<RoomCycle?>

    @Query("SELECT * FROM cycle WHERE id = 1 LIMIT 1")
    suspend fun getCycle(): RoomCycle?

    @Upsert
    suspend fun upsert(cycle: RoomCycle)
}
