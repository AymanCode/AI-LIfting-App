package com.ayman.ecolift.data

import com.ayman.ecolift.data.room.SplitExercise as RoomSplitExercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitExerciseDao {
    @Query("SELECT * FROM split_exercise WHERE splitId = :splitId ORDER BY orderIndex ASC")
    fun observeForSplit(splitId: Long): Flow<List<RoomSplitExercise>>

    @Query("SELECT * FROM split_exercise WHERE splitId = :splitId ORDER BY orderIndex ASC")
    suspend fun getForSplit(splitId: Long): List<RoomSplitExercise>

    @Query("SELECT * FROM split_exercise ORDER BY splitId, orderIndex")
    fun observeAll(): Flow<List<RoomSplitExercise>>

    @Query("SELECT * FROM split_exercise ORDER BY splitId, orderIndex")
    suspend fun getAll(): List<RoomSplitExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<RoomSplitExercise>)

    @Query("DELETE FROM split_exercise WHERE splitId = :splitId")
    suspend fun deleteForSplit(splitId: Long)

    @Query("DELETE FROM split_exercise")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceForSplit(splitId: Long, exerciseIds: List<Long>) {
        deleteForSplit(splitId)
        if (exerciseIds.isEmpty()) return
        insertAll(exerciseIds.mapIndexed { i, exId ->
            RoomSplitExercise(splitId = splitId, exerciseId = exId, orderIndex = i)
        })
    }
}
