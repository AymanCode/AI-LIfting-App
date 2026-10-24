package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ayman.ecolift.data.room.ArchivedCycle as RoomArchivedCycle

@Dao
interface ArchivedCycleDao {
    @Query("SELECT * FROM archived_cycle ORDER BY endDate DESC, id DESC")
    fun observeAll(): Flow<List<RoomArchivedCycle>>

    @Query("SELECT * FROM archived_cycle ORDER BY endDate DESC, id DESC")
    suspend fun getAll(): List<RoomArchivedCycle>

    @Query("SELECT * FROM archived_cycle WHERE id = :id")
    suspend fun getById(id: Long): RoomArchivedCycle?

    @Query(
        """
        SELECT COUNT(*) FROM archived_cycle
        WHERE startDate <= :end AND :start <= endDate
        """
    )
    suspend fun countOverlapping(start: String, end: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cycle: RoomArchivedCycle): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cycles: List<RoomArchivedCycle>)

    @Query("DELETE FROM archived_cycle WHERE id = :id")
    suspend fun deleteById(id: Long)
}
