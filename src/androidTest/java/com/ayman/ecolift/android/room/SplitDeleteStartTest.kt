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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitDeleteStartTest {
    @Test
    fun delete_removes_both_board_references_and_start_uses_only_survivors() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope)
        try {
            db.userSettingsDao().upsert(UserSettings(smartSuggest = false))
            repos.loadAll()
            val ids = listOf("SP delete Gamma", "SP delete Alpha", "SP delete Beta").map { repos.exercises.getOrCreate(it).id }
            val slot = repos.workout.addCycleSlot("SP delete manual")
            ids.forEach { repos.workout.appendSplitExercise(slot, it) }
            val optionSlot = repos.workout.addCycleSlot("SP delete option")
            val untouched = repos.workout.addCycleSlot("SP untouched")
            repos.workout.saveSplitMembers(slot, EffectiveSplitMembers.of(ids.take(2), ids.drop(2)))
            repos.workout.saveSplitMembers(optionSlot, EffectiveSplitMembers.of(ids.drop(1), ids.take(1)))
            repos.workout.saveSplitMembers(untouched, EffectiveSplitMembers.of(ids.drop(1), emptyList()))

            repos.exercises.deleteExercise(ids.first())

            val saved = db.splitMemberChoiceDao().getBySplitId(slot)!!.toChoice()
            assertEquals(listOf(ids[1]), saved.board.inSplit)
            assertEquals(listOf(ids[2]), saved.board.otherOptions)
            assertEquals(2L, saved.revision)
            val option = db.splitMemberChoiceDao().getBySplitId(optionSlot)!!.toChoice()
            assertEquals(emptyList<Long>(), option.board.otherOptions)
            assertEquals(2L, option.revision)
            assertEquals(1L, db.splitMemberChoiceDao().getBySplitId(untouched)!!.revision)
            withTimeout(5_000) {
                repos.workout.splitMemberChoices.first { it is SplitChoicesState.Ready && it.choices[slot]?.revision == 2L }
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
            assertEquals(listOf(ids[1]), db.workoutSetDao().getAll().filter { it.date == date }.map { it.exerciseId }.distinct())
            assertTrue(db.exerciseDao().getAll().none { it.id == ids.first() })
        } finally {
            repos.close()
            scope.coroutineContext.job.cancelAndJoin()
            db.close()
        }
    }
}
