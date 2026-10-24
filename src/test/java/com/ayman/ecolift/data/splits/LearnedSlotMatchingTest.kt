package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.splits.LearnedFixtures.day
import com.ayman.ecolift.data.splits.LearnedFixtures.members
import com.ayman.ecolift.data.splits.LearnedFixtures.millis
import com.ayman.ecolift.data.splits.LearnedFixtures.slot
import com.ayman.ecolift.data.splits.WorkedExample.CHEST_ARMS
import com.ayman.ecolift.data.splits.WorkedExample.LEGS_EXTENSION
import com.ayman.ecolift.data.splits.WorkedExample.LEGS_HACK
import com.ayman.ecolift.data.splits.WorkedExample.UPPER
import com.ayman.ecolift.data.splits.WorkedExample.UPPER_1_SLOT
import com.ayman.ecolift.data.splits.WorkedExample.key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnedSlotMatchingTest {
    private val first = LearnedSplitsEngine.compute(WorkedExample.inputs())

    /** Writes a pass's actions into the inputs the way the RPC and the day queue would. */
    private fun persist(inputs: LearnedInputs, model: LearnedModel, firstNewId: Long = 100): LearnedInputs {
        var nextId = firstNewId
        var slots = inputs.slots
        var rows = inputs.slotMembers
        val assigned = HashMap<Long, Long>()
        for (upsert in model.actions.slotUpserts) {
            val id = upsert.slotId ?: nextId++.also { assigned[upsert.modelSlotId] = it }
            val existing = slots.firstOrNull { it.id == id }
            slots = if (existing == null) {
                slots + CycleSlot(
                    id = id, name = upsert.name, orderIndex = (slots.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                    learned = upsert.learned, createdAt = millis(inputs.asOf.toString()),
                    anchorUpdatedAt = millis(inputs.asOf.toString()), colorIndex = (slots.maxOfOrNull { it.colorIndex } ?: -1) + 1,
                )
            } else {
                slots.map { if (it.id == id) it.copy(learned = upsert.learned, anchorUpdatedAt = millis(inputs.asOf.toString())) else it }
            }
            rows = rows.filterNot { it.splitId == id } + upsert.memberIds.mapIndexed { index, lift ->
                SplitExercise(id = id * 1000 + index, splitId = id, exerciseId = lift, orderIndex = index)
            }
        }
        slots = slots.map { if (it.id in model.actions.slotArchives) it.copy(archivedAt = 1L) else it }
        var days = inputs.workoutDays
        for (write in model.actions.dayWrites) {
            days = days.filterNot { it.date == write.date } +
                WorkoutDay(write.date, write.slotType, write.occurrence, null, write.slotId)
        }
        return inputs.copy(slots = slots, slotMembers = rows, workoutDays = days)
    }

    @Test
    fun upper1MatchesItsClusterAndBecomesLearnedOnTheFirstPass() {
        val upper = first.group(key(UPPER))!!
        assertEquals(UPPER_1_SLOT, upper.slotId)
        assertEquals("Upper 1", upper.name)
        assertTrue(upper.learned)
        assertEquals(
            SlotUpsert(UPPER_1_SLOT, UPPER_1_SLOT, "Upper 1", learned = true, memberIds = upper.ticketOrder),
            first.actions.slotUpserts.single { it.slotId == UPPER_1_SLOT },
        )
    }

    @Test
    fun newGroupsGetDeterministicNegativeIdsAndTheNextColours() {
        assertEquals(-1L, first.group(key(CHEST_ARMS))!!.slotId)
        assertEquals(-2L, first.group(key(LEGS_HACK))!!.slotId)
        assertEquals(-3L, first.group(key(LEGS_EXTENSION))!!.slotId)
        assertEquals(listOf(1, 2, 3), listOf(CHEST_ARMS, LEGS_HACK, LEGS_EXTENSION).map { first.group(key(it))!!.colorIndex })
        assertEquals(listOf(-1L, -2L, -3L, UPPER_1_SLOT), first.actions.slotUpserts.map { it.modelSlotId })
        assertTrue(first.groups.none { it.isUnnamed && it.slotId > 0 })
    }

    @Test
    fun theDueGroupAndRolesMatchTheWorkedExample() {
        assertEquals(key(CHEST_ARMS), first.dueGroupKey)
        assertEquals(LiftRole.SOLO, first.roles[WorkedExample.BENCH_PRESS])
        assertEquals(LiftRole.MEMBER, first.roles[WorkedExample.INCLINE_BENCH])
        assertNull(first.roles[WorkedExample.SKULL_CRUSHER])
    }

    @Test
    fun daysWaitForTheirSlotAndEarlierDaysRenumberLaterOnes() {
        assertEquals(
            listOf(
                DayWrite("2026-09-09", UPPER_1_SLOT, 0, 1),
                DayWrite("2026-09-15", UPPER_1_SLOT, 0, 2),
                DayWrite("2026-09-20", UPPER_1_SLOT, 0, 3),
                DayWrite("2026-09-26", UPPER_1_SLOT, 0, 4),
            ),
            first.actions.dayWrites,
        )
    }

    @Test
    fun theSamePersistedStateGivesTheSameIdentitiesAndNoFurtherWrites() {
        val second = LearnedSplitsEngine.compute(persist(WorkedExample.inputs(), first))
        assertEquals(listOf(100L, 101L, 102L), listOf(CHEST_ARMS, LEGS_HACK, LEGS_EXTENSION).map { second.group(key(it))!!.slotId })
        assertTrue(second.actions.slotUpserts.isEmpty())
        assertEquals(DayWrite("2026-09-27", 101L, 2, 4), second.actions.dayWrites.single { it.date == "2026-09-27" })
        val third = LearnedSplitsEngine.compute(persist(persist(WorkedExample.inputs(), first), second))
        assertTrue(third.actions.isEmpty, "third pass wrote ${third.actions}")
        assertEquals(second.groups.map { it.key to it.slotId }, third.groups.map { it.key to it.slotId })
    }

    @Test
    fun aSeedCountsOnlySessionsSinceItWasCreated() {
        val createdLate = WorkedExample.inputs().copy(slots = listOf(WorkedExample.upper1.copy(createdAt = millis("2026-09-21"))))
        val late = LearnedSplitsEngine.compute(createdLate).group(key(UPPER))!!
        assertFalse(late.learned)
        assertTrue(LearnedSplitsEngine.compute(createdLate).actions.slotUpserts.none { it.slotId == UPPER_1_SLOT })
        val createdEarlier = WorkedExample.inputs().copy(slots = listOf(WorkedExample.upper1.copy(createdAt = millis("2026-09-20"))))
        assertTrue(LearnedSplitsEngine.compute(createdEarlier).group(key(UPPER))!!.learned)
    }

    @Test
    fun aFreshSeedIsDueFirstUntilItsFirstSessionThenDormantAfterFourteenDays() {
        val push = slot(50, name = "Push", createdAt = millis("2026-09-25"), colorIndex = 4)
        val withSeed = WorkedExample.inputs().let {
            it.copy(slots = it.slots + push, slotMembers = it.slotMembers + members(50, 900, 901))
        }
        val fresh = LearnedSplitsEngine.compute(withSeed)
        assertEquals("s:50", fresh.dueGroupKey)
        assertTrue(fresh.group("s:50")!!.isFresh)

        val later = LearnedSplitsEngine.compute(withSeed.copy(asOf = kotlinx.datetime.LocalDate.parse("2026-10-12")))
        assertTrue(later.group("s:50")!!.isDormant)
        assertEquals("s:50", later.dueOrder.last())

        val trained = LearnedSplitsEngine.compute(withSeed.copy(sets = withSeed.sets + day("2026-09-25", 900, 901)))
        val group = trained.group("s:50")!!
        assertEquals(listOf("2026-09-25"), group.sessions)
        assertFalse(group.isFresh)
        assertEquals(key(CHEST_ARMS), trained.dueGroupKey)
    }

    @Test
    fun severalFreshSeedsOrderByCreationThenId() {
        val a = slot(60, name = "B seed", createdAt = millis("2026-09-26"))
        val b = slot(61, name = "A seed", createdAt = millis("2026-09-24"))
        val c = slot(59, name = "C seed", createdAt = millis("2026-09-26"))
        val inputs = WorkedExample.inputs().let { it.copy(slots = it.slots + a + b + c) }
        assertEquals(listOf("s:61", "s:59", "s:60"), LearnedSplitsEngine.compute(inputs).dueOrder.take(3))
    }

    @Test
    fun theHigherJaccardSlotWinsAndThenTheLowerSlotId() {
        val clusters = listOf(LiftCluster(listOf(1L, 2L, 3L, 4L), listOf("2026-09-01", "2026-09-08")))
        val partial = slot(10, name = "Partial")
        val exact = slot(11, name = "Exact")
        val twin = slot(12, name = "Twin")
        val anchors = mapOf(10L to listOf(1L, 2L, 3L), 11L to listOf(1L, 2L, 3L, 4L), 12L to listOf(1L, 2L, 3L, 4L))
        val matching = matchSlots(clusters, listOf(twin, partial, exact), anchors, LearnedTunables())
        assertEquals(11L, matching.units.single { it.isCluster }.slotId)
    }

    @Test
    fun duplicateLearnedSlotsConvergeOnTheNamedOneAndArchiveTheUnnamed() {
        val clusters = listOf(LiftCluster(listOf(1L, 2L, 3L), listOf("2026-09-01", "2026-09-08")))
        val anchors = mapOf(20L to listOf(1L, 2L, 3L), 21L to listOf(1L, 2L, 3L), 22L to listOf(1L, 2L, 3L))
        val unnamed = slot(20, learned = true)
        val named = slot(21, name = "Pull", learned = true)
        val unnamedToo = slot(22, learned = true)
        val matching = matchSlots(clusters, listOf(unnamed, named, unnamedToo), anchors, LearnedTunables())
        assertEquals(21L, matching.units.single { it.isCluster }.slotId)
        assertEquals(listOf(20L, 22L), matching.duplicateArchives)

        val bothNamed = matchSlots(clusters, listOf(slot(20, "Pull", learned = true), slot(21, "Pull again", learned = true)), anchors, LearnedTunables())
        assertEquals(20L, bothNamed.units.single { it.isCluster }.slotId)
        assertTrue(bothNamed.duplicateArchives.isEmpty())
        assertTrue(bothNamed.units.none { it.slotId == 21L })
    }

    @Test
    fun aRetiredGroupIsNotRecreated() {
        val retired = WorkedExample.upper1.copy(learned = true, suppressedAt = millis("2026-09-27"))
        val model = LearnedSplitsEngine.compute(WorkedExample.inputs().copy(slots = listOf(retired)))
        val upper = model.group(key(UPPER))!!
        assertTrue(upper.suppressed)
        assertTrue(model.visibleGroups.none { it.key == key(UPPER) })
        assertTrue(model.actions.slotUpserts.none { it.modelSlotId == upper.slotId })
        assertEquals(LiftRole.SOLO, model.roles[WorkedExample.LAT_PULLDOWN])
        assertEquals(DayWrite("2026-09-20", null, null, null), model.actions.dayWrites.single { it.date == "2026-09-20" })
    }

    @Test
    fun anUnnamedLearnedSlotNoGroupMatchesIsArchivedAfterTwentyEightDays() {
        val idle = slot(30, learned = true, anchorUpdatedAt = millis("2026-08-20"))
        val recent = slot(31, learned = true, anchorUpdatedAt = millis("2026-09-05"))
        val named = slot(32, name = "Old", learned = true, anchorUpdatedAt = millis("2026-08-20"))
        val inputs = WorkedExample.inputs().let {
            it.copy(
                slots = it.slots + idle + recent + named,
                slotMembers = it.slotMembers + members(30, 900, 901) + members(31, 902, 903) + members(32, 904, 905),
            )
        }
        assertEquals(listOf(30L), LearnedSplitsEngine.compute(inputs).actions.slotArchives)
    }

    @Test
    fun aGroupThatDriftsRefreshesItsAnchor() {
        val learnedUpper = WorkedExample.upper1.copy(learned = true)
        val drifted = WorkedExample.upper1Members + members(UPPER_1_SLOT, 999).map { it.copy(id = 99_999, orderIndex = 5) }
        val model = LearnedSplitsEngine.compute(WorkedExample.inputs().copy(slots = listOf(learnedUpper), slotMembers = drifted))
        val upsert = model.actions.slotUpserts.single { it.slotId == UPPER_1_SLOT }
        assertEquals(UPPER.toSet(), upsert.memberIds.toSet())
    }

    @Test
    fun userAddedRowsSurviveAClusterPassAndTheNextPassWritesNothing() {
        val added = members(UPPER_1_SLOT, 999, 997).mapIndexed { index, row ->
            row.copy(id = 99_999L + index, orderIndex = 6 + index, userAdded = true)
        }
        val ordinaryDrift = members(UPPER_1_SLOT, 998).map { it.copy(orderIndex = 8) }
        val inputs = WorkedExample.inputs().copy(
            slots = listOf(WorkedExample.upper1.copy(learned = true)),
            slotMembers = WorkedExample.upper1Members + added + ordinaryDrift,
        )
        val pass = LearnedSplitsEngine.compute(inputs)
        val group = pass.group(key(UPPER))!!
        assertTrue(999L !in group.members && 997L !in group.members)
        val write = pass.actions.slotUpserts.single { it.slotId == UPPER_1_SLOT }
        assertEquals(group.ticketOrder + listOf(999L, 997L), write.memberIds)
        val saved = write.memberIds.mapIndexed { index, id ->
            SplitExercise(index.toLong(), UPPER_1_SLOT, id, index, userAdded = id == 999L || id == 997L)
        }
        val again = LearnedSplitsEngine.compute(inputs.copy(slotMembers = saved))
        assertTrue(again.actions.slotUpserts.none { it.slotId == UPPER_1_SLOT })
        assertEquals((group.members + listOf(999L, 997L)).toSet(), anchorsOf(saved) { it }[UPPER_1_SLOT]!!.toSet())
    }

    @Test
    fun daysOnOrBeforeTheLastArchiveAreFrozen() {
        val inputs = WorkedExample.inputs().copy(archivedCycleEndDates = listOf("2026-09-15"), cycleStartDate = "2026-09-16")
        val model = LearnedSplitsEngine.compute(inputs)
        assertTrue(model.actions.dayWrites.none { it.date <= "2026-09-15" })
        assertEquals(DayWrite("2026-09-26", UPPER_1_SLOT, 0, 2), model.actions.dayWrites.single { it.date == "2026-09-26" })
        assertTrue(model.actions.dayWrites.none { it.date == "2026-09-20" })
        assertTrue(model.isFrozen("2026-09-15"))
        assertNull(model.displaySlotId("2026-09-09", null))
        assertEquals(UPPER_1_SLOT, model.displaySlotId("2026-09-26", null))
    }

    @Test
    fun aSingleTrainingDayLearnsNothing() {
        val model = LearnedSplitsEngine.compute(LearnedFixtures.inputs("2026-09-28", day("2026-09-27", 1, 2, 3)))
        assertTrue(model.groups.isEmpty())
        assertNull(model.dueGroupKey)
        assertTrue(model.actions.isEmpty)
    }
}
