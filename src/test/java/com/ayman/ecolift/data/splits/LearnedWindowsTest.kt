package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.splits.LearnedFixtures.asOf
import com.ayman.ecolift.data.splits.LearnedFixtures.day
import com.ayman.ecolift.data.splits.LearnedFixtures.history
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [learnedWindows]: a window's start is the epoch day of the kth most recent training day before
 * D, capped so it is never older than D − maxDays (spec "The model" and "Generality"). All three
 * windows (memory, recent, cluster) share the same rule, only their k and cap differ, so these
 * tests exercise the rule once with tunables sized for readable dates rather than the production
 * defaults.
 */
class LearnedWindowsTest {
    private fun testTunables(sessions: Int, maxDays: Int) = LearnedTunables(
        memorySessions = sessions, memoryMaxDays = maxDays,
        recentSessions = sessions, recentMaxDays = maxDays,
        clusterSessions = sessions, clusterMaxDays = maxDays,
    )

    @Test
    fun fewerThanKTrainingDaysUsesTheCalendarCap() {
        val d = asOf("2026-09-28")
        val tunables = testTunables(sessions = 5, maxDays = 30)
        // Only two training days before D exist, short of the five sessions asked for: the cap wins.
        val h = history(day("2026-09-01", 1) + day("2026-09-10", 1))
        val windows = learnedWindows(h, d, tunables)
        assertEquals(d - 30, windows.memoryStart)
        assertEquals(d - 30, windows.recentStart)
        assertEquals(d - 30, windows.clusterStart)
    }

    @Test
    fun exactlyKTrainingDaysStartsAtTheOldestOne() {
        val d = asOf("2026-09-28")
        val tunables = testTunables(sessions = 3, maxDays = 90)
        // Exactly three training days before D, all well inside the cap: the third most recent
        // (the oldest of the three) is the start.
        val h = history(listOf("2026-09-01", "2026-09-10", "2026-09-20").flatMap { day(it, 1) })
        val windows = learnedWindows(h, d, tunables)
        assertEquals(epochDay("2026-09-01"), windows.memoryStart)
    }

    @Test
    fun moreThanKTrainingDaysIgnoresTheOlderOnes() {
        val d = asOf("2026-09-28")
        val tunables = testTunables(sessions = 3, maxDays = 90)
        // A fourth, older training day exists, but only the last three sessions count.
        val h = history(listOf("2026-08-01", "2026-09-01", "2026-09-10", "2026-09-20").flatMap { day(it, 1) })
        val windows = learnedWindows(h, d, tunables)
        assertEquals(epochDay("2026-09-01"), windows.memoryStart)
    }

    @Test
    fun theCapWinsOverTheKthSessionForADailyLifterWhoseBurstIsStale() {
        val d = asOf("2026-09-28")
        val tunables = testTunables(sessions = 5, maxDays = 20)
        // Five consecutive daily training days (Aug 1-5), but the whole burst ended long before
        // the 20-day cap (Sep 8): the cap wins over the fifth-most-recent (oldest) session, so the
        // window holds none of the lifter's actual training days.
        val dates = listOf("2026-08-01", "2026-08-02", "2026-08-03", "2026-08-04", "2026-08-05")
        val h = history(dates.flatMap { day(it, 1) })
        val windows = learnedWindows(h, d, tunables)
        assertEquals(epochDay("2026-09-08"), windows.memoryStart)
        assertTrue(dates.all { epochDay(it) < windows.memoryStart })
    }

    @Test
    fun noTrainingDaysUsesTheCalendarCap() {
        val d = asOf("2026-09-28")
        val tunables = LearnedTunables()
        val windows = learnedWindows(history(emptyList()), d, tunables)
        assertEquals(d - tunables.memoryMaxDays, windows.memoryStart)
        assertEquals(d - tunables.recentMaxDays, windows.recentStart)
        assertEquals(d - tunables.clusterMaxDays, windows.clusterStart)
    }

    @Test
    fun sameTrainingDaysGiveTheSameWindowsRegardlessOfInputOrder() {
        val d = asOf("2026-09-28")
        val tunables = LearnedTunables()
        val dates = listOf("2026-08-01", "2026-08-20", "2026-09-05", "2026-09-14", "2026-09-21", "2026-09-27")
        val ordered = learnedWindows(history(dates.flatMap { day(it, 1) }), d, tunables)
        repeat(3) { seed ->
            val shuffled = history(dates.shuffled(kotlin.random.Random(seed)).flatMap { day(it, 1) })
            assertEquals(ordered, learnedWindows(shuffled, d, tunables))
        }
    }
}
