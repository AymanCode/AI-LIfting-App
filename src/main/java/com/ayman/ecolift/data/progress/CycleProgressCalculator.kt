package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.ExerciseSnapshot
import com.ayman.ecolift.data.SessionPoint
import com.ayman.ecolift.data.SplitBucketKind
import com.ayman.ecolift.data.WeightLbs
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object CycleProgressCalculator {
    const val MOMENTUM_GAIN = 6.0

    data class SetInput(
        val id: Long,
        val exerciseId: Long,
        val date: String,
        val setNumber: Int,
        val weightLbs: Int?,
        val reps: Int?,
        val isBodyweight: Boolean,
        val completed: Boolean,
    )

    data class LiftKey(val exerciseId: Long, val splitSlotId: Long)

    data class CoreResult(
        val core: CycleProgressCore,
        val realSlotCount: Int,
        val totalSetsByLift: Map<LiftKey, Int>,
    )

    data class Regression(
        val slopePerDay: Float,
        val intercept: Float,
        val r2: Float,
        val meanValue: Float,
        val firstDate: String,
    )

    private data class ResolvedValue(
        val date: String,
        val metric: LiftMetric,
        val value: Float,
    )

    private data class SessionSeries(
        val metric: LiftMetric,
        val points: List<TrendPoint>,
    )

    fun buildCore(
        snapshot: CycleSnapshot,
        sets: List<SetInput>,
        userBodyweightLbs: Int?,
        realSlotCount: Int = snapshot.splits.count { it.bucketKind == SplitBucketKind.Real },
    ): CoreResult {
        val sessionDateSet = snapshot.splits
            .flatMap { split -> split.exercises.flatMap { exercise -> exercise.sessions.map { it.date } } }
            .toSet()
        val scopedSets = sets
            .asSequence()
            .filter { it.completed && it.date in snapshot.startDate..snapshot.endDate }
            .filter { sessionDateSet.isEmpty() || it.date in sessionDateSet }
            .sortedWith(setInputOrder())
            .toList()
        val sessionDates = if (sessionDateSet.isNotEmpty()) {
            sessionDateSet.sorted()
        } else {
            scopedSets.map { it.date }.distinct().sorted()
        }
        val spanDays = spanDays(snapshot.startDate, snapshot.endDate)

        val totalSetsByLift = mutableMapOf<LiftKey, Int>()
        val lifts = snapshot.splits
            .sortedWith(compareBy({ it.orderIndex }, { it.slotId }))
            .flatMap { split ->
                split.exercises.map { exercise ->
                    val key = LiftKey(exercise.exerciseId, split.slotId)
                    val exerciseDates = exercise.sessions.map { it.date }.toSet()
                    val liftSets = scopedSets.filter { it.exerciseId == exercise.exerciseId && it.date in exerciseDates }
                    totalSetsByLift[key] = liftSets.size
                    val series = sessionSeries(
                        sets = liftSets,
                        isBodyweight = exercise.isBodyweight,
                        userBodyweightLbs = userBodyweightLbs,
                        forcedMetric = null,
                    ) ?: snapshotSeries(exercise)
                    val metric = series?.metric ?: if (exercise.isBodyweight) LiftMetric.REPS else LiftMetric.E1RM
                    val points = series?.points.orEmpty()
                    val regression = linreg(points)
                    LiftTrend(
                        exerciseId = exercise.exerciseId,
                        name = exercise.name,
                        splitSlotId = split.slotId,
                        splitName = split.name,
                        metric = metric,
                        isBodyweight = exercise.isBodyweight,
                        points = points,
                        slopePerWeek = regression?.slopePerDay?.times(7f),
                        r2 = regression?.r2,
                        unitLabel = unitLabel(metric),
                    )
                }
            }

        val core = CycleProgressCore(
            startDate = snapshot.startDate,
            endDate = snapshot.endDate,
            spanDays = spanDays,
            sessions = sessionDates.size,
            sessionsPerWeek = if (spanDays > 0) sessionDates.size * 7f / spanDays else 0f,
            sessionDates = sessionDates,
            totalSets = scopedSets.size,
            repBuckets = repBuckets(scopedSets),
            lifts = lifts,
        )
        return CoreResult(
            core = core,
            realSlotCount = realSlotCount,
            totalSetsByLift = totalSetsByLift,
        )
    }

    fun compare(
        coreResult: CoreResult,
        priorSetsByExerciseId: Map<Long, List<SetInput>>,
        window: ComparisonWindow,
        userBodyweightLbs: Int?,
    ): CycleComparison {
        val core = coreResult.core
        val start = parseDate(core.startDate) ?: return emptyComparison(window)
        val windowStart = start.minusMonths(window.months.toLong()).toString()
        val comparisons = core.lifts.map { lift ->
            val priorSets = priorSetsByExerciseId[lift.exerciseId].orEmpty()
                .asSequence()
                .filter { it.completed && it.date >= windowStart && it.date < core.startDate }
                .sortedWith(setInputOrder())
                .toList()
            val priorSeries = sessionSeries(
                sets = priorSets,
                isBodyweight = lift.isBodyweight,
                userBodyweightLbs = userBodyweightLbs,
                forcedMetric = lift.metric,
            )?.points.orEmpty()
            val priorRegression = if (priorSeries.size >= 3) linreg(priorSeries) else null
            val baselineValue = when {
                priorSeries.size >= 3 && priorRegression != null -> project(priorRegression, core.endDate)
                priorSeries.isNotEmpty() -> priorSeries.last().value
                else -> null
            }
            val isNew = priorSeries.isEmpty()
            val endValue = lift.points.lastOrNull()?.value
            val vsPct = if (!isNew && baselineValue != null && baselineValue > 0f && endValue != null) {
                ((endValue - baselineValue) / baselineValue) * 100f
            } else {
                null
            }
            val currentRate = ratePctPerMonth(linreg(lift.points))
            val priorRate = ratePctPerMonth(priorRegression)
            val momentumScore = if (currentRate != null && priorRate != null) {
                clamp(50f + ((currentRate - priorRate) * MOMENTUM_GAIN).toFloat(), 0f, 100f)
            } else {
                null
            }
            LiftComparison(
                exerciseId = lift.exerciseId,
                name = lift.name,
                metric = lift.metric,
                isBodyweight = lift.isBodyweight,
                isNew = isNew,
                baselineValue = baselineValue,
                endValue = endValue,
                vsPct = vsPct,
                currentRatePctPerMonth = currentRate,
                priorRatePctPerMonth = priorRate,
                momentumScore = momentumScore,
                movement = movement(vsPct),
                totalSets = coreResult.totalSetsByLift[LiftKey(lift.exerciseId, lift.splitSlotId)] ?: 0,
            )
        }

        val compared = comparisons.filter { !it.isNew && it.vsPct != null }
        val improvedCount = compared.count { it.movement == Movement.IMPROVED }
        val heldCount = compared.count { it.movement == Movement.HELD }
        val regressedCount = compared.count { it.movement == Movement.REGRESSED }
        val progression = if (compared.isNotEmpty()) 100f * improvedCount / compared.size else 0f
        return CycleComparison(
            window = window,
            lifts = comparisons,
            progression = progression,
            momentum = median(comparisons.mapNotNull { it.momentumScore }),
            consistency = consistencyScore(core.sessions, coreResult.realSlotCount, core.spanDays),
            improvedCount = improvedCount,
            heldCount = heldCount,
            regressedCount = regressedCount,
            comparedCount = compared.size,
        )
    }

    fun score(c: CycleComparison, w: ScoreWeights): ScoreBreakdown {
        val totalWeight = (w.progression + w.momentum + w.consistency).coerceAtLeast(1)
        val composite = (
            (c.progression * w.progression) +
                (c.momentum * w.momentum) +
                (c.consistency * w.consistency)
            ) / totalWeight
        return ScoreBreakdown(
            composite = composite.roundToInt().coerceIn(0, 100),
            progression = c.progression,
            momentum = c.momentum,
            consistency = c.consistency,
        )
    }

    fun linreg(points: List<TrendPoint>): Regression? {
        if (points.size < 2) return null
        val sorted = points.sortedBy { it.date }
        val firstDate = parseDate(sorted.first().date) ?: return null
        val xs = sorted.map { point ->
            val date = parseDate(point.date) ?: return null
            ChronoUnit.DAYS.between(firstDate, date).toDouble()
        }
        val ys = sorted.map { it.value.toDouble() }
        val xMean = xs.average()
        val yMean = ys.average()
        val ssX = xs.sumOf { x -> (x - xMean) * (x - xMean) }
        if (ssX == 0.0) return null
        val ssTot = ys.sumOf { y -> (y - yMean) * (y - yMean) }
        if (ssTot == 0.0) return null
        val slope = xs.zip(ys).sumOf { (x, y) -> (x - xMean) * (y - yMean) } / ssX
        val intercept = yMean - (slope * xMean)
        val ssRes = xs.zip(ys).sumOf { (x, y) ->
            val projected = intercept + slope * x
            val residual = y - projected
            residual * residual
        }
        val r2 = 1.0 - (ssRes / ssTot)
        return Regression(
            slopePerDay = slope.toFloat(),
            intercept = intercept.toFloat(),
            r2 = r2.toFloat(),
            meanValue = yMean.toFloat(),
            firstDate = sorted.first().date,
        )
    }

    fun project(regression: Regression, date: String): Float {
        val firstDate = parseDate(regression.firstDate) ?: return regression.intercept
        val targetDate = parseDate(date) ?: return regression.intercept
        val x = ChronoUnit.DAYS.between(firstDate, targetDate).toFloat()
        return regression.intercept + regression.slopePerDay * x
    }

    fun ratePctPerMonth(regression: Regression?): Float? {
        if (regression == null || regression.meanValue <= 0f) return null
        return (regression.slopePerDay * 30.44f / regression.meanValue) * 100f
    }

    private fun sessionSeries(
        sets: List<SetInput>,
        isBodyweight: Boolean,
        userBodyweightLbs: Int?,
        forcedMetric: LiftMetric?,
    ): SessionSeries? {
        val resolved = sets
            .map { set -> resolveValue(set, isBodyweight, userBodyweightLbs) }
            .filter { value -> forcedMetric == null || value.metric == forcedMetric }
        val metric = forcedMetric ?: chooseMetric(resolved)
        if (metric == null) return null
        val points = resolved
            .asSequence()
            .filter { it.metric == metric }
            .groupBy { it.date }
            .map { (date, values) -> TrendPoint(date, values.maxOf { it.value }) }
            .sortedBy { it.date }
            .toList()
        if (points.isEmpty()) return null
        return SessionSeries(metric = metric, points = points)
    }

    private fun snapshotSeries(exercise: ExerciseSnapshot): SessionSeries? {
        val metric = if (exercise.isBodyweight) LiftMetric.REPS else LiftMetric.E1RM
        val points = exercise.sessions.mapNotNull { session ->
            snapshotValue(session, metric)?.let { value -> TrendPoint(session.date, value) }
        }
        if (points.isEmpty()) return null
        return SessionSeries(metric = metric, points = points.sortedBy { it.date })
    }

    private fun snapshotValue(session: SessionPoint, metric: LiftMetric): Float? =
        when (metric) {
            LiftMetric.E1RM -> session.bestE1rm
            LiftMetric.ADDED_WEIGHT -> session.topWeight
            LiftMetric.REPS -> session.totalReps.toFloat()
        }

    private fun resolveValue(
        set: SetInput,
        exerciseIsBodyweight: Boolean,
        userBodyweightLbs: Int?,
    ): ResolvedValue {
        val reps = set.reps ?: 0
        val isBodyweight = exerciseIsBodyweight || set.isBodyweight
        if (!isBodyweight) {
            val load = WeightLbs.toLbs(set.weightLbs)
            return ResolvedValue(
                date = set.date,
                metric = LiftMetric.E1RM,
                value = (load * (1.0 + reps / 30.0)).toFloat(),
            )
        }

        val addedStored = set.weightLbs?.takeIf { it > 0 }
        if (addedStored != null) {
            val addedLbs = WeightLbs.toLbs(addedStored)
            return if (userBodyweightLbs != null) {
                val load = userBodyweightLbs + addedLbs
                ResolvedValue(
                    date = set.date,
                    metric = LiftMetric.E1RM,
                    value = (load * (1.0 + reps / 30.0)).toFloat(),
                )
            } else {
                ResolvedValue(
                    date = set.date,
                    metric = LiftMetric.ADDED_WEIGHT,
                    value = addedLbs.toFloat(),
                )
            }
        }

        return ResolvedValue(
            date = set.date,
            metric = LiftMetric.REPS,
            value = reps.toFloat(),
        )
    }

    private fun chooseMetric(values: List<ResolvedValue>): LiftMetric? =
        when {
            values.any { it.metric == LiftMetric.E1RM } -> LiftMetric.E1RM
            values.any { it.metric == LiftMetric.ADDED_WEIGHT } -> LiftMetric.ADDED_WEIGHT
            values.any { it.metric == LiftMetric.REPS } -> LiftMetric.REPS
            else -> null
        }

    private fun repBuckets(sets: List<SetInput>): List<RepBucket> {
        val buckets = listOf(
            BucketDef("Strength", 1, 5),
            BucketDef("Hypertrophy", 6, 12),
            BucketDef("Endurance", 13, 19),
            BucketDef("Metabolic", 20, Int.MAX_VALUE),
        )
        val total = sets.size
        return buckets.map { bucket ->
            val count = sets.count { set ->
                val reps = set.reps ?: 0
                reps >= bucket.min && reps <= bucket.max
            }
            RepBucket(
                label = bucket.label,
                minReps = bucket.min,
                maxReps = bucket.max,
                sets = count,
                pctOfSets = if (total > 0) count * 100f / total else 0f,
            )
        }
    }

    private data class BucketDef(val label: String, val min: Int, val max: Int)

    private fun consistencyScore(sessions: Int, realSlotCount: Int, spanDays: Int): Float {
        if (sessions <= 0 || realSlotCount <= 0 || spanDays <= 0) return 0f
        // v1 heuristic: one expected session per real slot per week.
        val plannedSessions = (realSlotCount * spanDays / 7f).roundToInt()
        if (plannedSessions <= 0) return 0f
        return clamp(sessions * 100f / plannedSessions, 0f, 100f)
    }

    private fun movement(vsPct: Float?): Movement =
        when {
            vsPct != null && vsPct > 1f -> Movement.IMPROVED
            vsPct != null && vsPct < -1f -> Movement.REGRESSED
            else -> Movement.HELD
        }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }

    private fun unitLabel(metric: LiftMetric): String =
        when (metric) {
            LiftMetric.E1RM, LiftMetric.ADDED_WEIGHT -> "lb/wk"
            LiftMetric.REPS -> "reps/wk"
        }

    private fun emptyComparison(window: ComparisonWindow): CycleComparison =
        CycleComparison(
            window = window,
            lifts = emptyList(),
            progression = 0f,
            momentum = 0f,
            consistency = 0f,
            improvedCount = 0,
            heldCount = 0,
            regressedCount = 0,
            comparedCount = 0,
        )

    private fun spanDays(startDate: String, endDate: String): Int =
        runCatching {
            (ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1)
                .toInt()
                .coerceAtLeast(0)
        }.getOrDefault(0)

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

    private fun clamp(value: Float, min: Float, max: Float): Float =
        value.coerceIn(min, max)

    private fun setInputOrder(): Comparator<SetInput> =
        compareBy<SetInput> { it.date }.thenBy { it.setNumber }.thenBy { it.id }
}
