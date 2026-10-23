package com.ayman.ecolift.data

import com.ayman.ecolift.data.room.ExerciseDistinctPair as RoomExerciseDistinctPair

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExerciseDistinctPairDao {
    @Query("SELECT * FROM exercise_distinct_pair ORDER BY id")
    suspend fun getAll(): List<RoomExerciseDistinctPair>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: RoomExerciseDistinctPair): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<RoomExerciseDistinctPair>)
}
