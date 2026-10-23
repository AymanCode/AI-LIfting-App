package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayman.ecolift.data.room.ExerciseSwapReceiptRow

@Dao
interface ExerciseSwapReceiptDao {
    @Query("SELECT * FROM exercise_swap_receipt WHERE requestId = :requestId")
    suspend fun find(requestId: String): ExerciseSwapReceiptRow?

    @Query("SELECT * FROM exercise_swap_receipt")
    suspend fun getAll(): List<ExerciseSwapReceiptRow>

    @Insert
    suspend fun insert(row: ExerciseSwapReceiptRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExerciseSwapReceiptRow>)

    @Query("UPDATE exercise_swap_receipt SET undone = 1 WHERE requestId = :requestId")
    suspend fun markUndone(requestId: String)
}
