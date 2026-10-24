package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.cardio.session
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardioWeeklyDurationTest {
    @Test
    fun legacy_huge_duration_does_not_overflow_the_weekly_summary() {
        val sessions = listOf(session("2026-10-23", minutes = 30).copy(durationSec = Int.MAX_VALUE)) +
            (1..9).map { session("2026-10-23", minutes = 30, id = it.toLong()) }
        assertEquals(2147499847L, cardioWeekDurationSeconds(sessions, LocalDate(2026, 10, 23)))
    }

    @Test
    fun weekly_duration_uses_only_active_sessions_inside_the_rolling_week() {
        val sessions = listOf(
            session("2026-10-17", minutes = 30),
            session("2026-10-23", minutes = 15),
            session("2026-10-16", minutes = 120),
            session("2026-10-24", minutes = 120),
            session("2026-10-23", minutes = 0),
        )
        assertEquals(2700L, cardioWeekDurationSeconds(sessions, LocalDate(2026, 10, 23)))
    }

    @Test
    fun week_month_and_year_totals_stay_positive_for_a_huge_day() {
        val sessions = listOf(session("2026-10-23", minutes = 30).copy(durationSec = Int.MAX_VALUE)) +
            (1..9).map { session("2026-10-23", minutes = 30, id = it.toLong()) }
        val today = LocalDate(2026, 10, 23)
        val expected = 2147499847L

        val minutes = CardioInsights.weekArc(sessions, today).first { it.date == today }.minutes
        val monthSeconds = CardioInsights.monthBars(sessions, today).first { it.year == 2026 && it.month == 10 }.totalSeconds
        val yearSeconds = CardioInsights.yearColumns(sessions, 2026)[9]

        assertEquals(35791664, minutes)
        assertTrue(minutes > 0)
        assertEquals(expected, monthSeconds)
        assertTrue(monthSeconds > 0)
        assertEquals(expected, yearSeconds)
        assertTrue(yearSeconds > 0)
        assertEquals("596527h 44m", formatDurationCompact(monthSeconds))
    }
}
