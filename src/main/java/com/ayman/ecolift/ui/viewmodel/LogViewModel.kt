package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
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
    private val exerciseRepository = ExerciseRepository(database.exerciseDao())
    private val workoutRepository = WorkoutRepository(database)
    private val setRepository = SetRepository(database)
    private val pendingReviewRepository = PendingReviewRepository(database)

    private val currentDate = MutableStateFlow(WorkoutDates.today())
    private val _sessionSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private val exerciseInput = MutableStateFlow("")
    private val reviewsExpanded = MutableStateFlow(false)
    private val restStopwatchSeconds = MutableStateFlow<Int?>(null)
    private val _restTimes = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private var stopwatchJob: kotlinx.coroutines.Job? = null
    private var lastCompletedSetId: Long? = null

    private val _exerciseHints = MutableStateFlow<Map<Long, String?>>(emptyMap())
    private val _exercisePBs = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    private val workoutDays = workoutRepository.observeAllWorkoutDays()
    private val currentDay = currentDate.flatMapLatest { workoutRepository.observeWorkoutDay(it) }

    init {
        viewModelScope.launch {
            currentDate.collect { date ->
                val sets = setRepository.getSetsForDate(date)
                _sessionSets.value = sets
                updateHistoricalHints(date, sets)
            }
        }
    }

    private suspend fun updateHistoricalHints(date: String, currentSets: List<WorkoutSet>) {
        val exerciseIds = currentSets.map { it.exerciseId }.distinct()
        val hints = mutableMapOf<Long, String?>()
        val pbs = mutableMapOf<Long, Boolean>()

        exerciseIds.forEach { id ->
            val history = setRepository.getRecentHistoryForExercise(id, date)
            val lastDate = history.maxOfOrNull { it.date }
            val lastSessionSets = history.filter { it.date == lastDate }
            
            hints[id] = if (lastSessionSets.isNotEmpty()) {
                val maxWeight = lastSessionSets.maxOf { it.weightLbs ?: 0 }
                val maxLabel = if (maxWeight == 0) "BW" else "$maxWeight lbs"
                "${lastSessionSets.size} sets | Max $maxLabel"
            } else null

            val currentMax1RM = currentSets.filter { it.exerciseId == id }.maxOfOrNull { 
                val reps = (it.reps ?: 0).coerceIn(0, 36)
                if (reps == 0) 0f else (it.weightLbs ?: 0) / (1.0278f - 0.0278f * reps)
            } ?: 0f

            val allTimeMaxWeight = setRepository.getMaxWeightBeforeDate(id, date) ?: 0
            pbs[id] = currentMax1RM > 0 && currentSets.any { it.exerciseId == id && (it.weightLbs ?: 0) > allTimeMaxWeight }
        }
        _exerciseHints.value = hints
        _exercisePBs.value = pbs
    }

    private val uiInputs = combine(
        currentDate,
        exerciseInput,
        reviewsExpanded,
        restStopwatchSeconds
    ) { date, input, expanded, stopwatch ->
        UiInputs(date = date, input = input, expanded = expanded, restStopwatch = stopwatch)
    }

    private val scheduleSnapshot = combine(workoutDays, currentDay, _sessionSets) {
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
        workoutRepository.observeCycleSlots(),
        pendingReviewRepository.unresolved,
    ) { exercises, cycle, slots, pendingReviews ->
        LibrarySnapshot(
            exercises = exercises,
            cycle = cycle,
            slots = slots,
            pendingReviews = pendingReviews,
        )
    }

    private val dbSnapshot = combine(scheduleSnapshot, librarySnapshot) { schedule, library ->
        DbSnapshot(
            exercises = library.exercises,
            cycle = library.cycle,
            slots = library.slots,
            workoutDays = schedule.workoutDays,
            currentDay = schedule.currentDay,
            currentSets = schedule.currentSets,
            pendingReviews = library.pendingReviews,
        )
    }

    val uiState: StateFlow<LogUiState> = combine(uiInputs, dbSnapshot, _restTimes) { inputs, snapshot, restTimes ->
        buildUiState(inputs, snapshot, restTimes)
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

    fun assignCycleSlot(slotId: Long) {
        viewModelScope.launch {
            val assignedDay = workoutRepository.assignCycleSlot(currentDate.value, slotId)
            val previousOccurrence = (assignedDay.cycleSlotOccurrence ?: 1) - 1
            if (previousOccurrence > 0) {
                val previousDay = workoutRepository.getPreviousOccurrenceDayForSlot(
                    date = currentDate.value,
                    slotId = slotId,
                    occurrence = previousOccurrence,
                )
                if (previousDay != null) {
                    setRepository.cloneDay(previousDay.date, currentDate.value)
                }
            }
            // Always refresh session sets from DB after slot assignment/cloning
            _sessionSets.value = setRepository.getSetsForDate(currentDate.value)
        }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            val newSet = setRepository.addSet(currentDate.value, exerciseId)
            _sessionSets.update { it + newSet }
        }
    }

    fun updateWeight(setId: Long, input: String) {
        updateSetLocal(setId) { set ->
            val value = input.filter { it.isDigit() }.toIntOrNull()
            set.copy(weightLbs = value, completed = false)
        }
    }

    fun adjustWeight(setId: Long, delta: Int) {
        updateSetLocal(setId) { set ->
            val current = set.weightLbs ?: 0
            set.copy(weightLbs = (current + delta).coerceAtLeast(0), completed = false)
        }
    }

    fun updateReps(setId: Long, input: String) {
        updateSetLocal(setId) { set ->
            val value = input.filter { it.isDigit() }.toIntOrNull()
            set.copy(reps = value, completed = false)
        }
    }

    fun adjustReps(setId: Long, delta: Int) {
        updateSetLocal(setId) { set ->
            val current = set.reps ?: 0
            set.copy(reps = (current + delta).coerceAtLeast(0), completed = false)
        }
    }

    fun toggleBodyweight(setId: Long) {
        updateSetLocal(setId) { set ->
            val turningOn = !set.isBodyweight
            set.copy(isBodyweight = turningOn, weightLbs = if (turningOn) 0 else set.weightLbs, completed = false)
        }
    }

    fun deleteSet(setId: Long) {
        _sessionSets.update { list -> list.filter { it.id != setId } }
        if (setId > 0) {
            viewModelScope.launch {
                setRepository.deleteSet(setId)
            }
        }
    }

    fun deleteExercise(exerciseId: Long) {
        viewModelScope.launch {
            exerciseRepository.deleteExercise(exerciseId)
        }
    }

    fun updateExerciseName(exerciseId: Long, newName: String) {
        viewModelScope.launch {
            exerciseRepository.updateName(exerciseId, newName)
        }
    }

    fun toggleCompleted(setId: Long) {
        val set = _sessionSets.value.find { it.id == setId } ?: return
        val newCompleted = !set.completed

        if (newCompleted) {
            // Record elapsed rest for the previous completed set
            lastCompletedSetId?.let { prevId ->
                val elapsed = restStopwatchSeconds.value ?: 0
                if (elapsed > 0) _restTimes.update { it + (prevId to elapsed) }
            }
            lastCompletedSetId = setId
            startStopwatch()
        } else {
            if (lastCompletedSetId == setId) {
                stopStopwatch()
                lastCompletedSetId = null
            }
        }

        updateSetLocal(setId) { it.copy(completed = newCompleted) }
    }

    private fun updateSetLocal(setId: Long, transform: (WorkoutSet) -> WorkoutSet) {
        _sessionSets.update { list ->
            list.map { if (it.id == setId) transform(it) else it }
        }
        viewModelScope.launch {
            _sessionSets.value.find { it.id == setId }?.let { updatedSet ->
                if (updatedSet.id > 0) {
                    setRepository.updateSet(updatedSet)
                }
            }
        }
    }

    private fun startStopwatch() {
        stopwatchJob?.cancel()
        restStopwatchSeconds.value = 0
        stopwatchJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                restStopwatchSeconds.update { (it ?: 0) + 1 }
            }
        }
    }

    fun cancelRestTimer() {
        stopStopwatch()
    }

    private fun stopStopwatch() {
        stopwatchJob?.cancel()
        restStopwatchSeconds.value = null
    }

    private suspend fun addExercise(rawInput: String) {
        val exactMatch = exerciseRepository.findExact(rawInput)
        val rankedMatches = exerciseRepository.suggestions(rawInput, maxDistance = 5, limit = 5)
        val bestMatch = rankedMatches.firstOrNull()
        val exercise = when {
            exactMatch != null -> exactMatch
            bestMatch != null && bestMatch.distance <= 2 -> bestMatch.exercise
            else -> {
                if (bestMatch != null && bestMatch.distance in 3..5) {
                    pendingReviewRepository.add(rawInput, currentDate.value)
                }
                exerciseRepository.getOrCreate(rawInput)
            }
        }
        addSet(exercise.id)
    }

    private fun buildUiState(inputs: UiInputs, snapshot: DbSnapshot, restTimes: Map<Long, Int> = emptyMap()): LogUiState {
        val exerciseMap = snapshot.exercises.associateBy(Exercise::id)
        val suggestions = buildSuggestions(inputs.input, snapshot.exercises)
        
        // Quick-add: Removed as requested
        val quickAdd = emptyList<ExerciseChipUi>()

        val groupedExercises = snapshot.currentSets
            .groupBy(WorkoutSet::exerciseId)
            .entries
            .sortedByDescending { entry -> entry.value.maxOfOrNull(WorkoutSet::id) ?: 0L }
            .mapNotNull { entry ->
                val exercise = exerciseMap[entry.key] ?: return@mapNotNull null
                val sets = entry.value.sortedBy(WorkoutSet::setNumber)
                
                // Calculate Est. 1RM: Brzycki Formula
                val currentMax1RM = sets.maxOfOrNull { 
                    val reps = (it.reps ?: 0).coerceIn(0, 36)
                    if (reps == 0) 0f else (it.weightLbs ?: 0) / (1.0278f - 0.0278f * reps)
                }?.toInt() ?: 0

                LogExerciseUi(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    muscleGroups = exercise.muscleGroups,
                    lastSessionHint = _exerciseHints.value[exercise.id],
                    sets = sets.map { set ->
                        LogSetUi(
                            id = set.id,
                            exerciseId = set.exerciseId,
                            setNumber = set.setNumber,
                            weightLbs = set.weightLbs,
                            reps = set.reps,
                            isBodyweight = set.isBodyweight,
                            completed = set.completed,
                            restAfterSeconds = restTimes[set.id],
                        )
                    },
                    estimated1RM = currentMax1RM,
                    isNewPB = _exercisePBs.value[exercise.id] ?: false
                )
            }
        
        val expectedSlotId = buildExpectedSlotId(
            currentDate = inputs.date,
            slots = snapshot.slots,
            workoutDays = snapshot.workoutDays,
        )
        val cycleOptions = if (snapshot.cycle.isActive) {
            snapshot.slots.map { slot ->
                val nextOccurrence = snapshot.workoutDays
                    .filter { it.date < inputs.date && it.cycleSlotId == slot.id }
                    .maxOfOrNull { it.cycleSlotOccurrence ?: 0 }
                    ?.plus(1)
                    ?: 1
                CycleSlotUi(
                    type = slot.id.toInt(),
                    occurrence = nextOccurrence,
                    label = "${slot.name} | $nextOccurrence",
                    shortLabel = "${slot.name.take(1)}$nextOccurrence",
                    isExpected = slot.id == expectedSlotId,
                    isSelected = snapshot.currentDay?.cycleSlotId == slot.id
                )
            }
        } else {
            emptyList()
        }

        return LogUiState(
            currentDate = inputs.date,
            currentDateLabel = WorkoutDates.formatHeader(inputs.date),
            cycleEnabled = snapshot.cycle.isActive,
            cycleSlot = if (snapshot.currentDay?.cycleSlotId != null && snapshot.currentDay.cycleSlotOccurrence != null) {
                val slot = snapshot.slots.find { it.id == snapshot.currentDay.cycleSlotId }
                CycleSlotUi(
                    type = snapshot.currentDay.cycleSlotId.toInt(),
                    occurrence = snapshot.currentDay.cycleSlotOccurrence,
                    label = "${slot?.name ?: "Unknown"} | ${snapshot.currentDay.cycleSlotOccurrence}",
                    shortLabel = "${slot?.name?.take(1) ?: "U"}${snapshot.currentDay.cycleSlotOccurrence}",
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
            restStopwatchSeconds = inputs.restStopwatch,
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
        val maxWeight = lastSessionSets.maxOf { it.weightLbs ?: 0 }
        val maxLabel = if (maxWeight == 0) "BW" else "$maxWeight lbs"
        return "${lastSessionSets.size} sets | Max $maxLabel"
    }

    private fun buildExpectedSlotId(
        currentDate: String,
        slots: List<CycleSlot>,
        workoutDays: List<WorkoutDay>,
    ): Long? {
        if (slots.isEmpty()) return null
        val latestAssigned = workoutDays
            .filter { it.date < currentDate && it.cycleSlotId != null }
            .maxByOrNull(WorkoutDay::date)
        return if (latestAssigned?.cycleSlotId != null) {
            val currentIndex = slots.indexOfFirst { it.id == latestAssigned.cycleSlotId }
            if (currentIndex == -1) slots.first().id
            else slots[(currentIndex + 1) % slots.size].id
        } else {
            slots.first().id
        }
    }

    private fun buildSuggestions(
        input: String,
        exercises: List<Exercise>,
    ): List<com.ayman.ecolift.data.ExerciseSuggestion> {
        val normalized = exerciseRepository.normalizeName(input)
        if (normalized.isEmpty()) return emptyList()
        val normalizedLower = normalized.lowercase()
        val candidatePool = exercises.asSequence()
            .filter { exercise ->
                val name = exercise.name.lowercase()
                name.startsWith(normalizedLower) ||
                    name.contains(" $normalizedLower") ||
                    (normalizedLower.length >= 3 && name.contains(normalizedLower))
            }
            .take(24)
            .toList()
            .ifEmpty { exercises.take(24) }

        return candidatePool
            .map { exercise ->
                com.ayman.ecolift.data.ExerciseSuggestion(
                    exercise = exercise,
                    distance = FuzzyMatcher.levenshteinDistance(normalizedLower, exercise.name.lowercase()),
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
    val restStopwatch: Int?,
)

private data class ScheduleSnapshot(
    val workoutDays: List<WorkoutDay>,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
)

private data class LibrarySnapshot(
    val exercises: List<Exercise>,
    val cycle: Cycle,
    val slots: List<CycleSlot>,
    val pendingReviews: List<PendingReview>,
)

private data class DbSnapshot(
    val exercises: List<Exercise>,
    val cycle: Cycle,
    val slots: List<CycleSlot>,
    val workoutDays: List<WorkoutDay>,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
    val pendingReviews: List<PendingReview>,
)
