package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow

class SetRepository(private val db: AppDatabase) {
    val allSets: Flow<List<WorkoutSet>> = db.workoutSetDao().observeAll()

    fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> = db.workoutSetDao().observeForDate(date)

    suspend fun getAllSets(): List<WorkoutSet> = db.workoutSetDao().observeAllSnapshot()

    suspend fun getSetsForDate(date: String): List<WorkoutSet> = db.workoutSetDao().getForDate(date)

    suspend fun addSet(date: String, exerciseId: Long): WorkoutSet {
        val template = db.workoutSetDao().getLastForDateAndExercise(date, exerciseId)
            ?: db.workoutSetDao().getMostRecentBeforeDate(exerciseId, date)
        val nextSetNumber = db.workoutSetDao().getForDateAndExercise(date, exerciseId).size + 1
        val newSet = WorkoutSet(
            exerciseId = exerciseId,
            date = date,
            setNumber = nextSetNumber,
            weightLbs = template?.weightLbs ?: 0,
            reps = template?.reps ?: 0,
            isBodyweight = template?.isBodyweight ?: false,
            completed = false,
        )
        val insertedId = db.workoutSetDao().insert(newSet)
        return newSet.copy(id = insertedId)
    }

    suspend fun cloneDay(templateDate: String, targetDate: String) {
        val existing = db.workoutSetDao().getForDate(targetDate)
        if (existing.isNotEmpty()) {
            return
        }
        db.workoutSetDao().getForDate(templateDate).forEach { set ->
            db.workoutSetDao().insert(
                set.copy(
                    id = 0,
                    date = targetDate,
                    completed = false,
                )
            )
        }
    }

    suspend fun updateSet(set: WorkoutSet) {
        db.workoutSetDao().update(set)
    }

    suspend fun getById(id: Long): WorkoutSet? = db.workoutSetDao().getById(id)

    suspend fun deleteSet(id: Long) {
        db.workoutSetDao().deleteById(id)
    }
}
