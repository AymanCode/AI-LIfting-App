package com.ayman.ecolift.web.supabase.sync

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Serializable mirror of WorkoutSet, keyed by the id the UI currently knows
 *  (temp negative for a not-yet-synced insert, or the real positive id). */
@Serializable
data class QueuedSet(
    val localId: Long,
    val exerciseId: Long,
    val date: String,
    val setNumber: Int,
    val weightLbs: Int? = null,
    val reps: Int? = null,
    val isBodyweight: Boolean = false,
    val completed: Boolean = false,
    val restTimeSeconds: Int? = null,
    val syncId: String = "",
    val rowRevision: Long = 0,
    val cardId: String = "",
    val isWarmup: Boolean = false,
)

fun WorkoutSet.toQueued() = QueuedSet(
    localId = id, exerciseId = exerciseId, date = date, setNumber = setNumber,
    weightLbs = weightLbs, reps = reps, isBodyweight = isBodyweight,
    completed = completed, isWarmup = isWarmup, restTimeSeconds = restTimeSeconds,
    syncId = syncId, rowRevision = rowRevision, cardId = cardId,
)

@Serializable
data class QueuedWorkoutCard(
    val id: String,
    val date: String,
    val exerciseId: Long,
    val position: Int,
    val displayMode: WorkoutCardDisplayMode,
    val origin: WorkoutCardOrigin,
    val sourcePlanId: String? = null,
    val rowRevision: Long = 1,
    val createdAt: String = "",
    val updatedAt: String = "",
)

fun WorkoutExerciseCard.toQueued() = QueuedWorkoutCard(
    id = id,
    date = date,
    exerciseId = exerciseId,
    position = position,
    displayMode = displayMode,
    origin = origin,
    sourcePlanId = sourcePlanId,
    rowRevision = rowRevision,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun QueuedWorkoutCard.toDomain() = WorkoutExerciseCard(
    id = id,
    date = date,
    exerciseId = exerciseId,
    position = position,
    displayMode = displayMode,
    origin = origin,
    sourcePlanId = sourcePlanId,
    rowRevision = rowRevision,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Serializable
sealed class PendingWrite {
    @Serializable
    sealed class IdBased : PendingWrite() {
        abstract val localId: Long
    }

    @Serializable @SerialName("insert")
    data class Insert(override val localId: Long, val set: QueuedSet) : IdBased()

    @Serializable @SerialName("update")
    data class Update(override val localId: Long, val set: QueuedSet) : IdBased()

    @Serializable @SerialName("delete")
    data class Delete(override val localId: Long) : IdBased()

    @Serializable @SerialName("delete_natural_key")
    data class DeleteNaturalKey(
        val date: String,
        val exerciseId: Long,
        val setNumber: Int,
    ) : PendingWrite()

    /**
     * Immutable V1 server mutation. Unlike the legacy variants above, this
     * object is never coalesced after creation: mutation id, preconditions, and
     * payload hash must continue to describe the exact request that may already
     * have committed while its response was lost.
     *
     * [desiredSet] is the optimistic read model for INSERT/UPDATE and is null
     * for DELETE. [sourceDate] remains available for DELETE and date moves even
     * though DELETE's typed RPC payload contains no mutable set fields.
     * A non-null [blockedCode] preserves a rejected local edit for explicit
     * reconciliation instead of retrying forever or silently discarding it.
     */
    @Serializable @SerialName("mutation_v1")
    data class MutationV1(
        override val localId: Long,
        val params: MutateWorkoutSetV1Params,
        val desiredSet: QueuedSet? = null,
        val sourceDate: String,
        val blockedCode: String? = null,
    ) : IdBased()

    /** Immutable, idempotent compact/expanded card mutation. */
    @Serializable @SerialName("card_display_mutation_v1")
    data class CardDisplayMutationV1(
        val params: MutateWorkoutCardDisplayV1Params,
        val desiredCard: QueuedWorkoutCard,
        val blockedCode: String? = null,
    ) : PendingWrite()

    /** Immutable, idempotent provenance transition authored by split replacement. */
    @Serializable @SerialName("card_split_origin_mutation_v1")
    data class CardSplitOriginMutationV1(
        val params: MarkWorkoutCardSplitV1Params,
        val desiredCard: QueuedWorkoutCard,
        val blockedCode: String? = null,
    ) : PendingWrite()
}

fun PendingWrite.isLegacySetWrite(): Boolean = when (this) {
    is PendingWrite.Insert,
    is PendingWrite.Update,
    is PendingWrite.Delete,
    is PendingWrite.DeleteNaturalKey -> true
    is PendingWrite.MutationV1,
    is PendingWrite.CardDisplayMutationV1,
    is PendingWrite.CardSplitOriginMutationV1 -> false
}

fun PendingWrite.isVersionedWorkoutMutation(): Boolean =
    this is PendingWrite.MutationV1 ||
        this is PendingWrite.CardDisplayMutationV1 ||
        this is PendingWrite.CardSplitOriginMutationV1

fun PendingWrite.blockedCodeOrNull(): String? = when (this) {
    is PendingWrite.MutationV1 -> blockedCode
    is PendingWrite.CardDisplayMutationV1 -> blockedCode
    is PendingWrite.CardSplitOriginMutationV1 -> blockedCode
    else -> null
}

fun PendingWrite.mutationDateOrNull(): String? = when (this) {
    is PendingWrite.MutationV1 -> sourceDate
    is PendingWrite.CardDisplayMutationV1 -> desiredCard.date
    is PendingWrite.CardSplitOriginMutationV1 -> desiredCard.date
    else -> null
}

/**
 * Fold [op] into [this] queue, keeping it minimal and consistent:
 *  - Update of a still-pending Insert  -> rewrite the Insert payload (no separate op).
 *  - Update of an existing Update       -> collapse to the latest payload.
 *  - Delete of a still-pending Insert   -> cancel the Insert (drop its Updates too).
 *  - Delete of a synced row             -> drop pending Updates, append one Delete.
 *  - Insert                             -> append (localId is always a fresh temp id).
 *  - DeleteNaturalKey                   -> append without ID-based collapsing.
 */
fun List<PendingWrite>.coalesce(op: PendingWrite): List<PendingWrite> = when (op) {
    is PendingWrite.Insert -> this + op
    is PendingWrite.Update -> {
        val insertIdx = indexOfFirst { it is PendingWrite.Insert && it.localId == op.localId }
        if (insertIdx >= 0) {
            toMutableList().also { it[insertIdx] = PendingWrite.Insert(op.localId, op.set) }
        } else {
            filterNot { it is PendingWrite.Update && it.localId == op.localId } + op
        }
    }
    is PendingWrite.Delete -> {
        val hasPendingInsert = any { it is PendingWrite.Insert && it.localId == op.localId }
        if (hasPendingInsert) {
            filterNot { queued ->
                when (queued) {
                    is PendingWrite.Insert -> queued.localId == op.localId
                    is PendingWrite.Update -> queued.localId == op.localId
                    is PendingWrite.Delete -> queued.localId == op.localId
                    is PendingWrite.DeleteNaturalKey -> false
                    is PendingWrite.MutationV1 -> false
                    is PendingWrite.CardDisplayMutationV1 -> false
                    is PendingWrite.CardSplitOriginMutationV1 -> false
                }
            } // cancel insert + its updates
        } else {
            filterNot { it is PendingWrite.Update && it.localId == op.localId }
                .let { if (it.any { e -> e is PendingWrite.Delete && e.localId == op.localId }) it else it + op }
        }
    }
    is PendingWrite.DeleteNaturalKey -> this + op
    is PendingWrite.MutationV1 -> this + op
    is PendingWrite.CardDisplayMutationV1 -> this + op
    is PendingWrite.CardSplitOriginMutationV1 -> this + op
}
