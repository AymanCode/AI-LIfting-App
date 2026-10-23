package com.ayman.ecolift.cardio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardioEntryParserTest {
    @Test
    fun parses_a_basic_manual_entry() {
        assertEquals(1800, CardioEntryParser.parseDurationSeconds("30:00"))
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
    fun duration_parser_rejects_values_at_integer_wrap_boundaries() {
        listOf("35791395", "71582788", "71582789", "71582790", "2147483647").forEach { minutes ->
            assertNull(CardioEntryParser.parseDurationSeconds(minutes), minutes)
            assertNull(CardioEntryParser.parseDurationSeconds("$minutes:00"), "$minutes:00")
        }
        listOf("596524:00:00", "1193046:00:00", "1193047:00:00", "2147483647:59:59").forEach { clock ->
            assertNull(CardioEntryParser.parseDurationSeconds(clock), clock)
        }
    }

    @Test
    fun duration_parser_keeps_the_six_hour_upper_bound_in_every_form() {
        listOf("360", "360:00", "6:00:00").forEach { text ->
            assertEquals(21600, CardioEntryParser.parseDurationSeconds(text), text)
        }
        listOf("361", "360:01", "6:00:01", "9223372036854775807:59:59").forEach { text ->
            assertNull(CardioEntryParser.parseDurationSeconds(text), text)
        }
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
