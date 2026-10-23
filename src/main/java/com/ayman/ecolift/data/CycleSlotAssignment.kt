package com.ayman.ecolift.data

internal fun WorkoutDay?.hasCycleSlotAssignment(): Boolean =
    this?.cycleSlotId != null || this?.cycleSlotType != null

internal fun WorkoutDay?.resolvedCycleSlotId(slots: List<CycleSlot>): Long? {
    val day = this ?: return null
    day.cycleSlotId?.let { slotId ->
        return slotId.takeIf { candidate -> slots.any { it.id == candidate } }
    }
    val orderedSlots = slots.sortedWith(compareBy<CycleSlot> { it.orderIndex }.thenBy { it.id })
    return day.cycleSlotType?.let { orderedSlots.getOrNull(it)?.id }
}
