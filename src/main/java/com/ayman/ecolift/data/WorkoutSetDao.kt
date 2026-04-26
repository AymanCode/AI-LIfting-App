package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {
    @Insert
    suspend fun insert(set: WorkoutSet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<WorkoutSet>)

    @androidx.room.Upsert
    suspend fun upsert(set: WorkoutSet): Long

    @Update
    suspend fun update(set: WorkoutSet)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT date FROM workout_set ORDER BY date DESC")
    fun observeAllDistinctDates(): Flow<List<String>>

    @Query("SELECT * FROM workout_set")
    suspend fun getAll(): List<WorkoutSet>

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

    @Query(
        """
        SELECT 
            e.id as exerciseId, 
            e.name as exerciseName,
            e.isBodyweight as isBodyweight,
            COUNT(DISTINCT s.date) as sessionCount,
            MAX(s.date) as lastSessionDate
        FROM exercise e
        JOIN workout_set s ON e.id = s.exerciseId
        GROUP BY e.id
        """
    )
    fun observeExerciseProgressSummaries(): Flow<List<ExerciseProgressSummary>>

    @Query(
        """
        SELECT date, CAST(ROUND(SUM(COALESCE(weightLbs, 0) * COALESCE(reps, 0)) / 10.0) AS INTEGER) as volume
        FROM workout_set
        WHERE exerciseId = :exerciseId
        GROUP BY date
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    suspend fun getVolumeHistory(exerciseId: Long, limit: Int): List<DateVolume>

    @Query(
        """
        SELECT exerciseId, CAST(ROUND(SUM(COALESCE(weightLbs, 0) * COALESCE(reps, 0)) / 10.0) AS INTEGER) as volume
        FROM workout_set
        WHERE date >= :sinceDate
        GROUP BY exerciseId
        """
    )
    suspend fun getVolumesSince(sinceDate: String): List<ExerciseVolume>

    @Query("SELECT * FROM workout_set WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkoutSet?

    @Query("UPDATE workout_set SET date = :newDate WHERE date = :oldDate")
    suspend fun updateDate(oldDate: String, newDate: String)

    @Query("SELECT MAX(date) FROM workout_set WHERE date < :beforeDate")
    suspend fun getLastSessionDate(beforeDate: String): String?

    @Query("SELECT * FROM workout_set WHERE date = :date")
    suspend fun getSetsByDate(date: String): List<WorkoutSet>

    @Query(
        """
        SELECT * FROM workout_set
        WHERE date IN (:dates)
        ORDER BY date DESC, exerciseId ASC, setNumber ASC
        """
    )
    suspend fun getForDates(dates: List<String>): List<WorkoutSet>

    @Query(
        """
        SELECT * FROM workout_set 
        WHERE exerciseId = :exerciseId AND date < :beforeDate
        ORDER BY date DESC
        LIMIT 100
        """
    )
    suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet>

    @Query(
        """
        SELECT MAX(weightLbs) FROM workout_set
        WHERE exerciseId = :exerciseId AND date < :beforeDate
    """
    )
    suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int?

    @Query(
        """
        SELECT * FROM workout_set
        WHERE exerciseId = :exerciseId AND date >= :sinceDate
        ORDER BY date ASC
        """
    )
    suspend fun getSetsSince(exerciseId: Long, sinceDate: String): List<WorkoutSet>

    @Query(
        """
        SELECT * FROM workout_set
        WHERE exerciseId = :exerciseId AND date >= :sinceDate
        ORDER BY date ASC, setNumber ASC
        """
    )
    fun observeSetsSince(exerciseId: Long, sinceDate: String): Flow<List<WorkoutSet>>

    @Query(
        """
        SELECT exerciseId, MAX(weightLbs) as maxWeight
        FROM workout_set
        GROUP BY exerciseId
        """
    )
    suspend fun getAllTimeMaxWeights(): List<ExerciseMaxWeight>

    @Query(
        """
        SELECT exerciseId, MAX(weightLbs) as maxWeight
        FROM workout_set
        WHERE exerciseId IN (:exerciseIds)
        GROUP BY exerciseId
        """
    )
    suspend fun getMaxWeightsForExercises(exerciseIds: List<Long>): List<ExerciseMaxWeight>
}

data class ExerciseVolume(val exerciseId: Long, val volume: Long)
data class ExerciseMaxWeight(val exerciseId: Long, val maxWeight: Int)

data class ExerciseProgressSummary(
    val exerciseId: Long,
    val exerciseName: String,
    val isBodyweight: Boolean,
    val sessionCount: Int,
    val lastSessionDate: String
)

data class DateVolume(
    val date: String,
    val volume: Long
)
