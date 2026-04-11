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
            val insertedId = db.exerciseDao().insert(exercise)
            exercise.copy(id = insertedId)
        }
    }

    suspend fun getAll(): List<Exercise> = db.exerciseDao().getAll()

    suspend fun getById(id: Long): Exercise? = db.exerciseDao().getById(id)

    suspend fun getRecentlyUsed(limit: Int = 5): List<Exercise> = db.exerciseDao().getRecentlyUsed(limit)

    suspend fun match(query: String, exercises: List<Exercise>, threshold: Double = 0.7, limit: Int = 3): List<Exercise> {
        return com.ayman.ecolift.data.match(query, exercises, threshold, limit)
    }

    suspend fun matchOne(query: String, exercises: List<Exercise>, threshold: Double = 0.6): Exercise? {
        return com.ayman.ecolift.data.matchOne(query, exercises, threshold)
    }
}
