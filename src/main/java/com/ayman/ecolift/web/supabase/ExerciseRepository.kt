package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseAlias
import com.ayman.ecolift.data.ExerciseDistinctPair
import com.ayman.ecolift.data.ExerciseMatch
import com.ayman.ecolift.data.ExerciseSuggestion
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import kotlinx.coroutines.flow.StateFlow

class ExerciseCanonicalizationException(id: Long) :
    IllegalStateException("Exercise $id has a cyclic or overlong merge chain")

internal class ExerciseUniqueViolationException : IllegalStateException("23505")

sealed interface ExerciseRenameResult {
    data class Success(val exercise: Exercise) : ExerciseRenameResult
    data class NameCollision(val existing: Exercise) : ExerciseRenameResult
}

data class ExerciseMergePreview(
    val exerciseIdA: Long,
    val exerciseIdB: Long,
    val expectedKeeperId: Long,
)

sealed interface ExerciseMergePreparationResult {
    data class Ready(val preview: ExerciseMergePreview) : ExerciseMergePreparationResult
    data object QueueNotSettled : ExerciseMergePreparationResult
    data object ReconciliationRequired : ExerciseMergePreparationResult
}

sealed interface ExerciseMergeResult {
    data class Success(val keeperId: Long) : ExerciseMergeResult
    data object KeeperDrift : ExerciseMergeResult
    data object QueueNotSettled : ExerciseMergeResult
    data object ReconciliationRequired : ExerciseMergeResult
}

sealed interface ExerciseMergeGateResult<out T> {
    data class Ready<T>(val value: T) : ExerciseMergeGateResult<T>
    data object QueueNotSettled : ExerciseMergeGateResult<Nothing>
    data object ReconciliationRequired : ExerciseMergeGateResult<Nothing>
}

/** Serializes merge preparation/RPC work with the set sync engine. */
interface ExerciseMergeGate {
    suspend fun <T> withSettledQueue(
        exerciseIds: Set<Long>,
        block: suspend () -> T,
    ): ExerciseMergeGateResult<T>

    /** Settles only writes naming [exerciseIds]; durable work for other exercises does not hold the block. */
    suspend fun <T> withSettledExercises(
        exerciseIds: Set<Long>,
        block: suspend () -> T,
    ): ExerciseMergeGateResult<T> = withSettledQueue(exerciseIds, block)
}

interface ExerciseRepository {
    /** Active exercises only. Tombstones stay in the repository's private catalog. */
    val exercises: StateFlow<List<Exercise>>
    fun observeAliases(): StateFlow<List<ExerciseAlias>>
    suspend fun refresh()
    suspend fun getAll(): List<Exercise>
    fun canonicalId(id: Long): Long
    /** Every exercise id, merged-away rows included, to its merged_into_id (null for keepers). */
    fun mergeGraph(): Map<Long, Long?>
    suspend fun getById(id: Long): Exercise?
    suspend fun getByIds(ids: Collection<Long>): List<Exercise>
    suspend fun findExact(name: String): Exercise?
    suspend fun getOrCreate(name: String): Exercise
    suspend fun suggestions(query: String, maxDistance: Int = 2, limit: Int = 5): List<ExerciseSuggestion>
    suspend fun getPredictive(query: String, limit: Int = 4): List<Exercise>
    suspend fun duplicateCandidate(exerciseId: Long): ExerciseMatch?
    suspend fun duplicateCandidate(name: String): ExerciseMatch?
    fun normalizeName(name: String): String
    suspend fun updateName(id: Long, newName: String): ExerciseRenameResult
    suspend fun updateMuscleGroups(id: Long, muscleGroups: String)
    suspend fun deleteExercise(id: Long)
    suspend fun loadDistinctPairs(): List<ExerciseDistinctPair>
    suspend fun recordAlias(exerciseId: Long, alias: String)
    suspend fun recordDistinctPair(idA: Long, idB: Long)
    suspend fun prepareMerge(idA: Long, idB: Long): ExerciseMergePreparationResult
    suspend fun mergeExercises(
        idA: Long,
        idB: Long,
        expectedKeeperId: Long,
        alias: String? = null,
    ): ExerciseMergeResult
}

/** SQL-equivalent client key for migration 0007's normalized-name conflict recovery. */
fun simpleNameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * Exercises created on this device before the server knows them. A created exercise has a
 * negative local id that sets may reference at once; the uploader maps it to a server id later.
 */
interface LocalExerciseCatalog {
    suspend fun create(name: String, isBodyweight: Boolean): LocalCommit<Exercise>

    /** Every stored local exercise as a catalog row, or null when storage cannot be read. */
    suspend fun exercises(): List<Exercise>?

    /** Rename a local exercise whose create request has not been sent. */
    suspend fun rename(localId: Long, name: String): LocalCommit<Exercise>

    /**
     * Exercise ids named by queued durable requests, or null when that queue cannot be read.
     * Requests waiting on a local exercise are included.
     */
    suspend fun queuedExerciseIds(): Set<Long>?

    /**
     * Store a pending delete record for each of [ids], stamped with [sentAt] and [attemptId], so
     * nothing in any tab stores a reference to one. Refused when a queued request already names one;
     * the delete must then not be sent. A [resend] only restamps records still pending and never creates one.
     */
    suspend fun recordDeletes(ids: Set<Long>, sentAt: Long, attemptId: String, resend: Boolean = false): DeleteRecording =
        DeleteRecording.Recorded(null)

    /** The server deleted [ids]. Their records stay, so a stale catalog cannot name them again. */
    suspend fun confirmDeletes(ids: Set<Long>): Boolean = true

    /**
     * The server refused attempt [attemptId], so the exercises stay and their pending records go. False
     * when storage failed or a pending record names another attempt, which may still land; the records then stay.
     */
    suspend fun dropDeletes(ids: Set<Long>, attemptId: String): Boolean = true

    /** Stored delete records as exercise id to whether the server confirmed it, or null when storage cannot be read. */
    suspend fun recordedDeletes(): Map<Long, Boolean>? = emptyMap()
}

sealed interface DeleteRecording {
    /** Stored. [earlierSentAt] is when the latest earlier attempt for one of the ids was sent, or null when none was. */
    data class Recorded(val earlierSentAt: Long?) : DeleteRecording

    /** A queued request names the exercise. */
    data object Referenced : DeleteRecording

    /** Nothing is still pending to send again. */
    data object Settled : DeleteRecording
    data object StorageUnavailable : DeleteRecording
}
