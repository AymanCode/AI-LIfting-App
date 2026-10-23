package com.ayman.ecolift.web.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NumbersTest {
    @Test
    fun thousandsGrouping() {
        assertEquals("0", groupThousands(0L))
        assertEquals("999", groupThousands(999L))
        assertEquals("1,000", groupThousands(1000L))
        assertEquals("523,450", groupThousands(523450L))
        assertEquals("1,234,567", groupThousands(1234567L))
        assertEquals("-12,345", groupThousands(-12345L))
    }

    @Test
    fun fixedDecimals() {
        // Mirrors String.format("%.1f" / "%.2f"): fixed places, ties away from zero.
        assertEquals("12.3", formatFixed(12.25, 1))
        assertEquals("12.0", formatFixed(12.0, 1))
        assertEquals("-3.5", formatFixed(-3.45, 1))
        assertEquals("0.90", formatFixed(0.9, 2))
        assertEquals("0.87", formatFixed(0.866, 2))
        assertEquals("100", formatFixed(99.6, 0))
    }

    @Test
    fun wholeGrouped() {
        // Mirrors String.format(Locale.US, "%,.0f"): rounds, then groups.
        assertEquals("3,330", formatWholeGrouped(3330.4))
        assertEquals("10,000", formatWholeGrouped(9999.5))
        assertEquals("245", formatWholeGrouped(245.49))
    }
}
