package com.ayman.ecolift.web.supabase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** One row of public.list_assistant_changes_v1: a change Claude applied (or undid) through MCP. */
@Serializable
data class AssistantChangeRow(
    val id: String,
    val kind: String,
    val status: String,
    val summary: String,
    val reason: String? = null,
    val proposedAt: String? = null,
    val appliedAt: String? = null,
    val undoneAt: String? = null,
    val undoneBy: String? = null,
)

@Serializable
data class ListAssistantChangesV1Result(
    val ok: Boolean,
    val code: String,
    val changes: List<AssistantChangeRow> = emptyList(),
    val latestAppliedAt: String? = null,
)

@Serializable
data class UndoAssistantChangeV1Result(
    val ok: Boolean,
    val code: String,
    val changeId: String? = null,
    val undoChangeId: String? = null,
    val status: String? = null,
    val summary: String? = null,
)

/**
 * The IronMind "Claude changes" list. Reads only happen when the list is opened
 * or on the sync engine's existing refresh path; there is no polling of its own.
 */
interface AssistantChangeRepository {
    /** Max applied_at across the user's applied/undone changes; drives the unread dot. */
    val latestAppliedAt: StateFlow<String?>
    suspend fun list(limit: Int = 20): List<AssistantChangeRow>
    suspend fun undo(changeId: String): UndoAssistantChangeV1Result
    /** One tiny call, made from the existing focus/visibility/reconnect/60s refresh. */
    suspend fun refreshLatestAppliedAt()
}

/** Demo and offline stand-in: nothing to list, nothing to undo. */
object NoAssistantChangeRepository : AssistantChangeRepository {
    override val latestAppliedAt: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
    override suspend fun list(limit: Int): List<AssistantChangeRow> = emptyList()
    override suspend fun undo(changeId: String) = UndoAssistantChangeV1Result(ok = false, code = "UNAVAILABLE")
    override suspend fun refreshLatestAppliedAt() = Unit
}
