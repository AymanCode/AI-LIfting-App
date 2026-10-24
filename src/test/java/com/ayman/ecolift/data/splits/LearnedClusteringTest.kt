package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.splits.LearnedFixtures.asOf
import com.ayman.ecolift.data.splits.LearnedFixtures.day
import com.ayman.ecolift.data.splits.LearnedFixtures.history
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LearnedClusteringTest {
    private val tunables = LearnedTunables()
    private val d = "2026-09-28"

    @Test
    fun affinityIsTheOverlapCoefficient() {
        // Calves (2): 3 leg days plus 2 upper days. Squat (1): the 3 leg days. Overlap = 3 / min(3, 5) = 1.0.
        val sets = day("2026-09-02", 1, 2) + day("2026-09-09", 1, 2) + day("2026-09-16", 1, 2) +
            day("2026-09-04", 2, 3) + day("2026-09-11", 2, 3)
        val h = history(sets)
        val days = clusterWindowDays(h, asOf(d), learnedWindows(h, asOf(d), tunables))
        assertEquals(1.0, affinity(1, 2, days, h))
        assertEquals(0.0, affinity(1, 3, days, h))
        assertEquals(1.0, affinity(3, 2, days, h))
    }

    @Test
    fun calvesDoneOnUpperDaysStayInTheLegGroup() {
        val legs = listOf(1L, 2L, 3L)
        val upper = listOf(4L, 5L, 6L)
        val sets = listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22").flatMap { day(it, *legs.toLongArray()) } +
            listOf("2026-09-03", "2026-09-10", "2026-09-17", "2026-09-24").flatMap { day(it, *upper.toLongArray()) } +
            day("2026-09-24", 3)
        val h = history(sets)
        val clusters = learnClusters(h, asOf(d), learnedWindows(h, asOf(d), tunables), tunables).clusters
        assertEquals(listOf(legs, upper), clusters.map { it.members })
    }

    @Test
    fun aMeanOfExactlyTheThresholdDoesNotMerge() {
        // 1 and 2 share one of their two days: affinity 0.5, not strictly above MERGE_AFFINITY.
        val sets = day("2026-09-07", 1, 2) + day("2026-09-14", 1) + day("2026-09-21", 2)
        val h = history(sets)
        val days = clusterWindowDays(h, asOf(d), learnedWindows(h, asOf(d), tunables))
        assertEquals(listOf(listOf(1L), listOf(2L)), clusterLifts(listOf(1L, 2L), days, h, tunables))
    }

    @Test
    fun mergeTiesGoToCoverageThenToTheSmallerIdList() {
        val a = MergeCandidate(0, 1, 0.75, coverage = 4, unionIds = listOf(3L, 4L))
        val b = MergeCandidate(0, 2, 0.75, coverage = 5, unionIds = listOf(5L, 6L))
        val c = MergeCandidate(1, 2, 0.75, coverage = 5, unionIds = listOf(1L, 9L))
        val higher = MergeCandidate(2, 3, 0.9, coverage = 1, unionIds = listOf(8L, 9L))
        assertEquals(c, pickMerge(listOf(a, b, c)))
        assertEquals(higher, pickMerge(listOf(a, b, c, higher)))
        assertEquals(null, pickMerge(emptyList()))
    }

    @Test
    fun chainTieIsBrokenByIdsNotByInputOrder() {
        // 2 covers both 1 and 3 (affinity 1.0 each, same coverage); 1 and 3 never meet.
        val sets = day("2026-09-07", 1, 2) + day("2026-09-14", 1, 2) + day("2026-09-21", 2, 3) + day("2026-09-22", 2, 3)
        val h = history(sets)
        val days = clusterWindowDays(h, asOf(d), learnedWindows(h, asOf(d), tunables))
        val expected = listOf(listOf(1L, 2L), listOf(3L))
        assertEquals(expected, clusterLifts(listOf(3L, 2L, 1L), days, h, tunables))
        assertEquals(expected, clusterLifts(listOf(1L, 2L, 3L), days, h, tunables))
    }

    @Test
    fun oneCoOccurrenceDayIsNeverAGroup() {
        // Bench (1) and the hammer curl (2): affinity 1.0 but together on one day only.
        val sets = day("2026-08-24", 2) + day("2026-09-02", 1) + day("2026-09-10", 1) + day("2026-09-20", 1, 2)
        val h = history(sets)
        val learned = learnClusters(h, asOf(d), learnedWindows(h, asOf(d), tunables), tunables)
        assertEquals(listOf(1L, 2L), learned.held)
        assertTrue(learned.clusters.isEmpty())
    }

    @Test
    fun aLiftTrainedEveryDayBesideTwoAlternatingRoutinesIsEverydayAndSolo() {
        // U = 1,2,3 and V = 4,5,6 alternate daily for two weeks; 9 is trained every day.
        val dates = (1..14).map { "2026-09-${(it + 10).toString().padStart(2, '0')}" }
        val sets = dates.flatMapIndexed { index, date ->
            if (index % 2 == 0) day(date, 1, 2, 3, 9) else day(date, 4, 5, 6, 9)
        }
        val h = history(sets)
        val learned = learnClusters(h, asOf(d), learnedWindows(h, asOf(d), tunables), tunables)
        assertEquals(setOf(9L), learned.everyday)
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L, 5L, 6L)), learned.clusters.map { it.members })
    }

    @Test
    fun theSameSixLiftsEverySessionAreOneGroup() {
        val sets = listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22").flatMap { day(it, 1, 2, 3, 4, 5, 6) }
        val h = history(sets)
        val learned = learnClusters(h, asOf(d), learnedWindows(h, asOf(d), tunables), tunables)
        assertTrue(learned.everyday.isEmpty())
        assertEquals(listOf(listOf(1L, 2L, 3L, 4L, 5L, 6L)), learned.clusters.map { it.members })
    }

    @Test
    fun shuffledInputLearnsTheSamePartition() {
        val sets = listOf("2026-09-01", "2026-09-07", "2026-09-13", "2026-09-19", "2026-09-25").flatMap { day(it, 1, 2, 3) } +
            listOf("2026-09-03", "2026-09-09", "2026-09-15", "2026-09-21", "2026-09-27").flatMap { day(it, 4, 5, 6) } +
            listOf("2026-09-05", "2026-09-11", "2026-09-17", "2026-09-23").flatMap { day(it, 7, 8) }
        val h = history(sets)
        val base = learnClusters(h, asOf(d), learnedWindows(h, asOf(d), tunables), tunables)
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L, 5L, 6L), listOf(7L, 8L)), base.clusters.map { it.members })
        repeat(5) { seed ->
            val shuffled = history(sets.shuffled(kotlin.random.Random(seed)))
            assertEquals(base, learnClusters(shuffled, asOf(d), learnedWindows(shuffled, asOf(d), tunables), tunables))
        }
    }
}
