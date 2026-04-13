package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SplitViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val workoutRepository = WorkoutRepository(database)

    val uiState: StateFlow<SplitUiState> = combine(
        workoutRepository.cycle,
        workoutRepository.observeCycleSlots()
    ) { cycle, slots ->
        SplitUiState(
            isActive = cycle.isActive,
            slots = slots.map { slot ->
                CycleSlotUi(
                    type = slot.id.toInt(),
                    occurrence = 0,
                    label = slot.name,
                    shortLabel = slot.name,
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SplitUiState(),
    )

    fun toggleActive() {
        viewModelScope.launch {
            val cycle = workoutRepository.getCycle()
            workoutRepository.saveCycle(!cycle.isActive, cycle.numTypes)
        }
    }

    fun addSlot(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            workoutRepository.addCycleSlot(name)
        }
    }

    fun deleteSlot(id: Long) {
        viewModelScope.launch {
            workoutRepository.deleteCycleSlot(id)
        }
    }
}
