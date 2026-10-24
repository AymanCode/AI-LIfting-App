package com.ayman.ecolift.android.services

import com.ayman.ecolift.data.CardioSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthConnectImportSkipTest {
    @Test
    fun a_dismissed_uid_without_a_row_is_skipped_and_a_stored_one_still_updates() = runTest {
        val dismissed = MemoryDismissed()
        rememberDeletedHealthConnectUid("hc-9", dismissed)
        rememberDeletedHealthConnectUid("hc-kept", dismissed)
        val rows = mutableListOf(healthSession("hc-kept", durationSec = 100))
        val upserts = mutableListOf<String>()
        val state = ImportState(
            stored = setOf("hc-kept"),
            dismissed = dismissed.all(),
            seen = seenHealthConnectUids(rows.map { it.hcUid }, dismissed),
            upsert = { session ->
                upserts += session.hcUid.orEmpty()
                val index = rows.indexOfFirst { it.hcUid == session.hcUid }
                if (index >= 0) rows[index] = session else rows += session
            },
        )

        importSessions(
            listOf(
                healthSession("hc-9", durationSec = 500),
                healthSession("hc-kept", durationSec = 999),
                healthSession("hc-new", durationSec = 700),
            ),
            state,
        )

        assertFalse(upserts.contains("hc-9"), "dismissed uid with no row was upserted: $upserts")
        assertTrue(upserts.contains("hc-kept"), "stored dismissed uid was not updated: $upserts")
        assertTrue(upserts.contains("hc-new"), "new uid was not imported: $upserts")
        assertEquals(1, state.added)
        assertEquals(null, rows.firstOrNull { it.hcUid == "hc-9" }?.hcUid)
        assertEquals(999, rows.single { it.hcUid == "hc-kept" }.durationSec)
        assertEquals(700, rows.single { it.hcUid == "hc-new" }.durationSec)
    }

    @Test
    fun a_row_saved_before_a_failing_upsert_stays_counted() = runTest {
        val rows = mutableListOf<String>()
        val state = ImportState(
            stored = emptySet(),
            dismissed = emptySet(),
            seen = mutableSetOf(),
            upsert = { session ->
                check(rows.isEmpty()) { "second upsert fails" }
                rows += session.hcUid.orEmpty()
            },
        )

        val failure = runCatching {
            importSessions(listOf(healthSession("hc-a", durationSec = 300), healthSession("hc-b", durationSec = 400)), state)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException, "the second upsert should have thrown: $failure")
        assertEquals(listOf("hc-a"), rows)
        assertEquals(1, state.added)
    }
}

private fun healthSession(uid: String, durationSec: Int): CardioSession = sessionFromHealthConnect(
    activityType = "ROW",
    startEpochMilli = 1_700_000_000_000,
    endEpochMilli = 1_700_000_000_000 + durationSec * 1000L,
    zoneOffsetSeconds = 0,
    hcUid = uid,
    dataOriginPackage = "test",
    lastModifiedEpochMilli = 1_700_000_000_000,
    nowEpochMilli = 1_700_000_100_000,
    localUuid = "local-$uid-$durationSec",
)

private class MemoryDismissed : DismissedHealthConnectUids {
    private val uids = mutableSetOf<String>()

    override fun all(): Set<String> = uids.toSet()

    override fun dismiss(uid: String) {
        if (uid.isNotBlank()) uids += uid
    }
}
