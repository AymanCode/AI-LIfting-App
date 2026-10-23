package com.ayman.ecolift.web.supabase.sync

import com.ayman.ecolift.data.WorkoutDay
import kotlinx.serialization.Serializable

@Serializable
data class DayPendingWrite(
    val date: String,
    val cycleSlotId: Long? = null,
    val cycleSlotType: Int? = null,
    val cycleSlotOccurrence: Int? = null,
    val alternativeForDate: String? = null,
    val ensureOnly: Boolean = false,
)

fun WorkoutDay.toPendingWrite() = DayPendingWrite(
    date = date,
    cycleSlotId = cycleSlotId,
    cycleSlotType = cycleSlotType,
    cycleSlotOccurrence = cycleSlotOccurrence,
    alternativeForDate = alternativeForDate,
)

fun DayPendingWrite.toWorkoutDay() = WorkoutDay(
    date = date,
    cycleSlotType = cycleSlotType,
    cycleSlotOccurrence = cycleSlotOccurrence,
    alternativeForDate = alternativeForDate,
    cycleSlotId = cycleSlotId,
)

fun List<DayPendingWrite>.coalesce(op: DayPendingWrite): List<DayPendingWrite> =
    if (op.ensureOnly) {
        if (any { it.date == op.date }) this else this + op
    } else {
        filterNot { it.date == op.date } + op
    }

fun overlayWorkoutDays(
    server: List<WorkoutDay>,
    pending: List<DayPendingWrite>,
): List<WorkoutDay> = pending.fold(server) { days, op ->
    if (op.ensureOnly) {
        if (days.any { it.date == op.date }) days else days + op.toWorkoutDay()
    } else {
        days.filterNot { it.date == op.date } + op.toWorkoutDay()
    }
}
