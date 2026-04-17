package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch

/**
 * Result of one orchestrator turn — consumed directly by the UI layer.
 */
sealed interface AgentTurn {

    /** Read result, clarification question, or simple confirmation. */
    data class TextResponse(val text: String) : AgentTurn

    /** A destructive patch is pending; the UI must ask the user to confirm before calling confirm(). */
    data class NeedsConfirmation(
        val summary: String,
        val patches: List<DbPatch>,
        val requestId: String
    ) : AgentTurn

    /** Patches were successfully applied. [auditId] can be passed to undo(). */
    data class Applied(val text: String, val auditId: Long) : AgentTurn

    /** Unrecoverable error — show to user and log. */
    data class Error(val message: String) : AgentTurn
}
