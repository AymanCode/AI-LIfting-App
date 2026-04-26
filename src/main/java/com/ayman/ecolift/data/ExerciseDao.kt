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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun getByExactName(name: String): Exercise?

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Exercise>

    @Query(
        """
        SELECT * FROM exercise
        WHERE name LIKE :query || '%'
           OR name LIKE '% ' || :query || '%'
        ORDER BY name ASC
        LIMIT :limit
        """
    )
    suspend fun searchByName(query: String, limit: Int): List<Exercise>

    @Query("UPDATE exercise SET name = :newName WHERE id = :id")
    suspend fun updateName(id: Long, newName: String)

    @androidx.room.Upsert
    suspend fun upsert(exercise: Exercise): Long

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun deleteById(id: Long)

    @androidx.room.Transaction
    suspend fun deleteExerciseWithLogs(exerciseId: Long) {
        deleteById(exerciseId)
    }

    @Query("DELETE FROM workout_set")
    suspend fun deleteAllSets()

    @Query("DELETE FROM exercise")
    suspend fun deleteAllExercises()
}
