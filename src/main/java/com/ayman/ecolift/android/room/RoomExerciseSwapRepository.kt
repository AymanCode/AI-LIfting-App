package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.room.SplitAlternativeRow
import com.ayman.ecolift.data.room.SwapAlternativeQueueRow
import com.ayman.ecolift.data.splits.SplitAlternative
import com.ayman.ecolift.web.supabase.ExerciseSwapRepository
import com.ayman.ecolift.web.supabase.ExerciseSwapRequest
import com.ayman.ecolift.web.supabase.ExerciseSwapResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class RoomExerciseSwapRepository(
    private val store: RoomStore,
    private val onChanged: () -> Unit = {},
) : ExerciseSwapRepository {
    override val alternatives: StateFlow<List<SplitAlternative>> = store.alternatives

    override suspend fun refresh() {
        store.write {
            alternatives = db.splitAlternativeDao().getAll().map { row ->
                SplitAlternative(row.splitId, row.sourceId, row.alternativeId, row.replacesSlot)
            }
        }
    }

    override suspend fun swap(request: ExerciseSwapRequest): ExerciseSwapResult {
        val result = store.write {
            val current = db.workoutDayStateDao().revision(request.date) ?: 0L
            val bound = bindSwapRevision(request, mapOf(request.date to current))
            val receipt = db.exerciseSwapReceiptDao().find(request.requestId)?.toSwapReceipt()
            val plan = planExerciseSwap(
                world = swapSnapshot(listOfNotNull(receipt), mapOf(request.date to current)),
                request = bound,
            )
            plan.change?.let { applySwapChange(it) }
            plan.result
        }
        onChanged()
        return result
    }

    override suspend fun undo(requestId: String): ExerciseSwapResult {
        val result = store.write {
            val receipt = db.exerciseSwapReceiptDao().find(requestId)?.toSwapReceipt()
            val revision = if (receipt == null) 0L else db.workoutDayStateDao().revision(receipt.date) ?: 0L
            val revisions = if (receipt == null) emptyMap() else mapOf(receipt.date to revision)
            val plan = planExerciseUndo(swapSnapshot(listOfNotNull(receipt), revisions), requestId)
            plan.change?.let { applySwapChange(it) }
            plan.result
        }
        onChanged()
        return result
    }

    override fun queueAlternatives(choices: List<SplitAlternative>) {
        if (choices.isEmpty()) return
        runBlocking(Dispatchers.IO) {
            store.write {
                val existing = db.swapAlternativeQueueDao().all().map { row ->
                    SplitAlternative(row.splitId, row.sourceId, row.alternativeId)
                }
                val merged = enqueueAlternatives(existing, choices)
                db.swapAlternativeQueueDao().deleteAll()
                if (merged.isNotEmpty()) {
                    db.swapAlternativeQueueDao().insertAll(
                        merged.map { choice ->
                            SwapAlternativeQueueRow(choice.splitId, choice.sourceId, choice.alternativeId)
                        },
                    )
                }
            }
        }
    }

    override suspend fun flushAlternatives() {
        val pending = store.db.swapAlternativeQueueDao().all()
        for (row in pending) {
            store.write {
                val plan = planRemember(swapSnapshot(emptyList(), emptyMap()), row.splitId, row.sourceId, row.alternativeId)
                plan.change?.let { applySwapChange(it) }
                db.swapAlternativeQueueDao().delete(row.splitId, row.sourceId, row.alternativeId)
            }
        }
        onChanged()
    }

    override suspend fun recover() {
        try {
            if (store.db.swapAlternativeQueueDao().all().isNotEmpty()) flushAlternatives()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }
}

private fun RoomDraft.swapSnapshot(receipts: List<SwapReceipt>, revisions: Map<String, Long>) = SwapWorld(
    exercises = exercises,
    cards = cards,
    sets = sets,
    slots = slots,
    members = splitExercises,
    alternatives = alternatives,
    receipts = receipts,
    dayRevisions = revisions,
)

private suspend fun RoomDraft.applySwapChange(change: SwapChange) {
    if (change.deleteSetIds.isNotEmpty()) removeSets(change.deleteSetIds)
    if (change.finalCards != null) rewriteDayCards(change.date, change.finalCards)
    change.insertSets.forEach { insertSet(it) }
    val choice = change.remember
    if (choice != null) {
        val inserted = db.splitAlternativeDao().insert(
            SplitAlternativeRow(
                splitId = choice.splitId,
                sourceId = choice.sourceId,
                alternativeId = choice.alternativeId,
                replacesSlot = choice.replacesSlot,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (inserted != -1L) alternatives = alternatives + choice
    }
    change.members.forEach { member ->
        when (member) {
            is MemberWrite.Mark -> markSplitMemberUserAdded(member.id)
            is MemberWrite.Add -> insertSplitMember(
                SplitExercise(
                    splitId = member.splitId,
                    exerciseId = member.exerciseId,
                    orderIndex = member.orderIndex,
                    userAdded = true,
                ),
            )
        }
    }
    val receipt = change.receipt
    if (receipt != null) {
        val revision = bumpDay(change.date)
        check(revision == receipt.afterRevision)
        db.exerciseSwapReceiptDao().insert(receipt.toRow())
    } else if (change.bump) {
        bumpDay(change.date)
    }
    val undoneRequest = change.markUndoneRequestId
    if (undoneRequest != null) db.exerciseSwapReceiptDao().markUndone(undoneRequest)
}
