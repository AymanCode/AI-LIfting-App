package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.hiddenWithSmartSuggestOff
import com.ayman.ecolift.data.nextDueCycleSlotId
import com.ayman.ecolift.data.visibleCycleSlots
import kotlinx.datetime.LocalDate

/**
 * The model with Smart Suggest off: the app as it was before learned splits. Every group is one of
 * the user's own rotation slots with its saved member rows; the due split is a simple rotation;
 * nothing is learned into, written or archived. Days the user never assigned keep no split.
 */
internal fun manualSplitsModel(inputs: LearnedInputs): LearnedModel {
    val tunables = inputs.tunables
    val asOf = epochDay(inputs.asOf)
    val history = buildLiftHistory(inputs.sets, inputs.cards, inputs.resolve)
    val windows = learnedWindows(history, asOf, tunables)
    val rotation = inputs.slots.visibleCycleSlots(smartSuggest = false)
    val rotationById = rotation.associateBy { it.id }
    val anchors = anchorsOf(inputs.slotMembers, inputs.resolve)
    val options = inputs.alternatives.map { it.copy(sourceId = inputs.resolve(it.sourceId), alternativeId = inputs.resolve(it.alternativeId)) }
    val storedDays = HashMap<Long, MutableList<String>>()
    for (stored in inputs.workoutDays) {
        val day = epochDay(stored.date)
        if (day < windows.memoryStart || day > asOf) continue
        if (stored.date !in history.presence) continue
        val slotId = stored.cycleSlotId ?: continue
        if (slotId !in rotationById) continue
        storedDays.getOrPut(slotId) { mutableListOf() }.add(stored.date)
    }
    val groups = rotation.map { slot ->
        val members = anchors[slot.id].orEmpty()
        LearnedGroup(
            key = "s:${slot.id}",
            slotId = slot.id,
            name = slot.name,
            colorIndex = slot.colorIndex,
            members = members.sorted(),
            ticketOrder = members,
            coOccurrenceDays = emptyList(),
            sessions = storedDays[slot.id].orEmpty().filter { epochDay(it) < asOf }.sorted(),
            fadedMembers = emptySet(),
            isCluster = false,
            learned = false,
            createdAt = slot.createdAt,
            isFresh = false,
            isDormant = false,
            suppressed = false,
        )
    }
    val attribution = storedDays.entries
        .flatMap { (slotId, dates) ->
            val members = anchors[slotId].orEmpty().toSet()
            dates.map { date ->
                date to DayAttribution(date, "s:$slotId", history.lifts(date).filterNot { it in members }.sorted())
            }
        }
        .toMap()
    val dueSlotId = nextDueCycleSlotId(inputs.asOf.toString(), rotation, inputs.workoutDays, inputs.cycleStartDate)
    val dialStart = dueSlotId?.let { id -> rotation.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
    val dial = if (dialStart == 0) rotation else rotation.drop(dialStart) + rotation.take(dialStart)
    return LearnedModel(
        asOf = inputs.asOf.toString(),
        memoryStart = LocalDate.fromEpochDays(windows.memoryStart).toString(),
        lastArchiveEnd = inputs.archivedCycleEndDates.maxOrNull(),
        roles = emptyMap(),
        groups = groups.map { withRememberedAlternatives(it, options) },
        dueOrder = dial.map { "s:${it.id}" },
        dueGroupKey = dueSlotId?.let { "s:$it" },
        attribution = attribution,
        lastWorkingDate = lastWorkingDates(history, asOf),
        presence = history.presence,
        actions = LearnedActions(),
        smartSuggest = false,
        hiddenSlotIds = inputs.slots.hiddenWithSmartSuggestOff(),
    )
}
