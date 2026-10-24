package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.ai.ExerciseMuscleClassifier
import com.ayman.ecolift.ai.MuscleClassification
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.DayQueries
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.epleyEstimate
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.data.normalizeBodyPartGroup
import com.ayman.ecolift.data.normalizedBodyweightLoad
import com.ayman.ecolift.data.resolvedCycleSlotId
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.SplitSeedBuilder
import com.ayman.ecolift.data.splits.SplitSwitch
import com.ayman.ecolift.data.splits.SplitSwitchIntent
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.data.splits.SplitSwitchPlan
import com.ayman.ecolift.data.splits.SplitSwitchPrecondition
import com.ayman.ecolift.data.splits.SplitSwitchRequest
import com.ayman.ecolift.data.splits.SplitSwitchTarget
import com.ayman.ecolift.data.splits.effectiveSplitMembers
import com.ayman.ecolift.data.splits.learnedGroupLabel
import com.ayman.ecolift.data.suggestMuscleGroup
import com.ayman.ecolift.data.suggestMuscleGroupFromKeywords
import com.ayman.ecolift.data.visibleCycleSlots
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.logDateFromLocation
import com.ayman.ecolift.web.supabase.ExerciseMergePreparationResult
import com.ayman.ecolift.web.supabase.ExerciseMergeResult
import com.ayman.ecolift.web.supabase.ExerciseRenameResult
import com.ayman.ecolift.web.supabase.ResolvedSplitReplacement
import com.ayman.ecolift.web.supabase.SplitReplacementResult
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.UserVisibleSyncException
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.NotCommittedException
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import com.ayman.ecolift.web.supabase.sync.orThrow
import com.ayman.ecolift.web.supabase.sync.toPendingWrite
import com.ayman.ecolift.web.util.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

data class PendingSplitSwitchConfirmation(
    val request: SplitSwitchRequest,
    val targetLabel: String,
    val affectedSetCount: Int,
)

private data class ExerciseAddContext(
    val token: Long,
    val date: String,
    val dateGeneration: Long,
    val searchGeneration: Long,
    val resolutionRequestId: Long? = null,
    val survivesQueryEdit: Boolean = false,
)

internal fun shouldOfferExerciseResolution(isOnline: Boolean, isDemo: Boolean): Boolean =
    isOnline && !isDemo

/**
 * The text field on Log is an add-exercise search, not a filter over the active workout.
 * Keep the query in this seam so a regression test can pin that boundary explicitly.
 */
internal fun workoutSetsForLog(
    currentSets: List<WorkoutSet>,
    @Suppress("UNUSED_PARAMETER") exerciseSearchQuery: String,
): List<WorkoutSet> = currentSets

/**
 * A checked row can make a historical card quiet by default. Today stays in the editable
 * rotation unless the user explicitly parked the persisted card.
 */
internal fun fallbackLogCardDisplayMode(
    date: String,
    today: String,
    sets: List<WorkoutSet>,
): WorkoutCardDisplayMode =
    if (date < today && sets.isNotEmpty() && sets.all(WorkoutSet::completed)) {
        WorkoutCardDisplayMode.COMPACT
    } else {
        WorkoutCardDisplayMode.EXPANDED
    }

/**
 * The set repository publishes an inserted row into its own StateFlow *before* the add call
 * returns, so the session collector can echo that row back while the caller is still awaiting it.
 * Appending the returned rows blindly then shows the same set twice until the next repository
 * emission wipes it. Append only what is genuinely missing, in the order the repository built it.
 */
internal fun appendSessionSets(
    current: List<WorkoutSet>,
    added: List<WorkoutSet>,
): List<WorkoutSet> {
    if (added.isEmpty()) return current
    val merged = current.toMutableList()
    added.forEach { row ->
        if (merged.none { existing -> isSameSessionSet(existing, row) }) merged += row
    }
    return merged
}

/**
 * Identity for an added row, strongest evidence first: the syncId survives the temp-to-real id
 * remap, a real id survives everything else, and a row carrying neither (only the demo repository
 * mints these) can be recognised solely by the logical address it would occupy.
 */
internal fun isSameSessionSet(existing: WorkoutSet, added: WorkoutSet): Boolean = when {
    added.syncId.isNotBlank() -> existing.syncId == added.syncId
    added.id != 0L -> existing.id == added.id
    else ->
        existing.id == 0L &&
            existing.syncId.isBlank() &&
            existing.date == added.date &&
            existing.exerciseId == added.exerciseId &&
            existing.setNumber == added.setNumber
}

internal fun workoutSetIdRemap(
    previous: List<WorkoutSet>,
    current: List<WorkoutSet>,
): Map<Long, Long> {
    val currentBySyncId = current
        .filter { it.syncId.isNotBlank() }
        .associateBy(WorkoutSet::syncId)
    return previous.mapNotNull { old ->
        val updated = currentBySyncId[old.syncId] ?: return@mapNotNull null
        if (old.id == updated.id) null else old.id to updated.id
    }.toMap()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(
    private val repos: AppRepos,
    private val isOnline: () -> Boolean = { true },
    // Tests hand in their test dispatcher so the scrub-commit debounce runs on virtual time.
    private val stepCommitContext: CoroutineContext = EmptyCoroutineContext,
    // Keep elapsed rest tied to wall time in production and to virtual time in tests.
    private val restNowMillis: () -> Long = ::nowMillis,
    private val classifyMuscles: suspend (List<Exercise>) -> List<MuscleClassification> = { targets ->
        ExerciseMuscleClassifier().classifyBatch(targets)
    },
    private val derivationContext: CoroutineContext = Dispatchers.Default,
) : ViewModel() {
    private val exerciseRepository = repos.exercises
    private val setRepository = repos.sets
    private val workoutRepository = repos.workout
    private val pendingReviewRepository = repos.pendingReviews

    private val assist = com.ayman.ecolift.agent.assist.AssistService(
        tools = com.ayman.ecolift.agent.tools.AgentToolsImpl(repos),
        engine = null, // Log helpers are local-only; no Groq.
    )
    private val _assistSuggestions = MutableStateFlow<Map<Long, com.ayman.ecolift.agent.assist.SetSuggestion>>(emptyMap())
    private val _inputWarnings = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _recommendationStates = MutableStateFlow<Map<Long, RecommendationState>>(emptyMap())
    private val _logGestureHintSessions = MutableStateFlow(LogGestureHintSessionStore.load())
    val logGestureHintSessions: StateFlow<Int> = _logGestureHintSessions

    // `?date=YYYY-MM-DD` deep-links a day; the QA harness relies on it.
    private val currentDate = MutableStateFlow(logDateFromLocation() ?: WorkoutDates.today())
    private val _sessionSets = MutableStateFlow(SetQueries.forDate(setRepository.sets.value, currentDate.value))
    private val _sessionSetSnapshot = MutableStateFlow(
        DatedWorkoutSets(currentDate.value, _sessionSets.value),
    )
    private val exerciseInput = MutableStateFlow("")
    private val predictiveSuggestions = MutableStateFlow<List<Exercise>>(emptyList())
    private val _pendingExerciseResolution = MutableStateFlow<PendingExerciseResolutionUi?>(null)
    private val reviewsExpanded = MutableStateFlow(false)
    private val _smartAdjustments = MutableStateFlow<Map<Long, SmartSetAdjustment>>(emptyMap())
    private val _uiMessage = MutableStateFlow<String?>(null)
    private val _pendingSplitSwitch = MutableStateFlow<PendingSplitSwitchConfirmation?>(null)
    val pendingSplitSwitch: StateFlow<PendingSplitSwitchConfirmation?> = _pendingSplitSwitch.asStateFlow()
    private val _splitSwitchOutcomes = MutableSharedFlow<SplitSwitchOutcome>(extraBufferCapacity = 1)
    val splitSwitchOutcomes: SharedFlow<SplitSwitchOutcome> = _splitSwitchOutcomes.asSharedFlow()
    private val _exerciseSwap = MutableStateFlow<ExerciseSwapUi?>(null)
    val exerciseSwap: StateFlow<ExerciseSwapUi?> = _exerciseSwap.asStateFlow()
    private val _exerciseSwapUndo = MutableStateFlow<ExerciseSwapUndoUi?>(null)
    val exerciseSwapUndo: StateFlow<ExerciseSwapUndoUi?> = _exerciseSwapUndo.asStateFlow()
    private var swapAfterSignature: DayContentSignature? = null
    private var swapRequest: com.ayman.ecolift.web.supabase.ExerciseSwapRequest? = null
    private var swapGeneration = 0L
    private var swapUndoGeneration = 0L
    private var dayUndoGeneration = 0L
    private var swapSearchJob: kotlinx.coroutines.Job? = null
    private var swapInvalidationJob: kotlinx.coroutines.Job? = null
    private val _swapUndoBusy = MutableStateFlow(false)
    val swapUndoBusy: StateFlow<Boolean> = _swapUndoBusy.asStateFlow()
    private val _dayUndo = MutableStateFlow<DayUndoState?>(null)
    val dayUndo: StateFlow<DayUndoState?> = _dayUndo.asStateFlow()
    private var nextDayUndoId = 0L
    private val _restStopwatch = MutableStateFlow<RestStopwatch?>(null)
    val restStopwatch: StateFlow<RestStopwatch?> = _restStopwatch.asStateFlow()
    private var activeRest: ActiveRest? = null
    private var restTickerJob: Job? = null
    private val splitSwitchMutex = Mutex()
    private var nextSplitSwitchRequestId = 0L
    private var nextExerciseResolutionRequestId = 0L
    private var nextExerciseAddToken = 0L
    private var activeExerciseAdd: ExerciseAddContext? = null
    private var pendingExerciseAddContext: ExerciseAddContext? = null
    private var predictiveSearchGeneration = 0L
    private var predictiveSearchJob: Job? = null
    private var latestSplitSwitchRequestId = 0L
    private var dateSelectionGeneration = 0L
    private var pendingSplitSwitchContext: PendingSplitSwitchContext? = null

    private val _exerciseHistorySnapshot = MutableStateFlow(ExerciseHistorySnapshot())
    private var historyRefreshGeneration = 0L

    /** Tape steps waiting for the scrub to settle; see [stepSetLocal]. */
    private val stepCommits = SetCommitBuffer()
    private val stepCommitJobs = mutableMapOf<String, Job>()

    /**
     * Where settled tape steps are written. Never cancelled, so a write that is waiting on the
     * repository's mutation lock when the view model is cleared still reaches the sync queue.
     * Same dispatcher as [viewModelScope], so a flushed step enters the repository before the
     * write that triggered the flush, exactly as when the step wrote through viewModelScope.
     */
    private val commitScope = CoroutineScope(
        SupervisorJob() + (viewModelScope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Main),
    )

    /** Step writes started but not yet returned from the repository; tests pin clearing against it. */
    internal var stepCommitsInFlight = 0
        private set

    private val flushWhenHidden = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) flushPendingSetCommits()
    }

    private val workoutDays = workoutRepository.observeAllWorkoutDays()
    private val currentDay = currentDate.flatMapLatest { workoutRepository.observeWorkoutDay(it) }
    private val selectedDaySnapshot = currentDate.flatMapLatest { date ->
        workoutRepository.observeWorkoutDay(date).map { day -> DatedWorkoutDay(date, day) }
    }

    /** Every day with at least one checked set. Drives the calendar's worked-day marks. */
    val workedDays: StateFlow<Set<LocalDate>> =
        setRepository.sets
            .map { sets -> sets.filter { it.completed }.mapTo(HashSet()) { it.date } }
            .distinctUntilChanged()
            .map { dates -> dates.mapNotNullTo(mutableSetOf()) { runCatching { LocalDate.parse(it) }.getOrNull() } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet()
            )

    init {
        repos.dataGeneration?.let { generation ->
            val initial = generation.changes.value
            viewModelScope.launch {
                var observed = initial
                generation.changes.collect { current ->
                    if (current != observed) {
                        observed = current
                        swapSearchJob?.cancel()
                        swapInvalidationJob?.cancel()
                        swapRequest = null
                        swapAfterSignature = null
                        _exerciseSwapUndo.value = null
                        _exerciseSwap.value = null
                        _swapUndoBusy.value = false
                        _dayUndo.value = null
                        _ticketSwaps.value = emptyMap()
                        _ticketInclusion.value = emptyMap()
                        _chosenEmptyDaySlot.value = emptyMap()
                        _putAwayEmptyDay.value = emptySet()
                        dateSelectionGeneration++
                        latestSplitSwitchRequestId = ++nextSplitSwitchRequestId
                        clearPendingSplitSwitch()
                        clearExerciseSearch()
                        stepCommitJobs.values.forEach { it.cancel() }
                        stepCommitJobs.clear()
                        stepCommits.discard()
                        _unsavedSetInputs.value = emptyMap()
                        _smartAdjustments.value = emptyMap()
                        _assistSuggestions.value = emptyMap()
                        _inputWarnings.value = emptyMap()
                        _recommendationStates.value = emptyMap()
                        setActiveRest(null)
                        val date = currentDate.value
                        publishSessionSets(date, SetQueries.forDate(setRepository.sets.value, date))
                    }
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(flushWhenHidden)
        viewModelScope.launch {
            var lastLoadedDate: String? = null
            combine(currentDate, setRepository.sets) { date, allSets ->
                date to SetQueries.forDate(allSets, date)
            }.collect { (date, repositorySets) ->
                val dateChanged = lastLoadedDate != date
                // A legacy sync can remap a temp id or assign a syncId; the pending step follows the row.
                if (!dateChanged) rekeyPendingSetCommits(_sessionSets.value, repositorySets)
                // A scrub's steps are not in the repository yet; keep them on screen over any echo.
                val sets = stepCommits.overlay(repositorySets)
                if (!dateChanged) reconcileTransientSetIds(_sessionSets.value, sets)
                if (dateChanged) {
                    _recommendationStates.value = emptyMap()
                    _exerciseHistorySnapshot.value = ExerciseHistorySnapshot(date = date)
                }
                val shouldRefreshHistory = dateChanged || _sessionSets.value != sets
                publishSessionSets(date, sets)
                lastLoadedDate = date
                if (shouldRefreshHistory) updateHistoricalTopSets(date, sets)
            }
        }
        launchQuietClassification {
            backfillKnownExerciseMuscles()
        }
        viewModelScope.launch {
            // Re-reads current state inside the collector instead of trusting the combine's
            // captured payload: a slow-to-process trigger (e.g. the "date changed" tuple queued
            // just before a split switch races ahead and publishes a fresh offer) would otherwise
            // compare the live offer to a stale, superseded signature and clear it wrongly.
            combine(currentDate, setRepository.sets, setRepository.cards) { _, _, _ -> Unit }.collect {
                _exerciseSwapUndo.value?.let { swap ->
                    swapInvalidationJob?.cancel()
                    if (swap.date != currentDate.value) {
                        _exerciseSwapUndo.value = null
                    } else if (swapAfterSignature != currentSignature(swap.date)) {
                        // Sets and cards publish separately during a snapshot refresh. Check
                        // settled content so a momentary mixed snapshot cannot erase Undo.
                        swapInvalidationJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(150)
                            if (_exerciseSwapUndo.value == swap && swapAfterSignature != currentSignature(swap.date)) {
                                _exerciseSwapUndo.value = null
                            }
                        }
                    }
                }
                _exerciseSwap.value?.let { swap ->
                    if (swap.date != currentDate.value && !swap.busy) dismissExerciseSwap()
                }
                val offer = (_dayUndo.value as? DayUndoState.Offered)?.offer ?: return@collect
                val date = currentDate.value
                if (offer.date != date || offer.afterSignature != currentSignature(date)) _dayUndo.value = null
            }
        }
    }

    private fun dataGeneration(): Long = repos.dataGeneration?.changes?.value ?: 0L

    private suspend fun <T : Any> withDataGeneration(expected: Long, block: suspend () -> T): T? {
        val generation = repos.dataGeneration ?: return block()
        // A clipboard add already holds this lock. Locking again on the same call deadlocks.
        val held = coroutineContext[HeldGeneration]
        if (held != null) {
            return if (held.expected == expected && generation.changes.value == expected) block() else null
        }
        return generation.withCurrent(expected) {
            withContext(HeldGeneration(expected)) { block() }
        }
    }

    fun openExerciseSwap(sourceId: Long) {
        flushPendingSetCommits()
        if (_exerciseSwap.value?.busy == true) return
        val date = currentDate.value
        val source = exerciseRepository.exercises.value.firstOrNull { it.id == sourceId } ?: return
        if (setRepository.sets.value.none { it.date == date && safeCanonicalId(it.exerciseId) == sourceId }) return
        val model = repos.learnedSplits.model.value
        val requested = _chosenEmptyDaySlot.value[date] ?: attributedSlotId.value
        val group = swapGroupForDay(model, requested,
            setRepository.sets.value.filter { it.date == date }.map { safeCanonicalId(it.exerciseId) }.toSet())
        val slotId = group?.slotId?.takeIf { it > 0 }
        val name = clipboardPrograms.value.firstOrNull { it.slotId == slotId }?.name
        swapRequest = null
        swapGeneration = dataGeneration()
        _exerciseSwap.value = ExerciseSwapUi(date, sourceId, source.name, slotId, name,
            retainedSetCount = setRepository.sets.value.count { it.date == date && safeCanonicalId(it.exerciseId) == sourceId && it.completed },
            context = group?.let { cardSwapSheet(it.key, sourceId) })
        refreshSwapCandidates()
    }

    fun dismissExerciseSwap() {
        if (_exerciseSwap.value?.busy == true) return
        swapSearchJob?.cancel()
        _exerciseSwap.value = null
    }

    fun changeSwapQuery(query: String) {
        val state = _exerciseSwap.value?.takeUnless { it.busy } ?: return
        _exerciseSwap.value = state.copy(query = query.take(120), error = null)
        swapSearchJob?.cancel()
        swapSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(60)
            refreshSwapCandidates()
        }
    }

    private fun refreshSwapCandidates() {
        val state = _exerciseSwap.value ?: return
        _exerciseSwap.value = state.copy(candidates = swapCandidates(
            state.sourceId, state.splitId, state.query, state.date,
            exerciseRepository.exercises.value, exerciseRepository.observeAliases().value,
            setRepository.sets.value, repos.exerciseSwaps.alternatives.value,
            repos.learnedSplits.model.value.groupForSlot(state.splitId)?.members.orEmpty().toSet(), ::safeCanonicalId,
        ))
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun swapExercise(targetId: Long, onReady: (Long, String?) -> Unit) {
        flushPendingSetCommits()
        val state = _exerciseSwap.value?.takeUnless { it.busy } ?: return
        val expectedGeneration = swapGeneration
        if (state.date != currentDate.value || targetId == state.sourceId) return
        if (DemoGuard.blockIfDemo()) {
            _exerciseSwap.value = state.copy(error = com.ayman.ecolift.web.supabase.exerciseSwapError("DEMO"))
            return
        }
        _exerciseSwap.value = state.copy(busy = true, error = null)
        val target = safeCanonicalId(targetId)
        val request = swapRequest?.takeIf { it.date == state.date && it.sourceId == state.sourceId && it.targetId == target }
            ?: com.ayman.ecolift.web.supabase.ExerciseSwapRequest(kotlin.uuid.Uuid.random().toString(), state.date, state.sourceId, target, state.splitId)
        swapRequest = request
        viewModelScope.launch {
            try {
                val result = withDataGeneration(expectedGeneration) { repos.exerciseSwaps.swap(request) } ?: return@launch
                if (dataGeneration() != expectedGeneration) return@launch
                if (!result.ok) {
                    _exerciseSwap.value = if (currentDate.value == state.date)
                        state.copy(error = com.ayman.ecolift.web.supabase.exerciseSwapError(result.code)) else null
                    return@launch
                }
                _exerciseSwap.value = null
                val fresh = SetQueries.forDate(setRepository.sets.value, state.date)
                if (currentDate.value == state.date) {
                    publishSessionSets(state.date, fresh)
                    updateHistoricalTopSets(state.date, fresh)
                    if (dataGeneration() != expectedGeneration) return@launch
                    val id = if (result.code == "UNDONE") state.sourceId else result.targetId ?: target
                    val first = fresh.filter { it.exerciseId == id }.sortedBy { it.setNumber }
                        .let { rows -> rows.firstOrNull { !it.completed } ?: rows.firstOrNull() }
                    _dayUndo.value = null
                    swapAfterSignature = currentSignature(state.date)
                    swapUndoGeneration = expectedGeneration
                    _exerciseSwapUndo.value = if (result.code == "UNDONE") null
                        else ExerciseSwapUndoUi(request.requestId, state.date, state.sourceId, id)
                    onReady(id, first?.syncId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _exerciseSwap.value = if (dataGeneration() == expectedGeneration && currentDate.value == state.date)
                    state.copy(error = com.ayman.ecolift.web.supabase.exerciseSwapError("FAILED")) else null
            }
        }
    }

    fun undoExerciseSwap(onReady: (Long, String?) -> Unit) {
        flushPendingSetCommits()
        val undo = _exerciseSwapUndo.value ?: return
        val expectedGeneration = swapUndoGeneration
        if (_swapUndoBusy.value || DemoGuard.blockIfDemo()) return
        _swapUndoBusy.value = true
        viewModelScope.launch {
            try {
                val result = withDataGeneration(expectedGeneration) { repos.exerciseSwaps.undo(undo.requestId) } ?: return@launch
                if (dataGeneration() != expectedGeneration) return@launch
                if (result.ok) {
                    _exerciseSwapUndo.value = null
                    val fresh = SetQueries.forDate(setRepository.sets.value, undo.date)
                    if (currentDate.value == undo.date) {
                        publishSessionSets(undo.date, fresh)
                        updateHistoricalTopSets(undo.date, fresh)
                        if (dataGeneration() != expectedGeneration) return@launch
                        onReady(undo.sourceId, fresh.filter { it.exerciseId == undo.sourceId }.minByOrNull { it.setNumber }?.syncId)
                    }
                } else {
                    _uiMessage.value = com.ayman.ecolift.web.supabase.exerciseSwapError(result.code)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (dataGeneration() == expectedGeneration) _uiMessage.value = "Couldn't undo the swap. Try again."
            } finally {
                if (dataGeneration() == expectedGeneration) _swapUndoBusy.value = false
            }
        }
    }

    private fun reconcileTransientSetIds(previous: List<WorkoutSet>, current: List<WorkoutSet>) {
        val remap = workoutSetIdRemap(previous, current)
        remapTransientSetIds(remap)
    }

    private fun remapTransientSetIds(remap: Map<Long, Long>) {
        if (remap.isEmpty()) return
        activeRest?.let { rest ->
            remap[rest.completedSetId]?.let { mappedId ->
                setActiveRest(rest.copy(completedSetId = mappedId))
            }
        }
        _assistSuggestions.update { values ->
            values.entries.associate { (setId, suggestion) -> (remap[setId] ?: setId) to suggestion }
        }
        _inputWarnings.update { values ->
            values.entries.associate { (setId, warning) -> (remap[setId] ?: setId) to warning }
        }
        _smartAdjustments.update { values ->
            values.entries.associate { (setId, adjustment) ->
                val mappedSetId = remap[setId] ?: setId
                mappedSetId to adjustment.copy(
                    setId = mappedSetId,
                    sourceSetId = remap[adjustment.sourceSetId] ?: adjustment.sourceSetId,
                )
            }
        }
    }

    /**
     * Publishes the editable rows and their owning date as one UI snapshot. The logger renders
     * [DatedWorkoutSets], so every optimistic edit must travel through this function; updating only
     * [_sessionSets] makes the scrubber appear stuck until the repository echoes the write back.
     */
    private fun publishSessionSets(date: String, sets: List<WorkoutSet>) {
        _sessionSets.value = sets
        _sessionSetSnapshot.value = DatedWorkoutSets(date, sets)
    }

    /** The rows currently open for editing, paired with the day they actually belong to. */
    internal fun sessionSetSnapshot(): DatedWorkoutSets = _sessionSetSnapshot.value

    /**
     * Applies a write that belongs to [date], and only while [date] still owns the published
     * snapshot. A write that lands after the day handed over must not drag the old day's rows
     * under the new date — the repository echo for the new day is already on its way.
     * Returns null when the write was declined for that reason.
     */
    private fun updateSessionSets(
        date: String,
        transform: (List<WorkoutSet>) -> List<WorkoutSet>,
    ): List<WorkoutSet>? {
        if (_sessionSetSnapshot.value.date != date) return null
        val updated = transform(_sessionSets.value)
        publishSessionSets(date, updated)
        return updated
    }

    /**
     * Edits whichever day the published snapshot currently owns. Row edits are aimed at rows the
     * user can see, so they follow the snapshot rather than [currentDate]: re-publishing them
     * against a date that has already advanced is what renders yesterday's sets as today's workout.
     */
    private fun editSessionSets(transform: (List<WorkoutSet>) -> List<WorkoutSet>): List<WorkoutSet> {
        val snapshot = _sessionSetSnapshot.value
        val updated = transform(snapshot.sets)
        publishSessionSets(snapshot.date, updated)
        return updated
    }

    private suspend fun updateHistoricalTopSets(date: String, currentSets: List<WorkoutSet>) {
        // A write that finished after date navigation may still request an old-day refresh. It must
        // not advance the shared generation and thereby invalidate the new day's in-flight result.
        if (currentDate.value != date) return
        val refreshGeneration = ++historyRefreshGeneration
        val exerciseIds = currentSets.map { it.exerciseId }.distinct()
        val topSets = mutableMapOf<Long, ExerciseHistoryTopSets>()
        val pbs = mutableMapOf<Long, Boolean>()
        // Snapshot once per refresh: the trend re-derives from scratch whenever a set changes, so
        // a bodyweight edit in Settings is picked up on the next history refresh without a
        // dedicated observer here.
        val userBodyweightLbs = workoutRepository.observeUserBodyweightLbs().first()

        exerciseIds.forEach { id ->
            val history = setRepository.getRecentHistoryForExercise(id, date)
            val lastDate = history.maxOfOrNull { it.date }
            val lastSessionSets = history.filter { it.date == lastDate }

            topSets[id] = if (lastSessionSets.isNotEmpty()) {
                val last = topSetLabel(lastSessionSets)
                val best = topSetLabel(history + currentSets.filter { it.exerciseId == id })
                ExerciseHistoryTopSets(
                    lastTopSet = last,
                    bestTopSet = best,
                    lastSessionSets = lastSessionSets
                        .sortedBy { it.setNumber }
                        .map {
                            GhostSetUi(
                                weightLbs = it.weightLbs,
                                reps = it.reps,
                                isBodyweight = it.isBodyweight,
                                isWarmup = it.isWarmup,
                            )
                        },
                    lastSessionDate = lastDate,
                    e1rmHistory = sessionE1rmHistory(history, userBodyweightLbs),
                )
            } else {
                ExerciseHistoryTopSets(lastTopSet = null, bestTopSet = null)
            }

            val allTimeMaxWeight = setRepository.getMaxWeightBeforeDate(id, date) ?: 0
            pbs[id] = currentSets.any { it.exerciseId == id && WorkoutMetrics.isStrengthEligible(it) && (it.weightLbs ?: 0) > allTimeMaxWeight }
        }
        if (
            refreshGeneration == historyRefreshGeneration &&
            currentDate.value == date
        ) {
            _exerciseHistorySnapshot.value = ExerciseHistorySnapshot(
                date = date,
                topSets = topSets,
                pbs = pbs,
            )
        }
    }

    private val alternativesMemo = PriorHistoryMemo<EmptyDayLedgerSnapshot>()

    /** How often the learned programs / ledger history were built; tests pin that edits skip them. */
    internal var learnedProgramBuilds = 0
        private set
    internal var ledgerBuilds = 0
        private set
    internal var alternativesBuilds = 0
        private set

    /** The group the picker placed on a date: that day's proposal. Absent means the due group. */
    private val _chosenEmptyDaySlot = MutableStateFlow<Map<String, Long>>(emptyMap())
    val chosenEmptyDaySlot: StateFlow<Map<String, Long>> = _chosenEmptyDaySlot.asStateFlow()

    fun chooseEmptyDaySlot(date: String, slotId: Long) {
        _chosenEmptyDaySlot.value = _chosenEmptyDaySlot.value + (date to slotId)
    }

    /**
     * Dates whose suggested sheet was put away (the X), in memory exactly like [chosenEmptyDaySlot].
     * Putting a sheet away hides it for that date only: it never advances or skips the rotation,
     * deletes nothing and leaves the placed group, inclusion and swaps as they were.
     */
    private val _putAwayEmptyDay = MutableStateFlow<Set<String>>(emptySet())
    val putAwayEmptyDay: StateFlow<Set<String>> = _putAwayEmptyDay.asStateFlow()

    fun putAwayEmptyDay(date: String) {
        _putAwayEmptyDay.value = _putAwayEmptyDay.value + date
    }

    fun bringBackEmptyDay(date: String) {
        _putAwayEmptyDay.value = _putAwayEmptyDay.value - date
    }

    private val uiInputCore = combine(
        currentDate,
        exerciseInput,
        predictiveSuggestions,
        reviewsExpanded,
        _smartAdjustments
    ) { date, input, suggestions, expanded, smartAdjustments ->
        UiInputCore(
            date = date,
            input = input,
            suggestions = suggestions,
            expanded = expanded,
            smartAdjustments = smartAdjustments,
        )
    }

    // combine() tops out at five flows; bodyweight rides in with uiInputCore so the trend's
    // bodyweight math (see sessionE1rmHistory) stays on the same settings value the card shows.
    private val uiInputCoreWithBodyweight = combine(
        uiInputCore,
        workoutRepository.observeUserBodyweightLbs(),
    ) { core, bodyweightLbs -> core to bodyweightLbs }

    private val baseUiInputs = combine(
        uiInputCoreWithBodyweight,
        _assistSuggestions,
        _inputWarnings,
        _exerciseHistorySnapshot
    ) { (core, bodyweightLbs), assistSuggestions, inputWarnings, historySnapshot ->
        UiInputs(
            date = core.date,
            input = core.input,
            suggestions = core.suggestions,
            expanded = core.expanded,
            smartAdjustments = core.smartAdjustments,
            assistSuggestions = assistSuggestions,
            inputWarnings = inputWarnings,
            historySnapshot = historySnapshot.takeIf { it.date == core.date }
                ?: ExerciseHistorySnapshot(date = core.date),
            userBodyweightLbs = bodyweightLbs,
        )
    }

    private val uiInputs = combine(baseUiInputs, _recommendationStates) { inputs, recommendationStates ->
        inputs.copy(recommendationStates = recommendationStates)
    }

    private val scheduleSnapshot = combine(workoutDays, selectedDaySnapshot, _sessionSetSnapshot, setRepository.cards) {
            workoutDaysList,
            daySnapshot,
            setSnapshot,
            cards,
        ->
        ScheduleSnapshot(
            date = setSnapshot.date,
            workoutDays = workoutDaysList,
            currentDay = daySnapshot.day.takeIf { daySnapshot.date == setSnapshot.date },
            currentSets = setSnapshot.sets,
            cards = cards,
        )
    }

    private val librarySnapshot = combine(
        exerciseRepository.exercises,
        workoutRepository.cycle,
        workoutRepository.observeCycleSlots(),
        pendingReviewRepository.unresolved,
        repos.learnedSplits.model,
    ) { exercises, cycle, slots, pendingReviews, model ->
        LibrarySnapshot(
            exercises = exercises,
            cycle = cycle,
            slots = slots.visibleCycleSlots(model.smartSuggest),
            pendingReviews = pendingReviews,
            dueSlotId = model.group(model.dueGroupKey)?.slotId,
            model = model,
        )
    }

    private val dbSnapshot = combine(scheduleSnapshot, librarySnapshot, chosenEmptyDaySlot) { schedule, library, proposals ->
        val model = library.model
        val hasChecked = schedule.currentSets.any { it.completed }
        val attributed = if (hasChecked) model.displaySlotId(schedule.date, schedule.currentDay) else null
        val proposed = proposals[schedule.date] ?: schedule.currentDay?.cycleSlotId
            ?: if (!hasChecked) model.group(model.dueGroupKey)?.slotId else null
        val slotId = attributed ?: proposed
        val group = model.visibleGroups.firstOrNull { it.slotId == slotId && it.slotId > 0 }
        // One shared alternatives pass per repository/proposal change; input changes reuse it.
        // Reads only history before the shown day, so an edit on the shown day reuses the last pass.
        val alternatives = group?.let {
            alternativesMemo.get(schedule.date, "${it.key}|${it.slotId}|${it.name}", setRepository.sets.value,
                listOf(model, library.exercises)) { prior ->
                alternativesBuilds++
                buildEmptyDayLedgerSnapshot(schedule.date,
                    listOf(ClipboardProgramUi(it.slotId, it.name, 0, false, emptyList(), groupKey = it.key)),
                    model, library.exercises, prior, ::safeCanonicalId)
            }
        }
        val takenGroups = schedule.currentSets.groupBy { it.exerciseId }.mapNotNull { (id, rows) ->
            if (group != null &&
                canUseTakenChip(group.slotId,
                    alternatives?.let { slotAlternatives(it, group.key, id).size } ?: 0, rows.any { it.completed }))
                id to group.key else null
        }.toMap()
        DbSnapshot(
            date = schedule.date,
            exercises = library.exercises,
            cycle = library.cycle,
            slots = library.slots,
            workoutDays = schedule.workoutDays,
            currentDay = schedule.currentDay,
            currentSets = schedule.currentSets,
            cards = schedule.cards,
            pendingReviews = library.pendingReviews,
            dueSlotId = library.dueSlotId,
            takenGroups = takenGroups,
        )
    }

    val uiState: StateFlow<LogUiState> = combine(
        uiInputs,
        dbSnapshot,
        _uiMessage,
        _pendingExerciseResolution,
    ) {
            inputs,
            snapshot,
            uiMessage,
            pendingExerciseResolution,
        ->
        buildUiState(inputs, snapshot, uiMessage, pendingExerciseResolution)
    }.flowOn(derivationContext).onCompletion { flushPendingSetCommits() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initialUiState(),
    )

    private fun initialUiState(): LogUiState {
        val date = currentDate.value
        return buildUiState(
            inputs = UiInputs(
                date = date,
                input = "",
                suggestions = emptyList(),
                expanded = false,
                smartAdjustments = emptyMap(),
                historySnapshot = _exerciseHistorySnapshot.value,
                userBodyweightLbs = repos.settings.settings.value.userBodyweightLbs,
            ),
            snapshot = DbSnapshot(
                date = date,
                exercises = exerciseRepository.exercises.value,
                cycle = workoutRepository.cycle.value,
                slots = emptyList(),
                workoutDays = emptyList(),
                currentDay = null,
                currentSets = _sessionSets.value,
                cards = setRepository.cards.value,
                pendingReviews = emptyList(),
                dueSlotId = null,
                takenGroups = emptyMap(),
            ),
            uiMessage = null,
            pendingExerciseResolution = null,
        )
    }

    /** The clipboard's pages: every lift with the last day it had a checked set, newest first. */
    val clipboardLifts: StateFlow<List<ClipboardLiftUi>> =
        combine(exerciseRepository.exercises, setRepository.sets.distinctUntilChangedBy(::clipboardLiftsKey)) { exercises, sets ->
            buildClipboardLifts(exercises, sets, ::safeCanonicalId)
        }.flowOn(derivationContext).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private class BoardInputs(val slots: List<CycleSlot>, val rows: List<SplitExercise>, val state: SplitChoicesState) {
        val choices: Map<Long, SplitMemberChoice> get() = (state as? SplitChoicesState.Ready)?.choices.orEmpty()
    }

    private val boardInputs = combine(
        workoutRepository.observeAllCycleSlots(),
        workoutRepository.observeAllSplitExercises(),
        workoutRepository.splitMemberChoices,
    ) { slots, rows, state ->
        BoardInputs(slots, rows, state)
    }

    /**
     * The clipboard's pocket, the empty-day ticket and the picker: every learned group and
     * hand-made split in due order (spec 2026-09-28), with each lift's last top set and stake
     * read from history before the shown date. Independent of the cycle's is_active flag.
     */
    private val learnedProposalSnapshot: StateFlow<LearnedProposalSnapshot> =
        combine(
            // A weight/reps edit on the shown day or later changes nothing these builders read.
            combine(currentDate, setRepository.sets) { date, sets -> date to sets }
                .distinctUntilChangedBy { (date, sets) -> learningSetsKey(date, sets) },
            repos.learnedSplits.model,
            exerciseRepository.exercises,
            boardInputs,
        ) { (date, sets), model, exercises, boards ->
            learnedProgramBuilds++
            LearnedProposalSnapshot(date, model, exercises, sets,
                buildLearnedPrograms(model, date, exercises, sets, boards.slots, boards.rows, boards.choices, ::safeCanonicalId)
                    .map { program -> program.copy(startable = canStartSlot(boards.state, program.slotId)) })
        }.flowOn(derivationContext).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), LearnedProposalSnapshot.EMPTY)

    /**
     * Splits retired from the picker's X, hidden at once while the write and the relearn run; a
     * failed write puts the split back.
     */
    private val _retiredSlots = MutableStateFlow<Set<Long>>(emptySet())

    val clipboardPrograms: StateFlow<List<ClipboardProgramUi>> =
        combine(learnedProposalSnapshot, _retiredSlots) { snapshot, retired ->
            if (retired.isEmpty()) snapshot.programs else snapshot.programs.filterNot { it.slotId in retired }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The group the shown day belongs to, from its checked sets (the model), for the clipboard's
     * stub and the Log header; a frozen past day keeps its stored split. Before the first model,
     * and only while Smart Suggest is on, it is the stored split.
     */
    val attributedSlotId: StateFlow<Long?> =
        combine(currentDate, repos.learnedSplits.model, workoutDays) { date, model, days ->
            val stored = days.firstOrNull { it.date == date }
            if (model.asOf.isEmpty() && model.smartSuggest) stored?.cycleSlotId else model.displaySlotId(date, stored)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * A program loaded from the pocket, the Splits tab or the ticket becomes the day's proposal, so
     * the stamp names what was loaded until a set is checked instead of falling back to the due
     * group. Called once the load has happened; a load the user backs out of changes nothing.
     */
    private fun rememberLoadedProposal(request: SplitSwitchRequest) {
        val slotId = (request.target as? SplitSwitchTarget.Proposal)?.slotId ?: return
        chooseEmptyDaySlot(request.date, slotId)
    }

    /** Ticket rows switched on or off, keyed by "date|group key"; an absent key means the default. */
    private val _ticketInclusion = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())
    val ticketInclusion: StateFlow<Map<String, Set<Long>>> = _ticketInclusion.asStateFlow()

    /** Day-only substitutions, keyed like inclusion; successful Start appends choices to saved slots. */
    private val _ticketSwaps = MutableStateFlow<Map<String, Map<Long, Long>>>(emptyMap())
    val ticketSwaps: StateFlow<Map<String, Map<Long, Long>>> = _ticketSwaps.asStateFlow()

    private val emptyDayLedgerSnapshot = learnedProposalSnapshot.map { source ->
        // The card Log must acquire no history work, including while a collector is stopping.
        if (source.date.isBlank() || source.sets.any { it.date == source.date }) {
            EmptyDayLedgerSnapshot.EMPTY
        } else {
            ledgerBuilds++
            buildEmptyDayLedgerSnapshot(source.date, source.programs, source.model, source.exercises, source.sets, ::safeCanonicalId)
        }
    }.flowOn(derivationContext).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), EmptyDayLedgerSnapshot.EMPTY)

    /** Only TodayScreen's empty-day branch subscribes to this projection. */
    val emptyDayLedger: StateFlow<EmptyDayLedgerUi> = combine(emptyDayLedgerSnapshot, ticketSwaps) { snapshot, swaps ->
        applyLedgerSwaps(snapshot, swaps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), EmptyDayLedgerUi())

    /**
     * Read-only snapshot function shared with slice 2b. The ledger uses its cached state;
     * a caller without that collector can still compute alternatives from repository truth.
     * Call from ViewModel state projection on data changes, never during card composition.
     */
    fun slotAlternatives(groupKey: String, exerciseId: Long): List<SlotAlternativeUi> {
        val cached = emptyDayLedgerSnapshot.value
        val date = currentDate.value
        if (cached.date == date) return slotAlternatives(cached, groupKey, exerciseId)
        val model = repos.learnedSplits.model.value
        val group = model.group(groupKey) ?: return emptyList()
        val program = ClipboardProgramUi(group.slotId, group.name, 0, false, emptyList(), groupKey = groupKey)
        val snapshot = buildEmptyDayLedgerSnapshot(date, listOf(program), model,
            exerciseRepository.exercises.value, setRepository.sets.value, ::safeCanonicalId)
        return slotAlternatives(snapshot, groupKey, exerciseId)
    }

    fun swapTicketRow(date: String, program: ClipboardProgramUi, usualExerciseId: Long, chosenExerciseId: Long) {
        if (DemoGuard.blockIfDemo()) return
        if (exerciseRepository.exercises.value.none { it.id == chosenExerciseId }) return
        val key = ticketInclusionKey(date, program.groupKey)
        val swaps = _ticketSwaps.value[key].orEmpty()
        _ticketSwaps.value = _ticketSwaps.value + (key to if (usualExerciseId == chosenExerciseId) swaps - usualExerciseId else swaps + (usualExerciseId to chosenExerciseId))
        // Choosing a lift opts the slot into today's Start, including a faded/tail slot.
        _ticketInclusion.value = _ticketInclusion.value + (key to ((_ticketInclusion.value[key] ?: defaultTicketInclusion(program)) + usualExerciseId))
    }

    fun undoTicketSwap(date: String, program: ClipboardProgramUi, usualExerciseId: Long) {
        if (DemoGuard.blockIfDemo()) return
        val key = ticketInclusionKey(date, program.groupKey)
        _ticketSwaps.value = _ticketSwaps.value + (key to (_ticketSwaps.value[key].orEmpty() - usualExerciseId))
    }

    fun dropTicketRow(date: String, program: ClipboardProgramUi, usualExerciseId: Long) {
        if (DemoGuard.blockIfDemo()) return
        val key = ticketInclusionKey(date, program.groupKey)
        _ticketInclusion.value = _ticketInclusion.value + (key to ((_ticketInclusion.value[key] ?: defaultTicketInclusion(program)) - usualExerciseId))
    }

    /** Flips one ticket row for [date] and [program] between included and not. */
    fun toggleTicketRow(date: String, program: ClipboardProgramUi, exerciseId: Long) {
        val key = ticketInclusionKey(date, program.groupKey)
        val current = _ticketInclusion.value[key] ?: defaultTicketInclusion(program)
        val next = if (exerciseId in current) current - exerciseId else current + exerciseId
        _ticketInclusion.value = _ticketInclusion.value + (key to next)
    }

    /** Start on the ticket: seeds the included rows in ticket order. */
    fun startProposal(date: String, program: ClipboardProgramUi): Long? {
        if (!canStartSlot(workoutRepository.splitMemberChoices.value, program.slotId)) return null
        val key = ticketInclusionKey(date, program.groupKey)
        val included = includedTicketLifts(program, _ticketInclusion.value[key], _ticketSwaps.value[key].orEmpty())
        return seedDayFromProposal(date, included, program.name, program.slotId, groupKey = program.groupKey)
    }

    /**
     * Loads exactly [exerciseIds] through the whole-last-session copy, with the split-switch
     * confirmation and Undo. [proposalStartDay] decides whether the day stores the split.
     */
    fun seedDayFromProposal(
        date: String,
        exerciseIds: List<Long>,
        label: String = "Workout",
        slotId: Long? = null,
        intent: SplitSwitchIntent = SplitSwitchIntent.UserSelection,
        groupKey: String? = null,
    ): Long? {
        if (DemoGuard.blockIfDemo() || exerciseIds.isEmpty()) return null
        return acceptSplitSwitchRequest(
            date = date,
            target = SplitSwitchTarget.Proposal(exerciseIds.distinct(), label, slotId),
            intent = intent,
            appends = proposalAppends(slotId, _ticketSwaps.value[ticketInclusionKey(date,
                groupKey ?: repos.learnedSplits.model.value.visibleGroups.firstOrNull { it.slotId == slotId }?.key.orEmpty())].orEmpty()),
        )
    }

    /** The expensive shared sheet projection is created only after the chip is tapped. */
    fun cardSwapSheet(groupKey: String, exerciseId: Long): SwapSheetUi? {
        val model = repos.learnedSplits.model.value
        val group = model.group(groupKey)?.takeIf { it.slotId > 0 } ?: return null
        val date = currentDate.value
        val snapshot = buildEmptyDayLedgerSnapshot(date,
            listOf(ClipboardProgramUi(group.slotId, group.name, 0, false, emptyList(), groupKey = group.key)),
            model, exerciseRepository.exercises.value, setRepository.sets.value, ::safeCanonicalId)
        val name = com.ayman.ecolift.data.splits.learnedGroupLabel(group, exerciseRepository.exercises.value.associateBy { it.id })
        return SwapSheetUi(group.key, exerciseId, exerciseId,
            "${snapshot.lifts[exerciseId]?.region ?: "Lift"} slot · $name", snapshot.ranges[group.key],
            snapshot.sessions[group.key].orEmpty(), slotAlternatives(snapshot, group.key, exerciseId), false, group.colorIndex,
            groupName = name, unavailableExerciseIds = setRepository.sets.value.filter { it.date == date }.mapTo(mutableSetOf()) { it.exerciseId })
    }

    fun useCardLiftToday(date: String, groupKey: String, outgoingId: Long, chosenId: Long) {
        if (DemoGuard.blockIfDemo() || date != currentDate.value) return
        if (repos.learnedSplits.model.value.group(groupKey)?.slotId?.let { it > 0 } != true) return
        openExerciseSwap(outgoingId)
        swapExercise(chosenId) { _, _ -> }
    }

    fun searchSwapLibrary(sourceId: Long, slotId: Long?, query: String): List<SwapCandidateUi> =
        swapCandidates(sourceId, slotId, query, currentDate.value, exerciseRepository.exercises.value,
            exerciseRepository.observeAliases().value, setRepository.sets.value,
            repos.exerciseSwaps.alternatives.value, repos.learnedSplits.model.value.groupForSlot(slotId)?.members.orEmpty().toSet(),
            ::safeCanonicalId)

    /** Persist membership choices synchronously after Start; reconnect retries the same choices. */
    private fun appendChoices(choices: List<com.ayman.ecolift.data.splits.SplitAlternative>) {
        if (choices.isEmpty()) return
        try {
            repos.exerciseSwaps.queueAlternatives(choices.map {
                it.copy(
                sourceId = safeCanonicalId(it.sourceId), alternativeId = safeCanonicalId(it.alternativeId))
            })
        } catch (_: Throwable) {
            _uiMessage.value = "Workout saved. Split choices will retry."
            return
        }
        viewModelScope.launch {
            try {
                repos.exerciseSwaps.flushAlternatives()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiMessage.value = "Your workout is saved. Split choices will retry when connected."
            }
        }
    }

    /** Renames a group's split from the ticket header. A group whose slot is not saved yet waits. */
    fun renameProposal(slotId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || DemoGuard.blockIfDemo()) return
        if (slotId <= 0L) {
            _uiMessage.value = "Rename after this split syncs."
            return
        }
        viewModelScope.launch {
            runCatching { workoutRepository.renameCycleSlot(slotId, trimmed) }
                .onSuccess { repos.learnedSplits.requestRun() }
                .onFailure { _uiMessage.value = "Couldn't rename the split." }
        }
    }

    /**
     * Retires a split from the picker's X: the Splits tab's Retire (suppressed_at), then a relearn.
     * A group whose slot is not saved yet waits, like a rename.
     */
    fun retireSplit(slotId: Long) {
        if (DemoGuard.blockIfDemo()) return
        if (slotId <= 0L) {
            _uiMessage.value = "This split can be retired once it has synced."
            return
        }
        _retiredSlots.value = _retiredSlots.value + slotId
        viewModelScope.launch {
            val retired = writeOrSay("Couldn't retire the split. Try again.") {
                workoutRepository.suppressCycleSlot(slotId)
            }
            if (retired) repos.learnedSplits.requestRun()
            else _retiredSlots.value = _retiredSlots.value - slotId
        }
    }

    /** The name shown in a bulk-action Undo pill: the catalog name, or a filler when it's gone. */
    private fun exerciseNameFor(exerciseId: Long): String =
        exerciseRepository.exercises.value.firstOrNull { it.id == exerciseId }?.name ?: "Lift"

    private fun safeCanonicalId(id: Long): Long = runCatching { exerciseRepository.canonicalId(id) }.getOrDefault(id)

    private fun lastCheckedDate(exerciseId: Long): String? =
        setRepository.sets.value
            .filter { it.completed && safeCanonicalId(it.exerciseId) == exerciseId }
            .maxOfOrNull { it.date }

    /**
     * What a ticked clipboard line needs before it is added. Read-only: the same duplicate check
     * the add path asks about today, run at tick time so the note can ask before anything is saved.
     */
    suspend fun checkClipboardPick(pick: ClipboardPick): ClipboardCheck {
        if (pick is ClipboardPick.Typed) {
            exerciseRepository.findExact(pick.name)?.let { return ClipboardCheck.Exact(it.id, it.name) }
        }
        if (!canOfferExerciseResolution()) return ClipboardCheck.Clear
        val match = when (pick) {
            is ClipboardPick.Existing -> exerciseRepository.duplicateCandidate(pick.exerciseId)
            is ClipboardPick.Typed -> exerciseRepository.duplicateCandidate(pick.name)
        } ?: return ClipboardCheck.Clear
        return ClipboardCheck.LookAlike(match.exerciseId, match.displayName, lastCheckedDate(match.exerciseId))
    }

    /**
     * Adds the ticked lines in the order they were ticked, one at a time through the same steps a
     * single add takes, so each lift is seeded exactly as it is from search. A note's answer does
     * what "Yes, same" / "Different" do today. Stops at the first line that can't be added.
     */
    fun addClipboardPicks(
        requests: List<ClipboardPickRequest>,
        onAdded: (exerciseId: Long, setSyncId: String) -> Unit = { _, _ -> },
    ) {
        flushPendingSetCommits()
        if (requests.isEmpty() || DemoGuard.blockIfDemo()) return
        val expectedGeneration = dataGeneration()
        viewModelScope.launch {
            withDataGeneration(expectedGeneration) add@{
            // Every line belongs to the day the user was on when they tapped. Changing the day
            // mid-run also clears the add token, which reads as "this line is done", so without
            // this check the remaining lines would land on the day the user moved to.
            val startDate = currentDate.value
            val startGeneration = dateSelectionGeneration
            val syncIdsBefore = SetQueries.forDate(setRepository.sets.value, startDate).map { it.syncId }.toSet()
            for (request in requests) {
                if (currentDate.value != startDate || dateSelectionGeneration != startGeneration) return@add
                val context = beginExerciseAdd(survivesQueryEdit = true) ?: return@add
                val carryOn = try {
                    addClipboardPick(request, context)
                } catch (cancelled: CancellationException) {
                    abandonExerciseAdd(context)
                    throw cancelled
                } catch (failure: Throwable) {
                    if (ownsExerciseAdd(context)) {
                        _uiMessage.value = visibleCatalogFailure(failure, "Couldn't add ${request.pick.label}.")
                        abandonExerciseAdd(context)
                    }
                    false
                }
                if (!carryOn) return@add
            }
            if (currentDate.value != startDate || dateSelectionGeneration != startGeneration) return@add
            val added = SetQueries.forDate(setRepository.sets.value, startDate)
                .filter { it.syncId.isNotBlank() && it.syncId !in syncIdsBefore }
            if (added.isEmpty()) return@add
            onAdded(added.first().exerciseId, added.first().syncId)
            val lifts = added.map { it.exerciseId }.distinct().map { id -> id to exerciseNameFor(id) }
            publishDayUndo(startDate, addedCopy(lifts, added.size), DayUndoRestore.DeleteAdded(added.map { it.syncId }.toSet()))
            }
        }
    }

    private suspend fun addClipboardPick(request: ClipboardPickRequest, context: ExerciseAddContext): Boolean {
        val lookAlike = request.lookAlike
        if (lookAlike != null && request.answer == null) {
            // The note must be answered first; the clipboard never sends this.
            abandonExerciseAdd(context)
            return false
        }
        val exerciseId = when (val pick = request.pick) {
            is ClipboardPick.Existing -> when {
                lookAlike == null -> pick.exerciseId
                request.answer == ClipboardAnswer.Same ->
                    mergeClipboardPick(pick.exerciseId, lookAlike.exerciseId) ?: run {
                        abandonExerciseAdd(context)
                        return false
                    }
                else -> {
                    if (pick.exerciseId != lookAlike.exerciseId) {
                        exerciseRepository.recordDistinctPair(pick.exerciseId, lookAlike.exerciseId)
                    }
                    pick.exerciseId
                }
            }
            is ClipboardPick.Typed -> {
                val exact = exerciseRepository.findExact(pick.name)
                when {
                    exact != null -> exact.id
                    lookAlike == null -> createAndClassifyExercise(pick.name).id
                    request.answer == ClipboardAnswer.Same -> {
                        exerciseRepository.recordAlias(lookAlike.exerciseId, pick.name)
                        lookAlike.exerciseId
                    }
                    else -> {
                        val created = createAndClassifyExercise(pick.name)
                        if (created.id != lookAlike.exerciseId) {
                            exerciseRepository.recordDistinctPair(created.id, lookAlike.exerciseId)
                        }
                        created.id
                    }
                }
            }
        }
        if (!ownsExerciseAdd(context)) return false
        finishExerciseResolution(exerciseId, context)
        return activeExerciseAdd == null
    }

    /** "Same lift?" answered yes for two lifts already in the catalog: the merge "Yes, same" runs. */
    private suspend fun mergeClipboardPick(selectedId: Long, candidateId: Long): Long? =
        when (val preparation = exerciseRepository.prepareMerge(selectedId, candidateId)) {
            is ExerciseMergePreparationResult.Ready -> when (
                val result = exerciseRepository.mergeExercises(
                    idA = selectedId,
                    idB = candidateId,
                    expectedKeeperId = preparation.preview.expectedKeeperId,
                )
            ) {
                is ExerciseMergeResult.Success -> result.keeperId
                ExerciseMergeResult.KeeperDrift -> {
                    _uiMessage.value = "Exercise history changed while confirming. Tick it again to answer."
                    null
                }
                ExerciseMergeResult.QueueNotSettled -> {
                    _uiMessage.value = "Finish syncing before merging these exercises."
                    null
                }
                ExerciseMergeResult.ReconciliationRequired -> {
                    _uiMessage.value = "Workout changed during the merge. Answer again."
                    null
                }
            }
            ExerciseMergePreparationResult.QueueNotSettled -> {
                _uiMessage.value = "Finish syncing before merging these exercises."
                null
            }
            ExerciseMergePreparationResult.ReconciliationRequired -> {
                _uiMessage.value = "Workout changed during the merge. Answer again."
                null
            }
        }

    /**
     * Clips a program on from the clipboard's pocket, leaving [skippedExerciseIds] out; seed-only,
     * like the ticket's Start. The Splits tab's Load route uses it too ([intent] DeepLinkLoad).
     * The lifts are the split's IN THIS SPLIT list resolved now, the same as its card, so a program
     * built before the last edit or before the first model cannot load an older list. Nothing loads
     * while the saved boards are unread or this split's board is still saving ([awaitSplitChoices]).
     */
    fun loadProgram(
        slotId: Long,
        skippedExerciseIds: Set<Long>,
        intent: SplitSwitchIntent = SplitSwitchIntent.UserSelection,
    ): Long? {
        if (DemoGuard.blockIfDemo()) return null
        val state = workoutRepository.splitMemberChoices.value
        if (!canStartSlot(state, slotId)) return null
        val choices = (state as SplitChoicesState.Ready).choices
        val group = repos.learnedSplits.model.value.groupForSlot(slotId)
        val slot = activeSlots.value.firstOrNull { it.id == slotId }
        if (group == null && slot == null) return null
        val rows = savedSplitRows.value ?: return null
        val members = effectiveSplitMembers(slotId, group, rows, choices[slotId], ::safeCanonicalId)
        val name = clipboardPrograms.value.firstOrNull { it.slotId == slotId }?.name
            ?: slot?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: group?.let { learnedGroupLabel(it, exerciseRepository.exercises.value.associateBy { exercise -> exercise.id }) }
            ?: "Workout"
        val present = exerciseRepository.exercises.value.mapTo(HashSet()) { it.id }
        return seedDayFromProposal(
            currentDate.value,
            members.inSplit.filterNot { it in skippedExerciseIds }.filter { it in present },
            name,
            slotId,
            intent,
        )
    }

    /** Suspends until [slotId]'s board can start: read, and not being saved. */
    suspend fun awaitSplitChoices(slotId: Long) {
        combine(workoutRepository.splitMemberChoices, activeSlots, savedSplitRows) { choices, slots, rows ->
            canStartSlot(choices, slotId) && slots.any { it.id == slotId } && rows != null
        }.first { it }
    }

    /** The active splits and their saved lists, for a deep link that arrives before the first model. */
    private val activeSlots: StateFlow<List<CycleSlot>> =
        combine(workoutRepository.observeCycleSlots(), repos.learnedSplits.model) { slots, model ->
            slots.visibleCycleSlots(model.smartSuggest)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val savedSplitRows: StateFlow<List<SplitExercise>?> =
        workoutRepository.observeAllSplitExercises().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun goToPreviousDay() {
        flushPendingSetCommits()
        finishActiveWorkoutDate()
        clearExerciseSearch()
        dateSelectionGeneration++
        _dayUndo.value = null
        currentDate.update { WorkoutDates.addDays(it, -1) }
    }

    fun goToNextDay() {
        flushPendingSetCommits()
        finishActiveWorkoutDate()
        clearExerciseSearch()
        dateSelectionGeneration++
        _dayUndo.value = null
        currentDate.update { WorkoutDates.addDays(it, 1) }
    }

    fun selectDate(date: String) {
        flushPendingSetCommits()
        if (date == currentDate.value) return
        finishActiveWorkoutDate()
        clearExerciseSearch()
        dateSelectionGeneration++
        _dayUndo.value = null
        currentDate.value = date
    }

    fun refreshWorkoutFromCloud() {
        flushPendingSetCommits()
        viewModelScope.launch {
            runCatching { setRepository.refresh() }
                .onSuccess { _uiMessage.value = "Workout refreshed." }
                // SupabaseSetRepository reports transport failures through the
                // app-wide sync snackbar; keep the local overlay untouched.
                .onFailure { Unit }
        }
    }

    fun updateExerciseInput(value: String) {
        val generation = ++predictiveSearchGeneration
        exerciseInput.value = value
        predictiveSuggestions.value = emptyList()
        _pendingExerciseResolution.value = null
        pendingExerciseAddContext = null
        if (activeExerciseAdd?.survivesQueryEdit != true) activeExerciseAdd = null
        predictiveSearchJob?.cancel()
        if (value.isNotBlank()) {
            predictiveSearchJob = viewModelScope.launch {
                val results = exerciseRepository.getPredictive(value)
                if (predictiveSearchGeneration == generation && exerciseInput.value == value) {
                    predictiveSuggestions.value = results
                }
            }
        }
    }

    private fun clearExerciseSearch() {
        predictiveSearchGeneration++
        predictiveSearchJob?.cancel()
        predictiveSearchJob = null
        exerciseInput.value = ""
        predictiveSuggestions.value = emptyList()
        _pendingExerciseResolution.value = null
        pendingExerciseAddContext = null
        activeExerciseAdd = null
    }

    private fun beginExerciseAdd(survivesQueryEdit: Boolean = false): ExerciseAddContext? {
        if (activeExerciseAdd != null || _pendingExerciseResolution.value != null) return null
        return ExerciseAddContext(
            token = ++nextExerciseAddToken,
            date = currentDate.value,
            dateGeneration = dateSelectionGeneration,
            searchGeneration = predictiveSearchGeneration,
            survivesQueryEdit = survivesQueryEdit,
        ).also { activeExerciseAdd = it }
    }

    private fun ownsExerciseAdd(context: ExerciseAddContext): Boolean =
        activeExerciseAdd?.token == context.token

    private fun abandonExerciseAdd(context: ExerciseAddContext) {
        if (activeExerciseAdd?.token == context.token) activeExerciseAdd = null
        if (pendingExerciseAddContext?.token == context.token) pendingExerciseAddContext = null
    }

    fun addExerciseFromInput(input: String = exerciseInput.value) {
        flushPendingSetCommits()
        val rawInput = input.trim()
        if (rawInput.isEmpty()) return
        if (DemoGuard.blockIfDemo()) return
        val context = beginExerciseAdd() ?: return
        viewModelScope.launch {
            try {
                requestTypedExercise(rawInput, context)
            } catch (cancelled: CancellationException) {
                abandonExerciseAdd(context)
                throw cancelled
            } catch (failure: Throwable) {
                if (ownsExerciseAdd(context)) {
                    _uiMessage.value = visibleCatalogFailure(failure, "Couldn't add that exercise.")
                    abandonExerciseAdd(context)
                }
            }
        }
    }

    fun useSuggestion(exercise: com.ayman.ecolift.data.Exercise) {
        useSuggestion(exercise.id)
    }

    fun useSuggestion(exerciseId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        val context = beginExerciseAdd() ?: return
        viewModelScope.launch {
            try {
                val exercise = exerciseRepository.getById(exerciseId)
                    ?: run {
                        abandonExerciseAdd(context)
                        return@launch
                    }
                if (!ownsExerciseAdd(context)) return@launch
                if (canOfferExerciseResolution()) {
                    val candidate = exerciseRepository.duplicateCandidate(exercise.id)
                    if (!ownsExerciseAdd(context)) return@launch
                    if (candidate != null) {
                        publishExerciseResolution(
                            context = context,
                            selectedExerciseId = exercise.id,
                            selectedName = exercise.name,
                            typedName = exercise.name,
                            candidateExerciseId = candidate.exerciseId,
                            candidateName = candidate.displayName,
                        )
                        return@launch
                    }
                }
                finishExerciseResolution(exercise.id, context)
            } catch (cancelled: CancellationException) {
                abandonExerciseAdd(context)
                throw cancelled
            } catch (failure: Throwable) {
                if (ownsExerciseAdd(context)) {
                    _uiMessage.value = visibleCatalogFailure(failure, "Couldn't add that exercise.")
                    abandonExerciseAdd(context)
                }
            }
        }
    }

    private suspend fun requestTypedExercise(rawInput: String, context: ExerciseAddContext) {
        exerciseRepository.findExact(rawInput)?.let {
            if (ownsExerciseAdd(context)) finishExerciseResolution(it.id, context)
            return
        }
        if (!ownsExerciseAdd(context)) return
        if (canOfferExerciseResolution()) {
            val candidate = exerciseRepository.duplicateCandidate(rawInput)
            if (!ownsExerciseAdd(context)) return
            if (candidate != null) {
                publishExerciseResolution(
                    context = context,
                    selectedExerciseId = null,
                    selectedName = null,
                    typedName = rawInput,
                    candidateExerciseId = candidate.exerciseId,
                    candidateName = candidate.displayName,
                )
                return
            }
        }
        val exercise = createAndClassifyExercise(rawInput)
        if (ownsExerciseAdd(context)) finishExerciseResolution(exercise.id, context)
    }

    private fun publishExerciseResolution(
        context: ExerciseAddContext,
        selectedExerciseId: Long?,
        selectedName: String?,
        typedName: String,
        candidateExerciseId: Long,
        candidateName: String,
    ) {
        if (!ownsExerciseAdd(context)) return
        val requestId = ++nextExerciseResolutionRequestId
        pendingExerciseAddContext = context.copy(resolutionRequestId = requestId)
        _pendingExerciseResolution.value = PendingExerciseResolutionUi(
            requestId = requestId,
            selectedExerciseId = selectedExerciseId,
            selectedName = selectedName,
            typedName = typedName,
            candidateExerciseId = candidateExerciseId,
            candidateName = candidateName,
        )
    }

    private fun canOfferExerciseResolution(): Boolean =
        shouldOfferExerciseResolution(isOnline = isOnline(), isDemo = DemoGuard.isDemo)

    fun confirmExercisesSame(requestId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo() || !isOnline()) return
        val pending = _pendingExerciseResolution.value?.takeIf { it.requestId == requestId } ?: return
        val context = pendingExerciseAddContext?.takeIf { it.resolutionRequestId == requestId }
        if (pending.isBusy) return
        _pendingExerciseResolution.value = pending.copy(isBusy = true)
        viewModelScope.launch {
            try {
                if (pending.selectedExerciseId == null) {
                    exerciseRepository.recordAlias(pending.candidateExerciseId, pending.typedName)
                    if (isCurrentResolution(pending, context)) {
                        completeExerciseResolution(pending.candidateExerciseId, context)
                    }
                    return@launch
                }

                when (val preparation = exerciseRepository.prepareMerge(
                    pending.selectedExerciseId,
                    pending.candidateExerciseId,
                )) {
                    is ExerciseMergePreparationResult.Ready -> {
                        if (!isCurrentResolution(pending, context)) return@launch
                        when (val result = exerciseRepository.mergeExercises(
                            idA = pending.selectedExerciseId,
                            idB = pending.candidateExerciseId,
                            expectedKeeperId = preparation.preview.expectedKeeperId,
                        )) {
                            is ExerciseMergeResult.Success -> {
                                if (!isCurrentResolution(pending, context)) return@launch
                                completeExerciseResolution(result.keeperId, context)
                            }
                            ExerciseMergeResult.KeeperDrift -> rePromptAfterKeeperDrift(pending, context)
                            ExerciseMergeResult.QueueNotSettled -> showQueueSettlementMessage(pending, context)
                            ExerciseMergeResult.ReconciliationRequired -> showReconciliationMessage(pending, context)
                        }
                    }
                    ExerciseMergePreparationResult.QueueNotSettled -> showQueueSettlementMessage(pending, context)
                    ExerciseMergePreparationResult.ReconciliationRequired -> showReconciliationMessage(pending, context)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                surfaceExerciseResolutionFailure(failure, pending, context)
            }
        }
    }

    fun confirmExercisesDifferent(requestId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo() || !isOnline()) return
        val pending = _pendingExerciseResolution.value?.takeIf { it.requestId == requestId } ?: return
        val context = pendingExerciseAddContext?.takeIf { it.resolutionRequestId == requestId }
        if (pending.isBusy) return
        _pendingExerciseResolution.value = pending.copy(isBusy = true)
        viewModelScope.launch {
            try {
                val selected = pending.selectedExerciseId?.let { exerciseRepository.getById(it) }
                    ?: createAndClassifyExercise(pending.typedName)
                if (!isCurrentResolution(pending, context)) return@launch
                if (selected.id != pending.candidateExerciseId) {
                    exerciseRepository.recordDistinctPair(selected.id, pending.candidateExerciseId)
                }
                if (isCurrentResolution(pending, context)) {
                    completeExerciseResolution(selected.id, context)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                surfaceExerciseResolutionFailure(failure, pending, context)
            }
        }
    }

    private fun isCurrentResolution(
        pending: PendingExerciseResolutionUi,
        context: ExerciseAddContext?,
    ): Boolean =
        _pendingExerciseResolution.value?.requestId == pending.requestId &&
            (context == null || ownsExerciseAdd(context))

    private fun rePromptAfterKeeperDrift(
        pending: PendingExerciseResolutionUi,
        context: ExerciseAddContext?,
    ) {
        if (!isCurrentResolution(pending, context)) return
        _uiMessage.value = "Exercise history changed. Answer again."
        val nextRequestId = ++nextExerciseResolutionRequestId
        _pendingExerciseResolution.value = pending.copy(
            requestId = nextRequestId,
            isBusy = false,
        )
        if (context != null) {
            pendingExerciseAddContext = context.copy(resolutionRequestId = nextRequestId)
        }
    }

    private fun showQueueSettlementMessage(
        pending: PendingExerciseResolutionUi,
        context: ExerciseAddContext?,
    ) {
        if (!isCurrentResolution(pending, context)) return
        _uiMessage.value = "Finish syncing before merging these exercises."
        _pendingExerciseResolution.value = pending.copy(isBusy = false)
    }

    private fun showReconciliationMessage(
        pending: PendingExerciseResolutionUi,
        context: ExerciseAddContext?,
    ) {
        if (!isCurrentResolution(pending, context)) return
        _uiMessage.value = "Workout changed during the merge. Answer again."
        val nextRequestId = ++nextExerciseResolutionRequestId
        _pendingExerciseResolution.value = pending.copy(
            requestId = nextRequestId,
            isBusy = false,
        )
        if (context != null) {
            pendingExerciseAddContext = context.copy(resolutionRequestId = nextRequestId)
        }
    }

    private fun surfaceExerciseResolutionFailure(
        failure: Throwable,
        pending: PendingExerciseResolutionUi,
        context: ExerciseAddContext?,
    ) {
        if (!isCurrentResolution(pending, context)) return
        _uiMessage.value = visibleCatalogFailure(failure, "Couldn't save the exercise decision.")
        _pendingExerciseResolution.update { current ->
            current?.takeIf { it.requestId == pending.requestId }?.copy(isBusy = false) ?: current
        }
    }

    private suspend fun completeExerciseResolution(
        exerciseId: Long,
        context: ExerciseAddContext?,
    ) {
        if (context == null) {
            // Rename/merge resolution changes the catalog only; it must never create a workout set.
            _pendingExerciseResolution.value = null
            return
        }
        finishExerciseResolution(exerciseId, context)
    }

    private suspend fun finishExerciseResolution(exerciseId: Long, context: ExerciseAddContext) {
        if (!ownsExerciseAdd(context)) return
        val canonical = exerciseRepository.canonicalId(exerciseId)
        if (!ownsExerciseAdd(context)) return
        addExerciseSession(canonical, context)
        if (ownsExerciseAdd(context)) clearExerciseSearch()
    }

    fun toggleReviewsExpanded() {
        reviewsExpanded.update { !it }
    }

    fun markReviewResolved(id: Long) {
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            try {
                pendingReviewRepository.markResolved(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiMessage.value = "Couldn't save your answer."
            }
        }
    }

    /**
     * Loads a saved split and writes it as the day's split. No screen calls this since learned
     * splits (every load is seed-only, see [seedDayFromProposal]); the split-switch tests drive the
     * shared replacement machinery through it.
     */
    fun assignCycleSlot(
        slotId: Long,
        intent: SplitSwitchIntent = SplitSwitchIntent.UserSelection,
    ): Long? {
        if (DemoGuard.blockIfDemo() || slotId <= 0L) return null
        return acceptSplitSwitchRequest(
            date = currentDate.value,
            target = SplitSwitchTarget.Saved(slotId),
            intent = intent,
        )
    }

    fun clearCycleSlot(): Long? {
        if (DemoGuard.blockIfDemo()) return null
        val date = currentDate.value
        return acceptSplitSwitchRequest(
            date = date,
            target = SplitSwitchTarget.Clear,
            intent = SplitSwitchIntent.UserSelection,
        )
    }

    fun confirmSplitSwitch(requestId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        val pending = pendingSplitSwitchContext
            ?.takeIf { it.context.request.requestId == requestId }
            ?: return
        if (pending.context.dateSelectionGeneration != dateSelectionGeneration) {
            clearPendingSplitSwitch(requestId)
            viewModelScope.launch {
                _splitSwitchOutcomes.emit(SplitSwitchOutcome.Cancelled(requestId))
            }
            return
        }

        clearPendingSplitSwitch(requestId)
        launchSplitSwitch(pending.context) {
            applySplitSwitch(pending.context, pending.planned)
        }
    }

    fun cancelSplitSwitch(requestId: Long) {
        val pending = pendingSplitSwitchContext
            ?.takeIf { it.context.request.requestId == requestId }
            ?: return
        clearPendingSplitSwitch(requestId)
        viewModelScope.launch {
            _splitSwitchOutcomes.emit(SplitSwitchOutcome.Cancelled(pending.context.request.requestId))
        }
    }

    internal fun splitSwitchTransientStateSnapshot() = SplitSwitchTransientStateSnapshot(
        activeRestSetId = activeRest?.completedSetId,
        assistSetIds = _assistSuggestions.value.keys.toSet(),
        warningSetIds = _inputWarnings.value.keys.toSet(),
        adjustmentSetIds = _smartAdjustments.value.keys.toSet(),
        adjustmentSourceSetIds = _smartAdjustments.value.values.mapTo(linkedSetOf()) { it.sourceSetId },
        recommendationExerciseIds = _recommendationStates.value.keys.toSet(),
    )

    private fun acceptSplitSwitchRequest(
        date: String,
        target: SplitSwitchTarget,
        intent: SplitSwitchIntent,
        appends: List<com.ayman.ecolift.data.splits.SplitAlternative> = emptyList(),
    ): Long {
        flushPendingSetCommits()
        val requestId = ++nextSplitSwitchRequestId
        val context = SplitSwitchRequestContext(
            request = SplitSwitchRequest(
                requestId = requestId,
                date = date,
                target = target,
                intent = intent,
            ),
            dateSelectionGeneration = dateSelectionGeneration,
            dataGeneration = dataGeneration(),
            appends = appends,
        )
        latestSplitSwitchRequestId = requestId
        clearPendingSplitSwitch()
        launchSplitSwitch(context) {
            val planned = buildCanonicalSplitSwitch(context.request)
            if (latestSplitSwitchRequestId != requestId) return@launchSplitSwitch
            val effectiveContext = context.copy(request = planned.resolved.request)
            when (planned.resolved.plan) {
                is SplitSwitchPlan.Apply -> applySplitSwitch(effectiveContext, planned)
                is SplitSwitchPlan.NeedsConfirm -> publishSplitSwitchConfirmation(effectiveContext, planned)
                SplitSwitchPlan.NoOp -> emitNoOpApplied(effectiveContext)
            }
        }
        return requestId
    }

    private fun launchSplitSwitch(
        context: SplitSwitchRequestContext,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                withDataGeneration(context.dataGeneration, block)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (latestSplitSwitchRequestId == context.request.requestId) {
                    emitSplitSwitchFailure(
                        context.request,
                        "Couldn't switch the split. Try again.",
                    )
                }
            }
        }
    }

    private suspend fun applySplitSwitch(
        initialContext: SplitSwitchRequestContext,
        initialPlan: PlannedSplitSwitch,
    ) {
        splitSwitchMutex.withLock {
            val requestId = initialContext.request.requestId
            if (latestSplitSwitchRequestId != requestId) return

            var context = initialContext
            var planned = initialPlan
            while (true) {
                if (latestSplitSwitchRequestId != requestId) return
                var canonicalReplan: PlannedSplitSwitch? = null
                var snapshot: DaySnapshot? = null
                val result = withContext(NonCancellable) {
                    repos.splitReplacement.apply(planned.resolved) {
                        if (latestSplitSwitchRequestId != requestId) {
                            throw CancellationException("Split switch request was superseded")
                        }
                        snapshot = captureDaySnapshot(context.request.date)
                        buildCanonicalSplitSwitch(context.request)
                            .also { canonicalReplan = it }.resolved
                    }
                }

                when (result) {
                    is SplitReplacementResult.Applied -> {
                        val appliedPlan = canonicalReplan ?: planned
                        withContext(NonCancellable) {
                            clearPendingSplitSwitch(requestId)
                            rememberLoadedProposal(context.request)
                            appendChoices(context.appends)
                            cleanupAfterSplitSwitch(context, appliedPlan)
                            // The switch's data write already succeeded by this point; a failure
                            // building or publishing its Undo offer must not turn a successful
                            // switch into a reported "Couldn't prepare workout replacement" — it
                            // just means this one change has no Undo.
                            try {
                                offerSplitSwitchUndo(context.request, requireNotNull(snapshot))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                Unit
                            }
                            _splitSwitchOutcomes.emit(result.outcome)
                        }
                        return
                    }

                    is SplitReplacementResult.ReplanRequired -> {
                        val fresh = requireNotNull(canonicalReplan) {
                            "Coordinator requested a replan without returning canonical state"
                        }
                        require(fresh.resolved == result.replacement) {
                            "Coordinator returned a different canonical replacement"
                        }
                        if (latestSplitSwitchRequestId != requestId) return
                        context = context.copy(request = fresh.resolved.request)
                        when (fresh.resolved.plan) {
                            is SplitSwitchPlan.Apply -> planned = fresh
                            is SplitSwitchPlan.NeedsConfirm -> {
                                publishSplitSwitchConfirmation(context, fresh)
                                return
                            }

                            SplitSwitchPlan.NoOp -> {
                                emitNoOpApplied(context)
                                return
                            }
                        }
                    }

                    is SplitReplacementResult.Failed -> {
                        clearPendingSplitSwitch(requestId)
                        _uiMessage.value = result.outcome.message
                        _splitSwitchOutcomes.emit(result.outcome)
                        return
                    }
                }
            }
        }
    }

    private fun publishSplitSwitchConfirmation(
        context: SplitSwitchRequestContext,
        planned: PlannedSplitSwitch,
    ) {
        if (latestSplitSwitchRequestId != context.request.requestId) return
        val confirmation = planned.resolved.plan as SplitSwitchPlan.NeedsConfirm
        pendingSplitSwitchContext = PendingSplitSwitchContext(context, planned)
        _pendingSplitSwitch.value = PendingSplitSwitchConfirmation(
            request = confirmation.request,
            targetLabel = planned.targetLabel,
            affectedSetCount = confirmation.affectedSetCount,
        )
    }

    private suspend fun emitNoOpApplied(context: SplitSwitchRequestContext) {
        if (latestSplitSwitchRequestId != context.request.requestId) return
        clearPendingSplitSwitch(context.request.requestId)
        rememberLoadedProposal(context.request)
        appendChoices(context.appends)
        _splitSwitchOutcomes.emit(
            SplitSwitchOutcome.Applied(context.request.requestId, context.request.date),
        )
    }

    private suspend fun emitSplitSwitchFailure(request: SplitSwitchRequest, message: String) {
        clearPendingSplitSwitch(request.requestId)
        _uiMessage.value = message
        _splitSwitchOutcomes.emit(SplitSwitchOutcome.Failed(request.requestId, message))
    }

    private fun clearPendingSplitSwitch(requestId: Long? = null) {
        if (requestId != null && pendingSplitSwitchContext?.context?.request?.requestId != requestId) return
        pendingSplitSwitchContext = null
        _pendingSplitSwitch.value = null
    }

    private suspend fun buildCanonicalSplitSwitch(
        request: SplitSwitchRequest,
    ): PlannedSplitSwitch {
        val allSets = setRepository.sets.value.toList()
        val days = workoutDays.first().toList()
        val slots = workoutRepository.observeCycleSlots().first().toList()
        val splitExercises = workoutRepository.observeAllSplitExercises().first().toList()
        val cycleStartDate = workoutRepository.getActiveCycleStart()
        val day = days.firstOrNull { it.date == request.date }
        val daySets = SetQueries.forDate(allSets, request.date)
        val currentAssignedSlotId = day.resolvedCycleSlotId(slots)
        val baselineSeed = buildCurrentSplitSeed(
            date = request.date,
            currentDay = day,
            currentAssignedSlotId = currentAssignedSlotId,
            allSets = allSets,
            days = days,
            splitExercises = splitExercises,
            cycleStartDate = cycleStartDate,
        )
        val plan = SplitSwitch.plan(
            request = request,
            currentAssignedSlotId = currentAssignedSlotId,
            daySets = daySets,
            seedSets = baselineSeed,
        )
        val effectiveRequest = when (plan) {
            is SplitSwitchPlan.Apply -> plan.request
            is SplitSwitchPlan.NeedsConfirm -> plan.request
            SplitSwitchPlan.NoOp -> request
        }
        val desired = buildDesiredSplitSeed(
            request = effectiveRequest,
            allSets = allSets,
            days = days,
            slots = slots,
            splitExercises = splitExercises,
            cycleStartDate = cycleStartDate,
        )
        val resolved = ResolvedSplitReplacement(
            request = effectiveRequest,
            plan = when (plan) {
                is SplitSwitchPlan.Apply -> SplitSwitchPlan.Apply(effectiveRequest)
                is SplitSwitchPlan.NeedsConfirm -> plan.copy(request = effectiveRequest)
                SplitSwitchPlan.NoOp -> SplitSwitchPlan.NoOp
            },
            precondition = SplitSwitchPrecondition.capture(
                workoutDay = day,
                daySets = daySets,
                baselineSeed = baselineSeed,
                desiredSeed = desired.sets,
            ),
            day = desired.day,
            desiredSets = desired.sets,
        )
        return PlannedSplitSwitch(
            resolved = resolved,
            targetLabel = splitSwitchTargetLabel(effectiveRequest.target, slots),
            outgoingSetIds = daySets.mapTo(linkedSetOf(), WorkoutSet::id),
            outgoingExerciseIds = daySets.mapTo(linkedSetOf(), WorkoutSet::exerciseId),
        )
    }

    private fun buildCurrentSplitSeed(
        date: String,
        currentDay: WorkoutDay?,
        currentAssignedSlotId: Long?,
        allSets: List<WorkoutSet>,
        days: List<WorkoutDay>,
        splitExercises: List<SplitExercise>,
        cycleStartDate: String,
    ): List<WorkoutSet> {
        if (currentAssignedSlotId != null) {
            val occurrence = currentDay?.cycleSlotOccurrence
                ?: (
                    (DayQueries.maxOccurrenceForSlotBefore(
                        days = days,
                        date = date,
                        slotId = currentAssignedSlotId,
                        notBeforeDate = cycleStartDate,
                    ) ?: 0) + 1
                )
            return buildSavedSplitSeed(
                date = date,
                slotId = currentAssignedSlotId,
                occurrence = occurrence,
                allSets = allSets,
                days = days,
                splitExercises = splitExercises,
                cycleStartDate = cycleStartDate,
            )
        }

        return SplitSeedBuilder.unknownBaseline()
    }

    private fun buildDesiredSplitSeed(
        request: SplitSwitchRequest,
        allSets: List<WorkoutSet>,
        days: List<WorkoutDay>,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,
        cycleStartDate: String,
    ): DesiredSplitSeed = when (val target = request.target) {
        is SplitSwitchTarget.Saved -> {
            val day = assignedCycleDay(
                date = request.date,
                slotId = target.slotId,
                slots = slots,
                days = days,
                cycleStartDate = cycleStartDate,
            )
            DesiredSplitSeed(
                day = day,
                sets = buildSavedSplitSeed(
                    date = request.date,
                    slotId = target.slotId,
                    occurrence = checkNotNull(day.cycleSlotOccurrence),
                    allSets = allSets,
                    days = days,
                    splitExercises = splitExercises,
                    cycleStartDate = cycleStartDate,
                    skippedExerciseIds = target.skippedExerciseIds,
                ),
            )
        }

        SplitSwitchTarget.Clear -> DesiredSplitSeed(
            day = DayPendingWrite(date = request.date),
            sets = emptyList(),
        )

        is SplitSwitchTarget.Proposal -> DesiredSplitSeed(
            day = proposalStartDay(
                smartSuggest = repos.learnedSplits.model.value.smartSuggest,
                date = request.date,
                slotId = target.slotId,
                slots = slots,
                days = days,
                cycleStartDate = cycleStartDate,
            ),
            sets = SplitSeedBuilder.forExplicitExercises(
                date = request.date,
                orderedExerciseIds = target.exerciseIds,
                historyBeforeDate = allSets,
                bodyweightIds = exerciseRepository.exercises.value.filter { it.isBodyweight }.mapTo(hashSetOf()) { it.id },
            ),
        )
    }

    private fun buildSavedSplitSeed(
        date: String,
        slotId: Long,
        occurrence: Int,
        allSets: List<WorkoutSet>,
        days: List<WorkoutDay>,
        splitExercises: List<SplitExercise>,
        cycleStartDate: String,
        skippedExerciseIds: Set<Long> = emptySet(),
    ): List<WorkoutSet> =
        when (val source = savedSplitSource(date, slotId, occurrence, allSets, days, splitExercises, cycleStartDate)) {
            is SavedSplitSource.Explicit -> SplitSeedBuilder.forExplicitExercises(
                date = date,
                orderedExerciseIds = source.exerciseIds.filterNot { it in skippedExerciseIds },
                historyBeforeDate = allSets,
            )
            is SavedSplitSource.PriorOccurrence -> SplitSeedBuilder.forPriorOccurrence(
                date = date,
                sourceRows = source.rows.filterNot { it.exerciseId in skippedExerciseIds },
            )
            SavedSplitSource.None -> emptyList()
        }

    /** A saved split seeds from its saved lifts, else from the rows of its previous occurrence. */
    private fun savedSplitSource(
        date: String,
        slotId: Long,
        occurrence: Int,
        allSets: List<WorkoutSet>,
        days: List<WorkoutDay>,
        splitExercises: List<SplitExercise>,
        cycleStartDate: String,
    ): SavedSplitSource {
        val orderedExerciseIds = splitExercises
            .filter { it.splitId == slotId }
            .sortedBy { it.orderIndex }
            .map { it.exerciseId }
        if (orderedExerciseIds.isNotEmpty()) return SavedSplitSource.Explicit(orderedExerciseIds)

        val previousDay = DayQueries.previousOccurrenceForSlot(
            days = days,
            slotId = slotId,
            occurrence = occurrence - 1,
            beforeDate = date,
            notBeforeDate = cycleStartDate,
        ) ?: return SavedSplitSource.None
        return SavedSplitSource.PriorOccurrence(SetQueries.forDate(allSets, previousDay.date))
    }

    private fun splitSwitchTargetLabel(
        target: SplitSwitchTarget,
        slots: List<CycleSlot>,
    ): String = when (target) {
        is SplitSwitchTarget.Saved -> slots.firstOrNull { it.id == target.slotId }?.name ?: "Workout"
        is SplitSwitchTarget.Proposal -> target.label
        SplitSwitchTarget.Clear -> "Clear workout"
    }

    private suspend fun cleanupAfterSplitSwitch(
        context: SplitSwitchRequestContext,
        planned: PlannedSplitSwitch,
    ) {
        val outgoingSetIds = planned.outgoingSetIds
        if (activeRest?.completedSetId?.let { it in outgoingSetIds } == true) setActiveRest(null)
        _assistSuggestions.update { current -> current.filterKeys { it !in outgoingSetIds } }
        _inputWarnings.update { current -> current.filterKeys { it !in outgoingSetIds } }
        _smartAdjustments.update { current ->
            current.filter { (setId, adjustment) ->
                setId !in outgoingSetIds && adjustment.sourceSetId !in outgoingSetIds
            }
        }
        _recommendationStates.update { current ->
            current.filterKeys { it !in planned.outgoingExerciseIds }
        }

        val stillShowingRequestDate = context.dateSelectionGeneration == dateSelectionGeneration
        if (stillShowingRequestDate) {
            val refreshedSets = SetQueries.forDate(setRepository.sets.value, context.request.date)
            publishSessionSets(context.request.date, refreshedSets)
            try {
                updateHistoricalTopSets(context.request.date, refreshedSets)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _exerciseHistorySnapshot.value = ExerciseHistorySnapshot(date = context.request.date)
            }
            try {
                classifySessionExercises(refreshedSets)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: NotCommittedException) {
                _uiMessage.value = visibleCatalogFailure(failure, "Couldn't change the muscle group.")
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    /** The date's sets, card order/modes and workout day, exactly as they are right now — for Undo. */
    private suspend fun captureDaySnapshot(date: String): DaySnapshot {
        val sets = SetQueries.forDate(setRepository.sets.value, date)
        val cards = setRepository.cards.value.filter { it.date == date }.sortedWith(compareBy({ it.position }, { it.id }))
        val order = (cards.map { it.exerciseId } + sets.map { it.exerciseId }).distinct()
        return DaySnapshot(
            date = date,
            sets = sets,
            cardOrder = order,
            cardModes = cards.filter { it.displayMode != WorkoutCardDisplayMode.EXPANDED }.associate { it.exerciseId to it.displayMode },
            day = workoutRepository.observeWorkoutDay(date).first(),
        )
    }

    private fun currentSignature(date: String) =
        dayContentSignature(date, setRepository.sets.value, setRepository.cards.value)

    private fun publishDayUndo(date: String, copy: DayUndoCopy, restore: DayUndoRestore) {
        dayUndoGeneration = dataGeneration()
        _dayUndo.value = DayUndoState.Offered(DayUndoOffer(++nextDayUndoId, date, copy, restore, currentSignature(date)))
    }

    fun dismissDayUndo(offerId: Long) {
        if (_dayUndo.value?.offer?.id == offerId) _dayUndo.value = null
    }

    /** Builds the pill copy for a split start/load/clear and publishes it as the day's Undo. */
    private suspend fun offerSplitSwitchUndo(request: SplitSwitchRequest, before: DaySnapshot) {
        val slots = workoutRepository.getCycleSlots()
        val previousSlotId = before.day?.cycleSlotId
        val previousName = previousSlotId?.let { id ->
            slots.firstOrNull { it.id == id }?.name ?: workoutRepository.getSlotByIdIncludingArchived(id)?.name
        }
        val after = SetQueries.forDate(setRepository.sets.value, request.date)
        val checked = before.sets.count { it.completed }
        val copy = when (val target = request.target) {
            is SplitSwitchTarget.Saved -> {
                val name = slots.firstOrNull { it.id == target.slotId }?.name ?: "Workout"
                if (before.sets.isEmpty()) {
                    startedCopy(name, target.slotId, after.map { it.exerciseId }.distinct().size, after.size)
                } else {
                    loadedCopy(name, target.slotId, previousName, before.sets.size, checked)
                }
            }
            is SplitSwitchTarget.Proposal -> if (before.sets.isEmpty()) {
                startedCopy(target.label, target.slotId, after.map { it.exerciseId }.distinct().size, after.size)
            } else {
                loadedCopy(target.label, target.slotId, previousName, before.sets.size, checked)
            }
            SplitSwitchTarget.Clear -> clearedCopy(previousName, previousSlotId, before.sets.size, checked)
        }
        publishDayUndo(request.date, copy, DayUndoRestore.RestoreDay(before))
    }

    /** The set-id part of [cleanupAfterSplitSwitch], for the sets a restore just replaced. */
    private suspend fun cleanupAfterUndo(date: String, outgoingSetIds: Set<Long>) {
        if (activeRest?.completedSetId in outgoingSetIds) setActiveRest(null)
        _assistSuggestions.update { current -> current.filterKeys { it !in outgoingSetIds } }
        _inputWarnings.update { current -> current.filterKeys { it !in outgoingSetIds } }
        _smartAdjustments.update { current ->
            current.filter { (setId, adjustment) -> setId !in outgoingSetIds && adjustment.sourceSetId !in outgoingSetIds }
        }
        if (currentDate.value != date) return
        val refreshedSets = SetQueries.forDate(setRepository.sets.value, date)
        publishSessionSets(date, refreshedSets)
        try {
            updateHistoricalTopSets(date, refreshedSets)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            _exerciseHistorySnapshot.value = ExerciseHistorySnapshot(date = date)
        }
    }

    /** Confirms Undo only after its local restore commits. */
    fun undoDayChange(offerId: Long) {
        flushPendingSetCommits()
        val offer = (_dayUndo.value as? DayUndoState.Offered)?.offer?.takeIf { it.id == offerId } ?: return
        val expectedGeneration = dayUndoGeneration
        if (DemoGuard.blockIfDemo()) return
        _dayUndo.value = DayUndoState.Restoring(offer)
        viewModelScope.launch {
            withDataGeneration(expectedGeneration) {
            val precondition = {
                if (currentSignature(offer.date) != offer.afterSignature) throw DayUndoStale()
            }
            try {
                val outgoing = SetQueries.forDate(setRepository.sets.value, offer.date).map { it.id }.toSet()
                when (val restore = offer.restore) {
                    is DayUndoRestore.RestoreDay -> {
                        val snapshot = restore.snapshot
                        val ordered = snapshot.sets.sortedWith(compareBy(
                            { snapshot.cardOrder.indexOf(it.exerciseId).let { index -> if (index < 0) Int.MAX_VALUE else index } },
                            { it.setNumber },
                        )).map { it.copy(id = 0, syncId = "", rowRevision = 0, cardId = "") }
                        val day = snapshot.day?.toPendingWrite() ?: DayPendingWrite(snapshot.date)
                        repos.splitReplacement.restore(snapshot.date, day, ordered, snapshot.cardModes, validate = precondition).getOrThrow()
                    }
                    is DayUndoRestore.DeleteAdded -> {
                        val ids = SetQueries.forDate(setRepository.sets.value, offer.date)
                            .filter { it.syncId in restore.syncIds }.map { it.id }
                        setRepository.deleteSets(ids, precondition).orThrow()
                    }
                }
                cleanupAfterUndo(offer.date, outgoing)
                if (_dayUndo.value == DayUndoState.Restoring(offer)) {
                    _dayUndo.value = if (currentDate.value == offer.date) DayUndoState.Restored(offer) else null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: DayUndoStale) {
                if (_dayUndo.value == DayUndoState.Restoring(offer)) _dayUndo.value = null
            } catch (_: Throwable) {
                if (_dayUndo.value == DayUndoState.Restoring(offer)) {
                    _dayUndo.value = if (currentDate.value == offer.date && currentSignature(offer.date) == offer.afterSignature) {
                        DayUndoState.Offered(offer)
                    } else {
                        null
                    }
                }
                _uiMessage.value = "Couldn't undo. Try again."
            }
            }
        }
    }

    /** The batch form of [deleteSet]: one local edit, one history refresh, one repository call. */
    fun deleteSets(ids: List<Long>, expectedGeneration: Long = dataGeneration(), onResult: (LocalCommit<Unit>) -> Unit = {}) {
        if (expectedGeneration != dataGeneration()) return
        flushPendingSetCommits()
        val targets = ids.filter { it != 0L }.distinct()
        if (targets.isEmpty() || DemoGuard.blockIfDemo()) {
            onResult(LocalCommit.Committed(Unit))
            return
        }
        val date = currentDate.value
        viewModelScope.launch {
            withDataGeneration(expectedGeneration) {
            when (val committed = setRepository.deleteSets(targets)) {
                is LocalCommit.Committed -> {
                    onResult(committed)
                    cleanupAfterUndo(date, targets.toSet())
                }
                is LocalCommit.NotCommitted -> {
                    onResult(committed)
                    reportSetNotSaved(committed.reason)
                }
            }
            }
        }
    }

    fun consumeUiMessage() {
        _uiMessage.value = null
    }

    fun addSet(exerciseId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        val generation = dataGeneration()
        viewModelScope.launch {
            val date = currentDate.value
            val dateGeneration = dateSelectionGeneration
            val saved = withDataGeneration(generation) {
                setRepository.addSet(date, exerciseRepository.canonicalId(exerciseId))
            } ?: return@launch
            val newSet = when (saved) {
                is LocalCommit.Committed -> saved.value
                is LocalCommit.NotCommitted -> { reportSetNotSaved(saved.reason); return@launch }
            }
            if (currentDate.value == date && dateSelectionGeneration == dateGeneration) {
                val updated = updateSessionSets(date) { appendSessionSets(it, listOf(newSet)) }
                if (updated != null) updateHistoricalTopSets(date, updated)
            }
        }
    }

    fun addSetFrom(exerciseId: Long, sourceSetId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        val generation = dataGeneration()
        viewModelScope.launch {
            val date = currentDate.value
            val dateGeneration = dateSelectionGeneration
            val canonicalExerciseId = exerciseRepository.canonicalId(exerciseId)
            // The swiped row can still be carrying a temp id that the repository has since
            // remapped, so fall back to the repository before giving up on the copy — losing
            // the source here silently appends a set seeded from the previous session instead.
            val source = _sessionSets.value.find {
                it.id == sourceSetId &&
                    it.date == date &&
                    exerciseRepository.canonicalId(it.exerciseId) == canonicalExerciseId
            } ?: setRepository.getById(sourceSetId)?.takeIf {
                exerciseRepository.canonicalId(it.exerciseId) == canonicalExerciseId && it.date == date
            }
            val saved = withDataGeneration(generation) {
                setRepository.addSet(date, canonicalExerciseId, copyFrom = source)
            } ?: return@launch
            val newSet = when (saved) {
                is LocalCommit.Committed -> saved.value
                is LocalCommit.NotCommitted -> { reportSetNotSaved(saved.reason); return@launch }
            }
            if (currentDate.value == date && dateSelectionGeneration == dateGeneration) {
                val updated = updateSessionSets(date) { appendSessionSets(it, listOf(newSet)) }
                if (updated != null) updateHistoricalTopSets(date, updated)
            }
        }
    }

    fun updateWeight(setId: Long, input: String) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearAssistState(setId)
        clearSmartAdjustment(setId)
        updateSetLocal(setId) { set ->
            val value = WeightLbs.parseInputToStorage(input)
            set.copy(weightLbs = normalizedSetWeight(set, value))
        }
        _sessionSets.value.find { it.id == setId }?.let { source ->
            updateSmartAdjustmentsFrom(
                source = source,
                beforeSourceEdit = before,
                propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
            )
        }
    }

    fun adjustWeight(setId: Long, delta: Int) {
        if (DemoGuard.blockIfDemo()) return
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearAssistState(setId)
        clearSmartAdjustment(setId)
        stepSetLocal(setId, before) { set ->
            val current = set.weightLbs ?: 0
            val next = (current + delta).coerceAtLeast(0)
            set.copy(weightLbs = normalizedSetWeight(set, next))
        }
    }

    fun updateReps(setId: Long, input: String) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearAssistState(setId)
        clearSmartAdjustment(setId)
        updateSetLocal(setId) { set ->
            val value = input.filter { it.isDigit() }.toIntOrNull()
            set.copy(reps = value)
        }
        _sessionSets.value.find { it.id == setId }?.let { source ->
            updateSmartAdjustmentsFrom(
                source = source,
                beforeSourceEdit = before,
                propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
            )
        }
    }

    fun adjustReps(setId: Long, delta: Int) {
        if (DemoGuard.blockIfDemo()) return
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearAssistState(setId)
        clearSmartAdjustment(setId)
        stepSetLocal(setId, before) { set ->
            val current = set.reps ?: 0
            set.copy(reps = (current + delta).coerceAtLeast(0))
        }
    }

    fun toggleWarmup(setId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        clearAssistState(setId)
        clearSmartAdjustment(setId)
        clearSmartAdjustmentsFromSource(setId)
        updateSetLocal(setId) { it.copy(isWarmup = !it.isWarmup) }
    }

    fun toggleBodyweight(setId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearAssistState(setId)
        clearSmartAdjustment(setId)
        clearSmartAdjustmentsFromSource(setId)
        updateSetLocal(setId) { set ->
            val turningOn = !set.isBodyweight
            val next = set.copy(
                isBodyweight = turningOn,
                weightLbs = if (turningOn) normalizedBodyweightLoad(set.weightLbs) else set.weightLbs,
            )
            next.copy(weightLbs = normalizedSetWeight(next, next.weightLbs))
        }
        _sessionSets.value.find { it.id == setId }?.let { source ->
            updateSmartAdjustmentsFrom(
                source = source,
                beforeSourceEdit = before,
                propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
            )
        }
    }

    fun deleteSet(setId: Long) { deleteSets(listOf(setId)) }

    fun deleteExercise(exerciseId: Long) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            runCatching { exerciseRepository.deleteExercise(exerciseId) }
                .onFailure {
                    if (it is CancellationException) throw it
                    _uiMessage.value = deleteExerciseFailureText(it) ?: visibleCatalogFailure(it, "Couldn't delete the exercise.")
                }
        }
    }

    fun updateExerciseName(exerciseId: Long, newName: String) {
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            try {
                when (val result = exerciseRepository.updateName(exerciseId, newName)) {
                    is ExerciseRenameResult.Success -> Unit
                    is ExerciseRenameResult.NameCollision -> {
                        val current = exerciseRepository.getById(exerciseId) ?: return@launch
                        if (canOfferExerciseResolution()) {
                            _pendingExerciseResolution.value = PendingExerciseResolutionUi(
                                requestId = ++nextExerciseResolutionRequestId,
                                selectedExerciseId = current.id,
                                selectedName = current.name,
                                typedName = newName,
                                candidateExerciseId = result.existing.id,
                                candidateName = result.existing.name,
                            )
                        } else {
                            _uiMessage.value = "That exercise name is already in use."
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiMessage.value = "Couldn't rename the exercise."
            }
        }
    }

    fun updateExerciseMuscleGroup(exerciseId: Long, muscleGroup: String) {
        val normalized = normalizeBodyPartGroup(muscleGroup) ?: return
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            runCatching { exerciseRepository.updateMuscleGroups(exerciseId, normalized) }
                .onFailure {
                    if (it is CancellationException) throw it
                    _uiMessage.value = "Couldn't change the muscle group."
                }
        }
    }

    fun toggleCompleted(setId: Long, onCommitted: () -> Unit = {}) {
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        beginSetEntry(setId)
        val set = _sessionSets.value.find { it.id == setId } ?: return
        val newCompleted = !set.completed
        // TRY is rendered separately from the stored primary number. LOG confirms that stored
        // number; it is not an implicit accept action for either advisory channel *on this row*.
        if (newCompleted) {
            clearSmartAdjustment(setId)
            clearAssistState(setId)
        }

        updateSetLocal(setId, onCommitted = {
            if (newCompleted) {
                commitSmartAdjustmentsFrom(setId)
                val hasNextSameExercise = _sessionSets.value.any {
                    it.exerciseId == set.exerciseId && it.id != set.id && !it.completed
                }
                setActiveRest(if (hasNextSameExercise) ActiveRest(
                    exerciseId = set.exerciseId,
                    completedSetId = set.id,
                    startedAtMillis = restNowMillis(),
                ) else null)
            } else if (activeRest?.completedSetId == set.id) {
                setActiveRest(null)
            }
            onCommitted()
        }) { it.copy(completed = newCompleted) }
    }

    fun focusSetInput(setId: Long) {
        clearAssistState(setId)
        beginSetEntry(setId)
    }

    fun beginSetEntry(setId: Long) {
        val rest = activeRest ?: return
        val targetSet = _sessionSets.value.find { it.id == setId } ?: return
        if (targetSet.completed || targetSet.id == rest.completedSetId || targetSet.exerciseId != rest.exerciseId) return

        val elapsedSeconds = ((restNowMillis() - rest.startedAtMillis) / 1000)
            .toInt()
            .coerceAtLeast(1)
        setActiveRest(null)
        updateSetLocal(setId) { it.copy(restTimeSeconds = elapsedSeconds) }
    }

    fun finishExercise(exerciseId: Long) {
        // A pending step's hint pass would otherwise recreate the hints Done is about to clear.
        flushPendingSetCommits()
        if (DemoGuard.blockIfDemo()) return
        clearAdvisoryStateForExercise(exerciseId)
        if (activeRest?.exerciseId == exerciseId) {
            setActiveRest(null)
        }
        // Done is a deliberate press: the lift's open working sets are logged exactly as they stand,
        // with no hint carried onto them and no rest started. A warm-up left open was skipped and
        // stays unlogged; logging it would record work that never happened.
        _sessionSets.value
            .filter { it.exerciseId == exerciseId && !it.completed && !it.isWarmup }
            .forEach { open -> updateSetLocal(open.id) { it.copy(completed = true) } }
    }

    fun updateCardDisplayMode(cardId: String, displayMode: WorkoutCardDisplayMode) {
        flushPendingSetCommits()
        if (cardId.isBlank()) return
        val generation = dataGeneration()
        viewModelScope.launch {
            runCatching {
                val saved = withDataGeneration(generation) {
                    setRepository.updateCardDisplayMode(cardId, displayMode)
                } ?: return@launch
                saved.orThrow()
            }.onFailure {
                _uiMessage.value = "Couldn't save the card. Try again."
            }
        }
    }

    private fun updateSmartAdjustmentsFrom(
        source: WorkoutSet,
        beforeSourceEdit: WorkoutSet?,
        propagationPolicy: SmartSetPropagationPolicy,
    ) {
        _smartAdjustments.update { current ->
            buildSmartAdjustmentsFromSource(
                current = current,
                sessionSets = _sessionSets.value,
                source = source,
                beforeSourceEdit = beforeSourceEdit,
                propagationPolicy = propagationPolicy,
            )
        }
    }

    /**
     * Logging a set is the moment its carry-over becomes real.
     *
     * Last session was 135 × 8/5/3 and today's rows open as a copy of it. Scrubbing set 1 to
     * 140 × 7 only *hints* 140 × 4 and 140 × 2 at sets 2 and 3 (a scrub must never make another
     * row look edited, and the tape fires per tick, so nothing is written for them yet). Once set 1
     * is checked, those hints are the numbers the remaining sets should open with (Ayman,
     * 2026-09-21: "the current session becomes suggestions after the first set is set to 140lb
     * 7r and the suggestions are the differences"), so they are written into the sets that are
     * still open, as the same kind of unchecked seed the whole-session copy already is.
     *
     * A later set the user has already edited by hand carries no hint (its own scrub cleared it)
     * and keeps its numbers; a set that is already checked is never rewritten.
     */
    private fun commitSmartAdjustmentsFrom(sourceSetId: Long) {
        val outgoing = _smartAdjustments.value.values.filter { it.sourceSetId == sourceSetId }
        if (outgoing.isEmpty()) return
        val rows = _sessionSets.value.associateBy { it.id }
        outgoing.forEach { adjustment ->
            val target = rows[adjustment.setId] ?: return@forEach
            if (target.completed || target.isWarmup) return@forEach
            updateSetLocal(target.id) { acceptSmartAdjustment(it, adjustment) }
        }
        clearSmartAdjustmentsFromSource(sourceSetId)
    }

    private fun clearSmartAdjustment(setId: Long) {
        if (setId in _smartAdjustments.value) {
            _smartAdjustments.update { it - setId }
        }
    }

    private fun clearSmartAdjustmentsFromSource(sourceSetId: Long) {
        if (_smartAdjustments.value.values.any { it.sourceSetId == sourceSetId }) {
            _smartAdjustments.update { current ->
                current.filterValues { it.sourceSetId != sourceSetId }
            }
        }
    }

    private fun clearAdvisoryStateForExercise(exerciseId: Long) {
        val setIds = _sessionSets.value
            .filter { it.exerciseId == exerciseId }
            .mapTo(mutableSetOf()) { it.id }
        if (setIds.isEmpty()) return
        _smartAdjustments.update { current ->
            current.filter { (setId, adjustment) ->
                setId !in setIds && adjustment.sourceSetId !in setIds
            }
        }
        _assistSuggestions.update { current -> current.filterKeys { it !in setIds } }
        _inputWarnings.update { current -> current.filterKeys { it !in setIds } }
    }

    /**
     * A tape step (one notch of [adjustWeight] / [adjustReps], many per second in a flick or a
     * coast). The row on screen moves at once; the repository write, the history refresh and the
     * later sets' hints wait until no step has arrived for [STEP_COMMIT_DELAY_MS], so a scrub
     * publishes the repository's set list once instead of once per notch.
     */
    private fun stepSetLocal(setId: Long, before: WorkoutSet?, transform: (WorkoutSet) -> WorkoutSet) {
        var updatedSet: WorkoutSet? = null
        editSessionSets { list ->
            list.map { set ->
                if (set.id == setId) transform(set).also { updatedSet = it } else set
            }
        }
        val row = updatedSet ?: return
        if (row.id == 0L) {
            // Nothing to write for an id-less row; keep the old immediate refresh and hints.
            commitStep(SetCommit(row, before), write = false)
            return
        }
        val key = stepCommits.stage(row, before, dataGeneration())
        stepCommitJobs.remove(key)?.cancel()
        stepCommitJobs[key] = viewModelScope.launch(stepCommitContext) {
            delay(STEP_COMMIT_DELAY_MS)
            // A remap may have moved the commit (and this timer) to a new key meanwhile.
            val settledKey = stepCommits.resolve(key)
            stepCommitJobs.remove(settledKey)
            stepCommits.take(settledKey)?.let { commitStep(it) }
        }
    }

    private fun commitStep(commit: SetCommit, write: Boolean = true) {
        val key = SetCommitBuffer.keyOf(commit.row)
        fun afterCommit() {
            refreshHistoricalTopSets()
            _sessionSets.value.find { SetCommitBuffer.keyOf(it) == key }?.let { source ->
                updateSmartAdjustmentsFrom(source, commit.beforeFirstStep, SmartSetPropagationPolicy.SUGGEST_LATER_SETS)
            }
        }
        if (!write) { afterCommit(); return }
        val current = setRepository.sets.value.firstOrNull { SetCommitBuffer.keyOf(it) == key }
            ?: _sessionSets.value.firstOrNull { SetCommitBuffer.keyOf(it) == key } ?: return
        // Only the fields the steps moved are this person's edit; the base puts those back to their pre-scrub values.
        val row = commit.stepsOnto(current)
        val observed = commit.baseOn(current)
        stepCommitsInFlight++
        commitScope.launch {
            try {
                val saved = withDataGeneration(commit.generation) {
                    withContext(NonCancellable) { setRepository.updateSet(row, observed = observed) }
                }
                when (saved) {
                    null -> Unit
                    is LocalCommit.Committed -> {
                        clearUnsavedSetInput(row)
                        afterCommit()
                    }
                    is LocalCommit.NotCommitted -> {
                        restoreUnsavedSet(commit.beforeFirstStep ?: current, commit.row)
                        retainUnsavedSetInput(row, observed, saved.reason, generation = commit.generation)
                    }
                }
            } finally {
                stepCommitsInFlight--
            }
        }
    }

    /**
     * Moves pending steps to their row's new key when a repository emission remaps it: a legacy
     * insert's temp id becoming the server id (the row has no syncId to key on), or a blank syncId
     * being filled in. [previous] are the rows on screen, [current] the repository's rows for the day.
     */
    private fun rekeyPendingSetCommits(previous: List<WorkoutSet>, current: List<WorkoutSet>) {
        if (stepCommits.isEmpty) return
        val previousIds = previous.mapTo(HashSet()) { it.id }
        previous.forEach { old ->
            val oldKey = SetCommitBuffer.keyOf(old)
            if (!stepCommits.has(oldKey)) return@forEach
            val counterpart = current.firstOrNull { old.syncId.isNotBlank() && it.syncId == old.syncId }
                ?: current.firstOrNull { it.id == old.id }
                // A remapped legacy row keeps its natural key; it is the one row there that is new.
                ?: current.singleOrNull {
                    old.syncId.isBlank() &&
                        it.id !in previousIds &&
                        it.date == old.date &&
                        it.exerciseId == old.exerciseId &&
                        it.setNumber == old.setNumber
                }
                ?: return@forEach
            val newKey = SetCommitBuffer.keyOf(counterpart)
            if (stepCommits.rekey(oldKey, newKey)) {
                stepCommitJobs.remove(oldKey)?.let { stepCommitJobs[newKey] = it }
            }
        }
    }

    /**
     * Commits every pending tape step now, in step order. Runs before any other write or any read
     * of the repository's rows, so nothing can observe (or overwrite) a set whose last steps have
     * not been written yet. Also runs when the collector stops, the PWA is hidden and on clear.
     */
    internal fun flushPendingSetCommits() {
        if (stepCommits.isEmpty) return
        stepCommitJobs.values.forEach { it.cancel() }
        stepCommitJobs.clear()
        val generation = dataGeneration()
        stepCommits.flush().forEach { commit ->
            if (commit.generation == generation) commitStep(commit)
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(flushWhenHidden)
        // The writes go out on commitScope, which outlives viewModelScope.
        flushPendingSetCommits()
        super.onCleared()
    }

    private fun updateSetLocal(
        setId: Long,
        onCommitted: (WorkoutSet) -> Unit = {},
        transform: (WorkoutSet) -> WorkoutSet,
    ) {
        flushPendingSetCommits()
        val before = _sessionSets.value.firstOrNull { it.id == setId } ?: return
        val attempted = transform(before)
        editSessionSets { list -> list.map { if (it.id == setId) attempted else it } }
        if (attempted.id == 0L) return
        val generation = dataGeneration()
        viewModelScope.launch {
            val saved = withDataGeneration(generation) {
                setRepository.updateSet(attempted, observed = before)
            }
            when (saved) {
                null -> Unit
                is LocalCommit.Committed -> {
                    clearUnsavedSetInput(attempted)
                    refreshHistoricalTopSets()
                    if (currentDate.value == attempted.date) onCommitted(saved.value)
                }
                is LocalCommit.NotCommitted -> {
                    restoreUnsavedSet(before, attempted)
                    retainUnsavedSetInput(attempted, before, saved.reason, onCommitted, generation)
                }
            }
        }
    }

    private fun retainUnsavedSetInput(
        attempted: WorkoutSet,
        observed: WorkoutSet,
        reason: NotCommittedReason,
        afterSave: (WorkoutSet) -> Unit = {},
        generation: Long = dataGeneration(),
    ) {
        _unsavedSetInputs.update {
            it + (SetCommitBuffer.keyOf(attempted) to UnsavedSetInput(attempted, observed, reason, afterSave, generation))
        }
    }

    private fun clearUnsavedSetInput(row: WorkoutSet) {
        val key = SetCommitBuffer.keyOf(row)
        if (key in _unsavedSetInputs.value) _unsavedSetInputs.update { it - key }
    }

    /**
     * Try a refused edit again against the row it was computed from. Only this exact input is
     * cleared or updated, so a newer refused edit of the same set made meanwhile stays retained.
     */
    fun retryUnsavedSet(key: String) {
        val input = _unsavedSetInputs.value[key] ?: return
        flushPendingSetCommits()
        viewModelScope.launch {
            val saved = withDataGeneration(input.generation) {
                setRepository.updateSet(input.attempted, observed = input.observed)
            }
            when (saved) {
                null -> _unsavedSetInputs.update { if (it[key] === input) it - key else it }
                is LocalCommit.Committed -> {
                    _unsavedSetInputs.update { if (it[key] === input) it - key else it }
                    refreshHistoricalTopSets()
                    if (currentDate.value == input.attempted.date) input.afterSave(saved.value)
                }
                is LocalCommit.NotCommitted -> {
                    _unsavedSetInputs.update { if (it[key] === input) it + (key to input.copy(reason = saved.reason)) else it }
                }
            }
        }
    }

    /** Drop the unsaved value. The saved row was never changed, so nothing else moves. */
    fun discardUnsavedSet(key: String) {
        _unsavedSetInputs.update { it - key }
    }

    private fun restoreUnsavedSet(before: WorkoutSet, attempted: WorkoutSet) {
        val key = SetCommitBuffer.keyOf(attempted)
        editSessionSets { list ->
            list.map { current ->
            if (SetCommitBuffer.keyOf(current) == key &&
                current.copy(
                    id = attempted.id, syncId = attempted.syncId, rowRevision = attempted.rowRevision, cardId = attempted.cardId,
                ) == attempted) {
                before.copy(id = current.id, syncId = current.syncId, rowRevision = current.rowRevision, cardId = current.cardId)
            } else {
                current
            }
        }
        }
        clearSmartAdjustmentsFromSource(attempted.id)
    }

    private val _unsavedSetInputs = MutableStateFlow<Map<String, UnsavedSetInput>>(emptyMap())

    /** Edits storage refused, by set key. They never enter the session rows or completed-work numbers. */
    val unsavedSetInputs: StateFlow<Map<String, UnsavedSetInput>> = _unsavedSetInputs.asStateFlow()

    private fun reportSetNotSaved(reason: NotCommittedReason) {
        SyncErrors.report(UserVisibleSyncException("Set not saved · ${catalogRefusalText(reason)}"))
    }

    private fun refreshHistoricalTopSets() {
        // Read the date and its rows as one value. Taking the date from `currentDate` and the rows
        // from the session flow can pair a new day's header with the previous day's sets, which
        // then publishes that day's PB and top-set numbers against the wrong workout.
        val snapshot = _sessionSetSnapshot.value
        viewModelScope.launch {
            updateHistoricalTopSets(snapshot.date, snapshot.sets)
        }
    }

    fun cancelRestTimer() {
        setActiveRest(null)
    }

    fun requestRecommendations(exerciseId: Long) {
        if (_recommendationStates.value[exerciseId] == RecommendationState.Working) return
        if (_recommendationStates.value[exerciseId] == RecommendationState.Done) {
            val setIds = _sessionSets.value
                .filter { it.exerciseId == exerciseId }
                .map { it.id }
                .toSet()
            _assistSuggestions.update { current -> current.filterKeys { it !in setIds } }
            _recommendationStates.update { it + (exerciseId to RecommendationState.Idle) }
            return
        }

        _recommendationStates.update { it + (exerciseId to RecommendationState.Working) }
        viewModelScope.launch {
            // Suggestions are computed locally, so this resolves in single-digit millis. Without a
            // floor the Working state — and the card/rail animation that reports it — would flash
            // past before it could be read as anything.
            val startedAt = nowMillis()
            val selectedDate = currentDate.value
            val targetSets = _sessionSets.value.filter {
                it.exerciseId == exerciseId && !it.completed
            }
            val targetSetIds = targetSets.map { it.id }.toSet()
            _assistSuggestions.update { current -> current.filterKeys { it !in targetSetIds } }
            targetSets.forEach { set ->
                recommendationForSet(set, exerciseId, selectedDate)?.let { suggestion ->
                    _assistSuggestions.update { current -> current + (set.id to suggestion) }
                }
            }
            val elapsed = nowMillis() - startedAt
            if (elapsed < RECOMMENDATION_MIN_WORKING_MILLIS) {
                delay(RECOMMENDATION_MIN_WORKING_MILLIS - elapsed)
            }
            _recommendationStates.update { it + (exerciseId to RecommendationState.Done) }
        }
    }

    private suspend fun recommendationForSet(
        set: WorkoutSet,
        exerciseId: Long,
        selectedDate: String,
    ): com.ayman.ecolift.agent.assist.SetSuggestion? {
        // A rep target of 0 isn't a target — asking for a suggestion at 0 returns noise. Sets with
        // no reps yet are skipped, matching what the old per-set suggestion path did.
        val targetReps = set.reps?.takeIf { it > 0 } ?: return null
        val suggestion = runCatching {
            assist.suggestSetEntry(exerciseId, targetReps, beforeDate = selectedDate)
        }.getOrNull()
        return suggestion?.takeIf { it.hasData }
    }

    /** Clear an assist suggestion when the set is completed or otherwise dismissed. */
    fun onAssistSuggestionConsumed(setId: Long) { _assistSuggestions.update { it - setId } }

    /** Advisory sanity-check after a set value is committed. Never blocks. */
    fun checkSetInput(setId: Long, exerciseId: Long, weightStored: Int?, reps: Int?, isBodyweight: Boolean) {
        viewModelScope.launch {
            val verdict = runCatching {
                assist.checkInput(exerciseId, com.ayman.ecolift.agent.assist.SetDraft(weightStored, reps, isBodyweight))
            }.getOrNull()
            _inputWarnings.update { current ->
                if (verdict is com.ayman.ecolift.agent.assist.InputVerdict.Suspect) current + (setId to verdict.message)
                else current - setId
            }
        }
    }

    fun dismissInputWarning(setId: Long) { _inputWarnings.update { it - setId } }

    fun recordLogGestureHintShown(workoutDate: String) {
        _logGestureHintSessions.value = LogGestureHintSessionStore.record(workoutDate)
    }

    private fun clearAssistState(setId: Long) {
        _assistSuggestions.update { it - setId }
        _inputWarnings.update { it - setId }
    }

    private fun finishActiveWorkoutDate() {
        val leavingDate = currentDate.value
        setActiveRest(null)
        classifyFinishedSessionMuscles(leavingDate)
    }

    private fun setActiveRest(rest: ActiveRest?) {
        activeRest = rest
        restTickerJob?.cancel()
        restTickerJob = null

        if (rest == null) {
            _restStopwatch.value = null
            return
        }

        _restStopwatch.value = RestStopwatch(rest.exerciseId, elapsedRestSeconds(rest))
        restTickerJob = viewModelScope.launch {
            while (activeRest == rest) {
                _restStopwatch.value = RestStopwatch(rest.exerciseId, elapsedRestSeconds(rest))
                delay(1_000)
            }
        }
    }

    private fun elapsedRestSeconds(rest: ActiveRest): Int =
        ((restNowMillis() - rest.startedAtMillis) / 1_000)
            .toInt()
            .coerceAtLeast(0)

    private fun classifyFinishedSessionMuscles(date: String) {
        launchQuietClassification {
            val sets = setRepository.getSetsForDate(date)
            classifySessionExercises(sets)
        }
    }

    private suspend fun backfillKnownExerciseMuscles() {
        exerciseRepository.getAll()
            .filter { ExerciseMuscleClassifier.shouldClassify(it) }
            .mapNotNull(ExerciseMuscleClassifier::classifyLocally)
            .forEach { classification ->
                saveMuscleGroupsQuietly(classification.exerciseId, classification.muscleGroups)
            }
    }

    private suspend fun classifySessionExercises(sets: List<WorkoutSet>) {
        val exercises = exerciseRepository
            .getByIds(sets.map { it.exerciseId }.distinct())
            .filter { ExerciseMuscleClassifier.shouldClassify(it) }
        classifyAndPersist(exercises)
    }

    private suspend fun classifyLoggedExercise(exerciseId: Long) {
        val exercise = exerciseRepository.getById(exerciseId) ?: return
        if (!ExerciseMuscleClassifier.shouldClassify(exercise)) return

        val local = ExerciseMuscleClassifier.classifyLocally(exercise)
        if (local != null) {
            saveMuscleGroupsQuietly(local.exerciseId, local.muscleGroups)
            return
        }

        launchQuietClassification {
            classifyAndPersist(listOf(exercise))
        }
    }

    private fun catalogRefusalText(reason: NotCommittedReason): String = when (reason) {
        NotCommittedReason.QuotaExceeded -> "device storage full"
        NotCommittedReason.StorageUnavailable -> "device storage unavailable"
        NotCommittedReason.NeedsReconciliation -> "changed on another device"
    }

    private fun visibleCatalogFailure(failure: Throwable, fallback: String): String = when (failure) {
        is NotCommittedException -> fallback.trimEnd('.') + " · " + catalogRefusalText(failure.reason)
        is UserVisibleSyncException -> failure.message ?: fallback
        else -> fallback
    }

    private fun launchQuietClassification(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: NotCommittedException) {
            }
        }
    }

    private suspend fun saveMuscleGroupsQuietly(exerciseId: Long, muscleGroups: String) {
        try {
            exerciseRepository.updateMuscleGroups(exerciseId, muscleGroups)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }

    private suspend fun classifyAndPersist(exercises: List<Exercise>) {
        val targets = exercises.filter { ExerciseMuscleClassifier.shouldClassify(it) }
        if (targets.isEmpty()) return

        val classifications = try {
            classifyMuscles(targets)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return
        }
        classifications.forEach { classification ->
            saveMuscleGroupsQuietly(classification.exerciseId, classification.muscleGroups)
        }
    }

    private suspend fun createAndClassifyExercise(rawInput: String): Exercise {
        val exercise = exerciseRepository.getOrCreate(rawInput)
        if (selectableBodyPartParts(exercise.muscleGroups).isEmpty()) {
            suggestMuscleGroupFromKeywords(exercise.name)?.group?.let { group ->
                saveMuscleGroupsQuietly(exercise.id, group)
            }
        }
        return exerciseRepository.getById(exercise.id) ?: exercise
    }

    private suspend fun addExerciseSession(exerciseId: Long, context: ExerciseAddContext) {
        val generation = dataGeneration()
        val canonicalExerciseId = exerciseRepository.canonicalId(exerciseId)
        val result = withDataGeneration(generation) {
            setRepository.addExerciseSession(context.date, canonicalExerciseId)
        } ?: return
        if (result is LocalCommit.NotCommitted) reportSetNotSaved(result.reason)
        val newSets = result.orThrow()
        if (
            ownsExerciseAdd(context) &&
            currentDate.value == context.date &&
            dateSelectionGeneration == context.dateGeneration
        ) {
            val updated = updateSessionSets(context.date) { appendSessionSets(it, newSets) }
            if (updated != null) updateHistoricalTopSets(context.date, updated)
        }
        classifyLoggedExercise(canonicalExerciseId)
    }

    private fun buildUiState(
        inputs: UiInputs,
        snapshot: DbSnapshot,
        uiMessage: String?,
        pendingExerciseResolution: PendingExerciseResolutionUi?,
    ): LogUiState {
        // Date and repository flows can emit independently. Never render or edit old-day rows
        // beneath a new date header during the handoff frame.
        val snapshotMatchesDate = snapshot.date == inputs.date
        val currentSets = if (snapshotMatchesDate) snapshot.currentSets else emptyList()
        val selectedDay = snapshot.currentDay.takeIf { snapshotMatchesDate }
        val exerciseMap = snapshot.exercises.associateBy { it.id }
        val taggedExerciseCatalog = snapshot.exercises.filter {
            selectableBodyPartParts(it.muscleGroups).isNotEmpty()
        }

        val groupedExercises = orderLogExerciseGroups(
            // The add-exercise query belongs only to the predictive dropdown. It must never
            // filter, reorder, or otherwise change the workout already being logged.
            sets = workoutSetsForLog(currentSets, inputs.input),
            cards = snapshot.cards,
            date = inputs.date,
        ).mapNotNull { group ->
                val exercise = exerciseMap[group.exerciseId] ?: return@mapNotNull null
                val sets = group.sets.sortedBy(WorkoutSet::setNumber)
                val displayMode = group.card?.displayMode
                    ?: fallbackLogCardDisplayMode(
                        date = inputs.date,
                        today = WorkoutDates.today(),
                        sets = sets,
                    )

                val currentMax1RM = sets.mapNotNull {
                    WorkoutMetrics.estimatedOneRepMax(it, exercise.isBodyweight)
                }.maxOrNull()?.toInt() ?: 0

                LogExerciseUi(
                    cardId = group.card?.id ?: sets.firstOrNull()?.cardId.orEmpty(),
                    cardPosition = group.card?.position ?: group.fallbackPosition,
                    displayMode = displayMode,
                    exerciseId = exercise.id,
                    name = exercise.name,
                    muscleGroups = exercise.muscleGroups,
                    showTakenChip = snapshot.takenGroups.containsKey(exercise.id),
                    takenGroupKey = snapshot.takenGroups[exercise.id],
                    takenChipLabel = if (primaryGroupOf(exercise.muscleGroups)?.let {
                        com.ayman.ecolift.data.BodyPartRegionMapper.mapGroupToRegion(it).displayName == "Chest"
                    } == true) "Bench taken?" else "Machine taken?",
                    lastTopSet = inputs.historySnapshot.topSets[exercise.id]?.lastTopSet,
                    bestTopSet = inputs.historySnapshot.topSets[exercise.id]?.bestTopSet,
                    showBaselineHint = inputs.historySnapshot.topSets[exercise.id]
                        ?.let { it.lastTopSet == null && it.bestTopSet == null }
                        ?: false,
                    ghostSets = inputs.historySnapshot.topSets[exercise.id]?.lastSessionSets.orEmpty(),
                    lastSessionDate = inputs.historySnapshot.topSets[exercise.id]?.lastSessionDate,
                    e1rmHistory = inputs.historySnapshot.topSets[exercise.id]?.e1rmHistory.orEmpty(),
                    suggestedMuscleGroup = if (selectableBodyPartParts(exercise.muscleGroups).isEmpty()) {
                        suggestMuscleGroup(exercise.name, taggedExerciseCatalog)?.group
                    } else {
                        null
                    },
                    sets = sets.map { set ->
                        val smartAdjustment = inputs.smartAdjustments[set.id]
                        val assistSuggestion = inputs.assistSuggestions[set.id]
                        LogSetUi(
                            id = set.id,
                            syncId = set.syncId,
                            exerciseId = set.exerciseId,
                            setNumber = set.setNumber,
                            weightLbs = set.weightLbs,
                            reps = set.reps,
                            // SmartSetAdjustment always wins; assist fills the slot only when no
                            // SmartSetAdjustment is present (its niche is the opening set).
                            suggestedWeightLbs = smartAdjustment?.suggestedWeightLbs
                                ?: assistSuggestion?.weightStored,
                            suggestedReps = smartAdjustment?.suggestedReps
                                ?: assistSuggestion?.reps,
                            hasAssistSuggestion = assistSuggestion != null && smartAdjustment == null,
                            inputWarning = inputs.inputWarnings[set.id],
                            isBodyweight = set.isBodyweight,
                            completed = set.completed,
                            isWarmup = set.isWarmup,
                            restAfterSeconds = set.restTimeSeconds,
                        )
                    },
                    estimated1RM = currentMax1RM,
                    isNewPB = inputs.historySnapshot.pbs[exercise.id] ?: false,
                    recommendationState = inputs.recommendationStates[exercise.id]
                        ?: RecommendationState.Idle,
                    userBodyweightLbs = inputs.userBodyweightLbs,
                )
            }

        val expectedSlotId = snapshot.dueSlotId
        val cycleOptions = snapshot.slots.map { slot ->
            val nextOccurrence = snapshot.workoutDays
                .filter {
                    it.date < inputs.date &&
                        (snapshot.cycle.startDate == null || it.date >= snapshot.cycle.startDate) &&
                        it.resolvedCycleSlotId(snapshot.slots) == slot.id
                }
                .maxOfOrNull { it.cycleSlotOccurrence ?: 0 }
                ?.plus(1)
                ?: 1
            CycleSlotUi(
                slotId = slot.id,
                occurrence = nextOccurrence,
                label = slot.name,
                shortLabel = slot.name,
                isExpected = slot.id == expectedSlotId,
                isSelected = selectedDay.resolvedCycleSlotId(snapshot.slots) == slot.id
            )
        }

        return LogUiState(
            currentDate = inputs.date,
            currentDateLabel = WorkoutDates.formatHeader(inputs.date),
            cycleEnabled = snapshot.cycle.isActive,
            cycleSlot = buildCurrentCycleSlotUi(selectedDay, snapshot.slots),
            alternativeForDate = selectedDay?.alternativeForDate,
            cycleOptions = cycleOptions,
            exercises = groupedExercises,
            exerciseInput = inputs.input,
            predictiveExercises = inputs.suggestions,
            pendingExerciseResolution = pendingExerciseResolution,
            pendingReviews = snapshot.pendingReviews,
            reviewsExpanded = inputs.expanded,
            uiMessage = uiMessage,
        )
    }
}

internal fun ticketInclusionKey(date: String, groupKey: String): String = "$date|$groupKey"

/** Members on, faded and tail rows off. */
internal fun defaultTicketInclusion(program: ClipboardProgramUi): Set<Long> =
    program.rows.filter { it.includedByDefault }.mapTo(linkedSetOf()) { it.usualExerciseId }
        .ifEmpty { program.lifts.mapTo(linkedSetOf()) { it.exerciseId } }

/** What Start seeds: the included rows in ticket order ([chosen] null means the default). */
internal fun includedTicketLifts(program: ClipboardProgramUi, chosen: Set<Long>?, swaps: Map<Long, Long> = emptyMap()): List<Long> {
    val included = chosen ?: defaultTicketInclusion(program)
    val order = program.rows.map { it.usualExerciseId }.ifEmpty { program.lifts.map { it.exerciseId } }
    return order.filter { it in included }.map { swaps[it] ?: it }.distinct()
}

internal fun orderLogExerciseGroups(
    groups: Iterable<Map.Entry<Long, List<WorkoutSet>>>,
): List<Map.Entry<Long, List<WorkoutSet>>> = groups.toList()

internal data class OrderedLogExerciseGroup(
    val exerciseId: Long,
    val sets: List<WorkoutSet>,
    val card: WorkoutExerciseCard?,
    val fallbackPosition: Int,
)

/** Active cards follow their saved workout order so a newly added exercise comes after the
 * exercises already in the session. Compact cards move below every active row to keep the working
 * area clear without reversing either group.
 * Rows without card data remain visible between active and compact cards during
 * the legacy-queue compatibility window. */
internal fun orderLogExerciseGroups(
    sets: List<WorkoutSet>,
    cards: List<WorkoutExerciseCard>,
    date: String,
): List<OrderedLogExerciseGroup> {
    val cardsByExercise = cards.asSequence()
        .filter { it.date == date }
        .associateBy(WorkoutExerciseCard::exerciseId)

    return sets.groupBy(WorkoutSet::exerciseId)
        .entries
        .mapIndexed { fallbackPosition, (exerciseId, exerciseSets) ->
            OrderedLogExerciseGroup(
                exerciseId = exerciseId,
                sets = exerciseSets,
                card = cardsByExercise[exerciseId],
                fallbackPosition = fallbackPosition,
            )
        }
        .sortedWith(
            compareBy<OrderedLogExerciseGroup> { group ->
                when (group.card?.displayMode) {
                    WorkoutCardDisplayMode.EXPANDED -> 0
                    null -> 1
                    WorkoutCardDisplayMode.COMPACT -> 2
                }
            }.thenBy { it.card?.position ?: Int.MAX_VALUE }
                .thenBy { it.card?.createdAt.orEmpty() }
                .thenBy { it.card?.id.orEmpty() }
                .thenBy { it.fallbackPosition },
        )
}

private data class UiInputs(
    val date: String,
    val input: String,
    val suggestions: List<Exercise>,
    val expanded: Boolean,
    val smartAdjustments: Map<Long, SmartSetAdjustment>,
    val assistSuggestions: Map<Long, com.ayman.ecolift.agent.assist.SetSuggestion> = emptyMap(),
    val inputWarnings: Map<Long, String> = emptyMap(),
    val historySnapshot: ExerciseHistorySnapshot = ExerciseHistorySnapshot(),
    val recommendationStates: Map<Long, RecommendationState> = emptyMap(),
    val userBodyweightLbs: Int? = null,
)

private data class UiInputCore(
    val date: String,
    val input: String,
    val suggestions: List<Exercise>,
    val expanded: Boolean,
    val smartAdjustments: Map<Long, SmartSetAdjustment>,
)

private data class SplitSwitchRequestContext(
    val request: SplitSwitchRequest,
    val dateSelectionGeneration: Long,
    val dataGeneration: Long,
    val appends: List<com.ayman.ecolift.data.splits.SplitAlternative> = emptyList(),
)

private sealed interface SavedSplitSource {
    data class Explicit(val exerciseIds: List<Long>) : SavedSplitSource
    data class PriorOccurrence(val rows: List<WorkoutSet>) : SavedSplitSource
    data object None : SavedSplitSource
}

private data class PlannedSplitSwitch(
    val resolved: ResolvedSplitReplacement,
    val targetLabel: String,
    val outgoingSetIds: Set<Long>,
    val outgoingExerciseIds: Set<Long>,
)

private data class PendingSplitSwitchContext(
    val context: SplitSwitchRequestContext,
    val planned: PlannedSplitSwitch,
)

private data class DesiredSplitSeed(
    val day: DayPendingWrite,
    val sets: List<WorkoutSet>,
)

/**
 * The day a Proposal start stores. Smart Suggest on leaves the split for the engine. Off, a
 * proposal whose slot is in [slots] stores that slot the same way a saved switch does.
 */
internal fun proposalStartDay(
    smartSuggest: Boolean,
    date: String,
    slotId: Long?,
    slots: List<CycleSlot>,
    days: List<WorkoutDay>,
    cycleStartDate: String,
): DayPendingWrite {
    if (smartSuggest || slotId == null || slots.none { it.id == slotId }) {
        return DayPendingWrite(date = date, ensureOnly = true)
    }
    return assignedCycleDay(date, slotId, slots, days, cycleStartDate)
}

private fun assignedCycleDay(
    date: String,
    slotId: Long,
    slots: List<CycleSlot>,
    days: List<WorkoutDay>,
    cycleStartDate: String,
): DayPendingWrite {
    val slotIndex = slots.indexOfFirst { it.id == slotId }
    require(slotIndex >= 0) { "Split $slotId no longer exists" }
    val occurrence = (
        DayQueries.maxOccurrenceForSlotBefore(
            days = days,
            date = date,
            slotId = slotId,
            notBeforeDate = cycleStartDate,
        ) ?: 0
    ) + 1
    return DayPendingWrite(
        date = date,
        cycleSlotId = slotId,
        cycleSlotType = slotIndex,
        cycleSlotOccurrence = occurrence,
    )
}

internal data class SplitSwitchTransientStateSnapshot(
    val activeRestSetId: Long?,
    val assistSetIds: Set<Long>,
    val warningSetIds: Set<Long>,
    val adjustmentSetIds: Set<Long>,
    val adjustmentSourceSetIds: Set<Long>,
    val recommendationExerciseIds: Set<Long>,
)

/** What the rest tab shows: whose rest it is (for its colour) and how long it has run. */
data class RestStopwatch(val exerciseId: Long, val elapsedSeconds: Int)

private data class ActiveRest(
    val exerciseId: Long,
    val completedSetId: Long,
    val startedAtMillis: Long,
)

/**
 * Best Epley estimated 1RM per session, oldest first, capped to the last eight sessions so the
 * card's trend stays legible. Epley matches `rotationE1rm` on the card, so the live point and
 * the history it sits against are on the same scale.
 *
 * Bodyweight sets are normally excluded from e1RM (there is no external load to Epley), but when
 * [userBodyweightLbs] is known we treat bodyweight + added weight as the total load so pull-ups
 * etc. still get a trend. Without a bodyweight on file those sets are skipped, same as before.
 */
internal fun sessionE1rmHistory(history: List<WorkoutSet>, userBodyweightLbs: Int?): List<E1rmPointUi> =
    history.filter { set ->
        com.ayman.ecolift.data.WorkoutMetrics.estimatedOneRepMax(set) != null ||
            (
                set.isBodyweight &&
                    com.ayman.ecolift.data.WorkoutMetrics.isStrengthEligible(set) &&
                    userBodyweightLbs != null
                )
    }
        .groupBy { it.date }
        .entries
        .sortedBy { it.key }
        .map { (date, sets) ->
            E1rmPointUi(
                date = date,
                e1rm = sets.maxOf { set ->
                    val reps = (set.reps ?: 0).coerceAtLeast(0)
                    when {
                        reps == 0 -> 0f
                        set.isBodyweight -> {
                            val bodyweight = userBodyweightLbs ?: return@maxOf 0f
                            epleyEstimate((bodyweight + WeightLbs.toLbs(set.weightLbs)).toFloat(), reps)
                        }
                        else -> epleyEstimate(WeightLbs.toLbs(set.weightLbs).toFloat(), reps)
                    }
                },
            )
        }
        .filter { it.e1rm > 0f }
        .takeLast(8)

private data class ExerciseHistoryTopSets(
    val lastTopSet: String?,
    val bestTopSet: String?,
    // The same rows `lastTopSet` is derived from, kept whole. The log aims each
    // set at last session's set of the same index, which one summary string can't express.
    val lastSessionSets: List<GhostSetUi> = emptyList(),
    val lastSessionDate: String? = null,
    val e1rmHistory: List<E1rmPointUi> = emptyList(),
)

private data class ExerciseHistorySnapshot(
    val date: String = "",
    val topSets: Map<Long, ExerciseHistoryTopSets> = emptyMap(),
    val pbs: Map<Long, Boolean> = emptyMap(),
)

internal object LogGestureHintSessionStore {
    private var count = 0
    private val dates = mutableSetOf<String>()

    fun load(): Int = count

    fun record(workoutDate: String): Int {
        if (workoutDate.isBlank()) return load()
        if (dates.add(workoutDate)) count += 1
        return count
    }
}

/** Floor on the Recommend "Working" state so the thinking animation is legible. */
private const val RECOMMENDATION_MIN_WORKING_MILLIS = 650L

/** Quiet time after the last tape step before a scrub is written; see LogViewModel.stepSetLocal. */
internal const val STEP_COMMIT_DELAY_MS = 200L

internal fun buildCurrentCycleSlotUi(
    currentDay: WorkoutDay?,
    slots: List<CycleSlot>,
): CycleSlotUi? {
    val slotId = currentDay.resolvedCycleSlotId(slots) ?: return null
    val occurrence = currentDay?.cycleSlotOccurrence ?: 1
    val slot = slots.find { it.id == slotId } ?: return null
    return CycleSlotUi(
        slotId = slot.id,
        occurrence = occurrence,
        label = slot.name,
        shortLabel = slot.name,
    )
}

private fun topSetLabel(sets: List<WorkoutSet>): String {
    val topSet = sets.filter { it.isCheckedWorking }.maxWithOrNull(
        compareBy<WorkoutSet> { it.weightLbs ?: 0 }
            .thenBy { it.reps ?: 0 }
    ) ?: return "No sets"
    return "${formatCompactSetLoadLabel(topSet.isBodyweight, topSet.weightLbs)}×${topSet.reps ?: 0}"
}

private fun formatCompactSetLoadLabel(isBodyweight: Boolean, weightLbs: Int?): String {
    if (isBodyweight) {
        val addedLoad = normalizedBodyweightLoad(weightLbs)
        return if (addedLoad == null) "BW" else "BW+${WeightLbs.formatStored(addedLoad)}"
    }
    return weightLbs?.let(WeightLbs::formatStored) ?: "0"
}

private data class DatedWorkoutDay(val date: String, val day: WorkoutDay?)
internal data class DatedWorkoutSets(val date: String, val sets: List<WorkoutSet>)

private data class ScheduleSnapshot(
    val date: String,
    val workoutDays: List<WorkoutDay>,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
    val cards: List<WorkoutExerciseCard>,
)

private data class LibrarySnapshot(
    val exercises: List<Exercise>,
    val cycle: Cycle,
    val slots: List<CycleSlot>,
    val pendingReviews: List<PendingReview>,
    /** The learned model's due group's slot (spec 2026-09-28); replaces the rotation's "next". */
    val dueSlotId: Long?,
    val model: LearnedModel,
)

private data class DbSnapshot(
    val date: String,
    val exercises: List<Exercise>,
    val cycle: Cycle,
    val slots: List<CycleSlot>,
    val workoutDays: List<WorkoutDay>,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
    val cards: List<WorkoutExerciseCard>,
    val pendingReviews: List<PendingReview>,
    val dueSlotId: Long?,
    val takenGroups: Map<Long, String>,
)

/** A set edit that was not saved, with the row it was computed from. */
data class UnsavedSetInput(
    val attempted: WorkoutSet,
    val observed: WorkoutSet,
    val reason: NotCommittedReason,
    /** The completion step the original edit would have run once saved, such as moving to the next set. */
    val afterSave: (WorkoutSet) -> Unit = {},
    /** Generation captured when the edit was refused. A restore makes a retry of this input a no-op. */
    val generation: Long = 0L,
)

/** Present only while [LogViewModel.withDataGeneration] holds the generation lock. */
private class HeldGeneration(val expected: Long) : AbstractCoroutineContextElement(HeldGeneration) {
    companion object Key : CoroutineContext.Key<HeldGeneration>
}

/** A split's Start runs only on boards that are read, and never while its own board is saving. */
internal fun canStartSlot(state: SplitChoicesState, slotId: Long): Boolean =
    state is SplitChoicesState.Ready && slotId !in state.pendingSlots

/**
 * A delete is refused while its exercise still has writes on this device to sync, so it says to finish
 * syncing rather than blaming another device.
 */
internal fun deleteExerciseFailureText(failure: Throwable): String? =
    "Finish syncing before deleting this exercise.".takeIf {
        failure is NotCommittedException && failure.reason == NotCommittedReason.NeedsReconciliation
    }
