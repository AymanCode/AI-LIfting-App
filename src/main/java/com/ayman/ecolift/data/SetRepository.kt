package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow

class SetRepository(private val db: AppDatabase) {
    val allSets: Flow<List<WorkoutSet>> = db.workoutSetDao().observeAll()

    fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> = db.workoutSetDao().observeForDate(date)

    suspend fun getSetsForDate(date: String): List<WorkoutSet> = db.workoutSetDao().getForDate(date)

    suspend fun addSet(date: String, exerciseId: Long): WorkoutSet {
        val currentSets = db.workoutSetDao().getForDateAndExercise(date, exerciseId)
        val template = currentSets.lastOrNull()
            ?: db.workoutSetDao().getMostRecentBeforeDate(exerciseId, date)
        
        val nextSetNumber = currentSets.size + 1
        val newSet = WorkoutSet(
            exerciseId = exerciseId,
            date = date,
            setNumber = nextSetNumber,
            weightLbs = template?.weightLbs,
            reps = template?.reps,
            isBodyweight = template?.isBodyweight ?: false,
            completed = false,
        )
        val insertedId = db.workoutSetDao().insert(newSet)
        return newSet.copy(id = insertedId)
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

    suspend fun getById(id: Long): WorkoutSet? = db.workoutSetDao().getById(id)

    suspend fun deleteSet(id: Long) {
        db.workoutSetDao().deleteById(id)
    }
}
