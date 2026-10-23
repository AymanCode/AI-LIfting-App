package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.test.runVmTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveWeeklyCacheTest {
    private val loads = mutableListOf<Long>()
    private val results = mutableMapOf<Long, () -> List<Int>>()
    private val cache = ArchiveWeeklyCache { id ->
        loads += id
        results.getValue(id)()
    }

    @Test fun each_archive_loads_once_across_refreshes() = runVmTest {
        results[1] = { listOf(3, 4) }
        results[2] = { listOf(2) }
        val rows = listOf(row(1), row(2))
        assertTrue(cache.refresh(rows))
        assertFalse(cache.refresh(rows))
        assertFalse(cache.refresh(rows.map { it.copy() }))
        assertEquals(listOf(1L, 2L), loads)
        assertEquals(listOf(3, 4), cache.countsFor(1))
        assertEquals(listOf(2), cache.countsFor(2))
    }

    @Test fun only_a_new_or_changed_archive_loads_again() = runVmTest {
        results[1] = { listOf(3, 4) }
        results[2] = { listOf(2) }
        results[3] = { listOf(5) }
        cache.refresh(listOf(row(1), row(2)))
        loads.clear()
        results[2] = { listOf(1, 1) }
        assertTrue(cache.refresh(listOf(row(1), row(2, name = "Renamed"), row(3))))
        assertEquals(listOf(2L, 3L), loads)
        assertEquals(listOf(1, 1), cache.countsFor(2))
        assertEquals(listOf(5), cache.countsFor(3))
    }

    @Test fun a_failed_reload_keeps_the_counts_it_had() = runVmTest {
        results[1] = { listOf(3, 4) }
        cache.refresh(listOf(row(1)))
        results[1] = { error("snapshot unreadable") }
        cache.refresh(listOf(row(1, name = "Renamed")))
        assertEquals(listOf(3, 4), cache.countsFor(1))
    }

    @Test fun a_first_failure_leaves_it_empty_and_is_not_retried_for_the_same_row() = runVmTest {
        results[1] = { error("snapshot unreadable") }
        cache.refresh(listOf(row(1)))
        cache.refresh(listOf(row(1)))
        assertTrue(cache.countsFor(1).isEmpty())
        assertEquals(listOf(1L), loads)
    }

    private fun row(id: Long, name: String = "Cycle $id") = ArchivedCycle(
        id = id,
        name = name,
        startDate = "2026-01-05",
        endDate = "2026-04-26",
        splitCount = 4,
        totalVolumeLbs = 941_380L,
        totalSessions = 64,
        archivedAt = 0L,
        schemaVersion = 1,
        snapshotJson = "{}",
    )
}
