package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val exerciseDao: ExerciseDao) {
    val exercises: Flow<List<Exercise>> = exerciseDao.observeAll()

    suspend fun getAll(): List<Exercise> = exerciseDao.getAll()

    suspend fun getById(id: Long): Exercise? = exerciseDao.getById(id)

    suspend fun getByIds(ids: Collection<Long>): List<Exercise> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return exerciseDao.getByIds(ids.distinct())
    }

    suspend fun findExact(name: String): Exercise? = exerciseDao.getByExactName(name.trim())

    suspend fun getOrCreate(name: String): Exercise {
        val normalized = normalizeName(name)
        val existing = exerciseDao.getByExactName(normalized)
        if (existing != null) {
            return existing
        }
        val exercise = Exercise(name = normalized)
        val insertedId = exerciseDao.insert(exercise)
        return if (insertedId == -1L) {
            exerciseDao.getByExactName(normalized) ?: exercise
        } else {
            exercise.copy(id = insertedId)
        }
    }

    suspend fun suggestions(query: String, maxDistance: Int = 2, limit: Int = 5): List<ExerciseSuggestion> {
        val normalizedQuery = normalizeName(query)
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }

        val candidates = exerciseDao.searchByName(
            query = normalizedQuery,
            limit = limit.coerceAtLeast(1) * 8,
        )
        return candidates
            .map { exercise ->
                ExerciseSuggestion(
                    exercise = exercise,
                    distance = FuzzyMatcher.levenshteinDistance(
                        normalizedQuery.lowercase(),
                        exercise.name.lowercase(),
                    ),
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
        exerciseDao.getById(id)?.let { exercise ->
            exerciseDao.upsert(exercise.copy(name = normalized))
        }
    }

    suspend fun deleteExercise(id: Long) {
        exerciseDao.deleteExerciseWithLogs(id)
    }
}

data class ExerciseSuggestion(
    val exercise: Exercise,
    val distance: Int,
)
