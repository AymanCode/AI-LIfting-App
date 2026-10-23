package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.effectiveSplitMembers
import com.ayman.ecolift.data.splits.learnedGroupLabel
import com.ayman.ecolift.data.splits.regionLabel
import com.ayman.ecolift.data.visibleCycleSlots
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * One lift's numbers on a split, from checked sets strictly before today: the latest session's top
 * set, the working volume of its last six sessions (oldest first), and the change from the first
 * of those to the last, null with fewer than two sessions or a zero first one.
 */
data class SplitLiftMetrics(
    val topSetLabel: String?,
    val workingVolume: List<Float>,
    val deltaPercent: Int?,
)

internal fun splitLiftMetrics(exercise: Exercise, history: List<WorkoutSet>, before: LocalDate): SplitLiftMetrics {
    val cutoff = before.toString()
    val volume = history.filter { it.date < cutoff && it.isCheckedWorking }
        .groupBy { it.date }.entries.sortedBy { it.key }.takeLast(6)
        .map { WorkoutMetrics.workingVolumeLbs(it.value).toFloat() }
    val first = volume.firstOrNull()
    val delta = if (volume.size < 2 || first == null || first == 0f) null
    else ((volume.last() - first) / first * 100f).roundToInt()
    val top = reportBefore(history, exercise.isBodyweight, before).topSet
    return SplitLiftMetrics(top?.let { formatTicketTopSet(it, exercise.isBodyweight) }, volume, delta)
}

data class SplitCardMemberUi(
    val exerciseId: Long,
    val name: String,
    val metrics: SplitLiftMetrics,
)

/**
 * One split on the Splits tab: a learned group or a hand-made split. [members] are IN THIS SPLIT in
 * order and [otherOptions] the rest, both from [effectiveSplitMembers]. [creationNumber] is the
 * slot's rank among every slot the user has had; [slotId] below zero is a group not saved yet
 * (no number, no editor). [estimatedMinutes] is 0 before the first session.
 */
data class SplitGroupCardUi(
    val slotId: Long,
    val name: String,
    val colorIndex: Int,
    val isUnnamed: Boolean,
    val isDue: Boolean,
    val members: List<SplitCardMemberUi>,
    val otherOptions: List<SplitCardMemberUi>,
    val sessions: Int,
    val lastSessionDate: String?,
    val creationNumber: Int?,
    val estimatedMinutes: Int = 0,
) {
    val notTrainedYet: Boolean get() = lastSessionDate == null
    val isSaved: Boolean get() = slotId > 0
}

/**
 * The cards in due order, then every active slot the model does not show that is named or has a
 * saved board (a duplicate left for the user, or a board whose cluster went elsewhere).
 * [slots] are every slot, archived and retired included, so creation numbers never shift.
 */
internal fun buildSplitGroupCards(
    model: LearnedModel,
    today: String,
    exercises: List<Exercise>,
    sets: List<WorkoutSet>,
    canonicalId: (Long) -> Long,
    slots: List<CycleSlot>,
    savedRows: List<SplitExercise> = emptyList(),
    choices: Map<Long, SplitMemberChoice> = emptyMap(),
): List<SplitGroupCardUi> {
    val byId = exercises.associateBy { it.id }
    val setsByLift = sets.groupBy { canonicalId(it.exerciseId) }
    val day = LocalDate.parse(today)
    val metrics = HashMap<Long, SplitLiftMetrics>()
    fun lifts(ids: List<Long>) = ids.mapNotNull { lift ->
        val exercise = byId[lift] ?: return@mapNotNull null
        SplitCardMemberUi(lift, exercise.name, metrics.getOrPut(lift) { splitLiftMetrics(exercise, setsByLift[lift].orEmpty(), day) })
    }
    val creationNumber = slots.sortedBy { it.id }.withIndex().associate { (index, slot) -> slot.id to index + 1 }
    val checkedByDate = sets.filter { it.completed }.groupingBy { it.date }.eachCount()
    fun minutes(last: String?): Int = if (last == null) 0 else ((checkedByDate[last] ?: 0) * 3).coerceAtLeast(15)
    fun card(slotId: Long, name: String, colorIndex: Int, isUnnamed: Boolean, isDue: Boolean, board: EffectiveSplitMembers, sessions: List<String>) =
        SplitGroupCardUi(
            slotId = slotId,
            name = name,
            colorIndex = colorIndex,
            isUnnamed = isUnnamed,
            isDue = isDue,
            members = lifts(board.inSplit),
            otherOptions = lifts(board.otherOptions),
            sessions = sessions.size,
            lastSessionDate = sessions.lastOrNull(),
            creationNumber = creationNumber[slotId],
            estimatedMinutes = minutes(sessions.lastOrNull()),
        )
    val cards = model.groupsInDueOrder.filterNot { it.suppressed }.map { group ->
        card(group.slotId, learnedGroupLabel(group, byId), group.colorIndex, group.isUnnamed, group.key == model.dueGroupKey,
            effectiveSplitMembers(group.slotId, group, savedRows, choices[group.slotId], canonicalId), group.sessions)
    }
    val shown = cards.map { it.slotId }.toSet()
    val visibleSlotIds = slots.visibleCycleSlots(model.smartSuggest).map { it.id }.toSet()
    val orphans = slots.filter { it.isActive && it.id in visibleSlotIds && it.id !in shown && (it.name.isNotBlank() || it.id in choices) }
        .sortedWith(compareBy({ it.orderIndex }, { it.id }))
        .map { slot ->
            val board = effectiveSplitMembers(slot.id, null, savedRows, choices[slot.id], canonicalId)
            card(slot.id, slot.name.trim().ifBlank { regionLabel(board.inSplit, byId) }, slot.colorIndex, slot.name.isBlank(),
                isDue = false, board = board, sessions = emptyList())
        }
    return cards + orphans
}
