package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet

/**
 * A split's lifts as the user sees them: IN THIS SPLIT in order, then OTHER OPTIONS. Every id
 * appears once; an id named in both lists stays IN.
 */
@ConsistentCopyVisibility
data class EffectiveSplitMembers private constructor(
    val inSplit: List<Long>,
    val otherOptions: List<Long>,
) {
    companion object {
        fun of(inSplit: List<Long>, otherOptions: List<Long>): EffectiveSplitMembers {
            val included = inSplit.distinct()
            val inSet = included.toSet()
            return EffectiveSplitMembers(included, otherOptions.distinct().filterNot { it in inSet })
        }
    }
}

/**
 * The user's saved board for one persisted split (split_member_choice). A present board with no IN
 * lifts means "deliberately empty", not "automatic"; only a missing board is automatic.
 */
data class SplitMemberChoice(val splitId: Long, val revision: Long, val board: EffectiveSplitMembers)

/**
 * The saved boards. Loading until the first read settles, so Start never mistakes an unread board
 * for an automatic one. [Ready.choices] is what screens show, boards still being saved included;
 * [Ready.pendingSlots] names those slots; [Ready.confirmed] holds only what the server returned,
 * the only boards learning may read.
 */
sealed interface SplitChoicesState {
    data object Loading : SplitChoicesState
    data class Ready(
        val choices: Map<Long, SplitMemberChoice>,
        val pendingSlots: Set<Long> = emptySet(),
        val confirmed: Map<Long, SplitMemberChoice>,
    ) : SplitChoicesState {
        val settled: Boolean get() = pendingSlots.isEmpty()
    }
}

/** True when boards are read and no save is out: the engine may persist what it computed. */
val SplitChoicesState.isSettled: Boolean get() = this is SplitChoicesState.Ready && settled

/** One edit on a board. Each is idempotent: repeating it gives the same board. */
sealed interface SplitMemberEdit {
    data class Include(val exerciseId: Long) : SplitMemberEdit
    data class Exclude(val exerciseId: Long) : SplitMemberEdit
    /** [beforeExerciseId] null moves to the end. */
    data class MoveBefore(val exerciseId: Long, val beforeExerciseId: Long?) : SplitMemberEdit
    data class Replace(val exerciseIds: List<Long>) : SplitMemberEdit
}

/**
 * The one membership rule the card, the editor, the Log ticket and Start all read. Without a
 * board, IN is the engine's ticket order less its faded and optional members, which become
 * options with any user-added anchor rows the group does not hold; a slot with no group uses its
 * saved rows. With a board, IN is exactly the board's IN list, and every lift it does not mention
 * (a new engine discovery included) joins the end of its options.
 */
internal fun effectiveSplitMembers(
    slotId: Long,
    group: LearnedGroup?,
    savedRows: List<SplitExercise>,
    choice: SplitMemberChoice?,
    canonicalId: (Long) -> Long,
): EffectiveSplitMembers {
    val rows = savedRows.filter { it.splitId == slotId }
        .sortedWith(compareBy({ it.orderIndex }, { it.id }))
        .map { canonicalId(it.exerciseId) }
    val automatic = if (group == null) {
        EffectiveSplitMembers.of(rows, emptyList())
    } else {
        val userAdded = savedRows.filter { it.splitId == slotId && it.userAdded }
            .sortedWith(compareBy({ it.orderIndex }, { it.id }))
            .map { canonicalId(it.exerciseId) }
            .filterNot { it in group.members }
        val faded = group.ticketOrder.filter { it in group.fadedMembers && it !in group.optionalMembers }
        val optional = group.ticketOrder.filter { it in group.optionalMembers }
        EffectiveSplitMembers.of(
            group.ticketOrder.filterNot { it in group.fadedMembers || it in group.optionalMembers },
            faded + optional + userAdded,
        )
    }
    if (choice == null) return automatic
    return EffectiveSplitMembers.of(
        choice.board.inSplit.map(canonicalId),
        choice.board.otherOptions.map(canonicalId) + automatic.inSplit + automatic.otherOptions,
    )
}

/**
 * [edit] applied to [board]. Include appends; Exclude moves an IN lift to the end of the options;
 * MoveBefore reorders IN only and ignores a lift or target that is not IN; Replace makes the given
 * lifts IN and puts the previous IN lifts it drops at the front of the options.
 */
internal fun applyEdit(board: EffectiveSplitMembers, edit: SplitMemberEdit): EffectiveSplitMembers = when (edit) {
    is SplitMemberEdit.Include ->
        if (edit.exerciseId in board.inSplit) board
        else EffectiveSplitMembers.of(board.inSplit + edit.exerciseId, board.otherOptions)
    is SplitMemberEdit.Exclude ->
        if (edit.exerciseId !in board.inSplit) board
        else EffectiveSplitMembers.of(board.inSplit - edit.exerciseId, board.otherOptions + edit.exerciseId)
    is SplitMemberEdit.MoveBefore -> {
        val lift = edit.exerciseId
        val before = edit.beforeExerciseId
        if (lift !in board.inSplit || lift == before || (before != null && before !in board.inSplit)) {
            board
        } else {
            val rest = board.inSplit - lift
            val at = if (before == null) rest.size else rest.indexOf(before)
            EffectiveSplitMembers.of(rest.take(at) + lift + rest.drop(at), board.otherOptions)
        }
    }
    is SplitMemberEdit.Replace -> {
        val next = edit.exerciseIds.distinct()
        EffectiveSplitMembers.of(next, board.inSplit.filterNot { it in next } + board.otherOptions)
    }
}

/**
 * The anchors the engine matches clusters against. A seed (not yet learned) with a board learns
 * from its board's IN lifts, so a split built only in the editor can earn its first sessions.
 * Every learned slot keeps its raw anchor rows: the user's picks never move attribution.
 */
internal fun learningAnchors(
    slots: List<CycleSlot>,
    savedRows: List<SplitExercise>,
    choices: Map<Long, SplitMemberChoice>,
    canonicalId: (Long) -> Long,
): Map<Long, List<Long>> {
    val seedBoards = slots.filterNot { it.learned }.mapNotNull { slot ->
        choices[slot.id]?.let { slot.id to it.board.inSplit.map(canonicalId).distinct() }
    }
    return anchorsOf(savedRows, canonicalId) + seedBoards
}

/**
 * The lifts of one workout day for "From a workout": canonical lifts with a checked set that day
 * (a checked warm-up counts), by card position, then first set number, then id.
 */
internal fun workoutLiftsFor(
    date: String,
    sets: List<WorkoutSet>,
    cards: List<WorkoutExerciseCard>,
    canonicalId: (Long) -> Long,
): List<Long> {
    val firstSet = HashMap<Long, Int>()
    for (set in sets) {
        if (set.date != date || !set.completed) continue
        val lift = canonicalId(set.exerciseId)
        firstSet[lift] = minOf(firstSet[lift] ?: Int.MAX_VALUE, set.setNumber)
    }
    val position = HashMap<Long, Int>()
    for (card in cards) {
        if (card.date != date) continue
        val lift = canonicalId(card.exerciseId)
        if (lift in firstSet) position[lift] = minOf(position[lift] ?: Int.MAX_VALUE, card.position)
    }
    return firstSet.keys.sortedWith(compareBy({ position[it] ?: Int.MAX_VALUE }, { firstSet.getValue(it) }, { it }))
}
