package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {
    @Insert
    suspend fun insert(set: WorkoutSet): Long

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM workout_set WHERE workoutId = :workoutId ORDER BY loggedAt ASC")
    fun getSetsForWorkout(workoutId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_set WHERE exerciseId = :exerciseId ORDER BY loggedAt ASC")
    suspend fun getSetsForExercise(exerciseId: Long): List<WorkoutSet>

    @Query("SELECT COUNT(*) FROM workout_set WHERE workoutId = :workoutId")
    suspend fun countForWorkout(workoutId: Long): Int
}
