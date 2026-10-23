package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.supabase.AssistantChangeRepository
import com.ayman.ecolift.web.supabase.CardioRepository
import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.ExerciseSwapRepository
import com.ayman.ecolift.web.supabase.LearnedSplitsCoordinator
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import com.ayman.ecolift.web.supabase.NoAssistantChangeRepository
import com.ayman.ecolift.web.supabase.NoExerciseSwapRepository
import com.ayman.ecolift.web.supabase.PendingReviewRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.UserSettingsRepository
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoomRepos(
    db: AppDatabase,
    private val scope: CoroutineScope,
) : AppRepos {
    private val store = RoomStore(db)
    private val setRepository = RoomSetRepository(store)
    private val exerciseRepository = RoomExerciseRepository(store)
    private val learned = ForwardingLearnedSplits()
    private var learnedJob: Job? = null
    private var learnedStarted = false

    override val exerciseSwaps: ExerciseSwapRepository = NoExerciseSwapRepository
    override val exercises: ExerciseRepository = exerciseRepository
    override val sets: SetRepository = setRepository
    private val settingsRepository = RoomUserSettingsRepository(store) { enabled ->
        if (learnedStarted) installLearnedSplits(enabled)
    }
    override val settings: UserSettingsRepository = settingsRepository
    override val workout: WorkoutRepository = RoomWorkoutRepository(
        store = store,
        sets = setRepository,
        exercises = exerciseRepository,
        settings = settingsRepository,
        scope = scope,
    )
    override val pendingReviews: PendingReviewRepository = RoomPendingReviewRepository(store)
    override val cardio: CardioRepository = RoomCardioRepository(store)
    override val assistantChanges: AssistantChangeRepository = NoAssistantChangeRepository
    override val splitReplacement: SplitReplacementCoordinator = SplitReplacementCoordinator(
        journal = MemorySplitJournal(store),
        replaceSetDayBatch = { date, desired -> setRepository.replaceDayBatch(date, desired) },
        enqueueDayBatch = { op -> workout.enqueueDayBatch(op) },
        withDrainLock = { block -> store.write { block() } },
        kickEngine = {},
        restoreSetDayBatch = { date, desired, modes, validate ->
            setRepository.replaceDayBatch(date, desired, modes, validate)
        },
    )
    override val learnedSplits: LearnedSplitsSource = learned
    override val syncStatus: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.UpToDate)
    override val cloudStamps: StateFlow<CloudStamps> = MutableStateFlow(CloudStamps())

    override suspend fun loadAll() {
        store.write { reloadAll() }
        exerciseRepository.publishVisible()
        if (!learnedStarted) {
            learnedStarted = true
            installLearnedSplits(store.settings.value.smartSuggest)
        }
    }

    override fun close() {
        learnedJob?.cancel()
        learnedJob = null
    }

    private fun installLearnedSplits(smartSuggest: Boolean) {
        learnedJob?.cancel()
        val parent = scope.coroutineContext[Job]
        val job = SupervisorJob(parent)
        learnedJob = job
        val child = CoroutineScope(scope.coroutineContext + job)
        val coordinator = LearnedSplitsCoordinator(
            scope = child,
            sets = setRepository,
            exercises = exerciseRepository,
            workout = workout,
            persist = true,
            isOnline = { true },
            smartSuggest = smartSuggest,
        )
        learned.bind(child, coordinator)
        coordinator.start(slotsFreshFromBoot = true)
    }
}

private class ForwardingLearnedSplits : LearnedSplitsSource {
    private val modelFlow = MutableStateFlow(LearnedModel.EMPTY)
    override val model: StateFlow<LearnedModel> = modelFlow
    private var current: LearnedSplitsCoordinator? = null
    private var collectJob: Job? = null

    fun bind(scope: CoroutineScope, coordinator: LearnedSplitsCoordinator) {
        collectJob?.cancel()
        current = coordinator
        collectJob = scope.launch {
            coordinator.model.collect { modelFlow.value = it }
        }
    }

    override fun requestRun() {
        current?.requestRun()
    }
}

private class MemorySplitJournal(
    private val store: RoomStore,
) : SplitReplacementJournal {
    override fun load(): Result<List<PreparedSplitReplacement>> =
        Result.success(store.journalNow().values.toList())

    override fun save(replacement: PreparedSplitReplacement): Result<Unit> {
        store.saveJournal(replacement)
        return Result.success(Unit)
    }

    override fun clear(date: String): Result<Unit> {
        store.clearJournal(date)
        return Result.success(Unit)
    }
}
