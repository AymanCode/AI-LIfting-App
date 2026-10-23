package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.data.progress.MonthRollup
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class EmphasisLift(val exerciseId: Long, val name: String, val sets: Int, val barFraction: Float)
data class RegionWeek(
    val start: LocalDate,
    val label: String,
    val endLabel: String,
    val sets: Map<BodyPartRegion, Int>,
    val total: Int,
    val lifts: Map<BodyPartRegion, List<EmphasisLift>>,
    val sessions: Map<BodyPartRegion, Int>,
)
data class EmphasisHeadline(
    val region: BodyPartRegion,
    val week: RegionWeek,
    val count: Int,
    /** The other week with the most sets for [region]: the previous best when [count] tops it, otherwise the best itself. */
    val runnerUp: RegionWeek?,
) {
    val runnerUpCount: Int get() = runnerUp?.sets?.get(region) ?: 0
    /** Only a week that tops every other week; a tie or a region's first week beats nothing. */
    val record: Boolean get() = runnerUp != null && count > runnerUpCount
}
data class EmphasisUiState(
    val weeks: List<RegionWeek> = emptyList(),
    val history: List<RegionWeek> = emptyList(),
    val headline: EmphasisHeadline? = null,
    val regions: List<BodyPartRegion> = emptyList(),
    val chartMax: Int = 10,
    val rangeLabel: String = "",
    val historyRangeLabel: String = "",
)

/** The region's name on Emphasis; exercises with no muscle group read as "Other", not "Unknown". */
internal fun emphasisRegionName(region: BodyPartRegion): String =
    if (region == BodyPartRegion.UNKNOWN) "Other" else region.displayName

/** "SEP 21 – 27", or "SEP 28 – OCT 4" when the week crosses a month. */
internal fun emphasisWeekSpan(week: RegionWeek): String {
    val sameMonth = week.label.substringBefore(' ') == week.endLabel.substringBefore(' ')
    return "${week.label} – ${if (sameMonth) week.endLabel.substringAfter(' ') else week.endLabel}".uppercase()
}

/** Deterministic: newest week wins a count tie, then the legend order. Completed weeks only. */
internal fun emphasisHeadline(history: List<RegionWeek>, recent: List<RegionWeek>, regions: List<BodyPartRegion>): EmphasisHeadline? {
    val choices = recent.flatMap { week -> regions.map { region -> region to week } }
    val best = choices.sortedWith(compareByDescending<Pair<BodyPartRegion, RegionWeek>> { (r, w) -> w.sets[r] ?: 0 }
        .thenByDescending { it.second.start }.thenBy { regions.indexOf(it.first) }).firstOrNull() ?: return null
    val (region, week) = best
    val count = week.sets[region] ?: 0
    if (count == 0) return null
    val runnerUp = history.filter { it.start != week.start && (it.sets[region] ?: 0) > 0 }
        .maxWithOrNull(compareBy<RegionWeek> { it.sets[region] ?: 0 }.thenBy { it.start })
    return EmphasisHeadline(region, week, count, runnerUp)
}

/** One pass over working sets: never filter/parse the full log once for each week. */
internal fun buildEmphasis(sets: List<WorkoutSet>, exercises: List<Exercise>, today: LocalDate): EmphasisUiState {
    val catalog = exercises.associateBy { it.id }
    val currentMonday = today.toEpochDays() - today.dayOfWeek.ordinal
    val lastMonday = currentMonday - 7
    val byWeek = mutableMapOf<Long, MutableMap<BodyPartRegion, MutableMap<Long, Int>>>()
    val sessionDates = mutableMapOf<Pair<Long, BodyPartRegion>, MutableSet<String>>()
    val regionsById = catalog.mapValues { MonthRollup.regionOf(it.value) }
    sets.asSequence().filter { it.isCheckedWorking }.groupBy { it.date }.forEach { (iso, daySets) ->
        val date = LocalDate.parse(iso)
        val monday = date.toEpochDays() - date.dayOfWeek.ordinal
        if (monday >= currentMonday) return@forEach
        val bucket = byWeek.getOrPut(monday) { mutableMapOf() }
        daySets.forEach { set ->
            val region = regionsById[set.exerciseId] ?: BodyPartRegion.UNKNOWN
            val lifts = bucket.getOrPut(region) { mutableMapOf() }
            lifts[set.exerciseId] = (lifts[set.exerciseId] ?: 0) + 1
            sessionDates.getOrPut(monday to region) { mutableSetOf() }.add(iso)
        }
    }
    val historyStart = byWeek.keys.minOrNull() ?: lastMonday
    val firstMonday = minOf(historyStart, lastMonday - 49)
    val timeline = (0L..((lastMonday - firstMonday) / 7)).map { offset ->
        val epoch = firstMonday + offset * 7
        val liftCounts = byWeek[epoch].orEmpty()
        val counts = liftCounts.mapValues { (_, rows) -> rows.values.sum() }
        val lifts = liftCounts.mapValues { (_, rows) ->
            val max = rows.values.maxOrNull()?.coerceAtLeast(1) ?: 1
            rows.map { (id, n) -> EmphasisLift(id, catalog[id]?.name ?: "Unknown lift", n, n.toFloat() / max) }
                .sortedWith(compareByDescending<EmphasisLift> { it.sets }.thenBy { it.name }.thenBy { it.exerciseId })
        }
        val start = LocalDate.fromEpochDays(epoch)
        RegionWeek(start, insightDate(start), insightDate(LocalDate.fromEpochDays(epoch + 6)), counts, counts.values.sum(),
            lifts, counts.keys.associateWith { sessionDates[epoch to it]?.size ?: 0 })
    }
    val recent = timeline.takeLast(8)
    val history = timeline.filter { it.start.toEpochDays() >= historyStart }
    val regions = listOf(BodyPartRegion.ARMS, BodyPartRegion.SHOULDERS, BodyPartRegion.CHEST, BodyPartRegion.BACK, BodyPartRegion.LEGS) +
        listOf(BodyPartRegion.CORE, BodyPartRegion.UNKNOWN).filter { r -> history.any { (it.sets[r] ?: 0) > 0 } }
    val max = (((recent.maxOfOrNull { it.total } ?: 0) + 9) / 10 * 10).coerceAtLeast(10)
    return EmphasisUiState(recent, history, emphasisHeadline(history, recent, regions), regions, max,
        "${recent.first().label} – ${recent.last().endLabel}",
        "${history.first().label} – ${history.last().endLabel}")
}

/** Shared at the navigation host; subscribing on a second visit reuses the same history projection. */
class EmphasisViewModel(repos: AppRepos) : ViewModel() {
    // Load, reps and rest edits can't change set counts. Suppress those revisions before bucketing.
    private val countedSets = repos.sets.sets.map(::emphasisCountRows).distinctUntilChanged()
    val uiState = combine(countedSets, repos.exercises.exercises) { sets, exercises ->
        buildEmphasis(sets, exercises, todayLocalDate())
    }.stateIn(viewModelScope, SharingStarted.Lazily, EmphasisUiState())
}

internal fun emphasisCountRows(sets: List<WorkoutSet>): List<WorkoutSet> = sets.asSequence().filter { it.isCheckedWorking }
    .map { WorkoutSet(exerciseId = it.exerciseId, date = it.date, setNumber = 0, completed = true) }.toList()
