package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.web.supabase.AssistantChangeRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantChangeListMappingTest {

    private fun row(
        status: String = "APPLIED",
        kind: String = "LOG_SETS",
        appliedAt: String? = "2026-09-14T15:04:00.000000+00:00",
        reason: String? = null,
    ) = AssistantChangeRow(
        id = "c1", kind = kind, status = status, summary = "Log 3 set(s)",
        reason = reason, proposedAt = "2026-09-14T15:00:00+00:00", appliedAt = appliedAt,
    )

    @Test
    fun onlyAppliedNonUndoRowsOfferUndo() {
        assertTrue(claudeChangeUi(row(), today = "2026-09-14").canUndo)
        assertFalse(claudeChangeUi(row(status = "UNDONE"), today = "2026-09-14").canUndo)
        assertTrue(claudeChangeUi(row(status = "UNDONE"), today = "2026-09-14").undone)
        assertFalse(claudeChangeUi(row(kind = "UNDO"), today = "2026-09-14").canUndo)
    }

    @Test
    fun whenLabelUsesTimeForTodayAndDateOtherwise() {
        assertEquals("Today 15:04", claudeChangeUi(row(), today = "2026-09-14").whenLabel)
        assertEquals("2026-09-14", claudeChangeUi(row(), today = "2026-09-15").whenLabel)
        assertEquals("", claudeChangeUi(row(appliedAt = null).copy(proposedAt = null), today = "2026-09-14").whenLabel)
    }

    @Test
    fun blankReasonIsDropped() {
        assertEquals(null, claudeChangeUi(row(reason = "  "), today = "2026-09-14").reason)
        assertEquals("dupe", claudeChangeUi(row(reason = "dupe"), today = "2026-09-14").reason)
    }

    @Test
    fun unseenDotComparesLatestAgainstLastSeen() {
        assertFalse(isUnseenClaudeChange(latestAppliedAt = null, lastSeenAppliedAt = null))
        assertTrue(isUnseenClaudeChange(latestAppliedAt = "2026-09-14T15:04:00+00:00", lastSeenAppliedAt = null))
        assertTrue(isUnseenClaudeChange("2026-09-14T15:05:00+00:00", "2026-09-14T15:04:00+00:00"))
        assertFalse(isUnseenClaudeChange("2026-09-14T15:04:00+00:00", "2026-09-14T15:04:00+00:00"))
        assertFalse(isUnseenClaudeChange("2026-09-14T15:03:00+00:00", "2026-09-14T15:04:00+00:00"))
    }

    @Test
    fun refusalCodesReadAsPlainNotes() {
        assertEquals("Can't undo: you've edited this since.", undoRefusalNote("BLOCKED_BY_LATER_EDIT"))
        assertEquals("Already undone.", undoRefusalNote("ALREADY_UNDONE"))
        assertEquals("Undo failed (X).", undoRefusalNote("X"))
    }
}
