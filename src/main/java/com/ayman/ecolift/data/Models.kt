package com.ayman.ecolift.data

// Domain models mirroring the Android Room entities (field names identical so
// copied UI/logic code compiles unchanged). Persistence lives in web.supabase.

import kotlinx.serialization.Serializable

const val UNCLASSIFIED_MUSCLE_GROUPS = ""
const val LEGACY_DEFAULT_MUSCLE_GROUPS = "CHEST · TRICEPS"

data class Exercise(
    val id: Long = 0,
    val name: String,
    val muscleGroups: String = UNCLASSIFIED_MUSCLE_GROUPS,
    val isBodyweight: Boolean = false,
    val createdAt: Long = 0,
    val mergedIntoId: Long? = null,
)

data class ExerciseAlias(
    val id: Long = 0,
    val userId: String = "",
    val exerciseId: Long,
    val alias: String,
    val normalizedKey: String,
)

data class ExerciseDistinctPair(
    val id: Long = 0,
    val userId: String = "",
    val exerciseIdA: Long,
    val exerciseIdB: Long,
)

data class WorkoutSet(
    val id: Long = 0,
    val exerciseId: Long,
    val date: String,
    val setNumber: Int,
    val weightLbs: Int? = null,
    val reps: Int? = null,
    val isBodyweight: Boolean = false,
    val completed: Boolean = false,
    val restTimeSeconds: Int? = null,
    val syncId: String = "",
    val rowRevision: Long = 0,
    val cardId: String = "",
    val isWarmup: Boolean = false,
)

/** A checked working set: the rows every derived number counts. */
val WorkoutSet.isCheckedWorking: Boolean get() = completed && !isWarmup

@Serializable
enum class WorkoutCardDisplayMode {
    EXPANDED,
    COMPACT,
}

@Serializable
enum class WorkoutCardOrigin {
    LEGACY,
    MANUAL,
    SPLIT,
    IMPORT,
    MCP,
}

data class WorkoutExerciseCard(
    val id: String,
    val date: String,
    val exerciseId: Long,
    val position: Int,
    val displayMode: WorkoutCardDisplayMode,
    val origin: WorkoutCardOrigin,
    val sourcePlanId: String? = null,
    val rowRevision: Long = 1,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class UserSettings(
    val userBodyweightLbs: Int? = null,
    val glassPaletteChoice: String? = null,
    val timeZone: String = "UTC",
    val unitSystem: UnitSystem = UnitSystem.LB,
    val smartSuggest: Boolean = true,
)

@Serializable
enum class UnitSystem {
    LB,
    KG,
}

data class ExerciseSuggestion(
    val exercise: Exercise,
    val distance: Int,
)

data class WorkoutDay(
    val date: String,
    val cycleSlotType: Int? = null,
    val cycleSlotOccurrence: Int? = null,
    val alternativeForDate: String? = null,
    val cycleSlotId: Long? = null,
)

data class Cycle(
    val id: Int = 1,
    val numTypes: Int = 3,
    val isActive: Boolean = false,
    val nextSessionType: Int? = null,
    val startDate: String? = null,
    val name: String? = null,
)

data class CycleSlot(
    val id: Long = 0,
    val name: String,
    val orderIndex: Int = 0,
    val archivedAt: Long? = null,
    /** True once the engine owns the membership (a learned group, or a seed with two sessions). */
    val learned: Boolean = false,
    /** Epoch millis; 0 for rows that predate learned splits, so none of them is "fresh". */
    val createdAt: Long = 0,
    /** Bumped each time a hand-made list is edited. */
    val seedRevision: Int = 0,
    /** Epoch millis of the last membership write (engine anchor refresh or hand edit). */
    val anchorUpdatedAt: Long? = null,
    /** Epoch millis when the user retired the split; distinct from the cycle archive's [archivedAt]. */
    val suppressedAt: Long? = null,
    /** The calendar colour; kept for the slot's lifetime so reordering never recolours history. */
    val colorIndex: Int = 0,
) {
    val isActive: Boolean get() = archivedAt == null && suppressedAt == null
    val isSuppressed: Boolean get() = archivedAt == null && suppressedAt != null

    /** A split the user made or named; the engine's own splits are learned with a blank name. */
    val isUserOwned: Boolean get() = !learned || name.isNotBlank()
}

data class SplitExercise(
    val id: Long = 0,
    val splitId: Long,
    val exerciseId: Long,
    val orderIndex: Int = 0,
    val userAdded: Boolean = false,
)

data class PendingReview(
    val id: Long = 0,
    val rawInput: String,
    val dateLogged: String,
    val resolved: Boolean = false,
)

data class ArchivedCycle(
    val id: Long = 0,
    val name: String,
    val startDate: String,
    val endDate: String,
    val splitCount: Int,
    val totalVolumeLbs: Long,
    val totalSessions: Int,
    val archivedAt: Long,
    val schemaVersion: Int,
    val snapshotJson: String,
)

@Serializable
data class CardioSession(
    val id: Long = 0,
    val localUuid: String,
    val date: String,
    val activityType: String,
    val activityLabel: String? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val calories: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgSpeed: Double? = null,
    val avgInclinePercent: Double? = null,
    val cadenceRpm: Int? = null,
    val resistanceLevel: Double? = null,
    val avgPowerWatts: Int? = null,
    val strokeRateSpm: Int? = null,
    val paceSecPer500m: Int? = null,
    val floors: Int? = null,
    val steps: Int? = null,
    val source: String,
    val machineType: String? = null,
    val ocrConfidence: Double? = null,
    val notes: String = "",
    val startTime: Long? = null,
    val endTime: Long? = null,
    val zoneOffsetSeconds: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val hcUid: String? = null,
    val hcDataOriginPackage: String? = null,
    val hcLastModifiedTime: Long? = null,
    val ocrEngineVersion: String? = null,
)

@Serializable
enum class CardioActivityType {
    TREADMILL, RUN, BIKE, ROW, SWIM, WALK, ELLIPTICAL, STAIR_CLIMBER, OTHER
}

object CardioSessionSource {
    const val MANUAL = "manual"
    const val OCR = "ocr"
    const val HEALTH_CONNECT = "health_connect"
}
