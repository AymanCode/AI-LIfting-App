package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.DayInfo
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberEdit
import com.ayman.ecolift.data.splits.applyEdit
import com.ayman.ecolift.data.splits.effectiveSplitMembers
import com.ayman.ecolift.data.splits.workoutLiftsFor
import com.ayman.ecolift.data.splits.buildDayInfoMap
import com.ayman.ecolift.data.splits.learnedGroupLabel
import com.ayman.ecolift.data.splits.buildDayMarks
import com.ayman.ecolift.data.splits.DayMarks
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.UserVisibleSyncException
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SplitViewModel(private val repos: AppRepos) : ViewModel() {
    private val repo = repos.workout

    /**
     * The dial and the cards come from the learned model in due order: the due group is at UP
     * NEXT and nothing advances it by hand (spec 2026-09-28). The rotation is always on.
     */
    val uiState: StateFlow<SplitUiState> = combine(
        repos.learnedSplits.model,
        repo.observeAllCycleSlots(),
        repo.observeAllSplitExercises(),
        combine(repos.exercises.exercises, repos.sets.sets, repo.splitMemberChoices, ::Triple),
    ) { model, slots, splitRows, (exercises, sets, choices) ->
        Inputs(model, slots, splitRows, exercises, sets, choices)
    }.mapLatest { inputs ->
        latestInputs = inputs
        buildState(inputs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SplitUiState(),
    )

    /**
     * The last 30 days with a checked set, newest first, for "From a workout": each day's lifts are
     * exactly the ones the board would take ([workoutLiftsFor]), so the count matches the result.
     */
    val availableSessions: StateFlow<List<AvailableSessionUi>> =
        combine(repos.sets.sets, repos.sets.cards, repos.exercises.exercises) { allSets, cards, exercises ->
            val names = exercises.associateBy { it.id }
            val setsByDate = allSets.groupBy { it.date }
            val cardsByDate = cards.groupBy { it.date }
            allSets.filter { it.completed }.map { it.date }.distinct().sortedDescending().take(30).mapNotNull { date ->
                val ids = workoutLiftsFor(date, setsByDate[date].orEmpty(), cardsByDate[date].orEmpty(), ::canonicalId)
                if (ids.isEmpty()) null else AvailableSessionUi(date, ids.size, ids.mapNotNull { names[it]?.name }.take(4))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    /** Every day with at least one logged set. Drives the calendar's worked-day marks. */
    val workedDays: StateFlow<Set<LocalDate>> =
        repos.sets.sets
            .map { sets ->
                sets.filter { it.completed }.map { it.date }.distinct()
                    .mapNotNullTo(mutableSetOf()) { runCatching { LocalDate.parse(it) }.getOrNull() }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet()
            )

    /** Stickers read attribution: the model for recent days, the stored split for frozen ones. */
    val dayInfo: StateFlow<Map<LocalDate, DayInfo>> = combine(
        repos.sets.sets,
        combine(repos.exercises.exercises, repos.settings.settings, ::Pair),
        repos.learnedSplits.model,
        repo.observeAllWorkoutDays(),
        repo.observeAllCycleSlots(),
    ) { sets, (exercises, settings), model, workoutDays, slots ->
        val exercisesById = exercises.associateBy { it.id }
        buildDayInfoMap(
            completedSetsByDate = sets.filter { it.completed }.groupBy { it.date },
            setVolume = { set -> getSetVolume(set, exercisesById, settings.userBodyweightLbs) },
            workoutDays = workoutDays,
            slots = slots,
            model = model,
            groupLabel = { learnedGroupLabel(it, exercisesById) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )

    /** The sticker calendar's note: set counts, the day's muscles and its weight PRs. */
    val dayMarks: StateFlow<Map<LocalDate, DayMarks>> = combine(
        repos.sets.sets,
        repos.exercises.exercises,
    ) { sets, exercises ->
        buildDayMarks(sets, exercises.associateBy { it.id })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )

    /** Logged days per calendar month since the first one: the ticker's "monthly avg". */
    val personalMonthlyAverage: StateFlow<Double> =
        workedDays
            .map { loggedDaysPerMonth(it, todayLocalDate()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0.0,
            )

    // Actions

    fun addSplit(name: String) {
        if (name.isBlank()) return
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch { writeOrSay("Couldn't add the split.") { repo.addCycleSlot(name.trim()) } }
    }

    /** Retires a split (suppressed_at): it leaves the lists and is never learned again. */
    fun retireSplit(splitId: Long) {
        if (DemoGuard.blockIfDemo() || splitId <= 0L) return
        writeThenRelearn("Couldn't retire the split. Try again.") { repo.suppressCycleSlot(splitId) }
    }

    fun renameSplit(splitId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || splitId <= 0L) return
        if (DemoGuard.blockIfDemo()) return
        writeThenRelearn("Couldn't rename the split.") { repo.renameCycleSlot(splitId, trimmed) }
    }

    /** A split write, then a relearn; a failure shows [failureMessage]. */
    private fun writeThenRelearn(failureMessage: String, write: suspend () -> Unit) {
        viewModelScope.launch {
            writeSplitThenRelearn(repos, failureMessage, write)
        }
    }

    /** One edit to a split's board, applied to the board on screen and saved whole. */
    fun editMembers(slotId: Long, edit: SplitMemberEdit) {
        if (DemoGuard.blockIfDemo() || slotId <= 0L) return
        val board = shownBoard(slotId) ?: return
        val next = applyEdit(board, edit)
        if (next == board) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeSplitThenRelearn(repos, SAVE_FAILED) { repo.saveSplitMembers(slotId, next) }
        }
    }

    /**
     * Adds a typed lift to IN THIS SPLIT: a name or alias the user already has reuses that lift,
     * any other name creates the user's own. [onSaved] runs only once the board is saved.
     */
    fun addTypedLift(slotId: Long, name: String, onSaved: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || slotId <= 0L || DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            val saved = writeOrSay(SAVE_FAILED) {
                val lift = repos.exercises.canonicalId(repos.exercises.getOrCreate(trimmed).id)
                val board = shownBoard(slotId) ?: error("Split $slotId is not shown")
                repo.saveSplitMembers(slotId, applyEdit(board, SplitMemberEdit.Include(lift)))
            }
            if (saved) {
                repos.learnedSplits.requestRun()
                onSaved()
            }
        }
    }

    /** Makes [date]'s checked lifts the split's IN list; the lifts it drops become options. */
    fun saveSplitFromDate(slotId: Long, date: String) {
        editMembers(slotId, SplitMemberEdit.Replace(workoutLiftsFor(date, repos.sets.sets.value, repos.sets.cards.value, ::canonicalId)))
    }

    private fun shownBoard(slotId: Long): EffectiveSplitMembers? {
        val inputs = latestInputs ?: return null
        val choices = (repo.splitMemberChoices.value as? SplitChoicesState.Ready)?.choices ?: return null
        if (inputs.slots.none { it.id == slotId && it.isActive }) return null
        return effectiveSplitMembers(slotId, inputs.model.groupForSlot(slotId), inputs.splitRows, choices[slotId], ::canonicalId)
    }

    private fun canonicalId(id: Long): Long = runCatching { repos.exercises.canonicalId(id) }.getOrDefault(id)

    // Derivation

    private fun buildState(inputs: Inputs): SplitUiState {
        val ready = inputs.choices as? SplitChoicesState.Ready
        val cards = buildSplitGroupCards(
            model = inputs.model,
            today = todayLocalDate().toString(),
            exercises = inputs.exercises,
            sets = inputs.sets,
            canonicalId = ::canonicalId,
            slots = inputs.slots,
            savedRows = inputs.splitRows,
            choices = ready?.choices.orEmpty(),
        )
        val order = cards.map { card -> CycleEntry.SplitDay(card.slotId, card.name) }
        return SplitUiState(
            cycle = SplitCycle(enabled = true, order = order, currentIndex = 0),
            cards = cards,
            boardsReady = ready != null,
            pendingSlots = ready?.pendingSlots.orEmpty(),
        )
    }

    private data class Inputs(
        val model: com.ayman.ecolift.data.splits.LearnedModel,
        val slots: List<CycleSlot>,
        val splitRows: List<SplitExercise>,
        val exercises: List<Exercise>,
        val sets: List<WorkoutSet>,
        val choices: SplitChoicesState,
    )

    private var latestInputs: Inputs? = null

    private fun getSetVolume(
        set: WorkoutSet,
        exercisesById: Map<Long, Exercise>,
        userBodyweightLbs: Int?,
    ): Double {
        return com.ayman.ecolift.data.WorkoutMetrics.loggedVolumeLbs(set)
    }
}

private const val SAVE_FAILED = "Couldn't save the split."

/** Shared by Splits and the desk; preserves the same write, failure message and relearn path. */
/** Runs a split write; a failure says [failureMessage] in the app's snackbar. True when it landed. */
internal suspend fun writeOrSay(failureMessage: String, write: suspend () -> Unit): Boolean = try {
    write()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    SyncErrors.report(UserVisibleSyncException(failureMessage, failure))
    false
}

/** A split write shared by the Split and IronMind screens, then a relearn; a failure shows [failureMessage]. */
internal suspend fun writeSplitThenRelearn(repos: AppRepos, failureMessage: String, write: suspend () -> Unit) {
    if (writeOrSay(failureMessage, write)) repos.learnedSplits.requestRun()
}

/** Logged days divided by calendar months, counting the first logged month and today's. */
internal fun loggedDaysPerMonth(workedDates: Set<LocalDate>, today: LocalDate): Double {
    val earliest = workedDates.minOrNull() ?: return 0.0
    val months = (today.year - earliest.year) * 12 + (today.month.ordinal - earliest.month.ordinal) + 1
    return workedDates.size.toDouble() / months.coerceAtLeast(1)
}

data class AvailableSessionUi(
    val date: String,
    val exerciseCount: Int,
    val preview: List<String>,
)
