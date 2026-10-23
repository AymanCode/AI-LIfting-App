package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.splits.LearnedFixtures.asOf
import com.ayman.ecolift.data.splits.LearnedFixtures.day
import com.ayman.ecolift.data.splits.LearnedFixtures.history
import com.ayman.ecolift.data.splits.LearnedFixtures.set
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LearnedHistoryTest {
    private val tunables = LearnedTunables()

    /**
     * A [LearnedWindows] with each start [memory]/[recent]/[cluster] days before [asOfDay]: for
     * tests that exercise [heldLifts] or [clusterWindowDays] directly, independent of how
     * [learnedWindows] derives a start from the user's own training days ([LearnedWindowsTest]
     * covers that derivation).
     */
    private fun windows(asOfDay: Int, memory: Int = 84, recent: Int = 35, cluster: Int = 56) =
        LearnedWindows(memoryStart = asOfDay - memory, recentStart = asOfDay - recent, clusterStart = asOfDay - cluster)

    @Test
    fun onlyCheckedWorkingSetsMakeATrainingDay() {
        val h = history(
            listOf(
                set("2026-09-01", 1),
                set("2026-09-01", 2, completed = false),
                set("2026-09-02", 3, warmup = true),
            ),
        )
        assertEquals(mapOf("2026-09-01" to setOf(1L)), h.presence)
    }

    @Test
    fun keeperAndDonorOnOneDayCountOnce() {
        val h = history(day("2026-09-01", 1, 2), resolve = { if (it == 2L) 1L else it })
        assertEquals(setOf(1L), h.lifts("2026-09-01"))
    }

    @Test
    fun cardPositionIsTheLowestCardOfTheKeeperThatDay() {
        val h = history(
            day("2026-09-01", 1, 2, 3),
            cards = LearnedFixtures.cards("2026-09-01", 3, 2, 1),
            resolve = { if (it == 3L) 1L else it },
        )
        assertEquals(0, h.position("2026-09-01", 1))
        assertEquals(1, h.position("2026-09-01", 2))
        assertEquals(Int.MAX_VALUE, h.position("2026-09-02", 1))
    }

    @Test
    fun resolverFollowsChainsAndStopsAtAMissingOrCyclicLink() {
        val graph = mapOf<Long, Long?>(1L to null, 2L to 1L, 3L to 2L, 4L to 99L, 5L to 6L, 6L to 5L)
        val resolve = canonicalResolver(graph)
        assertEquals(1L, resolve(3))
        assertEquals(4L, resolve(4))
        assertEquals(6L, resolve(5))
        assertEquals(42L, resolve(42))
    }

    @Test
    fun heldNeedsTwoIsoWeeksInMemoryAndOneRecentSession() {
        val d = asOf("2026-09-28")
        val sets = day("2026-09-07", 1) + day("2026-09-21", 1) + // weeks 37 and 39, recent
            day("2026-09-01", 2) + day("2026-09-03", 2) + // one ISO week only
            day("2026-08-24", 3) + day("2026-09-07", 3) // two weeks, nothing inside the recent window
        assertEquals(listOf(1L), heldLifts(history(sets), d, windows(d, recent = 14)))
    }

    @Test
    fun memoryWindowIsHalfOpen() {
        // The memory window's start counts, one day earlier does not, and D itself is never learned from.
        val d = asOf("2026-09-28")
        val counts = day("2026-08-17", 1) + day("2026-09-20", 1)
        val tooOld = day("2026-08-16", 2) + day("2026-09-20", 2)
        val today = day("2026-09-20", 3) + day("2026-09-28", 3)
        val w = LearnedWindows(memoryStart = epochDay("2026-08-17"), recentStart = epochDay("2026-09-14"), clusterStart = epochDay("2026-08-17"))
        assertEquals(listOf(1L), heldLifts(history(counts + tooOld + today), d, w))
    }

    @Test
    fun recentWindowIsHalfOpen() {
        // The recent window's start counts as recent, one day earlier does not.
        val d = asOf("2026-09-28")
        val recentSets = day("2026-09-01", 1) + day("2026-09-14", 1)
        val staleSets = day("2026-09-01", 2) + day("2026-09-13", 2)
        val w = LearnedWindows(memoryStart = epochDay("2026-08-01"), recentStart = epochDay("2026-09-14"), clusterStart = epochDay("2026-08-01"))
        assertEquals(listOf(1L), heldLifts(history(recentSets + staleSets), d, w))
    }

    @Test
    fun aSundayAndTheFollowingMondayAreTwoWeeks() {
        val d = asOf("2026-09-28")
        val sundayMonday = day("2026-09-20", 1) + day("2026-09-21", 1)
        val mondaySunday = day("2026-09-21", 2) + day("2026-09-27", 2)
        assertEquals(listOf(1L), heldLifts(history(sundayMonday + mondaySunday), d, windows(d)))
        assertTrue(isoWeekIndex(epochDay("2026-09-20")) != isoWeekIndex(epochDay("2026-09-21")))
        assertEquals(isoWeekIndex(epochDay("2026-09-21")), isoWeekIndex(epochDay("2026-09-27")))
    }

    @Test
    fun everydayIsAtLeastThreeQuartersOfClusterWindowDays() {
        // Four training days, all inside the cluster window: lift 1 on 3 of them (0.75), lift 2 on 2 (0.5).
        val d = asOf("2026-09-28")
        val sets = day("2026-09-07", 1, 2) + day("2026-09-14", 1, 2) + day("2026-09-21", 1) + day("2026-09-22", 3)
        val h = history(sets)
        val days = clusterWindowDays(h, d, windows(d))
        assertEquals(4, days.size)
        assertEquals(setOf(1L), everydayLifts(listOf(1L, 2L), h, days, tunables))
    }

    @Test
    fun lastWorkingDateIgnoresTheAsOfDay() {
        val h = history(day("2026-09-20", 1) + day("2026-09-28", 1) + day("2026-09-21", 2))
        assertEquals(mapOf(1L to "2026-09-20", 2L to "2026-09-21"), lastWorkingDates(h, asOf("2026-09-28")))
    }
}
