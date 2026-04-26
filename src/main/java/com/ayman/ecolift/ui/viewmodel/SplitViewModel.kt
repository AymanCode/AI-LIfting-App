package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SplitViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repo = WorkoutRepository(database)
    private val setDao = database.workoutSetDao()
    private val exerciseDao = database.exerciseDao()

    val uiState: StateFlow<SplitUiState> = combine(
        repo.cycle,
        repo.observeCycleSlots(),
        repo.observeAllWorkoutDays(),
        repo.observeAllSplitExercises(),
    ) { cycle, slots, days, splitRows ->
        Quad(cycle.isActive, cycle.nextSessionType, slots, days, splitRows)
    }.mapLatest { q ->
        buildState(q.enabled, q.nextSessionType, q.slots, q.days, q.splitRows)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SplitUiState(),
    )

    /** Recent workout days user can save into a split. Newest first. */
    val availableSessions: StateFlow<List<AvailableSessionUi>> =
        setDao.observeAllDistinctDates()
            .mapLatest { dates ->
                dates.take(30).mapNotNull { date ->
                    val sets = setDao.getForDate(date)
                    if (sets.isEmpty()) return@mapNotNull null
                    val ids = sets.map { it.exerciseId }.distinct()
                    val names = exerciseDao.getByIds(ids).associateBy { it.id }
                    AvailableSessionUi(
                        date = date,
                        exerciseCount = ids.size,
                        preview = ids.mapNotNull { names[it]?.name }.take(4),
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // Actions

    fun toggleCycle(enabled: Boolean) {
        viewModelScope.launch {
            val current = repo.getCycle()
            repo.saveCycle(enabled, current.numTypes)
        }
    }

    fun addSplit(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addCycleSlot(name.trim()) }
    }

    fun deleteSplit(splitId: Long) {
        viewModelScope.launch { repo.deleteCycleSlot(splitId) }
    }

    fun renameSplit(splitId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repo.renameCycleSlot(splitId, trimmed) }
    }

    fun reorderSplits(idsInOrder: List<Long>) {
        viewModelScope.launch { repo.reorderCycleSlots(idsInOrder) }
    }

    fun saveSplitFromDate(splitId: Long, date: String) {
        viewModelScope.launch { repo.saveSplitFromDate(splitId, date) }
    }

    fun clearSavedExercises(splitId: Long) {
        viewModelScope.launch { repo.clearSplitExercises(splitId) }
    }

    fun advanceCycle() {
        viewModelScope.launch {
            val current = repo.getCycle()
            val slotCount = repo.getCycleSlots().size.coerceAtLeast(1)
            val next = ((current.nextSessionType ?: 0) + 1) % slotCount
            repo.setNextSessionType(next)
        }
    }

    // Derivation

    private suspend fun buildState(
        enabled: Boolean,
        nextSessionType: Int?,
        slots: List<CycleSlot>,
        days: List<WorkoutDay>,
        splitRows: List<SplitExercise>,
    ): SplitUiState {
        val splitRowsBySlot = splitRows.groupBy { it.splitId }
        val splits = slots.map { slot ->
            buildSplit(slot, days, splitRowsBySlot[slot.id].orEmpty())
        }
        val order = slots.map { slot -> CycleEntry.SplitDay(slot.id, slot.name) }
        val currentIndex = (nextSessionType ?: 0)
            .coerceIn(0, (order.size - 1).coerceAtLeast(0))
        return SplitUiState(
            cycle = SplitCycle(enabled = enabled, order = order, currentIndex = currentIndex),
            splits = splits,
        )
    }

    private suspend fun buildSplit(
        slot: CycleSlot,
        allDays: List<WorkoutDay>,
        savedRows: List<SplitExercise>,
    ): Split {
        val slotDays = allDays.filter { it.cycleSlotId == slot.id }
            .sortedByDescending { it.date }
        val mostRecentDate = slotDays.firstOrNull()?.date
        val lastEpochDay = mostRecentDate?.let {
            runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull()
        }

        // Prefer the user-saved exercise list; fall back to most-recent session.
        val isSaved = savedRows.isNotEmpty()
        val exerciseRefs: List<SplitExerciseRef> = if (isSaved) {
            val orderedIds = savedRows.sortedBy { it.orderIndex }.map { it.exerciseId }
            val byId = exerciseDao.getByIds(orderedIds).associateBy { it.id }
            orderedIds.mapNotNull { id ->
                val ex = byId[id] ?: return@mapNotNull null
                val history = setDao.getVolumeHistory(id, 6).reversed().map { it.volume.toFloat() }
                SplitExerciseRef(id, ex.name, history)
            }
        } else if (mostRecentDate != null) {
            val sets = setDao.getForDate(mostRecentDate)
            val orderedIds = sets.map { it.exerciseId }.distinct()
            val byId = exerciseDao.getByIds(orderedIds).associateBy { it.id }
            orderedIds.mapNotNull { id ->
                val ex = byId[id] ?: return@mapNotNull null
                val history = setDao.getVolumeHistory(id, 6).reversed().map { it.volume.toFloat() }
                SplitExerciseRef(id, ex.name, history)
            }
        } else emptyList()

        val estimatedDurationMin = if (mostRecentDate == null) 0
        else (setDao.getForDate(mostRecentDate).size * 3).coerceAtLeast(15)

        val recentDates = slotDays.take(6).map { it.date }
        val setsOnDates = if (recentDates.isEmpty()) emptyList()
        else setDao.getForDates(recentDates)
        val volumeByDate = setsOnDates.groupBy { it.date }
            .mapValues { (_, dateSets) ->
                dateSets.sumOf {
                    val weight = WeightLbs.toLbs(it.weightLbs)
                    val reps = it.reps ?: 0
                    weight * reps
                }.toFloat()
            }
        val recentVolume = recentDates.reversed().mapNotNull { volumeByDate[it] }

        return Split(
            id = slot.id,
            name = slot.name,
            exercises = exerciseRefs,
            lastPerformedEpochDay = lastEpochDay,
            estimatedDurationMin = estimatedDurationMin,
            recentVolume = recentVolume,
            isSaved = isSaved,
        )
    }

    private data class Quad(
        val enabled: Boolean,
        val nextSessionType: Int?,
        val slots: List<CycleSlot>,
        val days: List<WorkoutDay>,
        val splitRows: List<SplitExercise>,
    )
}

data class AvailableSessionUi(
    val date: String,
    val exerciseCount: Int,
    val preview: List<String>,
)
