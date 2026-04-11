package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: Exercise): Long

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun getByExactName(name: String): Exercise?

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: Long): Exercise?
}
