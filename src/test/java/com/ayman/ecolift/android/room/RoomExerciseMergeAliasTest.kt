package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.ExerciseAliasDao
import com.ayman.ecolift.data.ExerciseDao
import com.ayman.ecolift.data.ExerciseDistinctPairDao
import com.ayman.ecolift.data.ExerciseMatcher
import com.ayman.ecolift.web.supabase.ExerciseMergeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicLong
import com.ayman.ecolift.data.room.Exercise as RoomExercise

class RoomExerciseMergeAliasTest {
    @Test
    fun alias_recorded_before_merge_finds_the_keeper() {
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
        val nextAliasId = AtomicLong(1)
        runBlocking {
            whenever(exercises.getAll()).thenReturn(
                listOf(
                    RoomExercise(id = KEEPER_ID, name = KEEPER_NAME, muscleGroups = "CHEST", createdAt = 1),
                    RoomExercise(id = LOSER_ID, name = LOSER_NAME, muscleGroups = "CHEST", createdAt = 2),
                ),
            )
            whenever(aliases.getAll()).thenReturn(emptyList())
            whenever(aliases.insert(any())).thenAnswer { nextAliasId.getAndIncrement() }
            whenever(aliases.insertAll(any())).thenReturn(Unit)
            whenever(pairs.getAll()).thenReturn(emptyList())
            whenever(exercises.upsert(any())).thenReturn(LOSER_ID)
        }

        val repo = RoomExerciseRepository(RoomStore(db))
        runBlocking {
            repo.refresh()
            repo.recordAlias(LOSER_ID, ALIAS)
            assertEquals(listOf(LOSER_ID), repo.getPredictive(ALIAS, limit = 5).map { it.id })

            val merged = repo.mergeExercises(KEEPER_ID, LOSER_ID, KEEPER_ID, alias = null)
            assertEquals(ExerciseMergeResult.Success(KEEPER_ID), merged)
            assertEquals(listOf(KEEPER_ID), repo.getPredictive(ALIAS, limit = 5).map { it.id })

            repo.recordAlias(KEEPER_ID, ALIAS)
            val key = ExerciseMatcher.matchKey(ALIAS)
            val sameKey = repo.observeAliases().value.filter { it.normalizedKey == key }
            assertEquals(1, sameKey.size)
            assertEquals(KEEPER_ID, sameKey.single().exerciseId)

            val loserNameKey = ExerciseMatcher.matchKey(LOSER_NAME)
            val loserName = repo.observeAliases().value.filter { it.normalizedKey == loserNameKey }
            assertEquals(KEEPER_ID, loserName.single().exerciseId)
        }
    }

    private companion object {
        const val KEEPER_ID = 1L
        const val LOSER_ID = 2L
        const val KEEPER_NAME = "Cable Fly"
        const val LOSER_NAME = "Chest Squeeze"
        const val ALIAS = "Pec Deck"
    }
}
