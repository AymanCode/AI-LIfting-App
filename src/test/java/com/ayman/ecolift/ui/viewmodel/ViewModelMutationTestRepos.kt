package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.ExerciseRenameResult
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Mutable delegates around the same DemoRepos seed used by the existing view-model harnesses. */
internal class ViewModelMutationTestRepos(initialSets: List<WorkoutSet>) {
    private val initialExercises = listOf(
        Exercise(10L, "Bench Press", "CHEST"),
        Exercise(20L, "Row", "BACK"),
    )
    private val base = DemoRepos(
        DemoSeedData(
            anchorDate = LocalDate.parse("2026-08-14"),
            exercises = initialExercises,
            sets = initialSets,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 1, isActive = false),
            slots = emptyList(),
            splitExercises = emptyList(),
            workoutDays = initialSets.map { it.date }.distinct().map { WorkoutDay(date = it) },
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(initialSets),
        ),
    )
    val currentSets = MutableStateFlow(initialSets)
    val updates = mutableListOf<WorkoutSet>()
    var updateGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var updateFailure: NotCommittedReason? = null
    var deleteFailure: NotCommittedReason? = null
    val deletedIds = MutableStateFlow<List<Long>>(emptyList())
    val currentExercises = MutableStateFlow(initialExercises)
    val renames = mutableListOf<Pair<Long, String>>()

    val sets = object : SetRepository by base.sets {
        override val sets = currentSets
        override suspend fun getAllSets() = currentSets.value
        override suspend fun getById(id: Long) = currentSets.value.firstOrNull { it.id == id }
        override fun observeSetsForDate(date: String) = currentSets.map { SetQueries.forDate(it, date) }
        override suspend fun getSetsForDate(date: String) = SetQueries.forDate(currentSets.value, date)
        override suspend fun getSetsByDate(date: String) = getSetsForDate(date)
        override suspend fun getSetsForDates(dates: List<String>) = currentSets.value.filter { it.date in dates }
        override suspend fun getCompletedSetsForDates(dates: List<String>) =
            currentSets.value.filter { it.completed && it.date in dates }
        override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String) =
            SetQueries.completedHistoryBeforeDate(currentSets.value, exerciseId, beforeDate).take(100)
        override suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String) =
            SetQueries.maxCompletedWeightBefore(currentSets.value, exerciseId, beforeDate)
        override suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet> {
            updateGate?.await()
            updateFailure?.let { return LocalCommit.NotCommitted(it) }
            updates += set
            currentSets.value = currentSets.value.map { if (it.id == set.id) set else it }
            return LocalCommit.Committed(set)
        }
        override suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet?): LocalCommit<WorkoutSet> { return update(set) }
        override suspend fun delete(id: Long) {
            currentSets.value = currentSets.value.filterNot { it.id == id }
            deletedIds.value += id
        }
        override suspend fun deleteSet(id: Long) = delete(id)
        override suspend fun deleteSets(ids: List<Long>, precondition: () -> Unit): LocalCommit<Unit> {
            precondition()
            deleteFailure?.let { return LocalCommit.NotCommitted(it) }
            currentSets.value = currentSets.value.filterNot { it.id in ids }
            deletedIds.value += ids
            return LocalCommit.Committed(Unit)
        }
        override suspend fun deleteSetIfOpen(id: Long, syncId: String): Boolean =
            super<SetRepository>.deleteSetIfOpen(id, syncId)
        override suspend fun completeSetIfOpen(id: Long, syncId: String): WorkoutSet? =
            super<SetRepository>.completeSetIfOpen(id, syncId)
    }
    val exercises = object : ExerciseRepository by base.exercises {
        override val exercises = currentExercises
        override suspend fun getAll() = currentExercises.value
        override suspend fun getById(id: Long) = currentExercises.value.firstOrNull { it.id == id }
        override suspend fun getByIds(ids: Collection<Long>) = currentExercises.value.filter { it.id in ids }
        override suspend fun updateName(id: Long, newName: String): ExerciseRenameResult {
            renames += id to newName
            val collision = currentExercises.value.firstOrNull {
                it.id != id && it.name.equals(newName, ignoreCase = true)
            }
            if (collision != null) return ExerciseRenameResult.NameCollision(collision)
            val renamed = requireNotNull(getById(id)).copy(name = newName)
            currentExercises.value = currentExercises.value.map { if (it.id == id) renamed else it }
            return ExerciseRenameResult.Success(renamed)
        }
    }
    val repos: AppRepos = object : AppRepos by base {
        override val sets: SetRepository = this@ViewModelMutationTestRepos.sets
        override val exercises: ExerciseRepository = this@ViewModelMutationTestRepos.exercises
    }
}
