package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hub.BacklogItem
import com.ayman.ecolift.data.hub.HubBuild
import com.ayman.ecolift.data.hub.HubItem
import com.ayman.ecolift.data.hub.HubItems
import com.ayman.ecolift.data.hub.UncheckedSetsItem
import com.ayman.ecolift.data.hub.itemIds
import com.ayman.ecolift.data.hub.withoutItems
import com.ayman.ecolift.ui.hub.HubSwipe
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.supabase.AssistantChangeRow
import com.ayman.ecolift.web.supabase.sync.orThrow
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

data class HubTopSets(val last: String?, val best: String?)

private data class HubSnapshot(val sets: List<WorkoutSet>, val exercises: List<Exercise>, val build: HubBuild)

/** The earliest date that still gets an individual notification. Anything older is backlog. */
fun individualReviewFrom(today: LocalDate, windowDays: Int = 21): String =
    today.minus(windowDays, DateTimeUnit.DAY).toString()

/** Label builder for top set by weight formatted as "<weight> × <reps>" using the multiplication sign. */
fun hubTopSetLabel(sets: List<WorkoutSet>): String? {
    val completedSets = sets.filter { it.completed }
    if (completedSets.isEmpty()) return null
    val topSet = completedSets.maxWithOrNull(
        compareBy<WorkoutSet> { it.weightLbs ?: 0 }
            .thenBy { it.reps ?: 0 }
    ) ?: return null
    // weightLbs is stored in tenths of a pound, so it has to be formatted, not printed.
    val weight = WeightLbs.formatStored(topSet.weightLbs ?: 0)
    val reps = topSet.reps ?: 0
    return "$weight × $reps"
}

/**
 * Resolves an IronMind action against the live repository snapshot, never selecting checked work.
 * A captured syncId remains authoritative when a refresh remaps an optimistic ID; row actions
 * without a captured syncId still select exactly the requested unchecked set by ID.
 */
fun hubIndividualRemovalTarget(sets: List<WorkoutSet>, setId: Long, syncId: String = ""): WorkoutSet? {
    if (setId == 0L && syncId.isBlank()) return null
    return sets.firstOrNull {
        (if (syncId.isNotBlank()) it.syncId == syncId else it.id == setId) && !it.completed
    }
}

/** A row of the IronMind "Claude changes" list. */
data class ClaudeChangeUi(
    val id: String,
    val summary: String,
    val reason: String?,
    val whenLabel: String,
    val undone: Boolean,
    val canUndo: Boolean,
    val note: String? = null,
)

/**
 * Only an APPLIED, non-undo row can be undone. Undo rows and already-undone rows stay in the list
 * greyed so the history reads honestly, but they carry no button.
 */
fun claudeChangeUi(row: AssistantChangeRow, today: String): ClaudeChangeUi {
    val stamp = (row.appliedAt ?: row.proposedAt).orEmpty()
    val date = stamp.take(10)
    val time = stamp.drop(11).take(5)
    val whenLabel = when {
        date.isEmpty() -> ""
        date == today && time.isNotEmpty() -> "Today $time"
        else -> date
    }
    return ClaudeChangeUi(
        id = row.id,
        summary = row.summary,
        reason = row.reason?.takeIf { it.isNotBlank() },
        whenLabel = whenLabel,
        undone = row.status == "UNDONE",
        canUndo = row.status == "APPLIED" && row.kind != "UNDO",
    )
}

/** The unread dot: something was applied after the last time IronMind was opened. */
fun isUnseenClaudeChange(latestAppliedAt: String?, lastSeenAppliedAt: String?): Boolean =
    latestAppliedAt != null && (lastSeenAppliedAt == null || latestAppliedAt > lastSeenAppliedAt)

/** Human text for an undo refusal code from public.undo_assistant_change_v1. */
fun undoRefusalNote(code: String): String = when (code) {
    "BLOCKED_BY_LATER_EDIT" -> "Can't undo: you've edited this since."
    "ALREADY_UNDONE" -> "Already undone."
    "NOT_APPLIED", "NOT_UNDOABLE" -> "Nothing to undo."
    else -> "Undo failed ($code)."
}

/** Persists when the user last opened the Claude-changes list, so the tab dot can clear. */
internal object AssistantChangeSeenStore {
    private var memory: String? = null
    private val _lastSeen = MutableStateFlow(memory)
    val lastSeen: StateFlow<String?> = _lastSeen.asStateFlow()

    fun markSeen(appliedAt: String?) {
        if (appliedAt == null) return
        memory = appliedAt
        _lastSeen.value = appliedAt
    }
}

class HubViewModel(
    private val repos: AppRepos,
) : ViewModel() {

    private val _claudeChanges = MutableStateFlow<List<ClaudeChangeUi>>(emptyList())
    val claudeChanges: StateFlow<List<ClaudeChangeUi>> = _claudeChanges.asStateFlow()

    val hasUnseenClaudeChange: StateFlow<Boolean> = combine(
        repos.assistantChanges.latestAppliedAt,
        AssistantChangeSeenStore.lastSeen,
    ) { latest, seen -> isUnseenClaudeChange(latest, seen) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Fetches the list (only when IronMind is open) and clears the unread dot. */
    fun loadClaudeChanges() {
        viewModelScope.launch {
            val rows = runCatching { repos.assistantChanges.list() }.getOrElse { return@launch }
            val today = todayLocalDate().toString()
            _claudeChanges.value = rows.map { claudeChangeUi(it, today) }
            AssistantChangeSeenStore.markSeen(repos.assistantChanges.latestAppliedAt.value)
        }
    }

    /** Same RPC the connector's undo tool calls, so chat and app can never undo twice. */
    fun undoClaudeChange(changeId: String) {
        viewModelScope.launch {
            val result = runCatching { repos.assistantChanges.undo(changeId) }
                .getOrElse { t -> setNote(changeId, "Undo failed · ${t::class.simpleName ?: "Throwable"}"); return@launch }
            if (!result.ok) {
                setNote(changeId, undoRefusalNote(result.code))
                return@launch
            }
            // Sets, cards, or the day's split pointer changed on the server: pull everything
            // again (the same full refresh the app does at boot) so the Log and dial agree.
            runCatching { repos.loadAll() }
            loadClaudeChanges()
        }
    }

    private fun setNote(changeId: String, note: String) {
        _claudeChanges.update { list -> list.map { if (it.id == changeId) it.copy(note = note) else it } }
    }

    /** Unchecked-set and backlog notifications. The smart-split slips left with learned splits. */
    private val snapshot: StateFlow<HubSnapshot> = combine(
        repos.sets.sets,
        repos.exercises.exercises,
    ) { sets, exercises ->
        val today = todayLocalDate()
        val hub = HubItems.build(
            sets = sets,
            exercisesById = exercises.associateBy { it.id },
            today = today.toString(),
            individualReviewFrom = individualReviewFrom(today),
        )
        HubSnapshot(sets, exercises, hub)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HubSnapshot(emptyList(), emptyList(), HubBuild(days = emptyList(), backlog = null, count = 0)),
    )

    private val sourceBuild: StateFlow<HubBuild> = snapshot.map { it.build }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubBuild(emptyList(), null, 0))

    private val dismissedItemIds = MutableStateFlow<Set<String>>(emptySet())

    val build: StateFlow<HubBuild> = combine(sourceBuild, dismissedItemIds) { source, dismissed ->
        source.withoutItems(dismissed)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HubBuild(days = emptyList(), backlog = null, count = 0),
    )

    private val _expandedItemId = MutableStateFlow<String?>(null)
    val expandedItemId: StateFlow<String?> = _expandedItemId.asStateFlow()

    private val sessionMessages = MutableStateFlow<List<AiMessageUi>>(emptyList())

    fun updateSessionMessages(messages: List<AiMessageUi>) { sessionMessages.value = messages }

    fun nameDeskGroup(slotId: Long, name: String) {
        if (slotId <= 0 || name.isBlank() || com.ayman.ecolift.web.demo.DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            writeSplitThenRelearn(repos, "Couldn't rename the split.") { repos.workout.renameCycleSlot(slotId, name.trim()) }
        }
    }

    fun retireDeskGroup(slotId: Long) {
        if (slotId <= 0 || com.ayman.ecolift.web.demo.DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            writeSplitThenRelearn(repos, "Couldn't retire the split. Try again.") { repos.workout.suppressCycleSlot(slotId) }
        }
    }

    val desk: StateFlow<IronMindDeskUi> = combine(
        repos.learnedSplits.model, snapshot, dismissedItemIds, sessionMessages,
    ) { model, current, dismissed, messages ->
        projectIronMindDesk(model, current.sets, current.exercises, current.build.withoutItems(dismissed), messages, todayLocalDate())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IronMindDeskUi())

    /** The masthead and navigation badge count decisions, never observations or cleanup. */
    val count: StateFlow<Int> = desk
        .map { it.decisionCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val topSets: StateFlow<Map<String, HubTopSets>> = build
        .map { currentBuild ->
            val uncheckedItems = currentBuild.days.flatMap { it.items }.filterIsInstance<UncheckedSetsItem>()
            val result = mutableMapOf<String, HubTopSets>()
            for (item in uncheckedItems) {
                val history = repos.sets.getRecentHistoryForExercise(item.exerciseId, item.date)
                val completedHistory = history.filter { it.completed }
                val mostRecentDate = completedHistory.maxOfOrNull { it.date }
                val last = if (mostRecentDate != null) {
                    hubTopSetLabel(completedHistory.filter { it.date == mostRecentDate })
                } else {
                    null
                }
                val best = hubTopSetLabel(completedHistory)
                result[item.id] = HubTopSets(last = last, best = best)
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _hubMessage = MutableStateFlow<String?>(null)
    val hubMessage: StateFlow<String?> = _hubMessage.asStateFlow()

    fun consumeHubMessage() {
        _hubMessage.value = null
    }

    init {
        // Once the repository no longer contains a dismissed notification, forget the temporary
        // UI suppression. If a new unchecked set later gets the same date/exercise identity, it is
        // a new notification and should be allowed to appear.
        viewModelScope.launch {
            sourceBuild.collect { source ->
                val liveIds = source.itemIds()
                dismissedItemIds.update { dismissed -> dismissed.intersect(liveIds) }
                val expandedId = _expandedItemId.value
                if (expandedId != null && expandedId !in liveIds) {
                    _expandedItemId.value = null
                }
            }
        }
    }

    fun toggleExpanded(itemId: String) {
        _expandedItemId.value = if (_expandedItemId.value == itemId) null else itemId
    }

    fun setChecked(setId: Long) {
        viewModelScope.launch {
            val target = repos.sets.sets.value.firstOrNull { it.id == setId }
            if (target != null && !target.completed) {
                repos.sets.updateSet(target.copy(completed = true), observed = target).orThrow()
                repos.workout.ensureWorkoutDay(target.date)
            }
        }
    }

    fun updateSetValues(setId: Long, weightLbs: Int?, reps: Int?) {
        viewModelScope.launch {
            val target = repos.sets.sets.value.firstOrNull { it.id == setId } ?: return@launch
            repos.sets.updateSet(target.copy(weightLbs = weightLbs, reps = reps), observed = target).orThrow()
        }
    }

    fun addSet(date: String, exerciseId: Long) {
        viewModelScope.launch {
            repos.sets.addSet(date = date, exerciseId = exerciseId, copyFrom = null).orThrow()
        }
    }

    fun confirmItem(item: UncheckedSetsItem) {
        viewModelScope.launch {
            item.openSets.filter { !it.completed }.forEach { set ->
                val target = repos.sets.completeSetIfOpen(set.id, set.syncId)
                    ?: return@forEach
                repos.workout.ensureWorkoutDay(target.date)
            }
        }
    }

    fun removeSet(setId: Long) {
        viewModelScope.launch {
            val target = hubIndividualRemovalTarget(repos.sets.sets.value, setId) ?: return@launch
            repos.sets.deleteSet(target.id)
        }
    }

    fun clearOpenSets(item: UncheckedSetsItem) {
        viewModelScope.launch {
            item.openSets.filter { !it.completed }.forEach { set ->
                repos.sets.deleteSetIfOpen(set.id, set.syncId)
            }
        }
    }

    fun clearBacklog(backlog: BacklogItem, allSets: List<WorkoutSet>) {
        viewModelScope.launch {
            val toDelete = allSets.filter {
                it.date >= backlog.fromDate && it.date <= backlog.toDate && !it.completed
            }
            toDelete.forEach { set ->
                repos.sets.deleteSetIfOpen(set.id, set.syncId)
            }
        }
    }

    /**
     * Left clears, right confirms -- the same directions the Log screen's exercise card uses.
     *
     * Confirming from a swipe only makes sense for an item whose sets are already complete enough to
     * check, so it routes through the same confirmItem the expanded card's button calls. The other
     * two item types have nothing to affirm, so a right swipe on them is a no-op rather than a
     * silent data change.
     */
    fun dismiss(item: HubItem, direction: HubSwipe) {
        // Notification dismissal is a UI fact first. Hide it before the repository action starts,
        // especially when resolving an exercise requires several set writes.
        dismissedItemIds.update { it + item.id }
        when (direction) {
            HubSwipe.Clear -> {
                when (item) {
                    is UncheckedSetsItem -> clearOpenSets(item)
                    is BacklogItem -> clearBacklog(item, repos.sets.sets.value)
                }
            }
            HubSwipe.Confirm -> {
                when (item) {
                    is UncheckedSetsItem -> confirmItem(item)
                    is BacklogItem -> Unit
                }
            }
        }
        if (_expandedItemId.value == item.id) {
            _expandedItemId.value = null
        }
    }
}
