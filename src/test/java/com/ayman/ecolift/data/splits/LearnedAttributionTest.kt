package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.splits.LearnedFixtures.asOf
import com.ayman.ecolift.data.splits.LearnedFixtures.cards
import com.ayman.ecolift.data.splits.LearnedFixtures.day
import com.ayman.ecolift.data.splits.LearnedFixtures.history
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnedAttributionTest {
    private val tunables = LearnedTunables()

    /** A windows value whose recent-window start is [recentDaysBack] days before D, for the
     * [dueOrder] tie-break tests below, which are independent of how a window's start is derived
     * from the user's own training days ([LearnedWindowsTest] covers that derivation). */
    private fun dueWindows(asOfDay: Int, recentDaysBack: Int = 14) =
        LearnedWindows(memoryStart = asOfDay, recentStart = asOfDay - recentDaysBack, clusterStart = asOfDay)

    private fun unit(key: String, slotId: Long, vararg members: Long) =
        GroupUnit(key, slotId, null, members.toList().sorted(), emptyList(), isCluster = true, suppressed = false, colorIndex = 0)

    private fun group(
        key: String,
        slotId: Long,
        members: Int,
        sessions: List<String>,
        fresh: Boolean = false,
        dormant: Boolean = false,
        createdAt: Long = 0,
    ) = LearnedGroup(
        key = key, slotId = slotId, name = "", colorIndex = 0,
        members = (1..members).map { it.toLong() }, ticketOrder = (1..members).map { it.toLong() },
        coOccurrenceDays = emptyList(), sessions = sessions, fadedMembers = emptySet(), isCluster = !fresh && !dormant,
        learned = !fresh && !dormant, createdAt = createdAt, isFresh = fresh, isDormant = dormant, suppressed = false,
    )

    private val upper = unit("a", -1, 1, 2, 3)
    private val lower = unit("b", -2, 4, 5)

    @Test
    fun mostVotesWins() {
        val h = history(day("2026-09-20", 1, 2, 4))
        assertEquals("a", ownerOf("2026-09-20", h.lifts("2026-09-20"), listOf(upper, lower), emptySet(), h)?.key)
    }

    @Test
    fun aTieGoesToTheLowestCardPosition() {
        val h = history(day("2026-09-20", 1, 4), cards = cards("2026-09-20", 4, 1))
        assertEquals("b", ownerOf("2026-09-20", h.lifts("2026-09-20"), listOf(upper, lower), emptySet(), h)?.key)
    }

    @Test
    fun withoutCardsATieGoesToTheLowestCanonicalId() {
        val h = history(day("2026-09-20", 1, 4))
        assertEquals("a", ownerOf("2026-09-20", h.lifts("2026-09-20"), listOf(lower, upper), emptySet(), h)?.key)
    }

    @Test
    fun zeroVotesLeavesTheDayUnattributed() {
        val h = history(day("2026-09-20", 9))
        assertNull(ownerOf("2026-09-20", h.lifts("2026-09-20"), listOf(upper, lower), emptySet(), h))
    }

    @Test
    fun everydayLiftsNeverVote() {
        val withDaily = unit("a", -1, 1, 9)
        val h = history(day("2026-09-20", 9, 4))
        assertEquals("b", ownerOf("2026-09-20", h.lifts("2026-09-20"), listOf(withDaily, lower), setOf(9L), h)?.key)
    }

    @Test
    fun tailIsEveryLiftThatIsNotAMember() {
        val h = history(day("2026-09-20", 1, 2, 7, 8))
        val d = asOf("2026-09-28")
        val attribution = attributeDays(h, listOf(upper, lower), emptySet(), d, learnedWindows(h, d, tunables))
        assertEquals(DayAttribution("2026-09-20", "a", listOf(7L, 8L)), attribution["2026-09-20"])
    }

    @Test
    fun todayIsAttributedButIsNeitherASessionNorFadingEvidence() {
        val h = history(day("2026-09-14", 1, 2, 3) + day("2026-09-21", 1, 2, 3) + day("2026-09-28", 2, 3))
        val d = asOf("2026-09-28")
        val attribution = attributeDays(h, listOf(upper), emptySet(), d, learnedWindows(h, d, tunables))
        assertEquals("a", attribution["2026-09-28"]?.groupKey)
        val finished = finishGroups(listOf(upper), attribution, h, d, tunables).single()
        assertEquals(listOf("2026-09-14", "2026-09-21"), finished.sessions)
        assertTrue(finished.fadedMembers.isEmpty())
    }

    @Test
    fun aMemberAbsentFromTheTwoMostRecentSessionsIsFaded() {
        val h = history(day("2026-09-07", 1, 2, 3) + day("2026-09-14", 1, 2) + day("2026-09-21", 1, 2))
        val d = asOf("2026-09-28")
        val attribution = attributeDays(h, listOf(upper), emptySet(), d, learnedWindows(h, d, tunables))
        val finished = finishGroups(listOf(upper), attribution, h, d, tunables).single()
        assertEquals(setOf(3L), finished.fadedMembers)
        assertEquals(listOf(1L, 2L, 3L), finished.ticketOrder)
    }

    @Test
    fun ticketOrderFollowsTheLastSessionsCards() {
        val h = history(
            day("2026-09-14", 1, 2, 3) + day("2026-09-21", 3, 1),
            cards = cards("2026-09-21", 3, 1),
        )
        val d = asOf("2026-09-28")
        val attribution = attributeDays(h, listOf(upper), emptySet(), d, learnedWindows(h, d, tunables))
        val finished = finishGroups(listOf(upper), attribution, h, d, tunables).single()
        assertEquals(listOf(3L, 1L, 2L), finished.ticketOrder)
    }

    @Test
    fun dueIsTheAliveGroupWhoseLastSessionIsOldest() {
        val d = asOf("2026-09-28")
        val order = dueOrder(
            listOf(
                group("recent", 1, 3, listOf("2026-09-26")),
                group("oldest-alive", 2, 3, listOf("2026-09-15")),
                group("stale", 3, 3, listOf("2026-09-01")),
            ),
            d, dueWindows(d),
        )
        assertEquals(listOf("oldest-alive", "recent", "stale"), order.map { it.key })
    }

    @Test
    fun onAHolidayEveryGroupCompetes() {
        val d = asOf("2026-09-28")
        val order = dueOrder(
            listOf(group("b", 1, 3, listOf("2026-09-05")), group("a", 2, 3, listOf("2026-09-01"))),
            d, dueWindows(d),
        )
        assertEquals(listOf("a", "b"), order.map { it.key })
    }

    @Test
    fun dueTiesGoToMoreMembersThenTheLowerSlotId() {
        val d = asOf("2026-09-28")
        val order = dueOrder(
            listOf(
                group("small", 1, 2, listOf("2026-09-20")),
                group("big-late", 5, 4, listOf("2026-09-20")),
                group("big-early", 4, 4, listOf("2026-09-20")),
            ),
            d, dueWindows(d),
        )
        assertEquals(listOf("big-early", "big-late", "small"), order.map { it.key })
    }

    @Test
    fun freshSeedsComeFirstAndDormantSeedsLast() {
        val d = asOf("2026-09-28")
        val order = dueOrder(
            listOf(
                group("alive", 1, 3, listOf("2026-09-20")),
                group("dormant", 2, 3, emptyList(), dormant = true),
                group("fresh-late", 9, 3, emptyList(), fresh = true, createdAt = 200),
                group("fresh-early", 8, 3, emptyList(), fresh = true, createdAt = 100),
            ),
            d, dueWindows(d),
        )
        assertEquals(listOf("fresh-early", "fresh-late", "alive", "dormant"), order.map { it.key })
    }
}
