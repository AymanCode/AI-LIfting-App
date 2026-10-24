package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.LearnedGroup
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.effectiveSplitMembers
import com.ayman.ecolift.data.splits.learnedGroupLabel
import com.ayman.ecolift.data.splits.regionLabel
import com.ayman.ecolift.data.visibleCycleSlots
import kotlinx.datetime.LocalDate

/** The number to beat, as a suggestion. Never a negative stamp. */
sealed interface StakeUi {
    /** The top set reached the reps that usually earn a step: the next load. */
    data class StepUp(val targetLoadStored: Int, val isBodyweight: Boolean) : StakeUi
    /** The best reps at this weight is one or two above last time: the gap. */
    data class RepsToRecord(val reps: Int) : StakeUi
    /** The best reps ever done at this weight. */
    data class Beat(val reps: Int) : StakeUi
}

enum class TicketRowKind { MEMBER, FADED, TAIL, OPTION }

/** One ticket row: a lift, its last top set, what to beat, and whether Start includes it by default. */
data class TicketRowUi(
    val exerciseId: Long,
    val name: String,
    val primaryGroup: String?,
    val kind: TicketRowKind,
    /** "22.5×8", "BW×8", "BW+25×5"; null for a lift never trained. */
    val topSetLabel: String?,
    /** The lift's latest working session (ISO), shown on faded rows. */
    val lastDate: String?,
    val stake: StakeUi?,
    /** Ledger projection; the usual id remains the inclusion/ordering key after a day-only swap. */
    val usualExerciseId: Long = exerciseId,
    val usualName: String? = null,
    val lastSets: List<LedgerSetUi> = emptyList(),
    val lastSetsDateLabel: String? = null,
    val toBeatLabel: String? = null,
    /** Lifts this slot has used (the row's own lift included); read by the swap button's label. */
    val alternativeCount: Int = 0,
) {
    val includedByDefault: Boolean get() = kind == TicketRowKind.MEMBER
}

/** "22.5×8", "BW×8", "BW+25×5": the report's stored-tenths weights in the ticket's compact form. */
fun formatTicketTopSet(top: TopSet, isBodyweight: Boolean): String = when {
    isBodyweight && top.loadStored > 0 -> "BW+${WeightLbs.formatStored(top.loadStored)}×${top.reps}"
    isBodyweight -> "BW×${top.reps}"
    else -> "${WeightLbs.formatStored(top.loadStored)}×${top.reps}"
}

/**
 * The stake from the lift's full history before [before] through the changelog engine: "step up"
 * when the latest top set reached the trigger reps of a learned step, else the gap to the best
 * reps at that weight when it is one or two, else the best reps at that weight to beat.
 */
fun stakeFor(history: List<WorkoutSet>, isBodyweight: Boolean, before: LocalDate): StakeUi? =
    ticketHistoryBefore(history, isBodyweight, before).stake

/** The changelog engine over every session strictly before [before]. */
internal fun reportBefore(history: List<WorkoutSet>, isBodyweight: Boolean, before: LocalDate): ExerciseReport =
    buildExerciseReport(
        history.filter { it.date < before.toString() },
        isBodyweight,
        LocalDate(2000, 1, 1),
        LocalDate.fromEpochDays(before.toEpochDays().toInt() - 1),
    )

internal data class TicketHistory(
    val topSet: TopSet?,
    val lastSessionDate: String?,
    val stake: StakeUi?,
)

internal fun ticketHistoryBefore(history: List<WorkoutSet>, isBodyweight: Boolean, before: LocalDate): TicketHistory {
    val cutoff = before.toString()
    val end = LocalDate.fromEpochDays(before.toEpochDays().toInt() - 1)
    val sessions = reportSessions(history.filter { it.date < cutoff }, isBodyweight, end)
    val last = sessions.lastOrNull() ?: return TicketHistory(null, null, null)
    val top = last.takeIf { it.date >= "2000-01-01" }?.top
        ?: return TicketHistory(null, last.date, null)
    val suggestedReps = sessions.asSequence().flatMap { it.sets.asSequence() }
        .filter { it.loadStored == top.loadStored }.maxOf { it.reps } + 1
    return TicketHistory(top, last.date, stakeForTopSet(top, nextStepFor(sessions), suggestedReps, isBodyweight))
}

internal fun stakeFrom(report: ExerciseReport, isBodyweight: Boolean): StakeUi? =
    stakeForTopSet(report.topSet, report.nextStep, report.suggestedReps, isBodyweight)

private fun stakeForTopSet(top: TopSet?, step: NextStep?, suggestedReps: Int?, isBodyweight: Boolean): StakeUi? {
    if (top == null) return null
    if (step != null && top.reps >= step.triggerReps) return StakeUi.StepUp(step.targetLoadStored, isBodyweight)
    val best = (suggestedReps ?: return null) - 1
    val gap = best - top.reps
    return if (gap in 1..2) StakeUi.RepsToRecord(gap) else StakeUi.Beat(best)
}

/** The stake as a data line: numbers are data, the words filler. */
fun stakeParts(stake: StakeUi): List<DataPart> = when (stake) {
    is StakeUi.StepUp -> listOf(filler("step up → "), data(formatLoad(stake.targetLoadStored, stake.isBodyweight)))
    is StakeUi.RepsToRecord -> listOf(data("${stake.reps}"), filler(if (stake.reps == 1) " rep to a record" else " reps to a record"))
    is StakeUi.Beat -> listOf(filler("beat "), data("${stake.reps}"))
}

fun stakeText(stake: StakeUi): String = stakeParts(stake).joinToString("") { it.text }

/**
 * A split's ticket rows: IN THIS SPLIT as members, then its other options, then the lifts that were
 * also on the group's last session. Without a saved board an option the engine faded stays FADED.
 * Every row reads history strictly before [date].
 */
internal fun ticketRows(
    group: LearnedGroup?,
    members: EffectiveSplitMembers,
    chosen: Boolean,
    model: LearnedModel,
    date: String,
    exercisesById: Map<Long, Exercise>,
    setsByLift: Map<Long, List<WorkoutSet>>,
): List<TicketRowUi> {
    val day = LocalDate.parse(date)
    val listed = (members.inSplit + members.otherOptions).toSet()
    val tail = group?.lastSession?.let { model.attribution[it]?.tail }.orEmpty().filterNot { it in listed }
    fun optionKind(lift: Long) =
        if (!chosen && group != null && lift in group.fadedMembers && lift !in group.optionalMembers) TicketRowKind.FADED else TicketRowKind.OPTION
    val entries = members.inSplit.map { it to TicketRowKind.MEMBER } +
        members.otherOptions.map { it to optionKind(it) } +
        tail.map { it to TicketRowKind.TAIL }
    return entries.mapNotNull { (lift, kind) ->
        val exercise = exercisesById[lift] ?: return@mapNotNull null
        val report = ticketHistoryBefore(setsByLift[lift].orEmpty(), exercise.isBodyweight, day)
        TicketRowUi(
            exerciseId = lift,
            name = exercise.name,
            primaryGroup = primaryGroupOf(exercise.muscleGroups),
            kind = kind,
            topSetLabel = report.topSet?.let { formatTicketTopSet(it, exercise.isBodyweight) },
            lastDate = report.lastSessionDate,
            stake = report.stake,
        )
    }
}

/**
 * Every visible group and hand-made split as a program, in due order, then each active slot with a
 * saved board that no group shows: [ClipboardProgramUi.lifts] are the rows Start includes by
 * default, [ClipboardProgramUi.rows] every row the ticket shows.
 */
internal fun buildLearnedPrograms(
    model: LearnedModel,
    date: String,
    exercises: List<Exercise>,
    sets: List<WorkoutSet>,
    slots: List<CycleSlot> = emptyList(),
    savedRows: List<SplitExercise> = emptyList(),
    choices: Map<Long, SplitMemberChoice> = emptyMap(),
    canonicalId: (Long) -> Long,
): List<ClipboardProgramUi> {
    val exercisesById = exercises.associateBy { it.id }
    val setsByLift = sets.groupBy { canonicalId(it.exerciseId) }
    val visibleSlotIds = slots.visibleCycleSlots(model.smartSuggest).map { it.id }.toSet()
    val groups = model.groupsInDueOrder.filterNot { it.suppressed }
    val programs = groups.mapIndexed { rank, group ->
        val members = effectiveSplitMembers(group.slotId, group, savedRows, choices[group.slotId], canonicalId)
        val rows = ticketRows(group, members, group.slotId in choices, model, date, exercisesById, setsByLift)
        ClipboardProgramUi(
            slotId = group.slotId,
            name = learnedGroupLabel(group, exercisesById),
            position = rank,
            isExpected = group.key == model.dueGroupKey,
            lifts = rows.filter { it.includedByDefault }.map { row ->
                ClipboardProgramLiftUi(row.exerciseId, row.name, row.primaryGroup, topSetLabel = row.topSetLabel)
            },
            lastLoadedDate = group.lastSession,
            lastSetCount = group.lastSession?.let { last -> sets.count { it.date == last && it.completed } } ?: 0,
            colorIndex = group.colorIndex,
            groupKey = group.key,
            rows = rows,
            isUnnamed = group.isUnnamed,
        )
    }
    val shown = groups.map { it.slotId }.toSet()
    val orphans = slots.filter { it.isActive && it.id in visibleSlotIds && it.id in choices && it.id !in shown }.mapIndexed { index, slot ->
        val members = effectiveSplitMembers(slot.id, null, savedRows, choices[slot.id], canonicalId)
        val rows = ticketRows(null, members, chosen = true, model, date, exercisesById, setsByLift)
        ClipboardProgramUi(
            slotId = slot.id,
            name = slot.name.trim().ifBlank { regionLabel(members.inSplit, exercisesById) },
            position = programs.size + index,
            isExpected = false,
            lifts = rows.filter { it.includedByDefault }.map { row ->
                ClipboardProgramLiftUi(row.exerciseId, row.name, row.primaryGroup, topSetLabel = row.topSetLabel)
            },
            colorIndex = slot.colorIndex,
            groupKey = "s:${slot.id}",
            rows = rows,
            isUnnamed = slot.name.isBlank(),
        )
    }
    return programs + orphans
}
