package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ayman.ecolift.data.room.WorkoutDayState

@Dao
interface WorkoutDayStateDao {
    @Query("SELECT revision FROM workout_day_state WHERE date = :date")
    suspend fun revision(date: String): Long?

    @Query("SELECT * FROM workout_day_state ORDER BY date")
    suspend fun getAll(): List<WorkoutDayState>

    @Upsert
    suspend fun upsert(row: WorkoutDayState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<WorkoutDayState>)
}
