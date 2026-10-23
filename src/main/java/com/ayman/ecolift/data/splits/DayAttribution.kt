package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToLong

data class DayInfo(
    val splitId: Long?,
    /** The split's colour ([com.ayman.ecolift.data.CycleSlot.colorIndex]), stable for its lifetime. */
    val visualIndex: Int?,
    val volumeLbs: Long,
    val exerciseCount: Int,
    /** The split's name (region words when unnamed); null when the day has no split. */
    val label: String? = null,
)

/**
 * Each worked day's split, colour and label (spec 2026-09-28): the learned model's attribution
 * for recent days and the stored split for frozen ones ([LearnedModel.displaySlotId]), resolved
 * over every slot including retired ones, so ordering never recolours the calendar and old days
 * keep their label. A group whose slot is not saved yet takes its colour from the model.
 */
internal fun buildDayInfoMap(
    completedSetsByDate: Map<String, List<WorkoutSet>>,
    setVolume: (WorkoutSet) -> Double,
    workoutDays: List<WorkoutDay>,
    slots: List<CycleSlot>,
    model: LearnedModel = LearnedModel.EMPTY,
    groupLabel: (LearnedGroup) -> String = { it.name },
): Map<LocalDate, DayInfo> {
    val slotById = slots.associateBy { it.id }
    val workoutDayByDate = workoutDays.associateBy { it.date }

    return completedSetsByDate.mapNotNull { (dateIso, sets) ->
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return@mapNotNull null
        val completedSets = sets.filter { it.completed }
        if (completedSets.isEmpty()) return@mapNotNull null

        val stored = workoutDayByDate[dateIso]
        val slotId = if (model.asOf.isEmpty() && model.smartSuggest) stored?.cycleSlotId else model.displaySlotId(dateIso, stored)
        val slot = slotId?.let(slotById::get)
        val group = model.groupForSlot(slotId)
        val colour = slot?.colorIndex ?: group?.colorIndex
        val label = when {
            slot != null && slot.name.isNotBlank() -> slot.name
            group != null -> groupLabel(group)
            slot != null -> "Split workout"
            else -> null
        }
        date to DayInfo(
            splitId = slotId.takeIf { colour != null },
            visualIndex = colour,
            volumeLbs = completedSets.sumOf(setVolume).roundToLong(),
            exerciseCount = completedSets.map { it.exerciseId }.distinct().size,
            label = label.takeIf { colour != null },
        )
    }.toMap()
}

internal fun volumeBrightThreshold(dayVolumes: Collection<Long>): Long {
    if (dayVolumes.isEmpty()) return 0L
    val sorted = dayVolumes.sorted()
    val index = (ceil(sorted.size * 0.75).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
}
