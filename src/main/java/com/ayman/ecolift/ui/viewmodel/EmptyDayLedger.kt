package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.BodyPartRegionMapper
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.LearnedModel
import kotlinx.datetime.LocalDate

/** The placeholder target rule, isolated so a later progression rule can replace it. */
fun numberToBeat(topSet: TopSet?): TopSet? = topSet?.copy(reps = topSet.reps + 1)

data class LedgerSetUi(val label: String, val isTop: Boolean)
data class SlotSessionUi(val date: String, val month: String, val day: String)
data class SlotAlternativeUi(
    val exerciseId: Long,
    val name: String,
    val sessionDots: List<Boolean>,
    val topSetLabel: String?,
    val lastDateLabel: String?,
)

/** Shared sheet data. The caller owns the primary action and any split membership write. */
data class SwapSheetUi(
    val groupKey: String,
    val usualExerciseId: Long,
    val currentExerciseId: Long,
    val title: String,
    val sessionRange: String?,
    val sessions: List<SlotSessionUi>,
    val rows: List<SlotAlternativeUi>,
    val canUndo: Boolean,
    val colorIndex: Int,
    val groupName: String = "",
    val unavailableExerciseIds: Set<Long> = emptySet(),
)

internal data class LedgerLiftHistoryUi(
    val exerciseId: Long,
    val name: String,
    val primaryGroup: String?,
    val region: String,
    val setsByDate: Map<String, List<LedgerSetUi>>,
    val lastDate: String?,
    val lastDateLabel: String?,
    val topSetLabel: String?,
    val toBeatLabel: String?,
)

/** Programs and ledger history consume the very same settled inputs, without duplicate passes. */
internal data class LearnedProposalSnapshot(
    val date: String,
    val model: LearnedModel,
    val exercises: List<Exercise>,
    val sets: List<WorkoutSet>,
    val programs: List<ClipboardProgramUi>,
) {
    companion object { val EMPTY = LearnedProposalSnapshot("", LearnedModel.EMPTY, emptyList(), emptyList(), emptyList()) }
}

/** History is built only while the empty-day surface subscribes, from immutable repository snapshots. */
internal data class EmptyDayLedgerSnapshot(
    val date: String,
    val programs: List<ClipboardProgramUi>,
    val lifts: Map<Long, LedgerLiftHistoryUi>,
    val alternatives: Map<Pair<String, Long>, List<SlotAlternativeUi>>,
    val sessions: Map<String, List<SlotSessionUi>>,
    val ranges: Map<String, String?>,
) {
    companion object { val EMPTY = EmptyDayLedgerSnapshot("", emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap()) }
}

data class EmptyDayLedgerUi(
    val date: String = "",
    val programs: List<ClipboardProgramUi> = emptyList(),
    val sheets: Map<Pair<String, Long>, SwapSheetUi> = emptyMap(),
)

private val ledgerMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
internal fun ledgerDateLabel(iso: String): String {
    val date = LocalDate.parse(iso)
    return "${ledgerMonths[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/** Read-only, pure lookup; the ViewModel exposes the same seam to the future card chip. */
internal fun slotAlternatives(snapshot: EmptyDayLedgerSnapshot, groupKey: String, exerciseId: Long): List<SlotAlternativeUi> =
    snapshot.alternatives[groupKey to exerciseId].orEmpty()

internal fun buildEmptyDayLedgerSnapshot(
    date: String,
    programs: List<ClipboardProgramUi>,
    model: LearnedModel,
    exercises: List<Exercise>,
    sets: List<WorkoutSet>,
    canonicalId: (Long) -> Long,
): EmptyDayLedgerSnapshot {
    if (programs.isEmpty()) return EmptyDayLedgerSnapshot.EMPTY
    val shownDay = LocalDate.parse(date).toEpochDays()
    val prior = sets.filter { it.date < date }
    val byLift = prior.groupBy { canonicalId(it.exerciseId) }
    val ledgerDates = programs.mapNotNull { model.group(it.groupKey)?.lastSession }.toSet()
    val lifts = exercises.associate { exercise ->
        val history = byLift[exercise.id].orEmpty().filter(WorkoutMetrics::isStrengthEligible)
            .groupBy { it.date }
        val latest = history.entries.maxByOrNull { it.key }
        fun top(rows: List<WorkoutSet>) = rows.maxWithOrNull(compareBy<WorkoutSet> { it.weightLbs ?: 0 }.thenBy { it.reps ?: 0 })
        val topSet = latest?.value?.let(::top)?.let { TopSet(it.weightLbs ?: 0, it.reps ?: 0) }
        val primary = primaryGroupOf(exercise.muscleGroups)
        exercise.id to LedgerLiftHistoryUi(
            exercise.id, exercise.name, primary,
            BodyPartRegionMapper.mapGroupToRegion(primary.orEmpty()).displayName,
            history.filterKeys { it in ledgerDates || it == latest?.key }.mapValues { (_, rows) ->
                val topRow = top(rows)
                // Persisted set order is the oldest-first order within a session; id breaks ties.
                rows.sortedWith(compareBy<WorkoutSet> { it.setNumber }.thenBy { it.id }).map { set ->
                    LedgerSetUi(formatTicketTopSet(TopSet(set.weightLbs ?: 0, set.reps ?: 0), exercise.isBodyweight), set === topRow)
                }
            },
            latest?.key, latest?.key?.let(::ledgerDateLabel),
            topSet?.let { formatTicketTopSet(it, exercise.isBodyweight) },
            numberToBeat(topSet)?.let { formatTicketTopSet(it, exercise.isBodyweight) },
        )
    }
    val checked = prior.filter { it.completed && it.date >= model.memoryStart }.groupBy { it.date }
        .mapValues { (_, rows) -> rows.mapTo(mutableSetOf()) { canonicalId(it.exerciseId) } }
    val alternatives = mutableMapOf<Pair<String, Long>, List<SlotAlternativeUi>>()
    val sessions = mutableMapOf<String, List<SlotSessionUi>>()
    val ranges = mutableMapOf<String, String?>()
    val activeCount = model.visibleGroups.count { !it.isDormant }
    val ledgerPrograms = programs.map { program ->
        val group = model.group(program.groupKey)
        val dates = model.attribution.values.filter {
            it.groupKey == program.groupKey && it.date >= model.memoryStart && it.date < date
        }.map { it.date }.distinct().sorted()
        sessions[program.groupKey] = dates.map { iso ->
            val parsed = LocalDate.parse(iso)
            SlotSessionUi(iso, ledgerMonths[parsed.monthNumber - 1].uppercase(), parsed.dayOfMonth.toString())
        }
        ranges[program.groupKey] = dates.firstOrNull()?.let { "${ledgerDateLabel(it)} – ${ledgerDateLabel(dates.last())}" }
        val used = dates.flatMap { checked[it].orEmpty() }.toSet()
        val mostRecent = mutableMapOf<Long, String>()
        dates.forEach { day -> checked[day].orEmpty().forEach { mostRecent[it] = day } }
        fun alternative(lift: LedgerLiftHistoryUi) = SlotAlternativeUi(
            lift.exerciseId, lift.name, dates.map { lift.exerciseId in checked[it].orEmpty() }, lift.topSetLabel, lift.lastDateLabel,
        )
        val byRegion = used.mapNotNull(lifts::get).groupBy { it.region }.mapValues { (_, members) ->
            members.sortedWith(compareByDescending<LedgerLiftHistoryUi> { mostRecent[it.exerciseId].orEmpty() }.thenBy { it.exerciseId }).map(::alternative)
        }
        // Lists are shared by all used lifts in a region. Unused catalog lifts get their own row,
        // ensuring search picks and never-trained seeds are always present as today's pick.
        lifts.values.forEach { lift ->
            val regionRows = byRegion[lift.region].orEmpty()
            alternatives[program.groupKey to lift.exerciseId] = if (lift.exerciseId in used) regionRows else regionRows + alternative(lift)
        }
        program.copy(
            ledgerLastDateLabel = group?.lastSession?.let { ledgerDateLabel(it).uppercase() },
            daysSinceLastSession = group?.lastSession?.let { (shownDay - LocalDate.parse(it).toEpochDays()).toInt() },
            activeGroupCount = activeCount,
            rows = program.rows.map { row -> ledgerRow(row, lifts[row.exerciseId], group?.lastSession, swapped = false) },
        )
    }
    return EmptyDayLedgerSnapshot(date, ledgerPrograms, lifts, alternatives, sessions, ranges)
}

private fun ledgerRow(row: TicketRowUi, lift: LedgerLiftHistoryUi?, groupLastDate: String?, swapped: Boolean): TicketRowUi {
    if (lift == null) return row
    val setDate = if (swapped) lift.lastDate else groupLastDate
    return row.copy(
        exerciseId = lift.exerciseId, name = lift.name, primaryGroup = lift.primaryGroup,
        usualExerciseId = row.usualExerciseId, usualName = if (swapped) row.name else null,
        topSetLabel = lift.topSetLabel, lastDate = lift.lastDate,
        lastSets = setDate?.let { lift.setsByDate[it] }.orEmpty(), lastSetsDateLabel = if (swapped) lift.lastDateLabel else null,
        toBeatLabel = lift.toBeatLabel,
    )
}

/** Substitution never moves a row or changes its usual inclusion identity. */
internal fun applyLedgerSwaps(snapshot: EmptyDayLedgerSnapshot, swapsByDay: Map<String, Map<Long, Long>>): EmptyDayLedgerUi {
    val sheets = mutableMapOf<Pair<String, Long>, SwapSheetUi>()
    val programs = snapshot.programs.map { program ->
        val swaps = swapsByDay[ticketInclusionKey(snapshot.date, program.groupKey)].orEmpty()
        val rows = program.rows.map { usual ->
            val chosen = swaps[usual.usualExerciseId]?.let(snapshot.lifts::get)
            val base = if (chosen == null || chosen.exerciseId == usual.exerciseId) usual else ledgerRow(usual, chosen, null, swapped = true)
            val row = base.copy(alternativeCount = slotAlternatives(snapshot, program.groupKey, base.exerciseId).size)
            sheets[program.groupKey to usual.usualExerciseId] = SwapSheetUi(
                program.groupKey, usual.usualExerciseId, row.exerciseId,
                "${snapshot.lifts[row.exerciseId]?.region ?: "Lift"} · ${program.name}",
                snapshot.ranges[program.groupKey], snapshot.sessions[program.groupKey].orEmpty(),
                slotAlternatives(snapshot, program.groupKey, row.exerciseId), row.usualName != null, program.colorIndex,
            )
            row
        }
        program.copy(rows = rows)
    }
    return EmptyDayLedgerUi(snapshot.date, programs, sheets)
}
