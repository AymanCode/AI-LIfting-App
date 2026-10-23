package com.ayman.ecolift.web.util

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ClockLabelTest {
    @Test
    fun twelve_hour_clock_with_am_pm() {
        assertEquals("8:51 PM", clockLabel(Instant.parse("2026-10-08T20:51:00Z"), TimeZone.UTC))
        assertEquals("8:05 AM", clockLabel(Instant.parse("2026-10-08T08:05:00Z"), TimeZone.UTC))
        assertEquals("12:00 AM", clockLabel(Instant.parse("2026-10-08T00:00:00Z"), TimeZone.UTC))
        assertEquals("12:30 PM", clockLabel(Instant.parse("2026-10-08T12:30:00Z"), TimeZone.UTC))
    }
}
