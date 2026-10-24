package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.DayWrite
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.DataGeneration
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
import com.ayman.ecolift.data.room.Exercise as RoomExercise

@OptIn(ExperimentalTime::class)
class RoomRepos(
    db: AppDatabase,
    scope: CoroutineScope,
    private val generation: DataGeneration = DataGeneration(),
) : AppRepos {
    private val workerJob = SupervisorJob(scope.coroutineContext[Job])
    private val workerScope = CoroutineScope(scope.coroutineContext + workerJob + Dispatchers.Default)
    private val store = RoomStore(db)
    private val setRepository = RoomSetRepository(store)
    private val exerciseRepository = RoomExerciseRepository(store)
    private val learned = ForwardingLearnedSplits()
    private var learnedJob: Job? = null
    private var learnedStarted = false

    /** Test seam. Runs before a learned slot write, and before any generation check. */
    var beforeLearnedWrite: suspend () -> Unit = {}

    /** Test seam. Runs after that write attempt returns, including a rejected one. */
    var afterLearnedWrite: () -> Unit = {}

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

    /**
     * Leave [learnedStarted] set so the reload inside [replaceDataset] does not install a learner
     * for the generation [DataGeneration.replace] is about to retire.
     */
    private suspend fun stopLearnedSplits() {
        val job = synchronized(learned) {
            learnedStarted = true
            learnedJob
        }
        job?.cancel()
        job?.join()
    }

    suspend fun <T> replaceDataset(import: suspend () -> T): T {
        stopLearnedSplits()
        val result = generation.replace(reload = { loadAll() }) {
            import()
        }
        synchronized(learned) {
            learnedStarted = true
            installLearnedSplits(store.settings.value.smartSuggest)
        }
        return result
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
        val captured = generation.changes.value
        val coordinator = LearnedSplitsCoordinator(
            scope = child,
            sets = setRepository,
            exercises = exerciseRepository,
            workout = LearnedWriteGuard(
                inner = workout,
                generation = generation,
                captured = captured,
                beforeWrite = { beforeLearnedWrite() },
                afterWrite = { afterLearnedWrite() },
            ),
            persist = true,
            isOnline = { true },
            alternatives = { exerciseSwaps.alternatives.value },
            smartSuggest = smartSuggest,
        )
        learned.bind(child, coordinator)
        coordinator.start(slotsFreshFromBoot = true)
    }
}

private class LearnedWriteGuard(
    private val inner: WorkoutRepository,
    private val generation: DataGeneration,
    private val captured: Long,
    private val beforeWrite: suspend () -> Unit,
    private val afterWrite: () -> Unit,
) : WorkoutRepository by inner {
    override suspend fun upsertLearnedSlot(
        slotId: Long?,
        name: String?,
        learned: Boolean,
        memberIds: List<Long>,
        anchorUpdatedAt: Long,
    ): Long {
        beforeWrite()
        val stored = generation.withCurrent(captured) {
            inner.upsertLearnedSlot(slotId, name, learned, memberIds, anchorUpdatedAt)
        }
        afterWrite()
        return stored ?: (slotId ?: 0L)
    }

    override suspend fun archiveLearnedSlot(slotId: Long) {
        generation.withCurrent(captured) {
            inner.archiveLearnedSlot(slotId)
        }
    }

    override suspend fun enqueueLearnedDayWrites(writes: List<DayWrite>) {
        generation.withCurrent(captured) {
            inner.enqueueLearnedDayWrites(writes)
        }
    }
}

internal const val SEED_DEMO_EXTRA = "seedDemo"

/**
 * A fresh install already contains the packaged exercise catalog. That catalog is the empty
 * exercise state. Any other personal row, or any change to those exercises, means the database
 * is somebody's data and must not be replaced by the demo.
 */
internal data class DemoSeedOccupancy(
    val exercisesAreShippedCatalog: Boolean = true,
    val hasCycleSlots: Boolean = false,
    val hasSplitChoices: Boolean = false,
    val hasCardioSessions: Boolean = false,
    val hasSplitMembers: Boolean = false,
    val hasSettings: Boolean = false,
    val hasCycle: Boolean = false,
    val hasWorkoutDays: Boolean = false,
    val hasCards: Boolean = false,
    val hasPendingReviews: Boolean = false,
    val hasArchives: Boolean = false,
    val hasAliases: Boolean = false,
    val hasDistinctPairs: Boolean = false,
    val hasSplitAlternatives: Boolean = false,
) {
    val occupied: Boolean
        get() = !exercisesAreShippedCatalog ||
            hasCycleSlots ||
            hasSplitChoices ||
            hasCardioSessions ||
            hasSplitMembers ||
            hasSettings ||
            hasCycle ||
            hasWorkoutDays ||
            hasCards ||
            hasPendingReviews ||
            hasArchives ||
            hasAliases ||
            hasDistinctPairs ||
            hasSplitAlternatives
}

internal fun shouldSeedDemo(
    debug: Boolean,
    requested: Boolean,
    hasSets: Boolean,
    occupancy: DemoSeedOccupancy = DemoSeedOccupancy(),
): Boolean = debug && requested && !hasSets && !occupancy.occupied

private const val SHIPPED_MUSCLE_GROUPS = "CHEST · TRICEPS"

/** Identities copied from assets/database/ecolift.db. A fresh open always has exactly these rows. */
internal val SHIPPED_EXERCISE_CATALOG: List<RoomExercise> = listOf(
    RoomExercise(1, "Dip Machine Weighted", SHIPPED_MUSCLE_GROUPS, false, 1776720714660, null),
    RoomExercise(2, "Lateral Raise Dumbell", SHIPPED_MUSCLE_GROUPS, false, 1776720765657, null),
    RoomExercise(3, "Pull Up", SHIPPED_MUSCLE_GROUPS, false, 1776720799292, null),
    RoomExercise(4, "Row Machine", SHIPPED_MUSCLE_GROUPS, false, 1776720836561, null),
    RoomExercise(5, "Seated Dip", SHIPPED_MUSCLE_GROUPS, false, 1777509663679, null),
    RoomExercise(6, "Dip Machine", SHIPPED_MUSCLE_GROUPS, false, 1777509688906, null),
    RoomExercise(7, "Lateral Raise Machine", SHIPPED_MUSCLE_GROUPS, false, 1777510512774, null),
    RoomExercise(8, "Standing Calf Raise Machine", SHIPPED_MUSCLE_GROUPS, false, 1777510979540, null),
    RoomExercise(9, "One Legged Extension Machine", SHIPPED_MUSCLE_GROUPS, false, 1777511853615, null),
    RoomExercise(10, "Standing Behind The Back Skull Crusher", SHIPPED_MUSCLE_GROUPS, false, 1777511883653, null),
    RoomExercise(11, "Rear Tether", SHIPPED_MUSCLE_GROUPS, false, 1777512219358, null),
    RoomExercise(12, "Leaning Dumbell Curl", SHIPPED_MUSCLE_GROUPS, false, 1777512822868, null),
)

internal fun isShippedExerciseCatalog(rows: List<RoomExercise>): Boolean {
    if (rows.size != SHIPPED_EXERCISE_CATALOG.size) return false
    val byId = rows.associateBy { it.id }
    return SHIPPED_EXERCISE_CATALOG.all { expected ->
        val row = byId[expected.id] ?: return false
        row.name == expected.name &&
            row.muscleGroups == expected.muscleGroups &&
            !row.isBodyweight &&
            row.createdAt == expected.createdAt &&
            row.mergedIntoId == null
    }
}

internal suspend fun readDemoSeedOccupancy(db: AppDatabase): DemoSeedOccupancy = DemoSeedOccupancy(
    exercisesAreShippedCatalog = isShippedExerciseCatalog(db.exerciseDao().getAll()),
    hasCycleSlots = db.cycleSlotDao().getAll().isNotEmpty(),
    hasSplitChoices = db.splitMemberChoiceDao().getAll().isNotEmpty(),
    hasCardioSessions = db.cardioSessionDao().getAll().isNotEmpty(),
    hasSplitMembers = db.splitExerciseDao().getAll().isNotEmpty(),
    hasSettings = db.userSettingsDao().get() != null,
    hasCycle = db.cycleDao().getCycle() != null,
    hasWorkoutDays = db.workoutDayDao().getAll().isNotEmpty(),
    hasCards = db.workoutExerciseCardDao().getAll().isNotEmpty(),
    hasPendingReviews = db.pendingReviewDao().getAll().isNotEmpty(),
    hasArchives = db.archivedCycleDao().getAll().isNotEmpty(),
    hasAliases = db.exerciseAliasDao().getAll().isNotEmpty(),
    hasDistinctPairs = db.exerciseDistinctPairDao().getAll().isNotEmpty(),
    hasSplitAlternatives = db.splitAlternativeDao().getAll().isNotEmpty(),
)

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
