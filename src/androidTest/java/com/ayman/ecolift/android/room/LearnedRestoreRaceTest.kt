package com.ayman.ecolift.android.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.room.CycleSlot
import com.ayman.ecolift.data.room.Exercise
import com.ayman.ecolift.data.room.SplitExercise
import com.ayman.ecolift.data.room.WorkoutExerciseCard
import com.ayman.ecolift.data.room.WorkoutSet
import com.ayman.ecolift.web.DataGeneration
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearnedRestoreRaceTest {
    @Test
    fun an_in_flight_learned_write_does_not_replace_the_restored_routine() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val generation = DataGeneration()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope, generation)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        try {
            seedHandMadeSplit(db)
            repos.beforeLearnedWrite = {
                entered.complete(Unit)
                release.await()
            }
            repos.afterLearnedWrite = { finished.complete(Unit) }
            repos.loadAll()
            withTimeout(20_000) { entered.await() }

            db.splitExerciseDao().replaceForSplit(7L, listOf(8L, 9L))
            val slot = db.cycleSlotDao().getAll().single { it.id == 7L }
            db.cycleSlotDao().upsert(slot.copy(name = "Restored", learned = false))
            generation.replace { }

            release.complete(Unit)
            withTimeout(20_000) { finished.await() }

            val members = db.splitExerciseDao().getAll()
                .filter { it.splitId == 7L }
                .sortedBy { it.orderIndex }
                .map { it.exerciseId }
            assertEquals(listOf(8L, 9L), members)
            assertFalse(db.cycleSlotDao().getAll().single { it.id == 7L }.learned)
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }

    private suspend fun seedHandMadeSplit(db: AppDatabase) {
        val today = todayLocalDate()
        val older = today.minus(32, DateTimeUnit.DAY).toString()
        val recent = today.minus(18, DateTimeUnit.DAY).toString()
        db.exerciseDao().insertAll(
            (listOf(1L, 2L, 3L, 4L, 8L, 9L)).map { id ->
                Exercise(id = id, name = "Lift $id", createdAt = id)
            },
        )
        db.cycleSlotDao().upsert(CycleSlot(id = 7, name = "Push", createdAt = 0, anchorUpdatedAt = null))
        db.splitExerciseDao().insertAll(
            (1L..4L).map { lift ->
                SplitExercise(id = lift, splitId = 7, exerciseId = lift, orderIndex = (lift - 1).toInt())
            },
        )
        val sets = listOf(older, recent).flatMap { date ->
            listOf(1L, 2L).map { lift ->
                WorkoutSet(
                    exerciseId = lift,
                    date = date,
                    setNumber = 1,
                    weightLbs = 1000,
                    reps = 8,
                    completed = true,
                )
            }
        }
        db.workoutSetDao().insertAll(sets)
        val cards = listOf(older, recent).flatMap { date ->
            listOf(1L, 2L).mapIndexed { index, lift ->
                WorkoutExerciseCard(
                    id = "$date-$lift",
                    date = date,
                    exerciseId = lift,
                    position = index,
                    displayMode = "EXPANDED",
                    origin = "MANUAL",
                )
            }
        }
        db.workoutExerciseCardDao().insertAll(cards)
    }
}
