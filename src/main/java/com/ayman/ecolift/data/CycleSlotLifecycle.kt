package com.ayman.ecolift.data

internal fun List<CycleSlot>.activeCycleSlots(): List<CycleSlot> =
    filter(CycleSlot::isActive)
        .sortedWith(compareBy<CycleSlot> { it.orderIndex }.thenBy { it.id })

internal fun List<CycleSlot>.visibleCycleSlots(smartSuggest: Boolean): List<CycleSlot> =
    activeCycleSlots().filter { smartSuggest || it.isUserOwned }

/** Slots the off model never shows: auto-made learned slots, and any slot the user suppressed. */
internal fun List<CycleSlot>.hiddenWithSmartSuggestOff(): Set<Long> =
    filter { !it.isUserOwned || it.isSuppressed }.map { it.id }.toSet()

/**
 * The split due on [date] with Smart Suggest off, the rotation from before learned splits: the
 * slot after the one on the latest assigned day before [date] in this cycle, wrapping; the first
 * slot when no earlier day of the cycle has one. [slots] are the rotation, in order.
 */
internal fun nextDueCycleSlotId(
    date: String,
    slots: List<CycleSlot>,
    workoutDays: List<WorkoutDay>,
    cycleStartDate: String?,
): Long? {
    if (slots.isEmpty()) return null
    val latestSlotId = workoutDays
        .filter {
            it.date < date &&
                (cycleStartDate == null || it.date >= cycleStartDate) &&
                it.resolvedCycleSlotId(slots) != null
        }
        .maxByOrNull(WorkoutDay::date)
        .resolvedCycleSlotId(slots)
        ?: return slots.first().id
    val index = slots.indexOfFirst { it.id == latestSlotId }
    return slots[(index + 1) % slots.size].id
}

internal fun List<CycleSlot>.nextActiveCycleSlotOrder(): Int =
    (activeCycleSlots().maxOfOrNull(CycleSlot::orderIndex) ?: -1) + 1

internal fun List<CycleSlot>.retireCycleSlots(
    slotIds: Set<Long>,
    archivedAt: Long,
): List<CycleSlot> = map { slot ->
    if (slot.isActive && slot.id in slotIds) slot.copy(archivedAt = archivedAt) else slot
}

internal fun List<SplitExercise>.forActiveCycleSlots(slots: List<CycleSlot>): List<SplitExercise> {
    val activeSlotIds = slots.activeCycleSlots().mapTo(hashSetOf<Long>(), CycleSlot::id)
    return filter { it.splitId in activeSlotIds }
}

internal fun Cycle.withActiveCycleSlotCount(slots: List<CycleSlot>): Cycle {
    val activeCount = slots.count(CycleSlot::isActive)
    val maxIndex = (activeCount - 1).coerceAtLeast(0)
    return copy(
        numTypes = activeCount.coerceAtLeast(1),
        nextSessionType = nextSessionType?.coerceIn(0, maxIndex),
    )
}
