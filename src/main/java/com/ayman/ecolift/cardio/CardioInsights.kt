package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.web.util.YearMonthW
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

data class DayDot(val date: LocalDate, val minutes: Int, val machine: CardioActivityType?, val count: Int)
data class WeekTotals(val seconds: Int, val metres: Double, val calories: Int, val count: Int)
data class DayRuns(
    val currentRunStart: LocalDate?, val currentRunLength: Int,
    val longestRunStart: LocalDate?, val longestRunEnd: LocalDate?, val longestRunLength: Int,
    val daysMovedThisMonth: Int, val daysElapsedThisMonth: Int,
)
data class WeekStreak(val weeksInARow: Int, val currentWeekQualifies: Boolean, val currentWeekNeeds: Int)
data class MonthBar(val year: Int, val month: Int, val secondsByMachine: Map<CardioActivityType, Int>, val totalSeconds: Int, val isCurrent: Boolean)

enum class RecordKind {
    LONGEST_SESSION,
    FARTHEST,
    FASTEST_MILE,
    BEST_SPLIT_500,
    MOST_FLOORS,
    HIGHEST_POWER,
    BIGGEST_WEEK,
    BIGGEST_MONTH,
}

data class Record(
    val kind: RecordKind,
    val machine: CardioActivityType?,
    val value: Double,
    val date: LocalDate,
    val previous: Double?,
    val isNew: Boolean,
)

data class FieldDelta(
    val field: CardioEntryField,
    val delta: Double,
    val improved: Boolean,
)

data class SessionDeltas(
    val previous: CardioSession?,
    val deltas: List<FieldDelta>,
    val derivedPaceSec: Int?,
    val previousDerivedPaceSec: Int?,
)

object CardioInsights {
    fun weekArc(sessions: List<CardioSession>, today: LocalDate): List<DayDot> {
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        return dates.map { date ->
            val daySessions = sessions.filter { countsForActivity(it) && parseDate(it) == date }
            val count = daySessions.size
            val minutes = daySessions.sumOf { it.durationSec ?: 0 } / 60
            val machine = if (count > 0) {
                val longest = daySessions.minWith(
                    compareByDescending<CardioSession> { it.durationSec ?: 0 }.thenBy { it.createdAt }
                )
                activityType(longest)
            } else {
                null
            }
            DayDot(date = date, minutes = minutes, machine = machine, count = count)
        }
    }

    fun weekTotals(sessions: List<CardioSession>, today: LocalDate): WeekTotals {
        val startDate = today.minusDays(6)
        val valid = sessions.filter { session ->
            if (!countsForActivity(session)) return@filter false
            val d = parseDate(session) ?: return@filter false
            d in startDate..today
        }
        val seconds = valid.sumOf { it.durationSec ?: 0 }
        val metres = valid.sumOf { it.distanceM ?: 0.0 }
        val calories = valid.sumOf { it.calories ?: 0 }
        val count = valid.size
        return WeekTotals(seconds = seconds, metres = metres, calories = calories, count = count)
    }

    fun dayRuns(sessions: List<CardioSession>, today: LocalDate): DayRuns {
        val dates = sessions
            .filter { countsForActivity(it) }
            .mapNotNull { parseDate(it) }
            .filter { it <= today }
            .toSet()

        val sortedDates = dates.sorted()
        data class Run(val start: LocalDate, val end: LocalDate, val length: Int)
        val runs = mutableListOf<Run>()
        if (sortedDates.isNotEmpty()) {
            var runStart = sortedDates[0]
            var prev = sortedDates[0]
            var length = 1
            for (i in 1 until sortedDates.size) {
                val curr = sortedDates[i]
                if (curr == prev.plusDays(1)) {
                    length++
                    prev = curr
                } else {
                    runs.add(Run(runStart, prev, length))
                    runStart = curr
                    prev = curr
                    length = 1
                }
            }
            runs.add(Run(runStart, prev, length))
        }

        val longestRun = runs.maxByOrNull { it.length }

        val currentRun = when {
            dates.contains(today) -> runs.firstOrNull { it.end == today }
            dates.contains(today.minusDays(1)) -> runs.firstOrNull { it.end == today.minusDays(1) }
            else -> null
        }

        val daysMovedThisMonth = dates.count { it.year == today.year && it.month == today.month }
        val daysElapsedThisMonth = today.dayOfMonth

        return DayRuns(
            currentRunStart = currentRun?.start,
            currentRunLength = currentRun?.length ?: 0,
            longestRunStart = longestRun?.start,
            longestRunEnd = longestRun?.end,
            longestRunLength = longestRun?.length ?: 0,
            daysMovedThisMonth = daysMovedThisMonth,
            daysElapsedThisMonth = daysElapsedThisMonth,
        )
    }

    fun weekStreak(sessions: List<CardioSession>, today: LocalDate, minSessions: Int = 2): WeekStreak {
        val currentMonday = today.minusDays(today.dayOfWeek.ordinal.toLong())
        val validSessions = sessions.filter { countsForActivity(it) }
        val sessionDates = validSessions.mapNotNull { parseDate(it) }.filter { it <= today }

        val countThisWeek = sessionDates.count { it in currentMonday..today }
        val currentWeekQualifies = countThisWeek >= minSessions
        val currentWeekNeeds = max(0, minSessions - countThisWeek)

        var weeksInARow = 0
        var weekMonday = currentMonday.minusDays(7)
        while (true) {
            val weekSunday = weekMonday.plusDays(6)
            val count = sessionDates.count { it in weekMonday..weekSunday }
            if (count >= minSessions) {
                weeksInARow++
                weekMonday = weekMonday.minusDays(7)
            } else {
                break
            }
        }

        return WeekStreak(
            weeksInARow = weeksInARow,
            currentWeekQualifies = currentWeekQualifies,
            currentWeekNeeds = currentWeekNeeds,
        )
    }

    fun monthBars(sessions: List<CardioSession>, today: LocalDate, months: Int = 4): List<MonthBar> {
        val currentYm = YearMonthW.from(today)
        val yms = (months - 1 downTo 0).map { currentYm.minusMonths(it) }
        val validSessions = sessions.filter { countsForActivity(it) }

        return yms.map { ym ->
            val isCurrent = (ym == currentYm)
            val monthSessions = validSessions.filter { session ->
                val d = parseDate(session) ?: return@filter false
                d.year == ym.year && (d.month.ordinal + 1) == ym.month && d <= today
            }
            val secondsByMachine = mutableMapOf<CardioActivityType, Int>()
            for (s in monthSessions) {
                val type = activityType(s)
                secondsByMachine[type] = (secondsByMachine[type] ?: 0) + (s.durationSec ?: 0)
            }
            val totalSeconds = monthSessions.sumOf { it.durationSec ?: 0 }
            MonthBar(
                year = ym.year,
                month = ym.month,
                secondsByMachine = secondsByMachine,
                totalSeconds = totalSeconds,
                isCurrent = isCurrent,
            )
        }
    }

    fun yearColumns(sessions: List<CardioSession>, year: Int): List<Int> {
        val result = MutableList(12) { 0 }
        for (session in sessions) {
            if (!countsForActivity(session)) continue
            val d = parseDate(session) ?: continue
            if (d.year == year) {
                result[d.month.ordinal] += (session.durationSec ?: 0)
            }
        }
        return result
    }

    fun defaultMachineOrder(sessions: List<CardioSession>): List<CardioActivityType> {
        val countingSessions = sessions.filter { countsForActivity(it) }
        val entryOrder = CardioActivitySpecs.entryOrder
        val counts = CardioActivityType.entries.associateWith { type ->
            countingSessions.count { activityType(it) == type }
        }
        val sortedByUsage = entryOrder.sortedWith(
            compareByDescending<CardioActivityType> { counts[it] ?: 0 }
                .thenBy { entryOrder.indexOf(it) }
        )
        val top4 = sortedByUsage.take(4)
        val remaining = entryOrder.filter { it !in top4 }
        return top4 + remaining
    }

    fun lastOnMachine(sessions: List<CardioSession>, type: CardioActivityType): CardioSession? {
        val matching = sessions.filter { activityType(it) == type && countsForActivity(it) }
        val valid = matching.mapNotNull { s -> parseDate(s)?.let { d -> d to s } }
        return valid.maxWithOrNull(
            compareBy<Pair<LocalDate, CardioSession>> { it.first }.thenBy { it.second.createdAt }
        )?.second
    }

    fun activityType(session: CardioSession): CardioActivityType =
        CardioActivitySpecs.fromStoredType(session.activityType)

    fun countsForActivity(session: CardioSession): Boolean = (session.durationSec ?: 0) > 0

    fun usesMetres(type: CardioActivityType): Boolean =
        type == CardioActivityType.ROW || type == CardioActivityType.SWIM

    fun derivedPaceSec(session: CardioSession): Int? {
        val dur = session.durationSec ?: return null
        val dist = session.distanceM ?: return null
        if (dur <= 0 || dist <= 0.0) return null
        val type = activityType(session)
        val pace = when (type) {
            CardioActivityType.ROW -> dur / (dist / 500.0)
            CardioActivityType.SWIM -> dur / (dist / 100.0)
            else -> dur / (dist / 1609.344)
        }
        return round(pace).toInt()
    }

    fun records(sessions: List<CardioSession>, today: LocalDate): List<Record> {
        val counting = sessions.filter { countsForActivity(it) }
        val dated = counting.mapNotNull { s -> parseDate(s)?.let { d -> if (d <= today) d to s else null } }

        val countingByMachine = dated.groupBy { activityType(it.second) }
        val eligibleMachines = countingByMachine.filter { (_, list) -> list.size >= 3 }.keys

        val recordsList = mutableListOf<Record>()

        fun buildRecord(
            kind: RecordKind,
            machine: CardioActivityType?,
            candidates: List<Pair<LocalDate, Pair<Double, CardioSession>>>,
            higherIsBetter: Boolean,
        ): Record? {
            if (candidates.isEmpty()) return null
            val bestEntry = if (higherIsBetter) {
                candidates.minWith(
                    compareByDescending<Pair<LocalDate, Pair<Double, CardioSession>>> { it.second.first }
                        .thenBy { it.first }
                        .thenBy { it.second.second.createdAt }
                )
            } else {
                candidates.minWith(
                    compareBy<Pair<LocalDate, Pair<Double, CardioSession>>> { it.second.first }
                        .thenBy { it.first }
                        .thenBy { it.second.second.createdAt }
                )
            }

            val bestValue = bestEntry.second.first
            val bestDate = bestEntry.first

            val priorCandidates = candidates.filter { it.first < bestDate }
            val previous = if (priorCandidates.isEmpty()) {
                null
            } else {
                if (higherIsBetter) {
                    priorCandidates.maxOf { it.second.first }
                } else {
                    priorCandidates.minOf { it.second.first }
                }
            }

            val isNew = previous != null && (today.toEpochDays() - bestDate.toEpochDays() in 0..14)
            return Record(
                kind = kind,
                machine = machine,
                value = bestValue,
                date = bestDate,
                previous = previous,
                isNew = isNew,
            )
        }

        // 1. LONGEST_SESSION (per eligible machine)
        for (m in eligibleMachines) {
            val candidates = dated
                .filter { activityType(it.second) == m }
                .mapNotNull { (d, s) -> s.durationSec?.toDouble()?.let { v -> d to (v to s) } }
            buildRecord(RecordKind.LONGEST_SESSION, m, candidates, higherIsBetter = true)?.let { recordsList.add(it) }
        }

        // 2. FARTHEST (per eligible machine, needs distanceM)
        for (m in eligibleMachines) {
            val candidates = dated
                .filter { activityType(it.second) == m }
                .mapNotNull { (d, s) -> s.distanceM?.let { v -> if (v > 0.0) d to (v to s) else null } }
            buildRecord(RecordKind.FARTHEST, m, candidates, higherIsBetter = true)?.let { recordsList.add(it) }
        }

        // 3. FASTEST_MILE (one record across TREADMILL, RUN, WALK)
        val mileMachines = listOf(CardioActivityType.TREADMILL, CardioActivityType.RUN, CardioActivityType.WALK)
        val mileCandidates = dated
            .filter { activityType(it.second) in mileMachines && activityType(it.second) in eligibleMachines }
            .mapNotNull { (d, s) -> derivedPaceSec(s)?.let { p -> d to (p.toDouble() to s) } }
        if (mileCandidates.isNotEmpty()) {
            val bestEntry = mileCandidates.minWith(
                compareBy<Pair<LocalDate, Pair<Double, CardioSession>>> { it.second.first }
                    .thenBy { it.first }
                    .thenBy { it.second.second.createdAt }
            )
            val machine = activityType(bestEntry.second.second)
            val bestValue = bestEntry.second.first
            val bestDate = bestEntry.first
            val priorCandidates = mileCandidates.filter { it.first < bestDate }
            val previous = if (priorCandidates.isEmpty()) null else priorCandidates.minOf { it.second.first }
            val isNew = previous != null && (today.toEpochDays() - bestDate.toEpochDays() in 0..14)
            recordsList.add(
                Record(
                    kind = RecordKind.FASTEST_MILE,
                    machine = machine,
                    value = bestValue,
                    date = bestDate,
                    previous = previous,
                    isNew = isNew,
                )
            )
        }

        // 4. BEST_SPLIT_500 (ROW only)
        if (CardioActivityType.ROW in eligibleMachines) {
            val rowCandidates = dated
                .filter { activityType(it.second) == CardioActivityType.ROW }
                .mapNotNull { (d, s) ->
                    val split = s.paceSecPer500m ?: derivedPaceSec(s)
                    split?.let { sp -> if (sp > 0) d to (sp.toDouble() to s) else null }
                }
            buildRecord(RecordKind.BEST_SPLIT_500, CardioActivityType.ROW, rowCandidates, higherIsBetter = false)?.let {
                recordsList.add(it)
            }
        }

        // 5. MOST_FLOORS (STAIR_CLIMBER only)
        if (CardioActivityType.STAIR_CLIMBER in eligibleMachines) {
            val stairCandidates = dated
                .filter { activityType(it.second) == CardioActivityType.STAIR_CLIMBER }
                .mapNotNull { (d, s) -> s.floors?.let { f -> if (f > 0) d to (f.toDouble() to s) else null } }
            buildRecord(RecordKind.MOST_FLOORS, CardioActivityType.STAIR_CLIMBER, stairCandidates, higherIsBetter = true)?.let {
                recordsList.add(it)
            }
        }

        // 6. HIGHEST_POWER (BIKE / ELLIPTICAL)
        val powerMachines = listOf(CardioActivityType.BIKE, CardioActivityType.ELLIPTICAL)
        val powerCandidates = dated
            .filter { activityType(it.second) in powerMachines && activityType(it.second) in eligibleMachines }
            .mapNotNull { (d, s) -> s.avgPowerWatts?.let { w -> if (w > 0) d to (w.toDouble() to s) else null } }
        if (powerCandidates.isNotEmpty()) {
            val bestEntry = powerCandidates.minWith(
                compareByDescending<Pair<LocalDate, Pair<Double, CardioSession>>> { it.second.first }
                    .thenBy { it.first }
                    .thenBy { it.second.second.createdAt }
            )
            val machine = activityType(bestEntry.second.second)
            val bestValue = bestEntry.second.first
            val bestDate = bestEntry.first
            val priorCandidates = powerCandidates.filter { it.first < bestDate }
            val previous = if (priorCandidates.isEmpty()) null else priorCandidates.maxOf { it.second.first }
            val isNew = previous != null && (today.toEpochDays() - bestDate.toEpochDays() in 0..14)
            recordsList.add(
                Record(
                    kind = RecordKind.HIGHEST_POWER,
                    machine = machine,
                    value = bestValue,
                    date = bestDate,
                    previous = previous,
                    isNew = isNew,
                )
            )
        }

        // 7. BIGGEST_WEEK (most seconds in a complete Monday week; machine = null)
        data class WeekData(val monday: LocalDate, val totalSeconds: Double, val sessions: List<CardioSession>)
        val completeWeeks = mutableMapOf<LocalDate, MutableList<CardioSession>>()
        for ((d, s) in dated) {
            val monday = d.minusDays(d.dayOfWeek.ordinal.toLong())
            val sunday = monday.plusDays(6)
            if (sunday < today) {
                completeWeeks.getOrPut(monday) { mutableListOf() }.add(s)
            }
        }
        val weekList = completeWeeks.map { (mon, slist) ->
            WeekData(mon, slist.sumOf { it.durationSec ?: 0 }.toDouble(), slist)
        }.filter { it.totalSeconds > 0 }

        if (weekList.isNotEmpty()) {
            val bestWeek = weekList.minWith(
                compareByDescending<WeekData> { it.totalSeconds }.thenBy { it.monday }
            )
            val priorWeeks = weekList.filter { it.monday < bestWeek.monday }
            val previous = if (priorWeeks.isEmpty()) null else priorWeeks.maxOf { it.totalSeconds }
            val isNew = previous != null && (today.toEpochDays() - bestWeek.monday.toEpochDays() in 0..14)
            recordsList.add(
                Record(
                    kind = RecordKind.BIGGEST_WEEK,
                    machine = null,
                    value = bestWeek.totalSeconds,
                    date = bestWeek.monday,
                    previous = previous,
                    isNew = isNew,
                )
            )
        }

        // 8. BIGGEST_MONTH (most metres in a complete month; machine = null)
        data class MonthData(val ym: YearMonthW, val totalMetres: Double, val sessions: List<CardioSession>, val earliestDate: LocalDate)
        val completeMonths = mutableMapOf<YearMonthW, MutableList<Pair<LocalDate, CardioSession>>>()
        val currentYm = YearMonthW.from(today)
        for ((d, s) in dated) {
            val ym = YearMonthW.from(d)
            if (ym.year < currentYm.year || (ym.year == currentYm.year && ym.month < currentYm.month)) {
                completeMonths.getOrPut(ym) { mutableListOf() }.add(d to s)
            }
        }
        val monthList = completeMonths.map { (ym, slist) ->
            val totalM = slist.sumOf { it.second.distanceM ?: 0.0 }
            val earliest = slist.minOf { it.first }
            MonthData(ym, totalM, slist.map { it.second }, earliest)
        }.filter { it.totalMetres > 0 }

        if (monthList.isNotEmpty()) {
            val bestMonth = monthList.minWith(
                compareByDescending<MonthData> { it.totalMetres }
                    .thenBy { it.ym.year * 12 + it.ym.month }
            )
            val priorMonths = monthList.filter {
                (it.ym.year * 12 + it.ym.month) < (bestMonth.ym.year * 12 + bestMonth.ym.month)
            }
            val previous = if (priorMonths.isEmpty()) null else priorMonths.maxOf { it.totalMetres }
            val isNew = previous != null && (today.toEpochDays() - bestMonth.earliestDate.toEpochDays() in 0..14)
            recordsList.add(
                Record(
                    kind = RecordKind.BIGGEST_MONTH,
                    machine = null,
                    value = bestMonth.totalMetres,
                    date = bestMonth.earliestDate,
                    previous = previous,
                    isNew = isNew,
                )
            )
        }

        val entryOrder = CardioActivitySpecs.entryOrder
        return recordsList.sortedWith(
            compareBy<Record> { it.kind.ordinal }
                .thenBy { it.machine?.let { m -> entryOrder.indexOf(m) } ?: -1 }
        )
    }

    fun sessionDeltas(session: CardioSession, sessions: List<CardioSession>): SessionDeltas {
        val curDate = parseDate(session)
        val targetMachine = activityType(session)
        val previous = sessions.filter { other ->
            other.id != session.id &&
            other.localUuid != session.localUuid &&
            activityType(other) == targetMachine &&
            countsForActivity(other) &&
            run {
                val otherDate = parseDate(other) ?: return@run false
                if (curDate != null) {
                    otherDate < curDate || (otherDate == curDate && other.createdAt < session.createdAt)
                } else {
                    false
                }
            }
        }.maxWithOrNull(
            compareBy<CardioSession> { parseDate(it)!! }.thenBy { it.createdAt }
        )

        val derivedPace = derivedPaceSec(session)
        val previousDerivedPace = previous?.let { derivedPaceSec(it) }

        if (previous == null) {
            return SessionDeltas(
                previous = null,
                deltas = emptyList(),
                derivedPaceSec = derivedPace,
                previousDerivedPaceSec = null,
            )
        }

        val deltas = mutableListOf<FieldDelta>()

        // DURATION
        if (session.durationSec != null && previous.durationSec != null) {
            val delta = (session.durationSec - previous.durationSec).toDouble()
            deltas.add(FieldDelta(CardioEntryField.DURATION, delta, improved = false))
        }

        // DISTANCE_MILES
        if (session.distanceM != null && previous.distanceM != null) {
            val delta = session.distanceM - previous.distanceM
            deltas.add(FieldDelta(CardioEntryField.DISTANCE_MILES, delta, improved = delta > 0.0))
        }

        // AVG_SPEED_MPH
        if (session.avgSpeed != null && previous.avgSpeed != null) {
            val delta = session.avgSpeed - previous.avgSpeed
            deltas.add(FieldDelta(CardioEntryField.AVG_SPEED_MPH, delta, improved = delta > 0.0))
        }

        // AVG_INCLINE_PERCENT
        if (session.avgInclinePercent != null && previous.avgInclinePercent != null) {
            val delta = session.avgInclinePercent - previous.avgInclinePercent
            deltas.add(FieldDelta(CardioEntryField.AVG_INCLINE_PERCENT, delta, improved = false))
        }

        // CALORIES
        if (session.calories != null && previous.calories != null) {
            val delta = (session.calories - previous.calories).toDouble()
            deltas.add(FieldDelta(CardioEntryField.CALORIES, delta, improved = delta > 0.0))
        }

        // AVG_HEART_RATE
        if (session.avgHeartRate != null && previous.avgHeartRate != null) {
            val delta = (session.avgHeartRate - previous.avgHeartRate).toDouble()
            val machine = activityType(session)
            val mainMetricWithin5Percent: Boolean = when (machine) {
                CardioActivityType.TREADMILL, CardioActivityType.RUN, CardioActivityType.WALK -> {
                    if (session.avgSpeed != null && previous.avgSpeed != null && previous.avgSpeed > 0.0) {
                        abs(session.avgSpeed - previous.avgSpeed) / previous.avgSpeed <= 0.05
                    } else false
                }
                CardioActivityType.ROW -> {
                    val curSplit = (session.paceSecPer500m ?: derivedPaceSec(session))?.toDouble()
                    val prevSplit = (previous.paceSecPer500m ?: derivedPaceSec(previous))?.toDouble()
                    if (curSplit != null && prevSplit != null && prevSplit > 0.0) {
                        abs(curSplit - prevSplit) / prevSplit <= 0.05
                    } else false
                }
                CardioActivityType.BIKE, CardioActivityType.ELLIPTICAL -> {
                    if (session.avgPowerWatts != null && previous.avgPowerWatts != null && previous.avgPowerWatts > 0) {
                        abs((session.avgPowerWatts - previous.avgPowerWatts).toDouble()) / previous.avgPowerWatts.toDouble() <= 0.05
                    } else false
                }
                CardioActivityType.STAIR_CLIMBER -> {
                    if (session.floors != null && previous.floors != null && previous.floors > 0) {
                        abs((session.floors - previous.floors).toDouble()) / previous.floors.toDouble() <= 0.05
                    } else false
                }
                else -> false
            }
            val improved = delta < 0.0 && mainMetricWithin5Percent
            deltas.add(FieldDelta(CardioEntryField.AVG_HEART_RATE, delta, improved = improved))
        }

        // CADENCE_RPM
        if (session.cadenceRpm != null && previous.cadenceRpm != null) {
            val delta = (session.cadenceRpm - previous.cadenceRpm).toDouble()
            deltas.add(FieldDelta(CardioEntryField.CADENCE_RPM, delta, improved = false))
        }

        // RESISTANCE_LEVEL
        if (session.resistanceLevel != null && previous.resistanceLevel != null) {
            val delta = session.resistanceLevel - previous.resistanceLevel
            deltas.add(FieldDelta(CardioEntryField.RESISTANCE_LEVEL, delta, improved = false))
        }

        // AVG_POWER_WATTS
        if (session.avgPowerWatts != null && previous.avgPowerWatts != null) {
            val delta = (session.avgPowerWatts - previous.avgPowerWatts).toDouble()
            deltas.add(FieldDelta(CardioEntryField.AVG_POWER_WATTS, delta, improved = delta > 0.0))
        }

        // STROKE_RATE_SPM
        if (session.strokeRateSpm != null && previous.strokeRateSpm != null) {
            val delta = (session.strokeRateSpm - previous.strokeRateSpm).toDouble()
            deltas.add(FieldDelta(CardioEntryField.STROKE_RATE_SPM, delta, improved = false))
        }

        // PACE_500M
        if (session.paceSecPer500m != null && previous.paceSecPer500m != null) {
            val delta = (session.paceSecPer500m - previous.paceSecPer500m).toDouble()
            deltas.add(FieldDelta(CardioEntryField.PACE_500M, delta, improved = delta < 0.0))
        }

        // FLOORS
        if (session.floors != null && previous.floors != null) {
            val delta = (session.floors - previous.floors).toDouble()
            deltas.add(FieldDelta(CardioEntryField.FLOORS, delta, improved = delta > 0.0))
        }

        return SessionDeltas(
            previous = previous,
            deltas = deltas,
            derivedPaceSec = derivedPace,
            previousDerivedPaceSec = previousDerivedPace,
        )
    }

    private fun parseDate(session: CardioSession): LocalDate? =
        runCatching { LocalDate.parse(session.date) }.getOrNull()
}
