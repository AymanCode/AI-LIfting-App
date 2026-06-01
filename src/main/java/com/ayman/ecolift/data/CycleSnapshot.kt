package com.ayman.ecolift.data

import kotlinx.serialization.Serializable

const val CYCLE_SNAPSHOT_SCHEMA_VERSION = 1

@Serializable
data class CycleSnapshot(
    val schemaVersion: Int = CYCLE_SNAPSHOT_SCHEMA_VERSION,
    val startDate: String,
    val endDate: String,
    val totals: CycleTotals,
    val splits: List<SplitSnapshot>,
)

@Serializable
data class CycleTotals(
    val sessions: Int,
    val totalVolumeLbs: Long,
    val totalSets: Int,
    val spanDays: Int,
)

@Serializable
data class SplitSnapshot(
    val slotId: Long,
    val bucketKind: SplitBucketKind = SplitBucketKind.Real,
    val name: String,
    val orderIndex: Int,
    val firstUsedDate: String?,
    val lastUsedDate: String?,
    val usageCount: Int,
    val exercises: List<ExerciseSnapshot>,
)

@Serializable
enum class SplitBucketKind {
    Real,
    Deleted,
    Unassigned,
}

@Serializable
data class ExerciseSnapshot(
    val exerciseId: Long,
    val name: String,
    val isBodyweight: Boolean,
    val sessions: List<SessionPoint>,
    val startE1rm: Float?,
    val endE1rm: Float?,
    val startTopWeight: Float?,
    val endTopWeight: Float?,
    val startVolumeLbs: Long?,
    val endVolumeLbs: Long?,
)

@Serializable
data class SessionPoint(
    val date: String,
    val topWeight: Float?,
    val bestE1rm: Float?,
    val volumeLbs: Long,
    val totalReps: Int,
    val setCount: Int,
)

data class ExerciseMeta(
    val name: String,
    val isBodyweight: Boolean,
)
