package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hub.*
import com.ayman.ecolift.data.splits.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class IronMindDeskTest {
    private val today = LocalDate(2026, 10, 4)
    private val emptyHub = HubBuild(emptyList(), null, 0)
    private val lifts = listOf(Exercise(1, "Bench Press", "Chest"), Exercise(2, "Lat Pulldown", "Back"),
        Exercise(3, "Curl", "Biceps"), Exercise(4, "Dip", "Chest"), Exercise(5, "Row", "Back"))
    private fun group(key: String, name: String = "", learned: Boolean = true, dormant: Boolean = false,
                      suppressed: Boolean = false, members: List<Long> = listOf(1, 2)) = LearnedGroup(
        key, 10, name, 0, members, members, listOf("2026-09-09", "2026-09-26"),
        listOf("2026-09-09", "2026-09-26"), emptySet(), true, learned, 0, false, dormant, suppressed)
    private fun model(groups: List<LearnedGroup> = emptyList(), held: Set<Long> = setOf(1, 2)) = LearnedModel.EMPTY.copy(
        groups = groups, roles = held.associateWith { LiftRole.MEMBER },
        presence = mapOf("2026-09-09" to setOf(1L), "2026-09-26" to setOf(1L, 2L)))
    private fun set(id: Long, date: String, load: Int, reps: Int = 8, completed: Boolean = true, warmup: Boolean = false) =
        WorkoutSet(exerciseId = id, date = date, setNumber = 1, weightLbs = load, reps = reps, completed = completed, isWarmup = warmup)
    private fun desk(model: LearnedModel = model(), sets: List<WorkoutSet> = emptyList(), hub: HubBuild = emptyHub,
                     messages: List<AiMessageUi> = emptyList()) = projectIronMindDesk(model, sets, lifts, hub, messages, today)

    @Test fun decisionsSelectOnlyUnnamedLiveLearnedGroups() {
        val state = desk(model(listOf(group("live"), group("named", "Upper 1"), group("sleeping", dormant = true),
            group("retired", suppressed = true), group("seed", learned = false))))
        assertEquals(listOf("keep:live"), state.decisions.map { it.id })
        assertEquals(1, state.decisionCount)
        assertEquals("2 sessions · Sep 9 – Sep 26", state.decisions.single().evidence)
        assertEquals(listOf(true, true), state.decisions.single().lifts.first().sessionDots)
        assertEquals(listOf(false, true), state.decisions.single().lifts.last().sessionDots)
    }

    @Test fun unsyncedLearnedGroupsStillAppear() {
        val state = desk(model(listOf(group("waiting").copy(slotId = -1))))
        assertEquals(-1L, state.decisions.single().slotId)
        assertEquals(1, state.decisionCount)
    }

    @Test fun threeOfFourOverlapAddsAnEvidenceDecision() {
        val unnamed = group("new", members = listOf(1, 2, 3, 4))
        val named = group("named", "Upper 1", members = listOf(1, 2, 3, 5)).copy(slotId = 20)
        val input = model(listOf(unnamed, named)).copy(
            presence = mapOf("2026-09-26" to setOf(1L, 2L, 3L, 4L)),
            attribution = mapOf("2026-09-26" to DayAttribution("2026-09-26", "named", emptyList())))
        val state = desk(input)
        assertEquals(2, state.decisionCount)
        assertEquals("3/4 lifts in Upper 1 · Sep 26 had all 4", state.decisions.last().evidence)
        assertEquals(unnamed.slotId, state.decisions.last().slotId)
    }

    @Test fun insufficientOverlapIsAbsent() {
        val state = desk(model(listOf(group("new", members = listOf(1, 2, 3, 4)),
            group("named", "Upper 1", members = listOf(1, 2, 5)))))
        assertEquals(1, state.decisionCount)
    }

    @Test fun heavierLastTopSetUsesReportLoadWinAndDatedSessions() {
        val sets = listOf(set(1, "2026-09-09", 1500), set(1, "2026-09-26", 1550))
        val row = desk(sets = sets).noticed.single()
        assertEquals(NoticedKind.BeatPrevious, row.kind)
        assertEquals("150 → 155 lb · 2 sessions · Sep 9 – Sep 26", row.evidence)
        assertEquals(ChangelogWin("+5 lb", false), row.win)
    }

    @Test fun namedLearnedOverlapIsOneDecisionWithoutAKeepCard() {
        val state = desk(model(listOf(group("a", "Upper 1", members = listOf(1, 2, 3, 4)),
            group("b", "Upper 2", members = listOf(1, 2, 3, 5)))))
        assertEquals(1, state.decisionCount)
        assertEquals("Upper 2", state.decisions.single().overlapsName)
        assertTrue(state.decisions.single().id.startsWith("overlap:"))
    }

    @Test fun beatEvidenceSpansTheShownSessionsWhileChipDescribesTheLastWin() {
        val rows = desk(sets = listOf(set(1, "2026-09-02", 2500), set(1, "2026-09-09", 2550),
            set(1, "2026-09-15", 2600), set(1, "2026-09-20", 2700), set(1, "2026-09-30", 2750))).noticed
        assertEquals("250 → 275 lb · 5 sessions · Sep 2 – Sep 30", rows.single().evidence)
        assertEquals(ChangelogWin("+5 lb", false), rows.single().win)
    }

    @Test fun moreRepsAtSameLoadUseGreenRepWin() {
        val row = desk(sets = listOf(set(1, "2026-09-09", 1500, 8), set(1, "2026-09-26", 1500, 9))).noticed.single()
        assertEquals(ChangelogWin("+1 rep", true), row.win)
        assertEquals("150 × 8 → 150 × 9 · 2 sessions · Sep 9 – Sep 26", row.evidence)
    }

    @Test fun returningToAnOldLoadDoesNotInventAWin() {
        val row = desk(sets = listOf(set(1, "2026-09-02", 1600), set(1, "2026-09-09", 1500),
            set(1, "2026-09-26", 1600))).noticed.single()
        assertEquals(NoticedKind.BeatPrevious, row.kind)
        assertNull(row.win)
    }

    @Test fun fourConsecutiveHeldSessionsAtOneLoadProduceAskWhy() {
        val sets = listOf("2026-09-09", "2026-09-15", "2026-09-20", "2026-09-26").map { set(2, it, 1500) }
        val row = desk(sets = sets).noticed.single()
        assertEquals(NoticedKind.FlatLoad, row.kind)
        assertEquals("150 lb", row.loadLabel)
        assertEquals("4 sessions · Sep 9 – Sep 26", row.evidence)
        assertEquals("Why does Lat Pulldown look flat?", row.question)
        assertTrue(desk(model(held = emptySet()), sets).noticed.isEmpty())
        assertTrue(desk(sets = sets.dropLast(1)).noticed.isEmpty())
    }

    @Test fun loadChangesBreakTheFlatRunAndSetsAreNotSessions() {
        val sets = listOf(set(2, "2026-09-09", 1400), set(2, "2026-09-15", 1500),
            set(2, "2026-09-20", 1500), set(2, "2026-09-26", 1500))
        assertTrue(desk(sets = sets).noticed.isEmpty())
        assertTrue(desk(sets = List(5) { set(2, "2026-09-26", 1500).copy(setNumber = it + 1) }).noticed.isEmpty())
    }

    @Test fun uncheckedWarmupAndFutureSetsCannotCreateObservations() {
        val sets = listOf(set(1, "2026-09-09", 1500), set(1, "2026-09-26", 1550, completed = false),
            set(1, "2026-09-27", 1800, warmup = true), set(1, "2026-10-05", 1900))
        assertTrue(desk(sets = sets).noticed.isEmpty())
    }

    @Test fun mergedLiftHistoryFollowsItsKeeper() {
        val exercises = lifts + Exercise(6, "Old Bench", "Chest", mergedIntoId = 1)
        val state = projectIronMindDesk(model(), listOf(set(6, "2026-09-09", 1500), set(1, "2026-09-26", 1550)),
            exercises, emptyHub, asOf = today)
        assertEquals(1L, state.noticed.single().exerciseId)
    }

    @Test fun askedCapsAtThreeUserQuestionsNewestFirst() {
        val messages = (1L..5L).flatMap { id ->
            listOf(AiMessageUi(id, true, "Question $id", 1_790_000_000_000L),
            AiMessageUi(id + 10, false, "Answer", 1_790_000_000_000L))
        }
        val state = desk(messages = messages)
        assertEquals(listOf(5L, 4L, 3L), state.asked.map { it.messageId })
        assertTrue(state.asked.all { it.dateLabel.matches(Regex("[A-Z][a-z]{2} [0-9]{1,2}")) })
        assertTrue(desk().asked.isEmpty())
    }

    @Test fun cleanupCountsUncheckedSetsWhileBadgeCountsOnlyDecisions() {
        val item = UncheckedSetsItem("u", "2026-09-26", 1, "Bench", "Chest", false,
            listOf(set(1, "2026-09-26", 1500, completed = false), set(1, "2026-09-26", 1500, completed = false)), emptyList())
        val hub = HubBuild(listOf(HubDay("2026-09-26", HubDayKind.NeverVerified, listOf(item))),
            BacklogItem("backlog", "2026-08-01", "2026-08-10", 9, 2), 2)
        val history = listOf(set(1, "2026-09-09", 1500), set(1, "2026-09-26", 1550))
        val state = desk(model(listOf(group("live"))), history, hub)
        assertEquals(11, state.uncheckedSets)
        assertEquals(1, state.decisionCount)
        assertEquals(1, state.noticed.size)
        val dismissed = desk(hub = hub.withoutItems(setOf("u", "backlog")))
        assertEquals(0, dismissed.uncheckedSets)
        assertEquals(0, dismissed.decisionCount)
    }

    @Test fun observationsChooseAtMostOneNewestRowPerRule() {
        val sets = listOf(set(1, "2026-09-09", 1500), set(1, "2026-09-26", 1550)) +
            listOf("2026-09-09", "2026-09-15", "2026-09-20", "2026-09-26").map { set(2, it, 1500) } +
            listOf(set(3, "2026-09-09", 100), set(3, "2026-09-20", 150))
        val rows = desk(sets = sets).noticed
        assertEquals(listOf(1L, 2L), rows.map { it.exerciseId })
        assertEquals(listOf(NoticedKind.BeatPrevious, NoticedKind.FlatLoad), rows.map { it.kind })
    }

    @Test fun demoFixtureHasDatedNoticedEvidenceAndConsistentBadge() {
        val seed = com.ayman.ecolift.web.demo.DemoData.generate(today)
        val learned = LearnedSplitsEngine.compute(LearnedInputs(today, seed.sets, emptyList(), { it },
            slots = seed.slots, slotMembers = seed.splitExercises, workoutDays = seed.workoutDays))
        val hub = HubItems.build(seed.sets, seed.exercises.associateBy { it.id }, today.toString(), "2026-09-13")
        val state = projectIronMindDesk(learned, seed.sets, seed.exercises, hub, asOf = today)
        assertTrue(state.noticed.isNotEmpty(), "Demo has no qualifying noticed rows: ${state.noticed}")
        assertTrue(state.noticed.all { "sessions" in it.evidence && " – " in it.evidence }, "Demo evidence is missing dates or sessions")
        assertEquals(state.decisions.size, state.decisionCount)
        assertEquals(seed.sets.count { !it.completed && it.date < today.toString() }, state.uncheckedSets)
    }
}
