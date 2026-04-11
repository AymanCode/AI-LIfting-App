package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SplitViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val workoutRepository = WorkoutRepository(database)

    val uiState: StateFlow<SplitUiState> = workoutRepository.cycle
        .map { cycle ->
            SplitUiState(
                isActive = cycle.isActive,
                numTypes = cycle.numTypes,
                previewSlots = buildPreview(cycle.numTypes),
            )
        }
        .stateIn(
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

    fun setNumTypes(numTypes: Int) {
        viewModelScope.launch {
            val cycle = workoutRepository.getCycle()
            workoutRepository.saveCycle(cycle.isActive, numTypes.coerceAtLeast(1))
        }
    }

    private fun buildPreview(numTypes: Int): List<String> {
        return (0 until maxOf(numTypes * 2, 6)).map { index ->
            val type = index % numTypes
            val occurrence = (index / numTypes) + 1
            cycleTypeShortLabel(type, occurrence)
        }
    }
}
