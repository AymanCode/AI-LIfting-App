package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.splits.LearnedFixtures.day
import com.ayman.ecolift.data.splits.LearnedFixtures.inputs
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Spec "Testing": one fixture per training-frequency archetype, each with a fixed as-of day,
 * asserting held lifts, the groups, one attribution and the due group. These are what the
 * tempo-relative windows exist for: a routine defined by the user's own sessions, not by how
 * often they train (spec "Generality").
 */
class LearnedArchetypesTest {
    /** [count] dates starting at [start], each gap cycling through [gaps] days after the last. */
    private fun spacedDates(start: String, count: Int, gaps: List<Int>): List<String> {
        var day = epochDay(start)
        val out = ArrayList<String>(count)
        out += LocalDate.fromEpochDays(day.toLong()).toString()
        for (i in 1 until count) {
            day += gaps[(i - 1) % gaps.size]
            out += LocalDate.fromEpochDays(day.toLong()).toString()
        }
        return out
    }

    private fun daysAfter(date: String, days: Int): String =
        LocalDate.fromEpochDays((epochDay(date) + days).toLong()).toString()

    @Test
    fun fullBodySameSixLiftsThreeTimesAWeekIsOneGroupWithNoEverydayLift() {
        // The everyday rerun (spec "Edge cases"): every lift is on every training day, but with one
        // group after clustering the everyday exclusion never fires.
        val lifts = listOf(201L, 202L, 203L, 204L, 205L, 206L)
        val dates = spacedDates("2026-06-01", 18, listOf(2, 2, 3)) // three sessions a week for six weeks
        val sets = dates.flatMap { day(it, *lifts.toLongArray()) }
        val model = LearnedSplitsEngine.compute(inputs(daysAfter(dates.last(), 3), sets))

        lifts.forEach { assertEquals(LiftRole.MEMBER, model.roles[it], "lift $it should be a member, not everyday") }
        assertEquals(1, model.visibleGroups.size)
        val group = model.visibleGroups.single()
        assertEquals(lifts, group.members)
        assertEquals(group.key, model.attribution.getValue(dates.last()).groupKey)
        assertEquals(group.key, model.dueGroupKey)
    }

    @Test
    fun pushPullLegsSixDaysAWeekGivesThreeGroupsAllHeldDueOldestTrained() {
        val push = listOf(301L, 302L, 303L)
        val pull = listOf(311L, 312L, 313L)
        val legs = listOf(321L, 322L, 323L)
        val pattern = listOf(push, pull, legs, push, pull, legs, null) // six days a week, one rest day
        val start = epochDay("2026-05-04")
        val schedule = (0 until 4 * pattern.size).mapNotNull { offset ->
            val lifts = pattern[offset % pattern.size] ?: return@mapNotNull null
            LocalDate.fromEpochDays((start + offset).toLong()).toString() to lifts
        }
        val sets = schedule.flatMap { (date, lifts) -> day(date, *lifts.toLongArray()) }
        val lastDate = schedule.last().first
        val model = LearnedSplitsEngine.compute(inputs(daysAfter(lastDate, 3), sets))

        (push + pull + legs).forEach { assertEquals(LiftRole.MEMBER, model.roles[it], "lift $it should be held") }
        assertEquals(3, model.visibleGroups.size)
        val pushGroup = model.visibleGroups.first { it.members == push }
        val legsGroup = model.visibleGroups.first { it.members == legs }
        // The last day of week 4 is push, pull, legs in that order, so legs was trained most
        // recently and push longest ago among the three: push is due.
        assertEquals(legsGroup.key, model.attribution.getValue(lastDate).groupKey)
        assertEquals(pushGroup.key, model.dueGroupKey)
    }

    @Test
    fun fourWorkoutsTwiceAWeekEveryOtherWeekKeepsAllFourGroupsHeld() {
        // The case calendar windows broke (spec "Generality"): each specific workout only recurs
        // every other week, which a 14-day calendar recent window would have missed.
        val a = listOf(401L, 402L, 403L)
        val b = listOf(411L, 412L, 413L)
        val c = listOf(421L, 422L, 423L)
        val d = listOf(431L, 432L, 433L)
        val workouts = listOf(a, b, c, d)
        val dates = spacedDates("2026-04-06", 16, listOf(3, 4)) // twice a week for eight weeks
        val schedule = dates.mapIndexed { index, date -> date to workouts[index % workouts.size] }
        val sets = schedule.flatMap { (date, lifts) -> day(date, *lifts.toLongArray()) }
        val model = LearnedSplitsEngine.compute(inputs(daysAfter(dates.last(), 3), sets))

        workouts.flatten().forEach { assertEquals(LiftRole.MEMBER, model.roles[it], "lift $it should be held every other week") }
        assertEquals(4, model.visibleGroups.size)
        workouts.forEach { w -> assertTrue(model.visibleGroups.any { it.members == w }, "missing group $w") }
        val lastWorkout = workouts[(dates.size - 1) % workouts.size]
        val lastGroup = model.visibleGroups.first { it.members == lastWorkout }
        assertEquals(lastGroup.key, model.attribution.getValue(dates.last()).groupKey)
        // The last four sessions cover one full A-B-C-D round; A was trained longest ago of the four.
        assertEquals(model.visibleGroups.first { it.members == a }.key, model.dueGroupKey)
    }

    @Test
    fun returnAfterAFiveWeekBreakDoesNotWronglySplitOffAStaleGroup() {
        val upper = listOf(501L, 502L, 503L, 504L, 505L)
        val lower = listOf(511L, 512L, 513L, 514L, 515L)
        val preBreakDates = spacedDates("2026-01-05", 10, listOf(3)) // four weeks, alternating upper/lower
        val preBreak = preBreakDates.mapIndexed { index, date -> date to if (index % 2 == 0) upper else lower }
        val preBreakSets = preBreak.flatMap { (date, lifts) -> day(date, *lifts.toLongArray()) }
        val lastPreBreak = preBreakDates.last()

        // D = the day after the last pre-break session + 35: the break exceeds RECENT_MAX_DAYS, so
        // even the most recent pre-break session is outside the recent window and nothing is held.
        val breakEnd = daysAfter(lastPreBreak, 36)
        val duringBreak = LearnedSplitsEngine.compute(inputs(breakEnd, preBreakSets))
        assertTrue(duringBreak.groups.isEmpty(), "no group should still be held across a break this long")
        assertTrue(duringBreak.dueOrder.isEmpty())
        assertNull(duringBreak.dueGroupKey)

        // Two new sessions, upper then lower, right as training resumes.
        val newUpper = breakEnd
        val newLower = daysAfter(breakEnd, 3)
        val resumedSets = preBreakSets + day(newUpper, *upper.toLongArray()) + day(newLower, *lower.toLongArray())
        val resumed = LearnedSplitsEngine.compute(inputs(daysAfter(newLower, 2), resumedSets))

        (upper + lower).forEach { assertEquals(LiftRole.MEMBER, resumed.roles[it], "lift $it should be held again") }
        // Exactly the original two groups: nothing held before the break split off a group of its own.
        assertEquals(2, resumed.visibleGroups.size)
        val resumedUpper = resumed.visibleGroups.first { it.members == upper }
        val resumedLower = resumed.visibleGroups.first { it.members == lower }
        assertEquals(resumedLower.key, resumed.attribution.getValue(newLower).groupKey)
        assertEquals(resumedUpper.key, resumed.dueGroupKey)
    }

    @Test
    fun aFirstSessionEverHasNoHeldLiftsNoGroupsAndAnEmptyDueOrder() {
        val model = LearnedSplitsEngine.compute(inputs("2026-09-28", day("2026-09-27", 601L, 602L, 603L)))
        assertTrue(model.roles.isEmpty())
        assertTrue(model.groups.isEmpty())
        assertTrue(model.dueOrder.isEmpty())
        assertNull(model.dueGroupKey)
        assertEquals(DayAttribution("2026-09-27", null, listOf(601L, 602L, 603L)), model.attribution["2026-09-27"])
        assertTrue(model.actions.isEmpty)
    }
}
