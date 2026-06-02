package com.ayman.ecolift.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class GymCalendarCardTest {
    @Test
    fun `worked marks include adjacent-month days visible in grid`() {
        val displayedMonth = YearMonth.of(2026, 6)
        val grid = buildGymCalendarGrid(displayedMonth)
        val workedDays = setOf(
            LocalDate.of(2026, 5, 31),
            LocalDate.of(2026, 6, 2),
            LocalDate.of(2026, 7, 1),
        )

        assertTrue(grid.contains(LocalDate.of(2026, 5, 31)))
        assertTrue(grid.contains(LocalDate.of(2026, 7, 1)))
        assertTrue(isGymCalendarDateWorked(LocalDate.of(2026, 5, 31), workedDays))
        assertTrue(isGymCalendarDateWorked(LocalDate.of(2026, 7, 1), workedDays))
        assertFalse(isGymCalendarDateWorked(LocalDate.of(2026, 6, 1), workedDays))
        assertEquals(1, countGymDaysInMonth(workedDays, displayedMonth))
    }
}
