package com.ayman.ecolift.web.util

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DatesTest {
    @Test
    fun yearMonthFromDateAndLength() {
        val ym = YearMonthW.from(LocalDate(2026, 2, 14))
        assertEquals(2026, ym.year)
        assertEquals(2, ym.month)
        assertEquals(28, ym.lengthOfMonth())
        assertEquals(29, YearMonthW(2024, 2).lengthOfMonth())
    }

    @Test
    fun yearMonthArithmeticAndLabel() {
        assertEquals(YearMonthW(2026, 1), YearMonthW(2025, 12).plusMonths(1))
        assertEquals(YearMonthW(2025, 11), YearMonthW(2026, 1).plusMonths(-2))
        assertEquals("June 2026", YearMonthW(2026, 6).label())
        assertEquals(LocalDate(2026, 6, 9), YearMonthW(2026, 6).atDay(9))
    }

    @Test
    fun trimmedDecimalFormatting() {
        assertEquals("12.5", formatTrimmedDecimal(12.5))
        assertEquals("12", formatTrimmedDecimal(12.0))
        assertEquals("12.25", formatTrimmedDecimal(12.25))
    }

    @Test
    fun tenthsFormatting() {
        assertEquals("135", formatTenths(1350))
        assertEquals("132.5", formatTenths(1325))
    }
}
