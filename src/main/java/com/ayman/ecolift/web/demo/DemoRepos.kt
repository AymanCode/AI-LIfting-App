package com.ayman.ecolift.web.demo

import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.CycleSnapshotBuilder
import com.ayman.ecolift.data.DateVolume
import com.ayman.ecolift.data.DayQueries
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseAlias
import com.ayman.ecolift.data.ExerciseDistinctPair
import com.ayman.ecolift.data.ExerciseMatch
import com.ayman.ecolift.data.ExerciseMatchInput
import com.ayman.ecolift.data.ExerciseMatcher
import com.ayman.ecolift.data.ExerciseMaxWeight
import com.ayman.ecolift.data.ExerciseMeta
import com.ayman.ecolift.data.ExerciseNames
import com.ayman.ecolift.data.ExerciseProgressSummary
import com.ayman.ecolift.data.ExerciseSuggestion
import com.ayman.ecolift.data.ExerciseVolume
import com.ayman.ecolift.data.FuzzyMatcher
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitBucketKind
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UnitSystem
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.activeCycleSlots
import com.ayman.ecolift.data.forActiveCycleSlots
import com.ayman.ecolift.data.looksLikeBodyweightExerciseName
import com.ayman.ecolift.data.normalizedBodyweightLoad
import com.ayman.ecolift.data.normalizedUserBodyweightLbs
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCalculator
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.supabase.ArchiveSummary
import com.ayman.ecolift.web.supabase.AssistantChangeRepository
import com.ayman.ecolift.web.supabase.CardioRepository
import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.NoAssistantChangeRepository
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.ExerciseMergePreparationResult
import com.ayman.ecolift.web.supabase.ExerciseMergeResult
import com.ayman.ecolift.web.supabase.ExerciseRenameResult
import com.ayman.ecolift.web.supabase.simpleNameKey
import com.ayman.ecolift.web.supabase.PendingReviewRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.LearnedSplitsCoordinator
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import com.ayman.ecolift.web.supabase.SmartSplitRegistration
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.UserSettingsRepository
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

class DemoRepos(seed: DemoSeedData = DemoData.generate()) : AppRepos {
    override val exercises: ExerciseRepository = DemoExerciseRepository(seed.exercises, seed.usageCounts)
    override val sets: SetRepository = DemoSetRepository(seed.sets) { exercises.exercises.value }
    override val settings: UserSettingsRepository = DemoUserSettingsRepository(
        seed.settings.copy(smartSuggest = DemoSmartSuggest.read(seed.settings.smartSuggest)),
    )
    override val workout: WorkoutRepository = DemoWorkoutRepository(
        seed = seed,
        exercises = exercises,
        sets = sets,
        settings = settings,
    )
    override val pendingReviews: PendingReviewRepository = DemoPendingReviewRepository(seed.pendingReviews)
    override val cardio: CardioRepository = DemoCardioRepository(seed.cardio)
    override val assistantChanges: AssistantChangeRepository = NoAssistantChangeRepository
    override val splitReplacement: SplitReplacementCoordinator = SplitReplacementCoordinator(
        journal = object : SplitReplacementJournal {
            override fun load(): Result<List<PreparedSplitReplacement>> = Result.success(emptyList())
            override fun save(replacement: PreparedSplitReplacement): Result<Unit> = Result.success(Unit)
            override fun clear(date: String): Result<Unit> = Result.success(Unit)
        },
        replaceSetDayBatch = { _, _ -> LocalCommit.Committed(Unit) },
        enqueueDayBatch = {},
        withDrainLock = { block -> block() },
        kickEngine = {},
    )

    private val learnedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A pure projection: the engine runs on the demo data in memory and never writes. */
    override val learnedSplits: LearnedSplitsSource = LearnedSplitsCoordinator(
        scope = learnedScope,
        sets = sets,
        exercises = exercises,
        workout = workout,
        persist = false,
        smartSuggest = DemoSmartSuggest.read(seed.settings.smartSuggest),
    ).also { it.start() }

    override val syncStatus: StateFlow<SyncStatus> = MutableStateFlow<SyncStatus>(SyncStatus.UpToDate)
    @OptIn(ExperimentalTime::class)
    override val cloudStamps: StateFlow<CloudStamps> = MutableStateFlow(CloudStamps())

    override suspend fun loadAll() = Unit

    /** Leaving the demo stops the engine's triggers and its midnight timer. */
    override fun close() {
        learnedScope.cancel()
    }
}

private class DemoExerciseRepository(
    exercises: List<Exercise>,
    private val usageCounts: Map<Long, Int>,
) : ExerciseRepository {
    private val allExercises = exercises.sortedBy { it.name }
    private val _exercises = MutableStateFlow(allExercises.filter { it.mergedIntoId == null })
    private val _aliases = MutableStateFlow<List<ExerciseAlias>>(emptyList())
    override val exercises: StateFlow<List<Exercise>> = _exercises.asStateFlow()

    override fun observeAliases(): StateFlow<List<ExerciseAlias>> = _aliases.asStateFlow()
    override suspend fun refresh() = Unit
    override suspend fun getAll(): List<Exercise> = _exercises.value
    override fun mergeGraph(): Map<Long, Long?> = allExercises.associate { it.id to it.mergedIntoId }
    override fun canonicalId(id: Long): Long {
        var current = id
        repeat(16) {
            val next = allExercises.firstOrNull { it.id == current }?.mergedIntoId ?: return current
            current = next
        }
        if (allExercises.firstOrNull { it.id == current }?.mergedIntoId != null) {
            throw IllegalStateException("Exercise $id has a cyclic or overlong merge chain")
        }
        return current
    }
    override suspend fun getById(id: Long): Exercise? =
        _exercises.value.firstOrNull { it.id == canonicalId(id) }
    override suspend fun getByIds(ids: Collection<Long>): List<Exercise> {
        if (ids.isEmpty()) return emptyList()
        val distinct = ids.map(::canonicalId).distinct().toSet()
        return _exercises.value.filter { it.id in distinct }
    }

    override suspend fun findExact(name: String): Exercise? =
        _exercises.value.firstOrNull { simpleNameKey(it.name) == simpleNameKey(name) }

    override suspend fun getOrCreate(name: String): Exercise {
        val normalized = ExerciseNames.normalize(name)
        val key = simpleNameKey(normalized)
        return _exercises.value.firstOrNull { simpleNameKey(it.name) == key }
            ?: allExercises.firstOrNull { simpleNameKey(it.name) == key }?.let { getById(it.id) }
            ?: Exercise(name = normalized, isBodyweight = looksLikeBodyweightExerciseName(normalized))
    }

    override suspend fun suggestions(query: String, maxDistance: Int, limit: Int): List<ExerciseSuggestion> {
        val normalizedQuery = normalizeName(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        return _exercises.value
            .filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            .map { exercise ->
                ExerciseSuggestion(
                    exercise = exercise,
                    distance = FuzzyMatcher.levenshteinDistance(normalizedQuery.lowercase(), exercise.name.lowercase()),
                )
            }
            .filter { it.distance <= maxDistance }
            .sortedWith(compareBy<ExerciseSuggestion> { it.distance }.thenBy { it.exercise.name })
            .take(limit)
    }

    override suspend fun getPredictive(query: String, limit: Int): List<Exercise> {
        if (query.isBlank()) return emptyList()
        val byId = _exercises.value.associateBy { it.id }
        return ExerciseMatcher.rank(
            _exercises.value.map { ExerciseMatchInput(it, usageCount = usageCounts[it.id] ?: 0) },
            query.trim(),
            limit,
        ).mapNotNull { byId[it.exerciseId] }
    }

    override suspend fun duplicateCandidate(exerciseId: Long): ExerciseMatch? {
        val inputs = _exercises.value.map { ExerciseMatchInput(it, usageCount = usageCounts[it.id] ?: 0) }
        val target = inputs.firstOrNull { it.exercise.id == canonicalId(exerciseId) } ?: return null
        return ExerciseMatcher.duplicateCandidate(target, inputs)
    }

    override suspend fun duplicateCandidate(name: String): ExerciseMatch? = ExerciseMatcher.duplicateCandidate(
        target = ExerciseMatchInput(Exercise(id = Long.MIN_VALUE, name = normalizeName(name))),
        others = _exercises.value.map { ExerciseMatchInput(it, usageCount = usageCounts[it.id] ?: 0) },
    )

    override fun normalizeName(name: String): String = ExerciseNames.normalize(name)
    override suspend fun updateName(id: Long, newName: String): ExerciseRenameResult =
        ExerciseRenameResult.Success(getById(id) ?: Exercise(id = canonicalId(id), name = normalizeName(newName)))
    override suspend fun updateMuscleGroups(id: Long, muscleGroups: String) = Unit
    override suspend fun deleteExercise(id: Long) = Unit
    override suspend fun loadDistinctPairs(): List<ExerciseDistinctPair> = emptyList()
    override suspend fun recordAlias(exerciseId: Long, alias: String) = Unit
    override suspend fun recordDistinctPair(idA: Long, idB: Long) = Unit
    override suspend fun prepareMerge(idA: Long, idB: Long): ExerciseMergePreparationResult =
        ExerciseMergePreparationResult.QueueNotSettled
    override suspend fun mergeExercises(
        idA: Long,
        idB: Long,
        expectedKeeperId: Long,
        alias: String?,
    ): ExerciseMergeResult = ExerciseMergeResult.QueueNotSettled
}

private class DemoSetRepository(
    sets: List<WorkoutSet>,
    private val exercisesProvider: () -> List<Exercise>,
) : SetRepository {
    private val initialCards = buildList {
        val seen = mutableSetOf<Pair<String, Long>>()
        val positions = mutableMapOf<String, Int>()
        sets.forEach { set ->
            val key = set.date to set.exerciseId
            if (seen.add(key)) {
                val position = positions.getOrElse(set.date) { 0 }
                add(
                    WorkoutExerciseCard(
                        id = "demo-${set.date}-${set.exerciseId}",
                        date = set.date,
                        exerciseId = set.exerciseId,
                        position = position,
                        displayMode = if (sets.filter {
                                it.date == set.date && it.exerciseId == set.exerciseId
                            }.all(WorkoutSet::completed)
                        ) {
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
    private val cardIdBySession = initialCards.associate { (it.date to it.exerciseId) to it.id }
    private val _sets = MutableStateFlow(
        sets.map { set ->
            if (set.cardId.isNotBlank()) set
            else set.copy(cardId = cardIdBySession[set.date to set.exerciseId].orEmpty())
        },
    )
    override val sets: StateFlow<List<WorkoutSet>> = _sets.asStateFlow()
    private val _cards = MutableStateFlow(initialCards)
    override val cards: StateFlow<List<WorkoutExerciseCard>> = _cards.asStateFlow()

    override suspend fun refresh() = Unit
    override suspend fun insert(set: WorkoutSet): LocalCommit<WorkoutSet> = LocalCommit.Committed(set)
    override suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet> = LocalCommit.Committed(set)
    override suspend fun delete(id: Long) = Unit
    override suspend fun updateCardDisplayMode(cardId: String, displayMode: WorkoutCardDisplayMode): LocalCommit<Unit> {
        _cards.value = _cards.value.map { card ->
            if (card.id == cardId) {
                card.copy(displayMode = displayMode, rowRevision = card.rowRevision + 1)
            } else {
                card
            }
        }
        return LocalCommit.Committed(Unit)
    }
    override suspend fun getAllSets(): List<WorkoutSet> = _sets.value
    override fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> =
        _sets.map { SetQueries.forDate(it, date) }

    override suspend fun getSetsForDate(date: String): List<WorkoutSet> =
        SetQueries.forDate(_sets.value, date)

    override suspend fun addSet(date: String, exerciseId: Long, copyFrom: WorkoutSet?): LocalCommit<WorkoutSet> {
        val snapshot = _sets.value
        val current = SetQueries.forDateAndExercise(snapshot, date, exerciseId)
        val template = copyFrom
            ?: current.lastOrNull()
            ?: SetQueries.mostRecentBeforeDate(snapshot, exerciseId, date)
        val exercise = exercisesProvider().firstOrNull { it.id == exerciseId }
        val isBodyweight = template?.isBodyweight ?: exercise?.isBodyweight ?: false
        return LocalCommit.Committed(WorkoutSet(
            exerciseId = exerciseId,
            date = date,
            setNumber = SetQueries.nextSetNumber(current),
            weightLbs = if (isBodyweight) normalizedBodyweightLoad(template?.weightLbs) else template?.weightLbs,
            reps = template?.reps,
            isBodyweight = isBodyweight,
            completed = false,
        ))
    }

    override suspend fun addExerciseSession(date: String, exerciseId: Long, isBodyweightDefault: Boolean): LocalCommit<List<WorkoutSet>> =
        LocalCommit.Committed(emptyList())

    override suspend fun addSetsForExercises(date: String, exerciseIds: List<Long>): LocalCommit<Unit> = LocalCommit.Committed(Unit)
    override suspend fun replaceDayBatch(
        date: String,
        desiredSets: List<WorkoutSet>,
        cardModes: Map<Long, WorkoutCardDisplayMode>,
        precondition: () -> Unit,
    ): LocalCommit<Unit> { precondition(); return LocalCommit.Committed(Unit) }
    override suspend fun cloneDay(templateDate: String, targetDate: String): LocalCommit<Unit> = LocalCommit.Committed(Unit)
    override suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet?): LocalCommit<WorkoutSet> = LocalCommit.Committed(set)
    override suspend fun upsertSet(set: WorkoutSet): LocalCommit<Long> = LocalCommit.Committed(set.id)
    override suspend fun getLastForDateAndExercise(date: String, exerciseId: Long): WorkoutSet? =
        SetQueries.forDateAndExercise(_sets.value, date, exerciseId).lastOrNull()

    override suspend fun getMostRecentBeforeDate(exerciseId: Long, date: String): WorkoutSet? =
        SetQueries.mostRecentBeforeDate(_sets.value, exerciseId, date)

    override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> =
        SetQueries.completedHistoryBeforeDate(_sets.value, exerciseId, beforeDate).take(100)

    override suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int? =
        SetQueries.maxCompletedWeightBefore(_sets.value, exerciseId, beforeDate)

    override suspend fun getLastSessionDate(beforeDate: String): String? =
        _sets.value.filter { it.date < beforeDate }.maxOfOrNull { it.date }

    override suspend fun getSetsByDate(date: String): List<WorkoutSet> = getSetsForDate(date)
    override suspend fun getSetsForDates(dates: List<String>): List<WorkoutSet> {
        if (dates.isEmpty()) return emptyList()
        val dateSet = dates.toSet()
        return _sets.value.filter { it.date in dateSet }
    }

    override suspend fun getCompletedSetsForDates(dates: List<String>): List<WorkoutSet> {
        if (dates.isEmpty()) return emptyList()
        val dateSet = dates.toSet()
        return _sets.value.filter { it.completed && it.date in dateSet }
    }

    override suspend fun getById(id: Long): WorkoutSet? = _sets.value.firstOrNull { it.id == id }
    override fun observeExerciseProgressSummaries(): Flow<List<ExerciseProgressSummary>> =
        _sets.map { sets ->
            val exercisesById = exercisesProvider().associateBy { it.id }
            sets.filter { it.isCheckedWorking }
                .groupBy { it.exerciseId }
                .mapNotNull { (exerciseId, exerciseSets) ->
                    val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
                    ExerciseProgressSummary(
                        exerciseId = exerciseId,
                        exerciseName = exercise.name,
                        isBodyweight = exercise.isBodyweight,
                        sessionCount = exerciseSets.map { it.date }.distinct().size,
                        lastSessionDate = exerciseSets.maxOf { it.date },
                    )
                }
        }

    override fun observeSetsSince(exerciseId: Long, sinceDate: String): Flow<List<WorkoutSet>> =
        _sets.map { sets ->
            sets.filter { it.exerciseId == exerciseId && it.date >= sinceDate && it.isCheckedWorking }
                .sortedWith(compareBy({ it.date }, { it.setNumber }))
        }

    override suspend fun getVolumeHistory(exerciseId: Long, limit: Int): List<DateVolume> =
        _sets.value.filter { it.exerciseId == exerciseId && it.isCheckedWorking }
            .groupBy { it.date }
            .map { (date, daySets) ->
                DateVolume(
                    date = date,
                    volume = com.ayman.ecolift.data.WorkoutMetrics.roundedVolume(daySets),
                )
            }
            .sortedByDescending { it.date }
            .take(limit)

    override suspend fun getSetsSince(exerciseId: Long, sinceDate: String): List<WorkoutSet> =
        _sets.value.filter { it.exerciseId == exerciseId && it.date >= sinceDate && it.isCheckedWorking }
            .sortedWith(compareBy({ it.date }, { it.setNumber }))

    override suspend fun getVolumesSince(sinceDate: String): List<ExerciseVolume> =
        _sets.value.filter { it.date >= sinceDate && it.isCheckedWorking }
            .groupBy { it.exerciseId }
            .map { (exerciseId, exerciseSets) ->
                ExerciseVolume(
                    exerciseId = exerciseId,
                    volume = com.ayman.ecolift.data.WorkoutMetrics.roundedVolume(exerciseSets),
                )
            }

    override suspend fun getAllTimeMaxWeights(): List<ExerciseMaxWeight> =
        _sets.value.filter { it.isCheckedWorking }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exerciseSets) ->
                val max = exerciseSets.mapNotNull { it.weightLbs }.maxOrNull() ?: return@mapNotNull null
                ExerciseMaxWeight(exerciseId = exerciseId, maxWeight = max)
            }

    override suspend fun getMaxWeightsForExercises(exerciseIds: List<Long>): List<ExerciseMaxWeight> {
        if (exerciseIds.isEmpty()) return emptyList()
        val ids = exerciseIds.toSet()
        return _sets.value.filter { it.isCheckedWorking && it.exerciseId in ids }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exerciseSets) ->
                val max = exerciseSets.mapNotNull { it.weightLbs }.maxOrNull() ?: return@mapNotNull null
                ExerciseMaxWeight(exerciseId = exerciseId, maxWeight = max)
            }
    }

    override suspend fun deleteSet(id: Long) = Unit
    override suspend fun deleteSets(ids: List<Long>, precondition: () -> Unit): LocalCommit<Unit> { precondition(); return LocalCommit.Committed(Unit) }
    override suspend fun updateDate(fromDate: String, toDate: String) = Unit
}

private class DemoUserSettingsRepository(settings: UserSettings) : UserSettingsRepository {
    private val _settings = MutableStateFlow(settings)
    override val settings: StateFlow<UserSettings> = _settings.asStateFlow()
    override suspend fun refresh() = Unit
    override suspend fun setPaletteChoice(choice: String) = Unit
    override suspend fun setBodyweight(lbs: Int?) = Unit
    override suspend fun setTimeZone(timeZone: String) = Unit
    override suspend fun setUnitSystem(unitSystem: UnitSystem) = Unit
    override suspend fun setSmartSuggest(enabled: Boolean) {
        DemoSmartSuggest.write(enabled)
        _settings.update { it.copy(smartSuggest = enabled) }
    }
}

private class DemoWorkoutRepository(
    seed: DemoSeedData,
    private val exercises: ExerciseRepository,
    private val sets: SetRepository,
    private val settings: UserSettingsRepository,
) : WorkoutRepository {
    private val archiveJson = Json { ignoreUnknownKeys = true }
    private val _cycle = MutableStateFlow(seed.cycle)
    private val _slots = MutableStateFlow(seed.slots)
    private val _splitExercises = MutableStateFlow(seed.splitExercises)
    private val _workoutDays = MutableStateFlow(seed.workoutDays)
    private val _archives = MutableStateFlow(seed.archives)

    override val cycle: StateFlow<Cycle> = _cycle.asStateFlow()
    override fun observeWorkoutDay(date: String): Flow<WorkoutDay?> =
        _workoutDays.map { days -> days.firstOrNull { it.date == date } }

    override fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = _workoutDays
    override fun observeCycleSlots(): Flow<List<CycleSlot>> = _slots.map { it.activeCycleSlots() }
    override fun observeAllCycleSlots(): Flow<List<CycleSlot>> = _slots
    override suspend fun getCycleSlots(): List<CycleSlot> = _slots.value.activeCycleSlots()
    override suspend fun getSlotByIdIncludingArchived(id: Long): CycleSlot? =
        _slots.value.firstOrNull { it.id == id }
    override fun observeSplitExercises(splitId: Long): Flow<List<SplitExercise>> =
        _splitExercises.map { list -> list.filter { it.splitId == splitId }.sortedBy { it.orderIndex } }

    override fun observeAllSplitExercises(): Flow<List<SplitExercise>> =
        combine(_splitExercises, _slots) { rows, slots ->
            rows.forActiveCycleSlots(slots).sortedWith(compareBy({ it.splitId }, { it.orderIndex }))
        }
    override fun observeAllSplitExercisesIncludingRetired(): Flow<List<SplitExercise>> = _splitExercises
    override fun observeArchivedCycles(): Flow<List<ArchivedCycle>> = _archives
    suspend fun getUserBodyweightLbs(): Int? =
        normalizedUserBodyweightLbs(settings.settings.value.userBodyweightLbs)

    override fun observeUserBodyweightLbs(): Flow<Int?> =
        settings.settings.map { normalizedUserBodyweightLbs(it.userBodyweightLbs) }

    suspend fun setUserBodyweightLbs(bodyweightLbs: Int?) = Unit
    override suspend fun addCycleSlot(name: String): Long = 0L
    override suspend fun deleteCycleSlot(id: Long) = Unit
    override suspend fun appendSplitExercise(slotId: Long, exerciseId: Long) = Unit
    override suspend fun renameCycleSlot(id: Long, name: String) = Unit
    override val splitMemberChoices: StateFlow<SplitChoicesState> = MutableStateFlow(SplitChoicesState.Ready(emptyMap(), confirmed = emptyMap()))
    override suspend fun saveSplitMembers(slotId: Long, board: EffectiveSplitMembers) = Unit
    override suspend fun getCycle(): Cycle = _cycle.value
    override suspend fun saveCycle(isActive: Boolean, numTypes: Int) = Unit
    override suspend fun setNextSessionType(slotType: Int) = Unit

    override suspend fun assignCycleSlot(date: String, slotId: Long, alternativeFor: String?): WorkoutDay {
        val slots = _slots.value.activeCycleSlots()
        val occurrence = (
            DayQueries.maxOccurrenceForSlotBefore(
                days = _workoutDays.value,
                date = date,
                slotId = slotId,
                notBeforeDate = getActiveCycleStart(),
            ) ?: 0
        ) + 1
        val slotType = slots.indexOfFirst { it.id == slotId }.takeIf { it >= 0 }
        requireNotNull(slotType) { "Active cycle slot $slotId not found" }
        return WorkoutDay(date, slotType, occurrence, alternativeFor, slotId)
    }

    override suspend fun clearCycleSlot(date: String) = Unit
    override suspend fun ensureWorkoutDay(date: String) = Unit
    override suspend fun enqueueDayBatch(op: DayPendingWrite) = Unit

    override suspend fun getPreviousOccurrenceDayForSlot(date: String, slotId: Long, occurrence: Int): WorkoutDay? {
        if (occurrence <= 0) return null
        require(_slots.value.any { it.id == slotId && it.isActive }) { "Active cycle slot $slotId not found" }
        return DayQueries.previousOccurrenceForSlot(
            days = _workoutDays.value,
            slotId = slotId,
            occurrence = occurrence,
            beforeDate = date,
            notBeforeDate = getActiveCycleStart(),
        )
    }

    override suspend fun getArchivedCycle(id: Long): ArchivedCycle? = _archives.value.firstOrNull { it.id == id }
    override suspend fun deleteArchivedCycle(id: Long) = Unit
    override suspend fun countOverlappingArchives(start: String, end: String): Int =
        _archives.value.count { it.startDate <= end && it.endDate >= start }

    override suspend fun getLatestWorkoutDate(): String? = SetQueries.latestCompletedDate(sets.sets.value)
    override suspend fun moveWorkoutDay(currentDate: String, newDate: String) = Unit
    override suspend fun getActiveCycleStart(): String =
        getCycle().startDate
            ?: SetQueries.earliestCompletedDate(sets.sets.value)
            ?: todayLocalDate().toString()

    suspend fun activeCycleProgress(): CycleProgressCore = buildActiveCycleProgress().core
    suspend fun activeCycleComparison(window: ComparisonWindow): CycleComparison {
        val coreResult = buildActiveCycleProgress()
        return CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = loadPriorSets(coreResult.core.lifts.map { it.exerciseId }, coreResult.core.startDate),
            window = window,
            userBodyweightLbs = getUserBodyweightLbs(),
        )
    }

    override suspend fun archivedCycleProgress(archiveId: Long): CycleProgressCore =
        buildArchivedCycleProgress(archiveId).core

    override suspend fun archivedCycleComparison(archiveId: Long, window: ComparisonWindow): CycleComparison {
        val coreResult = buildArchivedCycleProgress(archiveId)
        return CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = loadPriorSets(coreResult.core.lifts.map { it.exerciseId }, coreResult.core.startDate),
            window = window,
            userBodyweightLbs = getUserBodyweightLbs(),
        )
    }

    override suspend fun archiveCurrentCycle(name: String, startDate: String, endDate: String): Long = 0L
    override suspend fun registerSmartSplit(
        name: String,
        exerciseIds: List<Long>,
        dates: List<String>,
    ): SmartSplitRegistration = SmartSplitRegistration.Refused("demo")

    // The demo never writes: the learned-splits model is a projection over the seed data.
    override suspend fun upsertLearnedSlot(slotId: Long?, name: String?, learned: Boolean, memberIds: List<Long>, anchorUpdatedAt: Long): Long =
        slotId ?: 0L
    override suspend fun refreshSlots() = Unit
    override suspend fun archiveLearnedSlot(slotId: Long) = Unit
    override suspend fun suppressCycleSlot(slotId: Long) = Unit
    override suspend fun enqueueLearnedDayWrites(writes: List<com.ayman.ecolift.data.splits.DayWrite>) = Unit

    override suspend fun archiveSummary(archive: ArchivedCycle): ArchiveSummary {
        val snapshot = buildMutableArchivedSnapshot(archive, getUserBodyweightLbs())
        return ArchiveSummary(
            splitCount = CycleSnapshotBuilder.splitCount(snapshot),
            totalSessions = snapshot.totals.sessions,
            totalVolumeLbs = snapshot.totals.totalVolumeLbs,
        )
    }

    private suspend fun buildActiveCycleProgress(): CycleProgressCalculator.CoreResult {
        val startDate = getActiveCycleStart()
        val latestDate = SetQueries.latestCompletedDate(sets.sets.value)
        val endDate = latestDate?.takeIf { it >= startDate } ?: startDate
        val cycleSetsList = SetQueries.completedSetsInRange(sets.sets.value, startDate, endDate)
        val slots = _slots.value.activeCycleSlots()
        val snapshot = CycleSnapshotBuilder.build(
            startDate = startDate,
            endDate = endDate,
            slots = slots,
            splitExercises = _splitExercises.value.forActiveCycleSlots(_slots.value),
            workoutDays = _workoutDays.value,
            sets = cycleSetsList,
            exerciseNames = exerciseMetaFor(cycleSetsList.map { it.exerciseId }),
            userBodyweightLbs = getUserBodyweightLbs(),
        )
        return CycleProgressCalculator.buildCore(
            snapshot = snapshot,
            sets = cycleSetsList.map { it.toProgressInput() },
            userBodyweightLbs = getUserBodyweightLbs(),
            realSlotCount = slots.size,
        )
    }

    private suspend fun buildArchivedCycleProgress(archiveId: Long): CycleProgressCalculator.CoreResult {
        val archive = requireNotNull(getArchivedCycle(archiveId)) { "Archived cycle not found: $archiveId" }
        val userBodyweightLbs = getUserBodyweightLbs()
        val snapshot = buildMutableArchivedSnapshot(archive, userBodyweightLbs)
        val cycleSetsList = SetQueries.completedSetsInRange(sets.sets.value, archive.startDate, archive.endDate)
        return CycleProgressCalculator.buildCore(
            snapshot = snapshot,
            sets = cycleSetsList.map { it.toProgressInput() },
            userBodyweightLbs = userBodyweightLbs,
            realSlotCount = snapshot.splits.count { it.bucketKind == SplitBucketKind.Real },
        )
    }

    private suspend fun buildMutableArchivedSnapshot(
        archive: ArchivedCycle,
        userBodyweightLbs: Int?,
    ): CycleSnapshot {
        val archivedSnapshot = archiveJson.decodeFromString<CycleSnapshot>(archive.snapshotJson)
        val slots = archivedSnapshot.splits
            .filter { it.bucketKind == SplitBucketKind.Real }
            .sortedWith(compareBy({ it.orderIndex }, { it.slotId }))
            .map { split -> CycleSlot(id = split.slotId, name = split.name, orderIndex = split.orderIndex) }
        val splitExercises = archivedSnapshot.splits
            .filter { it.bucketKind == SplitBucketKind.Real }
            .flatMap { split ->
                split.exercises.mapIndexed { index, exercise ->
                    SplitExercise(splitId = split.slotId, exerciseId = exercise.exerciseId, orderIndex = index)
                }
            }
        val cycleSetsList = SetQueries.completedSetsInRange(sets.sets.value, archive.startDate, archive.endDate)
        val snapshotMeta = archivedSnapshot.splits.flatMap { it.exercises }
            .associate { exercise -> exercise.exerciseId to ExerciseMeta(name = exercise.name, isBodyweight = exercise.isBodyweight) }
        val liveMeta = exerciseMetaFor((cycleSetsList.map { it.exerciseId } + snapshotMeta.keys).distinct())
        return CycleSnapshotBuilder.build(
            startDate = archive.startDate,
            endDate = archive.endDate,
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = _workoutDays.value,
            sets = cycleSetsList,
            exerciseNames = snapshotMeta + liveMeta,
            userBodyweightLbs = userBodyweightLbs,
        )
    }

    private suspend fun loadPriorSets(
        exerciseIds: List<Long>,
        beforeDate: String,
    ): Map<Long, List<CycleProgressCalculator.SetInput>> =
        exerciseIds.distinct().associateWith { exerciseId ->
            SetQueries.historyBeforeDate(sets.sets.value, exerciseId, beforeDate)
                .map { it.toProgressInput() }
        }

    private suspend fun exerciseMetaFor(exerciseIds: List<Long>): Map<Long, ExerciseMeta> {
        val ids = exerciseIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return ids.mapNotNull { exerciseId ->
            exercises.getById(exerciseId)?.let { exercise ->
                exerciseId to ExerciseMeta(name = exercise.name, isBodyweight = exercise.isBodyweight)
            }
        }.toMap()
    }

    private fun WorkoutSet.toProgressInput(): CycleProgressCalculator.SetInput =
        CycleProgressCalculator.SetInput(
            id = id,
            exerciseId = exerciseId,
            date = date,
            setNumber = setNumber,
            weightLbs = weightLbs,
            reps = reps,
            isBodyweight = isBodyweight,
            completed = completed,
        )
}

private class DemoPendingReviewRepository(reviews: List<PendingReview>) : PendingReviewRepository {
    private val _all = MutableStateFlow(reviews)
    override val unresolved: Flow<List<PendingReview>> = _all.map { list -> list.filter { !it.resolved } }
    override suspend fun add(rawInput: String, dateLogged: String) = Unit
    override suspend fun markResolved(id: Long) = Unit
}

private class DemoCardioRepository(sessions: List<CardioSession>) : CardioRepository {
    private val _all = MutableStateFlow(sessions.sortedForCardioHistory())
    override val sessions: StateFlow<List<CardioSession>> = _all.asStateFlow()
    override fun observeSessions(): Flow<List<CardioSession>> = _all
    fun observeSessionsForDate(date: String): Flow<List<CardioSession>> =
        _all.map { it.filter { session -> session.date == date } }

    override fun observeCaloriesForDate(date: String): Flow<Int> =
        _all.map { it.filter { session -> session.date == date }.sumOf { session -> session.calories ?: 0 } }

    suspend fun getAll(): List<CardioSession> = _all.value
    suspend fun getForDate(date: String): List<CardioSession> = _all.value.filter { it.date == date }
    override suspend fun refresh() = Unit

    override suspend fun saveManual(
        date: String,
        activityType: String,
        activityLabel: String?,
        durationSec: Int?,
        distanceM: Double?,
        calories: Int?,
        avgHeartRate: Int?,
        maxHeartRate: Int?,
        avgSpeed: Double?,
        avgInclinePercent: Double?,
        cadenceRpm: Int?,
        resistanceLevel: Double?,
        avgPowerWatts: Int?,
        strokeRateSpm: Int?,
        paceSecPer500m: Int?,
        floors: Int?,
        steps: Int?,
        notes: String,
    ): CardioSession =
        demoCardioNoop(
            date = date,
            activityType = activityType,
            activityLabel = activityLabel,
            durationSec = durationSec,
            distanceM = distanceM,
            calories = calories,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            avgSpeed = avgSpeed,
            avgInclinePercent = avgInclinePercent,
            cadenceRpm = cadenceRpm,
            resistanceLevel = resistanceLevel,
            avgPowerWatts = avgPowerWatts,
            strokeRateSpm = strokeRateSpm,
            paceSecPer500m = paceSecPer500m,
            floors = floors,
            steps = steps,
            source = CardioSessionSource.MANUAL,
            notes = notes,
        )

    override suspend fun saveOcrConfirmed(
        date: String,
        activityType: String,
        activityLabel: String?,
        durationSec: Int?,
        distanceM: Double?,
        calories: Int?,
        avgHeartRate: Int?,
        maxHeartRate: Int?,
        avgSpeed: Double?,
        avgInclinePercent: Double?,
        cadenceRpm: Int?,
        resistanceLevel: Double?,
        avgPowerWatts: Int?,
        strokeRateSpm: Int?,
        paceSecPer500m: Int?,
        floors: Int?,
        steps: Int?,
        machineType: String?,
        ocrConfidence: Double?,
        notes: String,
    ): CardioSession =
        demoCardioNoop(
            date = date,
            activityType = activityType,
            activityLabel = activityLabel,
            durationSec = durationSec,
            distanceM = distanceM,
            calories = calories,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            avgSpeed = avgSpeed,
            avgInclinePercent = avgInclinePercent,
            cadenceRpm = cadenceRpm,
            resistanceLevel = resistanceLevel,
            avgPowerWatts = avgPowerWatts,
            strokeRateSpm = strokeRateSpm,
            paceSecPer500m = paceSecPer500m,
            floors = floors,
            steps = steps,
            source = CardioSessionSource.OCR,
            machineType = machineType,
            ocrConfidence = ocrConfidence,
            notes = notes,
        )

    override suspend fun getById(id: Long): CardioSession? = _all.value.firstOrNull { it.id == id }
    override suspend fun update(session: CardioSession): CardioSession = session
    override suspend fun delete(id: Long) = Unit

    private fun demoCardioNoop(
        date: String,
        activityType: String,
        activityLabel: String? = null,
        durationSec: Int? = null,
        distanceM: Double? = null,
        calories: Int? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        avgSpeed: Double? = null,
        avgInclinePercent: Double? = null,
        cadenceRpm: Int? = null,
        resistanceLevel: Double? = null,
        avgPowerWatts: Int? = null,
        strokeRateSpm: Int? = null,
        paceSecPer500m: Int? = null,
        floors: Int? = null,
        steps: Int? = null,
        source: String,
        machineType: String? = null,
        ocrConfidence: Double? = null,
        notes: String = "",
    ): CardioSession =
        CardioSession(
            id = 0,
            localUuid = "demo-cardio-noop",
            date = date,
            activityType = activityType,
            activityLabel = activityLabel,
            durationSec = durationSec,
            distanceM = distanceM,
            calories = calories,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            avgSpeed = avgSpeed,
            avgInclinePercent = avgInclinePercent,
            cadenceRpm = cadenceRpm,
            resistanceLevel = resistanceLevel,
            avgPowerWatts = avgPowerWatts,
            strokeRateSpm = strokeRateSpm,
            paceSecPer500m = paceSecPer500m,
            floors = floors,
            steps = steps,
            source = source,
            machineType = machineType,
            ocrConfidence = ocrConfidence,
            notes = notes,
            createdAt = 0,
            updatedAt = 0,
        )
}

/** Process memory for the smart-suggest toggle. Recreate rebuilds DemoRepos from the seed. */
private object DemoSmartSuggest {
    private var enabled: Boolean? = null

    fun read(fallback: Boolean): Boolean = enabled ?: fallback

    fun write(value: Boolean) {
        enabled = value
    }
}

private fun List<CardioSession>.sortedForCardioHistory(): List<CardioSession> =
    sortedWith(
        compareByDescending<CardioSession> { it.date }
            .thenByDescending { it.createdAt }
            .thenByDescending { it.id },
    )
