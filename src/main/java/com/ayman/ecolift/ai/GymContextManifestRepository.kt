package com.ayman.ecolift.ai

import android.content.Context
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.WorkoutDates
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GymContextManifestRepository(
    private val context: Context,
    private val db: AppDatabase,
) {
    suspend fun refresh(today: String = WorkoutDates.today()): String {
        val payload = buildManifest(today)
        val file = File(context.filesDir, "gym_context_manifest.json")
        file.writeText(payload.toString(2))
        return file.absolutePath
    }

    suspend fun buildManifest(today: String = WorkoutDates.today()): JSONObject {
        val cycle = db.cycleDao().getCycle()
        val workoutDays = db.workoutDayDao().observeAllSnapshot()
        val exercises = db.exerciseDao().getAll().associateBy { it.id }
        val allSets = db.workoutSetDao().observeAllSnapshot()
        val splitPosition = currentSplitPosition(today, cycle?.numTypes ?: 0, cycle?.nextSessionType, workoutDays)
        val nextThree = nextWorkouts(today, cycle?.numTypes ?: 0, cycle?.nextSessionType, workoutDays, allSets, exercises)
        val plateaued = plateauedLifts(allSets, exercises)
        return JSONObject()
            .put("currentSplitPosition", splitPosition)
            .put("next3Workouts", JSONArray(nextThree))
            .put("topPlateauedLifts", JSONArray(plateaued))
    }

    suspend fun readManifestText(today: String = WorkoutDates.today()): String {
        val file = File(context.filesDir, "gym_context_manifest.json")
        return if (file.exists()) {
            file.readText()
        } else {
            refresh(today)
            file.readText()
        }
    }

    private fun currentSplitPosition(
        today: String,
        numTypes: Int,
        nextSessionType: Int?,
        workoutDays: List<com.ayman.ecolift.data.WorkoutDay>,
    ): JSONObject {
        val todayDay = workoutDays.firstOrNull { it.date == today }
        val nextType = when {
            todayDay?.cycleSlotType != null -> todayDay.cycleSlotType
            nextSessionType != null -> nextSessionType
            numTypes > 0 -> {
                val last = workoutDays
                    .filter { it.date < today && it.cycleSlotType != null }
                    .maxByOrNull { it.date }
                if (last?.cycleSlotType != null) (last.cycleSlotType + 1) % numTypes else 0
            }
            else -> null
        }
        return JSONObject()
            .put("label", nextType?.let { "Day ${('A' + it)}" } ?: "Unassigned")
            .put("date", today)
    }

    private fun nextWorkouts(
        today: String,
        numTypes: Int,
        nextSessionType: Int?,
        workoutDays: List<com.ayman.ecolift.data.WorkoutDay>,
        allSets: List<com.ayman.ecolift.data.WorkoutSet>,
        exercises: Map<Long, com.ayman.ecolift.data.Exercise>,
    ): List<JSONObject> {
        if (numTypes <= 0) return emptyList()
        val startType = nextSessionType ?: run {
            val last = workoutDays
                .filter { it.date < today && it.cycleSlotType != null }
                .maxByOrNull { it.date }
            if (last?.cycleSlotType != null) (last.cycleSlotType + 1) % numTypes else 0
        }
        return (0 until 3).map { offset ->
            val slotType = (startType + offset) % numTypes
            val label = "Day ${('A' + slotType)}"
            val latestDay = workoutDays
                .filter { it.cycleSlotType == slotType && it.date < today }
                .maxByOrNull { it.date }
            val exerciseList = latestDay?.let { day ->
                allSets.filter { it.date == day.date }
                    .mapNotNull { exercises[it.exerciseId]?.name }
                    .distinct()
                    .take(4)
            }.orEmpty()
            JSONObject()
                .put("slot", label)
                .put("previewExercises", JSONArray(exerciseList))
        }
    }

    private fun plateauedLifts(
        allSets: List<com.ayman.ecolift.data.WorkoutSet>,
        exercises: Map<Long, com.ayman.ecolift.data.Exercise>,
    ): List<JSONObject> {
        return allSets
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sets) ->
                val sessionMaxes = sets.groupBy { it.date }
                    .toSortedMap()
                    .values
                    .map { daySets -> daySets.maxOf { it.weightLbs } }
                if (sessionMaxes.size < 3) return@mapNotNull null
                val recent = sessionMaxes.takeLast(3)
                val plateaued = recent.last() <= recent.first()
                if (!plateaued) return@mapNotNull null
                val name = exercises[exerciseId]?.name ?: return@mapNotNull null
                JSONObject()
                    .put("exercise", name)
                    .put("recentMax", recent.last())
                    .put("sessionsWithoutIncrease", recent.size)
            }
            .take(5)
    }
}
