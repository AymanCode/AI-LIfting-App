package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutDatesTest {
    @Test
    fun addDays_crosses_month_boundary() {
        assertEquals("2026-07-01", WorkoutDates.addDays("2026-06-30", 1))
        assertEquals("2026-06-29", WorkoutDates.addDays("2026-07-01", -2))
    }

    @Test
    fun formatHeader_matches_android_pattern() {
        // 2026-06-08 is a Monday -> "EEE, MMM d" (avoids today-is-today special case)
        assertEquals("Mon, Jun 8", WorkoutDates.formatHeader("2026-06-08"))
    }

    @Test
    fun formatHeader_today_is_today() {
        assertEquals("Today", WorkoutDates.formatHeader(WorkoutDates.today()))
    }

    @Test
    fun formatAxis_matches_android_pattern() {
        assertEquals("Jun 10", WorkoutDates.formatAxis("2026-06-10"))
        assertEquals("Dec 3", WorkoutDates.formatAxis("2026-12-03"))
    }

    @Test
    fun startOfWeek_is_sunday_based_like_android() {
        // Android: ld.minusDays(ld.dayOfWeek.value % 7) -> Sunday start.
        assertEquals("2026-06-07", WorkoutDates.startOfWeek("2026-06-10")) // Wed -> prior Sun
        assertEquals("2026-06-07", WorkoutDates.startOfWeek("2026-06-07")) // Sun -> itself
        assertEquals("2026-06-07", WorkoutDates.startOfWeek("2026-06-08")) // Mon -> prior Sun
    }
}
