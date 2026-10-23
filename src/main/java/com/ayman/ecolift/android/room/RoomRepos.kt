package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.AssistantChangeRepository
import com.ayman.ecolift.web.supabase.CardioRepository
import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.ExerciseSwapRepository
import com.ayman.ecolift.web.supabase.LearnedSplitsCoordinator
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import com.ayman.ecolift.web.supabase.NoAssistantChangeRepository
import com.ayman.ecolift.web.supabase.PendingReviewRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.UserSettingsRepository
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoomRepos(
    db: AppDatabase,
    scope: CoroutineScope,
) : AppRepos {
    private val workerJob = SupervisorJob(scope.coroutineContext[Job])
    private val workerScope = CoroutineScope(scope.coroutineContext + workerJob + Dispatchers.Default)
    private val store = RoomStore(db)
    private val setRepository = RoomSetRepository(store)
    private val exerciseRepository = RoomExerciseRepository(store)
    private val learned = ForwardingLearnedSplits()
    private var learnedJob: Job? = null
    private var learnedStarted = false

    override val exerciseSwaps: ExerciseSwapRepository = RoomExerciseSwapRepository(store) {
        learned.requestRun()
    }
    override val exercises: ExerciseRepository = exerciseRepository
    override val sets: SetRepository = setRepository
    private val settingsRepository = RoomUserSettingsRepository(store) { enabled ->
        synchronized(learned) {
            if (learnedStarted) installLearnedSplits(enabled)
        }
    }
    override val settings: UserSettingsRepository = settingsRepository
    override val workout: WorkoutRepository = RoomWorkoutRepository(
        store = store,
        sets = setRepository,
        exercises = exerciseRepository,
        settings = settingsRepository,
        scope = workerScope,
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

    override suspend fun loadAll() = withContext(Dispatchers.IO) {
        store.write { reloadAll() }
        exerciseRepository.publishVisible()
        synchronized(learned) {
            if (!learnedStarted) {
                learnedStarted = true
                installLearnedSplits(store.settings.value.smartSuggest)
            }
        }
    }

    suspend fun installDemoSeed(seed: DemoSeedData) {
        store.write {
            db.exerciseDao().deleteAllExercises()
            exercises = emptyList()
            seed.exercises.forEach { upsertExercise(it) }
            upsertSettings(seed.settings)
            upsertCycle(seed.cycle)
            seed.slots.forEach { upsertSlot(it) }
            seed.splitExercises.forEach { insertSplitMember(it) }
            seed.workoutDays.forEach { upsertDay(it) }
            val cards = demoSessionCards(seed.sets)
            val cardIdBySession = cards.associate { (it.date to it.exerciseId) to it.id }
            cards.forEach { insertCard(it) }
            seed.sets.forEach { set ->
                val cardId = if (set.cardId.isNotBlank()) {
                    set.cardId
                } else {
                    cardIdBySession[set.date to set.exerciseId].orEmpty()
                }
                insertSet(set.copy(cardId = cardId))
            }
            seed.cardio.forEach { insertCardio(it) }
            seed.pendingReviews.forEach { insertReview(it) }
            seed.archives.forEach { insertArchive(it) }
        }
    }

    override fun close() {
        synchronized(learned) {
            workerJob.cancel()
            learnedJob = null
        }
    }

    private fun installLearnedSplits(smartSuggest: Boolean) {
        learnedJob?.cancel()
        val parent = workerScope.coroutineContext[Job]
        val job = SupervisorJob(parent)
        learnedJob = job
        val child = CoroutineScope(workerScope.coroutineContext + job)
        val coordinator = LearnedSplitsCoordinator(
            scope = child,
            sets = setRepository,
            exercises = exerciseRepository,
            workout = workout,
            persist = true,
            isOnline = { true },
            alternatives = { exerciseSwaps.alternatives.value },
            smartSuggest = smartSuggest,
        )
        learned.bind(child, coordinator)
        coordinator.start(slotsFreshFromBoot = true)
    }
}

internal const val SEED_DEMO_EXTRA = "seedDemo"

internal fun shouldSeedDemo(debug: Boolean, requested: Boolean, hasSets: Boolean): Boolean =
    debug && requested && !hasSets

private fun demoSessionCards(sets: List<WorkoutSet>): List<WorkoutExerciseCard> {
    val seen = mutableSetOf<Pair<String, Long>>()
    val positions = mutableMapOf<String, Int>()
    return buildList {
        sets.forEach { set ->
            val key = set.date to set.exerciseId
            if (seen.add(key)) {
                val position = positions.getOrElse(set.date) { 0 }
                val completed = sets
                    .filter { it.date == set.date && it.exerciseId == set.exerciseId }
                    .all { it.completed }
                add(
                    WorkoutExerciseCard(
                        id = "demo-${set.date}-${set.exerciseId}",
                        date = set.date,
                        exerciseId = set.exerciseId,
                        position = position,
                        displayMode = if (completed) {
                            WorkoutCardDisplayMode.COMPACT
                        } else {
                            WorkoutCardDisplayMode.EXPANDED
                        },
                        origin = WorkoutCardOrigin.LEGACY,
                    ),
                )
                positions[set.date] = position + 1
            }
        }
    }
}

private class ForwardingLearnedSplits : LearnedSplitsSource {
    private val modelFlow = MutableStateFlow(LearnedModel.EMPTY)
    override val model: StateFlow<LearnedModel> = modelFlow

    @Volatile
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
