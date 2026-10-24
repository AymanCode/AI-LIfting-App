package com.ayman.ecolift.android.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DismissedHealthConnectUidTest {
    @Test
    fun a_deleted_uid_stays_deleted_on_the_next_import() {
        val dismissed = MemoryDismissedHealthConnectUids()
        val stored = mutableSetOf("hc-9")
        stored.remove("hc-9")
        rememberDeletedHealthConnectUid("hc-9", dismissed)

        val seen = seenHealthConnectUids(stored, dismissed)
        assertFalse(claimNewHealthConnectUid(seen, "hc-9"))
        assertEquals(emptySet<String>(), stored)
    }
}

private class MemoryDismissedHealthConnectUids : DismissedHealthConnectUids {
    private val uids = mutableSetOf<String>()

    override fun all(): Set<String> = uids.toSet()

    override fun dismiss(uid: String) {
        if (uid.isNotBlank()) uids += uid
    }
}
