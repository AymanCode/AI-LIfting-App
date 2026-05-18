package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.agent.AgentOrchestrator
import com.ayman.ecolift.agent.AgentProcessingOptions
import com.ayman.ecolift.agent.AgentTurn
import com.ayman.ecolift.agent.PendingReviewCleanupParser
import com.ayman.ecolift.agent.WorkoutImportTextParser
import com.ayman.ecolift.agent.engine.GeminiNanoEngine
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.DebugDataHelper
import com.ayman.ecolift.data.PendingReview
import java.util.UUID
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
 * Reuses the AI screen UI models while delegating message handling, confirmation,
 * undo, and turn logging to the agent layer.
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

    private val workoutSetDao = db.workoutSetDao()
    private val exerciseDao = db.exerciseDao()

    // Track which review IDs have already been surfaced so the chat does not repeat them.
    private val notifiedReviewIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch { engine?.warmup() }

        // Proactively surface new unresolved entries in the chat.
        viewModelScope.launch {
            pendingReviewDao.observeUnresolved()
                .drop(1) // Skip initial DB emission on cold start; only react to new entries.
                .collect { reviews ->
                    val fresh = reviews.filter { it.id !in notifiedReviewIds }
                    if (fresh.isEmpty()) return@collect
                    notifiedReviewIds += fresh.map { it.id }
                    val lines = fresh.joinToString("\n") { "- \"${it.rawInput}\" (${it.dateLogged})" }
                    push(
                        isUser = false,
                        text = "Heads up - ${fresh.size} entry(s) didn't match any exercise:\n$lines\n\nTell me what they should be and I'll fix them."
                    )
                }
        }
    }
    // Internal state

    private val _input   = MutableStateFlow("")
    private val _msgs    = MutableStateFlow(listOf(WELCOME))
    private val _confirm = MutableStateFlow<AgentTurn.NeedsConfirmation?>(null)
    private val _busy    = MutableStateFlow(false)
    private var nextId   = 1L

    /** One-shot event: UI should show a Snackbar with "Undo" action. */
    private val _undoEvent = MutableSharedFlow<AgentTurn.Applied>(extraBufferCapacity = 1)
    val undoEvent: SharedFlow<AgentTurn.Applied> = _undoEvent.asSharedFlow()
    // Exposed state

    val uiState: StateFlow<AiUiState> = combine(
        _input, _msgs, _confirm, _busy, exerciseDao.observeAll()
    ) { input, msgs, confirm, busy, exercises ->
        val query = input.substringAfterLast('@', "").takeIf { input.contains('@') && !input.substringAfterLast('@').contains(' ') }
        val availableNames = if (query != null) {
            exercises.filter { it.name.contains(query, ignoreCase = true) }
                .sortedByDescending { it.name.startsWith(query, ignoreCase = true) }
                .map { it.name }
                .distinct()
        } else {
            exercises.map { it.name }.distinct().sorted()
        }

        AiUiState(
            isModelReady  = true,
            messages      = msgs,
            shortcuts     = buildDynamicShortcuts(exercises),
            input         = input,
            availableExerciseNames = availableNames,
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
        AiUiState(isModelReady = true, messages = listOf(WELCOME), shortcuts = emptyList())
    )

    private suspend fun buildDynamicShortcuts(exercises: List<com.ayman.ecolift.data.Exercise>): List<AiShortcutUi> {
        val shortcuts = mutableListOf<AiShortcutUi>()

        // 1. Trend for most recently logged exercise
        val recentExercise = workoutSetDao.getMostRecentExerciseName()
        if (recentExercise != null) {
            shortcuts += AiShortcutUi(
                title = "Trend for $recentExercise",
                subtitle = "Analyze your recent progress",
                prompt = "How is my $recentExercise trending?"
            )
        }

        // 2. Analyze last workout
        val lastDate = workoutSetDao.getLatestWorkoutDate()
        if (lastDate != null) {
            shortcuts += AiShortcutUi(
                title = "Analyze last workout",
                subtitle = "Check your session on $lastDate",
                prompt = "Analyze my workout from $lastDate"
            )
        }

        // 3. Defaults
        shortcuts += AiShortcutUi("Log a set", "Quick-log weight and reps", "bench press 185 x 5")
        shortcuts += AiShortcutUi("Fix a mistake", "Correct historical data", "Actually my bench on Monday was 225")

        return shortcuts.take(4)
    }

    /** Last 50 turns for the debug sheet, newest first. */
    val recentTurns: StateFlow<List<AgentTurnLog>> = turnLogDao
        .observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    // Actions

    fun updateInput(text: String) { _input.value = text }

    fun applyShortcut(prompt: String) { _input.value = prompt }

    fun sendMessage() {
        val text = _input.value.trim().ifEmpty { return }
        _input.value = ""
        push(isUser = true, text = text)

        val cleanupExercise = PendingReviewCleanupParser.extractExerciseName(text)
        if (cleanupExercise != null) {
            cleanupPendingReviewsAs(cleanupExercise)
            return
        }

        viewModelScope.launch {
            _busy.value = true
            val t0  = System.currentTimeMillis()
            val turn = agent.process(
                text,
                AgentProcessingOptions(allowModelFallback = false)
            )
            val ms  = System.currentTimeMillis() - t0
            handleTurn(turn)
            logTurn(text, turn, ms)
            _busy.value = false
        }
    }

    /**
     * Batch-resolves pending import rows as one exercise.
     *
     * User need: after importing old notes, several rows can fail because the
     * exercise was misspelled or absent from the catalog. A command like
     * "all pending rows are Hip Abduction" lets the user repair those rows in
     * one pass instead of opening each saved line and retyping it manually.
     */
    private fun cleanupPendingReviewsAs(exerciseQuery: String) {
        viewModelScope.launch {
            _busy.value = true
            val unresolved = pendingReviewDao.getUnresolved()
            if (unresolved.isEmpty()) {
                push(isUser = false, text = "No pending review rows to clean up.")
                _busy.value = false
                return@launch
            }

            val exercise = tools.findExercise(exerciseQuery)
            if (exercise == null) {
                push(isUser = false, text = "I couldn't find \"$exerciseQuery\" in your exercise list.")
                _busy.value = false
                return@launch
            }

            val patches = mutableListOf<DbPatch>()
            val resolvedReviewIds = mutableSetOf<Long>()
            val nextSetByDate = mutableMapOf<String, Int>()

            for (review in unresolved) {
                val sets = parseReviewSets(review)
                if (sets.isEmpty()) continue

                var nextSet = nextSetByDate.getOrPut(review.dateLogged) {
                    val recent = tools.getRecentSets(exercise.exerciseId, limit = 50)
                    (recent.filter { it.date == review.dateLogged }.maxOfOrNull { it.setNumber } ?: 0) + 1
                }
                for (set in sets) {
                    patches += DbPatch.LogSet(
                        exerciseId = exercise.exerciseId,
                        date = review.dateLogged,
                        setNumber = nextSet,
                        weightLbs = if (exercise.isBodyweight) null else set.weightLbs,
                        reps = set.reps,
                        isBodyweight = exercise.isBodyweight
                    )
                    nextSet += 1
                }
                nextSetByDate[review.dateLogged] = nextSet
                resolvedReviewIds += review.id
            }

            if (patches.isEmpty()) {
                push(isUser = false, text = "I found pending rows, but none had enough weight and rep detail to apply safely.")
                _busy.value = false
                return@launch
            }

            when (val result = service.applyPatches(UUID.randomUUID().toString(), patches, userConfirmed = false)) {
                is PatchResult.Applied -> {
                    for (id in resolvedReviewIds) pendingReviewDao.markResolved(id)
                    val rows = resolvedReviewIds.size
                    push(isUser = false, text = "Resolved $rows pending row(s) as ${exercise.name} and imported ${result.patchCount} set(s).")
                    _undoEvent.tryEmit(AgentTurn.Applied("Resolved pending review rows.", result.auditId))
                }
                is PatchResult.Rejected -> push(isUser = false, text = "Couldn't clean up pending rows: ${result.reason}")
                is PatchResult.Failed -> push(isUser = false, text = result.error, isError = true)
            }
            _busy.value = false
        }
    }

    private fun parseReviewSets(review: PendingReview): List<WorkoutImportTextParser.ParsedSet> {
        val importDraft = WorkoutImportTextParser.parse(review.rawInput, review.dateLogged)
        if (importDraft != null && importDraft.entries.isNotEmpty()) {
            return importDraft.entries.flatMap { it.sets }
        }
        val single = com.ayman.ecolift.agent.LogSetTextParser.parseOneExercise(review.rawInput)
        return single?.sets?.map { WorkoutImportTextParser.ParsedSet(it.weightLbs, it.reps) }.orEmpty()
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

    fun editRecoveryDraft(originalText: String) {
        _input.value = originalText
    }

    fun useRecoveryTemplate(template: String) {
        _input.value = template
    }

    fun saveRecoveryForReview(originalText: String, saveDate: String) {
        val raw = originalText.trim()
        if (raw.isBlank()) return
        viewModelScope.launch {
            pendingReviewDao.insert(
                PendingReview(
                    rawInput = raw,
                    dateLogged = saveDate
                )
            )
            push(isUser = false, text = "Saved for review. You can clean it up later without retyping it.")
        }
    }

    fun tryRecoveryWithModel(originalText: String) {
        val text = originalText.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            val t0 = System.currentTimeMillis()
            val turn = agent.process(
                text,
                AgentProcessingOptions(allowModelFallback = true)
            )
            val ms = System.currentTimeMillis() - t0
            handleTurn(turn)
            logTurn(text, turn, ms)
            _busy.value = false
        }
    }

    fun seedDebugData() {
        viewModelScope.launch {
            DebugDataHelper.seed(getApplication())
            push(isUser = false, text = "Debug data seeded - 7 exercises, about 30 sessions across 90 days. Try: \"how is my bench trending\" or \"what did I do on Monday\".")
        }
    }
    // Internal helpers

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
            is AgentTurn.RecoverableFailure -> {
                push(
                    isUser = false,
                    text = "${turn.title}\n${turn.detail}",
                    recovery = AiRecoveryActionUi(
                        title = turn.title,
                        detail = turn.detail,
                        originalText = turn.originalText,
                        suggestedTemplate = turn.suggestedTemplate,
                        saveDate = turn.saveDate,
                        canTryModel = turn.canTryModel
                    )
                )
            }
            is AgentTurn.ImportApplied -> {
                push(isUser = false, text = turn.text)
                persistPendingReviewDrafts(turn.pendingReviews)
                if (turn.auditId != null) {
                    _undoEvent.tryEmit(AgentTurn.Applied(turn.text, turn.auditId))
                }
            }
            is AgentTurn.Error -> push(isUser = false, text = turn.message, isError = true)
        }
    }

    private fun persistPendingReviewDrafts(drafts: List<AgentTurn.PendingReviewDraft>) {
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            for (draft in drafts) {
                val id = pendingReviewDao.insert(
                    PendingReview(
                        rawInput = draft.rawInput,
                        dateLogged = draft.dateLogged
                    )
                )
                notifiedReviewIds += id
            }
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
        runCatching { turnLogDao.insert(entry) }  // Fire-and-forget; never crash the UI.
    }

    private fun push(
        isUser: Boolean,
        text: String,
        isError: Boolean = false,
        recovery: AiRecoveryActionUi? = null
    ) {
        _msgs.update {
            it + AiMessageUi(
                id = nextId++,
                isUser = isUser,
                text = text,
                isError = isError,
                recovery = recovery
            )
        }
    }
    // Constants

    companion object {
        private val WELCOME = AiMessageUi(
            id     = 0L,
            isUser = false,
            text   = "IronMind ready. Log sets, check history, or ask for weight advice."
        )

        private val SHORTCUTS = listOf(
            AiShortcutUi("Log a set",      "Quick-log weight and reps",       "bench press 185 x 5"),
            AiShortcutUi("My history",     "See recent bench press sessions", "show my bench press history"),
            AiShortcutUi("Bench trend",    "How strength is progressing",     "how is my bench press trending"),
            AiShortcutUi("Today's session","What was logged today",           "what did I do today"),
        )
    }
}
