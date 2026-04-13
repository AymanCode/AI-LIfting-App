package com.ayman.ecolift.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Random

object DebugDataHelper {
    suspend fun populateMockData(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val exerciseDao = db.exerciseDao()
        val setDao = db.workoutSetDao()

        // Clear existing data to remove "Bench, Squat, Deadlift"
        exerciseDao.deleteAllSets()
        exerciseDao.deleteAllExercises()

        suspend fun getOrCreateExercise(name: String, muscleGroups: String, isBodyweight: Boolean): Long {
            val existing = exerciseDao.getByExactName(name)
            if (existing != null) return existing.id
            return exerciseDao.insert(Exercise(name = name, muscleGroups = muscleGroups, isBodyweight = isBodyweight))
        }

        // 1. Create Exercises
        val benchId = getOrCreateExercise("Ex 101", "CHEST · TRICEPS", false)
        val squatId = getOrCreateExercise("Ex 202", "LEGS · CORE", false)
        val pullupId = getOrCreateExercise("Ex 303", "BACK · BICEPS", true)

        val random = Random()
        val now = LocalDate.now()

        // 2. Generate history for Bench Press (Increasing trend)
        // Last 60 days, workout every 4 days
        for (i in 0..15) {
            val date = now.minusDays((15 - i) * 4L).toString()
            val baseWeight = 135 + (i * 5) // Gradual increase from 135 to 210
            
            for (setNum in 1..3) {
                setDao.insert(WorkoutSet(
                    exerciseId = benchId,
                    date = date,
                    setNumber = setNum,
                    weightLbs = baseWeight + random.nextInt(5),
                    reps = 8 + random.nextInt(3),
                    completed = true
                ))
            }
        }

        // 3. Generate history for Squat (Plateau)
        for (i in 0..10) {
            val date = now.minusDays((10 - i) * 6L).toString()
            val baseWeight = 225
            
            for (setNum in 1..4) {
                setDao.insert(WorkoutSet(
                    exerciseId = squatId,
                    date = date,
                    setNumber = setNum,
                    weightLbs = baseWeight + random.nextInt(10),
                    reps = 5,
                    completed = true
                ))
            }
        }

        // 4. Generate history for Pull Ups (Bodyweight trend)
        for (i in 0..8) {
            val date = now.minusDays((8 - i) * 7L).toString()
            val extraWeight = i * 2 // Adding 2lbs every week
            
            for (setNum in 1..3) {
                setDao.insert(WorkoutSet(
                    exerciseId = pullupId,
                    date = date,
                    setNumber = setNum,
                    weightLbs = extraWeight,
                    reps = 10,
                    isBodyweight = true,
                    completed = true
                ))
            }
        }
    }
}
