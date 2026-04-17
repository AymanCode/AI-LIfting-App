package com.ayman.ecolift.agent.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted record of one agent turn — written after every [AgentOrchestrator.process] call.
 *
 * Used for the in-app debug view (Phase 8) and future analytics / A/B tuning.
 */
@Entity(tableName = "agent_turn_log")
data class AgentTurnLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Wall-clock time the turn started (ms since epoch). */
    val timestamp: Long,

    /** Raw user input text. */
    val userText: String,

    /**
     * Sealed class variant of the result:
     * "TextResponse" | "NeedsConfirmation" | "Applied" | "Error"
     */
    val turnKind: String,

    /** End-to-end latency from process() entry to result (ms). */
    val latencyMs: Long,

    /** Non-null when turnKind == "Error". */
    val errorMessage: String? = null,

    /** Non-null when turnKind == "Applied". Stable ID for undo lookup. */
    val auditId: Long? = null,
)
