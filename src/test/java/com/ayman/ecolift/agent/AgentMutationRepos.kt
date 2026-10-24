package com.ayman.ecolift.agent

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.ExerciseRenameResult
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/**
 * AppRepos whose set, exercise, and workout-day writes stick.
 * DemoRepos records none of those, so patch tests cannot use it alone.
 */
internal class AgentMutationRepos(
    initialExercises: List<Exercise>,
    initialSets: List<WorkoutSet> = emptyList(),
    private val base: DemoRepos = DemoRepos(agentMutationSeed(initialExercises, initialSets)),
) : AppRepos by base {
    val currentSets = MutableStateFlow(initialSets)
    val currentExercises = MutableStateFlow(initialExercises)
    val movedDays = mutableListOf<Pair<String, String>>()
    private var nextSetId = (initialSets.maxOfOrNull { it.id } ?: 0L) + 1L

    override val exercises = object : ExerciseRepository by base.exercises {
        override val exercises = currentExercises
        override suspend fun getAll(): List<Exercise> = currentExercises.value
        override fun canonicalId(id: Long): Long = id
        override suspend fun getById(id: Long): Exercise? = currentExercises.value.firstOrNull { it.id == id }
        override suspend fun updateName(id: Long, newName: String): ExerciseRenameResult {
            val named = currentExercises.value
            val current = named.firstOrNull { it.id == id } ?: return ExerciseRenameResult.Success(Exercise(id, newName))
            val clash = named.firstOrNull { it.id != id && it.name.equals(newName, ignoreCase = true) }
            if (clash != null) return ExerciseRenameResult.NameCollision(clash)
            val updated = current.copy(name = newName)
            currentExercises.value = named.map { if (it.id == id) updated else it }
            return ExerciseRenameResult.Success(updated)
        }
    }

    override val sets = object : SetRepository by base.sets {
        override val sets = currentSets
        override suspend fun insert(set: WorkoutSet): LocalCommit<WorkoutSet> {
            val stored = if (set.id == 0L) set.copy(id = nextSetId++) else set
            currentSets.value = currentSets.value + stored
            return LocalCommit.Committed(stored)
        }

        override suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet> {
            currentSets.value = currentSets.value.map { if (it.id == set.id) set else it }
            return LocalCommit.Committed(set)
        }

        override suspend fun delete(id: Long) {
            currentSets.value = currentSets.value.filterNot { it.id == id }
        }

        override suspend fun getById(id: Long): WorkoutSet? = currentSets.value.firstOrNull { it.id == id }
        override suspend fun getSetsForDate(date: String): List<WorkoutSet> = SetQueries.forDate(currentSets.value, date)
        override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> =
            SetQueries.completedHistoryBeforeDate(currentSets.value, exerciseId, beforeDate).take(100)

        override suspend fun getSetsSince(exerciseId: Long, sinceDate: String): List<WorkoutSet> =
            currentSets.value
                .filter { it.exerciseId == exerciseId && it.date >= sinceDate && it.isCheckedWorking }
                .sortedWith(compareBy({ it.date }, { it.setNumber }))

        override suspend fun updateDate(fromDate: String, toDate: String) {
            currentSets.value = currentSets.value.map { set ->
                if (set.date == fromDate) set.copy(date = toDate) else set
            }
        }
    }

    override val workout = object : WorkoutRepository by base.workout {
        override suspend fun moveWorkoutDay(currentDate: String, newDate: String) {
            movedDays += currentDate to newDate
        }
    }
}

private fun agentMutationSeed(exercises: List<Exercise>, sets: List<WorkoutSet>) = DemoSeedData(
    anchorDate = LocalDate.parse("2026-08-14"),
    exercises = exercises,
    sets = sets,
    settings = UserSettings(),
    cycle = Cycle(numTypes = 1, isActive = false),
    slots = emptyList(),
    splitExercises = emptyList(),
    workoutDays = sets.map { it.date }.distinct().map { WorkoutDay(date = it) },
    archives = emptyList(),
    cardio = emptyList(),
    pendingReviews = emptyList(),
    usageCounts = SetQueries.usageCounts(sets),
)
