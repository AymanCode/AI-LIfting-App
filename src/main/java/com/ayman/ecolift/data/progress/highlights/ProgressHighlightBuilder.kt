package com.ayman.ecolift.data.progress.highlights

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** Initial product rules; tune eligibility here, never in composition. Canonical geometry stays gated. */
object HighlightTunables {
    const val FRESHNESS_DAYS = 21
    const val MAX_CARDS = 5
    const val MAX_BEAT_GAP_DAYS = 45
    const val MIN_BEST_HELD_DAYS = 14
    const val MIN_REPEAT_SESSIONS = 2
    const val APPROVED_WEEK_COUNT = 4
    const val APPROVED_RUNNING_TOTAL_SESSION_COUNT = 8
    const val MAX_MATCH_CUT_NAME_POSTER_UNITS = 14.5
    const val MAX_APPROVED_MATCH_CUT_REPS = 9
    const val MAX_APPROVED_MATCH_CUT_WEIGHT_STORED = 9_990
    const val MAX_APPROVED_MATCH_CUT_DELTA_STORED = 90
    const val MAX_APPROVED_SESSION_REPS = 99
    const val MAX_APPROVED_TOTAL_REPS = 999
    const val MAX_APPROVED_SET_COUNT = 99
}

private val FULL_MONTH_LABELS = listOf(
    "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
    "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
)

private val MONTH_LABELS = listOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

/**
 * Builds only the canonical poster families that the supplied real records can honestly support.
 *
 * Invalid dates, future-dated records, and unchecked sets cannot become highlight evidence. Frame
 * order is stable so opening the reel with identical inputs always yields identical paging.
 */
fun buildHighlightReelSnapshot(
    exercises: List<Exercise>,
    sets: List<WorkoutSet>,
    today: LocalDate,
): HighlightReelSnapshot {
    val datedSets = sets.mapNotNull { workoutSet ->
        if (!workoutSet.completed || workoutSet.isWarmup) return@mapNotNull null
        val date = runCatching { LocalDate.parse(workoutSet.date) }.getOrNull()
            ?: return@mapNotNull null
        if (date > today) return@mapNotNull null
        DatedSet(workoutSet, date)
    }

    val observations = matchObservations(exercises, datedSets)
    val freshnessStart = today.minusDays(HighlightTunables.FRESHNESS_DAYS - 1L)
    val recent = datedSets.filter { it.date >= freshnessStart }
    val exercisesById = exercises.associateBy(Exercise::id)
    // All-workout chapters keep their original population; the report link names a contributor.
    val reportId = recent.groupingBy { resolveCanonicalExerciseId(it.set.exerciseId, exercisesById) }
        .eachCount().entries.filter { it.key != null }
        .sortedWith(compareByDescending<Map.Entry<Long?, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()?.key
    val reportExercise = reportId?.let(exercisesById::get)
    val cards = mutableListOf<HighlightCard>()
    buildMatchCuts(observations).forEach { frame ->
        cards += card(frame, HighlightKind.MATCH_CUT, frame.nowDate, frame.canonicalExerciseId,
            frame.exerciseName, freshTag(frame.nowDate, today),
            "NEW LOAD BEST", formatWeekRange(frame.beforeDate, frame.nowDate))
    }
    val beats = buildBeats(observations, freshnessStart)
    // Month ranking also sees older jumps in that month, even when their cards are now stale.
    val monthJumps = observations.groupBy { it.canonicalExerciseId }.values.flatMap { lift ->
        eligibleBeatImprovements(lift)
    }
    beats.forEach { frame ->
        // One fact once: the canonical Match Cut owns an identical eligible comparison.
        if (cards.any {
            (it.frame as? ProgressHighlightFrame.MatchCut)?.let { match ->
                match.canonicalExerciseId == frame.canonicalExerciseId &&
                    match.reps == frame.now.reps &&
                    match.beforeDate == frame.then.date &&
                    match.nowDate == frame.now.date
            } == true
        }) return@forEach
        val monthBeats = monthJumps.filter { it.now.date.monthNumber == frame.now.date.monthNumber && it.now.date.year == frame.now.date.year }
        val largest = monthBeats.maxOf { it.deltaWeightLbsStored }
        val joint = monthBeats.filter { it.deltaWeightLbsStored == largest }.map { it.now.canonicalExerciseId }.distinct().size > 1
        val month = FULL_MONTH_LABELS[frame.now.date.monthNumber - 1]
        val liftCount = observations.filter { it.date.monthNumber == frame.now.date.monthNumber && it.date.year == frame.now.date.year }
            .map { it.canonicalExerciseId }.distinct().size
        val isLargest = frame.deltaWeightLbsStored == largest
        cards += card(frame, HighlightKind.BEAT, frame.now.date, frame.canonicalExerciseId, frame.exerciseName,
            if (isLargest) "${if (joint) "JOINT BIGGEST" else "BIGGEST"} OF $month" else "BIGGEST FOR THIS LIFT",
            if (isLargest) "${if (joint) "JOINT BIGGEST" else "BIGGEST"} SAME-REPS JUMP OF ${countLabel(liftCount, "LIFT")} IN $month"
            else "BIGGEST JUMP IN ${HighlightTunables.FRESHNESS_DAYS} DAYS",
            formatWeekRange(frame.then.date, frame.now.date))
    }
    buildRepeatableBests(observations, freshnessStart).forEach { frame ->
        cards += card(frame, HighlightKind.REPEATABLE_BEST, frame.sessions.last { it.weightLbsStored >= frame.best.weightLbsStored }.date,
            frame.canonicalExerciseId, frame.exerciseName, "HELD ${countLabel(frame.heldDays, "DAY")}",
            "", formatWeekRange(frame.sessions.first().date, frame.sessions.last().date))
    }
    if (reportExercise != null) {
        buildCadenceBands(datedSets)?.let { frame ->
            val date = frame.weeks.flatMap { it.workoutDates }.maxOrNull() ?: return@let
            cards += card(frame, HighlightKind.CADENCE_BANDS, date, reportExercise.id, reportExercise.name,
                freshTag(date, today), "1+ WORKOUT EVERY WEEK", formatWeekRange(frame.windowStart, date))
        }
        buildRunningTotal(datedSets, today)?.let { frame ->
            cards += card(frame, HighlightKind.RUNNING_TOTAL, frame.sessions.last().date, reportExercise.id, reportExercise.name,
                "REPS WITH RECEIPTS", "${countLabel(frame.workoutCount, "SESSION")} RECEIPTS ADD TO ${frame.totalReps} REPS",
                formatWeekRange(frame.sessions.first().date, frame.sessions.last().date))
        }
    }
    return selectHighlightCards(cards, today)
}

/** Fresh candidates are deduplicated once, with one representative of each available kind first. */
internal fun selectHighlightCards(candidates: List<HighlightCard>, today: LocalDate): HighlightReelSnapshot {
    val start = today.minusDays(HighlightTunables.FRESHNESS_DAYS - 1L)
    val ordered = candidates.filter { it.evidenceDate in start..today }
        .sortedWith(compareByDescending<HighlightCard> { it.evidenceDate }.thenBy { it.kind.ordinal }.thenBy { it.key })
        .distinctBy { it.kind to it.reportExerciseId }
    val representatives = ordered.distinctBy { it.kind }.take(HighlightTunables.MAX_CARDS)
    val selectedKeys = representatives.map { it.key }.toSet()
    val selected = (representatives + ordered.filter { it.key !in selectedKeys }.take(HighlightTunables.MAX_CARDS - representatives.size))
        .sortedWith(compareByDescending<HighlightCard> { it.evidenceDate }.thenBy { it.kind.ordinal }.thenBy { it.key })
    val latest = selected.firstOrNull()?.evidenceDate
    val month = latest?.let { "${FULL_MONTH_LABELS[it.monthNumber - 1]} ${it.year}" }.orEmpty()
    return HighlightReelSnapshot(selected.map { it.frame }, selected, month,
        if (selected.isEmpty()) "" else "$month · ${selected.size} ${if (selected.size == 1) "HIGHLIGHT" else "HIGHLIGHTS"} · NEWEST FIRST")
}

private fun card(frame: ProgressHighlightFrame, kind: HighlightKind, date: LocalDate, id: Long, name: String,
    tag: String, why: String, dates: String) = HighlightCard(
    "$kind:$id", kind, frame, date, id, name,
    when (kind) { HighlightKind.CADENCE_BANDS -> "ACTIVE WEEKS"; HighlightKind.RUNNING_TOTAL -> "REPS PER SESSION"; else -> name.uppercase() },
    tag, why, dates, frame.accessibilitySummary(),
)

private fun freshTag(date: LocalDate, today: LocalDate): String =
    if (date >= startOfIsoWeek(today)) "NEW THIS WEEK"
    else "NEW IN ${FULL_MONTH_LABELS[date.monthNumber - 1]}"

private fun matchObservations(exercises: List<Exercise>, datedSets: List<DatedSet>): List<MatchObservation> {
    val exercisesById = exercises.associateBy(Exercise::id)
    val normalizedNames = HashMap<Long, String>()
    return datedSets.mapNotNull { datedSet ->
        val source = exercisesById[datedSet.set.exerciseId] ?: return@mapNotNull null
        val id = resolveCanonicalExerciseId(source.id, exercisesById) ?: return@mapNotNull null
        val exercise = exercisesById[id] ?: return@mapNotNull null
        val reps = datedSet.set.reps ?: return@mapNotNull null
        val weight = datedSet.set.weightLbs ?: return@mapNotNull null
        if (reps <= 0 || weight <= 0) return@mapNotNull null
        // An added-load bodyweight lift can support Beat; unweighted bodyweight cannot.
        val name = normalizedNames.getOrPut(id) { exercise.name.normalizedHighlightName() }
        MatchObservation(id, name, reps, datedSet.date, weight,
            source.isBodyweight || exercise.isBodyweight || datedSet.set.isBodyweight)
    }
}

private fun buildMatchCuts(observations: List<MatchObservation>): List<ProgressHighlightFrame.MatchCut> {
    val improvements = observations.filter { !it.isBodyweight }
        .groupBy { MatchGroup(it.canonicalExerciseId, it.reps) }.values
        .flatMap(::allTimeBestImprovements).filter(::fitsApprovedMatchCutGeometry)
    return improvements.sortedWith(compareByDescending<MatchImprovement> { it.now.date }
        .thenByDescending { it.deltaWeightLbsStored }.thenBy { it.now.canonicalExerciseId }.thenBy { it.now.reps })
        .distinctBy { it.now.canonicalExerciseId }.map { winner ->
            ProgressHighlightFrame.MatchCut(
                exerciseName = winner.now.exerciseName,
                dateLabel = formatPosterDate(winner.now.date),
                beforeLoadLabel = "${WeightLbs.formatStored(winner.before.weightLbsStored)} LB",
                afterLoadLabel = "${WeightLbs.formatStored(winner.now.weightLbsStored)} LB",
                deltaLoadLabel = "+${WeightLbs.formatStored(winner.deltaWeightLbsStored)} LB",
                reps = winner.now.reps,
                canonicalExerciseId = winner.now.canonicalExerciseId,
                beforeDate = winner.before.date, nowDate = winner.now.date,
                beforeWeightLbsStored = winner.before.weightLbsStored,
                afterWeightLbsStored = winner.now.weightLbsStored,
                deltaWeightLbsStored = winner.deltaWeightLbsStored,
            )
        }
}

private fun MatchObservation.evidence() = HighlightSetEvidence(date, formatShortDate(date), weightLbsStored,
    if (isBodyweight) "BW+${WeightLbs.formatStored(weightLbsStored)}" else "${WeightLbs.formatStored(weightLbsStored)} LB", reps)

private fun eligibleBeatImprovements(lift: List<MatchObservation>): List<MatchImprovement> =
    lift.groupBy { it.reps }.values.flatMap(::allTimeBestImprovements).filter { jump ->
        jump.now.date.toEpochDays() - jump.before.date.toEpochDays() <= HighlightTunables.MAX_BEAT_GAP_DAYS &&
            lift.filter { it.date < jump.now.date }.all { it.weightLbsStored < jump.now.weightLbsStored }
    }

private fun buildBeats(observations: List<MatchObservation>, start: LocalDate): List<ProgressHighlightFrame.Beat> =
    observations.groupBy { it.canonicalExerciseId }.values.mapNotNull { lift ->
        val winner = eligibleBeatImprovements(lift).filter { jump ->
            jump.now.date >= start && lift.filter { it.reps == jump.now.reps }.all { it.weightLbsStored <= jump.now.weightLbsStored }
        }.sortedWith(compareByDescending<MatchImprovement> { it.deltaWeightLbsStored }
            .thenByDescending { it.now.date }.thenBy { it.now.reps }).firstOrNull() ?: return@mapNotNull null
        val sessions = lift.filter { it.date in winner.before.date..winner.now.date }.groupBy { it.date }.values
            .map { day -> day.maxBy { it.weightLbsStored } }.sortedBy { it.date }.map { it.evidence() }
        val span = (winner.now.date.toEpochDays() - winner.before.date.toEpochDays()).toFloat()
        ProgressHighlightFrame.Beat(winner.now.exerciseName, winner.now.canonicalExerciseId,
            winner.before.evidence(), winner.now.evidence(), winner.deltaWeightLbsStored,
            "+${WeightLbs.formatStored(winner.deltaWeightLbsStored)} LB", sessions,
            sessions.map { (it.date.toEpochDays() - winner.before.date.toEpochDays()) / span })
    }

private fun buildRepeatableBests(observations: List<MatchObservation>, start: LocalDate): List<ProgressHighlightFrame.RepeatableBest> =
    observations.groupBy { it.canonicalExerciseId }.values.mapNotNull liftLoop@{ lift ->
        val candidates = lift.groupBy { it.reps }.values.flatMap { sameReps ->
            var recordWeight = 0
            val records = sameReps.groupBy { it.date }.values.map { it.maxBy { set -> set.weightLbsStored } }
                .sortedBy { it.date }.filter { set ->
                    val record = set.weightLbsStored > recordWeight
                    recordWeight = maxOf(recordWeight, set.weightLbsStored)
                    record
                }
            records.mapNotNull recordLoop@{ best ->
                if (lift.any { it.date <= best.date && it.reps >= best.reps && it.weightLbsStored > best.weightLbsStored }) return@recordLoop null
                val later = lift.filter { it.date > best.date && it.reps >= best.reps }.groupBy { it.date }.values
                    .map { day -> day.maxBy { it.weightLbsStored } }.sortedBy { it.date }
                val firstReturn = later.firstOrNull { it.weightLbsStored >= best.weightLbsStored } ?: return@recordLoop null
                val held = firstReturn.date.toEpochDays() - best.date.toEpochDays()
                if (held < HighlightTunables.MIN_BEST_HELD_DAYS) return@recordLoop null
                val recent = later.filter { it.date >= start }
                val count = recent.count { it.weightLbsStored >= best.weightLbsStored }
                if (count < HighlightTunables.MIN_REPEAT_SESSIONS) return@recordLoop null
                val sessions = recent.map { it.evidence() }
                val chartSessions = listOf(best.evidence()) + sessions
                val weights = chartSessions.map { it.weightLbsStored }
                val low = weights.minOrNull()!!.toFloat() * 0.9f
                val high = weights.maxOrNull()!!.toFloat() * 1.05f
                fun y(weight: Int) = (high - weight) / (high - low)
                val span = (sessions.last().date.toEpochDays() - best.date.toEpochDays()).toFloat().coerceAtLeast(1f)
                ProgressHighlightFrame.RepeatableBest(best.exerciseName, best.canonicalExerciseId, best.evidence(), held.toInt(),
                    sessions, count, chartSessions.map {
                        HighlightChartPoint((it.date.toEpochDays() - best.date.toEpochDays()) / span,
                        y(it.weightLbsStored), it.weightLbsStored >= best.weightLbsStored)
                    }, y(best.weightLbsStored))
            }
        }
        candidates.sortedWith(compareByDescending<ProgressHighlightFrame.RepeatableBest> { it.best.date }
            .thenByDescending { it.sessions.last().date }.thenByDescending { it.best.weightLbsStored }
            .thenByDescending { it.best.reps }).firstOrNull()
    }

private fun formatShortDate(date: LocalDate) = "${MONTH_LABELS[date.monthNumber - 1]} ${date.day}"

/**
 * Reduces multiple completed sets on one date to that session's top load, then emits only dates
 * that strictly beat every earlier session at the exact same rep count.
 */
private fun allTimeBestImprovements(observations: List<MatchObservation>): List<MatchImprovement> {
    val dailyBests = observations
        .groupBy(MatchObservation::date)
        .map { (_, sameDate) ->
            sameDate.sortedWith(
                compareByDescending<MatchObservation> { it.weightLbsStored }
                    .thenBy { it.exerciseName },
            ).first()
        }
        .sortedBy(MatchObservation::date)

    var previousBest: MatchObservation? = null
    val improvements = mutableListOf<MatchImprovement>()
    dailyBests.forEach { current ->
        val before = previousBest
        if (before == null) {
            previousBest = current
        } else if (current.weightLbsStored > before.weightLbsStored) {
            val delta = current.weightLbsStored - before.weightLbsStored
            improvements += MatchImprovement(
                before = before,
                now = current,
                deltaWeightLbsStored = delta,
            )
            previousBest = current
        } else if (current.weightLbsStored == before.weightLbsStored) {
            previousBest = current
        }
    }
    return improvements
}

private fun buildCadenceBands(datedSets: List<DatedSet>): ProgressHighlightFrame.CadenceBands? {
    val workoutDates = datedSets.map(DatedSet::date).distinct().sorted()
    val workoutDatesByWeek = workoutDates.groupBy(::startOfIsoWeek)
    val activeWeekStarts = workoutDatesByWeek.keys.toSet()

    val endingWeek = activeWeekStarts.sortedDescending().firstOrNull { candidateEnd ->
        (0 until HighlightTunables.APPROVED_WEEK_COUNT).all { offset ->
            candidateEnd.minusDays(offset * 7L) in activeWeekStarts
        }
    } ?: return null

    val firstWeek = endingWeek.minusDays((HighlightTunables.APPROVED_WEEK_COUNT - 1) * 7L)
    val weeks = (0 until HighlightTunables.APPROVED_WEEK_COUNT).map { offset ->
        val weekStart = firstWeek.plusDays(offset * 7L)
        val weekEnd = weekStart.plusDays(6)
        val dates = workoutDatesByWeek[weekStart].orEmpty().sorted()
        CadenceWeek(
            dateRangeLabel = formatWeekRange(weekStart, weekEnd),
            workoutDays = dates.map { it.dayOfWeek.isoDayNumber - 1 }.toSet(),
            startDate = weekStart,
            endDate = weekEnd,
            workoutDates = dates,
        )
    }

    return ProgressHighlightFrame.CadenceBands(
        straightWeekCount = HighlightTunables.APPROVED_WEEK_COUNT,
        weeks = weeks,
        workoutCount = weeks.sumOf { it.workoutDates.size },
        windowStart = weeks.first().startDate,
        windowEnd = weeks.last().endDate,
    )
}

private fun buildRunningTotal(
    datedSets: List<DatedSet>,
    today: LocalDate,
): ProgressHighlightFrame.RunningTotal? {
    val currentWeekStart = startOfIsoWeek(today)
    val windowStart = currentWeekStart.minusDays((HighlightTunables.APPROVED_WEEK_COUNT - 1) * 7L)
    val windowEnd = currentWeekStart.plusDays(6)
    val population = datedSets.filter { it.date in windowStart..windowEnd }
    val byDate = population.groupBy(DatedSet::date).entries.sortedBy { it.key }
    if (byDate.size != HighlightTunables.APPROVED_RUNNING_TOTAL_SESSION_COUNT) return null

    val sessions = mutableListOf<RunningTotalSession>()
    byDate.forEachIndexed { index, (date, sessionSets) ->
        val reps = sessionSets.map { it.set.reps }
        // A checked row without a positive rep value cannot be shown as an auditable receipt.
        if (reps.any { it == null || it <= 0 }) return null
        val repsTotal = reps.filterNotNull().sumOf { it.toLong() }
        if (repsTotal !in 1L..HighlightTunables.MAX_APPROVED_SESSION_REPS.toLong()) return null
        sessions += RunningTotalSession(
            ordinal = index + 1,
            date = date,
            reps = repsTotal.toInt(),
            setCount = sessionSets.size,
        )
    }

    val totalReps = sessions.sumOf { it.reps.toLong() }
    val setCount = sessions.sumOf { it.setCount.toLong() }
    if (
        totalReps !in 1L..HighlightTunables.MAX_APPROVED_TOTAL_REPS.toLong() ||
        setCount !in 1L..HighlightTunables.MAX_APPROVED_SET_COUNT.toLong()
    ) {
        return null
    }

    return ProgressHighlightFrame.RunningTotal(
        sessionReps = sessions.map(RunningTotalSession::reps),
        totalReps = totalReps.toInt(),
        workoutCount = sessions.size,
        setCount = setCount.toInt(),
        periodLabel = "LAST 4 WEEKS",
        sessions = sessions,
        windowStart = windowStart,
        windowEnd = windowEnd,
    )
}

private fun resolveCanonicalExerciseId(
    exerciseId: Long,
    exercisesById: Map<Long, Exercise>,
): Long? {
    var current = exerciseId
    val seen = mutableSetOf<Long>()
    while (seen.add(current)) {
        val exercise = exercisesById[current] ?: return null
        current = exercise.mergedIntoId ?: return exercise.id
    }
    return null
}

/**
 * The canonical poster has no responsive type variant. This conservative content gate keeps its
 * live values inside the exact, single-line composition instead of silently shrinking or clipping.
 * The name budget approximates condensed-display glyph widths and is calibrated so the source
 * `CLOSE-GRIP BENCH` fits at 14.39 units; spaces and narrow glyphs do not consume a full unit.
 */
private fun fitsApprovedMatchCutGeometry(improvement: MatchImprovement): Boolean {
    val name = improvement.now.exerciseName
    val before = improvement.before.weightLbsStored
    val after = improvement.now.weightLbsStored
    val delta = improvement.deltaWeightLbsStored
    return name.isNotEmpty() &&
        name.posterWidthUnits() <= HighlightTunables.MAX_MATCH_CUT_NAME_POSTER_UNITS &&
        improvement.now.reps in 1..HighlightTunables.MAX_APPROVED_MATCH_CUT_REPS &&
        before in 10..HighlightTunables.MAX_APPROVED_MATCH_CUT_WEIGHT_STORED &&
        after in 10..HighlightTunables.MAX_APPROVED_MATCH_CUT_WEIGHT_STORED &&
        before % 10 == 0 &&
        after % 10 == 0 &&
        delta in 10..HighlightTunables.MAX_APPROVED_MATCH_CUT_DELTA_STORED &&
        delta % 10 == 0
}

private fun String.posterWidthUnits(): Double = uppercase().sumOf { character ->
    when (character) {
        ' ' -> 0.55
        '-', '.', '\'', 'I', '1' -> 0.60
        'J', 'L', 'T', 'F', 'R' -> 0.82
        'M', 'W', '@' -> 1.30
        else -> 1.0
    }
}

private fun startOfIsoWeek(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.isoDayNumber - 1).toLong())

private fun formatPosterDate(date: LocalDate): String =
    "${MONTH_LABELS[date.monthNumber - 1]} ${date.day.toString().padStart(2, '0')}, ${date.year}"

private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    val startMonth = MONTH_LABELS[start.monthNumber - 1]
    val endMonth = MONTH_LABELS[end.monthNumber - 1]
    val startDay = start.day.toString().padStart(2, '0')
    val endDay = end.day.toString().padStart(2, '0')
    return if (start.monthNumber == end.monthNumber && start.year == end.year) {
        "$startMonth $startDay–$endDay"
    } else {
        "$startMonth $startDay–$endMonth $endDay"
    }
}

private val highlightNameWhitespace = Regex("\\s+")

private fun String.normalizedHighlightName(): String =
    trim().split(highlightNameWhitespace).joinToString(" ")

private data class DatedSet(
    val set: WorkoutSet,
    val date: LocalDate,
)

private data class MatchGroup(
    val canonicalExerciseId: Long,
    val reps: Int,
)

private data class MatchObservation(
    val canonicalExerciseId: Long,
    val exerciseName: String,
    val reps: Int,
    val date: LocalDate,
    val weightLbsStored: Int,
    val isBodyweight: Boolean = false,
)

private data class MatchImprovement(
    val before: MatchObservation,
    val now: MatchObservation,
    val deltaWeightLbsStored: Int,
)

internal fun ProgressHighlightFrame.accessibilitySummary(): String = when (this) {
    is ProgressHighlightFrame.Beat -> "$exerciseName. $deltaLoadLabel at the same ${now.reps} reps. Then ${then.loadLabel} on ${then.dateLabel}. Now ${now.loadLabel} on ${now.dateLabel}. ${sessions.size} sessions."
    is ProgressHighlightFrame.RepeatableBest -> "$exerciseName. Best ${best.loadLabel} times ${best.reps} on ${best.dateLabel}. Held $heldDays days. $sessionsAtOrAbove sessions at or above it."

    is ProgressHighlightFrame.MatchCut -> buildString {
        append(exerciseName)
        append(". ")
        append(deltaLoadLabel)
        append(" while holding ")
        append(reps)
        append(if (reps == 1) " rep. " else " reps. ")
        append("Before: ")
        append(beforeLoadLabel)
        append(" times ")
        append(reps)
        append(". Now: ")
        append(afterLoadLabel)
        append(" times ")
        append(reps)
        append(". ")
        append(dateLabel)
        append('.')
    }

    is ProgressHighlightFrame.CadenceBands -> buildString {
        append(straightWeekCount)
        append(" straight active weeks. ")
        append(workoutCount)
        append(if (workoutCount == 1) " workout. " else " workouts. ")
        weeks.forEachIndexed { index, week ->
            append("Week ")
            append(index + 1)
            append(", ")
            append(week.dateRangeLabel)
            append(": workout on ")
            append(
                week.workoutDays
                    .sorted()
                    .joinToString(" and ") { day ->
                        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                            .getOrElse(day) { "day ${day + 1}" }
                    }
            )
            append(". ")
        }
        append("An active week has at least one workout in the calendar week.")
    }

    is ProgressHighlightFrame.RunningTotal -> buildString {
        append(totalReps)
        append(" reps across ")
        append(workoutCount)
        append(if (workoutCount == 1) " workout. " else " workouts. ")
        append("Session reps: ")
        append(sessionReps.joinToString(", "))
        append(". ")
        append(setCount)
        append(" sets, ")
        append(periodLabel)
        append('.')
    }
}
