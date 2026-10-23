package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.web.util.minusDays
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.round

enum class ProgressMetric(val label: String, val higherIsBetter: Boolean, val atHeartRate: Boolean) {
    SPEED_AT_HR("Speed at heart rate", true, true),
    SPEED("Speed", true, false),
    PACE("Pace", false, false),
    DISTANCE("Distance", true, false),
    SPLIT_AT_HR("Split at heart rate", false, true),
    SPLIT("Split per 500 m", false, false),
    STROKE_RATE("Stroke rate", true, false),
    POWER_AT_HR("Power at heart rate", true, true),
    POWER("Power", true, false),
    CADENCE("Cadence", true, false),
    RESISTANCE("Resistance", true, false),
    FLOORS_PER_MIN("Floors per minute", true, false),
    FLOORS("Floors", true, false),
    DURATION("Duration", true, false),
    PACE_100("Pace per 100 m", false, false),
}

data class ProgressPoint(val date: LocalDate, val value: Double, val sessionId: Long)

data class MachineProgress(
    val metric: ProgressMetric,
    val available: Boolean,
    val unavailableReason: String?,
    val points: List<ProgressPoint>,
    val excluded: List<ProgressPoint>, // excluded: hollow dots on the baseline (value = 0.0)
    val latest: Double?,
    val baseline: Double?,
    val delta: Double?,
    val referenceHeartRate: Int?,
    val eligibleCount: Int,
    val excludedCount: Int,
    val sentence: String,
    val unit: String,
)

object CardioProgress {
    private val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun metricsFor(type: CardioActivityType): List<ProgressMetric> = when (type) {
        CardioActivityType.TREADMILL, CardioActivityType.RUN, CardioActivityType.WALK -> listOf(
            ProgressMetric.SPEED_AT_HR, ProgressMetric.SPEED, ProgressMetric.PACE, ProgressMetric.DISTANCE
        )
        CardioActivityType.ROW -> listOf(
            ProgressMetric.SPLIT_AT_HR, ProgressMetric.SPLIT, ProgressMetric.DISTANCE, ProgressMetric.STROKE_RATE
        )
        CardioActivityType.BIKE -> listOf(
            ProgressMetric.POWER_AT_HR, ProgressMetric.POWER, ProgressMetric.CADENCE, ProgressMetric.DISTANCE
        )
        CardioActivityType.ELLIPTICAL -> listOf(
            ProgressMetric.CADENCE, ProgressMetric.RESISTANCE, ProgressMetric.DISTANCE, ProgressMetric.DURATION
        )
        CardioActivityType.STAIR_CLIMBER -> listOf(
            ProgressMetric.FLOORS_PER_MIN, ProgressMetric.FLOORS, ProgressMetric.DURATION
        )
        CardioActivityType.SWIM -> listOf(
            ProgressMetric.PACE_100, ProgressMetric.DISTANCE, ProgressMetric.DURATION
        )
        CardioActivityType.OTHER -> listOf(
            ProgressMetric.DURATION, ProgressMetric.DISTANCE
        )
    }

    fun metricValue(session: CardioSession, metric: ProgressMetric): Double? = when (metric) {
        ProgressMetric.SPEED_AT_HR, ProgressMetric.SPEED -> {
            session.avgSpeed?.let { it * 2.23694 }
                ?: if ((session.durationSec ?: 0) > 0 && (session.distanceM ?: 0.0) > 0.0) {
                    (session.distanceM!! / session.durationSec!!) * 2.23694
                } else null
        }
        ProgressMetric.PACE -> CardioInsights.derivedPaceSec(session)?.toDouble()
        ProgressMetric.DISTANCE -> {
            val type = CardioInsights.activityType(session)
            if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) {
                session.distanceM
            } else {
                session.distanceM?.let { it / 1609.344 }
            }
        }
        ProgressMetric.SPLIT_AT_HR, ProgressMetric.SPLIT -> {
            (session.paceSecPer500m ?: CardioInsights.derivedPaceSec(session))?.toDouble()
        }
        ProgressMetric.STROKE_RATE -> session.strokeRateSpm?.toDouble()
        ProgressMetric.POWER_AT_HR, ProgressMetric.POWER -> session.avgPowerWatts?.toDouble()
        ProgressMetric.CADENCE -> session.cadenceRpm?.toDouble()
        ProgressMetric.RESISTANCE -> session.resistanceLevel
        ProgressMetric.FLOORS_PER_MIN -> {
            val f = session.floors
            val dur = session.durationSec
            if (f != null && dur != null && dur > 0) {
                f.toDouble() / (dur / 60.0)
            } else null
        }
        ProgressMetric.FLOORS -> session.floors?.toDouble()
        ProgressMetric.DURATION -> session.durationSec?.let { it / 60.0 }
        ProgressMetric.PACE_100 -> CardioInsights.derivedPaceSec(session)?.toDouble()
    }

    private fun unitFor(type: CardioActivityType, metric: ProgressMetric): String = when (metric) {
        ProgressMetric.SPEED_AT_HR, ProgressMetric.SPEED -> "mph"
        ProgressMetric.PACE -> "s per mile"
        ProgressMetric.SPLIT_AT_HR, ProgressMetric.SPLIT -> "s per 500 m"
        ProgressMetric.PACE_100 -> "s per 100 m"
        ProgressMetric.DISTANCE -> if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) "m" else "miles"
        ProgressMetric.STROKE_RATE -> "spm"
        ProgressMetric.POWER_AT_HR, ProgressMetric.POWER -> "w"
        ProgressMetric.CADENCE -> "rpm"
        ProgressMetric.RESISTANCE -> "level"
        ProgressMetric.FLOORS_PER_MIN -> "floors/min"
        ProgressMetric.FLOORS -> "floors"
        ProgressMetric.DURATION -> "minutes"
    }

    private fun formatOneDecimal(v: Double): String {
        val rounded = round(abs(v) * 10).toLong()
        val whole = rounded / 10
        val frac = rounded % 10
        val sign = if (v < 0 && rounded != 0L) "-" else ""
        return "$sign$whole.$frac"
    }

    fun machineProgress(
        sessions: List<CardioSession>,
        type: CardioActivityType,
        metric: ProgressMetric,
        today: LocalDate,
    ): MachineProgress {
        val windowStart = today.minusDays(89)
        val windowSessions = sessions.filter { session ->
            CardioInsights.activityType(session) == type &&
            run {
                val d = runCatching { LocalDate.parse(session.date) }.getOrNull() ?: return@run false
                d in windowStart..today
            }
        }.sortedWith(
            compareBy<CardioSession> { LocalDate.parse(it.date) }.thenBy { it.createdAt }
        )

        val latestWindowSession = windowSessions.lastOrNull()
        val latestDuration = latestWindowSession?.durationSec ?: 0

        val hrSessions = windowSessions.filter { CardioInsights.countsForActivity(it) && it.avgHeartRate != null }
        val referenceHeartRate: Int? = if (hrSessions.isEmpty()) {
            null
        } else {
            val sortedHrs = hrSessions.map { it.avgHeartRate!! }.sorted()
            val size = sortedHrs.size
            val median = if (size % 2 == 1) {
                sortedHrs[size / 2].toDouble()
            } else {
                (sortedHrs[size / 2 - 1] + sortedHrs[size / 2]) / 2.0
            }
            round(median).toInt()
        }

        val eligiblePoints = mutableListOf<ProgressPoint>()
        val excludedPoints = mutableListOf<ProgressPoint>()

        for (session in windowSessions) {
            val rawValue = metricValue(session, metric) ?: continue
            val counts = CardioInsights.countsForActivity(session)
            val dur = session.durationSec ?: 0
            val durEligible = latestDuration > 0 && abs(dur - latestDuration).toDouble() / latestDuration <= 0.25
            val date = LocalDate.parse(session.date)

            if (metric.atHeartRate) {
                val hr = session.avgHeartRate
                val hrEligible = hr != null && referenceHeartRate != null && abs(hr - referenceHeartRate) <= 15
                if (counts && durEligible && hrEligible) {
                    val normValue = if (metric.higherIsBetter) {
                        rawValue * referenceHeartRate!!.toDouble() / hr!!.toDouble()
                    } else {
                        rawValue * hr!!.toDouble() / referenceHeartRate!!.toDouble()
                    }
                    eligiblePoints.add(ProgressPoint(date = date, value = normValue, sessionId = session.id))
                } else {
                    excludedPoints.add(ProgressPoint(date = date, value = 0.0, sessionId = session.id))
                }
            } else {
                if (counts && durEligible) {
                    eligiblePoints.add(ProgressPoint(date = date, value = rawValue, sessionId = session.id))
                } else {
                    excludedPoints.add(ProgressPoint(date = date, value = 0.0, sessionId = session.id))
                }
            }
        }

        val eligibleCount = eligiblePoints.size
        val excludedCount = excludedPoints.size
        val unit = unitFor(type, metric)

        if (eligibleCount < 6) {
            val unavailableReason = if (metric.atHeartRate) "needs 6 sessions with heart rate" else "needs 6 sessions of similar length"
            val sentence = "Not enough sessions yet. $unavailableReason."
            return MachineProgress(
                metric = metric,
                available = false,
                unavailableReason = unavailableReason,
                points = eligiblePoints,
                excluded = excludedPoints,
                latest = null,
                baseline = null,
                delta = null,
                referenceHeartRate = referenceHeartRate,
                eligibleCount = eligibleCount,
                excludedCount = excludedCount,
                sentence = sentence,
                unit = unit,
            )
        }

        val earliest3 = eligiblePoints.take(3)
        val latest3 = eligiblePoints.takeLast(3)
        val baseline = earliest3.map { it.value }.average()
        val latest = latest3.map { it.value }.average()
        val delta = latest - baseline

        val baselineMonth = MONTH_NAMES[earliest3.first().date.month.ordinal]

        val isOneDecimal = metric in listOf(
            ProgressMetric.SPEED, ProgressMetric.SPEED_AT_HR,
            ProgressMetric.DISTANCE, ProgressMetric.POWER, ProgressMetric.POWER_AT_HR
        )
        val roundedDelta = if (isOneDecimal) round(delta * 10) / 10.0 else round(delta)

        val sentence = if (abs(roundedDelta) == 0.0) {
            val latestStr = if (isOneDecimal) formatOneDecimal(latest) else round(latest).toInt().toString()
            "Holding steady at $latestStr $unit since $baselineMonth."
        } else {
            val deltaStr = if (isOneDecimal) formatOneDecimal(abs(delta)) else round(abs(delta)).toInt().toString()
            val word = when (metric) {
                ProgressMetric.SPEED, ProgressMetric.SPEED_AT_HR,
                ProgressMetric.PACE, ProgressMetric.SPLIT,
                ProgressMetric.SPLIT_AT_HR, ProgressMetric.PACE_100,
                ProgressMetric.FLOORS_PER_MIN -> {
                    val improved = if (metric.higherIsBetter) delta > 0 else delta < 0
                    if (improved) "faster" else "slower"
                }
                ProgressMetric.DISTANCE, ProgressMetric.FLOORS -> {
                    if (delta > 0) "more" else "fewer"
                }
                else -> {
                    if (delta > 0) "more" else "fewer"
                }
            }

            if (metric.atHeartRate) {
                val clause = if (excludedCount > 0) "; $excludedCount without it are drawn hollow" else ""
                val adj = when (type) {
                    CardioActivityType.BIKE, CardioActivityType.ELLIPTICAL -> " Resistance is not adjusted for."
                    CardioActivityType.ROW -> ""
                    else -> " Incline is not adjusted for."
                }
                "$deltaStr $unit $word at $referenceHeartRate bpm than $baselineMonth. Normalised to heart rate over $eligibleCount sessions$clause.$adj"
            } else {
                "$deltaStr $unit $word than $baselineMonth over sessions of similar length."
            }
        }

        return MachineProgress(
            metric = metric,
            available = true,
            unavailableReason = null,
            points = eligiblePoints,
            excluded = excludedPoints,
            latest = latest,
            baseline = baseline,
            delta = delta,
            referenceHeartRate = referenceHeartRate,
            eligibleCount = eligibleCount,
            excludedCount = excludedCount,
            sentence = sentence,
            unit = unit,
        )
    }
}
