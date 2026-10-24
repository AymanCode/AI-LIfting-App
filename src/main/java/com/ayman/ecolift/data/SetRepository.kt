package com.ayman.ecolift.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class SetRepository(private val db: AppDatabase) {
    suspend fun getAllSets(): List<WorkoutSet> = db.workoutSetDao().domain().getAll()

    fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> = db.workoutSetDao().domain().observeForDate(date)

    suspend fun getSetsForDate(date: String): List<WorkoutSet> = db.workoutSetDao().domain().getForDate(date)

    /**
     * Reading the current sets and inserting the next one share a transaction:
     * the set number is derived from what is already stored, so two concurrent
     * adds must not interleave between the read and the insert.
     */
    suspend fun addSet(date: String, exerciseId: Long): WorkoutSet = db.withTransaction {
        val currentSets = db.workoutSetDao().domain().getForDateAndExercise(date, exerciseId)
        val template = currentSets.lastOrNull()
            ?: db.workoutSetDao().domain().getMostRecentBeforeDate(exerciseId, date)
        val exercise = if (template == null) db.exerciseDao().domain().getById(exerciseId) else null
        val isBodyweight = template?.isBodyweight ?: exercise?.isBodyweight ?: false

        val nextSetNumber = nextSetNumber(currentSets)
        val newSet = WorkoutSet(
            exerciseId = exerciseId,
            date = date,
            setNumber = nextSetNumber,
            weightLbs = if (isBodyweight) normalizedBodyweightLoad(template?.weightLbs) else template?.weightLbs,
            reps = template?.reps,
            isBodyweight = isBodyweight,
            completed = false,
        )
        val insertedId = db.workoutSetDao().domain().insert(newSet)
        newSet.copy(id = insertedId)
    }

    suspend fun addExerciseSession(date: String, exerciseId: Long): List<WorkoutSet> = db.withTransaction {
        val currentSets = db.workoutSetDao().domain().getForDateAndExercise(date, exerciseId)
        if (currentSets.isNotEmpty()) {
            return@withTransaction listOf(addSet(date, exerciseId))
        }

        val sessionCopies = buildLastSessionSetCopies(
            date = date,
            exerciseId = exerciseId,
            historyBeforeDate = db.workoutSetDao().domain().getHistoryBeforeDate(exerciseId, date),
        )
        if (sessionCopies.isEmpty()) {
            return@withTransaction listOf(addSet(date, exerciseId))
        }

        sessionCopies.map { set ->
            val insertedId = db.workoutSetDao().domain().insert(set)
            set.copy(id = insertedId)
        }
    }

    suspend fun addSetsForExercises(date: String, exerciseIds: List<Long>) {
        exerciseIds.forEach { exId ->
            addExerciseSession(date, exId)
        }
    }

    suspend fun cloneDay(templateDate: String, targetDate: String) {
        val existing = db.workoutSetDao().domain().getForDate(targetDate)
        val existingExerciseIds = existing.map { it.exerciseId }.toSet()

        db.workoutSetDao().domain().getForDate(templateDate).forEach { set ->
            // Only add exercises that aren't already logged for today
            if (set.exerciseId !in existingExerciseIds) {
                db.workoutSetDao().domain().insert(
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
        db.workoutSetDao().domain().update(set)
    }

    suspend fun upsertSet(set: WorkoutSet): Long {
        return db.workoutSetDao().domain().upsert(set)
    }

    suspend fun getLastForDateAndExercise(date: String, exerciseId: Long) = db.workoutSetDao().domain().getLastForDateAndExercise(date, exerciseId)

    suspend fun getMostRecentBeforeDate(exerciseId: Long, date: String) = db.workoutSetDao().domain().getMostRecentBeforeDate(exerciseId, date)

    suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String) = db.workoutSetDao().domain().getRecentHistoryForExercise(exerciseId, beforeDate)

    suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String) = db.workoutSetDao().domain().getMaxWeightBeforeDate(exerciseId, beforeDate)

    suspend fun getLastSessionDate(beforeDate: String) = db.workoutSetDao().domain().getLastSessionDate(beforeDate)

    suspend fun getSetsByDate(date: String) = db.workoutSetDao().domain().getSetsByDate(date)

    suspend fun getSetsForDates(dates: List<String>) =
        if (dates.isEmpty()) emptyList() else db.workoutSetDao().domain().getForDates(dates)

    suspend fun getCompletedSetsForDates(dates: List<String>) =
        if (dates.isEmpty()) emptyList() else db.workoutSetDao().domain().getCompletedForDates(dates)

    suspend fun getById(id: Long): WorkoutSet? = db.workoutSetDao().domain().getById(id)

    fun observeExerciseProgressSummaries(): Flow<List<ExerciseProgressSummary>> = db.workoutSetDao().domain().observeExerciseProgressSummaries()

    fun observeSetsSince(exerciseId: Long, sinceDate: String): Flow<List<WorkoutSet>> =
        db.workoutSetDao().domain().observeSetsSince(exerciseId, sinceDate)

    suspend fun getVolumeHistory(exerciseId: Long, limit: Int) = db.workoutSetDao().domain().getVolumeHistory(exerciseId, limit)

    suspend fun getSetsSince(exerciseId: Long, sinceDate: String) = db.workoutSetDao().domain().getSetsSince(exerciseId, sinceDate)

    suspend fun getVolumesSince(sinceDate: String) = db.workoutSetDao().domain().getVolumesSince(sinceDate)

    suspend fun getAllTimeMaxWeights() = db.workoutSetDao().domain().getAllTimeMaxWeights()

    suspend fun getMaxWeightsForExercises(exerciseIds: List<Long>) =
        if (exerciseIds.isEmpty()) emptyList() else db.workoutSetDao().domain().getMaxWeightsForExercises(exerciseIds)

    suspend fun deleteSet(id: Long) {
        db.workoutSetDao().domain().deleteById(id)
    }

    suspend fun deleteSetAndCompact(set: WorkoutSet) {
        db.withTransaction {
            db.workoutSetDao().domain().deleteById(set.id)
            val survivors = db.workoutSetDao().domain().getForDateAndExercise(set.date, set.exerciseId)
            renumberSequentially(survivors).forEach { renumbered ->
                val original = survivors.find { it.id == renumbered.id }
                if (original != null && original.setNumber != renumbered.setNumber) {
                    db.workoutSetDao().domain().update(renumbered)
                }
            }
        }
    }
}

internal fun nextSetNumber(currentSets: List<WorkoutSet>): Int =
    (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1

internal fun renumberSequentially(sets: List<WorkoutSet>): List<WorkoutSet> =
    sets.sortedWith(compareBy({ it.setNumber }, { it.id }))
        .mapIndexed { index, set -> set.copy(setNumber = index + 1) }

internal fun buildLastSessionSetCopies(
    date: String,
    exerciseId: Long,
    historyBeforeDate: List<WorkoutSet>,
): List<WorkoutSet> {
    // The "reference" for a re-added exercise is the last session the user actually checked off.
    // Sessions logged but left unchecked (parked, abandoned, or later un-checked) must not seed the
    // new sets, keeping this in sync with the hint label, which is already completed-only.
    val completedHistory = historyBeforeDate
        .filter { it.exerciseId == exerciseId && it.date < date && it.completed }
    val lastDate = completedHistory
        .maxOfOrNull(WorkoutSet::date)
        ?: return emptyList()

    return completedHistory
        .filter { it.date == lastDate }
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
