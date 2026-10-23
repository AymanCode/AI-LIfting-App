package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.activeCycleSlots

internal fun jaccard(a: Set<Long>, b: Set<Long>): Double {
    val union = a.union(b).size
    return if (union == 0) 0.0 else a.intersect(b).size.toDouble() / union
}

/** Each slot's anchor (its split_exercise rows) in saved order, folded onto keepers. */
internal fun anchorsOf(slotMembers: List<SplitExercise>, resolve: (Long) -> Long): Map<Long, List<Long>> =
    slotMembers.groupBy { it.splitId }.mapValues { (_, rows) ->
        rows.sortedWith(compareBy({ it.orderIndex }, { it.id })).map { resolve(it.exerciseId) }.distinct()
    }

/**
 * [units]: every cluster (matched to an active slot, matched to a suppressed slot, or new with a
 * negative id) plus the slot-backed groups that are not clusters. [duplicateArchives]: unnamed
 * losers of the duplicate rule. [idleLearned]: unnamed learned slots no cluster matched.
 */
internal data class SlotMatching(
    val units: List<GroupUnit>,
    val duplicateArchives: List<Long>,
    val idleLearned: List<CycleSlot>,
)

private data class Candidate(val cluster: LiftCluster, val slot: CycleSlot, val score: Double)

/**
 * Greedy global matching: every (cluster, active slot) pair with Jaccard(members, anchor) at least
 * MATCH_JACCARD, ordered by Jaccard (high first), then cluster key, then slot id, consumed so each
 * cluster and each slot is used once. Two active learned slots matching one cluster are duplicates:
 * the named one (else the lower id) keeps it; an unnamed loser is archived, a named one is left.
 * An unmatched cluster that matches a suppressed slot stays retired. Unmatched active seeds, and
 * named learned slots that lost their cluster, become slot-backed groups (key "s:<id>").
 */
internal fun matchSlots(
    clusters: List<LiftCluster>,
    slots: List<CycleSlot>,
    anchors: Map<Long, List<Long>>,
    tunables: LearnedTunables,
    alternatives: List<SplitAlternative> = emptyList(),
): SlotMatching {
    val threshold = tunables.matchJaccard - 1e-9
    val active = slots.filter { it.isActive }.sortedBy { it.id }
    val suppressed = slots.filter { it.isSuppressed }.sortedBy { it.id }
    fun anchor(slot: CycleSlot): Set<Long> = anchors[slot.id].orEmpty().toSet()

    val equivalent = alternatives.groupBy { it.splitId }.mapValues { alternativeResolver(it.value) }
    fun similarity(members: Set<Long>, slot: CycleSlot): Double {
        val resolve = equivalent[slot.id] ?: return jaccard(members, anchor(slot))
        return jaccard(members.map(resolve).toSet(), anchor(slot).map(resolve).toSet())
    }

    val candidates = clusters.flatMap { cluster ->
        val members = cluster.members.toSet()
        active.mapNotNull { slot ->
            val score = similarity(members, slot)
            if (score >= threshold) Candidate(cluster, slot, score) else null
        }
    }.sortedWith(
        compareByDescending<Candidate> { it.score }.thenBy { it.cluster.key }.thenBy { it.slot.id },
    )
    val slotOf = LinkedHashMap<String, CycleSlot>()
    val used = HashSet<Long>()
    for (candidate in candidates) {
        if (candidate.cluster.key in slotOf || candidate.slot.id in used) continue
        slotOf[candidate.cluster.key] = candidate.slot
        used += candidate.slot.id
    }

    val duplicates = HashSet<Long>()
    val duplicateArchives = mutableListOf<Long>()
    for (slot in active) {
        if (!slot.learned || slot.id in used) continue
        val cluster = clusters
            .filter { it.key in slotOf }
            .map { it to similarity(it.members.toSet(), slot) }
            .filter { it.second >= threshold }
            .sortedWith(compareByDescending<Pair<LiftCluster, Double>> { it.second }.thenBy { it.first.key })
            .firstOrNull()?.first ?: continue
        val holder = slotOf.getValue(cluster.key)
        if (!holder.learned) continue
        val keeper = listOf(holder, slot).minWith(compareBy<CycleSlot>({ it.name.isBlank() }, { it.id }))
        val loser = if (keeper.id == holder.id) slot else holder
        if (keeper.id != holder.id) {
            slotOf[cluster.key] = keeper
            used += keeper.id
            used -= holder.id
        }
        duplicates += loser.id
        if (loser.name.isBlank()) duplicateArchives += loser.id
    }

    var nextColor = (slots.maxOfOrNull { it.colorIndex } ?: -1) + 1
    var nextTempId = -1L
    val units = mutableListOf<GroupUnit>()
    for (cluster in clusters.sortedBy { it.key }) {
        val matched = slotOf[cluster.key]
        if (matched != null) {
            units += GroupUnit(cluster.key, matched.id, matched, cluster.members, cluster.coOccurrenceDays,
                isCluster = true, suppressed = false, colorIndex = matched.colorIndex)
            continue
        }
        val members = cluster.members.toSet()
        val retired = suppressed.firstOrNull { similarity(members, it) >= threshold }
        if (retired != null) {
            units += GroupUnit(cluster.key, retired.id, retired, cluster.members, cluster.coOccurrenceDays,
                isCluster = true, suppressed = true, colorIndex = retired.colorIndex)
            continue
        }
        units += GroupUnit(cluster.key, nextTempId, null, cluster.members, cluster.coOccurrenceDays,
            isCluster = true, suppressed = false, colorIndex = nextColor)
        nextTempId -= 1
        nextColor += 1
    }
    val idleLearned = mutableListOf<CycleSlot>()
    for (slot in active) {
        if (slot.id in used || slot.id in duplicates) continue
        if (slot.learned && slot.name.isBlank()) {
            idleLearned += slot
            continue
        }
        units += GroupUnit("s:${slot.id}", slot.id, slot, anchors[slot.id].orEmpty().sorted(), emptyList(),
            isCluster = false, suppressed = false, colorIndex = slot.colorIndex)
    }
    return SlotMatching(units.sortedBy { it.key }, duplicateArchives.sorted(), idleLearned)
}

/**
 * Unnamed learned slots no cluster matched are archived once the later of their last stored
 * attributed day and their last anchor write is more than AUTO_ARCHIVE_DAYS before D.
 */
internal fun autoArchives(
    idle: List<CycleSlot>,
    workoutDays: List<WorkoutDay>,
    asOf: Int,
    tunables: LearnedTunables,
): List<Long> = idle.filter { slot ->
    val lastDay = workoutDays.filter { it.cycleSlotId == slot.id }.maxOfOrNull { epochDay(it.date) }
    val lastAnchor = epochDayOfMillis(slot.anchorUpdatedAt ?: slot.createdAt)
    maxOf(lastDay ?: Int.MIN_VALUE, lastAnchor) < asOf - tunables.autoArchiveDays
}.map { it.id }

/**
 * Slot writes, diff only: a new cluster is inserted (learned, blank name); a matched learned slot
 * whose anchor differs from its members is refreshed; a seed that became learned this pass is
 * written as learned with its members. A seed matched to a cluster that has not yet earned two
 * sessions keeps its hand-made list. Learned writes retain user-added anchor rows after the
 * cluster order, in saved order; matching and Jaccard still include every anchor row.
 */
internal fun slotUpserts(
    groups: List<LearnedGroup>,
    units: List<GroupUnit>,
    anchors: Map<Long, List<Long>>,
    userAddedAnchors: Map<Long, List<Long>> = emptyMap(),
): List<SlotUpsert> {
    val unitByKey = units.associateBy { it.key }
    return groups.filterNot { it.suppressed }.mapNotNull { group ->
        val slot = unitByKey.getValue(group.key).slot
        val written = (group.ticketOrder + userAddedAnchors[slot?.id].orEmpty()).distinct()
        when {
            slot == null -> SlotUpsert(null, group.slotId, "", true, written)
            !group.learned -> null
            !slot.learned -> SlotUpsert(slot.id, slot.id, slot.name, true, written)
            group.isCluster && anchors[slot.id].orEmpty().toSet() != written.toSet() ->
                SlotUpsert(slot.id, slot.id, slot.name, true, written)
            else -> null
        }
    }.sortedWith(
        // Inserts first, in the order their colours were assigned (-1, -2, …), so the RPC's
        // "next unused colour" gives each new slot the colour the model already showed.
        compareBy<SlotUpsert>({ it.slotId != null }, { if (it.slotId == null) -it.modelSlotId else it.modelSlotId }),
    )
}

/**
 * Day writes, diff only, for every non-frozen day in [D − MEMORY_DAYS, D] that is a training day
 * or has a stored split: all four fields (slot id, legacy slot type = index among active slots,
 * occurrence = chronological count of that slot's days since the cycle start, alternative_for_date
 * null). Unattributed days clear all four. Days owned by a group with no persisted slot yet wait.
 */
internal fun dayWrites(
    groups: List<LearnedGroup>,
    attribution: Map<String, DayAttribution>,
    inputs: LearnedInputs,
    history: LiftHistory,
    asOf: Int,
    lastArchiveEnd: String?,
    windows: LearnedWindows,
): List<DayWrite> {
    val memoryStart = windows.memoryStart
    fun frozen(date: String): Boolean =
        epochDay(date) < memoryStart || (lastArchiveEnd != null && date <= lastArchiveEnd)
    val stored = inputs.workoutDays.associateBy { it.date }
    val groupByKey = groups.associateBy { it.key }
    val dates = (
        attribution.keys + inputs.workoutDays.filter { day ->
            epochDay(day.date) in memoryStart..asOf &&
                (day.cycleSlotId != null || day.cycleSlotType != null || day.cycleSlotOccurrence != null || day.alternativeForDate != null)
        }.map { it.date }
        ).distinct().sorted()
    val desired = LinkedHashMap<String, Long?>()
    for (date in dates) {
        if (frozen(date)) continue
        val group = groupByKey[attribution[date]?.groupKey]
        val slotId = if (group == null || group.suppressed) null else group.slotId
        if (slotId != null && slotId < 0) continue
        desired[date] = slotId
    }
    val effective = HashMap<String, Long?>()
    for (day in inputs.workoutDays) effective[day.date] = day.cycleSlotId
    effective.putAll(desired)
    val cycleStart = inputs.cycleStartDate ?: history.dates.firstOrNull() ?: inputs.asOf.toString()
    val slotType = inputs.slots.activeCycleSlots().withIndex().associate { (index, slot) -> slot.id to index }
    return desired.mapNotNull { (date, slotId) ->
        val write = if (slotId == null) {
            DayWrite(date, null, null, null)
        } else {
            val occurrence = if (date < cycleStart) 1 else effective.count { (other, owner) ->
                owner == slotId && other >= cycleStart && other <= date
            }
            DayWrite(date, slotId, slotType[slotId], occurrence)
        }
        val current = stored[date]
        val unchanged = current?.cycleSlotId == write.slotId &&
            current?.cycleSlotType == write.slotType &&
            current?.cycleSlotOccurrence == write.occurrence &&
            current?.alternativeForDate == null
        if (unchanged) null else write
    }
}
