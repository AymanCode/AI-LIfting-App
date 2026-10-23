package com.ayman.ecolift.data.progress.highlights

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.*

class HighlightsStackTest {
    private val today = LocalDate(2026, 9, 30)
    private val lift = Exercise(1, "Dip", isBodyweight = true)
    private fun set(date: String, weight: Int, reps: Int = 8, exerciseId: Long = 1, id: Long = 1,
        completed: Boolean = true, warmup: Boolean = false) = WorkoutSet(id, exerciseId, date, id.toInt(),
        weight, reps, isBodyweight = true, completed = completed, isWarmup = warmup)
    private fun build(sets: List<WorkoutSet>, exercises: List<Exercise> = listOf(lift)) = buildHighlightReelSnapshot(exercises, sets, today)
    private fun beats(sets: List<WorkoutSet>) = build(sets).frames.filterIsInstance<ProgressHighlightFrame.Beat>()
    private fun repeats(sets: List<WorkoutSet>) = build(sets).frames.filterIsInstance<ProgressHighlightFrame.RepeatableBest>()

    @Test fun beat_requires_exact_reps_and_an_all_time_load_record() {
        assertTrue(beats(listOf(set("2026-09-12", 250), set("2026-09-23", 450, reps = 7))).isEmpty())
        assertTrue(beats(listOf(set("2026-09-01", 500, reps = 5), set("2026-09-12", 250), set("2026-09-23", 450))).isEmpty())
        val beat = beats(listOf(set("2026-09-12", 250), set("2026-09-17", 250, reps = 13), set("2026-09-23", 450))).single()
        assertEquals(8, beat.then.reps)
        assertEquals(beat.then.reps, beat.now.reps)
        assertEquals("+20 LB", beat.deltaLoadLabel)
        assertEquals("BW+25", beat.then.loadLabel)
        assertEquals(listOf(LocalDate(2026, 9, 12), LocalDate(2026, 9, 17), LocalDate(2026, 9, 23)), beat.sessions.map { it.date })
        assertEquals(listOf(0f, 5f / 11f, 1f), beat.sessionTickPositions)
    }

    @Test fun beat_accepts_45_days_and_rejects_46_days() {
        assertEquals(1, beats(listOf(set("2026-08-09", 250), set("2026-09-23", 450))).size)
        assertTrue(beats(listOf(set("2026-08-08", 250), set("2026-09-23", 450))).isEmpty())
    }

    @Test fun beat_does_not_hide_a_heavier_same_rep_set_or_compare_with_a_dip() {
        val beat = beats(listOf(set("2026-09-10", 350), set("2026-09-12", 250), set("2026-09-23", 450))).single()
        assertEquals(350, beat.then.weightLbsStored)
        assertEquals(100, beat.deltaWeightLbsStored)
        // A later, larger load at these reps prevents presenting the earlier pair as the biggest.
        val later = beats(listOf(set("2026-09-10", 250), set("2026-09-12", 450), set("2026-09-23", 500))).single()
        assertEquals(LocalDate(2026, 9, 23), later.now.date)
        assertEquals(50, later.deltaWeightLbsStored)
    }

    @Test fun beat_uses_one_daily_best_and_ignores_unfinished_warmup_and_future_sets() {
        val sets = listOf(set("2026-09-12", 250), set("2026-09-23", 350), set("2026-09-23", 450, id = 2),
            set("2026-09-24", 600, completed = false), set("2026-09-25", 600, warmup = true), set("2026-10-01", 600))
        val beat = beats(sets).single()
        assertEquals(450, beat.now.weightLbsStored)
        assertEquals(2, beat.sessions.size)
    }

    @Test fun repeatable_best_held_14_days_needs_two_distinct_sessions_with_enough_reps() {
        val sets = listOf(set("2026-09-10", 1450), set("2026-09-18", 1400), set("2026-09-24", 1450), set("2026-09-29", 1500, reps = 9))
        val best = repeats(sets).single()
        assertEquals(14, best.heldDays)
        assertEquals(2, best.sessionsAtOrAbove)
        assertEquals(1450, best.best.weightLbsStored)
        assertTrue(best.chartPoints.any { !it.atOrAbove && it.y > best.ceilingY })
        assertTrue(best.chartPoints.any { it.atOrAbove && it.y < best.ceilingY })
        assertTrue(repeats(sets.dropLast(1)).isEmpty())
        assertTrue(repeats(sets.dropLast(1) + set("2026-09-24", 1450, id = 2)).isEmpty())
        assertTrue(repeats(sets.dropLast(1) + set("2026-09-29", 1500, reps = 7)).isEmpty())
        assertTrue(repeats(sets.map { if (it.date == "2026-09-10") it.copy(date = "2026-09-11") else it }).isEmpty())
    }

    @Test fun repeatable_best_uses_a_real_old_best_and_counts_only_recent_returns() {
        val sets = listOf(set("2026-05-07", 1450), set("2026-08-25", 1450), set("2026-09-12", 1450), set("2026-09-20", 1500))
        val snapshot = build(sets)
        val best = snapshot.frames.filterIsInstance<ProgressHighlightFrame.RepeatableBest>().single()
        assertEquals(110, best.heldDays)
        assertEquals(2, best.sessionsAtOrAbove)
        assertEquals("HELD 110 DAYS", snapshot.cards.single { it.kind == HighlightKind.REPEATABLE_BEST }.tag)
        assertTrue(repeats(listOf(set("2026-05-01", 1600, reps = 10)) + sets).isEmpty())
    }

    @Test fun selection_has_21_day_freshness_newest_first_deduplication_and_cap_five() {
        val frame = beats(listOf(set("2026-09-12", 250), set("2026-09-23", 450))).single()
        fun card(id: Long, day: Int, kind: HighlightKind = HighlightKind.BEAT) = HighlightCard("$kind:$id:$day", kind, frame,
            LocalDate(2026, 9, day), id, "Lift $id", "LIFT $id", "NEW THIS WEEK", "REASON", "SEP $day")
        val candidates = (1L..7L).map { card(it, 22 + it.toInt()) } + card(1, 30) + card(8, 9) + card(9, 10) +
            card(10, 21, HighlightKind.REPEATABLE_BEST)
        val selected = selectHighlightCards(candidates, today)
        assertEquals(5, selected.cards.size)
        assertEquals(selected.cards.map { it.evidenceDate }.sortedDescending(), selected.cards.map { it.evidenceDate })
        assertEquals(selected.cards.size, selected.cards.distinctBy { it.kind to it.reportExerciseId }.size)
        assertTrue(selected.cards.none { it.reportExerciseId == 8L })
        assertTrue(selected.cards.any { it.kind == HighlightKind.REPEATABLE_BEST })
        assertEquals(30, selected.cards.single { it.reportExerciseId == 1L }.evidenceDate.day)
        assertEquals(1, selectHighlightCards(listOf(card(9, 10)), today).cards.size)
        assertTrue(selectHighlightCards(listOf(card(8, 9)), today).cards.isEmpty())
        assertEquals("SEPTEMBER 2026 · 5 HIGHLIGHTS · NEWEST FIRST", selected.kicker)
        assertEquals(selected.cards.map { it.frame }, selected.frames)
    }

    @Test fun tags_describe_truthful_rank_freshness_and_accumulation() {
        val exercises = listOf(lift, Exercise(2, "Raise", isBodyweight = true))
        val snapshot = build(listOf(set("2026-09-12", 250), set("2026-09-23", 450),
            set("2026-09-11", 200, exerciseId = 2), set("2026-09-22", 400, exerciseId = 2)), exercises)
        assertEquals(2, snapshot.cards.count { it.tag == "JOINT BIGGEST OF SEPTEMBER" })
        assertTrue(snapshot.cards.all { it.why.contains("2 LIFTS IN SEPTEMBER") })
        val bench = Exercise(1, "Bench")
        fun match(now: String) = buildHighlightReelSnapshot(listOf(bench), listOf(
            set("2026-09-12", 1000, reps = 7).copy(isBodyweight = false),
            set(now, 1040, reps = 7).copy(isBodyweight = false)), today).cards.single { it.kind == HighlightKind.MATCH_CUT }
        assertEquals("NEW THIS WEEK", match("2026-09-28").tag)
        assertEquals("NEW IN SEPTEMBER", match("2026-09-27").tag)
        val receiptDates = listOf("2026-09-09", "2026-09-12", "2026-09-16", "2026-09-19", "2026-09-23", "2026-09-26", "2026-09-28", "2026-09-30")
        val total = build(receiptDates.map { set(it, 250) }).cards.single { it.kind == HighlightKind.RUNNING_TOTAL }
        assertEquals("REPS WITH RECEIPTS", total.tag)
        assertTrue(total.why.contains("RECEIPTS"))
        assertFalse(total.why.contains("STRENGTH"))
    }

    @Test fun month_rank_does_not_hide_a_bigger_jump_outside_the_freshness_window() {
        val exercises = listOf(lift, Exercise(2, "Raise", isBodyweight = true))
        val snapshot = build(listOf(set("2026-08-20", 250), set("2026-09-01", 450), set("2026-09-15", 450), set("2026-09-24", 500),
            set("2026-09-12", 200, exerciseId = 2), set("2026-09-23", 350, exerciseId = 2)), exercises)
        assertTrue(snapshot.cards.filter { it.kind == HighlightKind.BEAT }.all { it.tag == "BIGGEST FOR THIS LIFT" })
        assertTrue(snapshot.cards.filter { it.kind == HighlightKind.BEAT }.all { it.why.contains("IN 21 DAYS") })
    }

    @Test fun an_identical_match_cut_and_beat_fact_is_only_shown_once() {
        val bench = Exercise(1, "Bench")
        val snapshot = buildHighlightReelSnapshot(listOf(bench), listOf(
            set("2026-09-12", 1000, reps = 7).copy(isBodyweight = false),
            set("2026-09-23", 1040, reps = 7).copy(isBodyweight = false)), today)
        assertEquals(1, snapshot.cards.size)
        assertEquals(HighlightKind.MATCH_CUT, snapshot.cards.single().kind)
    }

    @Test fun no_candidates_keeps_the_existing_empty_state_contract() {
        val empty = build(emptyList())
        assertTrue(empty.frames.isEmpty())
        assertTrue(empty.cards.isEmpty())
        assertEquals("", empty.kicker)
        assertTrue(build(listOf(set("2026-07-01", 250), set("2026-07-15", 450))).cards.isEmpty())
    }
}
