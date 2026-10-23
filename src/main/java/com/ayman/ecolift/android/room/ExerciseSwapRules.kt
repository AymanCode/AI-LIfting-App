package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.room.ExerciseSwapReceiptRow
import com.ayman.ecolift.data.splits.SplitAlternative
import com.ayman.ecolift.web.supabase.ExerciseSwapRequest
import com.ayman.ecolift.web.supabase.ExerciseSwapResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.LocalDate

internal class SwapRejected(message: String) : IllegalStateException(message)

internal class MergeChainTooDeep(exerciseId: Long) : IllegalStateException(
    "canonical_exercise_id: merge chain too deep or cyclic at $exerciseId",
)

internal class SwapIds(
    val cardId: () -> String = { java.util.UUID.randomUUID().toString() },
    val syncId: () -> String = { java.util.UUID.randomUUID().toString() },
)

internal data class SwapWorld(
    val exercises: List<Exercise>,
    val cards: List<WorkoutExerciseCard>,
    val sets: List<WorkoutSet>,
    val slots: List<CycleSlot>,
    val members: List<SplitExercise>,
    val alternatives: List<SplitAlternative>,
    val receipts: List<SwapReceipt>,
    val dayRevisions: Map<String, Long>,
)

internal data class SwapReceipt(
    val requestId: String,
    val date: String,
    val sourceId: Long,
    val targetId: Long,
    val splitId: Long?,
    val beforeSourceSets: List<WorkoutSet>,
    val beforeCards: List<WorkoutExerciseCard>,
    val targetExisted: Boolean,
    val afterRevision: Long,
    val undone: Boolean,
)

internal sealed class MemberWrite {
    data class Mark(val id: Long) : MemberWrite()
    data class Add(val splitId: Long, val exerciseId: Long, val orderIndex: Int) : MemberWrite()
}

internal data class SwapChange(
    val date: String,
    val deleteSetIds: List<Long> = emptyList(),
    val finalCards: List<WorkoutExerciseCard>? = null,
    val insertSets: List<WorkoutSet> = emptyList(),
    val remember: SplitAlternative? = null,
    val members: List<MemberWrite> = emptyList(),
    val receipt: SwapReceipt? = null,
    val markUndoneRequestId: String? = null,
    val bump: Boolean = false,
)

internal data class SwapPlan(
    val result: ExerciseSwapResult,
    val world: SwapWorld,
    val change: SwapChange?,
)

private val isoDate = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
private val swapJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun isIsoDate(value: String): Boolean {
    if (!isoDate.matches(value)) return false
    return try {
        LocalDate.parse(value).toString() == value
    } catch (_: DateTimeException) {
        false
    }
}

internal fun canonicalExerciseId(exerciseId: Long, exercises: List<Exercise>): Long {
    val byId = exercises.associateBy { it.id }
    var target = exerciseId
    var hops = 0
    while (true) {
        val next = byId[target]?.mergedIntoId ?: return target
        hops += 1
        if (hops > 16) throw MergeChainTooDeep(exerciseId)
        target = next
    }
}

internal fun bindSwapRevision(request: ExerciseSwapRequest, dayRevisions: Map<String, Long>): ExerciseSwapRequest {
    if (request.expectedRevision != null) return request
    return request.copy(expectedRevision = dayRevisions[request.date] ?: 0L)
}

internal fun enqueueAlternatives(
    pending: List<SplitAlternative>,
    choices: List<SplitAlternative>,
): List<SplitAlternative> = (pending + choices).distinctBy { Triple(it.splitId, it.sourceId, it.alternativeId) }

internal fun planExerciseSwap(world: SwapWorld, request: ExerciseSwapRequest, ids: SwapIds = SwapIds()): SwapPlan {
    if (request.requestId.isBlank() || !isIsoDate(request.date)) throw SwapRejected("Invalid swap request")
    val existing = world.receipts.find { it.requestId == request.requestId }
    if (existing != null) {
        if (
            existing.date != request.date ||
            existing.sourceId != request.sourceId ||
            existing.targetId != request.targetId ||
            existing.splitId != request.splitId
        ) {
            throw SwapRejected("Request identity reused")
        }
        val code = if (existing.undone) "UNDONE" else "APPLIED"
        return SwapPlan(
            result = ExerciseSwapResult(true, code, existing.requestId, existing.sourceId, existing.targetId),
            world = world,
            change = null,
        )
    }
    val source = canonicalExerciseId(request.sourceId, world.exercises)
    val target = canonicalExerciseId(request.targetId, world.exercises)
    val targetRow = world.exercises.find { it.id == request.targetId }
    if (source != request.sourceId || target != request.targetId || source == target || targetRow == null) {
        return failed(world, "EXERCISE_CHANGED")
    }
    val currentRevision = world.dayRevisions[request.date] ?: 0L
    if (request.expectedRevision == null || request.expectedRevision != currentRevision) {
        return failed(world, "WORKOUT_CHANGED")
    }
    val dayCards = world.cards.filter { it.date == request.date }.sortedBy { it.position }
    val sourceCard = dayCards.find { it.exerciseId == source }
    val sourceSets = if (sourceCard == null) emptyList() else world.sets.filter { it.cardId == sourceCard.id }
    if (sourceCard == null || sourceSets.isEmpty()) return failed(world, "EXERCISE_CHANGED")
    if (request.splitId != null && !slotIsOpen(world, request.splitId)) return failed(world, "SPLIT_CHANGED")

    // A checked set stays on the source card, including a completed warm-up.
    val beforeSets = sourceSets.filter { !it.completed }.sortedWith(compareBy({ it.setNumber }, { it.id }))
    val deleteIds = beforeSets.map { it.id }.toSet()
    val remainingSets = world.sets.filter { it.id !in deleteIds }
    val sourceSurvives = remainingSets.any { it.cardId == sourceCard.id }
    val targetCard = dayCards.find { it.exerciseId == target }
    val targetExisted = targetCard != null
    var nextCards = dayCards.filter { sourceSurvives || it.id != sourceCard.id }
    if (sourceSurvives && sourceCard.displayMode != WorkoutCardDisplayMode.COMPACT) {
        nextCards = nextCards.map { card ->
            if (card.id == sourceCard.id) card.copy(displayMode = WorkoutCardDisplayMode.COMPACT) else card
        }
    }
    val targetCardId: String
    val insertedSets: List<WorkoutSet>
    if (targetCard == null) {
        targetCardId = ids.cardId()
        nextCards = nextCards + WorkoutExerciseCard(
            id = targetCardId,
            date = request.date,
            exerciseId = target,
            position = sourceCard.position,
            displayMode = WorkoutCardDisplayMode.EXPANDED,
            origin = WorkoutCardOrigin.MANUAL,
        )
        insertedSets = copiedSets(world, request.date, target, targetCardId, targetRow, ids)
    } else {
        targetCardId = targetCard.id
        if (targetCard.displayMode != WorkoutCardDisplayMode.EXPANDED) {
            nextCards = nextCards.map { card ->
                if (card.id == targetCardId) card.copy(displayMode = WorkoutCardDisplayMode.EXPANDED) else card
            }
        }
        insertedSets = emptyList()
    }
    val ordered = nextCards.sortedWith(
        compareBy<WorkoutExerciseCard>(
            { if (it.id == targetCardId) sourceCard.position else it.position },
            { if (it.id == targetCardId) 0 else 1 },
            { it.id },
        ),
    ).mapIndexed { index, card -> card.copy(position = index) }
    val finalCards = withRevisions(dayCards, ordered)
    val splitId = request.splitId
    var members = world.members
    val memberWrites = mutableListOf<MemberWrite>()
    var remember: SplitAlternative? = null
    if (splitId != null) {
        val sourceIsMember = members.any { it.splitId == splitId && it.exerciseId == source }
        val targetIsMember = members.any { it.splitId == splitId && it.exerciseId == target }
        // Both lifts already in the split are not one slot replacing the other.
        val replaces = !targetExisted && !(sourceIsMember && targetIsMember)
        val already = world.alternatives.any {
            it.splitId == splitId && it.sourceId == source && it.alternativeId == target
        }
        if (!already) remember = SplitAlternative(splitId, source, target, replaces)
        val (withSource, sourceWrite) = appendMember(members, splitId, source)
        val (withTarget, targetWrite) = appendMember(withSource, splitId, target)
        members = withTarget
        memberWrites += sourceWrite
        memberWrites += targetWrite
    }
    val afterRevision = currentRevision + 1
    val receipt = SwapReceipt(
        requestId = request.requestId,
        date = request.date,
        sourceId = request.sourceId,
        targetId = request.targetId,
        splitId = request.splitId,
        beforeSourceSets = beforeSets,
        beforeCards = dayCards,
        targetExisted = targetExisted,
        afterRevision = afterRevision,
        undone = false,
    )
    val change = SwapChange(
        date = request.date,
        deleteSetIds = deleteIds.toList(),
        finalCards = finalCards,
        insertSets = insertedSets,
        remember = remember,
        members = memberWrites,
        receipt = receipt,
        bump = true,
    )
    return SwapPlan(
        result = ExerciseSwapResult(true, "APPLIED", request.requestId, source, target),
        world = world.copy(
            cards = world.cards.filter { it.date != request.date } + finalCards,
            sets = remainingSets + insertedSets,
            members = members,
            alternatives = if (remember == null) world.alternatives else world.alternatives + remember,
            receipts = world.receipts + receipt,
            dayRevisions = world.dayRevisions + (request.date to afterRevision),
        ),
        change = change,
    )
}

internal fun planExerciseUndo(world: SwapWorld, requestId: String): SwapPlan {
    val receipt = world.receipts.find { it.requestId == requestId } ?: return failed(world, "NOT_FOUND")
    if (receipt.undone) {
        return SwapPlan(ExerciseSwapResult(ok = true, code = "UNDONE"), world, change = null)
    }
    val current = world.dayRevisions[receipt.date] ?: 0L
    if (current != receipt.afterRevision) return failed(world, "WORKOUT_CHANGED")
    val sourceCanonical = canonicalExerciseId(receipt.sourceId, world.exercises)
    val targetCanonical = canonicalExerciseId(receipt.targetId, world.exercises)
    if (sourceCanonical != receipt.sourceId || targetCanonical != receipt.targetId) {
        return failed(world, "EXERCISE_CHANGED")
    }
    val deleteIds = if (receipt.targetExisted) {
        emptyList()
    } else {
        world.sets.filter { it.date == receipt.date && it.exerciseId == receipt.targetId }.map { it.id }
    }
    val deleteSet = deleteIds.toSet()
    val remaining = world.sets.filter { it.id !in deleteSet }
    val day = world.cards.filter { it.date == receipt.date }
    var next = if (receipt.targetExisted) day else day.filter { it.exerciseId != receipt.targetId }
    val sourceSnap = receipt.beforeCards.first { it.exerciseId == receipt.sourceId }
    val existingSource = next.find { it.exerciseId == receipt.sourceId }
    val sourceCardId = if (existingSource == null) {
        next = next + sourceSnap.copy(rowRevision = 1, updatedAt = "")
        sourceSnap.id
    } else {
        next = next.map { card ->
            if (card.id != existingSource.id) card
            else card.copy(sourcePlanId = sourceSnap.sourcePlanId, createdAt = sourceSnap.createdAt)
        }
        existingSource.id
    }
    val snaps = receipt.beforeCards.associateBy { it.id }
    next = next.map { card ->
        val snap = snaps[card.id] ?: return@map card
        card.copy(position = snap.position, displayMode = snap.displayMode)
    }
    val finalCards = withRevisions(day, next).map { card ->
        if (day.any { it.id == card.id }) card else card.copy(rowRevision = 1)
    }
    val restored = receipt.beforeSourceSets.map { set ->
        set.copy(
            id = 0,
            exerciseId = receipt.sourceId,
            date = receipt.date,
            completed = false,
            cardId = sourceCardId,
            rowRevision = 1,
        )
    }
    return SwapPlan(
        result = ExerciseSwapResult(true, "UNDONE", receipt.requestId, receipt.sourceId, receipt.targetId),
        world = world.copy(
            cards = world.cards.filter { it.date != receipt.date } + finalCards,
            sets = remaining + restored,
            receipts = world.receipts.map { if (it.requestId == receipt.requestId) it.copy(undone = true) else it },
            dayRevisions = world.dayRevisions + (receipt.date to current + 1),
        ),
        change = SwapChange(
            date = receipt.date,
            deleteSetIds = deleteIds,
            finalCards = finalCards,
            insertSets = restored,
            markUndoneRequestId = receipt.requestId,
            bump = true,
        ),
    )
}

internal fun planRemember(world: SwapWorld, splitId: Long, sourceId: Long, alternativeId: Long): SwapPlan {
    if (!slotIsOpen(world, splitId)) throw SwapRejected("Split changed")
    val sourceCanonical = canonicalExerciseId(sourceId, world.exercises)
    val alternativeCanonical = canonicalExerciseId(alternativeId, world.exercises)
    val sourceExists = world.exercises.any { it.id == sourceId }
    val alternativeExists = world.exercises.any { it.id == alternativeId }
    if (
        sourceId == alternativeId ||
        sourceCanonical != sourceId ||
        alternativeCanonical != alternativeId ||
        !sourceExists ||
        !alternativeExists
    ) {
        throw SwapRejected("Exercise changed")
    }
    val sourceIsMember = world.members.any { it.splitId == splitId && it.exerciseId == sourceId }
    val alternativeIsMember = world.members.any { it.splitId == splitId && it.exerciseId == alternativeId }
    val replaces = !(sourceIsMember && alternativeIsMember)
    val already = world.alternatives.any {
        it.splitId == splitId && it.sourceId == sourceId && it.alternativeId == alternativeId
    }
    val remember = if (already) null else SplitAlternative(splitId, sourceId, alternativeId, replaces)
    val (withSource, sourceWrite) = appendMember(world.members, splitId, sourceId)
    val (withTarget, targetWrite) = appendMember(withSource, splitId, alternativeId)
    return SwapPlan(
        result = ExerciseSwapResult(ok = true, code = "APPLIED"),
        world = world.copy(
            members = withTarget,
            alternatives = if (remember == null) world.alternatives else world.alternatives + remember,
        ),
        change = SwapChange(
            date = "",
            remember = remember,
            members = listOf(sourceWrite, targetWrite),
        ),
    )
}

internal fun encodeSwapSets(sets: List<WorkoutSet>): String = swapJson.encodeToString(sets.map { it.toSnap() })

internal fun decodeSwapSets(json: String): List<WorkoutSet> =
    swapJson.decodeFromString<List<SwapSetSnap>>(json).map { it.toSet() }

internal fun encodeSwapCards(cards: List<WorkoutExerciseCard>): String = swapJson.encodeToString(cards.map { it.toSnap() })

internal fun decodeSwapCards(json: String): List<WorkoutExerciseCard> =
    swapJson.decodeFromString<List<SwapCardSnap>>(json).map { it.toCard() }

internal fun SwapReceipt.toRow() = ExerciseSwapReceiptRow(
    requestId = requestId,
    date = date,
    sourceId = sourceId,
    targetId = targetId,
    splitId = splitId,
    beforeSourceSetsJson = encodeSwapSets(beforeSourceSets),
    beforeCardsJson = encodeSwapCards(beforeCards),
    targetExisted = targetExisted,
    afterRevision = afterRevision,
    undone = undone,
)

internal fun ExerciseSwapReceiptRow.toSwapReceipt() = SwapReceipt(
    requestId = requestId,
    date = date,
    sourceId = sourceId,
    targetId = targetId,
    splitId = splitId,
    beforeSourceSets = decodeSwapSets(beforeSourceSetsJson),
    beforeCards = decodeSwapCards(beforeCardsJson),
    targetExisted = targetExisted,
    afterRevision = afterRevision,
    undone = undone,
)

private fun failed(world: SwapWorld, code: String) = SwapPlan(
    result = ExerciseSwapResult(ok = false, code = code),
    world = world,
    change = null,
)

private fun slotIsOpen(world: SwapWorld, splitId: Long): Boolean {
    val slot = world.slots.find { it.id == splitId } ?: return false
    return slot.archivedAt == null && slot.suppressedAt == null
}

private fun copiedSets(
    world: SwapWorld,
    date: String,
    targetId: Long,
    cardId: String,
    target: Exercise,
    ids: SwapIds,
): List<WorkoutSet> {
    val historyDate = world.sets
        .filter { it.exerciseId == targetId && it.date < date && it.completed }
        .maxOfOrNull { it.date }
    if (historyDate == null) {
        return listOf(
            WorkoutSet(
                exerciseId = targetId,
                date = date,
                setNumber = 1,
                isBodyweight = target.isBodyweight,
                completed = false,
                syncId = ids.syncId(),
                cardId = cardId,
                rowRevision = 1,
            ),
        )
    }
    return world.sets
        .filter { it.exerciseId == targetId && it.date == historyDate && it.completed }
        .sortedWith(compareBy({ it.setNumber }, { it.id }))
        .mapIndexed { index, row ->
            row.copy(
                id = 0,
                date = date,
                setNumber = index + 1,
                completed = false,
                syncId = ids.syncId(),
                cardId = cardId,
                rowRevision = 1,
            )
        }
}

private fun appendMember(
    members: List<SplitExercise>,
    splitId: Long,
    exerciseId: Long,
): Pair<List<SplitExercise>, MemberWrite> {
    val existing = members.find { it.splitId == splitId && it.exerciseId == exerciseId }
    if (existing != null) {
        val updated = members.map { row ->
            if (row.id == existing.id && row.splitId == splitId) row.copy(userAdded = true) else row
        }
        return updated to MemberWrite.Mark(existing.id)
    }
    val order = (members.filter { it.splitId == splitId }.maxOfOrNull { it.orderIndex } ?: -1) + 1
    val added = SplitExercise(splitId = splitId, exerciseId = exerciseId, orderIndex = order, userAdded = true)
    return members + added to MemberWrite.Add(splitId, exerciseId, order)
}

private fun withRevisions(
    original: List<WorkoutExerciseCard>,
    next: List<WorkoutExerciseCard>,
): List<WorkoutExerciseCard> {
    val originalById = original.associateBy { it.id }
    return next.map { card ->
        val previous = originalById[card.id] ?: return@map card
        if (sameCardShape(previous, card)) previous else card.copy(rowRevision = previous.rowRevision + 1)
    }
}

private fun sameCardShape(left: WorkoutExerciseCard, right: WorkoutExerciseCard): Boolean =
    left.position == right.position &&
        left.displayMode == right.displayMode &&
        left.origin == right.origin &&
        left.sourcePlanId == right.sourcePlanId &&
        left.createdAt == right.createdAt &&
        left.exerciseId == right.exerciseId &&
        left.date == right.date

@Serializable
private data class SwapSetSnap(
    val id: Long,
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

@Serializable
private data class SwapCardSnap(
    val id: String,
    val date: String,
    val exerciseId: Long,
    val position: Int,
    val displayMode: String,
    val origin: String,
    val sourcePlanId: String? = null,
    val rowRevision: Long = 1,
    val createdAt: String = "",
    val updatedAt: String = "",
)

private fun WorkoutSet.toSnap() = SwapSetSnap(
    id = id,
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed,
    restTimeSeconds = restTimeSeconds,
    syncId = syncId,
    rowRevision = rowRevision,
    cardId = cardId,
    isWarmup = isWarmup,
)

private fun SwapSetSnap.toSet() = WorkoutSet(
    id = id,
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed,
    restTimeSeconds = restTimeSeconds,
    syncId = syncId,
    rowRevision = rowRevision,
    cardId = cardId,
    isWarmup = isWarmup,
)

private fun WorkoutExerciseCard.toSnap() = SwapCardSnap(
    id = id,
    date = date,
    exerciseId = exerciseId,
    position = position,
    displayMode = displayMode.name,
    origin = origin.name,
    sourcePlanId = sourcePlanId,
    rowRevision = rowRevision,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun SwapCardSnap.toCard() = WorkoutExerciseCard(
    id = id,
    date = date,
    exerciseId = exerciseId,
    position = position,
    displayMode = WorkoutCardDisplayMode.entries.first { it.name == displayMode },
    origin = WorkoutCardOrigin.entries.first { it.name == origin },
    sourcePlanId = sourcePlanId,
    rowRevision = rowRevision,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
