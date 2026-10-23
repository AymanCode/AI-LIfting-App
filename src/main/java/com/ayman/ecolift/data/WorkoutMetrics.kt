package com.ayman.ecolift.data

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Recorded external load only. Profile weight is never substituted into historical work. */
object WorkoutMetrics {
    const val VOLUME_DEFINITION = "EXTERNAL_COMPLETED_V1"
    const val WORKING_VOLUME_DEFINITION = "EXTERNAL_WORKING_V1"
    fun loggedVolumeLbs(set: WorkoutSet): Double =
        if (set.completed) WeightLbs.toLbs(set.weightLbs) * (set.reps ?: 0) else 0.0
    fun loggedVolumeLbs(sets: Iterable<WorkoutSet>): Double = sets.sumOf(::loggedVolumeLbs)
    fun workingVolumeLbs(sets: Iterable<WorkoutSet>): Double = loggedVolumeLbs(sets.filterNot { it.isWarmup })
    fun roundedVolume(sets: Iterable<WorkoutSet>): Long = loggedVolumeLbs(sets).roundToLong()
    fun roundedVolumeInt(sets: Iterable<WorkoutSet>): Int = loggedVolumeLbs(sets).roundToInt()
    fun isStrengthEligible(set: WorkoutSet): Boolean = set.completed && !set.isWarmup && (set.reps ?: 0) > 0
    /** BW e1RM needs dated bodyweight, which existing records do not carry. */
    fun estimatedOneRepMax(set: WorkoutSet, exerciseIsBodyweight: Boolean = false): Double? =
        if (!isStrengthEligible(set) || exerciseIsBodyweight || set.isBodyweight || (set.weightLbs ?: 0) <= 0) null
        else epleyEstimate(WeightLbs.toLbs(set.weightLbs), set.reps ?: 0)
}
