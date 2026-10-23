package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.*
import com.ayman.ecolift.data.progress.*
import kotlin.test.*

class ArchiveLinerNotesTest {
    private fun set(id: Long, date: String, load: Int? = 225, reps: Int? = 13, number: Int = 1) =
        WorkoutSet(exerciseId = id, date = date, setNumber = number, weightLbs = load, reps = reps, completed = true)

    private fun snapshot(rows: List<WorkoutSet>, bodyweightIds: Set<Long> = emptySet()): CycleSnapshot {
        val cycle = rows.filter { it.date in "2026-09-01".."2026-09-30" && it.completed && !it.isWarmup }
        return CycleSnapshot(startDate = "2026-09-01", endDate = "2026-09-30",
            totals = CycleTotals(cycle.map { it.date }.distinct().size, 0, cycle.size, 30),
            splits = listOf(SplitSnapshot(1, name = "Split", orderIndex = 0, firstUsedDate = null,
                lastUsedDate = null, usageCount = 0, exercises = cycle.groupBy { it.exerciseId }.map { (id, sets) ->
                    ExerciseSnapshot(id, "Lift $id", id in bodyweightIds,
                        sets.groupBy { it.date }.map { (date, day) -> SessionPoint(date, null, null, 0, 0, day.size) },
                        null, null, null, null, null, null)
                })))
    }
    private fun notes(rows: List<WorkoutSet>, window: ComparisonWindow = ComparisonWindow.M3,
        bodyweight: Set<Long> = emptySet()) = buildArchiveLinerNotes(snapshot(rows, bodyweight), rows, window)
    private fun prior(id: Long) = listOf(set(id, "2026-08-01"), set(id, "2026-08-15"))

    @Test fun relevanceUsesSessionsThenDistinctWeeksThenFairComparison() {
        val rows = prior(1) + prior(2) + prior(3) + listOf(set(4, "2026-08-01")) +
            listOf("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04", "2026-09-05").map { set(1, it) } +
            listOf("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04").map { set(2, it) } +
            listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22").flatMap { listOf(set(3, it), set(4, it)) }
        val tracks = notes(rows).setlist
        assertEquals(listOf(1L, 3L, 4L, 2L), tracks.map { it.exerciseId })
        assertEquals(listOf("01", "02", "03", "04"), tracks.map { it.indexLabel })
        assertEquals(listOf(1, 4, 4, 1), tracks.map { it.weeksSpanned })
        assertTrue(tracks[1].fairComparison)
        assertFalse(tracks[2].fairComparison)
    }

    @Test fun fairComparisonTieBreakChangesWithWindowWithoutMakingOldLiftsNew() {
        val rows = prior(1) + listOf(set(2, "2026-06-10"), set(2, "2026-06-20")) +
            listOf("2026-09-01", "2026-09-08", "2026-09-15").flatMap { listOf(set(1, it), set(2, it)) }
        assertTrue(notes(rows, ComparisonWindow.M3).earlyHints.all { it.fairComparison })
        assertFalse(notes(rows, ComparisonWindow.M1).earlyHints.single { it.exerciseId == 2L }.fairComparison)
        assertTrue(notes(rows, ComparisonWindow.M1).credits.isEmpty())
    }

    @Test fun firstCycleRanksAllRegularLiftsAsNewAndOmitsCredits() {
        val rows = (1L..4L).flatMap { id ->
            (1..id.toInt()).map { day -> set(id, "2026-09-0$day") }
        }
        val n = notes(rows)
        assertEquals(listOf(4L), n.setlist.map { it.exerciseId })
        assertEquals(listOf(3L), n.earlyHints.map { it.exerciseId })
        assertEquals(listOf("01", "02"), (n.setlist + n.earlyHints).map { it.indexLabel })
        assertTrue((n.setlist + n.earlyHints).all { it.isNew && " · NEW · " in it.description })
        assertTrue(n.credits.isEmpty())
        assertTrue(n.deepCuts.isEmpty())
        assertTrue(n.versus.isEmpty())
        assertEquals("4 sessions · 4 weeks 2 days · 4 lifts", n.heroCounts)
    }

    @Test fun newcomerWithFourSessionsRanksInSetlistWithNewMarkInsteadOfCredits() {
        val rows = listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-30").map { set(1, it) } +
            listOf(set(1, "2026-08-01").copy(completed = false), set(1, "2026-08-02").copy(isWarmup = true)) +
            prior(2) + listOf(set(2, "2026-09-01"), set(2, "2026-09-30"))
        val n = notes(rows)
        assertEquals(listOf(1L), n.setlist.map { it.exerciseId })
        assertTrue(n.setlist.single().isNew)
        assertTrue(" · NEW · " in n.setlist.single().description)
        assertEquals("First logged Sep 1", n.setlist.single().firstLoggedLabel)
        assertTrue(n.credits.isEmpty() && n.earlyHints.isEmpty() && n.versus.none { it.exerciseId == 1L })
        assertEquals(listOf(2L), n.deepCuts.map { it.exerciseId })
        assertFalse(n.deepCuts.single().isNew)
    }

    @Test fun newcomerWithTwoSessionsIsACreditAndReturningThinLiftIsADeepCut() {
        val rows = prior(1) +
            listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-30").map { set(1, it) } +
            listOf(set(2, "2026-09-01"), set(2, "2026-09-30")) +
            prior(3) + listOf(set(3, "2026-09-30"))
        val n = notes(rows)
        assertEquals(listOf(1L), n.setlist.map { it.exerciseId })
        assertFalse(n.setlist.single().isNew)
        assertFalse(" · NEW · " in n.setlist.single().description)
        assertEquals(listOf(2L), n.credits.map { it.exerciseId })
        assertEquals(2, n.credits.single().sessions)
        assertTrue(n.credits.single().isNew)
        assertEquals("First logged Sep 1", n.credits.single().firstLoggedLabel)
        assertEquals(listOf(3L), n.deepCuts.map { it.exerciseId })
        assertFalse(n.deepCuts.single().isNew)
    }

    @Test fun returningOneAndTwoSessionsFoldAndThreeSessionsAreEarlyHints() {
        val rows = (1L..4L).flatMap(::prior) + (1L..4L).flatMap { id ->
            (1..id.toInt()).map { day -> set(id, "2026-09-0$day") }
        }
        val n = notes(rows)
        assertEquals(listOf(4L), n.setlist.map { it.exerciseId })
        assertEquals(listOf(3L), n.earlyHints.map { it.exerciseId })
        assertEquals(listOf(2L, 1L), n.deepCuts.map { it.exerciseId })
        assertEquals("Deep cuts · 2 lifts · 1–2 sessions each", n.deepCutsLabel)
        assertEquals("Sep 1 → Sep 2", n.deepCuts.first().dateLabel)
    }

    @Test fun winsRewardHeavierLoadOrMoreRepsAtIdenticalLoadOnly() {
        val first = ArchiveTopSet(225, 13)
        assertEquals(ArchiveWin("+10 lb", false), archiveWin(first, ArchiveTopSet(325, 8)))
        assertEquals(ArchiveWin("+2 reps", true), archiveWin(first, ArchiveTopSet(225, 15)))
        assertEquals(ArchiveWin("+1 rep", true), archiveWin(first, ArchiveTopSet(225, 14)))
        assertNull(archiveWin(first, ArchiveTopSet(200, 20)))
        assertNull(archiveWin(first, first))
        assertNull(archiveWin(null, first))
        assertNull(archiveWin(first, null))
    }

    @Test fun bodyweightShowsRawRepsAndAddedLoadAndNoPercentage() {
        val rows = prior(1).map { it.copy(isBodyweight = true, weightLbs = 0, reps = 6) } +
            listOf(set(1, "2026-09-01", null, 8), set(1, "2026-09-30", 450, 8))
                .map { it.copy(isBodyweight = true) }
        val comparison = CycleComparison(ComparisonWindow.M3,
            listOf(LiftComparison(1, "Lift 1", LiftMetric.ADDED_WEIGHT, true, false,
                10f, 45f, 350f, 100f, 10f, 100f, Movement.IMPROVED, 2)),
            100f, 100f, 100f, 1, 0, 0, 1)
        val n = buildArchiveLinerNotes(snapshot(rows, setOf(1)), rows, ComparisonWindow.M3, comparison)
        assertEquals("BW×8 → BW+45×8", n.deepCuts.single().topSetsLabel)
        assertEquals(ArchiveWin("+45 lb", false), n.deepCuts.single().win)
        assertNull(n.versus.single().comparisonLabel)
        assertFalse(n.versus.single().description.contains("%"))
    }

    @Test fun topSetIsHighestRawLoadThenRepsAndSessionsAreDistinctDates() {
        val rows = prior(1) + listOf(set(1, "2026-09-01", 225, 13), set(1, "2026-09-01", 225, 15, 2),
            set(1, "2026-09-01", 200, 20, 3), set(1, "2026-09-30", 325, 8))
        val row = notes(rows).deepCuts.single()
        assertEquals(2, row.sessions)
        assertEquals("22.5×15 → 32.5×8", row.topSetsLabel)
        assertEquals(listOf(.18f, 1f), row.waveform)
    }

    @Test fun waveformUsesOnlyThisLiftsScaleAndFlatLoadUsesReps() {
        val rows = prior(1) + listOf(set(1, "2026-09-01", 225, 8), set(1, "2026-09-30", 225, 12)) +
            prior(2) + listOf(set(2, "2026-09-01", 10000, 8), set(2, "2026-09-30", 20000, 8))
        val tracks = notes(rows).deepCuts
        assertEquals(tracks[0].waveform, tracks[1].waveform)
        assertTrue(tracks.flatMap { it.waveform }.all { it in 0f..1f })
    }

    @Test fun legacyMissingTopSetDoesNotInventRepsOrAWin() {
        val rows = prior(1) + listOf(set(1, "2026-09-01"), set(1, "2026-09-30", reps = null))
        val row = notes(rows).deepCuts.single()
        assertEquals("22.5×13", row.topSetsLabel)
        assertNull(row.win)
        assertEquals(0f, row.waveform.last())
    }

    @Test fun snapshotMembershipKeepsMergedSplitsUniqueAndExcludesLaterHistory() {
        val rows = prior(1) + listOf(set(1, "2026-09-01"), set(1, "2026-09-30"))
        val s = snapshot(rows)
        val n = buildArchiveLinerNotes(s.copy(splits = s.splits + s.splits.single().copy(slotId = 2)),
            rows + set(2, "2026-09-10") + set(1, "2026-10-01", 9999), ComparisonWindow.M3)
        assertEquals(1, n.deepCuts.size)
        assertEquals("2 sessions · 4 weeks 2 days · 1 lift", n.heroCounts)
        assertEquals(2, n.weeklySets.sum())
        assertEquals("22.5×13 → 22.5×13", n.deepCuts.single().topSetsLabel)
    }

    @Test fun noLabelContainsMissingValuePlaceholderOrFailureCopy() {
        val rows = prior(1) + listOf(set(1, "2026-09-01", load = null, reps = null), set(2, "2026-09-30"))
        val n = notes(rows)
        val labels = (n.setlist + n.earlyHints + n.credits + n.deepCuts + n.versus)
            .flatMap { listOf(it.description, it.firstLoggedLabel, it.comparisonLabel.orEmpty(), it.win?.label.orEmpty()) } +
            listOf(n.heroCounts, n.deepCutsLabel)
        assertTrue(labels.none { "N/A" in it || "failure" in it || "need more" in it })
        assertTrue(n.deepCuts.single().topSetsLabel.isEmpty())
    }
}
