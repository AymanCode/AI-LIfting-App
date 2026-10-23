package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.DayAttribution
import com.ayman.ecolift.data.splits.LearnedFixtures
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.LearnedSplitsEngine
import com.ayman.ecolift.data.splits.WorkedExample
import kotlinx.datetime.LocalDate
import kotlin.test.*

class ProgressGroupWallTest {
    private val start = LocalDate(2026, 9, 5)
    private val end = LocalDate(2026, 10, 2)
    private val lift = Exercise(1, "Lying Leg Curl", "HAMSTRINGS")
    private fun set(date: String, load: Int = 900, reps: Int = 12, id: Long = 1) =
        WorkoutSet(exerciseId = id, date = date, setNumber = 1, weightLbs = load, reps = reps, completed = true)
    private fun tile(sets: List<WorkoutSet>, exercise: Exercise = lift) = groupWallTile(exercise, sets, start, end)

    @Test fun twoWeeksIncludesExactlyFourteenDates() {
        assertEquals(LocalDate(2026, 9, 19), progressWindowStart(TimeframeFilter.TWO_WEEKS, end))
    }

    @Test fun everyLiftUsesTheSameDatesIncludingEmptyWeeksAndWindowBoundaries() {
        val a = tile(listOf(set("2026-09-05"), set("2026-09-12"), set("2026-10-02", 1050)))
        val b = tile(listOf(set("2026-09-12", 600), set("2026-10-02", 700)))
        assertEquals(listOf(0f, 7f / 27, 1f), a.chart.xs)
        assertEquals(listOf(a.chart.xs[1], a.chart.xs[2]), b.chart.xs)
        assertTrue(a.chart.xs[2] - a.chart.xs[1] > a.chart.xs[1] - a.chart.xs[0])
        // Heights are fractions of this lift's own axis range, so the dots sit inside it and 0 < 1.
        assertTrue(a.chart.ys.all { it > 0f && it < 1f })
        assertEquals(a.chart.ys[0], a.chart.ys[1])
        assertTrue(a.chart.ys[1] < a.chart.ys[2])
        assertEquals(a.chart.ys.first(), b.chart.ys.first(), 1e-5f) // Each lift has its own y range.
        assertEquals(a.chart.ys.last(), b.chart.ys.last(), 1e-5f)
    }

    @Test fun repGainAtTheSameWeightRaisesTheLine() {
        val t = tile(listOf(set("2026-09-29", 800, 8), set("2026-10-02", 800, 9)))
        assertEquals("80×9", t.topSetParts.single().text)
        assertTrue(t.chart.ys[1] > t.chart.ys[0])
        assertEquals("1RM", t.chart.unit)
    }

    @Test fun tilesFollowTheTopSetRatherThanABackOffSetsEstimateOrWarmups() {
        val sets = listOf(set("2026-09-12", 1050, 4), set("2026-09-12", 850, 20).copy(setNumber = 2),
            set("2026-09-12", 2000, 20).copy(setNumber = 3, isWarmup = true),
            set("2026-10-02", 1100, 5), set("2026-10-02", 1100, 8).copy(setNumber = 2),
            set("2026-10-03", 1200), set("2026-09-04", 1000), set("2026-09-20", 1500).copy(completed = false))
        val t = tile(sets)
        assertEquals(2, t.sessions)
        assertEquals("110×8", t.topSetParts.single().text)
        assertEquals(2, t.chart.pointCount)
        assertTrue(t.chart.ys[0] < t.chart.ys[1])
    }

    @Test fun weightWinShowsTheWholeWindowGainAndRepWinsHaveTheRightPlural() {
        assertEquals(ChangelogWin("+15 lb", false), tile(listOf(set("2026-09-10"), set("2026-09-18", 950),
            set("2026-09-24", 1000), set("2026-09-30", 1050))).win)
        assertEquals(ChangelogWin("+5 lb", false), tile(listOf(set("2026-09-10", 900, 8),
            set("2026-09-18", 950, 8), set("2026-09-30", 950, 9))).win)
        assertEquals(ChangelogWin("+1 rep", true), tile(listOf(set("2026-09-10", reps = 8), set("2026-09-30", reps = 9))).win)
        assertEquals(ChangelogWin("+2 reps", true), tile(listOf(set("2026-09-10", reps = 8), set("2026-09-30", reps = 10))).win)
    }

    @Test fun steadyLowerClimbBackAndBackOffMatchesEarnNoChip() {
        assertNull(tile(listOf(set("2026-09-10"), set("2026-09-30"))).win)
        assertNull(tile(listOf(set("2026-09-10"), set("2026-09-30", 850))).win)
        assertNull(tile(listOf(set("2026-08-30", 1050), set("2026-09-10", 900), set("2026-09-30", 1050))).win)
        assertNull(tile(listOf(set("2026-08-30", 1000, 8), set("2026-08-30", 900, 12).copy(setNumber = 2),
            set("2026-09-10", 900, 8), set("2026-09-30", 900, 12))).win)
    }

    @Test fun singleSessionHasOneDotAndNoChipEvenWhenItBeatOlderHistory() {
        val t = tile(listOf(set("2026-08-30", 900), set("2026-09-30", 1050)))
        assertEquals(1, t.sessions)
        assertEquals(1, t.chart.pointCount)
        assertNull(t.win)
        assertEquals("", t.winNumber)
        val none = tile(emptyList())
        assertEquals(0, none.chart.pointCount)
        assertNull(none.topSet)
        assertEquals("—", none.topSetParts.single().text)
        assertTrue(none.chart.yTicks.isEmpty())
        assertTrue(none.chart.xTicks.isNotEmpty()) // Axes still draw under the NO SESSIONS chip.
    }

    @Test fun bodyweightTopSetsUseBwAndAddedLoad() {
        val bw = lift.copy(isBodyweight = true)
        assertEquals("BW×8", tile(listOf(set("2026-09-10", 0, 8)), bw).topSetParts.single().text)
        assertEquals("BW+45×8", tile(listOf(set("2026-09-10", 450, 8)), bw).topSetParts.single().text)
    }

    @Test fun topSetIsSplitIntoWeightRepsAndTheLbSuperscriptRule() {
        val bw = lift.copy(isBodyweight = true)
        assertEquals(GroupWallTopSetUi("280", "13", true), tile(listOf(set("2026-09-10", 2800, 13))).topSet)
        assertEquals(GroupWallTopSetUi("37.5", "8", true), tile(listOf(set("2026-09-10", 375, 8))).topSet)
        assertEquals(GroupWallTopSetUi("BW+25", "8", true), tile(listOf(set("2026-09-10", 250, 8)), bw).topSet)
        assertEquals(GroupWallTopSetUi("BW", "30", false), tile(listOf(set("2026-09-10", 0, 30)), bw).topSet)
    }

    @Test fun winPillSplitsItsNumberFromItsUnit() {
        val weight = tile(listOf(set("2026-09-10"), set("2026-09-18", 950), set("2026-09-24", 1000), set("2026-09-30", 1050)))
        assertEquals("+15", weight.winNumber)
        assertEquals("LB", weight.winUnit)
        val reps = tile(listOf(set("2026-09-10", reps = 8), set("2026-09-30", reps = 10)))
        assertEquals("+2", reps.winNumber)
        assertEquals("REPS", reps.winUnit)
        assertEquals("REP", tile(listOf(set("2026-09-10", reps = 8), set("2026-09-30", reps = 9))).winUnit)
    }

    @Test fun descriptionKeepsTheFormatTheScenariosRead() {
        val t = tile(listOf(set("2026-09-10", 2550, 12), set("2026-09-30", 2800, 13)), lift.copy(name = "Standing Calf Raise"))
        assertEquals("Open Standing Calf Raise report, 280×13, 2 sessions, +25 lb", t.description)
    }

    @Test fun bodyweightRepGainAtTheSameAddedLoadRaisesTheLineWhenBodyweightIsKnown() {
        val bw = lift.copy(isBodyweight = true)
        val sets = listOf(set("2026-09-20", 250, 8), set("2026-10-02", 250, 9))
        val known = groupWallTile(bw, sets, start, end, bodyweightLbs = 180)
        assertEquals("1RM", known.chart.unit)
        assertTrue(known.chart.ys[1] > known.chart.ys[0])
        // The score is (bodyweight + added) × (1 + reps / 30): 205 × 1.2667 and 205 × 1.3.
        val ticks = known.chart.yTicks.map { it.label.toDouble() }
        assertTrue(ticks.all { it in 255.0..270.0 })
    }

    @Test fun bodyweightWithoutBodyweightPlotsAddedLoadOrElseReps() {
        val bw = lift.copy(isBodyweight = true)
        val loaded = groupWallTile(bw, listOf(set("2026-09-20", 250, 8), set("2026-10-02", 250, 9)), start, end)
        assertEquals("+LB", loaded.chart.unit)
        assertTrue(loaded.chart.ys[1] > loaded.chart.ys[0])
        val pure = groupWallTile(bw, listOf(set("2026-09-20", 0, 20), set("2026-10-02", 0, 25)), start, end)
        assertEquals("REPS", pure.chart.unit)
        assertTrue(pure.chart.ys[1] > pure.chart.ys[0])
        // Pure bodyweight with a known bodyweight scores bodyweight × (1 + reps / 30), so reps still raise it.
        val withBw = groupWallTile(bw, listOf(set("2026-09-20", 0, 20), set("2026-10-02", 0, 25)), start, end, bodyweightLbs = 180)
        assertEquals("1RM", withBw.chart.unit)
        assertTrue(withBw.chart.ys[1] > withBw.chart.ys[0])
        // A zero or missing bodyweight counts as unknown.
        assertEquals("REPS", groupWallTile(bw, listOf(set("2026-09-20", 0, 20)), start, end, bodyweightLbs = 0).chart.unit)
    }

    @Test fun bodyweightFlowsFromTheWallIntoItsTiles() {
        val pullUp = Exercise(1, "Pull Up", "BACK", isBodyweight = true)
        val section = HeldSectionUi("Pull", HeldSectionKind.GROUP, 0, listOf(HeldLiftRowUi(1, "Pull Up", "Back", null, null, false)), null)
        val source = GroupWallSource(listOf(section), LearnedModel.EMPTY, listOf(pullUp),
            listOf(set("2026-09-20", 0, 8), set("2026-10-02", 0, 9)))
        assertEquals("REPS", buildGroupWall(source, start, end, { it }).panels.single().rows.single().single().chart.unit)
        assertEquals("1RM", buildGroupWall(source, start, end, { it }, bodyweightLbs = 180).panels.single().rows.single().single().chart.unit)
    }

    @Test fun longWindowsKeepTheBestSessionPerWeekAtItsOwnDate() {
        val longStart = LocalDate(2026, 7, 10)
        val longEnd = LocalDate(2026, 10, 10) // 92 days: weekly
        // Mon Sep 14 – Sun Sep 20 holds three sessions; the middle one is the best.
        val sets = listOf(set("2026-08-04", 1000), set("2026-09-15", 1000), set("2026-09-17", 1100), set("2026-09-19", 900),
            set("2026-10-06", 1050))
        val t = groupWallTile(lift, sets, longStart, longEnd)
        assertEquals(5, t.sessions) // Bucketing thins the line, never the count.
        assertEquals(3, t.chart.pointCount)
        assertEquals(groupWallDateFraction(LocalDate(2026, 9, 17), longStart, longEnd), t.chart.xs[1])
        assertTrue(t.chart.ys[1] > t.chart.ys[2] && t.chart.ys[2] > t.chart.ys[0])
        assertTrue(t.chart.yTicks.size in 1..3)
    }

    @Test fun windowsOfFortyFiveDaysOrLessKeepEverySession() {
        // Six sessions inside one Monday-start week, in a 39-day window.
        val sets = (14..19).map { set("2026-09-$it", 1000 + (it - 14) * 10) }
        val t = groupWallTile(lift, sets, LocalDate(2026, 9, 1), LocalDate(2026, 10, 10))
        assertEquals(6, t.chart.pointCount)
        assertEquals(6, t.sessions)
    }

    @Test fun veryLongWindowsKeepTheBestSessionPerMonth() {
        val longStart = LocalDate(2025, 6, 10)
        val longEnd = LocalDate(2026, 10, 10) // 487 days: monthly
        val sets = listOf(set("2026-08-03", 1000), set("2026-08-17", 1200), set("2026-08-24", 1100), set("2026-09-07", 1000),
            set("2026-09-08", 1000), set("2025-12-10", 800))
        val t = groupWallTile(lift, sets, longStart, longEnd)
        assertEquals(6, t.sessions)
        assertEquals(3, t.chart.pointCount) // Dec 2025, Aug 2026, Sep 2026
        assertEquals(groupWallDateFraction(LocalDate(2026, 8, 17), longStart, longEnd), t.chart.xs[1])
        // A tie keeps the later session.
        assertEquals(groupWallDateFraction(LocalDate(2026, 9, 8), longStart, longEnd), t.chart.xs[2])
    }

    @Test fun flatDataGetsOneGridlineOnTheValue() {
        val flat = tile(listOf(set("2026-09-14", 1450, 10), set("2026-09-21", 1450, 10), set("2026-09-30", 1450, 10)))
        assertEquals(listOf("193"), flat.chart.yTicks.map { it.label }) // 145 × (1 + 10/30) = 193.3, whole pounds
        assertEquals(1, flat.chart.yTicks.size)
        assertTrue(flat.chart.ys.all { it == 0.5f })
        val one = tile(listOf(set("2026-09-30", 2250, 8)))
        assertEquals(listOf("285"), one.chart.yTicks.map { it.label })
        // Bodyweight reps print exactly, never rounded.
        val bw = lift.copy(isBodyweight = true)
        assertEquals(listOf("20"), groupWallTile(bw, listOf(set("2026-09-14", 0, 20), set("2026-09-30", 0, 20)), start, end)
            .chart.yTicks.map { it.label })
        val axis = groupWallYAxis(102.5, 102.5, exactFlat = true)
        assertEquals(listOf(102.5), axis.ticks)
        assertEquals(97.375, axis.lo, 1e-9)
    }

    @Test fun gridlineStepIsTheFirstOfOneTwoTwoPointFiveFiveThatGivesOneToThreeLines() {
        assertEquals(listOf(360.0, 380.0, 400.0), groupWallYAxis(357.0, 401.0, false).ticks)
        assertEquals(listOf(40.0, 45.0), groupWallYAxis(36.7, 47.5, false).ticks)
        assertEquals(listOf("22.5", "25", "27.5"), groupWallYAxis(21.0, 28.0, false).ticks.map(::groupWallAxisLabel))
        val ranges = listOf(1.0 to 2.0, 15.0 to 25.0, 80.0 to 81.5, 118.0 to 140.0, 185.0 to 190.0, 700.0 to 1250.0,
            0.0 to 30.0, 102.0 to 104.0, 17.5 to 22.5, 1180.0 to 1210.0)
        for ((min, max) in ranges) {
            val axis = groupWallYAxis(min, max, false)
            assertTrue(axis.ticks.size in 1..3, "$min..$max gave ${axis.ticks}")
            assertTrue(axis.ticks.all { it >= axis.lo && it <= axis.hi }, "$min..$max gave ${axis.ticks} outside ${axis.lo}..${axis.hi}")
            assertTrue(axis.lo < min && axis.hi > max)
        }
        assertEquals("22.5", groupWallAxisLabel(22.5))
        assertEquals("25", groupWallAxisLabel(25.0))
        assertEquals("102.5", groupWallAxisLabel(102.5))
    }

    @Test fun aOneMonthWindowMarksEveryMondayWithItsDayAndTheMonthOnChange() {
        val ticks = groupWallXTicks(LocalDate(2026, 9, 10), LocalDate(2026, 10, 10))
        assertEquals(listOf("SEP 14", "21", "28", "OCT 5"), ticks.map { it.label })
        assertTrue(ticks.all { it.major })
        assertEquals(listOf(4f / 30, 11f / 30, 18f / 30, 25f / 30), ticks.map { it.fraction })
    }

    @Test fun aThreeMonthWindowLabelsMonthStartsAndMarksMondaysQuietly() {
        val ticks = groupWallXTicks(LocalDate(2026, 7, 10), LocalDate(2026, 10, 10))
        assertEquals(listOf("AUG", "SEP", "OCT"), ticks.filter { it.label != null }.map { it.label })
        assertTrue(ticks.filter { it.label != null }.all { it.major })
        val mondays = ticks.filter { it.label == null }
        assertEquals(13, mondays.size) // Jul 13 … Oct 5
        assertTrue(mondays.none { it.major })
        assertEquals(ticks.map { it.fraction }, ticks.map { it.fraction }.sorted())
    }

    @Test fun aSixToTwelveMonthWindowLabelsQuartersAndTheNewYear() {
        val ticks = groupWallXTicks(LocalDate(2025, 11, 10), LocalDate(2026, 10, 10))
        val labelled = ticks.filter { it.label != null }
        assertEquals(listOf("’26", "APR", "JUL", "OCT"), labelled.map { it.label })
        assertTrue(labelled.all { it.major })
        assertEquals(listOf(false), ticks.filter { it.label == null }.map { it.major }.distinct())
        assertEquals(11, ticks.size) // Dec 1 … Oct 1
    }

    @Test fun aWindowOverFourHundredDaysLabelsOnlyTheYear() {
        val ticks = groupWallXTicks(LocalDate(2025, 6, 10), LocalDate(2026, 10, 10))
        assertEquals(listOf("2026"), ticks.mapNotNull { it.label })
        assertEquals(listOf(false, false, true, false, false, false), ticks.map { it.major }) // Jul, Oct, Jan, Apr, Jul, Oct
        val long = groupWallXTicks(LocalDate(2024, 4, 2), LocalDate(2026, 10, 10))
        assertEquals(listOf("2025", "2026"), long.mapNotNull { it.label })
    }

    @Test fun aTwoWeekWindowCrossingAMonthPrefixesTheNewMonth() {
        val ticks = groupWallXTicks(LocalDate(2026, 9, 25), LocalDate(2026, 10, 8))
        assertEquals(listOf("SEP 28", "OCT 5"), ticks.map { it.label })
    }

    @Test fun tailKickerSaysOneSessionOnlyWhenEveryTailLiftHasExactlyOne() {
        fun tail(vararg sessions: Int) = sessions.mapIndexed { i, n -> GroupWallTailUi(i + 1L, emptyList(), "", sessions = n) }
        assertEquals("ONE SESSION", groupWallTailLabel(tail(1, 1, 1)))
        assertEquals("+2 LIFTS", groupWallTailLabel(tail(1, 2)))
        assertEquals("+1 LIFT", groupWallTailLabel(tail(3)))
        assertEquals("", groupWallTailLabel(emptyList()))
    }

    @Test fun fadedMembersStayInTheirGroupsTailWhenPanelsAreReordered() {
        val model = LearnedSplitsEngine.compute(WorkedExample.inputs())
        val sections = buildHeldSections(model, WorkedExample.AS_OF, WorkedExample.exercises, WorkedExample.sets, { it })
        val wall = buildGroupWall(GroupWallSource(sections, model, WorkedExample.exercises, WorkedExample.sets),
            start, end, { it })
        sections.forEachIndexed { index, section ->
            // Learned groups may share a display title; identity survives the wall's reordering.
            val key = section.groupKey ?: "${section.kind}-$index"
            val panel = wall.panels.single { it.key == key }
            assertEquals(section.rows.filterNot { it.faded }.map { it.exerciseId }, panel.rows.flatten().map { it.exerciseId })
            val activeFaded = section.rows.filter { row ->
                row.faded &&
                tile(WorkedExample.sets.filter { set -> set.exerciseId == row.exerciseId }).sessions > 0
            }
            assertTrue(activeFaded.all { row -> panel.tail.any { it.exerciseId == row.exerciseId } })
            assertTrue(panel.tail.all { tail ->
                tile(WorkedExample.sets.filter { it.exerciseId == tail.exerciseId }).sessions > 0 })
        }
    }

    @Test fun sharedFadedLiftStaysInEachActiveTailAndZeroSessionTailsAreOmitted() {
        fun row(id: Long) = HeldLiftRowUi(id, "Lift $id", "Back", null, null, true)
        val template = LearnedSplitsEngine.compute(WorkedExample.inputs()).groups.first()
        val groups = listOf("older", "recent").mapIndexed { index, key ->
            template.copy(key = key, slotId = index + 1L, suppressed = false)
        }
        val model = LearnedModel.EMPTY.copy(memoryStart = start.toString(), groups = groups, attribution = mapOf(
            "2026-09-10" to DayAttribution("2026-09-10", "older", listOf(1)),
            "2026-09-30" to DayAttribution("2026-09-30", "recent", listOf(1)),
        ))
        val sections = groups.map { group ->
            HeldSectionUi("Same title", HeldSectionKind.GROUP, 0,
            listOf(row(1), row(1), row(2), row(3)), group.key)
        }
        val sets = listOf(set("2026-09-10", id = 1), set("2026-09-30", 1050, id = 1),
            set("2026-09-04", id = 2), set("2026-09-20", id = 3).copy(isWarmup = true))
        val source = GroupWallSource(sections, model, (1L..3L).map { Exercise(it, "Lift $it", "BACK") }, sets)
        val wall = buildGroupWall(source, start, end, { it })
        assertEquals(listOf("recent", "older"), wall.panels.map { it.key })
        wall.panels.forEach { panel ->
            assertEquals(listOf(1L), panel.tail.map { it.exerciseId })
            assertEquals("Lift 1 105×12 · 2 sessions", panel.tail.single().parts.joinToString("") { it.text })
            assertEquals("+1 LIFT", panel.tailLabel)
            assertTrue(panel.rows.isEmpty())
        }
        val empty = buildGroupWall(source.copy(sets = sets.filterNot { it.exerciseId == 1L }),
            start, end, { it })
        assertTrue(empty.panels.all { it.tail.isEmpty() })
    }

    @Test fun recentAttributedSessionsLeadTiesKeepHeldOrderAndEmptyGroupsGetTheirLastOwnedDay() {
        val template = LearnedSplitsEngine.compute(WorkedExample.inputs()).groups.first()
        val groups = listOf("quiet", "older", "recent").mapIndexed { index, key ->
            template.copy(key = key, slotId = index + 1L, suppressed = false)
        }
        val model = LearnedModel.EMPTY.copy(memoryStart = "2026-09-05", groups = groups,
            attribution = mapOf(
                "2026-09-20" to DayAttribution("2026-09-20", "older", emptyList()),
                "2026-09-30" to DayAttribution("2026-09-30", "recent", emptyList()),
                // A member used on another group's day must not update its own group's last day.
                "2026-10-01" to DayAttribution("2026-10-01", "older", listOf(1)),
            ))
        fun section(title: String, id: Long, key: String? = null) = HeldSectionUi(title,
            if (key == null) HeldSectionKind.EVERY_SESSION else HeldSectionKind.GROUP, 0,
            listOf(HeldLiftRowUi(id, "Lift $id", "Back", null, null, false)), key)
        val sections = listOf(section("Pull + Posterior", 1, "quiet"), section("Every session", 4),
            section("Upper Strength", 3, "recent"), section("Lower Strength", 2, "older"))
        val sets = listOf(set("2026-09-03", id = 1), set("2026-09-20", id = 2),
            set("2026-09-30", id = 3), set("2026-09-30", id = 4), set("2026-10-01", id = 1),
            set("2026-10-02", id = 1).copy(isWarmup = true),
            set("2026-10-02", id = 2).copy(completed = false))
        val source = GroupWallSource(sections, model, (1L..4L).map { Exercise(it, "Lift $it", "BACK") },
            sets, listOf(WorkoutDay(date = "2026-09-03", cycleSlotId = 1)))
        val wall = buildGroupWall(source, start, end, { it })
        assertEquals(listOf("Lower Strength", "Every session", "Upper Strength", "Pull + Posterior"),
            wall.panels.map { it.title })
        assertEquals(listOf(2, 1, 1, 0), wall.panels.map { it.sessions })
        assertEquals("2026-10-01", wall.panels.first().latestWindowDate)
        val quiet = wall.panels.last()
        assertNull(quiet.latestWindowDate)
        assertEquals("2026-09-03", quiet.lastAttributedDate)
        assertEquals("Pull + Posterior · 0 sessions · last Sep 3",
            quiet.foldedParts.joinToString("") { it.text })
        assertEquals("Pull + Posterior, 0 sessions in this window, last Sep 3", quiet.foldedDescription)
        assertEquals("Sep 3", quiet.lastLabel)
        // No invented last date if there has never been an attributed session.
        val never = buildGroupWall(source.copy(days = emptyList()), start, end, { it })
            .panels.single { it.key == "quiet" }
        assertNull(never.lastAttributedDate)
        assertEquals("Pull + Posterior · 0 sessions", never.foldedParts.joinToString("") { it.text })
        assertNull(never.lastLabel)
    }

    @Test fun attributedUnheldTailCountsAndFrozenHistoryFollowTheirGroup() {
        val history = listOf("2026-09-10", "2026-09-18", "2026-09-24").flatMap { LearnedFixtures.day(it, 1, 2, 3) } +
            listOf("2026-09-06", "2026-09-14", "2026-09-22").flatMap { LearnedFixtures.day(it, 4, 5, 6) }
        val model = LearnedSplitsEngine.compute(LearnedFixtures.inputs("2026-10-02", history))
        val group = model.groups.single { 1L in it.members }
        val attributed = model.copy(memoryStart = "2026-09-18", attribution = model.attribution +
            ("2026-09-24" to DayAttribution("2026-09-24", group.key, listOf(9))))
        val exercises = listOf(1L, 2L, 3L, 4L, 5L, 6L, 9L, 10L).map { Exercise(it, "Lift $it", "CHEST") }
        val sets = history + set("2026-09-24", 850, 7, 9) + set("2026-09-12", id = 10)
        val sections = buildHeldSections(attributed, "2026-10-02", exercises, sets, { it })
        val days = listOf(WorkoutDay(date = "2026-09-10", cycleSlotId = group.slotId))
        val source = GroupWallSource(sections, attributed, exercises, sets, days)
        val wall = buildGroupWall(source, start, end, { it })
        val panel = wall.panels.first { it.key == group.key }
        assertEquals("3", panel.sessionParts.first().text)
        assertEquals(listOf(9L), panel.tail.map { it.exerciseId })
        assertEquals("Lift 9 85×7", panel.tail.single().parts.joinToString("") { it.text })
        assertEquals(Triple("Lift 9", "85×7", 1), panel.tail.single().let { Triple(it.name, it.topSetLabel, it.sessions) })
        assertEquals(Pair("85", "7"), panel.tail.single().let { it.weight to it.reps })
        assertEquals("ONE SESSION", panel.tailLabel)
        assertTrue(panel.rows.flatten().none { it.exerciseId == 9L })
        assertTrue(buildGroupWall(source.copy(query = "Lift 1"), start, end, { it })
            .panels.first { it.key == group.key }.tail.isEmpty())
        assertEquals("Sep 5 – Oct 2", wall.windowLabel)
    }
}
