package com.ayman.ecolift.data

import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val db: AppDatabase) {
    suspend fun getOrCreateTodayWorkout(): Workout {
        val todayEpochDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        val openWorkout = db.workoutDao().getOpenWorkoutForDay(todayEpochDay)
        return if (openWorkout != null) {
            openWorkout
        } else {
            val workout = Workout(dateEpochDay = todayEpochDay, startedAt = System.currentTimeMillis())
            val insertedId = db.workoutDao().insert(workout)
            workout.copy(id = insertedId)
        }
    }

    suspend fun endWorkout(id: Long) {
        val currentEpochMillis = System.currentTimeMillis()
        db.workoutDao().updateEndedAt(id, currentEpochMillis)
    }

    fun getAllOrderedByDateDesc(): Flow<List<Workout>> = db.workoutDao().getAllOrderedByDateDesc()

    suspend fun getById(id: Long): Workout? = db.workoutDao().getById(id)
}
