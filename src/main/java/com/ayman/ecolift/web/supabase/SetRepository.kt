package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.DateVolume
import com.ayman.ecolift.data.ExerciseMaxWeight
import com.ayman.ecolift.data.ExerciseProgressSummary
import com.ayman.ecolift.data.ExerciseVolume
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.orThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SetRepository {
    val sets: StateFlow<List<WorkoutSet>>
    val cards: StateFlow<List<WorkoutExerciseCard>>
    suspend fun refresh()
    suspend fun insert(set: WorkoutSet): LocalCommit<WorkoutSet>
    suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet>
    suspend fun delete(id: Long)
    suspend fun updateCardDisplayMode(cardId: String, displayMode: WorkoutCardDisplayMode): LocalCommit<Unit>

    suspend fun getAllSets(): List<WorkoutSet>
    fun observeSetsForDate(date: String): Flow<List<WorkoutSet>>
    suspend fun getSetsForDate(date: String): List<WorkoutSet>
    /** Append a set to [exerciseId] on [date]. [copyFrom] is the set the user asked to copy;
     *  without one the new set inherits from today's last set, or failing that the most recent
     *  session. The values go in with the insert rather than as a follow-up update so there is
     *  no second op to lose while the insert is in flight. */
    suspend fun addSet(date: String, exerciseId: Long, copyFrom: WorkoutSet? = null): LocalCommit<WorkoutSet>
    suspend fun addExerciseSession(date: String, exerciseId: Long, isBodyweightDefault: Boolean = false): LocalCommit<List<WorkoutSet>>
    suspend fun addSetsForExercises(date: String, exerciseIds: List<Long>): LocalCommit<Unit>
    suspend fun replaceDayBatch(date: String, desiredSets: List<WorkoutSet>, cardModes: Map<Long, WorkoutCardDisplayMode> = emptyMap(), precondition: () -> Unit = {}): LocalCommit<Unit>
    suspend fun cloneDay(templateDate: String, targetDate: String): LocalCommit<Unit>
    /**
     * Save [set], an edit the caller computed from [observed]. Only the fields that differ from
     * [observed] are applied to the stored row, so a row that changed elsewhere keeps its other values.
     */
    suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet? = null): LocalCommit<WorkoutSet>
    suspend fun upsertSet(set: WorkoutSet): LocalCommit<Long>
    suspend fun getLastForDateAndExercise(date: String, exerciseId: Long): WorkoutSet?
    suspend fun getMostRecentBeforeDate(exerciseId: Long, date: String): WorkoutSet?
    suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet>
    suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int?
    suspend fun getLastSessionDate(beforeDate: String): String?
    suspend fun getSetsByDate(date: String): List<WorkoutSet>
    suspend fun getSetsForDates(dates: List<String>): List<WorkoutSet>
    suspend fun getCompletedSetsForDates(dates: List<String>): List<WorkoutSet>
    suspend fun getById(id: Long): WorkoutSet?
    fun observeExerciseProgressSummaries(): Flow<List<ExerciseProgressSummary>>
    fun observeSetsSince(exerciseId: Long, sinceDate: String): Flow<List<WorkoutSet>>
    suspend fun getVolumeHistory(exerciseId: Long, limit: Int): List<DateVolume>
    suspend fun getSetsSince(exerciseId: Long, sinceDate: String): List<WorkoutSet>
    suspend fun getVolumesSince(sinceDate: String): List<ExerciseVolume>
    suspend fun getAllTimeMaxWeights(): List<ExerciseMaxWeight>
    suspend fun getMaxWeightsForExercises(exerciseIds: List<Long>): List<ExerciseMaxWeight>
    suspend fun deleteSet(id: Long)
    suspend fun deleteSetIfOpen(id: Long, syncId: String = ""): Boolean {
        val current = sets.value.firstOrNull {
            if (syncId.isNotBlank()) it.syncId == syncId else it.id == id
        }?.takeUnless { it.completed } ?: return false
        deleteSet(current.id)
        return true
    }
    suspend fun completeSetIfOpen(id: Long, syncId: String = ""): WorkoutSet? {
        val current = sets.value.firstOrNull {
            if (syncId.isNotBlank()) it.syncId == syncId else it.id == id
        }?.takeUnless { it.completed } ?: return null
        val completed = current.copy(completed = true)
        updateSet(completed).orThrow()
        return completed
    }
    /** Delete many sets under one mutation lock, publishing `sets` once. */
    suspend fun deleteSets(ids: List<Long>, precondition: () -> Unit = {}): LocalCommit<Unit>
    /** Move all sets from [fromDate] to [toDate] (used by MoveWorkoutDay patch). */
    suspend fun updateDate(fromDate: String, toDate: String)
}
