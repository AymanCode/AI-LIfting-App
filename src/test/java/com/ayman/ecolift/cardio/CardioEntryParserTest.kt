package com.ayman.ecolift.cardio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioEntryParserTest {
    @Test
    fun parses_a_basic_manual_entry() {
        // Smoke test that the pure parser survived the port; adapt to its real return type.
        val result = CardioEntryParser.parseDurationSeconds("30:00")
        assertTrue(result != null && result > 0)
    }

    @Test
    fun duration_parser_rejects_impossible_clock_segments() {
        assertNull(CardioEntryParser.parseDurationSeconds("10:75"))
        assertNull(CardioEntryParser.parseDurationSeconds("1:60:00"))
        assertNull(CardioEntryParser.parseDurationSeconds("-1:30"))
        assertNull(CardioEntryParser.parseDurationSeconds("0"))
        assertNull(CardioEntryParser.parseDurationSeconds("0:00"))
    }

    @Test
    fun duration_parser_accepts_minutes_and_clock_values() {
        assertEquals(45 * 60, CardioEntryParser.parseDurationSeconds("45"))
        assertEquals(12 * 60 + 5, CardioEntryParser.parseDurationSeconds("12:05"))
        assertEquals(3600 + 2 * 60 + 3, CardioEntryParser.parseDurationSeconds("1:02:03"))
    }

    @Test
    fun metric_parsers_reject_non_positive_values() {
        assertNull(CardioEntryParser.parseMilesToMeters("0"))
        assertNull(CardioEntryParser.parseMilesToMeters("-1"))
        assertNull(CardioEntryParser.parseMphToMetersPerSecond("0"))
        assertNull(CardioEntryParser.parseMphToMetersPerSecond("-4"))
        assertNull(CardioEntryParser.parseInt("0"))
        assertNull(CardioEntryParser.parseInt("-12"))
    }

    @Test
    fun metric_parsers_reject_non_finite_values() {
        assertNull(CardioEntryParser.parseMilesToMeters("Infinity"))
        assertNull(CardioEntryParser.parseMphToMetersPerSecond("Infinity"))
        assertNull(CardioEntryParser.parseDouble("Infinity"))
        assertNull(CardioEntryParser.parseDouble("NaN"))
    }
}
