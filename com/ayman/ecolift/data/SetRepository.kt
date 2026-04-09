package com.ayman.ecolift.data

import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

class SetRepository(private val db: AppDatabase) {
    suspend fun logSet(workoutId: Long, exerciseId: Long, weightLb: Double, reps: Int): Long {
        val nextSetOrder = db.workoutSetDao().getSetsForWorkout(workoutId).size + 1
        val set = WorkoutSet(
            workoutId = workoutId,
            exerciseId = exerciseId,
            setOrder = nextSetOrder,
            weightLb = weightLb,
            reps = reps,
            loggedAt = System.currentTimeMillis()
        )
        return db.workoutSetDao().insert(set)
    }

    suspend fun deleteSet(id: Long) {
        db.workoutSetDao().delete(id)
    }

    fun getSetsForWorkout(workoutId: Long): Flow<List<WorkoutSet>> = db.workoutSetDao().getSetsForWorkout(workoutId)

    suspend fun getSetsForExercise(exerciseId: Long): List<WorkoutSet> = db.workoutSetDao().getSetsForExercise(exerciseId)
}
