package com.ayman.ecolift.android.room

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.room.CycleSlot
import com.ayman.ecolift.data.room.Exercise
import com.ayman.ecolift.data.room.SplitExercise
import com.ayman.ecolift.data.room.UserSettings
import com.ayman.ecolift.data.room.WorkoutExerciseCard
import com.ayman.ecolift.data.room.WorkoutSet
import com.ayman.ecolift.web.DataGeneration
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LearnedSmartSuggestRestoreTest {
    @Test
    fun a_restored_smart_suggest_value_reaches_the_running_learner() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope, DataGeneration())
        try {
            db.userSettingsDao().upsert(UserSettings(smartSuggest = false))
            repos.loadAll()
            assertTrue(
                "the first load did not install Smart suggest off",
                withTimeoutOrNull(20_000) { repos.learnedSplits.model.first { !it.smartSuggest } } != null,
            )

            repos.replaceDataset {
                db.userSettingsDao().upsert(UserSettings(smartSuggest = true))
            }
            assertTrue(
                "restored Smart suggest on did not reach the learner",
                withTimeoutOrNull(20_000) { repos.learnedSplits.model.first { it.smartSuggest } } != null,
            )

            repos.replaceDataset {
                db.userSettingsDao().upsert(UserSettings(smartSuggest = false))
            }
            assertTrue(
                "restored Smart suggest off did not reach the learner",
                withTimeoutOrNull(20_000) { repos.learnedSplits.model.first { !it.smartSuggest } } != null,
            )
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }

    @Test
    fun a_restore_whose_import_throws_keeps_a_learner_running() = runBlocking {
        val failure = learnerRunsAgainAfterAFailedRestore { throw IOException("bad file") }
        assertTrue("the import failure did not reach the caller", failure is IOException)
        assertEquals("bad file", failure.message)
    }

    @Test
    fun a_restore_whose_reload_throws_learns_only_from_the_restored_data() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope, DataGeneration())
        val phase = AtomicInteger(FIRST_LEARNER)
        val staleEntries = AtomicInteger()
        val firstLearner = CompletableDeferred<Unit>()
        val freshWrite = CompletableDeferred<Unit>()
        try {
            seedHandMadeSplit(db)
            repos.beforeLearnedWrite = {
                when (phase.get()) {
                    FIRST_LEARNER -> {
                        firstLearner.complete(Unit)
                        awaitCancellation()
                    }
                    STALE_CACHE -> {
                        staleEntries.incrementAndGet()
                        awaitCancellation()
                    }
                }
            }
            repos.afterLearnedWrite = { if (phase.get() == REPAIRED) freshWrite.complete(Unit) }
            repos.loadAll()
            assertTrue(
                "the first load did not start a learner on dataset A",
                withTimeoutOrNull(20_000) { firstLearner.await() } != null,
            )
            phase.set(STALE_CACHE)

            val writable = db.openHelper.writableDatabase
            val cardioSchema = writable.query(
                "SELECT sql FROM sqlite_master WHERE tbl_name = 'cardio_sessions' AND sql IS NOT NULL " +
                    "ORDER BY type = 'table' DESC",
            ).use { cursor -> List(cursor.count) { cursor.moveToNext(); cursor.getString(0) } }
            val failure = withTimeout(20_000) {
                runCatching {
                    repos.replaceDataset {
                        db.clearAllTables()
                        seedHandMadeSplit(db, lifts = 5L..8L, trained = listOf(5L, 6L), name = "Legs")
                        writable.execSQL("DROP TABLE cardio_sessions")
                    }
                }.exceptionOrNull()
            }
            assertTrue("the reload failure did not reach the caller", failure is SQLiteException)

            delay(5_000)
            assertEquals("a learner wrote from the stale cache of dataset A", 0, staleEntries.get())
            assertEquals("dataset B's split changed while the cache was stale", listOf(5L, 6L, 7L, 8L), members(db))
            assertFalse(
                "dataset B's split became learned while the cache was stale",
                db.cycleSlotDao().getAll().single { it.id == 7L }.learned,
            )

            phase.set(REPAIRED)
            cardioSchema.forEach(writable::execSQL)
            repos.loadAll()
            assertTrue(
                "no learner wrote after a successful reload",
                withTimeoutOrNull(20_000) { freshWrite.await() } != null,
            )
            val learned = members(db)
            assertTrue("the learner did not learn dataset B's lifts: $learned", learned.containsAll(listOf(5L, 6L)))
            assertTrue("dataset A's lifts reached dataset B: $learned", learned.none { it in 1L..4L })
            assertTrue("dataset B's split did not become learned", db.cycleSlotDao().getAll().single { it.id == 7L }.learned)
            assertEquals("a learner wrote from the stale cache of dataset A", 0, staleEntries.get())
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }

    private suspend fun learnerRunsAgainAfterAFailedRestore(
        import: suspend (AppDatabase) -> Unit,
    ): Throwable {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope, DataGeneration())
        val calls = AtomicInteger()
        val firstLearner = CompletableDeferred<Unit>()
        val nextLearner = CompletableDeferred<Unit>()
        try {
            seedHandMadeSplit(db)
            repos.beforeLearnedWrite = {
                if (calls.incrementAndGet() == 1) firstLearner.complete(Unit) else nextLearner.complete(Unit)
                awaitCancellation()
            }
            repos.loadAll()
            assertTrue(
                "the first load did not start a learner",
                withTimeoutOrNull(20_000) { firstLearner.await() } != null,
            )

            val failure = withTimeout(20_000) {
                runCatching { repos.replaceDataset { import(db) } }.exceptionOrNull()
            }

            assertTrue(
                "no learner ran again after the failed restore",
                withTimeoutOrNull(20_000) { nextLearner.await() } != null,
            )
            return checkNotNull(failure) { "the failed restore returned normally" }
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }

    private suspend fun members(db: AppDatabase): List<Long> =
        db.splitExerciseDao().getAll().filter { it.splitId == 7L }.sortedBy { it.orderIndex }.map { it.exerciseId }

    private suspend fun seedHandMadeSplit(
        db: AppDatabase,
        lifts: LongRange = 1L..4L,
        trained: List<Long> = listOf(1L, 2L),
        name: String = "Push",
    ) {
        val today = todayLocalDate()
        val dates = listOf(
            today.minus(32, DateTimeUnit.DAY).toString(),
            today.minus(18, DateTimeUnit.DAY).toString(),
        )
        db.exerciseDao().insertAll(lifts.map { id -> Exercise(id = id, name = "Lift $id", createdAt = id) })
        db.cycleSlotDao().upsert(CycleSlot(id = 7, name = name, createdAt = 0, anchorUpdatedAt = null))
        db.splitExerciseDao().insertAll(
            lifts.mapIndexed { index, lift ->
                SplitExercise(id = lift, splitId = 7, exerciseId = lift, orderIndex = index)
            },
        )
        db.workoutSetDao().insertAll(
            dates.flatMap { date ->
                trained.map { lift ->
                    WorkoutSet(
                        exerciseId = lift,
                        date = date,
                        setNumber = 1,
                        weightLbs = 1000,
                        reps = 8,
                        completed = true,
                    )
                }
            },
        )
        db.workoutExerciseCardDao().insertAll(
            dates.flatMap { date ->
                trained.mapIndexed { index, lift ->
                    WorkoutExerciseCard(
                        id = "$date-$lift",
                        date = date,
                        exerciseId = lift,
                        position = index,
                        displayMode = "EXPANDED",
                        origin = "MANUAL",
                    )
                }
            },
        )
    }

    private companion object {
        const val FIRST_LEARNER = 0
        const val STALE_CACHE = 1
        const val REPAIRED = 2
    }
}
