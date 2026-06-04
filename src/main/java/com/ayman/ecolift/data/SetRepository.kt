package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow

class SetRepository(private val db: AppDatabase) {
    suspend fun getAllSets(): List<WorkoutSet> = db.workoutSetDao().getAll()

    fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> = db.workoutSetDao().observeForDate(date)

    suspend fun getSetsForDate(date: String): List<WorkoutSet> = db.workoutSetDao().getForDate(date)

    suspend fun addSet(date: String, exerciseId: Long): WorkoutSet {
        val currentSets = db.workoutSetDao().getForDateAndExercise(date, exerciseId)
        val template = currentSets.lastOrNull()
            ?: db.workoutSetDao().getMostRecentBeforeDate(exerciseId, date)
        val exercise = if (template == null) db.exerciseDao().getById(exerciseId) else null
        val isBodyweight = template?.isBodyweight ?: exercise?.isBodyweight ?: false
        
        val nextSetNumber = currentSets.size + 1
        val newSet = WorkoutSet(
            exerciseId = exerciseId,
            date = date,
            setNumber = nextSetNumber,
            weightLbs = if (isBodyweight) normalizedBodyweightLoad(template?.weightLbs) else template?.weightLbs,
            reps = template?.reps,
            isBodyweight = isBodyweight,
            completed = false,
        )
        val insertedId = db.workoutSetDao().insert(newSet)
        return newSet.copy(id = insertedId)
    }

    suspend fun addExerciseSession(date: String, exerciseId: Long): List<WorkoutSet> {
        val currentSets = db.workoutSetDao().getForDateAndExercise(date, exerciseId)
        if (currentSets.isNotEmpty()) {
            return listOf(addSet(date, exerciseId))
        }

        val sessionCopies = buildLastSessionSetCopies(
            date = date,
            exerciseId = exerciseId,
            historyBeforeDate = db.workoutSetDao().getHistoryBeforeDate(exerciseId, date),
        )
        if (sessionCopies.isEmpty()) {
            return listOf(addSet(date, exerciseId))
        }

        return sessionCopies.map { set ->
            val insertedId = db.workoutSetDao().insert(set)
            set.copy(id = insertedId)
        }
    }

    suspend fun addSetsForExercises(date: String, exerciseIds: List<Long>) {
        exerciseIds.forEach { exId ->
            addExerciseSession(date, exId)
        }
    }

    suspend fun cloneDay(templateDate: String, targetDate: String) {
        val existing = db.workoutSetDao().getForDate(targetDate)
        val existingExerciseIds = existing.map { it.exerciseId }.toSet()
        
        db.workoutSetDao().getForDate(templateDate).forEach { set ->
            // Only add exercises that aren't already logged for today
            if (set.exerciseId !in existingExerciseIds) {
                db.workoutSetDao().insert(
                    set.copy(
                        id = 0,
                        date = targetDate,
                        completed = false,
                    )
                )
            }
        }
    }

    suspend fun updateSet(set: WorkoutSet) {
        db.workoutSetDao().update(set)
    }

    suspend fun upsertSet(set: WorkoutSet): Long {
        return db.workoutSetDao().upsert(set)
    }

    suspend fun getLastForDateAndExercise(date: String, exerciseId: Long) = db.workoutSetDao().getLastForDateAndExercise(date, exerciseId)

    suspend fun getMostRecentBeforeDate(exerciseId: Long, date: String) = db.workoutSetDao().getMostRecentBeforeDate(exerciseId, date)

    suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String) = db.workoutSetDao().getRecentHistoryForExercise(exerciseId, beforeDate)

    suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String) = db.workoutSetDao().getMaxWeightBeforeDate(exerciseId, beforeDate)

    suspend fun getLastSessionDate(beforeDate: String) = db.workoutSetDao().getLastSessionDate(beforeDate)

    suspend fun getSetsByDate(date: String) = db.workoutSetDao().getSetsByDate(date)

    suspend fun getSetsForDates(dates: List<String>) =
        if (dates.isEmpty()) emptyList() else db.workoutSetDao().getForDates(dates)

    suspend fun getCompletedSetsForDates(dates: List<String>) =
        if (dates.isEmpty()) emptyList() else db.workoutSetDao().getCompletedForDates(dates)

    suspend fun getById(id: Long): WorkoutSet? = db.workoutSetDao().getById(id)

    fun observeExerciseProgressSummaries(): Flow<List<ExerciseProgressSummary>> = db.workoutSetDao().observeExerciseProgressSummaries()

    fun observeSetsSince(exerciseId: Long, sinceDate: String): Flow<List<WorkoutSet>> =
        db.workoutSetDao().observeSetsSince(exerciseId, sinceDate)

    suspend fun getVolumeHistory(exerciseId: Long, limit: Int) = db.workoutSetDao().getVolumeHistory(exerciseId, limit)

    suspend fun getSetsSince(exerciseId: Long, sinceDate: String) = db.workoutSetDao().getSetsSince(exerciseId, sinceDate)

    suspend fun getVolumesSince(sinceDate: String) = db.workoutSetDao().getVolumesSince(sinceDate)

    suspend fun getAllTimeMaxWeights() = db.workoutSetDao().getAllTimeMaxWeights()

    suspend fun getMaxWeightsForExercises(exerciseIds: List<Long>) =
        if (exerciseIds.isEmpty()) emptyList() else db.workoutSetDao().getMaxWeightsForExercises(exerciseIds)

    suspend fun deleteSet(id: Long) {
        db.workoutSetDao().deleteById(id)
    }
}

internal fun buildLastSessionSetCopies(
    date: String,
    exerciseId: Long,
    historyBeforeDate: List<WorkoutSet>,
): List<WorkoutSet> {
    val lastDate = historyBeforeDate
        .filter { it.exerciseId == exerciseId && it.date < date }
        .maxOfOrNull(WorkoutSet::date)
        ?: return emptyList()

    return historyBeforeDate
        .filter { it.exerciseId == exerciseId && it.date == lastDate }
        .sortedBy(WorkoutSet::setNumber)
        .mapIndexed { index, template ->
            WorkoutSet(
                exerciseId = exerciseId,
                date = date,
                setNumber = index + 1,
                weightLbs = if (template.isBodyweight) normalizedBodyweightLoad(template.weightLbs) else template.weightLbs,
                reps = template.reps,
                isBodyweight = template.isBodyweight,
                completed = false,
                restTimeSeconds = null,
            )
        }
}
