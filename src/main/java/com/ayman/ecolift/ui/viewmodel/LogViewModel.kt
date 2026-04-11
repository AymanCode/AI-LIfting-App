package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.ai.GymContextManifestRepository
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.FuzzyMatcher
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.PendingReviewRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.TempSessionSwapRepository
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutRepository
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val exerciseRepository = ExerciseRepository(database)
    private val workoutRepository = WorkoutRepository(database)
    private val setRepository = SetRepository(database)
    private val tempSessionSwapRepository = TempSessionSwapRepository(database)
    private val pendingReviewRepository = PendingReviewRepository(database)
    private val manifestRepository = GymContextManifestRepository(application, database)

    private val currentDate = MutableStateFlow(WorkoutDates.today())
    private val exerciseInput = MutableStateFlow("")
    private val reviewsExpanded = MutableStateFlow(false)

    private val workoutDays = workoutRepository.observeAllWorkoutDays()
    private val currentDay = currentDate.flatMapLatest { workoutRepository.observeWorkoutDay(it) }
    private val currentSets = currentDate.flatMapLatest { setRepository.observeSetsForDate(it) }
    private val currentSwaps = currentDate.flatMapLatest { tempSessionSwapRepository.observeActiveForWeek(it) }

    private val uiInputs = combine(currentDate, exerciseInput, reviewsExpanded) { date, input, expanded ->
        UiInputs(date = date, input = input, expanded = expanded)
    }

    private val scheduleSnapshot = combine(workoutDays, currentDay, currentSets, currentSwaps) {
            workoutDaysList,
            workoutDay,
            setsForDay,
            swapsForWeek,
        ->
        ScheduleSnapshot(
            workoutDays = workoutDaysList,
            currentDay = workoutDay,
            currentSets = setsForDay,
            currentSwaps = swapsForWeek,
        )
    }

    private val librarySnapshot = combine(
        exerciseRepository.exercises,
        workoutRepository.cycle,
        setRepository.allSets,
        pendingReviewRepository.unresolved,
    ) { exercises, cycle, allSets, pendingReviews ->
        LibrarySnapshot(
            exercises = exercises,
            cycle = cycle,
            allSets = allSets,
            pendingReviews = pendingReviews,
        )
    }

    private val dbSnapshot = combine(scheduleSnapshot, librarySnapshot) { schedule, library ->
        DbSnapshot(
            exercises = library.exercises,
            cycle = library.cycle,
            workoutDays = schedule.workoutDays,
            currentDay = schedule.currentDay,
            currentSets = schedule.currentSets,
            currentSwaps = schedule.currentSwaps,
            allSets = library.allSets,
            pendingReviews = library.pendingReviews,
        )
    }

    val uiState: StateFlow<LogUiState> = combine(uiInputs, dbSnapshot) { inputs, snapshot ->
        buildUiState(inputs, snapshot)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogUiState(
            currentDate = WorkoutDates.today(),
            currentDateLabel = WorkoutDates.formatHeader(WorkoutDates.today()),
            cycleEnabled = false,
        ),
    )

    fun goToPreviousDay() {
        currentDate.update { WorkoutDates.addDays(it, -1) }
    }

    fun goToNextDay() {
        currentDate.update { WorkoutDates.addDays(it, 1) }
    }

    fun updateExerciseInput(value: String) {
        exerciseInput.value = value
    }

    fun addExerciseFromInput() {
        val rawInput = exerciseInput.value.trim()
        if (rawInput.isEmpty()) return
        viewModelScope.launch {
            addExercise(rawInput)
            exerciseInput.value = ""
        }
    }

    fun useSuggestion(name: String) {
        viewModelScope.launch {
            addExercise(name)
            exerciseInput.value = ""
        }
    }

    fun toggleReviewsExpanded() {
        reviewsExpanded.update { !it }
    }

    fun markReviewResolved(id: Long) {
        viewModelScope.launch {
            pendingReviewRepository.markResolved(id)
        }
    }

    fun assignCycleSlot(slotType: Int) {
        viewModelScope.launch {
            val assignedDay = workoutRepository.assignCycleSlot(currentDate.value, slotType)
            val previousOccurrence = (assignedDay.cycleSlotOccurrence ?: 1) - 1
            if (previousOccurrence <= 0) return@launch
            val previousDay = workoutRepository.getPreviousOccurrenceDay(
                date = currentDate.value,
                slotType = slotType,
                occurrence = previousOccurrence,
            )
            if (previousDay != null) {
                setRepository.cloneDay(previousDay.date, currentDate.value)
            }
            tempSessionSwapRepository.applySwapsToDate(currentDate.value, slotType)
            refreshManifest()
        }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            setRepository.addSet(currentDate.value, exerciseId)
            refreshManifest()
        }
    }

    fun adjustWeight(setId: Long, delta: Int) {
        updateSet(setId) { set ->
            set.copy(weightLbs = (set.weightLbs + delta).coerceAtLeast(0))
        }
    }

    fun updateWeight(setId: Long, input: String) {
        updateSet(setId) { set ->
            set.copy(weightLbs = input.filter { it.isDigit() }.toIntOrNull() ?: 0)
        }
    }

    fun adjustReps(setId: Long, delta: Int) {
        updateSet(setId) { set ->
            set.copy(reps = (set.reps + delta).coerceAtLeast(0))
        }
    }

    fun updateReps(setId: Long, input: String) {
        updateSet(setId) { set ->
            set.copy(reps = input.filter { it.isDigit() }.toIntOrNull() ?: 0)
        }
    }

    fun toggleBodyweight(setId: Long) {
        updateSet(setId) { set ->
            val turningOn = !set.isBodyweight
            set.copy(isBodyweight = turningOn, weightLbs = if (turningOn) 0 else set.weightLbs)
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            setRepository.deleteSet(setId)
            refreshManifest()
        }
    }

    fun toggleCompleted(setId: Long) {
        updateSet(setId) { set ->
            set.copy(completed = !set.completed)
        }
    }

    private fun updateSet(setId: Long, transform: (WorkoutSet) -> WorkoutSet) {
        viewModelScope.launch {
            val current = setRepository.getById(setId) ?: return@launch
            setRepository.updateSet(transform(current))
            refreshManifest()
        }
    }

    private suspend fun addExercise(rawInput: String) {
        val allExercises = exerciseRepository.getAll()
        val normalized = exerciseRepository.normalizeName(rawInput)
        val exactMatch = exerciseRepository.findExact(rawInput)
        val rankedMatches = allExercises
            .map { exercise -> exercise to FuzzyMatcher.levenshteinDistance(normalized, exercise.name) }
            .sortedWith(compareBy<Pair<Exercise, Int>> { it.second }.thenBy { it.first.name })
        val bestMatch = rankedMatches.firstOrNull()
        val exercise = when {
            exactMatch != null -> exactMatch
            bestMatch != null && bestMatch.second <= 2 -> bestMatch.first
            else -> {
                if (bestMatch != null && bestMatch.second in 3..5) {
                    pendingReviewRepository.add(rawInput, currentDate.value)
                }
                exerciseRepository.getOrCreate(rawInput)
            }
        }
        setRepository.addSet(currentDate.value, exercise.id)
        refreshManifest()
    }

    private suspend fun refreshManifest() {
        manifestRepository.refresh()
    }

    private fun buildUiState(inputs: UiInputs, snapshot: DbSnapshot): LogUiState {
        val exerciseMap = snapshot.exercises.associateBy(Exercise::id)
        val suggestions = buildSuggestions(inputs.input, snapshot.exercises)
        val groupedExercises = snapshot.currentSets
            .groupBy(WorkoutSet::exerciseId)
            .entries
            .sortedBy { entry -> entry.value.minOfOrNull(WorkoutSet::id) ?: Long.MAX_VALUE }
            .mapNotNull { entry ->
                val exercise = exerciseMap[entry.key] ?: return@mapNotNull null
                LogExerciseUi(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    lastSessionHint = buildLastSessionHint(
                        exerciseId = exercise.id,
                        currentDate = inputs.date,
                        allSets = snapshot.allSets,
                    ),
                    sets = entry.value
                        .sortedBy(WorkoutSet::setNumber)
                        .map { set ->
                            LogSetUi(
                                id = set.id,
                                setNumber = set.setNumber,
                                weightLbs = set.weightLbs,
                                reps = set.reps,
                                isBodyweight = set.isBodyweight,
                                completed = set.completed,
                            )
                        },
                )
            }
        val expectedType = buildExpectedType(
            currentDate = inputs.date,
            cycle = snapshot.cycle,
            workoutDays = snapshot.workoutDays,
        )
        val cycleOptions = if (snapshot.cycle.isActive && snapshot.currentDay?.cycleSlotType == null) {
            (0 until snapshot.cycle.numTypes).map { type ->
                val nextOccurrence = snapshot.workoutDays
                    .filter { it.date < inputs.date && it.cycleSlotType == type }
                    .maxOfOrNull { it.cycleSlotOccurrence ?: 0 }
                    ?.plus(1)
                    ?: 1
                CycleSlotUi(
                    type = type,
                    occurrence = nextOccurrence,
                    label = "${cycleTypeLabel(type)} | $nextOccurrence",
                    shortLabel = cycleTypeShortLabel(type, nextOccurrence),
                    isExpected = type == expectedType,
                )
            }
        } else {
            emptyList()
        }

        return LogUiState(
            currentDate = inputs.date,
            currentDateLabel = WorkoutDates.formatHeader(inputs.date),
            cycleEnabled = snapshot.cycle.isActive,
            cycleSlot = if (snapshot.currentDay?.cycleSlotType != null && snapshot.currentDay.cycleSlotOccurrence != null) {
                CycleSlotUi(
                    type = snapshot.currentDay.cycleSlotType,
                    occurrence = snapshot.currentDay.cycleSlotOccurrence,
                    label = "${cycleTypeLabel(snapshot.currentDay.cycleSlotType)} | ${snapshot.currentDay.cycleSlotOccurrence}",
                    shortLabel = cycleTypeShortLabel(
                        snapshot.currentDay.cycleSlotType,
                        snapshot.currentDay.cycleSlotOccurrence,
                    ),
                )
            } else {
                null
            },
            cycleOptions = cycleOptions,
            swapNotices = buildSwapNotices(
                currentDay = snapshot.currentDay,
                activeSwaps = snapshot.currentSwaps,
                exerciseMap = exerciseMap,
            ),
            exercises = groupedExercises,
            exerciseInput = inputs.input,
            inlineSuggestions = suggestions.map { it.exercise.name },
            pendingReviews = snapshot.pendingReviews,
            reviewsExpanded = inputs.expanded,
        )
    }

    private fun buildLastSessionHint(
        exerciseId: Long,
        currentDate: String,
        allSets: List<WorkoutSet>,
    ): String? {
        val previousSets = allSets.filter { it.exerciseId == exerciseId && it.date < currentDate }
        val lastDate = previousSets.maxOfOrNull(WorkoutSet::date) ?: return null
        val lastSessionSets = previousSets.filter { it.date == lastDate }
        if (lastSessionSets.isEmpty()) return null
        val maxWeight = lastSessionSets.maxOf(WorkoutSet::weightLbs)
        val maxLabel = if (maxWeight == 0) "BW" else "$maxWeight lbs"
        return "${lastSessionSets.size} sets | Max $maxLabel"
    }

    private fun buildExpectedType(
        currentDate: String,
        cycle: Cycle,
        workoutDays: List<WorkoutDay>,
    ): Int? {
        if (!cycle.isActive || cycle.numTypes <= 0) return null
        if (cycle.nextSessionType != null) {
            return cycle.nextSessionType.takeIf { it in 0 until cycle.numTypes }
        }
        val latestAssigned = workoutDays
            .filter { it.date < currentDate && it.cycleSlotType != null }
            .maxByOrNull(WorkoutDay::date)
        return if (latestAssigned?.cycleSlotType != null) {
            (latestAssigned.cycleSlotType + 1) % cycle.numTypes
        } else {
            0
        }
    }

    private fun buildSuggestions(
        input: String,
        exercises: List<Exercise>,
    ): List<com.ayman.ecolift.data.ExerciseSuggestion> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()
        return exercises
            .map { exercise ->
                com.ayman.ecolift.data.ExerciseSuggestion(
                    exercise = exercise,
                    distance = FuzzyMatcher.levenshteinDistance(trimmed, exercise.name),
                )
            }
            .filter { it.distance <= 2 }
            .sortedWith(compareBy<com.ayman.ecolift.data.ExerciseSuggestion> { it.distance }.thenBy { it.exercise.name })
            .take(5)
    }

    private fun buildSwapNotices(
        currentDay: WorkoutDay?,
        activeSwaps: List<com.ayman.ecolift.data.TempSessionSwap>,
        exerciseMap: Map<Long, Exercise>,
    ): List<SwapNoticeUi> {
        val slotType = currentDay?.cycleSlotType ?: return emptyList()
        return activeSwaps
            .filter { it.sourceSlotType == slotType || it.targetSlotType == slotType }
            .mapNotNull { swap ->
                val isTargetDay = swap.targetSlotType == slotType
                val incoming = if (isTargetDay) {
                    exerciseMap[swap.sourceExerciseId]
                } else {
                    exerciseMap[swap.targetExerciseId]
                } ?: return@mapNotNull null
                val outgoing = if (isTargetDay) {
                    exerciseMap[swap.targetExerciseId]
                } else {
                    exerciseMap[swap.sourceExerciseId]
                } ?: return@mapNotNull null
                SwapNoticeUi(
                    title = if (isTargetDay) "Previously swapped" else "Temporary swap active",
                    detail = if (isTargetDay) {
                        "${incoming.name} is scheduled for today instead of ${outgoing.name}."
                    } else {
                        "${incoming.name} replaces ${outgoing.name} for this session."
                    },
                )
            }
    }
}

private data class UiInputs(
    val date: String,
    val input: String,
    val expanded: Boolean,
)

private data class ScheduleSnapshot(
    val workoutDays: List<WorkoutDay>,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
    val currentSwaps: List<com.ayman.ecolift.data.TempSessionSwap>,
)

private data class LibrarySnapshot(
    val exercises: List<Exercise>,
    val cycle: Cycle,
    val allSets: List<WorkoutSet>,
    val pendingReviews: List<PendingReview>,
)

private data class DbSnapshot(
    val exercises: List<Exercise>,
    val cycle: Cycle,
    val workoutDays: List<WorkoutDay>,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
    val currentSwaps: List<com.ayman.ecolift.data.TempSessionSwap>,
    val allSets: List<WorkoutSet>,
    val pendingReviews: List<PendingReview>,
)
