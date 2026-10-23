package com.ayman.ecolift.android.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.room.UserSettings
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitSwitchIntent
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.ui.viewmodel.LogViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import com.ayman.ecolift.data.room.Exercise as RoomExercise

@RunWith(AndroidJUnit4::class)
class MergedExerciseDeleteStartTest {
    @Test
    fun delete_keeper_removes_the_merge_chain_and_start_seeds_the_survivor() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope)
        try {
            db.userSettingsDao().upsert(UserSettings(smartSuggest = false))
            repos.loadAll()
            db.exerciseDao().insertAll(
                listOf(
                    RoomExercise(id = 11, name = "Chain keeper"),
                    RoomExercise(id = 12, name = "Chain middle", mergedIntoId = 11),
                    RoomExercise(id = 14, name = "Chain leaf", mergedIntoId = 12),
                    RoomExercise(id = 13, name = "Chain survivor"),
                    RoomExercise(id = 21, name = "Dangling merge", mergedIntoId = 999),
                    RoomExercise(id = 31, name = "Cycle A", mergedIntoId = 32),
                    RoomExercise(id = 32, name = "Cycle B", mergedIntoId = 31),
                ),
            )
            repos.loadAll()
            assertEquals(21L, repos.exercises.canonicalId(21))
            assertThrows(IllegalStateException::class.java) { repos.exercises.canonicalId(31) }

            val slot = repos.workout.addCycleSlot("Chain start")
            repos.workout.saveSplitMembers(slot, EffectiveSplitMembers.of(listOf(14, 13), emptyList()))
            repos.exercises.deleteExercise(11)

            val saved = db.splitMemberChoiceDao().getBySplitId(slot)!!.toChoice()
            assertEquals(listOf(13L), saved.board.inSplit)
            assertEquals(emptyList<Long>(), saved.board.otherOptions)
            val remaining = db.exerciseDao().getAll().map { it.id }.toSet()
            assertEquals(setOf(13L, 21L, 31L, 32L), remaining)
            withTimeout(5_000) {
                repos.workout.splitMemberChoices.first {
                    it is SplitChoicesState.Ready && it.choices[slot]?.board?.inSplit == listOf(13L)
                }
            }
            val date = "2026-10-23"
            withContext(Dispatchers.Main) {
                val viewModel = LogViewModel(repos)
                viewModel.selectDate(date)
                val outcome = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(5_000) { viewModel.splitSwitchOutcomes.first { it is SplitSwitchOutcome.Applied } }
                }
                viewModel.awaitSplitChoices(slot)
                val request = viewModel.loadProgram(slot, emptySet(), SplitSwitchIntent.DeepLinkLoad)
                assertEquals(request, (outcome.await() as SplitSwitchOutcome.Applied).requestId)
            }
            assertEquals(listOf(13L), db.workoutSetDao().getAll().filter { it.date == date }.map { it.exerciseId }.distinct())
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }

    @Test
    fun start_ignores_a_board_id_with_no_exercise() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope)
        try {
            db.userSettingsDao().upsert(UserSettings(smartSuggest = false))
            repos.loadAll()
            db.exerciseDao().insertAll(listOf(RoomExercise(id = 13, name = "Board survivor")))
            repos.loadAll()
            val slot = repos.workout.addCycleSlot("Missing id")
            repos.workout.saveSplitMembers(slot, EffectiveSplitMembers.of(listOf(13, 999), emptyList()))
            val date = "2026-10-24"
            withContext(Dispatchers.Main) {
                val viewModel = LogViewModel(repos)
                viewModel.selectDate(date)
                val outcome = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(5_000) { viewModel.splitSwitchOutcomes.first { it is SplitSwitchOutcome.Applied } }
                }
                viewModel.awaitSplitChoices(slot)
                val request = viewModel.loadProgram(slot, emptySet(), SplitSwitchIntent.DeepLinkLoad)
                assertEquals(request, (outcome.await() as SplitSwitchOutcome.Applied).requestId)
            }
            assertEquals(listOf(13L), db.workoutSetDao().getAll().filter { it.date == date }.map { it.exerciseId }.distinct())
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }
}
