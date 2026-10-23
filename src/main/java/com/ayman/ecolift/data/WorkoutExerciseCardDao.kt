package com.ayman.ecolift.data

import com.ayman.ecolift.data.room.WorkoutExerciseCard as RoomWorkoutExerciseCard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WorkoutExerciseCardDao {
    @Query("SELECT * FROM workout_exercise_card ORDER BY date, position")
    suspend fun getAll(): List<RoomWorkoutExerciseCard>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: RoomWorkoutExerciseCard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<RoomWorkoutExerciseCard>)

    @Update
    suspend fun update(card: RoomWorkoutExerciseCard)

    @Query("DELETE FROM workout_exercise_card WHERE id = :id")
    suspend fun deleteById(id: String)
}
