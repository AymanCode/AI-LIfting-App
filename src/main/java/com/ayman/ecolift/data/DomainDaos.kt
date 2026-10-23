package com.ayman.ecolift.data

import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.data.room.toRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map as mapFlow

class DomainArchivedCycleDao(private val dao: ArchivedCycleDao) {
    fun observeAll(): Flow<List<ArchivedCycle>> = dao.observeAll().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<ArchivedCycle> = dao.getAll().map { it.toDomain() }
    suspend fun getById(id: Long): ArchivedCycle? = dao.getById(id)?.toDomain()
    suspend fun countOverlapping(start: String, end: String): Int = dao.countOverlapping(start, end)
    suspend fun insert(cycle: ArchivedCycle): Long = dao.insert(cycle.toRoom())
    suspend fun insertAll(cycles: List<ArchivedCycle>) = dao.insertAll(cycles.map { it.toRoom() })
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}

fun ArchivedCycleDao.domain() = DomainArchivedCycleDao(this)

class DomainCardioSessionDao(private val dao: CardioSessionDao) {
    suspend fun insert(session: CardioSession): Long = dao.insert(session.toRoom())
    suspend fun insertAll(sessions: List<CardioSession>) = dao.insertAll(sessions.map { it.toRoom() })
    suspend fun upsert(session: CardioSession): Long = dao.upsert(session.toRoom())
    suspend fun update(session: CardioSession) = dao.update(session.toRoom())
    fun observeAll(): Flow<List<CardioSession>> = dao.observeAll().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<CardioSession> = dao.getAll().map { it.toDomain() }
    suspend fun getById(id: Long): CardioSession? = dao.getById(id)?.toDomain()
    suspend fun getByHealthConnectUid(hcUid: String): CardioSession? = dao.getByHealthConnectUid(hcUid)?.toDomain()
    fun observeForDate(date: String): Flow<List<CardioSession>> = dao.observeForDate(date).mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getForDate(date: String): List<CardioSession> = dao.getForDate(date).map { it.toDomain() }
    suspend fun getInRange(startDate: String, endDate: String): List<CardioSession> = dao.getInRange(startDate, endDate).map { it.toDomain() }
    fun observeCaloriesForDate(date: String): Flow<Int> = dao.observeCaloriesForDate(date)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}

fun CardioSessionDao.domain() = DomainCardioSessionDao(this)

class DomainCycleDao(private val dao: CycleDao) {
    fun observeCycle(): Flow<Cycle?> = dao.observeCycle().mapFlow { row -> row?.toDomain() }
    suspend fun getCycle(): Cycle? = dao.getCycle()?.toDomain()
    suspend fun upsert(cycle: Cycle) = dao.upsert(cycle.toRoom())
}

fun CycleDao.domain() = DomainCycleDao(this)

class DomainCycleSlotDao(private val dao: CycleSlotDao) {
    suspend fun insertAll(slots: List<CycleSlot>) = dao.insertAll(slots.map { it.toRoom() })
    fun observeAll(): Flow<List<CycleSlot>> = dao.observeAll().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<CycleSlot> = dao.getAll().map { it.toDomain() }
    suspend fun upsert(slot: CycleSlot): Long = dao.upsert(slot.toRoom())
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getById(id: Long): CycleSlot? = dao.getById(id)?.toDomain()
    suspend fun updateName(id: Long, name: String) = dao.updateName(id, name)
    suspend fun updateOrder(id: Long, orderIndex: Int) = dao.updateOrder(id, orderIndex)
    suspend fun getMaxOrderIndex(): Int = dao.getMaxOrderIndex()
    suspend fun applyOrder(idsInOrder: List<Long>) = dao.applyOrder(idsInOrder)
}

fun CycleSlotDao.domain() = DomainCycleSlotDao(this)

class DomainExerciseDao(private val dao: ExerciseDao) {
    suspend fun insert(exercise: Exercise): Long = dao.insert(exercise.toRoom())
    suspend fun insertAll(exercises: List<Exercise>) = dao.insertAll(exercises.map { it.toRoom() })
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<Exercise> = dao.getAll().map { it.toDomain() }
    suspend fun getByExactName(name: String): Exercise? = dao.getByExactName(name)?.toDomain()
    suspend fun getById(id: Long): Exercise? = dao.getById(id)?.toDomain()
    suspend fun getByIds(ids: List<Long>): List<Exercise> = dao.getByIds(ids).map { it.toDomain() }
    suspend fun searchByName(query: String, limit: Int): List<Exercise> = dao.searchByName(query, limit).map { it.toDomain() }
    suspend fun searchByFrequency(query: String, limit: Int): List<Exercise> = dao.searchByFrequency(query, limit).map { it.toDomain() }
    suspend fun updateName(id: Long, newName: String) = dao.updateName(id, newName)
    suspend fun updateMuscleGroups(id: Long, muscleGroups: String) = dao.updateMuscleGroups(id, muscleGroups)
    suspend fun upsert(exercise: Exercise): Long = dao.upsert(exercise.toRoom())
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun deleteExerciseWithLogs(exerciseId: Long) = dao.deleteExerciseWithLogs(exerciseId)
    suspend fun deleteAllSets() = dao.deleteAllSets()
    suspend fun deleteAllExercises() = dao.deleteAllExercises()
}

fun ExerciseDao.domain() = DomainExerciseDao(this)

class DomainPendingReviewDao(private val dao: PendingReviewDao) {
    suspend fun insert(review: PendingReview): Long = dao.insert(review.toRoom())
    suspend fun insertAll(reviews: List<PendingReview>) = dao.insertAll(reviews.map { it.toRoom() })
    suspend fun getAll(): List<PendingReview> = dao.getAll().map { it.toDomain() }
    fun observeUnresolved(): Flow<List<PendingReview>> = dao.observeUnresolved().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getUnresolved(): List<PendingReview> = dao.getUnresolved().map { it.toDomain() }
    suspend fun markResolved(id: Long) = dao.markResolved(id)
}

fun PendingReviewDao.domain() = DomainPendingReviewDao(this)

class DomainSplitExerciseDao(private val dao: SplitExerciseDao) {
    fun observeForSplit(splitId: Long): Flow<List<SplitExercise>> = dao.observeForSplit(splitId).mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getForSplit(splitId: Long): List<SplitExercise> = dao.getForSplit(splitId).map { it.toDomain() }
    fun observeAll(): Flow<List<SplitExercise>> = dao.observeAll().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<SplitExercise> = dao.getAll().map { it.toDomain() }
    suspend fun insertAll(rows: List<SplitExercise>) = dao.insertAll(rows.map { it.toRoom() })
    suspend fun deleteForSplit(splitId: Long) = dao.deleteForSplit(splitId)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun replaceForSplit(splitId: Long, exerciseIds: List<Long>) = dao.replaceForSplit(splitId, exerciseIds)
}

fun SplitExerciseDao.domain() = DomainSplitExerciseDao(this)

class DomainUserSettingsDao(private val dao: UserSettingsDao) {
    suspend fun get(): UserSettings? = dao.get()?.toDomain()
    fun observe(): Flow<UserSettings?> = dao.observe().mapFlow { row -> row?.toDomain() }
    suspend fun upsert(settings: UserSettings) = dao.upsert(settings.toRoom())
    suspend fun deleteAll() = dao.deleteAll()
}

fun UserSettingsDao.domain() = DomainUserSettingsDao(this)

class DomainWorkoutDayDao(private val dao: WorkoutDayDao) {
    suspend fun insertAll(workoutDays: List<WorkoutDay>) = dao.insertAll(workoutDays.map { it.toRoom() })
    suspend fun upsert(workoutDay: WorkoutDay) = dao.upsert(workoutDay.toRoom())
    fun observeByDate(date: String): Flow<WorkoutDay?> = dao.observeByDate(date).mapFlow { row -> row?.toDomain() }
    suspend fun getByDate(date: String): WorkoutDay? = dao.getByDate(date)?.toDomain()
    fun observeAll(): Flow<List<WorkoutDay>> = dao.observeAll().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<WorkoutDay> = dao.getAll().map { it.toDomain() }
    suspend fun getLatestForSlotIdBefore(beforeDate: String, slotId: Long): WorkoutDay? = dao.getLatestForSlotIdBefore(beforeDate, slotId)?.toDomain()
    suspend fun getLatestForSlotTypeBefore(beforeDate: String, slotType: Int): WorkoutDay? = dao.getLatestForSlotTypeBefore(beforeDate, slotType)?.toDomain()
    suspend fun getMaxOccurrenceForSlotBefore(beforeDate: String, slotId: Long): Int? = dao.getMaxOccurrenceForSlotBefore(beforeDate, slotId)
    suspend fun getPreviousOccurrenceForSlot(slotId: Long, occurrence: Int, beforeDate: String): WorkoutDay? = dao.getPreviousOccurrenceForSlot(slotId, occurrence, beforeDate)?.toDomain()
    suspend fun deleteByDate(date: String) = dao.deleteByDate(date)
    suspend fun getMaxOccurrenceBefore(beforeDate: String, slotType: Int): Int? = dao.getMaxOccurrenceBefore(beforeDate, slotType)
    suspend fun getPreviousOccurrence(slotType: Int, occurrence: Int, beforeDate: String): WorkoutDay? = dao.getPreviousOccurrence(slotType, occurrence, beforeDate)?.toDomain()
}

fun WorkoutDayDao.domain() = DomainWorkoutDayDao(this)

class DomainWorkoutSetDao(private val dao: WorkoutSetDao) {
    suspend fun insert(set: WorkoutSet): Long = dao.insert(set.toRoom())
    suspend fun insertAll(sets: List<WorkoutSet>) = dao.insertAll(sets.map { it.toRoom() })
    suspend fun upsert(set: WorkoutSet): Long = dao.upsert(set.toRoom())
    suspend fun update(set: WorkoutSet) = dao.update(set.toRoom())
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    fun observeAllDistinctDates(): Flow<List<String>> = dao.observeAllDistinctDates()
    fun observeCompletedDistinctDates(): Flow<List<String>> = dao.observeCompletedDistinctDates()
    fun observeCompletedSets(): Flow<List<WorkoutSet>> = dao.observeCompletedSets().mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<WorkoutSet> = dao.getAll().map { it.toDomain() }
    fun observeForDate(date: String): Flow<List<WorkoutSet>> = dao.observeForDate(date).mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getForDate(date: String): List<WorkoutSet> = dao.getForDate(date).map { it.toDomain() }
    suspend fun getForDateAndExercise(date: String, exerciseId: Long): List<WorkoutSet> = dao.getForDateAndExercise(date, exerciseId).map { it.toDomain() }
    suspend fun getLastForDateAndExercise(date: String, exerciseId: Long): WorkoutSet? = dao.getLastForDateAndExercise(date, exerciseId)?.toDomain()
    suspend fun getMostRecentBeforeDate(exerciseId: Long, beforeDate: String): WorkoutSet? = dao.getMostRecentBeforeDate(exerciseId, beforeDate)?.toDomain()
    suspend fun getHistoryBeforeDate(exerciseId: Long, beforeDate: String): List<WorkoutSet> = dao.getHistoryBeforeDate(exerciseId, beforeDate).map { it.toDomain() }
    fun observeExerciseProgressSummaries(): Flow<List<ExerciseProgressSummary>> = dao.observeExerciseProgressSummaries()
    suspend fun getVolumeHistory(exerciseId: Long, limit: Int): List<DateVolume> = dao.getVolumeHistory(exerciseId, limit)
    suspend fun getVolumesSince(sinceDate: String): List<ExerciseVolume> = dao.getVolumesSince(sinceDate)
    suspend fun getById(id: Long): WorkoutSet? = dao.getById(id)?.toDomain()
    suspend fun updateDate(oldDate: String, newDate: String) = dao.updateDate(oldDate, newDate)
    suspend fun getLastSessionDate(beforeDate: String): String? = dao.getLastSessionDate(beforeDate)
    suspend fun getLatestWorkoutDate(): String? = dao.getLatestWorkoutDate()
    suspend fun getEarliestWorkoutDate(): String? = dao.getEarliestWorkoutDate()
    suspend fun getLatestCompletedWorkoutDate(): String? = dao.getLatestCompletedWorkoutDate()
    suspend fun getEarliestCompletedWorkoutDate(): String? = dao.getEarliestCompletedWorkoutDate()
    suspend fun getSetsInRange(startDate: String, endDate: String): List<WorkoutSet> = dao.getSetsInRange(startDate, endDate).map { it.toDomain() }
    suspend fun getCompletedSetsInRange(startDate: String, endDate: String): List<WorkoutSet> = dao.getCompletedSetsInRange(startDate, endDate).map { it.toDomain() }
    suspend fun getMostRecentExerciseName(): String? = dao.getMostRecentExerciseName()
    suspend fun getSetsByDate(date: String): List<WorkoutSet> = dao.getSetsByDate(date).map { it.toDomain() }
    suspend fun getForDates(dates: List<String>): List<WorkoutSet> = dao.getForDates(dates).map { it.toDomain() }
    suspend fun getCompletedForDates(dates: List<String>): List<WorkoutSet> = dao.getCompletedForDates(dates).map { it.toDomain() }
    suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> = dao.getRecentHistoryForExercise(exerciseId, beforeDate).map { it.toDomain() }
    suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int? = dao.getMaxWeightBeforeDate(exerciseId, beforeDate)
    suspend fun getSetsSince(exerciseId: Long, sinceDate: String): List<WorkoutSet> = dao.getSetsSince(exerciseId, sinceDate).map { it.toDomain() }
    fun observeSetsSince(exerciseId: Long, sinceDate: String): Flow<List<WorkoutSet>> = dao.observeSetsSince(exerciseId, sinceDate).mapFlow { rows -> rows.map { it.toDomain() } }
    suspend fun getAllTimeMaxWeights(): List<ExerciseMaxWeight> = dao.getAllTimeMaxWeights()
    suspend fun getMaxWeightsForExercises(exerciseIds: List<Long>): List<ExerciseMaxWeight> = dao.getMaxWeightsForExercises(exerciseIds)
}

fun WorkoutSetDao.domain() = DomainWorkoutSetDao(this)
