package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.ayman.ecolift.data.room.CardioSession as RoomCardioSession

@Dao
interface CardioSessionDao {
    @Insert
    suspend fun insert(session: RoomCardioSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<RoomCardioSession>)

    @Upsert
    suspend fun upsert(session: RoomCardioSession): Long

    @Update
    suspend fun update(session: RoomCardioSession)

    @Query("SELECT * FROM cardio_sessions ORDER BY date DESC, COALESCE(start_time, created_at) DESC, id DESC")
    fun observeAll(): Flow<List<RoomCardioSession>>

    @Query("SELECT * FROM cardio_sessions ORDER BY date DESC, COALESCE(start_time, created_at) DESC, id DESC")
    suspend fun getAll(): List<RoomCardioSession>

    @Query("SELECT * FROM cardio_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoomCardioSession?

    @Query("SELECT * FROM cardio_sessions WHERE hc_uid = :hcUid LIMIT 1")
    suspend fun getByHealthConnectUid(hcUid: String): RoomCardioSession?

    @Query("SELECT * FROM cardio_sessions WHERE date = :date ORDER BY COALESCE(start_time, created_at) DESC, id DESC")
    fun observeForDate(date: String): Flow<List<RoomCardioSession>>

    @Query("SELECT * FROM cardio_sessions WHERE date = :date ORDER BY COALESCE(start_time, created_at) DESC, id DESC")
    suspend fun getForDate(date: String): List<RoomCardioSession>

    @Query(
        """
        SELECT * FROM cardio_sessions
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC, COALESCE(start_time, created_at) ASC, id ASC
        """
    )
    suspend fun getInRange(startDate: String, endDate: String): List<RoomCardioSession>

    @Query(
        """
        SELECT COALESCE(SUM(calories), 0)
        FROM cardio_sessions
        WHERE date = :date AND calories IS NOT NULL
        """
    )
    fun observeCaloriesForDate(date: String): Flow<Int>

    @Query("DELETE FROM cardio_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
