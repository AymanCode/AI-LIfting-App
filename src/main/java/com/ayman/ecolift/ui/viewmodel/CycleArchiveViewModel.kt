package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

class CycleArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = WorkoutRepository(db)
    private val archiveJson = Json { ignoreUnknownKeys = true }

    val archives: StateFlow<List<ArchiveCardUi>> =
        repo.observeArchivedCycles()
            .map { rows -> rows.map { it.toCardUi() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow<CycleSnapshot?>(null)
    val detail: StateFlow<CycleSnapshot?> = _detail.asStateFlow()

    private val _detailName = MutableStateFlow("")
    val detailName: StateFlow<String> = _detailName.asStateFlow()

    fun loadArchive(id: Long) {
        viewModelScope.launch {
            val row = repo.getArchivedCycle(id)
            _detail.value = row?.let {
                runCatching { archiveJson.decodeFromString<CycleSnapshot>(it.snapshotJson) }
                    .getOrNull()
            }
            _detailName.value = row?.name.orEmpty()
        }
    }

    fun clearDetail() {
        _detail.value = null
        _detailName.value = ""
    }

    fun deleteArchive(id: Long) {
        viewModelScope.launch { repo.deleteArchivedCycle(id) }
    }

    suspend fun defaultArchiveWindow(): Pair<String, String> {
        val start = repo.getActiveCycleStart()
        val end = repo.getLatestWorkoutDate() ?: LocalDate.now().toString()
        return start to end
    }

    suspend fun overlapCount(start: String, end: String): Int =
        repo.countOverlappingArchives(start, end)

    fun archiveCurrentCycle(
        name: String,
        start: String,
        end: String,
        onArchived: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repo.archiveCurrentCycle(name, start, end)
            onArchived()
        }
    }
}
