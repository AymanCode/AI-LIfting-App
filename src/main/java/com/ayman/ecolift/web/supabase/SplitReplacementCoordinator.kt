package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.data.splits.SplitSwitchPlan
import com.ayman.ecolift.data.splits.SplitSwitchPrecondition
import com.ayman.ecolift.data.splits.SplitSwitchRequest
import com.ayman.ecolift.data.splits.SplitSwitchTarget
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.PendingWrite
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import com.ayman.ecolift.web.supabase.sync.orThrow
import com.ayman.ecolift.web.supabase.sync.requireValid
import com.ayman.ecolift.web.supabase.sync.toQueuedSetDraft
import com.ayman.ecolift.web.supabase.sync.toWorkoutSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ResolvedSplitReplacement(
    val request: SplitSwitchRequest,
    val plan: SplitSwitchPlan,
    val precondition: SplitSwitchPrecondition,
    val day: DayPendingWrite,
    val desiredSets: List<WorkoutSet>,
)

sealed interface SplitReplacementResult {
    data class Applied(val outcome: SplitSwitchOutcome.Applied) : SplitReplacementResult
    data class ReplanRequired(val replacement: ResolvedSplitReplacement) : SplitReplacementResult
    data class Failed(val outcome: SplitSwitchOutcome.Failed) : SplitReplacementResult
}

class SplitReplacementCoordinator(
    private val journal: SplitReplacementJournal,
    private val replaceSetDayBatch: suspend (String, List<WorkoutSet>) -> LocalCommit<*>,
    private val enqueueDayBatch: suspend (DayPendingWrite) -> Unit,
    private val withDrainLock: suspend (suspend () -> Unit) -> Unit,
    private val kickEngine: () -> Unit,
    private val restoreSetDayBatch: suspend (String, List<WorkoutSet>, Map<Long, WorkoutCardDisplayMode>, () -> Unit) -> LocalCommit<*> =
        { date, sets, _, validate -> validate(); replaceSetDayBatch(date, sets) },
) {
    private val replacementMutex = Mutex()
    private val replacementDates = mutableSetOf<String>()

    suspend fun apply(
        requestedReplacement: ResolvedSplitReplacement,
        replanFromCanonicalState: suspend () -> ResolvedSplitReplacement,
    ): SplitReplacementResult = replacementMutex.withLock {
        val requested = requestedReplacement.frozen().also { it.requireConsistent() }
        var result: SplitReplacementResult? = null
        var kickAfterUnlock = false
        var kickAttempted = false

        try {
            withDrainLock {
                val resumedDates = resumeLocked()
                kickAfterUnlock = resumedDates.isNotEmpty()

                val fresh = replanFromCanonicalState().frozen().also { it.requireConsistent() }
                if (fresh.request != requested.request || fresh.precondition != requested.precondition) {
                    result = SplitReplacementResult.ReplanRequired(fresh)
                    return@withDrainLock
                }

                val prepared = PreparedSplitReplacement(
                    requestId = fresh.request.requestId,
                    date = fresh.request.date,
                    day = fresh.day,
                    desiredSets = fresh.desiredSets.map { it.toQueuedSetDraft() },
                ).requireValid()
                journal.save(prepared).getOrThrow()
                replaceSetDayBatch(prepared.date, prepared.desiredSets.map { it.toWorkoutSet() }).orThrow()
                enqueueDayBatch(prepared.day)
                journal.clear(prepared.date).getOrThrow()
                replacementDates += prepared.date
                kickAfterUnlock = true
                result = SplitReplacementResult.Applied(
                    SplitSwitchOutcome.Applied(prepared.requestId, prepared.date),
                )
            }

            if (kickAfterUnlock) {
                kickAttempted = true
                kickEngine()
            }
            requireNotNull(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (kickAfterUnlock && !kickAttempted) runCatching { kickEngine() }
            SplitReplacementResult.Failed(
                SplitSwitchOutcome.Failed(
                    requestId = requested.request.requestId,
                    message = "Couldn't switch the split. Try again.",
                ),
            )
        }
    }

    /** Puts a day back exactly as an Undo snapshot had it, checked sets and parked cards included. */
    suspend fun restore(
        date: String,
        day: DayPendingWrite,
        sets: List<WorkoutSet>,
        cardModes: Map<Long, WorkoutCardDisplayMode>,
        validate: () -> Unit = {},
    ): Result<Unit> = replacementMutex.withLock {
        var kickAfterUnlock = false
        // Once restoreSetDayBatch has returned, the sets are already back: a later step failing
        // (the day assignment write, or clearing the journal) must not be reported as a failed
        // Undo, since resumeLocked() durably finishes it on the next apply/restore/recoverPending.
        var setsRestored = false
        try {
            withDrainLock {
                kickAfterUnlock = resumeLocked().isNotEmpty()
                val prepared = PreparedSplitReplacement(
                    requestId = 0,
                    date = date,
                    day = day,
                    desiredSets = sets.map { it.toQueuedSetDraft() },
                    restoresHistory = true,
                    cardModes = cardModes,
                ).requireValid()
                journal.save(prepared).getOrThrow()
                try {
                    restoreSetDayBatch(prepared.date, prepared.desiredSets.map { it.toWorkoutSet() }, prepared.cardModes, validate).orThrow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    // Nothing local changed (replaceDayBatchV1 only publishes at its end), so the
                    // journal entry this just wrote would otherwise replay a restore that never
                    // ran, silently reverting the day on the next launch or split action.
                    runCatching { journal.clear(prepared.date) }
                    throw failure
                }
                setsRestored = true
                enqueueDayBatch(prepared.day)
                journal.clear(prepared.date).getOrThrow()
                replacementDates += prepared.date
                kickAfterUnlock = true
            }
            if (kickAfterUnlock) kickEngine()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (kickAfterUnlock) runCatching { kickEngine() }
            if (setsRestored) Result.success(Unit) else Result.failure(failure)
        }
    }

    suspend fun recoverPending(kickWhenRecovered: Boolean = true): Result<Unit> = replacementMutex.withLock {
        var kickAfterUnlock = false
        try {
            withDrainLock {
                kickAfterUnlock = resumeLocked().isNotEmpty()
            }
            if (kickAfterUnlock && kickWhenRecovered) kickEngine()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    fun onSetOperationStarted(op: PendingWrite) {
        when (op) {
            is PendingWrite.Insert -> Unit
            is PendingWrite.Update -> Unit
            is PendingWrite.Delete -> Unit
            is PendingWrite.DeleteNaturalKey -> replacementDates += op.date
            is PendingWrite.MutationV1 -> replacementDates += op.sourceDate
            is PendingWrite.CardDisplayMutationV1 -> Unit
            is PendingWrite.CardSplitOriginMutationV1 -> replacementDates += op.desiredCard.date
        }
    }

    fun permanentFailureMessage(op: PendingWrite): String? {
        val date = when (op) {
            is PendingWrite.Insert -> op.set.date
            is PendingWrite.Update -> op.set.date
            is PendingWrite.Delete -> null
            is PendingWrite.DeleteNaturalKey -> op.date
            is PendingWrite.MutationV1 -> op.sourceDate
            is PendingWrite.CardDisplayMutationV1 -> null
            is PendingWrite.CardSplitOriginMutationV1 -> op.desiredCard.date
        } ?: return null
        return replacementFailureMessage(date)
    }

    fun permanentFailureMessage(op: DayPendingWrite): String = replacementFailureMessage(op.date)

    fun onDayOperationDrained(op: DayPendingWrite) {
        replacementDates -= op.date
    }

    private suspend fun resumeLocked(): Set<String> {
        val resumedDates = linkedSetOf<String>()
        journal.load().getOrThrow().forEach { prepared ->
            prepared.requireValid()
            val sets = prepared.desiredSets.map { it.toWorkoutSet() }
            if (prepared.restoresHistory) {
                restoreSetDayBatch(prepared.date, sets, prepared.cardModes, {}).orThrow()
            } else {
                replaceSetDayBatch(prepared.date, sets).orThrow()
            }
            enqueueDayBatch(prepared.day)
            journal.clear(prepared.date).getOrThrow()
            replacementDates += prepared.date
            resumedDates += prepared.date
        }
        return resumedDates
    }

    private fun ResolvedSplitReplacement.frozen() = copy(
        request = request.copy(
            target = when (val currentTarget = request.target) {
                is SplitSwitchTarget.Saved -> currentTarget
                is SplitSwitchTarget.Proposal -> currentTarget.copy(exerciseIds = currentTarget.exerciseIds.toList())
                SplitSwitchTarget.Clear -> SplitSwitchTarget.Clear
            },
        ),
        precondition = precondition.copy(
            daySets = precondition.daySets.toList(),
            baselineSeed = precondition.baselineSeed.toList(),
            desiredSeed = precondition.desiredSeed.toList(),
        ),
        desiredSets = desiredSets.toList(),
    )

    private fun ResolvedSplitReplacement.requireConsistent() {
        require(day.date == request.date) { "Replacement day must match request date" }
        require(desiredSets.all { it.date == request.date }) { "Replacement sets must match request date" }
        when (val currentPlan = plan) {
            is SplitSwitchPlan.Apply -> require(currentPlan.request == request) { "Apply plan request changed" }
            is SplitSwitchPlan.NeedsConfirm -> require(currentPlan.request == request) { "Confirmation plan request changed" }
            SplitSwitchPlan.NoOp -> Unit
        }
    }

    private fun replacementFailureMessage(date: String): String =
        "Workout changes for $date couldn't finish syncing. Reopen that date and try again."
}
