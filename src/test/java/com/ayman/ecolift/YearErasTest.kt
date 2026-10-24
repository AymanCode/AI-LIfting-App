package com.ayman.ecolift

import com.ayman.ecolift.data.splits.buildEraBands
import com.ayman.ecolift.ui.viewmodel.ArchiveCardUi
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YearErasTest {
    private val today = LocalDate(2026, 7, 17)
    private val windowStart = LocalDate(2025, 7, 21)
    private val windowEnd = LocalDate(2026, 7, 19)

    @Test
    fun archive_inside_window_keeps_its_range() {
        val bands = buildEraBands(
            archives = listOf(archive(1L, "Spring block", "2026-06-01", "2026-06-30")),
            earliestWorkout = null,
            today = today,
        )

        val archived = bands.first()
        assertEquals(1L, archived.archiveId)
        assertEquals("Spring block", archived.name)
        assertEquals(fraction(LocalDate(2026, 6, 1)), archived.startFrac)
        assertEquals(fraction(LocalDate(2026, 6, 30)), archived.endFrac)
        assertEquals("Current cycle", bands.last().name)
    }

    @Test
    fun archive_straddling_window_start_is_clipped() {
        val archived = buildEraBands(
            archives = listOf(archive(1L, "Summer block", "2025-07-01", "2025-08-01")),
            earliestWorkout = null,
            today = today,
        ).first()

        assertEquals(0f, archived.startFrac)
        assertEquals(fraction(LocalDate(2025, 8, 1)), archived.endFrac)
    }

    @Test
    fun no_archives_uses_earliest_workout_for_current_cycle() {
        val earliest = LocalDate(2025, 8, 1)
        val bands = buildEraBands(
            archives = emptyList(),
            earliestWorkout = earliest,
            today = today,
        )

        assertEquals(1, bands.size)
        assertEquals(null, bands.single().archiveId)
        assertEquals(fraction(earliest), bands.single().startFrac)
        assertEquals(fraction(today), bands.single().endFrac)
    }

    @Test
    fun no_workouts_at_all_returns_empty_list() {
        assertTrue(buildEraBands(emptyList(), earliestWorkout = null, today = today).isEmpty())
    }

    @Test
    fun malformed_archive_is_dropped() {
        val bands = buildEraBands(
            archives = listOf(archive(1L, "Broken", "not-a-date", "2026-06-30")),
            earliestWorkout = null,
            today = today,
        )

        assertTrue(bands.isEmpty())
    }

    private fun archive(
        id: Long,
        name: String,
        startIso: String,
        endIso: String,
    ) = ArchiveCardUi(
        id = id,
        name = name,
        startIso = startIso,
        endIso = endIso,
        dateRangeLabel = "$startIso - $endIso",
        splitCount = 3,
        sessionCount = 12,
        totalVolumeLbs = 100_000L,
    )

    private fun fraction(date: LocalDate): Float =
        (date.toEpochDays() - windowStart.toEpochDays()).toFloat() /
            (windowEnd.toEpochDays() - windowStart.toEpochDays()).toFloat()
}
