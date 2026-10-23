package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.cardio.session
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.DataGeneration
import com.ayman.ecolift.web.VisionService
import com.ayman.ecolift.web.ai.VisionStats
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.CardioRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioRestoreGenerationTest {
    private val vision = object : VisionService {
        override suspend fun scanStats(b64: String, mime: String): VisionStats? = null
        override suspend fun scansLeftToday(): Int? = null
    }

    private inner class Harness(stored: CardioSession) {
        val generation = DataGeneration()
        var updates = 0
        var deletes = 0
        private val base = DemoRepos(DemoData.generate(LocalDate(2026, 10, 23)).copy(cardio = listOf(stored)))
        private val cardio = object : CardioRepository by base.cardio {
            override suspend fun update(session: CardioSession): CardioSession {
                updates += 1
                return session
            }

            override suspend fun delete(id: Long) {
                deletes += 1
            }
        }
        val repos = object : AppRepos by base {
            override val dataGeneration = generation
            override val cardio: CardioRepository = this@Harness.cardio
        }
        val viewModel = CardioViewModel(repos, vision, photoPicker = {})
    }

    @Test
    fun an_open_edit_is_dropped_on_restore_and_save_writes_nothing() = runVmTest {
        val stored = session("2026-10-23", minutes = 20, id = 7).copy(notes = "restored")
        val harness = Harness(stored)
        harness.viewModel.startEditDraft(7)
        testScheduler.advanceUntilIdle()
        harness.viewModel.setNotes("stale edit")
        harness.generation.replace { }
        testScheduler.advanceUntilIdle()
        assertNull(harness.viewModel.uiState.value.draft)
        assertNull(harness.viewModel.uiState.value.toast)
        assertNull(harness.viewModel.uiState.value.lastSavedId)
        assertNull(harness.viewModel.uiState.value.selectedSessionId)
        assertNull(harness.viewModel.uiState.value.daySheetDate)
        harness.viewModel.save()
        testScheduler.advanceUntilIdle()
        assertEquals(0, harness.updates)
        assertEquals("restored", harness.repos.cardio.sessions.value.single { it.id == 7L }.notes)
    }

    @Test
    fun a_save_already_in_flight_writes_nothing_after_restore() = runVmTest {
        val stored = session("2026-10-23", minutes = 20, id = 7).copy(notes = "restored")
        val harness = Harness(stored)
        harness.viewModel.startEditDraft(7)
        testScheduler.advanceUntilIdle()
        harness.viewModel.setNotes("stale edit")
        val release = CompletableDeferred<Unit>()
        val holding = launch { harness.generation.replace { release.await() } }
        testScheduler.advanceUntilIdle()
        harness.viewModel.save()
        testScheduler.advanceUntilIdle()
        release.complete(Unit)
        holding.join()
        testScheduler.advanceUntilIdle()
        assertEquals(0, harness.updates)
        assertEquals("restored", harness.repos.cardio.sessions.value.single { it.id == 7L }.notes)
    }

    @Test
    fun undo_after_restore_does_not_delete_the_restored_session() = runVmTest {
        val stored = session("2026-10-23", minutes = 20, id = 7).copy(notes = "restored")
        val harness = Harness(stored)
        harness.viewModel.startNewDraft()
        harness.viewModel.save()
        testScheduler.advanceUntilIdle()
        assertTrue(harness.viewModel.uiState.value.lastSavedId != null)
        harness.generation.replace { }
        testScheduler.advanceUntilIdle()
        harness.viewModel.undoLastSave()
        testScheduler.advanceUntilIdle()
        assertEquals(0, harness.deletes)
        assertEquals("restored", harness.repos.cardio.sessions.value.single { it.id == 7L }.notes)
    }

    @Test
    fun an_open_scan_dies_when_the_generation_changes() = runVmTest {
        val stored = session("2026-10-23", minutes = 20, id = 7)
        val harness = Harness(stored)
        harness.viewModel.startScan()
        assertTrue(harness.viewModel.uiState.value.scanning)
        harness.generation.replace { }
        testScheduler.advanceUntilIdle()
        assertFalse(harness.viewModel.uiState.value.scanning)
        assertNull(harness.viewModel.uiState.value.draft)
    }

    @Test
    fun a_queued_delete_leaves_the_session_restored_under_the_same_id() = runVmTest {
        val original = session("2026-10-23", minutes = 20, id = 7).copy(notes = "original", localUuid = "uuid-a")
        val restored = original.copy(notes = "restored", localUuid = "uuid-b", durationSec = 2400)
        val generation = DataGeneration()
        val stored = MutableStateFlow(listOf(original))
        val gate = CompletableDeferred<Unit>()
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 10, 23)).copy(cardio = listOf(original)))
        val cardio = object : CardioRepository by base.cardio {
            override suspend fun delete(id: Long) {
                gate.await()
                stored.value = stored.value.filter { it.id != id }
            }
        }
        val repos = object : AppRepos by base {
            override val dataGeneration = generation
            override val cardio: CardioRepository = cardio
        }
        val viewModel = CardioViewModel(repos, vision, photoPicker = {})
        viewModel.deleteSession(7)
        testScheduler.advanceUntilIdle()
        val restoring = launch {
            generation.replace {
                stored.value = listOf(restored)
            }
        }
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)
        restoring.join()
        testScheduler.advanceUntilIdle()
        assertEquals(restored, stored.value.single())
    }
}
