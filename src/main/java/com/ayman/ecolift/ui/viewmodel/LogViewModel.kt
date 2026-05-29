package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.BuildConfig
import com.ayman.ecolift.ai.ExerciseMuscleClassifier
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.FuzzyMatcher
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.PendingReviewRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.WeightLbs
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
    private val muscleClassifier = ExerciseMuscleClassifier(
        apiKey = BuildConfig.GROQ_API_KEY,
        baseUrl = BuildConfig.GROQ_API_BASE_URL,
        model = BuildConfig.GROQ_MODEL,
    )

    private val currentDate = MutableStateFlow(WorkoutDates.today())
    private val _sessionSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private val exerciseInput = MutableStateFlow("")
    private val predictiveSuggestions = MutableStateFlow<List<Exercise>>(emptyList())
    private val reviewsExpanded = MutableStateFlow(false)
    private val _smartAdjustments = MutableStateFlow<Map<Long, SmartSetAdjustment>>(emptyMap())
    private var activeRest: ActiveRest? = null

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
                val maxLabel = if (maxWeight == 0) "BW" else "${WeightLbs.formatStored(maxWeight)} lbs"
                "${lastSessionSets.size} sets | Max $maxLabel"
            } else null

            val currentMax1RM = currentSets.filter { it.exerciseId == id }.maxOfOrNull { 
                val reps = (it.reps ?: 0).coerceIn(0, 36)
                if (reps == 0) 0f else WeightLbs.toLbs(it.weightLbs).toFloat() / (1.0278f - 0.0278f * reps)
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
        predictiveSuggestions,
        reviewsExpanded,
        _smartAdjustments
    ) { date, input, suggestions, expanded, smartAdjustments ->
        UiInputs(
            date = date,
            input = input,
            suggestions = suggestions,
            expanded = expanded,
            smartAdjustments = smartAdjustments,
        )
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
        finishActiveWorkoutDate()
        currentDate.update { WorkoutDates.addDays(it, -1) }
    }

    fun goToNextDay() {
        finishActiveWorkoutDate()
        currentDate.update { WorkoutDates.addDays(it, 1) }
    }

    fun updateExerciseInput(value: String) {
        exerciseInput.value = value
        if (value.isNotBlank()) {
            viewModelScope.launch {
                predictiveSuggestions.value = exerciseRepository.getPredictive(value)
            }
        } else {
            predictiveSuggestions.value = emptyList()
        }
    }

    fun addExerciseFromInput() {
        val rawInput = exerciseInput.value.trim()
        if (rawInput.isEmpty()) return
        viewModelScope.launch {
            addExercise(rawInput)
            exerciseInput.value = ""
            predictiveSuggestions.value = emptyList()
        }
    }

    fun useSuggestion(exercise: com.ayman.ecolift.data.Exercise) {
        viewModelScope.launch {
            addExerciseSession(exercise.id)
            exerciseInput.value = ""
            predictiveSuggestions.value = emptyList()
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
            
            // 1. Check if there are manually saved exercises for this split
            val savedExercises = database.splitExerciseDao().getForSplit(slotId)
            
            if (savedExercises.isNotEmpty()) {
                // Priority 1: Load from manually saved template
                setRepository.addSetsForExercises(currentDate.value, savedExercises.map { it.exerciseId })
            } else {
                // Priority 2: Clone from the most recent historical occurrence
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
            }

            // Always refresh session sets from DB after slot assignment/cloning
            val refreshedSets = setRepository.getSetsForDate(currentDate.value)
            _sessionSets.value = refreshedSets
            updateHistoricalHints(currentDate.value, refreshedSets)
        }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            val date = currentDate.value
            val newSet = setRepository.addSet(date, exerciseId)
            _sessionSets.update { it + newSet }
            updateHistoricalHints(date, _sessionSets.value)
        }
    }

    fun updateWeight(setId: Long, input: String) {
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearSmartAdjustment(setId)
        updateSetLocal(setId) { set ->
            val value = WeightLbs.parseInputToStorage(input)
            set.copy(weightLbs = value, completed = false)
        }
        _sessionSets.value.find { it.id == setId }?.let { source ->
            updateSmartAdjustmentsFrom(source, before)
        }
    }

    fun adjustWeight(setId: Long, delta: Int) {
        if (adjustSmartWeight(setId, delta)) return
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearSmartAdjustment(setId)
        updateSetLocal(setId) { set ->
            val current = set.weightLbs ?: 0
            set.copy(weightLbs = (current + delta).coerceAtLeast(0), completed = false)
        }
        _sessionSets.value.find { it.id == setId }?.let { source ->
            updateSmartAdjustmentsFrom(source, before)
        }
    }

    fun updateReps(setId: Long, input: String) {
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearSmartAdjustment(setId)
        updateSetLocal(setId) { set ->
            val value = input.filter { it.isDigit() }.toIntOrNull()
            set.copy(reps = value, completed = false)
        }
        _sessionSets.value.find { it.id == setId }?.let { source ->
            updateSmartAdjustmentsFrom(source, before)
        }
    }

    fun adjustReps(setId: Long, delta: Int) {
        if (adjustSmartReps(setId, delta)) return
        beginSetEntry(setId)
        val before = _sessionSets.value.find { it.id == setId }
        clearSmartAdjustment(setId)
        updateSetLocal(setId) { set ->
            val current = set.reps ?: 0
            set.copy(reps = (current + delta).coerceAtLeast(0), completed = false)
        }
        _sessionSets.value.find { it.id == setId }?.let { source ->
            updateSmartAdjustmentsFrom(source, before)
        }
    }

    fun toggleBodyweight(setId: Long) {
        beginSetEntry(setId)
        clearSmartAdjustment(setId)
        clearSmartAdjustmentsFromSource(setId)
        updateSetLocal(setId) { set ->
            val turningOn = !set.isBodyweight
            set.copy(isBodyweight = turningOn, weightLbs = if (turningOn) 0 else set.weightLbs, completed = false)
        }
    }

    fun deleteSet(setId: Long) {
        if (activeRest?.completedSetId == setId) activeRest = null
        clearSmartAdjustment(setId)
        clearSmartAdjustmentsFromSource(setId)
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
        beginSetEntry(setId)
        applySmartAdjustment(setId)
        val set = _sessionSets.value.find { it.id == setId } ?: return
        val newCompleted = !set.completed

        updateSetLocal(setId) { it.copy(completed = newCompleted) }

        if (newCompleted) {
            val hasNextSameExercise = _sessionSets.value.any {
                it.exerciseId == set.exerciseId && it.id != set.id && !it.completed
            }
            activeRest = if (hasNextSameExercise) {
                ActiveRest(
                    exerciseId = set.exerciseId,
                    completedSetId = set.id,
                    startedAtMillis = System.currentTimeMillis(),
                )
            } else {
                null
            }
        } else if (activeRest?.completedSetId == set.id) {
            activeRest = null
        }
    }

    fun focusSetInput(setId: Long) {
        clearSmartAdjustment(setId)
        beginSetEntry(setId)
    }

    fun beginSetEntry(setId: Long) {
        val rest = activeRest ?: return
        val targetSet = _sessionSets.value.find { it.id == setId } ?: return
        if (targetSet.completed || targetSet.id == rest.completedSetId || targetSet.exerciseId != rest.exerciseId) return

        val elapsedSeconds = ((System.currentTimeMillis() - rest.startedAtMillis) / 1000)
            .toInt()
            .coerceAtLeast(1)
        activeRest = null
        updateSetLocal(setId) { it.copy(restTimeSeconds = elapsedSeconds) }
    }

    fun finishExercise(exerciseId: Long) {
        applySmartAdjustmentsForExercise(exerciseId)
        if (activeRest?.exerciseId == exerciseId) {
            activeRest = null
        }
    }

    private fun updateSmartAdjustmentsFrom(source: WorkoutSet, beforeSourceEdit: WorkoutSet?) {
        val siblingSets = _sessionSets.value
            .filter { it.exerciseId == source.exerciseId && it.id != source.id && !it.completed }
            .sortedBy { it.setNumber }
        if (siblingSets.isEmpty()) return

        val existingFromSource = _smartAdjustments.value.values.firstOrNull { it.sourceSetId == source.id }
        val sourceBaselineReps = existingFromSource?.sourceBaselineReps ?: beforeSourceEdit?.reps ?: source.reps
        val hasSuggestionValue = source.weightLbs != null || source.reps != null
        if (!hasSuggestionValue) {
            clearSmartAdjustmentsFromSource(source.id)
            return
        }

        _smartAdjustments.update { current ->
            val next = current
                .filterValues { it.sourceSetId != source.id }
                .filterKeys { it != source.id }
                .toMutableMap()

            siblingSets.forEach { target ->
                next[target.id] = SmartSetAdjustment(
                    setId = target.id,
                    sourceSetId = source.id,
                    sourceBaselineReps = sourceBaselineReps,
                    suggestedWeightLbs = source.weightLbs,
                    suggestedReps = calculateSmartSuggestedReps(
                        sourceNewReps = source.reps,
                        sourceBaselineReps = sourceBaselineReps,
                        targetBaselineReps = target.reps,
                        sourceSetNumber = source.setNumber,
                        targetSetNumber = target.setNumber,
                    ),
                )
            }
            next
        }
    }

    private fun adjustSmartWeight(setId: Long, delta: Int): Boolean {
        val adjustment = _smartAdjustments.value[setId] ?: return false
        val currentWeight = adjustment.suggestedWeightLbs ?: return false
        _smartAdjustments.update { current ->
            current + (setId to adjustment.copy(suggestedWeightLbs = (currentWeight + delta).coerceAtLeast(0)))
        }
        return true
    }

    private fun adjustSmartReps(setId: Long, delta: Int): Boolean {
        val adjustment = _smartAdjustments.value[setId] ?: return false
        val currentReps = adjustment.suggestedReps ?: return false
        _smartAdjustments.update { current ->
            current + (setId to adjustment.copy(suggestedReps = (currentReps + delta).coerceAtLeast(0)))
        }
        return true
    }

    private fun applySmartAdjustment(setId: Long): Boolean {
        val adjustment = _smartAdjustments.value[setId] ?: return false
        val target = _sessionSets.value.find { it.id == setId } ?: return false
        val updated = target.copy(
            weightLbs = adjustment.suggestedWeightLbs ?: target.weightLbs,
            reps = adjustment.suggestedReps ?: target.reps,
            isBodyweight = if (adjustment.suggestedWeightLbs != null) false else target.isBodyweight,
            completed = false,
        )
        _smartAdjustments.update { it - setId }
        updateSetLocal(setId) { updated }
        return true
    }

    private fun applySmartAdjustmentsForExercise(exerciseId: Long) {
        _sessionSets.value
            .filter { it.exerciseId == exerciseId && it.id in _smartAdjustments.value.keys }
            .map { it.id }
            .forEach(::applySmartAdjustment)
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

    fun cancelRestTimer() {
        activeRest = null
    }

    private fun finishActiveWorkoutDate() {
        val leavingDate = currentDate.value
        activeRest = null
        classifyFinishedSessionMuscles(leavingDate)
    }

    private fun classifyFinishedSessionMuscles(date: String) {
        viewModelScope.launch {
            val sets = setRepository.getSetsForDate(date)
            val exercises = exerciseRepository
                .getByIds(sets.map { it.exerciseId }.distinct())
                .filter { ExerciseMuscleClassifier.shouldClassify(it) }
            if (exercises.isEmpty()) return@launch

            muscleClassifier.classifyBatch(exercises).forEach { classification ->
                exerciseRepository.updateMuscleGroups(classification.exerciseId, classification.muscleGroups)
            }
        }
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
        addExerciseSession(exercise.id)
    }

    private suspend fun addExerciseSession(exerciseId: Long) {
        val date = currentDate.value
        val newSets = setRepository.addExerciseSession(date, exerciseId)
        _sessionSets.update { it + newSets }
        updateHistoricalHints(date, _sessionSets.value)
    }

    private fun buildUiState(inputs: UiInputs, snapshot: DbSnapshot): LogUiState {
        val exerciseMap = snapshot.exercises.associateBy { it.id }
        
        // Filter exercises based on search input (case-insensitive)
        val filteredSets = if (inputs.input.isNotBlank()) {
            val query = inputs.input.lowercase()
            snapshot.currentSets.filter { set ->
                exerciseMap[set.exerciseId]?.name?.lowercase()?.contains(query) == true
            }
        } else {
            snapshot.currentSets
        }

        val groupedExercises = filteredSets
            .groupBy(WorkoutSet::exerciseId)
            .entries
            .let(::orderLogExerciseGroups)
            .mapNotNull { entry ->
                val exercise = exerciseMap[entry.key] ?: return@mapNotNull null
                val sets = entry.value.sortedBy(WorkoutSet::setNumber)
                
                // Calculate Est. 1RM: Brzycki Formula
                val currentMax1RM = sets.maxOfOrNull { 
                    val reps = (it.reps ?: 0).coerceIn(0, 36)
                    if (reps == 0) 0f else WeightLbs.toLbs(it.weightLbs).toFloat() / (1.0278f - 0.0278f * reps)
                }?.toInt() ?: 0

                LogExerciseUi(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    muscleGroups = exercise.muscleGroups,
                    lastSessionHint = _exerciseHints.value[exercise.id],
                    sets = sets.map { set ->
                        val smartAdjustment = inputs.smartAdjustments[set.id]
                        LogSetUi(
                            id = set.id,
                            exerciseId = set.exerciseId,
                            setNumber = set.setNumber,
                            weightLbs = set.weightLbs,
                            reps = set.reps,
                            suggestedWeightLbs = smartAdjustment?.suggestedWeightLbs,
                            suggestedReps = smartAdjustment?.suggestedReps,
                            isBodyweight = set.isBodyweight,
                            completed = set.completed,
                            restAfterSeconds = set.restTimeSeconds,
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
                    label = slot.name,
                    shortLabel = slot.name.take(2),
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
                    label = slot?.name ?: "Unknown",
                    shortLabel = slot?.name?.take(2) ?: "Un",
                )
            } else {
                null
            },
            alternativeForDate = snapshot.currentDay?.alternativeForDate,
            cycleOptions = cycleOptions,
            exercises = groupedExercises,
            exerciseInput = inputs.input,
            predictiveExercises = inputs.suggestions,
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
        val maxWeight = lastSessionSets.maxOf { it.weightLbs ?: 0 }
        val maxLabel = if (maxWeight == 0) "BW" else "${WeightLbs.formatStored(maxWeight)} lbs"
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

internal fun orderLogExerciseGroups(
    groups: Iterable<Map.Entry<Long, List<WorkoutSet>>>,
): List<Map.Entry<Long, List<WorkoutSet>>> = groups.toList()

private data class UiInputs(
    val date: String,
    val input: String,
    val suggestions: List<Exercise>,
    val expanded: Boolean,
    val smartAdjustments: Map<Long, SmartSetAdjustment>,
)

private data class ActiveRest(
    val exerciseId: Long,
    val completedSetId: Long,
    val startedAtMillis: Long,
)

private data class SmartSetAdjustment(
    val setId: Long,
    val sourceSetId: Long,
    val sourceBaselineReps: Int?,
    val suggestedWeightLbs: Int?,
    val suggestedReps: Int?,
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
