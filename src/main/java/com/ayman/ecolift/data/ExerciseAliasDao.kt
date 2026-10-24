package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayman.ecolift.data.room.ExerciseAlias as RoomExerciseAlias

@Dao
interface ExerciseAliasDao {
    @Query("SELECT * FROM exercise_alias ORDER BY id")
    suspend fun getAll(): List<RoomExerciseAlias>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: RoomExerciseAlias): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<RoomExerciseAlias>)
}
