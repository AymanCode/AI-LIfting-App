package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCalculator
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.data.progress.ScoreBreakdown
import com.ayman.ecolift.data.progress.ScoreWeights
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.ui.navigation.weeklySessionCounts
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.UserVisibleSyncException
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class CycleArchiveViewModel(private val repos: AppRepos) : ViewModel() {
    private val repo = repos.workout
    private val archiveJson = Json { ignoreUnknownKeys = true }

    /** The albums' weekly session counts, loaded once per archive row rather than on every set change. */
    private val weekly = ArchiveWeeklyCache { id -> weeklySessionCounts(repo.archivedCycleProgress(id)) }

    val archives: StateFlow<List<ArchiveCardUi>> =
        combine(
            repo.observeArchivedCycles(),
            repos.sets.sets,
            repo.observeUserBodyweightLbs(),
        ) { rows, _, _ -> rows }
            .map { rows ->
                rows to rows.map { row ->
                    try {
                        row.toCardUi(repo.archiveSummary(row))
                    } catch (_: Exception) {
                        row.toCardUi()
                    }
                }
            }
            .transformLatest { (rows, cards) ->
                // The list shows at once with the counts already known; new or changed albums follow.
                emit(cards.map { it.copy(weeklySessions = weekly.countsFor(it.id)) })
                if (weekly.refresh(rows)) emit(cards.map { it.copy(weeklySessions = weekly.countsFor(it.id)) })
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow<CycleSnapshot?>(null)
    val detail: StateFlow<CycleSnapshot?> = _detail.asStateFlow()

    private val _linerNotes = MutableStateFlow<ArchiveLinerNotes?>(null)
    val linerNotes: StateFlow<ArchiveLinerNotes?> = _linerNotes.asStateFlow()
    private var detailHistory: List<WorkoutSet> = emptyList()

    private val _detailName = MutableStateFlow("")
    val detailName: StateFlow<String> = _detailName.asStateFlow()

    val detailVolumeDefinition = MutableStateFlow("Saved volume")
    private val _detailVolumeLbs = MutableStateFlow<Long?>(null)
    val detailVolumeLbs: StateFlow<Long?> = _detailVolumeLbs.asStateFlow()

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
        // The archive entry owns this ViewModel until it leaves the stack. A report only
        // covers it, so returning keeps the frozen projection, selected window and weights.
        if (currentArchiveId == id && _core.value != null && _linerNotes.value != null) return
        currentArchiveId = id
        viewModelScope.launch {
            val row = repo.getArchivedCycle(id)
            if (currentArchiveId != id) return@launch
            _detail.value = row?.let {
                runCatching { archiveJson.decodeFromString<CycleSnapshot>(it.snapshotJson) }
                    .getOrNull()
            }
            _detailName.value = row?.name.orEmpty()
            detailVolumeDefinition.value = when {
                row?.snapshotJson?.contains("EXTERNAL_WORKING_V1") == true -> "Working volume"
                row?.snapshotJson?.contains("EXTERNAL_COMPLETED_V1") == true -> "Logged volume"
                else -> "Saved volume"
            }
            _detailVolumeLbs.value = row?.totalVolumeLbs
            detailHistory = repos.sets.sets.value.toList()
            val core = runCatching { repo.archivedCycleProgress(id) }.getOrNull()
            if (currentArchiveId != id) return@launch
            _core.value = core
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
        _comparison.value = null
        projectLinerNotes()
        viewModelScope.launch {
            val comparison = runCatching { repo.archivedCycleComparison(id, window) }.getOrNull()
            if (currentArchiveId != id || _window.value != window) return@launch
            _comparison.value = comparison
            projectLinerNotes()
        }
    }

    private fun projectLinerNotes() {
        _linerNotes.value = _detail.value?.let {
            buildArchiveLinerNotes(it, detailHistory, _window.value, _comparison.value)
        }
    }

    fun clearDetail() {
        currentArchiveId = null
        _detail.value = null
        detailHistory = emptyList()
        _linerNotes.value = null
        _detailName.value = ""
        _detailVolumeLbs.value = null
        _core.value = null
        _comparison.value = null
        _window.value = ComparisonWindow.M3
        _weights.value = ScoreWeights.PRESET
    }

    fun deleteArchive(id: Long) {
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            try {
                repo.deleteArchivedCycle(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                SyncErrors.report(UserVisibleSyncException("Couldn't delete the archive.", failure))
            }
        }
    }

    suspend fun defaultArchiveWindow(): Pair<String, String> {
        val start = repo.getActiveCycleStart()
        val end = repo.getLatestWorkoutDate() ?: todayLocalDate().toString()
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
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            try {
                repo.archiveCurrentCycle(name, start, end)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                SyncErrors.report(UserVisibleSyncException("Couldn't archive the cycle.", failure))
                return@launch
            }
            onArchived()
        }
    }
}

/**
 * Each archived cycle's weekly session counts for its album art. [refresh] loads only the rows it
 * has not seen, or that changed since it last loaded them, and reuses the rest, so the list's
 * refresh on every set change does not rebuild every album. A failed load keeps the counts the
 * album already had (empty the first time) and is not retried until its row changes.
 */
internal class ArchiveWeeklyCache(private val load: suspend (Long) -> List<Int>) {
    private var entries: Map<Long, Pair<ArchivedCycle, List<Int>>> = emptyMap()

    fun countsFor(id: Long): List<Int> = entries[id]?.second.orEmpty()

    /** Returns whether any album's counts were loaded. */
    suspend fun refresh(rows: List<ArchivedCycle>): Boolean {
        val ids = rows.mapTo(hashSetOf()) { it.id }
        entries = entries.filterKeys { it in ids }
        var loaded = false
        rows.forEach { row ->
            val cached = entries[row.id]
            if (cached?.first == row) return@forEach
            val counts = runCatching { load(row.id) }
                .getOrElse { error -> if (error is CancellationException) throw error else cached?.second.orEmpty() }
            entries = entries + (row.id to (row to counts))
            loaded = true
        }
        return loaded
    }
}
