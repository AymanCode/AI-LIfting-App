package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.*

class ExerciseChangelogTest {
    private val start = LocalDate.parse("2026-06-01")
    private val end = LocalDate.parse("2026-09-12")

    /** Sets for one day, in set order. Loads are whole pounds here; stored as tenths. */
    private fun day(date: String, loadLbs: Double, vararg reps: Int, bodyweight: Boolean = false): List<WorkoutSet> =
        reps.mapIndexed { i, r ->
            WorkoutSet(exerciseId = 1, date = date, setNumber = i + 1, weightLbs = (loadLbs * 10).toInt(), reps = r,
                completed = true, isBodyweight = bodyweight)
        }

    private val curated = day("2026-06-13", 60.0, 8, 7, 6) +
        day("2026-06-16", 60.0, 9, 8, 7) +
        day("2026-06-20", 60.0, 9, 9, 8) +
        day("2026-06-23", 65.0, 8, 7, 6) +
        day("2026-06-27", 65.0, 9, 8, 7) +
        day("2026-06-30", 60.0, 10, 9, 8)

    private fun text(parts: List<DataPart>) = parts.joinToString("") { it.text }

    @Test fun eachKindFiresInPriorityOrder() {
        val report = buildExerciseReport(curated, false, start, end)
        val byDate = report.events.associateBy { it.date }
        assertEquals(listOf("2026-06-30", "2026-06-27", "2026-06-23", "2026-06-20", "2026-06-16", "2026-06-13"), report.events.map { it.date })

        val first = byDate.getValue("2026-06-13")
        assertEquals(ChangelogKind.FIRST_SESSION, first.kind)
        assertNull(first.from)
        assertEquals("3 sets", text(first.aside))

        val repPr = byDate.getValue("2026-06-16")
        assertEquals(ChangelogKind.REP_PR, repPr.kind)
        assertEquals(TopSet(600, 8), repPr.from)
        assertEquals("+1 rep", repPr.deltaLabel)
        assertEquals("2nd session on 60. Sets 9 · 8 · 7, up from 8 · 7 · 6. ≈ 78 for one", text(repPr.note))

        val held = byDate.getValue("2026-06-20")
        assertEquals(ChangelogKind.HELD, held.kind)
        assertEquals("", held.deltaLabel)
        assertEquals("same top set · +1 on set 2", text(held.aside))

        val up = byDate.getValue("2026-06-23")
        assertEquals(ChangelogKind.MOVED_UP, up.kind)
        assertEquals(TopSet(600, 9), up.from)
        assertEquals("+5 lb", up.deltaLabel)
        assertEquals("Stepped after 60 × 9 on Jun 16 and Jun 20. 3 sessions on 60.", text(up.note))

        val weightAtReps = byDate.getValue("2026-06-27")
        assertEquals(ChangelogKind.WEIGHT_AT_REPS, weightAtReps.kind)
        assertEquals(TopSet(600, 9), weightAtReps.from)
        assertEquals("+5 lb at 9", weightAtReps.deltaLabel)
        assertEquals("was 60 × 9 on Jun 20", text(weightAtReps.note))

        val down = byDate.getValue("2026-06-30")
        assertEquals(ChangelogKind.MOVED_DOWN, down.kind)
        assertEquals("−5 lb", down.deltaLabel)
        assertEquals("2 sessions on 65.", text(down.note))
    }

    @Test fun weightAtRepsNeedsTheBestLowerLoadRepsAndNoEarlierMatchAtOrAboveThisLoad() {
        val sets = day("2026-06-13", 60.0, 12) + day("2026-06-16", 65.0, 8) + day("2026-06-20", 65.0, 11) + day("2026-06-23", 65.0, 12) + day("2026-06-27", 65.0, 12)
        val kinds = buildExerciseReport(sets, false, start, end).events.reversed().map { it.kind }
        assertEquals(listOf(ChangelogKind.FIRST_SESSION, ChangelogKind.MOVED_UP, ChangelogKind.REP_PR, ChangelogKind.WEIGHT_AT_REPS, ChangelogKind.HELD), kinds)
    }

    @Test fun windowKeepsHistoryButOnlyEmitsEventsInside() {
        val report = buildExerciseReport(curated, false, LocalDate.parse("2026-06-15"), end)
        assertEquals("2026-06-16", report.events.last().date)
        assertEquals(ChangelogKind.REP_PR, report.events.last().kind) // history before the window still counts
        assertEquals(5, report.events.size)
        assertEquals(TopSet(600, 9), report.firstTopSet)
        assertEquals(TopSet(600, 10), report.topSet)
    }

    @Test fun bodyweightLoadsFormatAsAddedLoad() {
        val sets = day("2026-06-13", 0.0, 10, bodyweight = true) + day("2026-06-16", 25.0, 8, bodyweight = true)
        val report = buildExerciseReport(sets, true, start, end)
        assertEquals("BW +25 × 8", formatTopSet(report.topSet!!, true))
        assertEquals("BW × 10", formatTopSet(report.firstTopSet!!, true))
        assertNull(report.events.first().e1rmLbs)
        assertNull(report.pace)
    }

    @Test fun nextStepNeedsTwoStepsAndReadsTheRepsYouSteppedAt() {
        assertNull(buildExerciseReport(curated, false, start, end).nextStep)
        var d = LocalDate.parse("2026-06-01")
        fun next(): String { d = LocalDate.fromEpochDays(d.toEpochDays() + 3); return d.toString() }
        val sets = listOf(60.0, 65.0, 70.0).flatMap { load -> listOf(8, 10, 12).flatMap { r -> day(next(), load, r) } } + day(next(), 75.0, 9)
        val step = buildExerciseReport(sets, false, start, end).nextStep!!
        assertEquals(800, step.targetLoadStored)
        assertEquals(12, step.triggerReps)
        assertEquals("12", step.repsRangeLabel)
        assertEquals(9, step.currentReps)
        assertEquals(1, step.sessionsOnCurrentLoad)
        assertEquals(3, step.medianSessionsPerStep)
    }

    @Test fun ladderIsBestRepsPerLoadNewestLoadFirstAndAlwaysKeepsCurrent() {
        val report = buildExerciseReport(curated, false, start, end)
        assertEquals(listOf(650, 600), report.ladder.map { it.loadStored })
        assertEquals(listOf(9, 10), report.ladder.map { it.bestReps })
        assertEquals(listOf("2026-06-27", "2026-06-30"), report.ladder.map { it.date })
        assertEquals(listOf(false, true), report.ladder.map { it.isCurrent })

        var d = LocalDate.parse("2026-06-01")
        fun next(): String { d = LocalDate.fromEpochDays(d.toEpochDays() + 3); return d.toString() }
        val many = listOf(100.0, 90.0, 80.0, 70.0, 60.0, 50.0, 40.0, 30.0).flatMap { day(next(), it, 10) }
        val ladder = buildExerciseReport(many, false, start, end).ladder
        assertEquals(6, ladder.size)
        assertEquals(300, ladder.last().loadStored)
        assertTrue(ladder.last().isCurrent)
    }

    @Test fun tilesAndDatesComeFromTheWindow() {
        val report = buildExerciseReport(curated, false, start, end)
        assertEquals(18, report.workingSets)
        assertEquals(6, report.sessions)
        assertEquals(3.4f, report.daysBetweenSessions!!, 0.01f)   // 17 days / 5 gaps
        assertEquals(650, report.heaviestStored)
        assertEquals("2026-06-23", report.heaviestSince)
        assertEquals(1, report.stepsInWindow)
        assertEquals("2026-06-27", report.unbeatenSince)          // 65 × 9 ≈ 84.5 is the best est. 1RM
        assertEquals("2026-06-13", report.firstDate)
        assertEquals("2026-06-30", report.latestDate)
    }

    @Test fun paceProjectsTheNextRoundFiveOrIsNull() {
        var d = LocalDate.parse("2026-06-13")
        fun next(i: Int): String { if (i > 0) d = LocalDate.fromEpochDays(d.toEpochDays() + if (i % 2 == 1) 3 else 4); return d.toString() }
        val loads = listOf(60, 60, 60, 60, 60, 65, 65, 65, 65, 65, 70, 70, 70, 70, 70, 75, 75, 75, 75, 75, 77, 77, 77, 77).map { if (it == 77) 77.5 else it.toDouble() }
        val reps = listOf(8, 9, 10, 11, 12, 8, 9, 10, 11, 12, 8, 9, 10, 11, 12, 8, 9, 10, 11, 11, 9, 10, 10, 11)
        val sets = loads.indices.flatMap { i -> day(next(i), loads[i], reps[i], maxOf(reps[i] - 1, 5), maxOf(reps[i] - 2, 5)) }
        val report = buildExerciseReport(sets, false, start, end)
        val pace = report.pace!!
        assertEquals(110, pace.targetLbs)
        assertTrue(LocalDate.parse(pace.date) > LocalDate.parse(report.latestDate!!))

        val flat = (0 until 6).flatMap { day("2026-07-${(it + 1).toString().padStart(2, '0')}", 60.0, 10) }
        assertNull(buildExerciseReport(flat, false, start, end).pace)
    }

    @Test fun setsPerWeekMeasuresTheTrainedSpanNotTheWindow() {
        val allTime = buildExerciseReport(curated, false, LocalDate.parse("2000-01-01"), end)
        val trained = buildExerciseReport(curated, false, start, end)
        assertEquals(trained.workingSets, allTime.workingSets)
        // Jun 13 → Sep 12 is 91 days = 13 weeks; 18 sets / 13 ≈ 1.4, not 18 / (26 years of weeks).
        assertEquals(18f / 13f, allTime.setsPerWeek, 0.01f)
    }

    @Test fun heldSessionsStillShowTheirRepChangeAgainstLastSession() {
        val sets = day("2026-06-13", 60.0, 12) + day("2026-06-16", 60.0, 10) + day("2026-06-20", 60.0, 11) + day("2026-06-23", 60.0, 11)
        val events = buildExerciseReport(sets, false, start, end).events.reversed()
        assertEquals(listOf(ChangelogKind.FIRST_SESSION, ChangelogKind.HELD, ChangelogKind.HELD, ChangelogKind.HELD), events.map { it.kind })
        assertEquals("−2 reps", events[1].deltaLabel)
        assertEquals("+1 rep", events[2].deltaLabel)
        assertEquals("", events[3].deltaLabel)
        assertEquals("same top set", text(events[3].aside))
    }

    @Test fun helpersFormat() {
        assertEquals("Jun 13", shortDate("2026-06-13"))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(::ordinal))
        assertEquals("77.5 × 11", formatTopSet(TopSet(775, 11), false))
        assertEquals("77.5", formatLoad(775, false))
        assertEquals("BW", formatLoad(0, true))
        assertEquals("Same reps, more weight", kindLabel(ChangelogKind.WEIGHT_AT_REPS))
    }

    @Test fun ignoresWarmupsIncompleteAndFutureSets() {
        val sets = day("2026-06-13", 60.0, 10) +
            day("2026-06-16", 100.0, 10).map { it.copy(isWarmup = true) } +
            day("2026-06-16", 60.0, 11) +
            day("2026-06-20", 100.0, 10).map { it.copy(completed = false) } +
            day("2026-09-20", 100.0, 10)
        val report = buildExerciseReport(sets, false, start, end)
        assertEquals(2, report.events.size)
        assertEquals(600, report.heaviestStored)
    }

    @Test fun mixedWeightReceiptsPreserveCheckedSetPairsAndEstimateTheirOwnTopSet() {
        val history = day("2026-09-01", 185.0, 5) +
            day("2026-09-01", 195.0, 2).map { it.copy(setNumber = 2) } +
            day("2026-09-01", 250.0, 8).map { it.copy(setNumber = 3, completed = false) } +
            day("2026-09-01", 300.0, 10).map { it.copy(setNumber = 4, isWarmup = true) }
        val report = buildExerciseReport(history.reversed(), false, start, end)
        val event = report.events.single()
        assertEquals(listOf(TopSet(1850, 5), TopSet(1950, 2)), event.sets)
        assertEquals(listOf("185 × 5", "195 × 2"), event.sets.map { formatTopSet(it, false) })
        assertEquals(TopSet(1950, 2), report.topSet)
        assertEquals(208, report.topSetE1rmLbs)
        assertEquals(216, report.latestE1rmLbs) // The session's best estimate still belongs on the trend.
        assertEquals(2, report.workingSets)
        assertEquals("185 × 5", supportingSetLabel(event.otherSets().single(), event.to, false))
    }

    @Test fun equalRepCountsAtDifferentWeightsDoNotRemoveOrHighlightTheWrongSet() {
        val history = day("2026-09-01", 185.0, 5) + day("2026-09-01", 195.0, 5).map { it.copy(setNumber = 2) }
        val event = buildExerciseReport(history, false, start, end).events.single()
        assertEquals(1, event.sets.indexOf(event.to))
        assertEquals(listOf(TopSet(1850, 5)), event.otherSets())
    }

    @Test fun supportRepImprovementRequiresTheSameLoad() {
        val history = day("2026-08-01", 185.0, 8) + day("2026-08-01", 195.0, 5).map { it.copy(setNumber = 2) } +
            day("2026-09-01", 165.0, 10) + day("2026-09-01", 195.0, 5).map { it.copy(setNumber = 2) }
        assertEquals("same top set", text(buildExerciseReport(history, false, start, end).events.first().aside))
    }

    @Test fun suggestionsDoNotRepeatOutliersOrRebounds() {
        fun step(loads: List<Double>) = buildExerciseReport(loads.flatMapIndexed { i, load ->
            day("2026-08-${(i + 1).toString().padStart(2, '0')}", load, 10)
        }, false, start, end).nextStep
        assertNull(step(listOf(80.0, 100.0, 225.0))) // Audited Leg Curl: never suggest 350.
        assertNull(step(listOf(100.0, 105.0, 110.0, 150.0))) // Repeated small steps do not validate a new large one.
        assertNull(step(listOf(100.0, 105.0, 110.0, 100.0, 105.0))) // Deload and rebound are a new run.
        assertNull(step(listOf(20.0, 40.0, 60.0))) // Even repeated very large jumps are not a recommendation.
        assertEquals(775, step(listOf(70.0, 72.5, 75.0))?.targetLoadStored)
        assertEquals(1150, step(listOf(100.0, 105.0, 110.0))?.targetLoadStored)
    }

    @Test fun bodyweightReceiptsKeepAddedLoadsWithoutAnEstimatedMaximum() {
        val history = day("2026-09-01", 0.0, 12, bodyweight = true) +
            day("2026-09-01", 25.0, 8, bodyweight = true).map { it.copy(setNumber = 2) }
        val report = buildExerciseReport(history, true, start, end)
        assertEquals(listOf("BW × 12", "BW +25 × 8"), report.events.single().sets.map { formatTopSet(it, true) })
        assertNull(report.topSetE1rmLbs)
        assertNull(report.latestE1rmLbs)
    }

    @Test fun sameRepsComparisonRequiresAnActualMatchingRepCount() {
        val sets = day("2026-06-13", 60.0, 8) + day("2026-06-16", 65.0, 7) + day("2026-06-20", 65.0, 9)
        val latest = buildExerciseReport(sets, false, start, end).events.first()
        assertEquals(ChangelogKind.REP_PR, latest.kind)
        assertEquals(TopSet(650, 7), latest.from)
    }

    @Test fun newHeaviestMarksOnlyFirstTimesAtAHeavierWeight() {
        // The skull crusher history: 30 → 40 → 50 → 50.5 → down to 40/30 → back up → 60 → a light
        // 40 × 20 day → 60 again → 70 → 80. Only the first 40, 50, 60, 70 and 80 are new.
        val history = day("2026-04-29", 30.0, 14, 14, 14) + day("2026-05-04", 40.0, 12, 12, 12) +
            day("2026-05-11", 50.0, 12, 7, 6) + day("2026-05-17", 50.5, 8) + day("2026-05-27", 40.0, 9, 10) +
            day("2026-06-03", 30.0, 12, 15, 9) + day("2026-06-06", 40.0, 12, 12, 7) + day("2026-06-13", 50.0, 10, 10, 10) +
            day("2026-07-02", 60.0, 7, 8, 8) + day("2026-07-15", 40.0, 20, 20, 15) + day("2026-07-27", 60.0, 10, 10, 8) +
            day("2026-08-11", 70.0, 10, 10) + day("2026-09-03", 80.0, 6, 6) + day("2026-09-07", 80.0, 7, 6)
        val all = buildExerciseReport(history, false, LocalDate.parse("2000-01-01"), end)
        assertEquals(
            listOf("2026-05-04" to 400, "2026-05-11" to 500, "2026-07-02" to 600, "2026-08-11" to 700, "2026-09-03" to 800),
            all.newHeaviest.map { it.date to it.loadStored },
        )
        // The moved-up count in the header is unchanged: it still counts every session-to-session climb.
        assertEquals(9, all.stepsInWindow)

        // Sessions before the window still set the bar: 60 on Jul 2 is new, the Jul 27 return is not.
        val recent = buildExerciseReport(history, false, LocalDate.parse("2026-06-24"), end)
        assertEquals(listOf("2026-07-02", "2026-08-11", "2026-09-03"), recent.newHeaviest.map { it.date })
    }

    @Test fun smallLoadBumpsAddUpToANewHeaviestMark() {
        // The /demo Reverse Fly climbs about 1 lb a cycle with lighter days in between; comparing each
        // bump with the running heaviest never cleared 2.5 lb, so nothing after the first step marked.
        val loads = listOf(25.0, 20.0, 26.0, 21.0, 27.0, 22.0, 28.0, 23.0, 29.0, 24.0, 30.0, 25.0, 31.0, 26.0, 32.0, 27.0, 33.0)
        var d = LocalDate.parse("2026-06-01")
        val history = loads.flatMap { load -> day(d.toString(), load, 10).also { d = LocalDate.fromEpochDays(d.toEpochDays() + 3) } }
        val report = buildExerciseReport(history, false, start, end)
        assertEquals(listOf(280, 310), report.newHeaviest.map { it.loadStored })
        // Every mark is a load never lifted before.
        report.newHeaviest.forEach { mark ->
            assertTrue(history.filter { it.date < mark.date }.all { (it.weightLbs ?: 0) < mark.loadStored }, "$mark")
        }
    }

    @Test fun chaptersAreRunsAtOneLoadAndAReturnToAnEarlierLoadStartsANewOne() {
        val report = buildExerciseReport(curated, false, start, end)
        assertEquals(listOf(600, 650, 600), report.chapters.map { it.loadStored })
        assertEquals(
            listOf(listOf("2026-06-30"), listOf("2026-06-27", "2026-06-23"), listOf("2026-06-20", "2026-06-16", "2026-06-13")),
            report.chapters.map { chapter -> chapter.events.map { it.date } },
        )
    }

    @Test fun onlySessionsThatBeatSomethingEarnAChip() {
        val report = buildExerciseReport(curated, false, start, end)
        val wins = report.events.associate { it.date to it.win }
        assertNull(wins.getValue("2026-06-13"), "first session")
        assertEquals(ChangelogWin("+1 rep", isReps = true), wins.getValue("2026-06-16"))
        assertNull(wins.getValue("2026-06-20"), "held")
        assertEquals(ChangelogWin("+5 lb", isReps = false), wins.getValue("2026-06-23"))
        assertEquals(ChangelogWin("+5 lb at 9", isReps = false), wins.getValue("2026-06-27"))
        // Back down to 60 for 10: never done at 60 before (best 9), so the lighter day is still a rep PR.
        assertEquals(ChangelogWin("+1 rep", isReps = true), wins.getValue("2026-06-30"))
        assertEquals(4, report.records)
    }

    @Test fun aLighterDayBelowItsWeightsBestGetsNoChip() {
        val sets = day("2026-06-13", 60.0, 9) + day("2026-06-16", 65.0, 8) + day("2026-06-20", 60.0, 8)
        val down = buildExerciseReport(sets, false, start, end).events.first()
        assertEquals(ChangelogKind.MOVED_DOWN, down.kind)
        assertNull(down.win)
    }

    @Test fun suggestedRepsBeatTheBestEverLoggedAtTheCurrentLoad() {
        assertEquals(11, buildExerciseReport(curated, false, start, end).suggestedReps)
        // An earlier run at 60 reached 12, so after coming back to 60 the next PR there is 13, not 10.
        val sets = day("2026-06-13", 60.0, 12) + day("2026-06-16", 65.0, 8) + day("2026-06-20", 60.0, 9)
        assertEquals(13, buildExerciseReport(sets, false, start, end).suggestedReps)
        assertEquals(11, buildExerciseReport(day("2026-06-13", 0.0, 10, bodyweight = true), true, start, end).suggestedReps)
    }

    @Test fun anEmptyWindowStillKnowsTheLastSession() {
        val report = buildExerciseReport(curated, false, LocalDate.parse("2026-09-01"), end)
        assertTrue(report.events.isEmpty())
        assertTrue(report.chapters.isEmpty())
        assertEquals(0, report.records)
        assertNull(report.suggestedReps)
        assertEquals("2026-06-30", report.lastSessionDate)
    }

    /** Sets for one day with their own loads, in set order (a top set plus back-off sets). */
    private fun mixed(date: String, vararg sets: Pair<Double, Int>): List<WorkoutSet> =
        sets.mapIndexed { i, (lbs, reps) ->
            WorkoutSet(exerciseId = 1, date = date, setNumber = i + 1, weightLbs = (lbs * 10).toInt(), reps = reps, completed = true)
        }

    @Test fun climbingBackToAWeightAlreadyLiftedIsNotARecord() {
        val back = buildExerciseReport(day("2026-09-01", 110.0, 8) + day("2026-09-04", 100.0, 10) + day("2026-09-08", 110.0, 6), false, start, end)
        assertEquals(ChangelogKind.MOVED_UP, back.events.first().kind)
        assertNull(back.events.first().win, "110 again, fewer reps: beat nothing")
        assertEquals(0, back.records)
        // Back at 110 for 9 beats the old 110 × 8: a rep PR, not a weight record.
        val better = buildExerciseReport(day("2026-09-01", 110.0, 8) + day("2026-09-04", 100.0, 10) + day("2026-09-08", 110.0, 9), false, start, end)
        assertEquals(ChangelogWin("+1 rep", isReps = true), better.events.first().win)
        // A load above everything before is still the weight chip.
        val higher = buildExerciseReport(day("2026-09-01", 110.0, 8) + day("2026-09-04", 100.0, 10) + day("2026-09-08", 115.0, 6), false, start, end)
        assertEquals(ChangelogWin("+15 lb", isReps = false), higher.events.first().win)
    }

    @Test fun backOffSetsCountForRecordsTheSuggestionAndTheEstimate() {
        val first = mixed("2026-09-01", 135.0 to 5, 115.0 to 12)
        val sets = first + mixed("2026-09-04", 115.0 to 10) + mixed("2026-09-08", 115.0 to 11)
        val report = buildExerciseReport(sets, false, start, end)
        val byDate = report.events.associateBy { it.date }
        // The top-set rules still call Sep 8 a rep PR over Sep 4, but 115 × 12 was done as a back-off set.
        assertEquals(ChangelogKind.REP_PR, byDate.getValue("2026-09-08").kind)
        assertNull(byDate.getValue("2026-09-08").win)
        assertNull(byDate.getValue("2026-09-04").win)
        assertEquals(0, report.records)
        assertEquals(13, report.suggestedReps, "one more than the 115 × 12 back-off set")
        // The estimate comes from the session's best set, as the chart's latest Trend value does.
        val one = buildExerciseReport(first, false, start, end)
        assertEquals(TopSet(1150, 12), one.latestE1rmSet)
        assertEquals(161, one.latestE1rmLbs)
    }
}
