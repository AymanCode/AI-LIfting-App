package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.SplitAlternative
import com.ayman.ecolift.web.supabase.ExerciseSwapRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSwapRulesTest {
    @Test
    fun swapKeepsCompletedSetsAndPlacesTheNewCardAtTheSourcePosition() {
        val plan = swap(world(sets = mixedSets))

        assertTrue(plan.result.ok)
        assertEquals("APPLIED", plan.result.code)
        assertEquals("req-1", plan.result.requestId)
        assertEquals(1L, plan.result.sourceId)
        assertEquals(3L, plan.result.targetId)
        val dayCards = plan.world.cards.filter { it.date == DAY }.sortedBy { it.position }
        assertEquals(listOf("card-row", "c-bench", "c-squat"), dayCards.map { it.id })
        assertEquals(listOf(0, 1, 2), dayCards.map { it.position })
        assertEquals(WorkoutCardDisplayMode.EXPANDED, dayCards[0].displayMode)
        assertEquals(WorkoutCardOrigin.MANUAL, dayCards[0].origin)
        assertEquals(3L, dayCards[0].exerciseId)
        assertEquals(WorkoutCardDisplayMode.COMPACT, dayCards.first { it.id == "c-bench" }.displayMode)
        assertEquals("plan-1", dayCards.first { it.id == "c-bench" }.sourcePlanId)
        val kept = plan.world.sets.filter { it.exerciseId == 1L }.sortedBy { it.setNumber }
        assertEquals(listOf(1, 3), kept.map { it.setNumber })
        assertEquals(listOf(true, true), kept.map { it.completed })
        assertEquals(listOf(false, true), kept.map { it.isWarmup })
        val created = plan.world.sets.single { it.exerciseId == 3L }
        assertEquals(1, created.setNumber)
        assertFalse(created.completed)
        assertEquals("sync-new", created.syncId)
        assertEquals("card-row", created.cardId)
        assertNull(created.weightLbs)
        assertFalse(created.isBodyweight)
        assertEquals(1L, plan.world.dayRevisions[DAY])
        val receipt = plan.world.receipts.single()
        assertFalse(receipt.targetExisted)
        assertEquals(listOf(11L), receipt.beforeSourceSets.map { it.id })
        assertEquals(listOf("c-bench", "c-squat"), receipt.beforeCards.map { it.id })
        assertTrue(plan.world.alternatives.isEmpty())
        assertTrue(plan.world.members.isEmpty())
        assertEquals(receipt, receipt.toRow().toSwapReceipt())
    }

    @Test
    fun undoRestoresTheIncompleteSetAndTheSourceCard() {
        val swapped = swap(world(sets = mixedSets))
        val undone = planExerciseUndo(swapped.world, "req-1")

        assertEquals("UNDONE", undone.result.code)
        assertEquals("req-1", undone.result.requestId)
        assertEquals(1L, undone.result.sourceId)
        assertEquals(3L, undone.result.targetId)
        val dayCards = undone.world.cards.filter { it.date == DAY }.sortedBy { it.position }
        assertEquals(listOf("c-bench", "c-squat"), dayCards.map { it.id })
        assertEquals(listOf(0, 1), dayCards.map { it.position })
        assertEquals(WorkoutCardDisplayMode.EXPANDED, dayCards[0].displayMode)
        assertEquals("plan-1", dayCards[0].sourcePlanId)
        assertEquals("2026-06-01T00:00:00Z", dayCards[0].createdAt)
        assertFalse(undone.world.cards.any { it.exerciseId == 3L })
        val source = undone.world.sets.filter { it.exerciseId == 1L }.sortedBy { it.setNumber }
        assertEquals(listOf(1, 2, 3), source.map { it.setNumber })
        assertEquals(listOf(true, false, true), source.map { it.completed })
        assertEquals("sync-11", source[1].syncId)
        assertEquals("c-bench", source[1].cardId)
        assertEquals(100, source[1].weightLbs)
        assertFalse(undone.world.sets.any { it.exerciseId == 3L })
        assertEquals(2L, undone.world.dayRevisions[DAY])
        assertTrue(undone.world.receipts.single().undone)
    }

    @Test
    fun swapDeletesTheSourceCardWhenEverySetIsIncompleteAndUndoRecreatesItsId() {
        val swapped = swap(world(sets = listOf(benchSet(11, 1, false))))
        assertFalse(swapped.world.cards.any { it.id == "c-bench" })

        val undone = planExerciseUndo(swapped.world, "req-1")
        val bench = undone.world.cards.first { it.id == "c-bench" }
        assertEquals(0, bench.position)
        assertEquals(WorkoutCardDisplayMode.EXPANDED, bench.displayMode)
        assertEquals(WorkoutCardOrigin.MANUAL, bench.origin)
        assertEquals("plan-1", bench.sourcePlanId)
        assertEquals("2026-06-01T00:00:00Z", bench.createdAt)
        val restored = undone.world.sets.single { it.exerciseId == 1L }
        assertEquals("sync-11", restored.syncId)
        assertEquals(1, restored.setNumber)
        assertFalse(restored.completed)
        assertFalse(undone.world.sets.any { it.exerciseId == 3L })
        assertFalse(undone.world.cards.any { it.exerciseId == 3L })
    }

    @Test
    fun nullRevisionIsWorkoutChangedUntilTheRepositoryBindsIt() {
        val unbound = swap(world(), request(revision = null))
        assertEquals("WORKOUT_CHANGED", unbound.result.code)
        assertFalse(unbound.result.ok)
        assertNull(unbound.result.requestId)

        val bound = bindSwapRevision(request(revision = null), mapOf(DAY to 0L))
        assertEquals(0L, bound.expectedRevision)
        assertEquals("APPLIED", swap(world(), bound).result.code)
        assertEquals(4L, bindSwapRevision(request(revision = null), mapOf(DAY to 4L)).expectedRevision)
        assertEquals(2L, bindSwapRevision(request(revision = 2L), mapOf(DAY to 4L)).expectedRevision)
    }

    @Test
    fun aSecondSwapWithTheOldRevisionIsWorkoutChanged() {
        val first = swap(world())
        val second = swap(first.world, request(id = "req-2"))

        assertEquals("WORKOUT_CHANGED", second.result.code)
        assertNull(second.result.requestId)
        assertEquals(first.world.sets, second.world.sets)
        assertEquals(first.world.cards, second.world.cards)
    }

    @Test
    fun theSameRequestIsAppliedAgainWithoutASecondMutation() {
        val first = swap(world())
        val again = swap(first.world)

        assertEquals("APPLIED", again.result.code)
        assertEquals(first.world.sets, again.world.sets)
        assertEquals(first.world.cards, again.world.cards)
        assertEquals(1L, again.world.dayRevisions[DAY])
    }

    @Test
    fun theSameRequestAfterUndoReportsUndone() {
        val undone = planExerciseUndo(swap(world()).world, "req-1")
        val again = swap(undone.world)

        assertEquals("UNDONE", again.result.code)
        assertEquals("req-1", again.result.requestId)
        assertEquals(1L, again.result.sourceId)
        assertEquals(undone.world.sets, again.world.sets)
        assertEquals(undone.world.cards, again.world.cards)
    }

    @Test
    fun aReusedRequestIdWithADifferentTargetThrows() {
        val swapped = swap(world())
        val error = assertThrows(SwapRejected::class.java) {
            swap(swapped.world, request(target = 2))
        }
        assertEquals("Request identity reused", error.message)
    }

    @Test
    fun aBlankRequestOrAnImpossibleDateThrows() {
        val blank = assertThrows(SwapRejected::class.java) {
            swap(world(), request().copy(requestId = " "))
        }
        val impossible = assertThrows(SwapRejected::class.java) {
            swap(world(), request().copy(date = "2026-02-31"))
        }
        assertEquals("Invalid swap request", blank.message)
        assertEquals("Invalid swap request", impossible.message)
        assertTrue(isIsoDate("2024-02-29"))
        assertTrue(isIsoDate(DAY))
        assertFalse(isIsoDate("2023-02-29"))
        assertFalse(isIsoDate("2026-13-01"))
        assertFalse(isIsoDate("2026-1-01"))
    }

    @Test
    fun mergeChainOfSixteenResolvesAndSeventeenThrows() {
        assertEquals(17L, canonicalExerciseId(1, mergeChain(16)))
        assertThrows(MergeChainTooDeep::class.java) { canonicalExerciseId(1, mergeChain(17)) }
        assertEquals(42L, canonicalExerciseId(42, emptyList()))
    }

    @Test
    fun aMissingCardEmptyCardOrNonCanonicalExerciseIsExerciseChanged() {
        assertEquals("EXERCISE_CHANGED", swap(world(cards = listOf(squatCard))).result.code)
        assertEquals(
            "EXERCISE_CHANGED",
            swap(world(sets = listOf(benchSet(10, 1, true).copy(cardId = "other")))).result.code,
        )
        assertEquals(
            "EXERCISE_CHANGED",
            swap(world(exercises = listOf(bench.copy(mergedIntoId = 2), squat, row))).result.code,
        )
        assertEquals("EXERCISE_CHANGED", swap(world(exercises = listOf(bench, squat))).result.code)
        assertEquals("EXERCISE_CHANGED", swap(world(), request(target = 1)).result.code)
        assertNull(swap(world(cards = listOf(squatCard))).result.requestId)
    }

    @Test
    fun aMissingArchivedOrSuppressedSlotIsSplitChanged() {
        val archived = CycleSlot(id = 7, name = "Push", archivedAt = 5)
        val suppressed = CycleSlot(id = 8, name = "Pull", suppressedAt = 5)
        assertEquals("SPLIT_CHANGED", swap(world(), request(split = 9)).result.code)
        assertEquals("SPLIT_CHANGED", swap(world(slots = listOf(archived)), request(split = 7)).result.code)
        assertEquals("SPLIT_CHANGED", swap(world(slots = listOf(suppressed)), request(split = 8)).result.code)
        assertNull(swap(world(), request(split = 9)).result.requestId)
    }

    @Test
    fun bothMembersAreRememberedWithoutReplacingTheSlot() {
        val members = listOf(
            SplitExercise(id = 4, splitId = 7, exerciseId = 1, orderIndex = 0),
            SplitExercise(id = 5, splitId = 7, exerciseId = 3, orderIndex = 1),
        )
        val plan = swap(world(slots = listOf(openSlot), members = members), request(split = 7))

        assertEquals(false, plan.world.alternatives.single().replacesSlot)
        assertEquals(listOf(0, 1), plan.world.members.map { it.orderIndex })
        assertEquals(listOf(1L, 3L), plan.world.members.map { it.exerciseId })
        assertTrue(plan.world.members.all { it.userAdded })
    }

    @Test
    fun anExistingTargetDoesNotCopyHistoryAndDoesNotReplaceTheSlot() {
        val rowCard = WorkoutExerciseCard(
            id = "c-row",
            date = DAY,
            exerciseId = 3,
            position = 2,
            displayMode = WorkoutCardDisplayMode.COMPACT,
            origin = WorkoutCardOrigin.SPLIT,
        )
        val history = WorkoutSet(
            id = 30,
            exerciseId = 3,
            date = "2026-05-01",
            setNumber = 1,
            completed = true,
            weightLbs = 40,
            cardId = "old",
        )
        val plan = swap(
            world(
                cards = listOf(benchCard, squatCard, rowCard),
                sets = listOf(benchSet(10, 1, true), benchSet(11, 2, false), history),
                slots = listOf(openSlot),
            ),
            request(split = 7),
        )

        assertTrue(plan.world.receipts.single().targetExisted)
        assertFalse(plan.world.sets.any { it.date == DAY && it.exerciseId == 3L })
        assertEquals(history, plan.world.sets.first { it.id == 30L })
        assertEquals(0, plan.world.cards.first { it.id == "c-row" }.position)
        assertEquals(WorkoutCardDisplayMode.EXPANDED, plan.world.cards.first { it.id == "c-row" }.displayMode)
        assertEquals(false, plan.world.alternatives.single().replacesSlot)
    }

    @Test
    fun historyCopiesCompletedSetsFromTheLatestEarlierDate() {
        val older = WorkoutSet(
            id = 21,
            exerciseId = 3,
            date = "2026-04-01",
            setNumber = 1,
            completed = true,
            weightLbs = 10,
            reps = 3,
            cardId = "old",
        )
        val kept = WorkoutSet(
            id = 22,
            exerciseId = 3,
            date = "2026-05-01",
            setNumber = 1,
            completed = true,
            weightLbs = 40,
            reps = 8,
            restTimeSeconds = 60,
            cardId = "may",
        )
        val skipped = WorkoutSet(
            id = 23,
            exerciseId = 3,
            date = "2026-05-01",
            setNumber = 2,
            completed = false,
            weightLbs = 50,
            cardId = "may",
        )
        val warmup = WorkoutSet(
            id = 24,
            exerciseId = 3,
            date = "2026-05-01",
            setNumber = 3,
            completed = true,
            weightLbs = 20,
            reps = 12,
            isWarmup = true,
            cardId = "may",
        )
        var generated = 0
        val plan = swap(
            world(sets = listOf(benchSet(10, 1, true), benchSet(11, 2, false), older, kept, skipped, warmup)),
            ids = SwapIds(cardId = { "card-row" }, syncId = { "sync-${generated++}" }),
        )
        val copied = plan.world.sets.filter { it.date == DAY && it.exerciseId == 3L }.sortedBy { it.setNumber }

        assertEquals(listOf(1, 2), copied.map { it.setNumber })
        assertEquals(listOf(40, 20), copied.map { it.weightLbs })
        assertEquals(listOf(8, 12), copied.map { it.reps })
        assertEquals(listOf(false, true), copied.map { it.isWarmup })
        assertEquals(listOf(60, null), copied.map { it.restTimeSeconds })
        assertEquals(listOf("sync-0", "sync-1"), copied.map { it.syncId })
        assertTrue(copied.all { !it.completed && it.cardId == "card-row" })
        assertEquals(older, plan.world.sets.first { it.id == 21L })
        assertEquals(skipped, plan.world.sets.first { it.id == 23L })
    }

    @Test
    fun noHistoryInsertsOneSetUsingTheExerciseBodyweightFlag() {
        val plan = swap(world(exercises = listOf(bench, squat, row.copy(isBodyweight = true))))
        val created = plan.world.sets.single { it.exerciseId == 3L }

        assertEquals(1, created.setNumber)
        assertTrue(created.isBodyweight)
        assertFalse(created.completed)
        assertNull(created.weightLbs)
        assertNull(created.reps)
    }

    @Test
    fun undoOfAnUnknownRequestIsNotFound() {
        val plan = planExerciseUndo(world(), "missing")
        assertEquals("NOT_FOUND", plan.result.code)
        assertFalse(plan.result.ok)
        assertNull(plan.result.requestId)
    }

    @Test
    fun undoAfterALaterRevisionIsWorkoutChanged() {
        val swapped = swap(world())
        val moved = swapped.world.copy(dayRevisions = mapOf(DAY to 5L))
        val plan = planExerciseUndo(moved, "req-1")

        assertEquals("WORKOUT_CHANGED", plan.result.code)
        assertEquals(moved.sets, plan.world.sets)
        assertFalse(plan.world.receipts.single().undone)
    }

    @Test
    fun aSecondUndoStaysUndoneEvenWhenTheRevisionMoved() {
        val undone = planExerciseUndo(swap(world()).world, "req-1")
        val tweaked = undone.world.copy(dayRevisions = mapOf(DAY to 99L))
        val again = planExerciseUndo(tweaked, "req-1")

        assertEquals("UNDONE", again.result.code)
        assertNull(again.result.requestId)
        assertEquals(99L, again.world.dayRevisions[DAY])
        assertEquals(tweaked.sets, again.world.sets)
    }

    @Test
    fun undoLeavesTheRememberedAlternativeAndTheAppendedMembers() {
        val swapped = swap(world(slots = listOf(openSlot)), request(split = 7))
        val undone = planExerciseUndo(swapped.world, "req-1")

        assertEquals(swapped.world.alternatives, undone.world.alternatives)
        assertEquals(swapped.world.members, undone.world.members)
        assertEquals(true, undone.world.alternatives.single().replacesSlot)
    }

    @Test
    fun rememberRejectsAClosedSlotOrAChangedExercise() {
        val closed = openSlot.copy(suppressedAt = 1)
        val archived = openSlot.copy(archivedAt = 1)
        assertEquals("Split changed", assertThrows(SwapRejected::class.java) {
            planRemember(world(slots = listOf(closed)), 7, 1, 3)
        }.message)
        assertEquals("Split changed", assertThrows(SwapRejected::class.java) {
            planRemember(world(slots = listOf(archived)), 7, 1, 3)
        }.message)
        assertEquals("Split changed", assertThrows(SwapRejected::class.java) {
            planRemember(world(), 7, 1, 3)
        }.message)
        assertEquals("Exercise changed", assertThrows(SwapRejected::class.java) {
            planRemember(world(slots = listOf(openSlot)), 7, 1, 1)
        }.message)
        assertEquals("Exercise changed", assertThrows(SwapRejected::class.java) {
            planRemember(
                world(slots = listOf(openSlot), exercises = listOf(bench.copy(mergedIntoId = 2), squat, row)),
                7,
                1,
                3,
            )
        }.message)
        assertEquals("Exercise changed", assertThrows(SwapRejected::class.java) {
            planRemember(world(slots = listOf(openSlot), exercises = listOf(bench, squat)), 7, 1, 3)
        }.message)
    }

    @Test
    fun rememberOnConflictKeepsTheStoredReplacesFlag() {
        val existing = SplitAlternative(splitId = 7, sourceId = 1, alternativeId = 3, replacesSlot = true)
        val members = listOf(
            SplitExercise(id = 4, splitId = 7, exerciseId = 1, orderIndex = 0, userAdded = true),
            SplitExercise(id = 5, splitId = 7, exerciseId = 3, orderIndex = 2),
        )
        val plan = planRemember(
            world(slots = listOf(openSlot), alternatives = listOf(existing), members = members),
            7,
            1,
            3,
        )

        assertEquals(listOf(existing), plan.world.alternatives)
        assertEquals(listOf(0, 2), plan.world.members.map { it.orderIndex })
        assertTrue(plan.world.members.all { it.userAdded })

        val fresh = planRemember(world(slots = listOf(openSlot)), 7, 1, 3)
        assertEquals(true, fresh.world.alternatives.single().replacesSlot)
        assertEquals(listOf(1L, 3L), fresh.world.members.map { it.exerciseId })
        assertEquals(listOf(0, 1), fresh.world.members.map { it.orderIndex })
        assertTrue(fresh.world.members.all { it.userAdded })
    }

    @Test
    fun snapshotJsonRoundTripsNulls() {
        val set = WorkoutSet(
            id = 4,
            exerciseId = 3,
            date = DAY,
            setNumber = 2,
            weightLbs = null,
            reps = 8,
            isWarmup = true,
            syncId = "s",
            cardId = "c",
        )
        val card = benchCard.copy(sourcePlanId = null)
        assertEquals(listOf(set), decodeSwapSets(encodeSwapSets(listOf(set))))
        assertEquals(listOf(card), decodeSwapCards(encodeSwapCards(listOf(card))))
    }

    @Test
    fun enqueueKeepsTheFirstChoiceForATriple() {
        val first = SplitAlternative(1, 2, 3, replacesSlot = true)
        val repeat = SplitAlternative(1, 2, 3, replacesSlot = false)
        val extra = SplitAlternative(1, 4, 5, replacesSlot = true)
        assertEquals(listOf(first, extra), enqueueAlternatives(listOf(first), listOf(repeat, extra)))
    }

    @Test
    fun swapLeavesAnotherDateUntouched() {
        val otherCard = squatCard.copy(id = "c-other", date = "2026-05-02", position = 0, exerciseId = 2)
        val otherSet = benchSet(80, 1, true).copy(date = "2026-05-02", cardId = "c-other", exerciseId = 2)
        val start = world(
            cards = listOf(benchCard, squatCard, otherCard),
            sets = listOf(benchSet(10, 1, true), benchSet(11, 2, false), otherSet),
        )
        val plan = swap(start)

        assertEquals(otherCard, plan.world.cards.first { it.id == "c-other" })
        assertEquals(otherSet, plan.world.sets.first { it.id == 80L })
        assertFalse(plan.world.dayRevisions.containsKey("2026-05-02"))
        assertEquals(1L, plan.world.dayRevisions[DAY])
    }
}

private const val DAY = "2026-06-01"

private val bench = Exercise(id = 1, name = "Bench")
private val squat = Exercise(id = 2, name = "Squat")
private val row = Exercise(id = 3, name = "Row")
private val openSlot = CycleSlot(id = 7, name = "Push")

private val benchCard = WorkoutExerciseCard(
    id = "c-bench",
    date = DAY,
    exerciseId = 1,
    position = 0,
    displayMode = WorkoutCardDisplayMode.EXPANDED,
    origin = WorkoutCardOrigin.MANUAL,
    sourcePlanId = "plan-1",
    createdAt = "2026-06-01T00:00:00Z",
)

private val squatCard = WorkoutExerciseCard(
    id = "c-squat",
    date = DAY,
    exerciseId = 2,
    position = 1,
    displayMode = WorkoutCardDisplayMode.EXPANDED,
    origin = WorkoutCardOrigin.SPLIT,
)

private fun benchSet(id: Long, number: Int, completed: Boolean, warmup: Boolean = false) = WorkoutSet(
    id = id,
    exerciseId = 1,
    date = DAY,
    setNumber = number,
    weightLbs = 100,
    reps = 5,
    completed = completed,
    syncId = "sync-$id",
    cardId = "c-bench",
    restTimeSeconds = 90,
    isWarmup = warmup,
)

private val mixedSets = listOf(
    benchSet(10, 1, completed = true),
    benchSet(11, 2, completed = false),
    benchSet(12, 3, completed = true, warmup = true),
)

private fun world(
    exercises: List<Exercise> = listOf(bench, squat, row),
    cards: List<WorkoutExerciseCard> = listOf(benchCard, squatCard),
    sets: List<WorkoutSet> = listOf(benchSet(10, 1, true), benchSet(11, 2, false)),
    slots: List<CycleSlot> = emptyList(),
    members: List<SplitExercise> = emptyList(),
    alternatives: List<SplitAlternative> = emptyList(),
    receipts: List<SwapReceipt> = emptyList(),
    revisions: Map<String, Long> = mapOf(DAY to 0L),
) = SwapWorld(
    exercises = exercises,
    cards = cards,
    sets = sets,
    slots = slots,
    members = members,
    alternatives = alternatives,
    receipts = receipts,
    dayRevisions = revisions,
)

private fun request(
    id: String = "req-1",
    source: Long = 1,
    target: Long = 3,
    split: Long? = null,
    revision: Long? = 0,
) = ExerciseSwapRequest(id, DAY, source, target, split, revision)

private fun swap(
    world: SwapWorld,
    request: ExerciseSwapRequest = request(),
    ids: SwapIds = SwapIds(cardId = { "card-row" }, syncId = { "sync-new" }),
) = planExerciseSwap(world, request, ids)

private fun mergeChain(links: Int): List<Exercise> =
    (1..links).map { id -> Exercise(id = id.toLong(), name = "E$id", mergedIntoId = id + 1L) } +
        Exercise(id = (links + 1).toLong(), name = "End")
