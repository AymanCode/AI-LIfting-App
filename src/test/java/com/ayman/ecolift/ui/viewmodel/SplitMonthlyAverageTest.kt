package com.ayman.ecolift.ui.viewmodel

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitMonthlyAverageTest {
    private fun days(vararg iso: String) = iso.map { LocalDate.parse(it) }.toSet()

    @Test
    fun noLoggedDaysAveragesZero() {
        assertEquals(0.0, loggedDaysPerMonth(emptySet(), LocalDate.parse("2026-09-29")))
    }

    @Test
    fun firstMonthCountsAsOneMonth() {
        val worked = days("2026-09-02", "2026-09-10", "2026-09-28")
        assertEquals(3.0, loggedDaysPerMonth(worked, LocalDate.parse("2026-09-29")))
    }

    @Test
    fun countsEveryCalendarMonthFromTheFirstLoggedOneThroughToday() {
        // Nov, Dec, Jan and Feb are four months, across the year boundary.
        val worked = days("2025-11-03", "2025-11-20", "2025-12-01", "2025-12-15", "2026-01-05", "2026-01-19", "2026-02-02", "2026-02-09")
        assertEquals(2.0, loggedDaysPerMonth(worked, LocalDate.parse("2026-02-10")))
    }

    @Test
    fun monthsWithoutTrainingStillCount() {
        val worked = days("2026-01-15", "2026-09-01")
        assertEquals(2.0 / 9, loggedDaysPerMonth(worked, LocalDate.parse("2026-09-29")))
    }
}
