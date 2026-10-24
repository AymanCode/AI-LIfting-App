package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet

/** The day exactly as it was before a bulk action, for Undo. */
data class DaySnapshot(
    val date: String,
    val sets: List<WorkoutSet>,
    /** Exercise ids in card order; sets are restored in this order so positions come back. */
    val cardOrder: List<Long>,
    val cardModes: Map<Long, WorkoutCardDisplayMode>,
    val day: WorkoutDay?,
)

sealed interface DayUndoTint {
    data class Split(val slotId: Long) : DayUndoTint
    data class Lift(val exerciseId: Long) : DayUndoTint
    data object Ink : DayUndoTint
}

/** Name line = [headline] + " " + [verb]; [headline] data parts take the tint colour for splits. */
data class DayUndoCopy(
    val headline: List<DataPart>,
    val verb: String,
    val detail: List<DataPart>,
    val tint: DayUndoTint,
)

sealed interface DayUndoRestore {
    data class RestoreDay(val snapshot: DaySnapshot) : DayUndoRestore
    data class DeleteAdded(val syncIds: Set<String>) : DayUndoRestore
}

data class DayUndoOffer(
    val id: Long,
    val date: String,
    val copy: DayUndoCopy,
    val restore: DayUndoRestore,
    val afterSignature: DayContentSignature,
)

sealed interface DayUndoState {
    val offer: DayUndoOffer
    data class Offered(override val offer: DayUndoOffer) : DayUndoState
    data class Restoring(override val offer: DayUndoOffer) : DayUndoState
    data class Restored(override val offer: DayUndoOffer) : DayUndoState
}

class DayUndoStale : IllegalStateException("The workout changed after this Undo was offered")

data class DayContentSignature(
    val sets: List<String>,
    val cardOrder: List<Long>,
    val cardModes: Map<Long, WorkoutCardDisplayMode>,
)

/** Content only: ids, sync ids, revisions and raw card positions are sync bookkeeping, not changes. */
fun dayContentSignature(date: String, sets: List<WorkoutSet>, cards: List<WorkoutExerciseCard>): DayContentSignature {
    val daySets = sets.filter { it.date == date }
    val dayCards = cards.filter { it.date == date && daySets.any { s -> s.exerciseId == it.exerciseId } }
        .sortedWith(compareBy({ it.position }, { it.id }))
    return DayContentSignature(
        sets = daySets.map {
            "${it.exerciseId}|${it.setNumber}|${it.weightLbs}|${it.reps}|${it.isBodyweight}|${it.completed}|${it.isWarmup}|${it.restTimeSeconds}"
        }.sorted(),
        cardOrder = dayCards.map { it.exerciseId },
        cardModes = dayCards.associate { it.exerciseId to it.displayMode },
    )
}

private fun count(n: Int, one: String, many: String) = listOf(data("$n"), filler(if (n == 1) " $one" else " $many"))
private val dot = filler(" · ")

fun startedCopy(splitName: String, slotId: Long?, liftCount: Int, setCount: Int) = DayUndoCopy(
    headline = listOf(data(splitName)), verb = "started",
    detail = count(liftCount, "lift", "lifts") + dot + count(setCount, "set", "sets"),
    tint = slotId?.let { DayUndoTint.Split(it) } ?: DayUndoTint.Ink,
)

fun loadedCopy(splitName: String, slotId: Long?, previousSplitName: String?, replacedSetCount: Int, checkedCount: Int) = DayUndoCopy(
    headline = listOf(data(splitName)), verb = "loaded",
    detail = listOf(filler("replaced ")) +
        (if (previousSplitName != null) listOf(data(previousSplitName)) else count(replacedSetCount, "set", "sets")) +
        (if (checkedCount > 0) listOf(dot) + count(checkedCount, "checked set", "checked sets") else emptyList()),
    tint = slotId?.let { DayUndoTint.Split(it) } ?: DayUndoTint.Ink,
)

fun clearedCopy(splitName: String?, slotId: Long?, setCount: Int, checkedCount: Int) = DayUndoCopy(
    headline = listOf(data(splitName ?: "Workout")), verb = "cleared",
    detail = count(setCount, "set", "sets") + (if (checkedCount > 0) listOf(dot, data("$checkedCount"), filler(" checked")) else emptyList()),
    tint = slotId?.let { DayUndoTint.Split(it) } ?: DayUndoTint.Ink,
)

fun addedCopy(lifts: List<Pair<Long, String>>, setCount: Int): DayUndoCopy =
    if (lifts.size == 1) DayUndoCopy(
        headline = listOf(data(lifts.single().second)), verb = "added",
        detail = count(setCount, "set", "sets"), tint = DayUndoTint.Lift(lifts.single().first),
    ) else DayUndoCopy(
        headline = listOf(data("${lifts.size}"), filler(" lifts")), verb = "added",
        detail = listOf(filler(lifts.joinToString(", ") { it.second })), tint = DayUndoTint.Ink,
    )

fun liftRemovedCopy(exerciseId: Long, name: String, setCount: Int, checkedCount: Int) = DayUndoCopy(
    headline = listOf(data(name)), verb = "removed",
    detail = count(setCount, "set", "sets") + (if (checkedCount > 0) listOf(dot, data("$checkedCount"), filler(" checked")) else emptyList()),
    tint = DayUndoTint.Lift(exerciseId),
)

fun setRemovedCopy(exerciseId: Long, liftName: String, ordinal: Int, isWarmup: Boolean) = DayUndoCopy(
    headline = if (isWarmup) listOf(data("Warm-up")) else listOf(filler("Set "), data("$ordinal")),
    verb = "removed",
    detail = listOf(filler(liftName)),
    tint = DayUndoTint.Lift(exerciseId),
)
