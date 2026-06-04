package com.ayman.ecolift.data

import androidx.room.withTransaction
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCalculator
import com.ayman.ecolift.data.progress.CycleProgressCore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class WorkoutRepository(private val db: AppDatabase) {
    val cycle: Flow<Cycle> = db.cycleDao().observeCycle().map { it ?: Cycle() }
    private val archiveJson = Json { ignoreUnknownKeys = true }

    fun observeWorkoutDay(date: String): Flow<WorkoutDay?> = db.workoutDayDao().observeByDate(date)

    fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = db.workoutDayDao().observeAll()

    fun observeCycleSlots(): Flow<List<CycleSlot>> = db.cycleSlotDao().observeAll()

    suspend fun getCycleSlots(): List<CycleSlot> = db.cycleSlotDao().getAll()

    suspend fun getUserBodyweightLbs(): Int? =
        normalizedUserBodyweightLbs(db.userSettingsDao().get()?.userBodyweightLbs)

    fun observeUserBodyweightLbs(): Flow<Int?> =
        db.userSettingsDao().observe().map { normalizedUserBodyweightLbs(it?.userBodyweightLbs) }

    suspend fun setUserBodyweightLbs(bodyweightLbs: Int?) {
        db.withTransaction {
            val current = db.userSettingsDao().get() ?: UserSettings()
            db.userSettingsDao().upsert(
                current.copy(userBodyweightLbs = normalizedUserBodyweightLbs(bodyweightLbs))
            )
        }
    }

    suspend fun addCycleSlot(name: String): Long = db.withTransaction {
        val nextOrder = db.cycleSlotDao().getMaxOrderIndex() + 1
        val id = db.cycleSlotDao().upsert(CycleSlot(name = name, orderIndex = nextOrder))
        syncCycleSlotCount()
        id
    }

    suspend fun deleteCycleSlot(id: Long) = db.withTransaction {
        db.cycleSlotDao().delete(id)
        syncCycleSlotCount()
    }

    suspend fun renameCycleSlot(id: Long, name: String) {
        db.cycleSlotDao().updateName(id, name)
    }

    suspend fun reorderCycleSlots(idsInOrder: List<Long>) {
        db.cycleSlotDao().applyOrder(idsInOrder)
    }

    fun observeSplitExercises(splitId: Long) =
        db.splitExerciseDao().observeForSplit(splitId)

    fun observeAllSplitExercises() = db.splitExerciseDao().observeAll()

    suspend fun saveSplitFromDate(splitId: Long, date: String) = db.withTransaction {
        val sets = db.workoutSetDao().getForDate(date)
        val orderedIds = sets.sortedBy { it.setNumber }
            .map { it.exerciseId }
            .distinct()
        db.splitExerciseDao().replaceForSplit(splitId, orderedIds)
    }

    suspend fun clearSplitExercises(splitId: Long) {
        db.splitExerciseDao().deleteForSplit(splitId)
    }

    suspend fun getCycle(): Cycle = db.cycleDao().getCycle() ?: Cycle()

    suspend fun saveCycle(isActive: Boolean, numTypes: Int) {
        val current = getCycle()
        db.cycleDao().upsert(
            current.copy(
                isActive = isActive,
                numTypes = numTypes.coerceAtLeast(1),
            )
        )
    }

    suspend fun setNextSessionType(slotType: Int) {
        val current = getCycle()
        db.cycleDao().upsert(current.copy(nextSessionType = slotType))
    }

    suspend fun assignCycleSlot(date: String, slotId: Long, alternativeFor: String? = null): WorkoutDay = db.withTransaction {
        val occurrence = (db.workoutDayDao().getMaxOccurrenceForSlotBefore(date, slotId) ?: 0) + 1
        val slotType = getCycleSlots()
            .indexOfFirst { it.id == slotId }
            .takeIf { it >= 0 }
        val day = WorkoutDay(
            date = date,
            cycleSlotType = slotType,
            cycleSlotId = slotId,
            cycleSlotOccurrence = occurrence,
            alternativeForDate = alternativeFor
        )
        db.workoutDayDao().upsert(day)
        day
    }

    suspend fun getPreviousOccurrenceDayForSlot(date: String, slotId: Long, occurrence: Int): WorkoutDay? {
        if (occurrence <= 0) return null
        return db.workoutDayDao().getPreviousOccurrenceForSlot(slotId, occurrence, date)
    }

    suspend fun getWorkoutDay(date: String): WorkoutDay? = db.workoutDayDao().getByDate(date)

    suspend fun resolveSlotType(day: WorkoutDay?): Int? {
        if (day == null) {
            return null
        }
        day.cycleSlotType?.let { return it }
        val slotId = day.cycleSlotId ?: return null
        return getCycleSlots().indexOfFirst { it.id == slotId }.takeIf { it >= 0 }
    }

    suspend fun getLatestAssignedDayBefore(date: String, slotType: Int): WorkoutDay? {
        val slotId = getCycleSlots().getOrNull(slotType)?.id
        if (slotId != null) {
            db.workoutDayDao().getLatestForSlotIdBefore(date, slotId)?.let { return it }
        }
        return db.workoutDayDao().getLatestForSlotTypeBefore(date, slotType)
    }

    private suspend fun syncCycleSlotCount() {
        val slots = getCycleSlots()
        val maxIndex = (slots.size - 1).coerceAtLeast(0)
        val current = getCycle()
        db.cycleDao().upsert(
            current.copy(
                numTypes = slots.size.coerceAtLeast(1),
                nextSessionType = current.nextSessionType?.coerceIn(0, maxIndex),
            )
        )
    }

    fun observeArchivedCycles(): Flow<List<ArchivedCycle>> =
        db.archivedCycleDao().observeAll()

    suspend fun getArchivedCycle(id: Long): ArchivedCycle? =
        db.archivedCycleDao().getById(id)

    suspend fun deleteArchivedCycle(id: Long) {
        db.archivedCycleDao().deleteById(id)
    }

    suspend fun countOverlappingArchives(start: String, end: String): Int =
        db.archivedCycleDao().countOverlapping(start, end)

    suspend fun getLatestWorkoutDate(): String? =
        db.workoutSetDao().getLatestCompletedWorkoutDate()

    suspend fun getActiveCycleStart(): String {
        val current = getCycle()
        return current.startDate
            ?: db.workoutSetDao().getEarliestCompletedWorkoutDate()
            ?: LocalDate.now().toString()
    }

    suspend fun activeCycleProgress(): CycleProgressCore =
        buildActiveCycleProgress().core

    suspend fun activeCycleComparison(window: ComparisonWindow): CycleComparison {
        val coreResult = buildActiveCycleProgress()
        return CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = loadPriorSets(coreResult.core.lifts.map { it.exerciseId }, coreResult.core.startDate),
            window = window,
            userBodyweightLbs = getUserBodyweightLbs(),
        )
    }

    suspend fun archivedCycleProgress(archiveId: Long): CycleProgressCore =
        buildArchivedCycleProgress(archiveId).core

    suspend fun archivedCycleComparison(archiveId: Long, window: ComparisonWindow): CycleComparison {
        val coreResult = buildArchivedCycleProgress(archiveId)
        return CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = loadPriorSets(coreResult.core.lifts.map { it.exerciseId }, coreResult.core.startDate),
            window = window,
            userBodyweightLbs = getUserBodyweightLbs(),
        )
    }

    suspend fun archiveCurrentCycle(name: String, startDate: String, endDate: String): Long {
        val parsedStart = LocalDate.parse(startDate)
        val parsedEnd = LocalDate.parse(endDate)
        require(!parsedStart.isAfter(parsedEnd)) {
            "Archive startDate must be on or before endDate"
        }
        val userBodyweightLbs = getUserBodyweightLbs()

        return db.withTransaction {
            val sets = db.workoutSetDao().getCompletedSetsInRange(startDate, endDate)
            val slots = db.cycleSlotDao().getAll()
            val splitExercises = db.splitExerciseDao().getAll()
            val workoutDays = db.workoutDayDao().getAll()
            val exerciseIds = sets.map { it.exerciseId }.distinct()
            val exerciseNames = if (exerciseIds.isEmpty()) {
                emptyMap()
            } else {
                db.exerciseDao().getByIds(exerciseIds)
                    .associate { exercise ->
                        exercise.id to ExerciseMeta(
                            name = exercise.name,
                            isBodyweight = exercise.isBodyweight,
                        )
                    }
            }
            val snapshot = CycleSnapshotBuilder.build(
                startDate = startDate,
                endDate = endDate,
                slots = slots,
                splitExercises = splitExercises,
                workoutDays = workoutDays,
                sets = sets,
                exerciseNames = exerciseNames,
                userBodyweightLbs = userBodyweightLbs,
            )
            val archiveId = db.archivedCycleDao().insert(
                ArchivedCycle(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    splitCount = CycleSnapshotBuilder.splitCount(snapshot),
                    totalVolumeLbs = snapshot.totals.totalVolumeLbs,
                    totalSessions = snapshot.totals.sessions,
                    archivedAt = System.currentTimeMillis(),
                    schemaVersion = snapshot.schemaVersion,
                    snapshotJson = archiveJson.encodeToString(snapshot),
                )
            )
            db.splitExerciseDao().deleteAll()
            db.cycleSlotDao().deleteAll()
            db.cycleDao().upsert(
                getCycle().copy(
                    isActive = false,
                    numTypes = 0,
                    nextSessionType = null,
                    startDate = parsedEnd.plusDays(1).toString(),
                    name = null,
                )
            )
            archiveId
        }
    }

    private suspend fun buildActiveCycleProgress(): CycleProgressCalculator.CoreResult {
        val startDate = getActiveCycleStart()
        val latestDate = db.workoutSetDao().getLatestCompletedWorkoutDate()
        val endDate = latestDate?.takeIf { it >= startDate } ?: startDate
        val sets = db.workoutSetDao().getCompletedSetsInRange(startDate, endDate)
        val slots = db.cycleSlotDao().getAll()
        val splitExercises = db.splitExerciseDao().getAll()
        val workoutDays = db.workoutDayDao().getAll()
        val userBodyweightLbs = getUserBodyweightLbs()
        val snapshot = CycleSnapshotBuilder.build(
            startDate = startDate,
            endDate = endDate,
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = workoutDays,
            sets = sets,
            exerciseNames = exerciseMetaFor(sets.map { it.exerciseId }),
            userBodyweightLbs = userBodyweightLbs,
        )
        return CycleProgressCalculator.buildCore(
            snapshot = snapshot,
            sets = sets.map { it.toProgressInput() },
            userBodyweightLbs = userBodyweightLbs,
            realSlotCount = slots.size,
        )
    }

    private suspend fun buildArchivedCycleProgress(archiveId: Long): CycleProgressCalculator.CoreResult {
        val archive = requireNotNull(db.archivedCycleDao().getById(archiveId)) {
            "Archived cycle not found: $archiveId"
        }
        val userBodyweightLbs = getUserBodyweightLbs()
        val snapshot = buildMutableArchivedSnapshot(archive, userBodyweightLbs)
        val sets = db.workoutSetDao().getCompletedSetsInRange(archive.startDate, archive.endDate)
        return CycleProgressCalculator.buildCore(
            snapshot = snapshot,
            sets = sets.map { it.toProgressInput() },
            userBodyweightLbs = userBodyweightLbs,
            realSlotCount = snapshot.splits.count { it.bucketKind == SplitBucketKind.Real },
        )
    }

    suspend fun archiveSummary(archive: ArchivedCycle): ArchiveSummary {
        val snapshot = buildMutableArchivedSnapshot(archive, getUserBodyweightLbs())
        return ArchiveSummary(
            splitCount = CycleSnapshotBuilder.splitCount(snapshot),
            totalSessions = snapshot.totals.sessions,
            totalVolumeLbs = snapshot.totals.totalVolumeLbs,
        )
    }

    private suspend fun buildMutableArchivedSnapshot(
        archive: ArchivedCycle,
        userBodyweightLbs: Int?,
    ): CycleSnapshot {
        val archivedSnapshot = archiveJson.decodeFromString<CycleSnapshot>(archive.snapshotJson)
        val slots = archivedSnapshot.splits
            .filter { it.bucketKind == SplitBucketKind.Real }
            .sortedWith(compareBy({ it.orderIndex }, { it.slotId }))
            .map { split ->
                CycleSlot(id = split.slotId, name = split.name, orderIndex = split.orderIndex)
            }
        val splitExercises = archivedSnapshot.splits
            .filter { it.bucketKind == SplitBucketKind.Real }
            .flatMap { split ->
                split.exercises.mapIndexed { index, exercise ->
                    SplitExercise(
                        splitId = split.slotId,
                        exerciseId = exercise.exerciseId,
                        orderIndex = index,
                    )
                }
            }
        val sets = db.workoutSetDao().getCompletedSetsInRange(archive.startDate, archive.endDate)
        val snapshotMeta = archivedSnapshot.splits
            .flatMap { it.exercises }
            .associate { exercise ->
                exercise.exerciseId to ExerciseMeta(
                    name = exercise.name,
                    isBodyweight = exercise.isBodyweight,
                )
            }
        val liveMeta = exerciseMetaFor((sets.map { it.exerciseId } + snapshotMeta.keys).distinct())
        return CycleSnapshotBuilder.build(
            startDate = archive.startDate,
            endDate = archive.endDate,
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = db.workoutDayDao().getAll(),
            sets = sets,
            exerciseNames = snapshotMeta + liveMeta,
            userBodyweightLbs = userBodyweightLbs,
        )
    }

    private suspend fun loadPriorSets(
        exerciseIds: List<Long>,
        beforeDate: String,
    ): Map<Long, List<CycleProgressCalculator.SetInput>> =
        exerciseIds.distinct().associateWith { exerciseId ->
            db.workoutSetDao()
                .getHistoryBeforeDate(exerciseId, beforeDate)
                .map { it.toProgressInput() }
        }

    private suspend fun exerciseMetaFor(exerciseIds: List<Long>): Map<Long, ExerciseMeta> {
        val ids = exerciseIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return db.exerciseDao().getByIds(ids).associate { exercise ->
            exercise.id to ExerciseMeta(
                name = exercise.name,
                isBodyweight = exercise.isBodyweight,
            )
        }
    }

    private fun WorkoutSet.toProgressInput(): CycleProgressCalculator.SetInput =
        CycleProgressCalculator.SetInput(
            id = id,
            exerciseId = exerciseId,
            date = date,
            setNumber = setNumber,
            weightLbs = weightLbs,
            reps = reps,
            isBodyweight = isBodyweight,
            completed = completed,
        )
}

data class ArchiveSummary(
    val splitCount: Int,
    val totalSessions: Int,
    val totalVolumeLbs: Long,
)
