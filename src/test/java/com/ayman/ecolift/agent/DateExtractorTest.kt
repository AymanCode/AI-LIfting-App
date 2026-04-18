package com.ayman.ecolift.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateExtractorTest {

    // Pin a reference date: Wednesday, 2026-04-15
    private val today = LocalDate.of(2026, 4, 15)

    @Test fun `yesterday returns day before today`() {
        assertEquals("2026-04-14", DateExtractor.extract("what did I do yesterday", today))
    }

    @Test fun `today returns today`() {
        assertEquals("2026-04-15", DateExtractor.extract("show me today's session", today))
    }

    @Test fun `last Monday returns most recent past Monday`() {
        // Today is Wednesday 15th, last Monday = April 13
        assertEquals("2026-04-13", DateExtractor.extract("what did I do last Monday", today))
    }

    @Test fun `bare day name returns most recent past occurrence`() {
        // "Monday" without "last" still means the most recent past Monday
        assertEquals("2026-04-13", DateExtractor.extract("show me Monday", today))
    }

    @Test fun `day name same as today returns previous week`() {
        // Today is Wednesday; asking "Wednesday" means 7 days ago
        assertEquals("2026-04-08", DateExtractor.extract("what did I do Wednesday", today))
    }

    @Test fun `Saturday returns previous Saturday`() {
        // April 15 Wednesday → previous Saturday = April 11
        assertEquals("2026-04-11", DateExtractor.extract("Saturday workout", today))
    }

    @Test fun `month day ordinal parses correctly`() {
        assertEquals("2026-04-05", DateExtractor.extract("what did I do on April 5th", today))
    }

    @Test fun `month day without ordinal parses correctly`() {
        assertEquals("2026-03-12", DateExtractor.extract("March 12 session", today))
    }

    @Test fun `future month date resolves to previous year`() {
        // December is after April, so it resolves to 2025
        assertEquals("2025-12-01", DateExtractor.extract("December 1st", today))
    }

    @Test fun `ISO date passes through`() {
        assertEquals("2026-01-20", DateExtractor.extract("my session on 2026-01-20", today))
    }

    @Test fun `unrecognized text returns null`() {
        assertNull(DateExtractor.extract("bench press 185 lbs 5 reps", today))
    }

    @Test fun `empty string returns null`() {
        assertNull(DateExtractor.extract("", today))
    }
}
