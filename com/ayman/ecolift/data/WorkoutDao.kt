package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: Workout): Long

    @Query("UPDATE workout SET endedAt = :endedAt WHERE id = :id")
    suspend fun updateEndedAt(id: Long, endedAt: Long)

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getById(id: Long): Workout?

    @Query("SELECT * FROM workout WHERE endedAt IS NULL AND dateEpochDay = :dateEpochDay")
    suspend fun getOpenWorkoutForDay(dateEpochDay: Long): Workout?

    @Query("SELECT * FROM workout ORDER BY dateEpochDay DESC")
    fun getAllOrderedByDateDesc(): Flow<List<Workout>>
}
