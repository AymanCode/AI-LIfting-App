package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate

/**
 * Learned splits (spec docs/superpowers/specs/2026-09-28-learned-splits-design.md). Every number
 * the engine uses is here, with the spec's defaults. Windows are counted in the user's own
 * training days (a "session"), never older than a calendar cap, so the same rules fit a lifter who
 * trains twice a week and one who trains six times a week (the "Generality" section). See
 * [LearnedWindows] and [LearnedSplitsEngine.compute]'s [learnedWindows] for how a window's start is
 * derived from these counts. User-added anchor rows are retained in slot write diffs regardless
 * of these thresholds; the matching threshold still compares every anchor row.
 */
data class LearnedTunables(
    val memorySessions: Int = 20,
    val memoryMaxDays: Int = 84,
    val recentSessions: Int = 10,
    val recentMaxDays: Int = 35,
    val clusterSessions: Int = 16,
    val clusterMaxDays: Int = 56,
    val everydayShare: Double = 0.75,
    val mergeAffinity: Double = 0.5,
    val matchJaccard: Double = 0.5,
    val groupMinLifts: Int = 2,
    val groupMinCoDays: Int = 2,
    val fadeSessions: Int = 2,
    val autoArchiveDays: Int = 28,
    val freshSplitDays: Int = 14,
)

/**
 * The tempo-relative window starts for one [LearnedSplitsEngine.compute] pass (epoch days),
 * computed once by `learnedWindows` and threaded through every reader. A window is
 * `[start, D)` (half-open, see `inWindow`): [memoryStart] gates held lifts' memory check and the
 * attribution range's old end, [recentStart] gates held lifts' recent check and due-alive, and
 * [clusterStart] bounds the days used for affinity, co-occurrence and the everyday share.
 */
data class LearnedWindows(val memoryStart: Int, val recentStart: Int, val clusterStart: Int)

/** What a held lift is to the model. Lifts that are not held have no role. */
enum class LiftRole { MEMBER, EVERYDAY, SOLO }

/**
 * Everything the engine reads. [resolve] maps any exercise id to its keeper (merged_into_id chain);
 * [slots] and [slotMembers] include archived and suppressed slots; [workoutDays] is the stored
 * attribution. [cycleStartDate] numbers occurrences; [archivedCycleEndDates] freezes old days.
 */
data class LearnedInputs(
    val asOf: LocalDate,
    val sets: List<WorkoutSet>,
    val cards: List<WorkoutExerciseCard>,
    val resolve: (Long) -> Long,
    val slots: List<CycleSlot> = emptyList(),
    val slotMembers: List<SplitExercise> = emptyList(),
    val workoutDays: List<WorkoutDay> = emptyList(),
    val archivedCycleEndDates: List<String> = emptyList(),
    val cycleStartDate: String? = null,
    val tunables: LearnedTunables = LearnedTunables(),
    val alternatives: List<SplitAlternative> = emptyList(),
    /** The user's saved boards by slot id; only a seed's board feeds matching ([learningAnchors]). */
    val memberChoices: Map<Long, SplitMemberChoice> = emptyMap(),
    /** False when Smart Suggest is off: the engine is skipped and [manualSplitsModel] publishes the user's own splits. */
    val smartSuggest: Boolean = true,
)

/**
 * One proposal unit: a learned cluster (key "c:<ids>") or a slot-backed group that is not a cluster
 * (key "s:<slotId>": a hand-made seed, or a named learned slot whose cluster dissolved).
 * [slotId] is the persisted cycle_slot id, or a negative in-memory id for a cluster that has no
 * slot yet. [name] is blank when unnamed. [members] are canonical ids, ascending.
 */
data class LearnedGroup(
    val key: String,
    val slotId: Long,
    val name: String,
    val colorIndex: Int,
    val members: List<Long>,
    /** [members] in ticket order: last session's card order, then missing members, then faded. */
    val ticketOrder: List<Long>,
    val coOccurrenceDays: List<String>,
    val sessions: List<String>,
    val fadedMembers: Set<Long>,
    val isCluster: Boolean,
    val learned: Boolean,
    val createdAt: Long,
    val isFresh: Boolean,
    val isDormant: Boolean,
    val suppressed: Boolean,
    val optionalMembers: Set<Long> = emptySet(),
) {
    val lastSession: String? get() = sessions.lastOrNull()
    val isUnnamed: Boolean get() = name.isBlank()
}

/** A training day's computed owner and the lifts on it that are not the owner's members. */
data class DayAttribution(val date: String, val groupKey: String?, val tail: List<Long>)

/** A cycle_slot write: [slotId] null inserts (the model used [modelSlotId], a negative id, meanwhile). */
data class SlotUpsert(
    val slotId: Long?,
    val modelSlotId: Long,
    val name: String,
    val learned: Boolean,
    val memberIds: List<Long>,
)

/** A workout_day write with all four split fields; every field null clears the day. */
data class DayWrite(
    val date: String,
    val slotId: Long?,
    val slotType: Int?,
    val occurrence: Int?,
)

data class LearnedActions(
    val slotUpserts: List<SlotUpsert> = emptyList(),
    val slotArchives: List<Long> = emptyList(),
    val dayWrites: List<DayWrite> = emptyList(),
) {
    val isEmpty: Boolean get() = slotUpserts.isEmpty() && slotArchives.isEmpty() && dayWrites.isEmpty()
}

data class LearnedModel(
    val asOf: String,
    /** First day of the memory window ([LearnedWindows.memoryStart]): the last MEMORY_SESSIONS
     * training days before D, never older than MEMORY_MAX_DAYS. */
    val memoryStart: String,
    /** End date of the most recent archived cycle, or null. */
    val lastArchiveEnd: String?,
    val roles: Map<Long, LiftRole>,
    /** Every group, suppressed ones included; readers use [visibleGroups]. */
    val groups: List<LearnedGroup>,
    /** Group keys: fresh seeds, alive groups, stale groups, then dormant seeds. */
    val dueOrder: List<String>,
    val dueGroupKey: String?,
    /** Computed owner of every training day in [memoryStart, D]. */
    val attribution: Map<String, DayAttribution>,
    /** Latest checked, non-warm-up date before D per canonical lift. */
    val lastWorkingDate: Map<Long, String>,
    /** Checked, non-warm-up lifts per date, canonical (the engine's view of history). */
    val presence: Map<String, Set<Long>>,
    val actions: LearnedActions,
    /** Whether the model is published with auto-created learned splits visible (Smart Suggest on). */
    val smartSuggest: Boolean = true,
    /** Slots never shown with Smart Suggest off: auto-made learned slots and suppressed ones. */
    val hiddenSlotIds: Set<Long> = emptySet(),
) {
    val visibleGroups: List<LearnedGroup> get() = groups.filterNot { it.suppressed }
    val groupsInDueOrder: List<LearnedGroup>
        get() = dueOrder.mapNotNull { key -> groups.firstOrNull { it.key == key } }

    fun group(key: String?): LearnedGroup? = key?.let { k -> groups.firstOrNull { it.key == k } }
    fun groupForSlot(slotId: Long?): LearnedGroup? =
        slotId?.let { id -> groups.firstOrNull { it.slotId == id && !it.suppressed } }

    /** True when stored attribution for [date] is kept as history (older than memory, or archived). */
    fun isFrozen(date: String): Boolean =
        date < memoryStart || (lastArchiveEnd != null && date <= lastArchiveEnd)

    /** The slot a reader shows for [date]. With Smart Suggest off it is the stored split unless that
     * split is hidden; with it on, stored for frozen days and computed otherwise. */
    fun displaySlotId(date: String, stored: WorkoutDay?): Long? {
        if (!smartSuggest) return stored?.cycleSlotId?.takeIf { it !in hiddenSlotIds }
        if (isFrozen(date)) return stored?.cycleSlotId
        val group = group(attribution[date]?.groupKey) ?: return null
        return if (group.suppressed) null else group.slotId
    }

    companion object {
        val EMPTY = LearnedModel(
            asOf = "",
            memoryStart = "",
            lastArchiveEnd = null,
            roles = emptyMap(),
            groups = emptyList(),
            dueOrder = emptyList(),
            dueGroupKey = null,
            attribution = emptyMap(),
            lastWorkingDate = emptyMap(),
            presence = emptyMap(),
            actions = LearnedActions(),
        )
    }
}
