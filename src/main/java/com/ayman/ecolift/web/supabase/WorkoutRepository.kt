package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.data.splits.DayWrite
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WorkoutRepository {
    val cycle: StateFlow<Cycle>
    fun observeWorkoutDay(date: String): Flow<WorkoutDay?>
    fun observeAllWorkoutDays(): Flow<List<WorkoutDay>>
    fun observeCycleSlots(): Flow<List<CycleSlot>>
    /**
     * Every slot, including archived ones.
     *
     * [observeCycleSlots] filters to active slots, which is right for editing and logging
     * but wrong for history: a past month that ran a since-archived split would show its
     * sessions as having no split at all. Read-only history views want this instead.
     */
    fun observeAllCycleSlots(): Flow<List<CycleSlot>>
    suspend fun getCycleSlots(): List<CycleSlot>
    suspend fun getSlotByIdIncludingArchived(id: Long): CycleSlot?
    fun observeSplitExercises(splitId: Long): Flow<List<SplitExercise>>
    fun observeAllSplitExercises(): Flow<List<SplitExercise>>
    /** Every split_exercise row, retired and suppressed slots included: the engine's anchors. */
    fun observeAllSplitExercisesIncludingRetired(): Flow<List<SplitExercise>>
    fun observeArchivedCycles(): Flow<List<ArchivedCycle>>
    fun observeUserBodyweightLbs(): Flow<Int?>
    suspend fun addCycleSlot(name: String): Long
    suspend fun deleteCycleSlot(id: Long)
    suspend fun renameCycleSlot(id: Long, name: String)
    /** Online-only protected append; reloads membership after the RPC succeeds. */
    suspend fun appendSplitExercise(slotId: Long, exerciseId: Long)
    /** The saved boards (split_member_choice), read with every slot refresh. */
    val splitMemberChoices: StateFlow<SplitChoicesState>
    /**
     * Saves [board] as the split's whole board. It shows in [splitMemberChoices] before the call
     * suspends and is taken back if the save fails. Saves run one at a time, in call order.
     */
    suspend fun saveSplitMembers(slotId: Long, board: EffectiveSplitMembers)
    suspend fun getCycle(): Cycle
    suspend fun saveCycle(isActive: Boolean, numTypes: Int)
    suspend fun setNextSessionType(slotType: Int)
    suspend fun assignCycleSlot(date: String, slotId: Long, alternativeFor: String? = null): WorkoutDay
    suspend fun clearCycleSlot(date: String)
    suspend fun ensureWorkoutDay(date: String)
    suspend fun enqueueDayBatch(op: DayPendingWrite)
    suspend fun getPreviousOccurrenceDayForSlot(date: String, slotId: Long, occurrence: Int): WorkoutDay?
    suspend fun getArchivedCycle(id: Long): ArchivedCycle?
    suspend fun deleteArchivedCycle(id: Long)
    suspend fun countOverlappingArchives(start: String, end: String): Int
    suspend fun getLatestWorkoutDate(): String?
    suspend fun moveWorkoutDay(currentDate: String, newDate: String)
    suspend fun getActiveCycleStart(): String
    suspend fun archivedCycleProgress(archiveId: Long): CycleProgressCore
    suspend fun archivedCycleComparison(archiveId: Long, window: ComparisonWindow): CycleComparison
    suspend fun archiveCurrentCycle(name: String, startDate: String, endDate: String): Long
    suspend fun archiveSummary(archive: ArchivedCycle): ArchiveSummary
    suspend fun registerSmartSplit(name: String, exerciseIds: List<Long>, dates: List<String>): SmartSplitRegistration

    /**
     * upsert_learned_split_v1: inserts a slot ([slotId] null) or updates one, and replaces its
     * members in one transaction. [name] null keeps the stored name. Returns the slot id. Does not
     * reload slots; call [refreshSlots] once after a batch.
     */
    suspend fun upsertLearnedSlot(slotId: Long?, name: String?, learned: Boolean, memberIds: List<Long>, anchorUpdatedAt: Long): Long
    /** Reloads cycle_slot, split_exercise and archived_cycle (none is on the realtime channel). */
    suspend fun refreshSlots()
    /** Archives an engine-owned slot (the database turns the delete into archived_at). */
    suspend fun archiveLearnedSlot(slotId: Long)
    /** Retires a split by hand: suppressed_at, so the engine never recreates it. */
    suspend fun suppressCycleSlot(slotId: Long)
    /** Queues the engine's day attributions through the durable day queue. */
    suspend fun enqueueLearnedDayWrites(writes: List<DayWrite>)
}

/** The engine's day write as the day queue's full-row write (all four split fields). */
fun DayWrite.toPendingWrite() = DayPendingWrite(
    date = date,
    cycleSlotId = slotId,
    cycleSlotType = slotType,
    cycleSlotOccurrence = occurrence,
    alternativeForDate = null,
)

/** Result of register_smart_split_v1: either the new slot and the dates it claimed,
 * or a refusal code (an RPC-returned code, or "pending_sync"/"bad_response" from the client). */
sealed interface SmartSplitRegistration {
    data class Added(val slotId: Long, val claimedDates: List<String>) : SmartSplitRegistration
    data class Refused(val code: String) : SmartSplitRegistration
}

data class ArchiveSummary(
    val splitCount: Int,
    val totalSessions: Int,
    val totalVolumeLbs: Long,
)

/** The routine context Log reads offline: the cycle, its slots, split exercises and assigned days. */
data class RoutineParts(val cycle: Cycle, val slots: List<CycleSlot>, val splitExercises: List<SplitExercise>, val days: List<WorkoutDay>)
