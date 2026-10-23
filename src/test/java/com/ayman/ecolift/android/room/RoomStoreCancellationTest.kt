package com.ayman.ecolift.android.room

import com.ayman.ecolift.agent.model.AuditEntity
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.WorkoutDayStateDao
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.WorkoutSetDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import com.ayman.ecolift.data.room.WorkoutSet as RoomSet

class RoomStoreCancellationTest {
    @Test
    fun cancellation_after_commit_keeps_database_memory_and_audit_together() = cancellationCase(false)

    @Test
    fun cancellation_during_preparation_rolls_back_database_memory_and_audit() = cancellationCase(true)

    private fun cancellationCase(duringPreparation: Boolean) = runTest {
        val db = mock<AppDatabase>()
        val dao = mock<WorkoutSetDao>()
        val days = mock<WorkoutDayStateDao>()
        val staged = mutableListOf<RoomSet>()
        var stored = emptyList<RoomSet>()
        var successful = false
        val audit = InMemoryAuditStore()
        lateinit var caller: Job
        whenever(db.transactionExecutor).thenReturn(Executor { it.run() })
        whenever(db.suspendingTransactionId).thenReturn(ThreadLocal())
        whenever(db.workoutSetDao()).thenReturn(dao)
        whenever(db.workoutDayStateDao()).thenReturn(days)
        whenever(dao.insert(any())).thenAnswer { invocation ->
            staged += invocation.getArgument<RoomSet>(0).copy(id = 1)
            if (duringPreparation) caller.cancel()
            1L
        }
        whenever(dao.getAll()).thenAnswer { stored }
        doAnswer { successful = true; null }.whenever(db).setTransactionSuccessful()
        doAnswer {
            if (successful) stored = staged.toList()
            if (!duringPreparation) caller.cancel()
            null
        }.whenever(db).endTransaction()
        val store = RoomStore(db)
        caller = launch(StandardTestDispatcher(testScheduler)) {
            store.write {
                insertSet(WorkoutSet(exerciseId = 1, date = "2026-10-23", setNumber = 1, weightLbs = 1350, reps = 8))
                afterCommit += {
                    audit.insert(AuditEntity(requestId = "committed", timestamp = 1, serializedPatches = "[]", serializedInverse = "[]", userConfirmed = true))
                }
            }
        }
        caller.join()
        assertTrue(caller.isCancelled)
        val weights = if (duringPreparation) emptyList() else listOf(1350)
        assertEquals(weights, dao.getAll().map { it.weightLbs })
        assertEquals(weights, store.sets.value.map { it.weightLbs })
        assertEquals(if (duringPreparation) emptyList() else listOf("committed"), audit.all().map { it.requestId })
    }
}
