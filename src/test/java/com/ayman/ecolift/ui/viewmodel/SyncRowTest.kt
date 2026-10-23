package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.web.supabase.CloudFailure
import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SyncRowTest {
    private val t840 = Instant.parse("2026-10-08T20:40:00Z")
    private val t851 = Instant.parse("2026-10-08T20:51:00Z")
    private val utc = TimeZone.UTC
    private fun text(parts: List<DataPart>) = parts.joinToString("") { it.text }
    private fun data(parts: List<DataPart>) = parts.filter { it.isData }.map { it.text }

    @Test
    fun up_to_date_shows_the_last_success_time_as_data() {
        val parts = syncRowParts(SyncStatus.UpToDate, CloudStamps(lastSuccessAt = t851), utc)
        assertEquals("Up to date · 8:51 PM", text(parts))
        assertEquals(listOf("8:51 PM"), data(parts))
        assertEquals("Up to date", text(syncRowParts(SyncStatus.UpToDate, CloudStamps(), utc)), "No success yet: no time")
    }

    @Test
    fun waiting_shows_the_count_and_the_failing_run_when_there_is_one() {
        val quiet = syncRowParts(SyncStatus.Waiting(3), CloudStamps(), utc)
        assertEquals("3 waiting", text(quiet))
        assertEquals(listOf("3"), data(quiet))
        val stuck = syncRowParts(SyncStatus.Waiting(3), CloudStamps(failingSince = t840), utc)
        assertEquals("3 waiting · since 8:40 PM", text(stuck))
        assertEquals(listOf("3", "8:40 PM"), data(stuck))
    }

    @Test
    fun offline_shows_since() {
        val parts = syncRowParts(SyncStatus.Offline(since = t840), CloudStamps(), utc)
        assertEquals("Offline · since 8:40 PM", text(parts))
        assertEquals(listOf("8:40 PM"), data(parts))
    }

    @Test
    fun last_failure_names_the_class_and_the_time_or_is_absent() {
        assertNull(lastFailureParts(CloudStamps(), utc))
        val parts = lastFailureParts(CloudStamps(lastFailure = CloudFailure("HttpRequestTimeoutException", "timed out", t840)), utc)!!
        assertEquals("HttpRequestTimeoutException · 8:40 PM", text(parts))
        assertEquals(listOf("HttpRequestTimeoutException", "8:40 PM"), data(parts))
    }
}
