package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.web.util.countLabel

import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.epleyEstimate
import com.ayman.ecolift.data.progress.MonthRollup
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.YearMonthW
import kotlinx.datetime.LocalDate

internal fun insightDate(date: LocalDate): String = "${MONTH_ABBREV[date.month.ordinal]} ${date.day}"
internal fun monthName(month: YearMonthW): String = month.label().substringBefore(' ')

/** The comparator for finding the best top set in a month: highest load, then most reps. */
internal val bestTopSetComparator = compareBy<InsightSession> { it.top.loadStored }.thenBy { it.top.reps }

/** Best top set against last month's: heavier -> +N lb, same load with more reps -> +N reps, else null. */
internal fun compareTopSetWin(earlier: TopSet, later: TopSet): ChangelogWin? = when {
    later.loadStored > earlier.loadStored ->
        ChangelogWin("+${WeightLbs.formatStored(later.loadStored - earlier.loadStored)} lb", isReps = false)
    later.loadStored == earlier.loadStored && later.reps > earlier.reps ->
        ChangelogWin("+${later.reps - earlier.reps} rep${if (later.reps - earlier.reps == 1) "" else "s"}", isReps = true)
    else -> null
}

/** Score for plotted values: load * (1 + reps / 30), or reps if unweighted. */
internal fun topSetScore(top: TopSet): Double {
    val lbs = WeightLbs.toLbs(top.loadStored)
    return if (lbs > 0.0) epleyEstimate(lbs, top.reps) else top.reps.toDouble()
}

/** Formats a top set compactly with no spaces: "27.5×8", "BW×6", "BW+45×8". */
internal fun formatCompactTopSet(top: TopSet, isBodyweight: Boolean): String {
    val loadStr = if (isBodyweight) {
        if (top.loadStored == 0) "BW" else "BW+${WeightLbs.formatStored(top.loadStored)}"
    } else {
        WeightLbs.formatStored(top.loadStored)
    }
    return "${loadStr}×${top.reps}"
}

data class MonthPathPoint(
    val dateLabel: String,
    val top: TopSet,
    val setLabel: String,
    val isAnchor: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
)

data class MonthLiftStory(
    val exerciseId: Long,
    val name: String,
    val region: BodyPartRegion,
    val win: ChangelogWin,
    val points: List<MonthPathPoint>,
    val fromLabel: String,
    val toLabel: String,
    val sentence: String = "$name: ${win.label}",
    val evidence: String = "",
    val axisLabel: String = "lb",
    val lowLabel: String = "",
    val highLabel: String = "",
)

data class MonthWeekDay(val label: String, val dateLabel: String, val regions: List<BodyPartRegion>)

data class MonthEntrance(
    val titleMonth: String,
    val year: Int,
    val sessions: Int,
    val workingSets: Int,
    val lead: MonthLiftStory? = null,
    val also: MonthLiftStory? = null,
    val earlyHint: MonthLiftStory? = null,
    val movedUp: Int = 0,
    val titleEnding: String = "done",
    val weekLabel: String? = null,
    val weekSessions: Int = 0,
    val week: List<MonthWeekDay> = emptyList(),
    val leadCandidate: MonthLeadCandidate? = null,
)

/** The month's closest lead-to-be: preserved for compatibility. */
data class MonthLeadCandidate(
    val exerciseId: Long,
    val name: String,
    val region: BodyPartRegion,
    val sessions: Int,
)

internal const val MONTH_LEAD_SESSIONS = 3

/** Lifts trained in only one of the two compared months, sorted by name. */
data class MonthOnlyLifts(val month: String, val names: List<String>) {
    val count: Int get() = names.size
}

data class MonthCompareRow(
    val exerciseId: Long,
    val name: String,
    val region: BodyPartRegion,
    val fromLabel: String,
    val toLabel: String,
    val fromSessions: Int,
    val toSessions: Int,
    val win: ChangelogWin?,
    val deltaLb: Int = 0,
    val deltaReps: Int = 0,
    val fromY: Float = 0.5f,
    val toY: Float = 0.5f,
) {
    val sessionsLabel: String get() = "$fromSessions → $toSessions"
    val dashed: Boolean get() = fromSessions == 1 || toSessions == 1
}

data class MonthComparison(
    val title: String,
    val fromMonth: String,
    val toMonth: String,
    val allCount: Int,
    val rows: List<MonthCompareRow>,
    val onlyBefore: MonthOnlyLifts,
    val onlyAfter: MonthOnlyLifts,
)

internal data class InsightSession(val date: LocalDate, val top: TopSet)
internal data class InsightLift(val exercise: Exercise, val sessions: List<InsightSession>)

/** Index dates once per repository revision; switching months never scans the full log again. */
internal class MonthInsightIndex(sets: List<WorkoutSet>, exercises: List<Exercise>) {
    private val catalog = exercises.associateBy { it.id }
    val workingByDate = sets.asSequence().filter { it.isCheckedWorking }.groupBy { it.date }
    private val dates = workingByDate.keys.associateWith { LocalDate.parse(it) }
    private val byMonth = workingByDate.entries.groupBy { it.key.take(7) }
    private val liftCache = mutableMapOf<YearMonthW, List<InsightLift>>()

    fun trainingDays(month: YearMonthW): Int = byMonth[month.atDay(1).toString().take(7)].orEmpty().size

    fun lifts(month: YearMonthW): List<InsightLift> = liftCache.getOrPut(month) {
        byMonth[month.atDay(1).toString().take(7)].orEmpty()
        .flatMap { it.value }.filter { (it.reps ?: 0) > 0 }
        .groupBy { it.exerciseId }.map { (id, rows) ->
            val exercise = catalog[id] ?: Exercise(id, "Exercise $id")
            InsightLift(exercise.copy(isBodyweight = exercise.isBodyweight || rows.any { it.isBodyweight }), rows.groupBy { it.date }.entries
                .sortedBy { it.key }.map { (date, dayRows) ->
                    // Same raw top set as the report: highest load, then reps, then earliest set.
                    val top = dayRows.maxWith(compareBy<WorkoutSet> { it.weightLbs ?: 0 }
                        .thenBy { it.reps ?: 0 }.thenBy { -it.setNumber })
                    InsightSession(dates.getValue(date), TopSet(top.weightLbs ?: 0, top.reps ?: 0))
                })
        }
    }

    fun entrance(month: YearMonthW, today: LocalDate = LocalDate(2026, 1, 1)): MonthEntrance {
        val priorMonth = month.minusMonths(1)
        val priorLifts = lifts(priorMonth)
        val currentLifts = lifts(month)
        val stories = if (priorLifts.isEmpty()) Triple(null, null, null)
                      else selectMonthStories(priorLifts, currentLifts, month)
        return MonthEntrance(
            titleMonth = monthName(month),
            year = month.year,
            sessions = trainingDays(month),
            workingSets = byMonth[month.atDay(1).toString().take(7)].orEmpty().sumOf { it.value.size },
            lead = stories.first,
            also = stories.second,
            earlyHint = stories.third,
        )
    }

    fun storyLifts(month: YearMonthW): List<MonthLiftStory> {
        val priorLifts = lifts(month.minusMonths(1))
        if (priorLifts.isEmpty()) return emptyList()
        val stories = selectMonthStories(priorLifts, lifts(month), month)
        return listOfNotNull(stories.first, stories.second, stories.third)
    }

    fun comparison(month: YearMonthW): MonthComparison? {
        val priorMonth = month.minusMonths(1)
        val priorLifts = lifts(priorMonth)
        if (priorLifts.isEmpty()) return null
        val currentLifts = lifts(month)
        val stories = selectMonthStories(priorLifts, currentLifts, month)
        val storyIds = setOfNotNull(stories.first?.exerciseId, stories.second?.exerciseId, stories.third?.exerciseId)
        return compareMonthLifts(priorLifts, currentLifts, month, storyIds)
    }
}

/** More load wins; reps win only at identical load. Never compares different exercises' sets. */
internal fun topSetBeats(later: TopSet, earlier: TopSet): Boolean = later.loadStored > earlier.loadStored ||
    (later.loadStored == earlier.loadStored && later.reps > earlier.reps)

internal fun initialMonthFor(today: LocalDate, currentTrainingDays: Int): YearMonthW =
    YearMonthW.from(today).let { if (currentTrainingDays < 2) it.minusMonths(1) else it }

/** Most sessions this month; ties go to the lift trained most recently, then name and id. */
internal fun selectMonthLeadCandidate(lifts: List<InsightLift>): MonthLeadCandidate? = lifts
    .filter { it.sessions.isNotEmpty() }
    .sortedWith(compareByDescending<InsightLift> { it.sessions.size }
        .thenByDescending { it.sessions.last().date.toEpochDays() }
        .thenBy { it.exercise.name.lowercase() }.thenBy { it.exercise.id })
    .firstOrNull()
    ?.let { MonthLeadCandidate(it.exercise.id, it.exercise.name, MonthRollup.regionOf(it.exercise), it.sessions.size) }

private data class StoryCandidate(
    val lift: InsightLift,
    val priorLift: InsightLift,
    val win: ChangelogWin,
    val runLength: Int,
    val gainPct: Double,
    val story: MonthLiftStory,
)

internal fun selectMonthStories(
    before: List<InsightLift>,
    after: List<InsightLift>,
    month: YearMonthW,
): Triple<MonthLiftStory?, MonthLiftStory?, MonthLiftStory?> {
    if (before.isEmpty()) return Triple(null, null, null)

    val priorMap = before.associateBy { it.exercise.id }
    val fromMonthAbbrev = MONTH_ABBREV[month.minusMonths(1).month - 1].uppercase()
    val toMonthAbbrev = MONTH_ABBREV[month.month - 1].uppercase()

    val candidates = mutableListOf<StoryCandidate>()

    for (current in after) {
        val prior = priorMap[current.exercise.id] ?: continue
        if (prior.sessions.isEmpty() || current.sessions.isEmpty()) continue

        val priorBest = prior.sessions.maxWith(bestTopSetComparator).top
        val currentBest = current.sessions.maxWith(bestTopSetComparator).top
        val win = compareTopSetWin(priorBest, currentBest) ?: continue

        // Check the run of sessions from the anchor on:
        // Every session in this month must beat the previous by topSetBeats AND plotted score rises.
        val sessions = current.sessions
        var broken = false
        var prevTop = priorBest
        var prevScore = topSetScore(priorBest)

        for (session in sessions) {
            val sTop = session.top
            val sScore = topSetScore(sTop)
            if (!topSetBeats(sTop, prevTop) || sScore <= prevScore) {
                broken = true
                break
            }
            prevTop = sTop
            prevScore = sScore
        }

        if (broken) continue

        val isBodyweight = prior.exercise.isBodyweight || current.exercise.isBodyweight
        val anchorPoint = MonthPathPoint(
            dateLabel = fromMonthAbbrev,
            top = priorBest,
            setLabel = formatCompactTopSet(priorBest, isBodyweight),
            isAnchor = true,
        )
        val sessionPoints = sessions.mapIndexed { idx, s ->
            MonthPathPoint(
                dateLabel = if (idx == 0) "$toMonthAbbrev ${s.date.day}" else "${s.date.day}",
                top = s.top,
                setLabel = formatCompactTopSet(s.top, isBodyweight),
                isAnchor = false,
            )
        }
        val points = listOf(anchorPoint) + sessionPoints

        val gainPct = if (priorBest.loadStored > 0) {
            (currentBest.loadStored - priorBest.loadStored).toDouble() / priorBest.loadStored
        } else {
            (currentBest.loadStored - priorBest.loadStored).toDouble()
        }

        val story = MonthLiftStory(
            exerciseId = current.exercise.id,
            name = current.exercise.name,
            region = MonthRollup.regionOf(current.exercise),
            win = win,
            points = points,
            fromLabel = formatCompactTopSet(priorBest, isBodyweight),
            toLabel = formatCompactTopSet(currentBest, isBodyweight),
            sentence = "${current.exercise.name}: ${win.label}",
            evidence = "${countLabel(sessions.size, "session")}",
        )

        candidates.add(
            StoryCandidate(
                lift = current,
                priorLift = prior,
                win = win,
                runLength = sessions.size,
                gainPct = gainPct,
                story = story,
            )
        )
    }

    val ranked = candidates.sortedWith(
        compareByDescending<StoryCandidate> { it.runLength }
            .thenByDescending { it.gainPct }
            .thenBy { it.lift.exercise.name.lowercase() }
            .thenBy { it.lift.exercise.id }
    )

    return Triple(
        ranked.getOrNull(0)?.story,
        ranked.getOrNull(1)?.story,
        ranked.getOrNull(2)?.story,
    )
}

internal fun compareMonthLifts(
    before: List<InsightLift>,
    after: List<InsightLift>,
    month: YearMonthW,
    storyExerciseIds: Set<Long> = emptySet(),
): MonthComparison {
    val prior = before.associateBy { it.exercise.id }
    val later = after.associateBy { it.exercise.id }
    val commonIds = prior.keys intersect later.keys
    val allCount = commonIds.size

    val allRows = commonIds.map { id ->
        val a = prior.getValue(id)
        val b = later.getValue(id)
        val from = a.sessions.maxWith(bestTopSetComparator).top
        val to = b.sessions.maxWith(bestTopSetComparator).top
        val bodyweight = a.exercise.isBodyweight || b.exercise.isBodyweight
        val win = compareTopSetWin(from, to)
        MonthCompareRow(
            exerciseId = id,
            name = b.exercise.name,
            region = MonthRollup.regionOf(b.exercise),
            fromLabel = formatCompactTopSet(from, bodyweight),
            toLabel = formatCompactTopSet(to, bodyweight),
            fromSessions = a.sessions.size,
            toSessions = b.sessions.size,
            win = win,
            deltaLb = to.loadStored - from.loadStored,
            deltaReps = to.reps - from.reps,
        )
    }

    // Story lifts are excluded from compare rows (ALL n still counts them).
    val filtered = allRows.filter { it.exerciseId !in storyExerciseIds }

    // Compare rows ordered: lb wins largest first, then rep wins largest first, then no-chip rows by name.
    val sorted = filtered.sortedWith { rowA, rowB ->
        val wA = rowA.win
        val wB = rowB.win
        when {
            wA != null && wB != null -> {
                if (!wA.isReps && !wB.isReps) {
                    if (rowB.deltaLb != rowA.deltaLb) rowB.deltaLb.compareTo(rowA.deltaLb)
                    else rowA.name.compareTo(rowB.name, ignoreCase = true)
                } else if (!wA.isReps && wB.isReps) {
                    -1
                } else if (wA.isReps && !wB.isReps) {
                    1
                } else {
                    if (rowB.deltaReps != rowA.deltaReps) rowB.deltaReps.compareTo(rowA.deltaReps)
                    else rowA.name.compareTo(rowB.name, ignoreCase = true)
                }
            }
            wA != null && wB == null -> -1
            wA == null && wB != null -> 1
            else -> rowA.name.compareTo(rowB.name, ignoreCase = true)
        }
    }

    fun only(source: Map<Long, InsightLift>, other: Map<Long, InsightLift>, label: String): MonthOnlyLifts =
        MonthOnlyLifts(label, (source.keys - other.keys).map { source.getValue(it).exercise.name }.sorted())

    val fromMonth = monthName(month.minusMonths(1))
    val toMonth = monthName(month)
    return MonthComparison(
        title = "$fromMonth → $toMonth",
        fromMonth = fromMonth,
        toMonth = toMonth,
        allCount = allCount,
        rows = sorted,
        onlyBefore = only(prior, later, fromMonth),
        onlyAfter = only(later, prior, toMonth),
    )
}
