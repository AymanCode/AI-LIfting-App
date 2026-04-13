package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val db: AppDatabase) {
    val exercises: Flow<List<Exercise>> = db.exerciseDao().observeAll()

    suspend fun getAll(): List<Exercise> = db.exerciseDao().getAll()

    suspend fun getById(id: Long): Exercise? = db.exerciseDao().getById(id)

    suspend fun findExact(name: String): Exercise? = db.exerciseDao().getByExactName(name.trim())

    suspend fun getOrCreate(name: String): Exercise {
        val normalized = normalizeName(name)
        val existing = db.exerciseDao().getByExactName(normalized)
        if (existing != null) {
            return existing
        }
        val exercise = Exercise(name = normalized)
        val insertedId = db.exerciseDao().insert(exercise)
        return if (insertedId == -1L) {
            db.exerciseDao().getByExactName(normalized) ?: exercise
        } else {
            exercise.copy(id = insertedId)
        }
    }

    suspend fun suggestions(query: String, maxDistance: Int = 2, limit: Int = 5): List<ExerciseSuggestion> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        return getAll()
            .map { exercise ->
                ExerciseSuggestion(
                    exercise = exercise,
                    distance = FuzzyMatcher.levenshteinDistance(normalizedQuery, exercise.name),
                )
            }
            .filter { it.distance <= maxDistance }
            .sortedWith(compareBy<ExerciseSuggestion> { it.distance }.thenBy { it.exercise.name })
            .take(limit)
    }

    fun normalizeName(name: String): String {
        return name
            .trim()
            .lowercase()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { first -> first.uppercase() }
            }
    }

    suspend fun updateName(id: Long, newName: String) {
        val normalized = normalizeName(newName)
        db.exerciseDao().getById(id)?.let { exercise ->
            db.exerciseDao().upsert(exercise.copy(name = normalized))
        }
    }

    suspend fun deleteExercise(id: Long) {
        db.exerciseDao().deleteExerciseWithLogs(id)
    }
}

data class ExerciseSuggestion(
    val exercise: Exercise,
    val distance: Int,
)
