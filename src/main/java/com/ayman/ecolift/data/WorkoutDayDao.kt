package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workoutDays: List<WorkoutDay>)

    @Upsert
    suspend fun upsert(workoutDay: WorkoutDay)

    @Query("SELECT * FROM workout_day WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<WorkoutDay?>

    @Query("SELECT * FROM workout_day WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): WorkoutDay?

    @Query("SELECT * FROM workout_day ORDER BY date ASC")
    fun observeAll(): Flow<List<WorkoutDay>>

    @Query("SELECT * FROM workout_day ORDER BY date ASC")
    suspend fun getAll(): List<WorkoutDay>

    @Query(
        """
        SELECT * FROM workout_day
        WHERE cycleSlotId = :slotId AND date < :beforeDate
        ORDER BY date DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForSlotIdBefore(beforeDate: String, slotId: Long): WorkoutDay?

    @Query(
        """
        SELECT * FROM workout_day
        WHERE cycleSlotType = :slotType AND date < :beforeDate
        ORDER BY date DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForSlotTypeBefore(beforeDate: String, slotType: Int): WorkoutDay?

    @Query(
        """
        SELECT MAX(cycleSlotOccurrence) FROM workout_day
        WHERE cycleSlotId = :slotId AND date < :beforeDate
        """
    )
    suspend fun getMaxOccurrenceForSlotBefore(beforeDate: String, slotId: Long): Int?

    @Query(
        """
        SELECT * FROM workout_day
        WHERE cycleSlotId = :slotId
          AND cycleSlotOccurrence = :occurrence
          AND date < :beforeDate
        ORDER BY date DESC
        LIMIT 1
        """
    )
    suspend fun getPreviousOccurrenceForSlot(
        slotId: Long,
        occurrence: Int,
        beforeDate: String,
    ): WorkoutDay?

    @Query("DELETE FROM workout_day WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query(
        """
        SELECT MAX(cycleSlotOccurrence) FROM workout_day
        WHERE cycleSlotType = :slotType AND date < :beforeDate
        """
    )
    suspend fun getMaxOccurrenceBefore(beforeDate: String, slotType: Int): Int?

    @Query(
        """
        SELECT * FROM workout_day
        WHERE cycleSlotType = :slotType
          AND cycleSlotOccurrence = :occurrence
          AND date < :beforeDate
        ORDER BY date DESC
        LIMIT 1
        """
    )
    suspend fun getPreviousOccurrence(
        slotType: Int,
        occurrence: Int,
        beforeDate: String,
    ): WorkoutDay?
}
