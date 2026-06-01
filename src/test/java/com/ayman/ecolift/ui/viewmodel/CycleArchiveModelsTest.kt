package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.ArchivedCycle
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleArchiveModelsTest {
    @Test
    fun `format archive date range omits duplicate year`() {
        assertEquals("Apr 1 - Apr 30, 2026", formatArchiveDateRange("2026-04-01", "2026-04-30"))
    }

    @Test
    fun `format archive date range keeps both years when crossing year boundary`() {
        assertEquals("Dec 28, 2025 - Jan 5, 2026", formatArchiveDateRange("2025-12-28", "2026-01-05"))
    }

    @Test
    fun `archive card ui maps denormalized columns`() {
        val card = ArchivedCycle(
            id = 7L,
            name = "Spring Block",
            startDate = "2026-04-01",
            endDate = "2026-04-30",
            splitCount = 3,
            totalVolumeLbs = 98_765L,
            totalSessions = 12,
            archivedAt = 0L,
            schemaVersion = 1,
            snapshotJson = "{}",
        ).toCardUi()

        assertEquals(7L, card.id)
        assertEquals("Spring Block", card.name)
        assertEquals("Apr 1 - Apr 30, 2026", card.dateRangeLabel)
        assertEquals(3, card.splitCount)
        assertEquals(12, card.sessionCount)
        assertEquals(98_765L, card.totalVolumeLbs)
    }

    @Test
    fun `format signed lbs rounds and treats small deltas as no change`() {
        assertEquals("+25 lb", formatSignedLbs(25f))
        assertEquals("+13 lb", formatSignedLbs(12.6f))
        assertEquals("-12 lb", formatSignedLbs(-12f))
        assertEquals("no change", formatSignedLbs(0.3f))
    }
}
