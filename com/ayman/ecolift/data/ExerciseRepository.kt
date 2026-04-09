package com.ayman.ecolift.data

import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val db: AppDatabase) {
    suspend fun createExercise(canonicalName: String): Exercise {
        val trimmed = canonicalName.trim()
        val lowerCase = trimmed.lowercase()
        val capitalized = lowerCase.split(" ").joinToString(" ") { it.capitalize() }
        val existing = db.exerciseDao().getByExactCanonicalName(lowerCase)
        return if (existing != null) {
            existing
        } else {
            val exercise = Exercise(canonicalName = capitalized, aliases = "")
            db.exerciseDao().insert(exercise)
            exercise
        }
    }

    suspend fun getAll(): List<Exercise> = db.exerciseDao().getAll()

    suspend fun getById(id: Long): Exercise? = db.exerciseDao().getById(id)

    suspend fun getRecentlyUsed(limit: Int = 5): List<Exercise> = db.exerciseDao().getRecentlyUsed(limit)
}
