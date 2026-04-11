package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.FuzzyMatcher
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.PendingReviewRepository
import com.ayman.ecolift.data.SetRepository
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
    private val pendingReviewRepository = PendingReviewRepository(database)

    private val currentDate = MutableStateFlow(WorkoutDates.today())
    private val exerciseInput = MutableStateFlow("")
    private val reviewsExpanded = MutableStateFlow(false)
    private val restTimerSeconds = MutableStateFlow<Int?>(null)
    private var timerJob: kotlinx.coroutines.Job? = null

    private val workoutDays = workoutRepository.observeAllWorkoutDays()
    private val currentDay = currentDate.flatMapLatest { workoutRepository.observeWorkoutDay(it) }
    private val currentSets = currentDate.flatMapLatest { setRepository.observeSetsForDate(it) }

    private val uiInputs = combine(
        currentDate,
        exerciseInput,
        reviewsExpanded,
        restTimerSeconds
    ) { date, input, expanded, timer ->
        UiInputs(date = date, input = input, expanded = expanded, restTimer = timer)
    }

    private val scheduleSnapshot = combine(workoutDays, currentDay, currentSets) {
            workoutDaysList,
            workoutDay,
            setsForDay,
        ->
        ScheduleSnapshot(
            workoutDays = workoutDaysList,
            currentDay = workoutDay,
            currentSets = setsForDay,
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
        }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            setRepository.addSet(currentDate.value, exerciseId)
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
        }
    }

    fun updateExerciseName(exerciseId: Long, newName: String) {
        viewModelScope.launch {
            exerciseRepository.updateName(exerciseId, newName)
        }
    }

    fun toggleCompleted(setId: Long) {
        updateSet(setId) { set ->
            val newCompleted = !set.completed
            if (newCompleted) {
                startRestTimer(90)
            }
            set.copy(completed = newCompleted)
        }
    }

    private fun startRestTimer(seconds: Int) {
        timerJob?.cancel()
        restTimerSeconds.value = seconds
        timerJob = viewModelScope.launch {
            while ((restTimerSeconds.value ?: 0) > 0) {
                kotlinx.coroutines.delay(1000)
                restTimerSeconds.update { (it ?: 0) - 1 }
            }
            restTimerSeconds.value = null
        }
    }

    fun cancelRestTimer() {
        timerJob?.cancel()
        restTimerSeconds.value = null
    }

    private fun updateSet(setId: Long, transform: (WorkoutSet) -> WorkoutSet) {
        viewModelScope.launch {
            val current = setRepository.getById(setId) ?: return@launch
            setRepository.updateSet(transform(current))
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
    }

    private fun buildUiState(inputs: UiInputs, snapshot: DbSnapshot): LogUiState {
        val exerciseMap = snapshot.exercises.associateBy(Exercise::id)
        val suggestions = buildSuggestions(inputs.input, snapshot.exercises)
        
        // Quick-add: Top 4 most recently used exercises NOT in current session
        val currentExerciseIds = snapshot.currentSets.map { it.exerciseId }.toSet()
        val quickAdd = snapshot.allSets
            .filter { it.exerciseId !in currentExerciseIds }
            .groupBy { it.exerciseId }
            .map { (id, sets) -> id to sets.maxOf { it.date } }
            .sortedByDescending { it.second }
            .take(4)
            .mapNotNull { (id, _) -> 
                exerciseMap[id]?.let { ExerciseChipUi(it.id, it.name) } 
            }

        val groupedExercises = snapshot.currentSets
            .groupBy(WorkoutSet::exerciseId)
            .entries
            .sortedBy { entry -> entry.value.minOfOrNull(WorkoutSet::id) ?: Long.MAX_VALUE }
            .mapNotNull { entry ->
                val exercise = exerciseMap[entry.key] ?: return@mapNotNull null
                val sets = entry.value.sortedBy(WorkoutSet::setNumber)
                
                // Calculate Est. 1RM: Brzycki Formula
                val currentMax1RM = sets.maxOfOrNull { 
                    if (it.reps == 0) 0f else it.weightLbs / (1.0278f - 0.0278f * it.reps)
                }?.toInt() ?: 0
                
                // Check for PB
                val previousSets = snapshot.allSets.filter { it.exerciseId == exercise.id && it.date < inputs.date }
                val allTimeMax1RM = previousSets.maxOfOrNull { 
                    if (it.reps == 0) 0f else it.weightLbs / (1.0278f - 0.0278f * it.reps)
                }?.toInt() ?: 0

                LogExerciseUi(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    lastSessionHint = buildLastSessionHint(
                        exerciseId = exercise.id,
                        currentDate = inputs.date,
                        allSets = snapshot.allSets,
                    ),
                    sets = sets.map { set ->
                        LogSetUi(
                            id = set.id,
                            setNumber = set.setNumber,
                            weightLbs = set.weightLbs,
                            reps = set.reps,
                            isBodyweight = set.isBodyweight,
                            completed = set.completed,
                        )
                    },
                    estimated1RM = currentMax1RM,
                    isNewPB = currentMax1RM > allTimeMax1RM && allTimeMax1RM > 0
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
            alternativeForDate = snapshot.currentDay?.alternativeForDate,
            cycleOptions = cycleOptions,
            exercises = groupedExercises,
            exerciseInput = inputs.input,
            inlineSuggestions = suggestions.map { it.exercise.name },
            quickAddExercises = quickAdd,
            pendingReviews = snapshot.pendingReviews,
            reviewsExpanded = inputs.expanded,
            restTimerSeconds = inputs.restTimer,
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
}

private data class UiInputs(
    val date: String,
    val input: String,
    val expanded: Boolean,
    val restTimer: Int?,
)

private data class ScheduleSnapshot(
    val workoutDays: List<WorkoutDay>,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
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
    val allSets: List<WorkoutSet>,
    val pendingReviews: List<PendingReview>,
)
