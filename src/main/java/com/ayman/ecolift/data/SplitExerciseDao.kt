package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitExerciseDao {
    @Query("SELECT * FROM split_exercise WHERE splitId = :splitId ORDER BY orderIndex ASC")
    fun observeForSplit(splitId: Long): Flow<List<SplitExercise>>

    @Query("SELECT * FROM split_exercise WHERE splitId = :splitId ORDER BY orderIndex ASC")
    suspend fun getForSplit(splitId: Long): List<SplitExercise>

    @Query("SELECT * FROM split_exercise ORDER BY splitId, orderIndex")
    fun observeAll(): Flow<List<SplitExercise>>

    @Query("SELECT * FROM split_exercise ORDER BY splitId, orderIndex")
    suspend fun getAll(): List<SplitExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SplitExercise>)

    @Query("DELETE FROM split_exercise WHERE splitId = :splitId")
    suspend fun deleteForSplit(splitId: Long)

    @Transaction
    suspend fun replaceForSplit(splitId: Long, exerciseIds: List<Long>) {
        deleteForSplit(splitId)
        if (exerciseIds.isEmpty()) return
        insertAll(exerciseIds.mapIndexed { i, exId ->
            SplitExercise(splitId = splitId, exerciseId = exId, orderIndex = i)
        })
    }
}
