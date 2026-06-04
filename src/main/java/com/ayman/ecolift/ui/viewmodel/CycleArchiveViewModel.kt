package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.WorkoutRepository
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCalculator
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.data.progress.ScoreBreakdown
import com.ayman.ecolift.data.progress.ScoreWeights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
        combine(
            repo.observeArchivedCycles(),
            db.workoutSetDao().observeCompletedSets(),
            repo.observeUserBodyweightLbs(),
        ) { rows, _, _ -> rows }
            .map { rows ->
                rows.map { row ->
                    try {
                        row.toCardUi(repo.archiveSummary(row))
                    } catch (_: Exception) {
                        row.toCardUi()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow<CycleSnapshot?>(null)
    val detail: StateFlow<CycleSnapshot?> = _detail.asStateFlow()

    private val _detailName = MutableStateFlow("")
    val detailName: StateFlow<String> = _detailName.asStateFlow()

    private val _core = MutableStateFlow<CycleProgressCore?>(null)
    val core: StateFlow<CycleProgressCore?> = _core.asStateFlow()

    private val _comparison = MutableStateFlow<CycleComparison?>(null)
    val comparison: StateFlow<CycleComparison?> = _comparison.asStateFlow()

    private val _window = MutableStateFlow(ComparisonWindow.M3)
    val window: StateFlow<ComparisonWindow> = _window.asStateFlow()

    private val _weights = MutableStateFlow(ScoreWeights.PRESET)
    val weights: StateFlow<ScoreWeights> = _weights.asStateFlow()

    val score: StateFlow<ScoreBreakdown?> =
        combine(_comparison, _weights) { comparison, weights ->
            comparison?.let { CycleProgressCalculator.score(it, weights) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var currentArchiveId: Long? = null

    fun loadArchive(id: Long) {
        currentArchiveId = id
        viewModelScope.launch {
            val row = repo.getArchivedCycle(id)
            _detail.value = row?.let {
                runCatching { archiveJson.decodeFromString<CycleSnapshot>(it.snapshotJson) }
                    .getOrNull()
            }
            _detailName.value = row?.name.orEmpty()
            _core.value = runCatching { repo.archivedCycleProgress(id) }.getOrNull()
            reloadComparison()
        }
    }

    fun setWindow(window: ComparisonWindow) {
        if (_window.value == window) return
        _window.value = window
        reloadComparison()
    }

    fun setWeights(weights: ScoreWeights) {
        _weights.value = weights
    }

    fun resetWeights() {
        _weights.value = ScoreWeights.PRESET
    }

    private fun reloadComparison() {
        val id = currentArchiveId ?: return
        val window = _window.value
        viewModelScope.launch {
            _comparison.value =
                runCatching { repo.archivedCycleComparison(id, window) }.getOrNull()
        }
    }

    fun clearDetail() {
        currentArchiveId = null
        _detail.value = null
        _detailName.value = ""
        _core.value = null
        _comparison.value = null
        _window.value = ComparisonWindow.M3
        _weights.value = ScoreWeights.PRESET
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
