package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioRecordsTest {
    private val today = LocalDate(2026, 9, 9)
    private val tm = CardioActivityType.TREADMILL

    @Test fun machine_needs_three_sessions_before_any_record() {
        val two = listOf(session("2026-09-01", metres = 3000.0), session("2026-09-02", metres = 4000.0))
        assertTrue(CardioInsights.records(two, today).none { it.machine == tm })
        val three = two + session("2026-09-03", metres = 1000.0)
        assertEquals(4000.0, CardioInsights.records(three, today).first { it.kind == RecordKind.FARTHEST }.value)
    }

    @Test fun new_needs_a_previous_best_and_fourteen_days() {
        val s = listOf(session("2026-07-01", minutes = 20), session("2026-07-05", minutes = 25), session("2026-09-05", minutes = 50))
        val r = CardioInsights.records(s, today).first { it.kind == RecordKind.LONGEST_SESSION }
        assertEquals(50.0 * 60, r.value); assertEquals(25.0 * 60, r.previous); assertTrue(r.isNew)
        val old = CardioInsights.records(s, LocalDate(2026, 10, 1)).first { it.kind == RecordKind.LONGEST_SESSION }
        assertEquals(false, old.isNew)
    }

    @Test fun a_tie_keeps_the_earlier_date_and_first_ever_value_is_not_new() {
        val s = listOf(session("2026-09-01", minutes = 40), session("2026-09-02", minutes = 30), session("2026-09-05", minutes = 40))
        val r = CardioInsights.records(s, today).first { it.kind == RecordKind.LONGEST_SESSION }
        assertEquals(LocalDate(2026, 9, 1), r.date); assertEquals(false, r.isNew); assertNull(r.previous)
    }

    @Test fun biggest_week_and_month_only_count_complete_periods() {
        val s = listOf(session("2026-09-07", minutes = 300, metres = 90000.0), session("2026-08-03", minutes = 100, metres = 5000.0), session("2026-08-04", minutes = 100), session("2026-08-05", minutes = 100))
        val recs = CardioInsights.records(s, today)
        assertEquals(LocalDate(2026, 8, 3), recs.first { it.kind == RecordKind.BIGGEST_WEEK }.date)   // week of Sep 7 is incomplete on Sep 9
        assertEquals(LocalDate(2026, 8, 3), recs.first { it.kind == RecordKind.BIGGEST_MONTH }.date)
    }

    @Test fun derived_pace_by_machine() {
        assertEquals(600, CardioInsights.derivedPaceSec(session("2026-09-01", minutes = 10, metres = 1609.344)))
        assertEquals(120, CardioInsights.derivedPaceSec(session("2026-09-01", type = CardioActivityType.ROW, minutes = 8, metres = 2000.0)))
        assertEquals(90, CardioInsights.derivedPaceSec(session("2026-09-01", type = CardioActivityType.SWIM, minutes = 15, metres = 1000.0)))
        assertNull(CardioInsights.derivedPaceSec(session("2026-09-01", minutes = 10)))
    }

    @Test fun session_deltas_compare_with_the_previous_same_machine_session() {
        val prev = session("2026-09-03", minutes = 28, metres = 4345.0, hr = 150, speedMps = 2.41, calories = 325, id = 1)
        val cur = session("2026-09-09", minutes = 32, metres = 4989.0, hr = 148, speedMps = 2.59, calories = 355, id = 2)
        val d = CardioInsights.sessionDeltas(cur, listOf(prev, cur, session("2026-09-07", type = CardioActivityType.BIKE)))
        assertEquals(1L, d.previous?.id)
        assertTrue(d.deltas.first { it.field == CardioEntryField.DISTANCE_MILES }.improved)
        assertEquals(false, d.deltas.first { it.field == CardioEntryField.AVG_HEART_RATE }.improved) // speed differs by more than 5%
        val same = CardioInsights.sessionDeltas(cur.copy(avgSpeed = 2.45), listOf(prev, cur))
        assertTrue(same.deltas.first { it.field == CardioEntryField.AVG_HEART_RATE }.improved)
    }
}
