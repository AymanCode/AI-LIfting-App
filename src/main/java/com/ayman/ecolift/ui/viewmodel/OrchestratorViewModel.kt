package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.agent.AgentOrchestrator
import com.ayman.ecolift.agent.AgentProcessingOptions
import com.ayman.ecolift.agent.AgentResponseCard
import com.ayman.ecolift.agent.AgentTurn
import com.ayman.ecolift.agent.LogSetTextParser
import com.ayman.ecolift.agent.PendingReviewCleanupParser
import com.ayman.ecolift.agent.WorkoutImportTextParser
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.agent.model.InMemoryTurnLogStore
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.router.Intent
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.router.RuleMatcher
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.android.room.PendingReviewResolver
import com.ayman.ecolift.android.room.PendingReviewSet
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel backed by [AgentOrchestrator].
 *
 * Web port: uses AppRepos plus an injected AI engine instead of AppDatabase/AndroidViewModel.
 */
@OptIn(ExperimentalUuidApi::class)
class OrchestratorViewModel(
    private val repos: AppRepos,
    private val engine: LocalGenAiEngine
) : ViewModel() {

    private val auditStore  = InMemoryAuditStore()
    private val turnLogStore = InMemoryTurnLogStore()
    private val tools = AgentToolsImpl(repos)
    private data class Session(val generation: Long, val service: PatchService, val agent: AgentOrchestrator)
    private var session = newSession()

    private fun newSession(): Session {
        val service = PatchService(repos, PatchValidator(), auditStore)
        return Session(
            repos.dataGeneration?.changes?.value ?: 0L,
            service,
            AgentOrchestrator(
                router = IntentRouter(engine = engine),
                tools = tools,
                patchApplier = service,
                engine = engine,
            ),
        )
    }

    private fun currentSession(): Session {
        if (session.generation != (repos.dataGeneration?.changes?.value ?: 0L)) {
            session.service.clearUndo()
            session = newSession()
            _confirm.value = null
            _busy.value = false
            _msgs.value = listOf(WELCOME)
            _input.value = ""
            _undoOffer.value = null
            _reviewError.value = null
            _recentTurns.value = emptyList()
            notifiedReviewIds.clear()
        }
        return session
    }

    // Track which review IDs have already been surfaced so the chat does not repeat them.
    private val notifiedReviewIds = mutableSetOf<Long>()

    init {
        // Proactively surface new unresolved entries in the chat.
        viewModelScope.launch {
            repos.pendingReviews.unresolved
                .drop(1) // Skip initial emission on cold start; only react to new entries.
                .collect { reviews ->
                    try {
                        val fresh = reviews.filter { it.id !in notifiedReviewIds }
                        if (fresh.isEmpty()) return@collect
                        notifiedReviewIds += fresh.map { it.id }
                        val lines = fresh.joinToString("\n") { "- \"${it.rawInput}\" (${humanDate(it.dateLogged)})" }
                        push(
                            isUser = false,
                            text = "${countLabel(fresh.size, "entry", "entries")} matched no exercise:\n$lines\n\nTell me what they should be and I'll fix them."
                        )
                    } catch (e: Exception) {
                        // Swallow per-cycle errors so future emissions are still monitored.
                    }
                }
        }
    }

    // Internal state

    private val _input   = MutableStateFlow("")
    private val _msgs    = MutableStateFlow(listOf(WELCOME))
    /** This session's messages only; agent conversations are not persisted. */
    val sessionMessages: StateFlow<List<AiMessageUi>> = _msgs
    private val _confirm = MutableStateFlow<AgentTurn.NeedsConfirmation?>(null)
    private val _busy    = MutableStateFlow(false)
    private var nextId   = 1L

    private val _undoOffer = MutableStateFlow<AgentTurn.Applied?>(null)
    val undoOffer: StateFlow<AgentTurn.Applied?> = _undoOffer.asStateFlow()

    private val _reviewError = MutableStateFlow<String?>(null)
    val reviewError: StateFlow<String?> = _reviewError.asStateFlow()

    fun clearReviewError() { _reviewError.value = null }

    fun resolveSavedReview(id: Long, set: PendingReviewSet, expectedGeneration: Long = repos.dataGeneration?.changes?.value ?: 0L) {
        if (expectedGeneration != (repos.dataGeneration?.changes?.value ?: 0L)) return
        if (_busy.value || DemoGuard.blockIfDemo()) return
        _busy.value = true
        _reviewError.value = null
        viewModelScope.launch {
            try {
                val resolve: suspend () -> Unit = {
                    val resolver = repos.pendingReviews as? PendingReviewResolver
                        ?: error("Saved review resolution is unavailable")
                    resolver.resolve(id, set)
                    Unit
                }
                val generation = repos.dataGeneration
                if (generation == null) resolve() else generation.withCurrent(expectedGeneration, resolve)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (expectedGeneration == (repos.dataGeneration?.changes?.value ?: 0L)) _reviewError.value = "Couldn't save your answer. Try again."
            } finally {
                if (expectedGeneration == (repos.dataGeneration?.changes?.value ?: 0L)) _busy.value = false
            }
        }
    }

    fun dismissSavedReview(id: Long, expectedGeneration: Long = repos.dataGeneration?.changes?.value ?: 0L) {
        if (expectedGeneration != (repos.dataGeneration?.changes?.value ?: 0L)) return
        if (_busy.value || DemoGuard.blockIfDemo()) return
        _busy.value = true
        _reviewError.value = null
        viewModelScope.launch {
            try {
                val dismiss: suspend () -> Unit = { repos.pendingReviews.markResolved(id) }
                val generation = repos.dataGeneration
                if (generation == null) dismiss() else generation.withCurrent(expectedGeneration, dismiss)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (expectedGeneration == (repos.dataGeneration?.changes?.value ?: 0L)) _reviewError.value = "Couldn't save your answer. Try again."
            } finally {
                if (expectedGeneration == (repos.dataGeneration?.changes?.value ?: 0L)) _busy.value = false
            }
        }
    }

    // Exposed state

    val uiState: StateFlow<AiUiState> = combine(
        _input, _msgs, _confirm, _busy, repos.exercises.exercises.combine(repos.sets.sets) { exercises, _ -> exercises }
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

    private fun buildDynamicShortcuts(exercises: List<com.ayman.ecolift.data.Exercise>): List<AiShortcutUi> {
        val shortcuts = mutableListOf<AiShortcutUi>()
        val allSets = repos.sets.sets.value

        // 1. Trend for most recently logged exercise
        val recentSet = allSets.filter { it.isCheckedWorking }.maxByOrNull { it.date }
        val recentExerciseName = recentSet?.exerciseId?.let { eid ->
            exercises.firstOrNull { it.id == eid }?.name
        }
        if (recentExerciseName != null) {
            shortcuts += AiShortcutUi(
                title = "Trend for $recentExerciseName",
                subtitle = "Analyze your recent progress",
                prompt = "How is my $recentExerciseName trending?"
            )
        }

        // 2. Analyze last workout
        val lastDate = SetQueries.latestCompletedDate(allSets)
        if (lastDate != null) {
            shortcuts += AiShortcutUi(
                title = "Analyze last workout",
                subtitle = "Check your session on ${humanDate(lastDate)}",
                prompt = "Analyze my workout from ${humanDateLong(lastDate)}"
            )
        }

        val openCount = allSets.count { !it.completed && it.date <= com.ayman.ecolift.data.WorkoutDates.today() }
        if (openCount > 0) shortcuts += AiShortcutUi(
            "Review ${countLabel(openCount, "unchecked set")}", "",
            "Show my $openCount open workout sets by date and exercise. Do not change or remove any sets."
        )
        return shortcuts.take(3)
    }

    /** Last 50 turns for the debug sheet, newest first. */
    private val _recentTurns = MutableStateFlow<List<AgentTurnLog>>(emptyList())
    val recentTurns: StateFlow<List<AgentTurnLog>> = _recentTurns

    init {
        repos.dataGeneration?.let { generation ->
            viewModelScope.launch {
                generation.changes.collect { currentSession() }
            }
        }
    }

    // Actions

    fun updateInput(text: String) { currentSession(); _input.value = text }

    fun applyShortcut(prompt: String) { currentSession(); _input.value = prompt }

    fun sendMessage() {
        val session = currentSession()
        val text = _input.value.trim().ifEmpty { return }
        _input.value = ""
        push(isUser = true, text = text)

        if (blockDemoWriteRequest(text)) return

        val cleanupExercise = PendingReviewCleanupParser.extractExerciseName(text)
        if (cleanupExercise != null) {
            cleanupPendingReviewsAs(cleanupExercise)
            return
        }

        viewModelScope.launch {
            _busy.value = true
            val t0  = nowMillis()
            val turn = session.agent.process(
                text,
                AgentProcessingOptions(allowModelFallback = false)
            )
            val ms  = nowMillis() - t0
            handleTurn(turn, session = session)
            if (session.generation == currentSession().generation) {
                logTurn(text, turn, ms)
                _busy.value = false
            }
        }
    }

    /**
     * Batch-resolves pending import rows as one exercise.
     */
    private fun cleanupPendingReviewsAs(exerciseQuery: String) {
        val session = currentSession()
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            _busy.value = true
            val unresolvedList = repos.pendingReviews.unresolved.first()
            if (unresolvedList.isEmpty()) {
                push(isUser = false, text = "No unchecked sets.")
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

            for (review in unresolvedList) {
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

            when (val result = session.service.applyPatches(Uuid.random().toString(), patches, userConfirmed = false)) {
                is PatchResult.Applied -> {
                    if (session.generation != currentSession().generation) return@launch
                    var rows = 0
                    val resolve: suspend () -> Unit = {
                        for (id in resolvedReviewIds) {
                            try {
                                repos.pendingReviews.markResolved(id)
                                rows += 1
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (t: Throwable) {
                                push(isUser = false, text = "Couldn't save your answer. Try again.")
                                break
                            }
                        }
                    }
                    val generation = repos.dataGeneration
                    if (generation == null) {
                        resolve()
                    } else {
                        generation.withCurrent(session.generation, resolve) ?: return@launch
                    }
                    if (session.generation != currentSession().generation) return@launch
                    val message = "Saved ${countLabel(rows, "set")} as ${exercise.name}."
                    push(
                        isUser = false,
                        text = message,
                        card = AiChangesCardUi(
                            patches.mapNotNull { patch ->
                                (patch as? DbPatch.LogSet)?.let {
                                    val load = it.weightLbs?.let(com.ayman.ecolift.data.WeightLbs::formatStored) ?: "BW"
                                    "${exercise.name} · added $load×${it.reps}"
                                }
                            }
                        )
                    )
                    _undoOffer.value = AgentTurn.Applied(message, result.auditId)
                }
                is PatchResult.Rejected -> push(isUser = false, text = "Couldn't clean up pending rows: ${result.reason}")
                is PatchResult.Failed -> push(isUser = false, text = result.error, isError = true)
            }
            if (session.generation == currentSession().generation) _busy.value = false
        }
    }

    private fun parseReviewSets(review: PendingReview): List<WorkoutImportTextParser.ParsedSet> {
        val importDraft = WorkoutImportTextParser.parse(review.rawInput, review.dateLogged)
        if (importDraft != null && importDraft.entries.isNotEmpty()) {
            return importDraft.entries.flatMap { it.sets }
        }
        val single = LogSetTextParser.parseOneExercise(review.rawInput)
        return single?.sets?.map { WorkoutImportTextParser.ParsedSet(it.weightLbs, it.reps) }.orEmpty()
    }

    fun confirmPending() {
        val session = currentSession()
        val pending = _confirm.value ?: return
        if (DemoGuard.blockIfDemo()) return
        _confirm.value = null
        viewModelScope.launch {
            _busy.value = true
            handleTurn(session.agent.confirm(pending.requestId, pending.patches), session = session)
            if (session.generation == currentSession().generation) _busy.value = false
        }
    }

    fun dismissPending() {
        _confirm.value = null
        push(isUser = false, text = "No changes made.")
    }

    fun undo(auditId: Long) {
        val session = currentSession()
        if (_undoOffer.value?.auditId != auditId) return
        if (DemoGuard.blockIfDemo()) return
        _undoOffer.value = null
        viewModelScope.launch {
            handleTurn(session.agent.undo(auditId), offerUndo = false, session = session)
        }
    }

    fun clearTurnLog() {
        // InMemoryTurnLogStore doesn't have a clearAll - no-op for now
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
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            try {
                repos.pendingReviews.add(rawInput = raw, dateLogged = saveDate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                push(isUser = false, text = "Couldn't save that for review")
                return@launch
            }
            push(isUser = false, text = "Saved for review.")
        }
    }

    fun tryRecoveryWithModel(originalText: String) {
        val session = currentSession()
        val text = originalText.trim()
        if (text.isBlank()) return
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            _busy.value = true
            val t0 = nowMillis()
            val turn = session.agent.process(
                text,
                AgentProcessingOptions(allowModelFallback = true)
            )
            val ms = nowMillis() - t0
            handleTurn(turn, session = session)
            if (session.generation == currentSession().generation) {
                logTurn(text, turn, ms)
                _busy.value = false
            }
        }
    }

    // Internal helpers

    private fun handleTurn(turn: AgentTurn, offerUndo: Boolean = true, session: Session) {
        if (session.generation != currentSession().generation) return
        if (DemoGuard.isDemo && turn.hasWriteEffect()) {
            _confirm.value = null
            DemoGuard.notifyBlocked()
            return
        }
        when (turn) {
            is AgentTurn.TextResponse      -> push(isUser = false, text = turn.text, card = turn.card?.toUiCard())
            is AgentTurn.NeedsConfirmation -> {
                _confirm.value = turn
            }
            is AgentTurn.Applied -> {
                push(
                    isUser = false,
                    text = turn.text,
                    card = turn.changes.takeIf { it.isNotEmpty() }?.let(::AiChangesCardUi)
                )
                if (offerUndo) {
                    _undoOffer.value = turn
                }
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
                push(
                    isUser = false,
                    text = turn.text,
                    card = turn.changes.takeIf { it.isNotEmpty() }?.let(::AiChangesCardUi)
                )
                persistPendingReviewDrafts(turn.pendingReviews, session)
                if (turn.auditId != null) {
                    val offer = AgentTurn.Applied(turn.text, turn.auditId)
                    _undoOffer.value = offer
                }
            }
            is AgentTurn.Error -> push(isUser = false, text = turn.message, isError = true)
        }
    }

    private fun blockDemoWriteRequest(text: String): Boolean {
        if (!DemoGuard.isDemo) return false
        val isWriteRequest =
            PendingReviewCleanupParser.extractExerciseName(text) != null ||
                WorkoutImportTextParser.looksLikeImport(text, WorkoutDates.today()) ||
                RuleMatcher.match(text)?.intent is Intent.Write
        if (!isWriteRequest) return false

        DemoGuard.notifyBlocked()
        return true
    }

    private fun AgentTurn.hasWriteEffect(): Boolean =
        when (this) {
            is AgentTurn.Applied,
            is AgentTurn.ImportApplied,
            is AgentTurn.NeedsConfirmation -> true
            else -> false
        }

    private fun persistPendingReviewDrafts(drafts: List<AgentTurn.PendingReviewDraft>, session: Session) {
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            for (draft in drafts) {
                try {
                    val generation = repos.dataGeneration
                    if (generation == null) {
                        repos.pendingReviews.add(rawInput = draft.rawInput, dateLogged = draft.dateLogged)
                    } else {
                        generation.withCurrent(session.generation) {
                            repos.pendingReviews.add(rawInput = draft.rawInput, dateLogged = draft.dateLogged)
                        } ?: return@launch
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    push(isUser = false, text = "Couldn't save that for review")
                    return@launch
                }
            }
        }
    }

    private fun logTurn(userText: String, turn: AgentTurn, latencyMs: Long) {
        val entry = AgentTurnLog(
            timestamp    = nowMillis(),
            userText     = userText,
            turnKind     = turn::class.simpleName ?: "Unknown",
            latencyMs    = latencyMs,
            errorMessage = (turn as? AgentTurn.Error)?.message,
            auditId      = (turn as? AgentTurn.Applied)?.auditId,
        )
        runCatching { turnLogStore.insert(entry) }  // Fire-and-forget; never crash the UI.
        _recentTurns.value = turnLogStore.latest(50)
    }

    private fun push(
        isUser: Boolean,
        text: String,
        isError: Boolean = false,
        card: AiMessageCardUi? = null,
        recovery: AiRecoveryActionUi? = null
    ) {
        _msgs.update {
            it + AiMessageUi(
                id = nextId++,
                isUser = isUser,
                text = text,
                timestampMillis = nowMillis(),
                isError = isError,
                card = card,
                recovery = recovery
            )
        }
    }

    private fun AgentResponseCard.toUiCard(): AiMessageCardUi =
        when (this) {
            is AgentResponseCard.History -> AiHistoryCardUi(
                exerciseName = exerciseName,
                prLine = prLine,
                sessions = sessions.map { AiHistoryRowUi(it.date, it.setLabel) },
            )
            is AgentResponseCard.Changes -> AiChangesCardUi(changes)
        }

    private fun humanDate(isoDate: String): String {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
        return "${MONTH_ABBREV[date.monthNumber - 1]} ${date.dayOfMonth}"
    }

    private fun humanDateLong(isoDate: String): String {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
        val monthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        return "${monthNames[date.monthNumber - 1]} ${date.dayOfMonth}"
    }

    // Constants

    companion object {
        private val WELCOME = AiMessageUi(
            id     = 0L,
            isUser = false,
            text   = "IronMind ready. Log sets, check history, or ask for weight advice.",
            timestampMillis = nowMillis()
        )
    }
}
