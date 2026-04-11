package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {
    @Insert
    suspend fun insert(set: WorkoutSet): Long

    @Update
    suspend fun update(set: WorkoutSet)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM workout_set ORDER BY date ASC, exerciseId ASC, setNumber ASC")
    fun observeAll(): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_set WHERE date = :date ORDER BY exerciseId ASC, setNumber ASC")
    fun observeForDate(date: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_set WHERE date = :date ORDER BY exerciseId ASC, setNumber ASC")
    suspend fun getForDate(date: String): List<WorkoutSet>

    @Query(
        """
        SELECT * FROM workout_set
        WHERE date = :date AND exerciseId = :exerciseId
        ORDER BY setNumber ASC
        """
    )
    suspend fun getForDateAndExercise(date: String, exerciseId: Long): List<WorkoutSet>

    @Query(
        """
        SELECT * FROM workout_set
        WHERE date = :date AND exerciseId = :exerciseId
        ORDER BY setNumber DESC
        LIMIT 1
        """
    )
    suspend fun getLastForDateAndExercise(date: String, exerciseId: Long): WorkoutSet?

    @Query(
        """
        SELECT * FROM workout_set
        WHERE exerciseId = :exerciseId AND date < :beforeDate
        ORDER BY date DESC, setNumber DESC
        LIMIT 1
        """
    )
    suspend fun getMostRecentBeforeDate(exerciseId: Long, beforeDate: String): WorkoutSet?

    @Query(
        """
        SELECT * FROM workout_set
        WHERE exerciseId = :exerciseId AND date < :beforeDate
        ORDER BY date DESC, setNumber ASC
        """
    )
    suspend fun getHistoryBeforeDate(exerciseId: Long, beforeDate: String): List<WorkoutSet>

    @Query("SELECT * FROM workout_set WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkoutSet?
}
