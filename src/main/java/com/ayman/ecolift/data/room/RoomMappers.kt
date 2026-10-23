package com.ayman.ecolift.data.room

import com.ayman.ecolift.data.ArchivedCycle as DomainArchivedCycle
import com.ayman.ecolift.data.CardioSession as DomainCardioSession
import com.ayman.ecolift.data.Cycle as DomainCycle
import com.ayman.ecolift.data.CycleSlot as DomainCycleSlot
import com.ayman.ecolift.data.Exercise as DomainExercise
import com.ayman.ecolift.data.PendingReview as DomainPendingReview
import com.ayman.ecolift.data.SplitExercise as DomainSplitExercise
import com.ayman.ecolift.data.UserSettings as DomainUserSettings
import com.ayman.ecolift.data.WorkoutDay as DomainWorkoutDay
import com.ayman.ecolift.data.WorkoutSet as DomainWorkoutSet

fun Exercise.toDomain() = DomainExercise(
    id = id,
    name = name,
    muscleGroups = muscleGroups,
    isBodyweight = isBodyweight,
    createdAt = createdAt,
)

fun DomainExercise.toRoom() = Exercise(
    id = id,
    name = name,
    muscleGroups = muscleGroups,
    isBodyweight = isBodyweight,
    createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
)

fun WorkoutSet.toDomain() = DomainWorkoutSet(
    id = id,
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed,
    restTimeSeconds = restTimeSeconds,
)

fun DomainWorkoutSet.toRoom() = WorkoutSet(
    id = id,
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed,
    restTimeSeconds = restTimeSeconds,
)

fun WorkoutDay.toDomain() = DomainWorkoutDay(
    date = date,
    cycleSlotType = cycleSlotType,
    cycleSlotOccurrence = cycleSlotOccurrence,
    alternativeForDate = alternativeForDate,
    cycleSlotId = cycleSlotId,
)

fun DomainWorkoutDay.toRoom() = WorkoutDay(
    date = date,
    cycleSlotType = cycleSlotType,
    cycleSlotOccurrence = cycleSlotOccurrence,
    alternativeForDate = alternativeForDate,
    cycleSlotId = cycleSlotId,
)

fun Cycle.toDomain() = DomainCycle(
    id = id,
    numTypes = numTypes,
    isActive = isActive,
    nextSessionType = nextSessionType,
    startDate = startDate,
    name = name,
)

fun DomainCycle.toRoom() = Cycle(
    id = id,
    numTypes = numTypes,
    isActive = isActive,
    nextSessionType = nextSessionType,
    startDate = startDate,
    name = name,
)

fun CycleSlot.toDomain() = DomainCycleSlot(
    id = id,
    name = name,
    orderIndex = orderIndex,
)

fun DomainCycleSlot.toRoom() = CycleSlot(
    id = id,
    name = name,
    orderIndex = orderIndex,
)

fun SplitExercise.toDomain() = DomainSplitExercise(
    id = id,
    splitId = splitId,
    exerciseId = exerciseId,
    orderIndex = orderIndex,
)

fun DomainSplitExercise.toRoom() = SplitExercise(
    id = id,
    splitId = splitId,
    exerciseId = exerciseId,
    orderIndex = orderIndex,
)

fun PendingReview.toDomain() = DomainPendingReview(
    id = id,
    rawInput = rawInput,
    dateLogged = dateLogged,
    resolved = resolved,
)

fun DomainPendingReview.toRoom() = PendingReview(
    id = id,
    rawInput = rawInput,
    dateLogged = dateLogged,
    resolved = resolved,
)

fun ArchivedCycle.toDomain() = DomainArchivedCycle(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    splitCount = splitCount,
    totalVolumeLbs = totalVolumeLbs,
    totalSessions = totalSessions,
    archivedAt = archivedAt,
    schemaVersion = schemaVersion,
    snapshotJson = snapshotJson,
)

fun DomainArchivedCycle.toRoom() = ArchivedCycle(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    splitCount = splitCount,
    totalVolumeLbs = totalVolumeLbs,
    totalSessions = totalSessions,
    archivedAt = archivedAt,
    schemaVersion = schemaVersion,
    snapshotJson = snapshotJson,
)

fun UserSettings.toDomain() = DomainUserSettings(
    userBodyweightLbs = userBodyweightLbs,
    glassPaletteChoice = glassPaletteChoice,
)

fun DomainUserSettings.toRoom() = UserSettings(
    id = USER_SETTINGS_ID,
    userBodyweightLbs = userBodyweightLbs,
    glassPaletteChoice = glassPaletteChoice,
)

fun CardioSession.toDomain() = DomainCardioSession(
    id = id,
    localUuid = localUuid,
    date = date,
    activityType = activityType,
    activityLabel = activityLabel,
    durationSec = durationSec,
    distanceM = distanceM,
    calories = calories,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    avgSpeed = avgSpeed,
    avgInclinePercent = avgInclinePercent,
    cadenceRpm = cadenceRpm,
    resistanceLevel = resistanceLevel,
    avgPowerWatts = avgPowerWatts,
    strokeRateSpm = strokeRateSpm,
    paceSecPer500m = paceSecPer500m,
    floors = floors,
    steps = steps,
    source = source,
    machineType = machineType,
    ocrConfidence = ocrConfidence,
    notes = notes,
    startTime = startTime,
    endTime = endTime,
    zoneOffsetSeconds = zoneOffsetSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    hcUid = hcUid,
    hcDataOriginPackage = hcDataOriginPackage,
    hcLastModifiedTime = hcLastModifiedTime,
    ocrEngineVersion = ocrEngineVersion,
)

fun DomainCardioSession.toRoom() = CardioSession(
    id = id,
    localUuid = localUuid,
    date = date,
    activityType = activityType,
    activityLabel = activityLabel,
    durationSec = durationSec,
    distanceM = distanceM,
    calories = calories,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    avgSpeed = avgSpeed,
    avgInclinePercent = avgInclinePercent,
    cadenceRpm = cadenceRpm,
    resistanceLevel = resistanceLevel,
    avgPowerWatts = avgPowerWatts,
    strokeRateSpm = strokeRateSpm,
    paceSecPer500m = paceSecPer500m,
    floors = floors,
    steps = steps,
    source = source,
    hcUid = hcUid,
    hcDataOriginPackage = hcDataOriginPackage,
    hcLastModifiedTime = hcLastModifiedTime,
    startTime = startTime,
    endTime = endTime,
    zoneOffsetSeconds = zoneOffsetSeconds,
    machineType = machineType,
    ocrConfidence = ocrConfidence,
    ocrEngineVersion = ocrEngineVersion,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
