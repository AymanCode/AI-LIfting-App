package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Query("SELECT * FROM exercise")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE canonicalName = :name")
    suspend fun getByExactCanonicalName(name: String): Exercise?

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercise ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentlyUsed(limit: Int): List<Exercise>
}
