package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hiddenWithSmartSuggestOff
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.data.splits.LearnedInputs
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.isSettled
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.LearnedSplitsEngine
import com.ayman.ecolift.data.splits.canonicalResolver
import com.ayman.ecolift.web.util.nowMillis
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What every reader (Log, Progress, Splits, the demo) sees: one learned-splits model. */
interface LearnedSplitsSource {
    val model: StateFlow<LearnedModel>
    /** Recompute from state now (a rename, a retire); the demo ignores it. */
    fun requestRun() = Unit
}

/** The checked, non-warm-up (date, exercise) pairs: learning reruns only when this set changes. */
internal fun checkedSignature(sets: List<WorkoutSet>): Set<Pair<String, Long>> =
    sets.asSequence().filter { it.isCheckedWorking }.map { it.date to it.exerciseId }.toSet()

@OptIn(ExperimentalTime::class)
internal fun millisUntilNextMidnight(nowMillis: Long, zone: TimeZone): Long {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val midnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
    return (midnight - nowMillis).coerceAtLeast(1_000L)
}

/**
 * Owns the learned-splits engine for one signed-in session (spec §Engine, lifecycle). It runs after
 * the first settled refresh ([start]), on any change to which lifts are checked (debounced),
 * whenever [requestRun] is called (sync completion, resume), and at local midnight. Each run
 * publishes the model at once, then persists only the engine's diff: day writes through the
 * durable day queue, slot writes (online only) through the RPC, then a second pass so days that
 * waited for a new slot's id are written. Writes never request a run. With [persist] false (the
 * demo) it only publishes.
 */
@OptIn(FlowPreview::class)
class LearnedSplitsCoordinator(
    private val scope: CoroutineScope,
    private val sets: SetRepository,
    private val exercises: ExerciseRepository,
    private val workout: WorkoutRepository,
    private val persist: Boolean,
    private val isOnline: () -> Boolean = { true },
    private val today: () -> LocalDate = { todayLocalDate() },
    private val now: () -> Long = { nowMillis() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val debounceMillis: Long = 500,
    private val alternatives: () -> List<com.ayman.ecolift.data.splits.SplitAlternative> = { emptyList() },
    // Read once at startup; flipping Smart Suggest reloads the page.
    private val smartSuggest: Boolean = true,
) : LearnedSplitsSource {
    private val _model = MutableStateFlow(LearnedModel.EMPTY.copy(smartSuggest = smartSuggest))
    override val model: StateFlow<LearnedModel> = _model.asStateFlow()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private var started = false

    /** Runs completed so far; tests read it. */
    internal var runCount = 0
        private set

    /** True until the first run: that run may trust slots the boot just loaded instead of reloading. */
    private var trustBootSlots = false

    /** [slotsFreshFromBoot]: the caller loaded cycle_slot, split_exercise and archived_cycle moments ago under this session. */
    fun start(slotsFreshFromBoot: Boolean = false) {
        if (started) return
        started = true
        trustBootSlots = slotsFreshFromBoot
        scope.launch {
            for (request in requests) {
                try {
                    runOnce()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    // The model stays published; the next trigger retries the writes.
                    logFailure("run", failure)
                }
            }
        }
        scope.launch {
            sets.sets.map(::checkedSignature).distinctUntilChanged().drop(1).debounce(debounceMillis).collect { requestRun() }
        }
        scope.launch {
            // A run that skipped its writes while boards were unread or saving writes once they settle.
            workout.splitMemberChoices.map { it.isSettled }.distinctUntilChanged().drop(1).collect { if (it) requestRun() }
        }
        scope.launch {
            while (true) {
                delay(millisUntilNextMidnight(now(), zone()))
                requestRun()
            }
        }
        if (!smartSuggest) {
            scope.launch {
                workout.observeAllCycleSlots().collect { slots ->
                    _model.update { current ->
                        if (current.asOf.isNotEmpty()) current
                        else current.copy(hiddenSlotIds = slots.hiddenWithSmartSuggestOff())
                    }
                }
            }
        }
        requestRun()
    }

    override fun requestRun() {
        requests.trySend(Unit)
    }

    suspend fun runOnce(): LearnedModel = mutex.withLock {
        runCount++
        // The boot's own load is this run's reload; later runs reload because another device may
        // have edited, retired or archived a split since.
        val serverFresh = if (trustBootSlots) {
            trustBootSlots = false
            persist && isOnline()
        } else {
            reloadServerSlots()
        }
        val snapshot = gather()
        val previous = lastSnapshot
        val first = if (previous != null && previous.sameAs(snapshot)) _model.value else LearnedSplitsEngine.compute(snapshot.inputs)
        lastSnapshot = snapshot
        _model.value = first
        if (!persist || first.actions.isEmpty || !boardsSettled()) return@withLock first
        writeDays(first)
        if (!serverFresh || !writeSlots(first) || !boardsSettled()) return@withLock first
        val again = gather()
        val second = LearnedSplitsEngine.compute(again.inputs)
        lastSnapshot = again
        _model.value = second
        writeDays(second)
        second
    }

    /**
     * The engine must not persist from boards it has not read, or from a board still being saved:
     * an unread board cannot protect its slot from an archive, and a staged one could make a seed
     * learn before the server accepted it.
     */
    private fun boardsSettled(): Boolean = workout.splitMemberChoices.value.isSettled

    /** One run's inputs and the merge graph its resolver was built from. */
    private class Snapshot(val inputs: LearnedInputs, val mergeGraph: Map<Long, Long?>) {
        /** The resolver is a new lambda every run, so everything but it is compared, plus the graph. */
        fun sameAs(other: Snapshot): Boolean =
            mergeGraph == other.mergeGraph && inputs.copy(resolve = other.inputs.resolve) == other.inputs
    }

    private var lastSnapshot: Snapshot? = null

    /**
     * Slots, members and archived cycles are not on the realtime channel, so an online run reloads
     * them before it computes (spec: anchors refresh on reconnect): a split edited, retired or
     * archived on another device must not be written back from this device's old copy. False when
     * offline, in the demo, or when the reload failed; slot writes then wait for a later run.
     */
    private suspend fun reloadServerSlots(): Boolean {
        if (!persist || !isOnline()) return false
        return try {
            workout.refreshSlots()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logFailure("reloading slots (slot writes wait)", failure)
            false
        }
    }

    private suspend fun gather(): Snapshot {
        val graph = exercises.mergeGraph()
        return Snapshot(
            LearnedInputs(
                asOf = today(),
                alternatives = alternatives(),
                sets = sets.sets.value,
                cards = sets.cards.value,
                resolve = canonicalResolver(graph),
                slots = workout.observeAllCycleSlots().first(),
                slotMembers = workout.observeAllSplitExercisesIncludingRetired().first(),
                workoutDays = workout.observeAllWorkoutDays().first(),
                archivedCycleEndDates = workout.observeArchivedCycles().first().map { it.endDate },
                cycleStartDate = workout.getActiveCycleStart(),
                memberChoices = (workout.splitMemberChoices.value as? SplitChoicesState.Ready)?.confirmed.orEmpty(),
                smartSuggest = smartSuggest,
            ),
            graph,
        )
    }

    private suspend fun writeDays(model: LearnedModel) {
        if (model.actions.dayWrites.isNotEmpty()) workout.enqueueLearnedDayWrites(model.actions.dayWrites)
    }

    /**
     * Slot inserts, anchor refreshes and archives; online only. Each write stands alone: one that
     * fails is logged and retried by a later run, and the rest still go. True when any landed.
     */
    private suspend fun writeSlots(model: LearnedModel): Boolean {
        val actions = model.actions
        if (actions.slotUpserts.isEmpty() && actions.slotArchives.isEmpty()) return false
        if (!isOnline()) return false
        val stamp = now()
        var landed = false
        try {
            for (upsert in actions.slotUpserts) {
                // A board save still out when this write starts changes the run's premise; a later run writes the rest.
                if (!boardsSettled()) return landed
                landed = attempt("saving split ${upsert.slotId ?: "(new)"}") {
                    workout.upsertLearnedSlot(
                        slotId = upsert.slotId,
                        name = if (upsert.slotId == null) upsert.name else null,
                        learned = upsert.learned,
                        memberIds = upsert.memberIds,
                        anchorUpdatedAt = stamp,
                    )
                } || landed
            }
            for (slotId in actions.slotArchives) {
                if (!boardsSettled()) return landed
                landed = attempt("archiving split $slotId") { workout.archiveLearnedSlot(slotId) } || landed
            }
        } finally {
            // Even after a failure: a slot that did land must be in the next run's input, or that
            // run would insert its group a second time. If this reload fails too, the next run
            // reloads before it writes anything, so nothing is inserted twice.
            attempt("reloading slots after writing") { workout.refreshSlots() }
        }
        return landed
    }

    private suspend fun attempt(what: String, write: suspend () -> Unit): Boolean = try {
        write()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        logFailure(what, failure)
        false
    }

    private fun logFailure(what: String, failure: Throwable) {
        println("EcoLift learned splits: $what failed: ${failure::class.simpleName}: ${failure.message}")
    }
}
