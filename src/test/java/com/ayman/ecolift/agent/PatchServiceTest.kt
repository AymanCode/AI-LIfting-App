package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.AuditEntity
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.AuditDao
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.patches.noOpTransactionRunner
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseDao
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutDayDao
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.WorkoutSetDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for PatchService using Mockito mocks + noOpTransactionRunner.
 *
 * Covers: validation gating, destructive-op confirmation, patch routing,
 * inverse capture in audit, undo wiring.
 *
 * Real Room round-trip tests live in androidTest/PatchServiceIntegrationTest.
 */
class PatchServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var setDao: WorkoutSetDao
    private lateinit var dayDao: WorkoutDayDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var auditDao: AuditDao
    private lateinit var service: PatchService

    private val stubSet = WorkoutSet(
        id = 1L, exerciseId = 10L, date = "2026-04-16",
        setNumber = 1, weightLbs = 135, reps = 8
    )
    private val stubExercise = Exercise(id = 10L, name = "Bench Press")
    private val stubDay = WorkoutDay(date = "2026-04-15")

    @Before
    fun setUp() {
        db = mock()
        setDao = mock()
        dayDao = mock()
        exerciseDao = mock()
        auditDao = mock()

        whenever(db.workoutSetDao()).thenReturn(setDao)
        whenever(db.workoutDayDao()).thenReturn(dayDao)
        whenever(db.exerciseDao()).thenReturn(exerciseDao)
        whenever(db.auditDao()).thenReturn(auditDao)

        service = PatchService(db, PatchValidator(), noOpTransactionRunner())
    }

    // ── Validation rejection ─────────────────────────────────────────

    @Test
    fun `invalid patch rejected, no DB calls`() = runTest {
        val patch = DbPatch.LogSet(
            exerciseId = -1, date = "2026-04-16", setNumber = 1,
            weightLbs = 135, reps = 8
        )
        val result = service.applyPatches("req-1", listOf(patch), false)
        assertTrue(result is PatchResult.Rejected)
        verifyNoInteractions(setDao, dayDao, exerciseDao, auditDao)
    }

    @Test
    fun `empty patch list succeeds with zero count`() = runTest {
        whenever(auditDao.insert(any())).thenReturn(1L)
        val result = service.applyPatches("req-empty", emptyList(), false)
        assertTrue(result is PatchResult.Applied)
        assertEquals(0, (result as PatchResult.Applied).patchCount)
    }

    // ── Destructive gating ───────────────────────────────────────────

    @Test
    fun `DeleteSet without confirmation rejected before any DAO call`() = runTest {
        val result = service.applyPatches(
            "req-2", listOf(DbPatch.DeleteSet(setId = 1L)), userConfirmed = false
        )
        assertTrue(result is PatchResult.Rejected)
        verifyNoInteractions(setDao)
    }

    @Test
    fun `RenameExercise without confirmation rejected before any DAO call`() = runTest {
        val result = service.applyPatches(
            "req-3",
            listOf(DbPatch.RenameExercise(exerciseId = 1L, newName = "Squat")),
            userConfirmed = false
        )
        assertTrue(result is PatchResult.Rejected)
        verifyNoInteractions(exerciseDao)
    }

    // ── LogSet ───────────────────────────────────────────────────────

    @Test
    fun `LogSet calls insert and writes audit`() = runTest {
        whenever(setDao.insert(any())).thenReturn(42L)
        whenever(auditDao.insert(any())).thenReturn(1L)

        val result = service.applyPatches(
            "req-4",
            listOf(DbPatch.LogSet(exerciseId = 10L, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8)),
            false
        )

        assertTrue(result is PatchResult.Applied)
        verify(setDao).insert(any())
        verify(auditDao).insert(any())
    }

    @Test
    fun `LogSet inverse stored as DeleteSet with inserted ID`() = runTest {
        whenever(setDao.insert(any())).thenReturn(99L)
        whenever(auditDao.insert(any())).thenAnswer { inv ->
            val audit = inv.getArgument<AuditEntity>(0)
            assertTrue("Expected DeleteSet in inverse", audit.serializedInverse.contains("DeleteSet"))
            assertTrue("Expected setId:99 in inverse", audit.serializedInverse.contains("\"setId\":99"))
            1L
        }

        service.applyPatches(
            "req-5",
            listOf(DbPatch.LogSet(exerciseId = 10L, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8)),
            false
        )
        verify(auditDao).insert(any())
    }

    // ── EditSet ──────────────────────────────────────────────────────

    @Test
    fun `EditSet reads pre-state before update, inverse contains original values`() = runTest {
        whenever(setDao.getById(1L)).thenReturn(stubSet)
        whenever(auditDao.insert(any())).thenAnswer { inv ->
            val audit = inv.getArgument<AuditEntity>(0)
            assertTrue(audit.serializedInverse.contains("\"weightLbs\":135"))
            assertTrue(audit.serializedInverse.contains("\"reps\":8"))
            1L
        }

        val result = service.applyPatches(
            "req-6",
            listOf(DbPatch.EditSet(setId = 1L, weightLbs = 225, reps = 3)),
            false
        )

        assertTrue(result is PatchResult.Applied)
        // getById called twice: once in InverseComputer (pre-state), once in applyPatch
        verify(setDao, times(2)).getById(1L)
        verify(setDao).update(any())
    }

    // ── DeleteSet ────────────────────────────────────────────────────

    @Test
    fun `DeleteSet with confirmation reads pre-state then deletes`() = runTest {
        whenever(setDao.getById(1L)).thenReturn(stubSet)
        whenever(auditDao.insert(any())).thenAnswer { inv ->
            val audit = inv.getArgument<AuditEntity>(0)
            assertTrue("Inverse should contain LogSet", audit.serializedInverse.contains("LogSet"))
            assertTrue(audit.serializedInverse.contains("\"weightLbs\":135"))
            1L
        }

        val result = service.applyPatches(
            "req-7", listOf(DbPatch.DeleteSet(setId = 1L)), userConfirmed = true
        )

        assertTrue(result is PatchResult.Applied)
        val order = inOrder(setDao)
        order.verify(setDao).getById(1L)
        order.verify(setDao).deleteById(1L)
    }

    // ── MoveWorkoutDay ───────────────────────────────────────────────

    @Test
    fun `MoveWorkoutDay upserts new day, deletes old, updates set dates`() = runTest {
        whenever(dayDao.getByDate("2026-04-15")).thenReturn(stubDay)
        whenever(auditDao.insert(any())).thenReturn(1L)

        val result = service.applyPatches(
            "req-8",
            listOf(DbPatch.MoveWorkoutDay(currentDate = "2026-04-15", newDate = "2026-04-17")),
            false
        )

        assertTrue(result is PatchResult.Applied)
        verify(dayDao).upsert(WorkoutDay(date = "2026-04-17"))
        verify(dayDao).deleteByDate("2026-04-15")
        verify(setDao).updateDate("2026-04-15", "2026-04-17")
    }

    @Test
    fun `MoveWorkoutDay inverse swaps dates`() = runTest {
        whenever(dayDao.getByDate("2026-04-15")).thenReturn(stubDay)
        whenever(auditDao.insert(any())).thenAnswer { inv ->
            val audit = inv.getArgument<AuditEntity>(0)
            assertTrue(audit.serializedInverse.contains("\"currentDate\":\"2026-04-17\""))
            assertTrue(audit.serializedInverse.contains("\"newDate\":\"2026-04-15\""))
            1L
        }

        service.applyPatches(
            "req-9",
            listOf(DbPatch.MoveWorkoutDay(currentDate = "2026-04-15", newDate = "2026-04-17")),
            false
        )
        verify(auditDao).insert(any())
    }

    // ── RenameExercise ───────────────────────────────────────────────

    @Test
    fun `RenameExercise reads old name then renames, inverse restores original`() = runTest {
        whenever(exerciseDao.getById(10L)).thenReturn(stubExercise)
        whenever(auditDao.insert(any())).thenAnswer { inv ->
            val audit = inv.getArgument<AuditEntity>(0)
            assertTrue(audit.serializedInverse.contains("\"newName\":\"Bench Press\""))
            1L
        }

        val result = service.applyPatches(
            "req-10",
            listOf(DbPatch.RenameExercise(exerciseId = 10L, newName = "Flat Bench")),
            userConfirmed = true
        )

        assertTrue(result is PatchResult.Applied)
        // getById called twice: once in InverseComputer (pre-state), once in applyPatch
        verify(exerciseDao, times(2)).getById(10L)
        verify(exerciseDao).updateName(10L, "Flat Bench")
    }

    // ── Audit ─────────────────────────────────────────────────────────

    @Test
    fun `audit row has correct requestId and is not marked as undo`() = runTest {
        whenever(setDao.insert(any())).thenReturn(1L)
        whenever(auditDao.insert(any())).thenAnswer { inv ->
            val audit = inv.getArgument<AuditEntity>(0)
            assertEquals("req-11", audit.requestId)
            assertFalse(audit.isUndo)
            assertTrue(audit.serializedPatches.contains("LogSet"))
            1L
        }

        service.applyPatches(
            "req-11",
            listOf(DbPatch.LogSet(exerciseId = 10L, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8)),
            false
        )
        verify(auditDao).insert(any())
    }

    // ── Undo ──────────────────────────────────────────────────────────

    @Test
    fun `undo returns Failed when audit not found`() = runTest {
        whenever(auditDao.getById(999L)).thenReturn(null)
        val result = service.undo(999L)
        assertTrue(result is PatchResult.Failed)
    }

    @Test
    fun `undo applies inverse patches from audit entry`() = runTest {
        val inverseJson = """[{"type":"com.ayman.ecolift.agent.model.DbPatch.DeleteSet","setId":42}]"""
        val auditEntry = AuditEntity(
            id = 1L, requestId = "req-12", timestamp = 0L,
            serializedPatches = "[]",
            serializedInverse = inverseJson,
            userConfirmed = false
        )
        whenever(auditDao.getById(1L)).thenReturn(auditEntry)
        whenever(setDao.getById(42L)).thenReturn(stubSet.copy(id = 42L))
        whenever(auditDao.insert(any())).thenReturn(2L)

        val result = service.undo(1L)
        assertTrue(result is PatchResult.Applied)
        verify(setDao).deleteById(42L)
    }

    // ── Exception handling ────────────────────────────────────────────

    @Test
    fun `exception inside transaction returns Failed`() = runTest {
        whenever(setDao.getById(any())).thenReturn(null) // will cause error("not found")

        val result = service.applyPatches(
            "req-13",
            listOf(DbPatch.EditSet(setId = 999L, reps = 5)),
            false
        )
        assertTrue(result is PatchResult.Failed)
    }
}
