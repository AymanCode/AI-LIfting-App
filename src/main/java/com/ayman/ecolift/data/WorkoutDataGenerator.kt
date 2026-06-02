package com.ayman.ecolift.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random

object WorkoutDataGenerator {

    suspend fun generateFakeData(db: AppDatabase) {
        val exerciseDao = db.exerciseDao()
        val cycleDao = db.cycleDao()
        val cycleSlotDao = db.cycleSlotDao()
        val splitExerciseDao = db.splitExerciseDao()
        val workoutDayDao = db.workoutDayDao()
        val workoutSetDao = db.workoutSetDao()

        // 1. Setup Exercise Definitions
        val exerciseDefinitions = mapOf(
            "Fake Bench Press" to "CHEST · TRICEPS",
            "Fake Dumbbell Press" to "CHEST", // Swap for Bench
            "Fake Squat" to "LEGS",
            "Fake Front Squat" to "LEGS", // Swap for Squat
            "Fake Deadlift" to "BACK · LEGS",
            "Fake RDL" to "LEGS", // Swap for Deadlift
            "Fake Overhead Press" to "SHOULDERS · TRICEPS",
            "Fake DB Shoulder Press" to "SHOULDERS", // Swap for OHP
            "Fake Bicep Curls" to "BICEPS",
            "Fake Leg Extensions" to "LEGS",
            "Fake Leg Press" to "LEGS", // Phase 2 evolution
            "Fake Lateral Raises" to "SHOULDERS",
            "Fake Tricep Pushdowns" to "TRICEPS"
        )

        val consistentEx = listOf("Fake Bench Press", "Fake Squat", "Fake Deadlift", "Fake Overhead Press")
        val swapMap = mapOf(
            "Fake Bench Press" to "Fake Dumbbell Press",
            "Fake Squat" to "Fake Front Squat",
            "Fake Deadlift" to "Fake RDL",
            "Fake Overhead Press" to "Fake DB Shoulder Press"
        )

        val exerciseIds = mutableMapOf<String, Long>()

        for ((name, muscle) in exerciseDefinitions) {
            val existing = exerciseDao.getByExactName(name)
            if (existing != null) {
                exerciseIds[name] = existing.id
            } else {
                val newId = exerciseDao.upsert(Exercise(name = name, muscleGroups = muscle))
                exerciseIds[name] = newId
            }
        }

        // 2. Setup Cycle and Slots
        val cycle = cycleDao.getCycle()
        if (cycle == null) {
            cycleDao.upsert(Cycle(id = 1, numTypes = 4, isActive = true, name = "My Cycle"))
        }

        val slots = listOf("Upper 1", "Lower 1", "Upper 2", "Lower 2")
        val slotIds = mutableListOf<Long>()
        val existingSlots = cycleSlotDao.getAll()
        
        if (existingSlots.isEmpty() || existingSlots.size < 4) {
             cycleSlotDao.deleteAll()
             for ((i, slotName) in slots.withIndex()) {
                 val id = cycleSlotDao.upsert(CycleSlot(name = slotName, orderIndex = i))
                 slotIds.add(id)
             }
        } else {
             slotIds.addAll(existingSlots.map { it.id }.take(4))
        }

        // 3. Define splits for Phase 1 and Phase 2
        fun getExercisesForPhaseAndSlot(slotIndex: Int, isPhase2: Boolean): List<String> {
            return when (slotIndex) {
                0 -> listOf("Fake Bench Press", "Fake Overhead Press", "Fake Bicep Curls")
                1 -> if (isPhase2) listOf("Fake Squat", "Fake Leg Press") else listOf("Fake Squat", "Fake Leg Extensions")
                2 -> listOf("Fake Overhead Press", "Fake Bench Press", "Fake Tricep Pushdowns")
                3 -> listOf("Fake Deadlift", "Fake Lateral Raises")
                else -> emptyList()
            }
        }

        // Update the database split definition to reflect Phase 2 as the "current" state
        splitExerciseDao.deleteAll()
        val splitsToInsert = mutableListOf<SplitExercise>()
        for (i in 0..3) {
            val phase2Ex = getExercisesForPhaseAndSlot(i, true)
            for ((order, exName) in phase2Ex.withIndex()) {
                splitsToInsert.add(SplitExercise(splitId = slotIds[i], exerciseId = exerciseIds[exName]!!, orderIndex = order))
            }
        }
        splitExerciseDao.insertAll(splitsToInsert)

        // 4. Setup Simulation Timeline
        var currentDate = LocalDate.now().minusYears(1)
        val endDate = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val totalDays = ChronoUnit.DAYS.between(currentDate, endDate).toInt()

        // Generate 2 random vacation blocks of 10 days
        val vacationStarts = listOf(
            currentDate.plusDays(Random.nextLong(30, (totalDays / 2).toLong())),
            currentDate.plusDays(Random.nextLong((totalDays / 2).toLong(), totalDays.toLong() - 20))
        )

        fun isVacation(date: LocalDate): Boolean {
            return vacationStarts.any { vStart ->
                !date.isBefore(vStart) && date.isBefore(vStart.plusDays(10))
            }
        }

        val progression = mutableMapOf(
            "Fake Bench Press" to 135,
            "Fake Squat" to 185,
            "Fake Deadlift" to 225,
            "Fake Overhead Press" to 95
        )

        var occurrenceCount = 0

        // 5. Run the Simulation Loop
        while (!currentDate.isAfter(endDate)) {
            val dayOfWeek = currentDate.dayOfWeek.value
            val isScheduledDay = dayOfWeek == 1 || dayOfWeek == 2 || dayOfWeek == 4 || dayOfWeek == 5

            if (isScheduledDay) {
                // Organic Scheduling (Complexity 1)
                val skipped = Random.nextFloat() < 0.15f
                if (isVacation(currentDate) || skipped) {
                    currentDate = currentDate.plusDays(1)
                    continue
                }

                val weekNumber = ChronoUnit.WEEKS.between(LocalDate.now().minusYears(1), currentDate).toInt()
                val isDeload = weekNumber % 6 == 0 // Deload every 6th week (Complexity 2)
                val isPhase2 = weekNumber > 26 // Change routine after 6 months (Complexity 6)

                val slotIndex = occurrenceCount % 4
                val slotId = slotIds[slotIndex]

                val dateString = currentDate.format(formatter)
                workoutDayDao.upsert(
                    WorkoutDay(
                        date = dateString,
                        cycleSlotId = slotId,
                        cycleSlotOccurrence = occurrenceCount / 4,
                        cycleSlotType = slotIndex
                    )
                )

                val plannedExercises = getExercisesForPhaseAndSlot(slotIndex, isPhase2)
                
                for (plannedExName in plannedExercises) {
                    var exName = plannedExName
                    var setOrdinal = 1
                    
                    // On-the-fly Swaps (Complexity 5)
                    if (swapMap.containsKey(exName) && Random.nextFloat() < 0.10f) {
                        exName = swapMap[exName]!!
                    }

                    val isConsistent = consistentEx.contains(plannedExName)
                    
                    if (!isConsistent && Random.nextFloat() > 0.6f) {
                        continue // Skip inconsistent exercise
                    }

                    var workingWeight = 0
                    if (isConsistent) {
                        workingWeight = progression[plannedExName]!!
                        if (isDeload) {
                            workingWeight = (workingWeight * 0.8).toInt()
                        } else {
                            // Normal progression logic
                            val r = Random.nextFloat()
                            if (r > 0.90f) progression[plannedExName] = workingWeight + 5
                            else if (r < 0.05f) progression[plannedExName] = workingWeight - 5
                        }
                    } else {
                        workingWeight = Random.nextInt(20, 100)
                    }
                    
                    // Warm-up Sets (Complexity 4)
                    if (isConsistent) {
                        // Warmup 1
                        workoutSetDao.insert(
                            WorkoutSet(
                                exerciseId = exerciseIds[exName]!!, date = dateString, setNumber = setOrdinal++,
                                weightLbs = (workingWeight * 0.5).toInt(), reps = 10, completed = true
                            )
                        )
                        // Warmup 2
                        workoutSetDao.insert(
                            WorkoutSet(
                                exerciseId = exerciseIds[exName]!!, date = dateString, setNumber = setOrdinal++,
                                weightLbs = (workingWeight * 0.75).toInt(), reps = 5, completed = true
                            )
                        )
                    }

                    val workingSets = if (isDeload) 2 else 3

                    // Working Sets & Fatigue (Complexity 3)
                    for (i in 1..workingSets) {
                        var reps = if (isConsistent) 8 else Random.nextInt(8, 16)
                        
                        // Fatigue on the last working set
                        if (i == workingSets && !isDeload && Random.nextFloat() < 0.5f) {
                            reps -= Random.nextInt(1, 4)
                            if (reps < 1) reps = 1
                        }

                        workoutSetDao.insert(
                            WorkoutSet(
                                exerciseId = exerciseIds[exName]!!, date = dateString, setNumber = setOrdinal++,
                                weightLbs = workingWeight, reps = reps, completed = true
                            )
                        )
                    }
                }

                occurrenceCount++
            }
            currentDate = currentDate.plusDays(1)
        }
    }
}
