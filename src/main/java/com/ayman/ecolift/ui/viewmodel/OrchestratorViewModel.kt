package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.agent.AgentOrchestrator
import com.ayman.ecolift.agent.AgentTurn
import com.ayman.ecolift.agent.engine.GeminiNanoEngine
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.DebugDataHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel backed by [AgentOrchestrator].
 *
 * Reuses [AiUiState] / [AiMessageUi] / [AiPendingActionUi] / [AiShortcutUi]
 * so [AiScreen] needs minimal UI type changes.
 *
 * Phase 8: every [sendMessage] call is timed and logged to [agent_turn_log].
 * [recentTurns] exposes the log as a Flow for the in-app debug sheet.
 */
class OrchestratorViewModel(application: Application) : AndroidViewModel(application) {

    private val db      = AppDatabase.getInstance(application)
    private val service = PatchService(db, PatchValidator())
    private val tools   = AgentToolsImpl(db)
    private val engine: LocalGenAiEngine? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GeminiNanoEngine(application)
        else null
    private val agent   = AgentOrchestrator(
        router       = IntentRouter(engine = engine),
        tools        = tools,
        patchApplier = service,
        engine       = engine
    )
    private val turnLogDao      = db.agentTurnLogDao()
    private val pendingReviewDao = db.pendingReviewDao()

    // Track which review IDs we've already surfaced so we don't repeat
    private val notifiedReviewIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch { engine?.warmup() }

        // Proactively surface new unresolved entries in the chat
        viewModelScope.launch {
            pendingReviewDao.observeUnresolved()
                .drop(1) // skip initial DB emission on cold start; only react to new entries
                .collect { reviews ->
                    val fresh = reviews.filter { it.id !in notifiedReviewIds }
                    if (fresh.isEmpty()) return@collect
                    notifiedReviewIds += fresh.map { it.id }
                    val lines = fresh.joinToString("\n") { "• \"${it.rawInput}\" (${it.dateLogged})" }
                    push(
                        isUser = false,
                        text = "Heads up — ${fresh.size} entry(s) didn't match any exercise:\n$lines\n\nTell me what they should be and I'll fix them."
                    )
                }
        }
    }

    // ── Internal state ────────────────────────────────────────────────

    private val _input   = MutableStateFlow("")
    private val _msgs    = MutableStateFlow(listOf(WELCOME))
    private val _confirm = MutableStateFlow<AgentTurn.NeedsConfirmation?>(null)
    private val _busy    = MutableStateFlow(false)
    private var nextId   = 1L

    /** One-shot event: UI should show a Snackbar with "Undo" action. */
    private val _undoEvent = MutableSharedFlow<AgentTurn.Applied>(extraBufferCapacity = 1)
    val undoEvent: SharedFlow<AgentTurn.Applied> = _undoEvent.asSharedFlow()

    // ── Exposed state ─────────────────────────────────────────────────

    val uiState: StateFlow<AiUiState> = combine(
        _input, _msgs, _confirm, _busy
    ) { input, msgs, confirm, busy ->
        AiUiState(
            isModelReady  = true,
            messages      = msgs,
            shortcuts     = SHORTCUTS,
            input         = input,
            isWorking     = busy,
            pendingAction = confirm?.let {
                AiPendingActionUi(
                    title        = "Confirm Change",
                    detail       = it.summary,
                    confirmLabel = "Apply"
                )
            }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AiUiState(isModelReady = true, messages = listOf(WELCOME), shortcuts = SHORTCUTS)
    )

    /** Phase 8 — last 50 turns for the debug sheet, newest first. */
    val recentTurns: StateFlow<List<AgentTurnLog>> = turnLogDao
        .observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Actions ───────────────────────────────────────────────────────

    fun updateInput(text: String) { _input.value = text }

    fun applyShortcut(prompt: String) { _input.value = prompt }

    fun sendMessage() {
        val text = _input.value.trim().ifEmpty { return }
        _input.value = ""
        push(isUser = true, text = text)
        viewModelScope.launch {
            _busy.value = true
            val t0  = System.currentTimeMillis()
            val turn = agent.process(text)
            val ms  = System.currentTimeMillis() - t0
            handleTurn(turn)
            logTurn(text, turn, ms)
            _busy.value = false
        }
    }

    fun confirmPending() {
        val pending = _confirm.value ?: return
        _confirm.value = null
        viewModelScope.launch {
            _busy.value = true
            handleTurn(agent.confirm(pending.requestId, pending.patches))
            _busy.value = false
        }
    }

    fun dismissPending() {
        _confirm.value = null
        push(isUser = false, text = "No changes made.")
    }

    fun undo(auditId: Long) {
        viewModelScope.launch {
            handleTurn(agent.undo(auditId))
        }
    }

    fun clearTurnLog() {
        viewModelScope.launch { turnLogDao.clearAll() }
    }

    fun seedDebugData() {
        viewModelScope.launch {
            DebugDataHelper.seed(getApplication())
            push(isUser = false, text = "Debug data seeded — 7 exercises, ~30 sessions across 90 days. Try: \"how is my bench trending\" or \"what did I do on Monday\".")
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────

    private fun handleTurn(turn: AgentTurn) {
        when (turn) {
            is AgentTurn.TextResponse      -> push(isUser = false, text = turn.text)
            is AgentTurn.NeedsConfirmation -> {
                push(isUser = false, text = turn.summary)
                _confirm.value = turn
            }
            is AgentTurn.Applied -> {
                push(isUser = false, text = turn.text)
                _undoEvent.tryEmit(turn)
            }
            is AgentTurn.Error -> push(isUser = false, text = turn.message, isError = true)
        }
    }

    private suspend fun logTurn(userText: String, turn: AgentTurn, latencyMs: Long) {
        val entry = AgentTurnLog(
            timestamp    = System.currentTimeMillis(),
            userText     = userText,
            turnKind     = turn::class.simpleName ?: "Unknown",
            latencyMs    = latencyMs,
            errorMessage = (turn as? AgentTurn.Error)?.message,
            auditId      = (turn as? AgentTurn.Applied)?.auditId,
        )
        runCatching { turnLogDao.insert(entry) }  // fire-and-forget; never crash the UI
    }

    private fun push(isUser: Boolean, text: String, isError: Boolean = false) {
        _msgs.update { it + AiMessageUi(id = nextId++, isUser = isUser, text = text, isError = isError) }
    }

    // ── Constants ─────────────────────────────────────────────────────

    companion object {
        private val WELCOME = AiMessageUi(
            id     = 0L,
            isUser = false,
            text   = "IronMind ready. Log sets, check history, or ask for weight advice."
        )

        private val SHORTCUTS = listOf(
            AiShortcutUi("Log a set",     "Quick-log weight and reps",    "bench press 135 lbs 8 reps"),
            AiShortcutUi("My history",    "See recent performance",       "show me my bench press history"),
            AiShortcutUi("Weight advice", "Get a recommendation",         "what weight should i use for squats"),
            AiShortcutUi("Fix last set",  "Correct a logged mistake",     "fix my last set it was 145 not 135"),
        )
    }
}
