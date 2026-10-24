package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.progress.EquivalenceUnit
import com.ayman.ecolift.data.progress.EquivalenceUnits
import com.ayman.ecolift.data.progress.MonthFigure
import com.ayman.ecolift.data.progress.MonthFigures
import com.ayman.ecolift.data.progress.MonthRollup
import com.ayman.ecolift.data.progress.MonthRollupResult
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.util.YearMonthW
import com.ayman.ecolift.web.util.countLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/** A month later than the present cannot be stepped into. */
fun canGoLater(selected: YearMonthW, now: YearMonthW): Boolean =
    selected.year < now.year || (selected.year == now.year && selected.month < now.month)

/** A month earlier than the first one ever logged cannot be stepped into. */
fun canGoEarlier(selected: YearMonthW, earliest: YearMonthW): Boolean =
    selected.year > earliest.year || (selected.year == earliest.year && selected.month > earliest.month)

/**
 * The earliest month holding a checked set, or null when nothing has been logged.
 *
 * The back arrow stops here. Without it the sheet walks backwards forever through months
 * that were never trained, which reads as a broken control rather than an empty month.
 */
fun earliestMonthWithSessions(sets: List<WorkoutSet>): YearMonthW? =
    sets.asSequence()
        .filter { it.completed }
        .minOfOrNull { it.date }
        ?.let { YearMonthW(it.take(4).toInt(), it.substring(5, 7).toInt()) }

/**
 * A stable, monotonically increasing index for the week containing [isoDate].
 * Seeds the fallback equivalence unit so it holds steady all week and rotates on Monday.
 */
fun weekIndexFor(isoDate: String): Int {
    val weekStart = LocalDate.parse(WorkoutDates.startOfWeek(isoDate))
    return (weekStart.toEpochDays() / 7).toInt()
}

data class MonthSheetUiState(
    val yearMonth: YearMonthW,
    val monthLabel: String,
    val rollup: MonthRollupResult?,
    val figures: List<MonthFigure>,
    val unit: EquivalenceUnit,
    val unitLabel: String,
    val canGoEarlier: Boolean,
    val canGoLater: Boolean,
    val entrance: MonthEntrance? = null,
    val comparison: MonthComparison? = null,
    val openingNote: String? = null,
)

/** Repository rows the rollup needs, snapshotted together so the rollup only rebuilds once per change. */
private data class MonthRepoSnapshot(
    val sets: List<WorkoutSet>,
    val exercises: List<Exercise>,
    val workoutDays: List<WorkoutDay>,
    val slots: List<CycleSlot>,
    val userBodyweightLbs: Int?,
    val insights: MonthInsightIndex,
)

class MonthViewModel(
    private val repos: AppRepos,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow<YearMonthW?>(null)
    private var openingMonth: YearMonthW? = null

    // Nothing fetches this yet -- a later task wires the model call. EquivalenceUnits.forWeek
    // falls back to a bundled unit meanwhile, which is also what every offline render uses.
    private val chosenUnit = MutableStateFlow<EquivalenceUnit?>(null)

    private val rollupSlots = combine(
        repos.workout.observeAllCycleSlots(),
        repos.learnedSplits.model.map { it.hiddenSlotIds }.distinctUntilChanged(),
    ) { slots, hiddenSlotIds ->
        if (hiddenSlotIds.isEmpty()) slots else slots.filter { it.id !in hiddenSlotIds }
    }

    private val repoSnapshot = combine(
        repos.sets.sets,
        repos.exercises.exercises,
        repos.workout.observeAllWorkoutDays(),
        rollupSlots,
        repos.workout.observeUserBodyweightLbs(),
    ) { sets, exercises, workoutDays, slots, userBodyweightLbs ->
        MonthRepoSnapshot(
            sets = sets,
            exercises = exercises,
            workoutDays = workoutDays,
            slots = slots,
            userBodyweightLbs = userBodyweightLbs,
            insights = MonthInsightIndex(sets, exercises),
        )
    }

    val uiState: StateFlow<MonthSheetUiState> = combine(
        selectedMonth,
        chosenUnit,
        repoSnapshot,
    ) { selection, chosenUnit, snapshot ->
        val today = LocalDate.parse(WorkoutDates.today())
        val now = YearMonthW.from(today)
        val initial = openingMonth ?: initialMonthFor(today, snapshot.insights.trainingDays(now)).also { openingMonth = it }
        val yearMonth = selection ?: initial
        val rollup = MonthRollup.build(
            year = yearMonth.year,
            month = yearMonth.month,
            sets = snapshot.sets,
            exercises = snapshot.exercises,
            workoutDays = snapshot.workoutDays,
            slots = snapshot.slots,
            userBodyweightLbs = snapshot.userBodyweightLbs,
        )
        val unit = EquivalenceUnits.forWeek(weekIndexFor(WorkoutDates.today()), chosenUnit)

        val earliest = earliestMonthWithSessions(snapshot.sets)

        MonthSheetUiState(
            yearMonth = yearMonth,
            monthLabel = yearMonth.label(),
            rollup = rollup,
            figures = MonthFigures.build(rollup),
            unit = unit,
            unitLabel = EquivalenceUnits.label(rollup.totalLoadLbs, unit),
            canGoEarlier = earliest != null && canGoEarlier(selected = yearMonth, earliest = earliest),
            canGoLater = canGoLater(selected = yearMonth, now = YearMonthW.now()),
            entrance = snapshot.insights.entrance(yearMonth, today),
            comparison = snapshot.insights.comparison(yearMonth),
            openingNote = if (selection == null && initial != now)
                "${com.ayman.ecolift.web.util.MONTH_ABBREV[today.month.ordinal]} is ${countLabel(today.day, "day")} old · showing ${monthName(initial)}" else null,
        )
    }.stateIn(
        scope = viewModelScope,
        // Retain the projection while this back-stack ViewModel lives; revisits don't rebuild history.
        started = SharingStarted.Lazily,
        initialValue = run {
            val now = YearMonthW.now()
            val initialUnit = EquivalenceUnits.forWeek(weekIndexFor(WorkoutDates.today()), null)
            MonthSheetUiState(
                yearMonth = now,
                monthLabel = now.label(),
                rollup = null,
                figures = emptyList(),
                unit = initialUnit,
                unitLabel = EquivalenceUnits.label(0.0, initialUnit),
                canGoEarlier = true,
                canGoLater = canGoLater(selected = now, now = now),
            )
        },
    )

    /**
     * One month either way. December and January are adjacent here, so walking off the end
     * of a year carries the sheet into the next one without a separate year control.
     */
    fun stepMonth(delta: Int) {
        selectedMonth.value = (selectedMonth.value ?: uiState.value.yearMonth).plusMonths(delta)
    }
}
