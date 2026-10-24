package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.splits.LearnedFixtures.asOf
import com.ayman.ecolift.data.splits.LearnedFixtures.history
import com.ayman.ecolift.data.splits.WorkedExample.BENCH_PRESS
import com.ayman.ecolift.data.splits.WorkedExample.CHEST_ARMS
import com.ayman.ecolift.data.splits.WorkedExample.INCLINE_BENCH
import com.ayman.ecolift.data.splits.WorkedExample.INCLINE_DUMBELL_HAMMER
import com.ayman.ecolift.data.splits.WorkedExample.LEGS_EXTENSION
import com.ayman.ecolift.data.splits.WorkedExample.LEGS_HACK
import com.ayman.ecolift.data.splits.WorkedExample.SKULL_CRUSHER
import com.ayman.ecolift.data.splits.WorkedExample.STANDING_CALF_RAISE_MACHINE
import com.ayman.ecolift.data.splits.WorkedExample.UPPER
import com.ayman.ecolift.data.splits.WorkedExample.key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The spec's worked example, step by step, before slot matching (every group has a new id). */
class LearnedWorkedExampleTest {
    private val tunables = LearnedTunables()
    private val d = asOf(WorkedExample.AS_OF)
    private val h = history(WorkedExample.sets, WorkedExample.cards)
    private val windows = learnedWindows(h, d, tunables)
    private val learned = learnClusters(h, d, windows, tunables)
    private val units = learned.clusters.mapIndexed { index, cluster ->
        GroupUnit(cluster.key, -(index + 1L), null, cluster.members, cluster.coOccurrenceDays, isCluster = true, suppressed = false, colorIndex = index)
    }
    private val attribution = attributeDays(h, units, learned.everyday, d, windows)
    private val groups = finishGroups(units, attribution, h, d, tunables).associateBy { it.key }

    private fun days(members: List<Long>) = groups.getValue(key(members)).sessions.map { it.substring(5) }

    @Test
    fun fourGroupsWithTheirCoOccurrenceDays() {
        assertEquals(
            setOf(UPPER, CHEST_ARMS, LEGS_HACK, LEGS_EXTENSION).map { it.sorted() }.toSet(),
            learned.clusters.map { it.members }.toSet(),
        )
        val coDays = learned.clusters.associate { it.members to it.coOccurrenceDays.map { d -> d.substring(5) } }
        assertEquals(listOf("09-09", "09-15", "09-20", "09-26"), coDays[UPPER.sorted()])
        assertEquals(listOf("09-07", "09-12", "09-17", "09-23"), coDays[CHEST_ARMS.sorted()])
        assertEquals(listOf("09-14", "09-21", "09-27"), coDays[LEGS_HACK.sorted()])
        assertEquals(listOf("09-10", "09-18"), coDays[LEGS_EXTENSION.sorted()])
        assertTrue(learned.everyday.isEmpty())
    }

    @Test
    fun shuffledSetsGiveTheSameGroupsAndAttribution() {
        val shuffled = history(WorkedExample.sets.shuffled(kotlin.random.Random(7)), WorkedExample.cards.shuffled(kotlin.random.Random(3)))
        val shuffledWindows = learnedWindows(shuffled, d, tunables)
        assertEquals(windows, shuffledWindows)
        val again = learnClusters(shuffled, d, shuffledWindows, tunables)
        assertEquals(learned, again)
        assertEquals(attribution, attributeDays(shuffled, units, again.everyday, d, shuffledWindows))
    }

    @Test
    fun sessionsFollowComputedAttribution() {
        assertEquals(listOf("09-09", "09-15", "09-20", "09-26"), days(UPPER))
        assertEquals(listOf("09-01", "09-03", "09-05", "09-07", "09-12", "09-17", "09-23"), days(CHEST_ARMS))
        assertEquals(listOf("09-02", "09-14", "09-21", "09-27"), days(LEGS_HACK))
        assertEquals(listOf("09-10", "09-18", "09-24"), days(LEGS_EXTENSION))
    }

    @Test
    fun theLegDayStampedUpper1BelongsToTheHackSquatGroup() {
        assertEquals(key(LEGS_HACK), attribution.getValue("2026-09-27").groupKey)
    }

    @Test
    fun swapsAndOneOffsAreTails() {
        assertEquals(DayAttribution("2026-09-20", key(UPPER), listOf(BENCH_PRESS, INCLINE_DUMBELL_HAMMER)), attribution["2026-09-20"])
        assertEquals(key(CHEST_ARMS), attribution.getValue("2026-09-23").groupKey)
        assertEquals(listOf(STANDING_CALF_RAISE_MACHINE), attribution.getValue("2026-09-23").tail)
        assertEquals(key(CHEST_ARMS), attribution.getValue("2026-09-03").groupKey)
        assertEquals(listOf(SKULL_CRUSHER), attribution.getValue("2026-09-03").tail)
    }

    @Test
    fun onlySoloAndUnheldLiftsLeaveADayUnattributed() {
        assertNull(attribution.getValue("2026-08-20").groupKey)
        assertNull(attribution.getValue("2026-08-24").groupKey)
    }

    @Test
    fun inclineBenchIsFadedAndNothingElseIs() {
        assertEquals(setOf(INCLINE_BENCH), groups.getValue(key(UPPER)).fadedMembers)
        assertEquals(INCLINE_BENCH, groups.getValue(key(UPPER)).ticketOrder.last())
        listOf(CHEST_ARMS, LEGS_HACK, LEGS_EXTENSION).forEach { assertEquals(emptySet(), groups.getValue(key(it)).fadedMembers) }
    }

    @Test
    fun chestArmsIsDueOnSeptember28() {
        assertEquals(key(CHEST_ARMS), dueOrder(groups.values.toList(), d, windows).first().key)
    }

    @Test
    fun soloHeldLiftsAreHeldButInNoGroup() {
        val members = learned.clusters.flatMap { it.members }.toSet()
        listOf(BENCH_PRESS, WorkedExample.PEC_DECK, INCLINE_DUMBELL_HAMMER, WorkedExample.HIP_ABDUCTION).forEach {
            assertEquals(true, it in learned.held && it !in members, "lift $it")
        }
        listOf(SKULL_CRUSHER, WorkedExample.ONE_LEGGED_EXTENSION, WorkedExample.CALVE_RAISE_LEG_PRESS, WorkedExample.HIP_ABDUCTOR, WorkedExample.PECTORAL_FLY)
            .forEach { assertEquals(false, it in learned.held, "lift $it") }
    }
}
