package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.data.room.toRoom
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.util.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DemoSeedLocalDateTest {
    @OptIn(ExperimentalTime::class)
    @Test
    fun seededTodayRowsStayOnTheLocalDateWhenUtcIsTheNextDay() {
        val zone = TimeZone.of("America/Los_Angeles")
        val instant = Instant.parse("2026-10-22T06:30:00Z")
        val anchor = instant.toLocalDateTime(zone).date
        val utcDate = instant.toLocalDateTime(TimeZone.UTC).date
        assertEquals(LocalDate(2026, 10, 21), anchor)
        assertEquals(LocalDate(2026, 10, 22), utcDate)

        val seed = DemoData.generate(anchor)
        assertEquals(anchor, seed.anchorDate)

        val todaySets = seed.sets.filter { it.date == "2026-10-21" }
        assertTrue(todaySets.isNotEmpty())
        todaySets.forEach { set ->
            val stored = set.toRoom().toDomain()
            assertEquals("2026-10-21", stored.date)
            assertEquals(anchor, LocalDate.parse(stored.date))
        }

        val todayDays = seed.workoutDays.filter { it.date == "2026-10-21" }
        assertTrue(todayDays.isNotEmpty())
        todayDays.forEach { day ->
            assertEquals("2026-10-21", day.toRoom().toDomain().date)
        }

        val newest = seed.sets.maxOf { it.date }
        assertEquals("2026-10-22", newest)
        assertEquals(anchor.plusDays(1).toString(), newest)
    }
}
