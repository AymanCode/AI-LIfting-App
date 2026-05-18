package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch

/**
 * Result of one orchestrator turn - consumed directly by the UI layer.
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

    /**
     * The request looks recoverable, but the agent could not safely turn it into a patch.
     * The UI should preserve the original text and offer edit, template, save, and model-retry actions.
     */
    data class RecoverableFailure(
        val title: String,
        val detail: String,
        val originalText: String,
        val suggestedTemplate: String,
        val saveDate: String,
        val canTryModel: Boolean
    ) : AgentTurn

    /**
     * A raw line that should be kept for later cleanup instead of being dropped.
     *
     * Use case: bulk imports often contain misspelled exercises, plate shorthand,
     * or partial notes. Saving those rows lets users start importing immediately
     * while leaving ambiguous data in a review queue they can resolve later.
     */
    data class PendingReviewDraft(
        val rawInput: String,
        val dateLogged: String,
        val reason: String
    )

    /**
     * Result of a dated workout-note import.
     *
     * High-confidence rows have already been converted into typed DbPatch writes.
     * Low-confidence rows are returned as [pendingReviews] so the UI can persist
     * them without forcing the user to retype the original import text.
     */
    data class ImportApplied(
        val text: String,
        val auditId: Long?,
        val appliedPatchCount: Int,
        val pendingReviews: List<PendingReviewDraft>
    ) : AgentTurn

    /** Patches were successfully applied. [auditId] can be passed to undo(). */
    data class Applied(val text: String, val auditId: Long) : AgentTurn

    /** Unrecoverable error - show to user and log. */
    data class Error(val message: String) : AgentTurn
}
