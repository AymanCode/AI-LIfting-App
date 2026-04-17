package com.ayman.ecolift.ai

import android.content.Context
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutSet
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
        val cycleSlots = db.cycleSlotDao().getAll()
        val slotOrder = cycleSlots.mapIndexed { index, slot -> slot.id to index }.toMap()
        val workoutDays = db.workoutDayDao().getAll().map { day ->
            ResolvedWorkoutDay(
                date = day.date,
                slotType = day.cycleSlotType ?: day.cycleSlotId?.let(slotOrder::get),
                occurrence = day.cycleSlotOccurrence,
            )
        }
        val exercisesById = db.exerciseDao().getAll().associateBy(Exercise::id)
        val setsByDate = db.workoutSetDao().getAll().groupBy(WorkoutSet::date)
        val numTypes = cycle?.numTypes ?: cycleSlots.size

        return JSONObject()
            .put("currentSplitPosition", currentSplitPosition(today, numTypes, cycle?.nextSessionType, workoutDays))
            .put("next3Workouts", JSONArray(nextWorkouts(today, numTypes, cycle?.nextSessionType, workoutDays, setsByDate, exercisesById)))
            .put("topPlateauedLifts", JSONArray(plateauedLifts(setsByDate, exercisesById)))
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
        workoutDays: List<ResolvedWorkoutDay>,
    ): JSONObject {
        val todayDay = workoutDays.firstOrNull { it.date == today }
        val lastAssigned = workoutDays
            .asSequence()
            .filter { it.date < today && it.slotType != null }
            .maxByOrNull { it.date }
        val nextType = when {
            todayDay?.slotType != null -> todayDay.slotType
            nextSessionType != null -> nextSessionType
            numTypes > 0 && lastAssigned?.slotType != null -> (lastAssigned.slotType + 1) % numTypes
            numTypes > 0 -> 0
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
        workoutDays: List<ResolvedWorkoutDay>,
        setsByDate: Map<String, List<WorkoutSet>>,
        exercises: Map<Long, Exercise>,
    ): List<JSONObject> {
        if (numTypes <= 0) {
            return emptyList()
        }

        val lastAssigned = workoutDays
            .asSequence()
            .filter { it.date < today && it.slotType != null }
            .maxByOrNull { it.date }
        val latestDayBySlot = workoutDays
            .asSequence()
            .filter { it.date < today && it.slotType != null }
            .groupBy { it.slotType!! }
            .mapValues { (_, days) -> days.maxByOrNull { it.date } }
        val startType = nextSessionType
            ?: lastAssigned?.slotType?.let { (it + 1) % numTypes }
            ?: 0

        return (0 until 3).map { offset ->
            val slotType = (startType + offset) % numTypes
            val previewExercises = latestDayBySlot[slotType]
                ?.let { day ->
                    setsByDate[day.date].orEmpty()
                        .asSequence()
                        .mapNotNull { set -> exercises[set.exerciseId]?.name }
                        .distinct()
                        .take(4)
                        .toList()
                }
                .orEmpty()
            JSONObject()
                .put("slot", "Day ${('A' + slotType)}")
                .put("previewExercises", JSONArray(previewExercises))
        }
    }

    private fun plateauedLifts(
        setsByDate: Map<String, List<WorkoutSet>>,
        exercises: Map<Long, Exercise>,
    ): List<JSONObject> {
        val setsByExercise = linkedMapOf<Long, MutableList<WorkoutSet>>()
        for (dateSets in setsByDate.values) {
            for (set in dateSets) {
                setsByExercise.getOrPut(set.exerciseId) { mutableListOf() }.add(set)
            }
        }

        return setsByExercise.mapNotNull { (exerciseId, sets) ->
            val sessionMaxes = sets
                .groupBy { it.date }
                .toSortedMap()
                .values
                .mapNotNull { daySets -> daySets.maxOfOrNull { it.weightLbs ?: 0 }?.takeIf { it > 0 } }
            if (sessionMaxes.size < 3) {
                return@mapNotNull null
            }
            val recent = sessionMaxes.takeLast(3)
            if (recent.last() > recent.first()) {
                return@mapNotNull null
            }
            val name = exercises[exerciseId]?.name ?: return@mapNotNull null
            JSONObject()
                .put("exercise", name)
                .put("recentMax", recent.last())
                .put("sessionsWithoutIncrease", recent.size)
        }.take(5)
    }
}

private data class ResolvedWorkoutDay(
    val date: String,
    val slotType: Int?,
    val occurrence: Int?,
)
