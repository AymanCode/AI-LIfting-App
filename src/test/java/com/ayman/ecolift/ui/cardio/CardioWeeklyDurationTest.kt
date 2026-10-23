package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.cardio.session
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
