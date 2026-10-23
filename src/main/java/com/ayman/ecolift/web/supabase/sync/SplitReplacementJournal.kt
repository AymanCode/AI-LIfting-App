package com.ayman.ecolift.web.supabase.sync

import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.serialization.Serializable

@Serializable
data class QueuedSetDraft(
    val exerciseId: Long,
    val date: String,
    val setNumber: Int,
    val weightLbs: Int? = null,
    val reps: Int? = null,
    val isBodyweight: Boolean = false,
    val completed: Boolean = false,
    val restTimeSeconds: Int? = null,
    val isWarmup: Boolean = false,
)

@Serializable
data class PreparedSplitReplacement(
    val requestId: Long,
    val date: String,
    val day: DayPendingWrite,
    val desiredSets: List<QueuedSetDraft>,
    /** An Undo restoring the day as it was: checked sets are allowed back. */
    val restoresHistory: Boolean = false,
    /** exerciseId -> card mode for cards that were not EXPANDED; empty for split loads. */
    val cardModes: Map<Long, WorkoutCardDisplayMode> = emptyMap(),
)

fun WorkoutSet.toQueuedSetDraft() = QueuedSetDraft(
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed, isWarmup = isWarmup,
    restTimeSeconds = restTimeSeconds,
)

fun QueuedSetDraft.toWorkoutSet() = WorkoutSet(
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed, isWarmup = isWarmup,
    restTimeSeconds = restTimeSeconds,
)

fun PreparedSplitReplacement.requireValid(): PreparedSplitReplacement {
    require(day.date == date) { "Prepared workout day must belong to $date" }
    require(desiredSets.all { it.date == date }) { "Every desired set must belong to $date" }
    require(restoresHistory || desiredSets.none { it.completed }) { "Generated replacement sets must be incomplete" }
    require(desiredSets.map { it.exerciseId to it.setNumber }.distinct().size == desiredSets.size) {
        "Generated replacement sets must have unique natural keys"
    }
    return this
}

interface SplitReplacementJournal {
    fun load(): Result<List<PreparedSplitReplacement>>
    fun save(replacement: PreparedSplitReplacement): Result<Unit>
    fun clear(date: String): Result<Unit>
}

interface SplitReplacementJournalStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
}
