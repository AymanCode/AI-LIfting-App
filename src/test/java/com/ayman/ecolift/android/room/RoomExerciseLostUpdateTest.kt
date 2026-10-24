package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.ExerciseDao
import com.ayman.ecolift.data.ExerciseAliasDao
import com.ayman.ecolift.data.ExerciseDistinctPairDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.ayman.ecolift.data.room.Exercise as RoomExercise

class RoomExerciseLostUpdateTest {
    @Test
    fun rename_and_muscle_group_change_both_survive() {
        val db = mock<AppDatabase>()
        val exercises = mock<ExerciseDao>()
        val aliases = mock<ExerciseAliasDao>()
        val pairs = mock<ExerciseDistinctPairDao>()
        whenever(db.transactionExecutor).thenReturn(java.util.concurrent.Executor { it.run() })
        whenever(db.suspendingTransactionId).thenReturn(ThreadLocal())
        whenever(db.exerciseDao()).thenReturn(exercises)
        whenever(db.exerciseAliasDao()).thenReturn(aliases)
        whenever(db.exerciseDistinctPairDao()).thenReturn(pairs)
        doAnswer { null }.whenever(db).setTransactionSuccessful()
        doAnswer { null }.whenever(db).endTransaction()
        val renameInsideWrite = CountDownLatch(1)
        val releaseRename = CountDownLatch(1)
        val upserts = AtomicInteger()
        runBlocking {
            whenever(exercises.getAll()).thenReturn(
                listOf(
                    RoomExercise(
                        id = EXERCISE_ID,
                        name = ORIGINAL_NAME,
                        muscleGroups = ORIGINAL_MUSCLES,
                        createdAt = 1,
                    ),
                ),
            )
            whenever(aliases.getAll()).thenReturn(emptyList())
            whenever(aliases.insert(any())).thenReturn(1L)
            whenever(pairs.getAll()).thenReturn(emptyList())
            // The first upsert is the rename. Hold that lock until the muscle-group caller is parked
            // on it, so a copy taken before the lock still carries the old name.
            whenever(exercises.upsert(any())).thenAnswer {
                if (upserts.incrementAndGet() == 1) {
                    renameInsideWrite.countDown()
                    assertTrue(releaseRename.await(WAIT_SECONDS, TimeUnit.SECONDS))
                }
                EXERCISE_ID
            }
        }

        val store = RoomStore(db)
        val repo = RoomExerciseRepository(store)
        runBlocking { repo.refresh() }

        val renameError = AtomicReference<Throwable>()
        val rename = Thread {
            try {
                runBlocking { repo.updateName(EXERCISE_ID, NEW_NAME) }
            } catch (error: Throwable) {
                renameError.set(error)
                releaseRename.countDown()
            }
        }
        val muscleError = AtomicReference<Throwable>()
        val muscle = Thread {
            try {
                runBlocking { repo.updateMuscleGroups(EXERCISE_ID, NEW_MUSCLES) }
            } catch (error: Throwable) {
                muscleError.set(error)
            }
        }
        rename.start()
        try {
            assertTrue(renameInsideWrite.await(WAIT_SECONDS, TimeUnit.SECONDS))
            muscle.start()
            var parkedChecks = 0
            val reachedLock = waitUntil {
                val parked = muscle.isAlive && muscle.state.let {
                    it == Thread.State.WAITING || it == Thread.State.TIMED_WAITING || it == Thread.State.BLOCKED
                }
                if (parked) parkedChecks += 1 else parkedChecks = 0
                parkedChecks >= 3
            }
            assertTrue(
                "muscle-group write did not reach the store lock (alive=${muscle.isAlive}, state=${muscle.state}, error=${muscleError.get()})",
                reachedLock && muscle.isAlive,
            )
        } finally {
            releaseRename.countDown()
        }
        rename.join(JOIN_MS)
        muscle.join(JOIN_MS)
        renameError.get()?.let { throw it }
        muscleError.get()?.let { throw it }
        assertTrue(rename.state == Thread.State.TERMINATED)
        assertTrue(muscle.state == Thread.State.TERMINATED)

        val saved = runBlocking { repo.getById(EXERCISE_ID) }
        assertEquals(NEW_NAME, saved?.name)
        assertEquals(NEW_MUSCLES, saved?.muscleGroups)
    }

    private fun waitUntil(predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + WAIT_SECONDS * 1_000_000_000
        while (System.nanoTime() < deadline) {
            if (predicate()) return true
            Thread.sleep(10)
        }
        return predicate()
    }

    private companion object {
        const val EXERCISE_ID = 1L
        const val ORIGINAL_NAME = "Cable Fly"
        const val NEW_NAME = "Cable Row"
        const val ORIGINAL_MUSCLES = "CHEST"
        const val NEW_MUSCLES = "BACK"
        const val WAIT_SECONDS = 5L
        const val JOIN_MS = 5_000L
    }
}
