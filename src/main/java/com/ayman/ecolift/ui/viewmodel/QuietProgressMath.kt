package com.ayman.ecolift.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.util.formatFixed
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class QuietProgressPoint(val date: String, val value: Float, val evidence: String)

@Immutable
data class QuietProgressOverview(
    val points: List<QuietProgressPoint> = emptyList(),
    val metricLabel: String = "Est. 1RM",
    val deltaLabel: String = "No sessions",
    val deltaSign: Int = 0,
    val previous: QuietProgressPoint? = null,
    val latest: QuietProgressPoint? = null,
    val isRecord: Boolean = false,
    val recordLabel: String = "Best yet",
    val sessionChangeLabel: String? = null,
    val valueSuffix: String = "lb Est. 1RM",
    val definition: String = "Estimated 1RM uses weight × (1 + reps ÷ 30). Only completed working sets are included.",
)

/** A real dated series and its proof. No warmups, drafts, future work, or profile-weight estimates. */
internal fun buildQuietProgressOverview(
    history: List<WorkoutSet>,
    isBodyweight: Boolean,
    start: LocalDate,
    end: LocalDate,
): QuietProgressOverview {
    val eligible = history.filter {
        WorkoutMetrics.isStrengthEligible(it) &&
            it.date <= end.toString() &&
            (isBodyweight || WorkoutMetrics.estimatedOneRepMax(it) != null)
    }
    val sessions = eligible.groupBy { it.date }.entries.sortedBy { it.key }.mapNotNull { (_, sets) ->
        if (isBodyweight) sets.maxWithOrNull(compareBy<WorkoutSet> { it.weightLbs ?: 0 }.thenBy { it.reps ?: 0 })
        else sets.maxByOrNull { WorkoutMetrics.estimatedOneRepMax(it) ?: 0.0 }
    }
    val window = sessions.filter { it.date >= start.toString() }
    val latestSet = window.lastOrNull() ?: return QuietProgressOverview(metricLabel = if (isBodyweight) "reps" else "Est. 1RM")
    val sameLoad = isBodyweight || (window.size > 1 && window.map { it.weightLbs }.distinct().size == 1)
    val load = latestSet.weightLbs ?: 0
    val atLoad = if (isBodyweight) {
        if (load == 0) "at BW" else "at BW +${WeightLbs.formatStored(load)} lb"
    } else {
        "at ${WeightLbs.formatStored(load)} lb"
    }
    val comparable = if (sameLoad) eligible.filter { (it.weightLbs ?: 0) == load }
        .groupBy { it.date }.entries.sortedBy { it.key }.mapNotNull { (_, sets) -> sets.maxByOrNull { it.reps ?: 0 } }
    else sessions
    fun evidence(set: WorkoutSet): String =
        (if (isBodyweight) {
            if ((set.weightLbs ?: 0) == 0) "BW" else "BW +${WeightLbs.formatStored(set.weightLbs)} lb"
        } else {
            "${WeightLbs.formatStored(set.weightLbs)} lb"
        }) + " × ${set.reps}"
    val allPoints = comparable.map { set ->
        QuietProgressPoint(
        date = set.date,
        value = if (sameLoad) (set.reps ?: 0).toFloat() else (WorkoutMetrics.estimatedOneRepMax(set) ?: 0.0).toFloat(),
        evidence = evidence(set),
    )
    }
    val points = allPoints.filter { it.date >= start.toString() }
    val latest = points.lastOrNull() ?: return QuietProgressOverview()
    val first = points.first()
    val previous = allPoints.lastOrNull { it.date < latest.date }
    val rangeChange = latest.value - first.value
    val priorBest = allPoints.filter { it.date < latest.date }.maxOfOrNull { it.value }
    val sessionChange = previous?.let { latest.value - it.value }
    fun signed(value: Float, units: String, whole: Boolean): String =
        (if (value > .0001f) "+" else if (value < -.0001f) "−" else "") +
            (if (whole) abs(value).roundToInt().toString() else formatFixed(abs(value).toDouble(), 1)) + units
    return QuietProgressOverview(
        points = points,
        metricLabel = if (sameLoad) atLoad else "Est. 1RM",
        deltaLabel = when {
            points.size < 2 -> "1 session"
            abs(rangeChange) < .0001f -> "Steady"
            sameLoad -> signed(rangeChange, " reps", true)
            first.value > 0 -> signed(rangeChange / first.value * 100f, "%", false)
            else -> "—"
        },
        deltaSign = if (points.size < 2) 0 else rangeChange.compareTo(0f),
        previous = previous,
        latest = latest,
        isRecord = priorBest != null && latest.value > priorBest + .0001f,
        recordLabel = if (sameLoad) "Best reps" else "Best yet",
        sessionChangeLabel = sessionChange?.let {
            // Match the rounded values shown below, avoiding 14 lb from a 301→287.5 mismatch.
            signed(if (sameLoad) it else latest.value.roundToInt().toFloat() - previous!!.value.roundToInt(), if (sameLoad) " reps" else " lb", true)
        },
        valueSuffix = if (sameLoad) "reps · $atLoad" else "lb Est. 1RM",
        definition = if (sameLoad) "Completed working-set reps compared $atLoad. Changes in load are not treated as rep improvements."
        else "Estimated 1RM uses weight × (1 + reps ÷ 30), rounded to the nearest pound. Completed working sets only.",
    )
}
