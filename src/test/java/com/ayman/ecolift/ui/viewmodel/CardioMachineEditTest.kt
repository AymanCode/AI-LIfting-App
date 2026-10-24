package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.session
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoAppServices
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.CardioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioMachineEditTest {
    @Test
    fun changing_machine_during_an_edit_updates_the_original_row() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val original = session(
            "2026-10-02",
            type = CardioActivityType.TREADMILL,
            minutes = 30,
            metres = 3218.688,
            id = 42L,
        )
        val book = SessionBook(original)
        val viewModel = viewModel(book)

        viewModel.startEditDraft(42L)
        testScheduler.advanceUntilIdle()
        viewModel.selectMachine(CardioActivityType.BIKE)
        viewModel.save()
        testScheduler.advanceUntilIdle()

        assertTrue(book.inserts.isEmpty(), "machine change inserted ${book.inserts}")
        assertEquals(listOf(42L), book.rows.map { it.id })
        assertEquals(CardioActivityType.BIKE.name, book.rows.single().activityType)
    }

    @Test
    fun photo_edit_keeps_2000_metres_when_the_machine_changes() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val original = session(
            "2026-10-02",
            type = CardioActivityType.ROW,
            minutes = 20,
            metres = 2000.0,
            id = 7L,
        ).copy(source = CardioSessionSource.OCR)
        val book = SessionBook(original)
        val viewModel = viewModel(book)

        viewModel.startEditDraft(7L)
        testScheduler.advanceUntilIdle()
        viewModel.selectMachine(CardioActivityType.TREADMILL)
        viewModel.save()
        testScheduler.advanceUntilIdle()

        assertTrue(book.inserts.isEmpty(), "photo machine change inserted ${book.inserts}")
        assertEquals(1, book.rows.size)
        assertEquals(7L, book.rows.single().id)
        assertEquals(2000.0, book.rows.single().distanceM!!, 1e-6)
    }

    @Test
    fun editing_keeps_time_and_distance_when_a_newer_session_exists() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val book = bookWithNewerSession()
        val viewModel = viewModel(book)

        viewModel.startEditDraft(42L)
        testScheduler.advanceUntilIdle()
        viewModel.selectMachine(CardioActivityType.BIKE)
        viewModel.save()
        testScheduler.advanceUntilIdle()

        val saved = book.rows.single { it.id == 42L }
        assertTrue(book.inserts.isEmpty(), "machine change inserted ${book.inserts}")
        assertEquals(CardioActivityType.BIKE.name, saved.activityType)
        assertEquals(1800, saved.durationSec)
        assertEquals(4828.0, saved.distanceM!!, 1e-6)
        assertEquals("hill repeats", saved.notes)
        assertEquals(1200, book.rows.single { it.id == 7L }.durationSec)
    }

    @Test
    fun switching_a_rower_edit_to_stairs_and_back_keeps_2000_metres() = runVmTest {
        assertAwayAndBackKeepsDistance(photo = false)
    }

    @Test
    fun switching_a_photo_rower_edit_to_stairs_and_back_keeps_the_typed_distance() = runVmTest {
        assertAwayAndBackKeepsDistance(photo = true, typedMetres = "2500")
    }

    private suspend fun TestScope.assertAwayAndBackKeepsDistance(photo: Boolean, typedMetres: String? = null) {
        CurrentAppMode.mode = AppMode.Authenticated
        val original = session(
            "2026-10-02",
            type = CardioActivityType.ROW,
            minutes = 20,
            metres = 2000.0,
            id = 7L,
        ).let { row -> if (photo) row.copy(source = CardioSessionSource.OCR) else row }
        val book = SessionBook(original)
        val viewModel = viewModel(book)

        viewModel.startEditDraft(7L)
        testScheduler.advanceUntilIdle()
        if (typedMetres != null) {
            viewModel.commitTyped(CardioEntryField.DISTANCE_MILES, typedMetres)
        }
        viewModel.selectMachine(CardioActivityType.STAIR_CLIMBER)
        viewModel.selectMachine(CardioActivityType.ROW)
        val shown = viewModel.uiState.value.draft?.values?.get(CardioEntryField.DISTANCE_MILES)
        viewModel.save()
        testScheduler.advanceUntilIdle()

        val expected = typedMetres?.toDouble() ?: 2000.0
        assertEquals(expected, shown ?: Double.NaN, 1e-6)
        assertTrue(book.inserts.isEmpty(), "away and back inserted ${book.inserts}")
        val saved = book.rows.single()
        assertEquals(7L, saved.id)
        assertEquals(CardioActivityType.ROW.name, saved.activityType)
        assertEquals(1200, saved.durationSec)
        assertEquals(expected, saved.distanceM ?: Double.NaN, 1e-6)
    }

    @Test
    fun an_out_of_range_rower_distance_does_not_block_save_on_stairs() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val original = session(
            "2026-10-02",
            type = CardioActivityType.ROW,
            minutes = 20,
            metres = 2000.0,
            id = 7L,
        )
        val book = SessionBook(original)
        val viewModel = viewModel(book)

        viewModel.startEditDraft(7L)
        testScheduler.advanceUntilIdle()
        viewModel.commitTyped(CardioEntryField.DISTANCE_MILES, "10")
        viewModel.selectMachine(CardioActivityType.STAIR_CLIMBER)
        val draft = viewModel.uiState.value.draft
        assertTrue(draft != null && CardioDraftLogic.canSave(draft), "hidden distance still blocks save: $draft")
        viewModel.save()
        testScheduler.advanceUntilIdle()

        val saved = book.rows.single()
        assertEquals(7L, saved.id)
        assertEquals(CardioActivityType.STAIR_CLIMBER.name, saved.activityType)
    }

    @Test
    fun an_invalid_rower_distance_comes_back_as_the_stored_2000() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val original = session(
            "2026-10-02",
            type = CardioActivityType.ROW,
            minutes = 20,
            metres = 2000.0,
            id = 7L,
        )
        val book = SessionBook(original)
        val viewModel = viewModel(book)

        viewModel.startEditDraft(7L)
        testScheduler.advanceUntilIdle()
        viewModel.commitTyped(CardioEntryField.DISTANCE_MILES, "10")
        viewModel.selectMachine(CardioActivityType.STAIR_CLIMBER)
        viewModel.selectMachine(CardioActivityType.ROW)
        val draft = viewModel.uiState.value.draft
        val shown = draft?.values?.get(CardioEntryField.DISTANCE_MILES)
        assertEquals(2000.0, shown ?: Double.NaN, 1e-6)
        assertTrue(draft != null && CardioEntryField.DISTANCE_MILES !in draft.invalid, "distance still invalid: $draft")
    }

    @Test
    fun a_typed_distance_wins_after_it_is_cleared() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val original = session(
            "2026-10-02",
            type = CardioActivityType.ROW,
            minutes = 20,
            metres = 2000.0,
            id = 7L,
        )
        val book = SessionBook(original)
        val viewModel = viewModel(book)

        viewModel.startEditDraft(7L)
        testScheduler.advanceUntilIdle()
        viewModel.selectMachine(CardioActivityType.STAIR_CLIMBER)
        viewModel.commitTyped(CardioEntryField.DISTANCE_MILES, "4.00")
        viewModel.selectMachine(CardioActivityType.ROW)
        viewModel.clearField(CardioEntryField.DISTANCE_MILES)
        viewModel.selectMachine(CardioActivityType.STAIR_CLIMBER)
        viewModel.selectMachine(CardioActivityType.ROW)

        assertNull(viewModel.uiState.value.draft?.values?.get(CardioEntryField.DISTANCE_MILES))
    }

    @Test
    fun a_cached_bike_draft_does_not_leak_into_an_edit() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val book = bookWithNewerSession()
        val viewModel = viewModel(book)
        testScheduler.advanceUntilIdle()

        viewModel.startNewDraft()
        viewModel.selectMachine(CardioActivityType.BIKE)
        viewModel.commitTyped(CardioEntryField.DURATION, "45:00")
        viewModel.commitTyped(CardioEntryField.DISTANCE_MILES, "4.00")
        viewModel.setNotes("parked bike")
        viewModel.selectMachine(CardioActivityType.TREADMILL)

        viewModel.startEditDraft(42L)
        testScheduler.advanceUntilIdle()
        parkBikeDraft(viewModel)
        viewModel.selectMachine(CardioActivityType.BIKE)
        viewModel.save()
        testScheduler.advanceUntilIdle()

        val saved = book.rows.single { it.id == 42L }
        assertTrue(book.inserts.isEmpty(), "machine change inserted ${book.inserts}")
        assertEquals(42L, saved.id)
        assertEquals(1800, saved.durationSec)
        assertEquals(4828.0, saved.distanceM!!, 1e-6)
        assertEquals("hill repeats", saved.notes)
        assertNull(saved.avgInclinePercent)

        viewModel.startNewDraft()
        if (viewModel.uiState.value.draft?.type != CardioActivityType.BIKE) {
            viewModel.selectMachine(CardioActivityType.BIKE)
        }
        assertNull(viewModel.uiState.value.draft?.editingId)
    }

    private fun bookWithNewerSession(): SessionBook {
        val newer = session(
            "2026-10-20",
            type = CardioActivityType.ELLIPTICAL,
            minutes = 20,
            metres = 1600.0,
            id = 7L,
            createdAt = 20L,
        )
        val edited = session(
            "2026-10-02",
            type = CardioActivityType.TREADMILL,
            minutes = 30,
            metres = 4828.0,
            id = 42L,
            createdAt = 1L,
        ).copy(notes = "hill repeats", avgInclinePercent = 1.5)
        return SessionBook(newer, edited)
    }

    private fun parkBikeDraft(viewModel: CardioViewModel) {
        val parked = CardioDraft(
            type = CardioActivityType.BIKE,
            values = mapOf(
                CardioEntryField.DURATION to 2700.0,
                CardioEntryField.DISTANCE_MILES to 4.0,
            ),
            notes = "parked bike",
        )
        val field = CardioViewModel::class.java.getDeclaredField("draftsByMachine")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val drafts = field.get(viewModel) as MutableMap<CardioActivityType, CardioDraft>
        drafts[CardioActivityType.BIKE] = parked
    }

    private fun viewModel(book: SessionBook): CardioViewModel {
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 6, 21)))
        val repos = object : AppRepos by base {
            override val cardio: CardioRepository = book.repository(base.cardio)
        }
        return CardioViewModel(repos, DemoAppServices().vision)
    }
}

private class SessionBook(vararg originals: CardioSession) {
    val rows = originals.toMutableList()
    val inserts = mutableListOf<String>()
    private val published = MutableStateFlow(rows.toList())

    fun repository(delegate: CardioRepository): CardioRepository = object : CardioRepository by delegate {
        override val sessions = published

        override suspend fun getById(id: Long): CardioSession? = rows.firstOrNull { it.id == id }

        override suspend fun update(session: CardioSession): CardioSession {
            val index = rows.indexOfFirst { it.id == session.id }
            if (index >= 0) rows[index] = session else rows += session
            published.value = rows.toList()
            return session
        }

        override suspend fun saveManual(
            date: String,
            activityType: String,
            activityLabel: String?,
            durationSec: Int?,
            distanceM: Double?,
            calories: Int?,
            avgHeartRate: Int?,
            maxHeartRate: Int?,
            avgSpeed: Double?,
            avgInclinePercent: Double?,
            cadenceRpm: Int?,
            resistanceLevel: Double?,
            avgPowerWatts: Int?,
            strokeRateSpm: Int?,
            paceSecPer500m: Int?,
            floors: Int?,
            steps: Int?,
            notes: String,
        ): CardioSession {
            inserts += "manual"
            return keep(activityType, durationSec, distanceM, notes)
        }

        override suspend fun saveOcrConfirmed(
            date: String,
            activityType: String,
            activityLabel: String?,
            durationSec: Int?,
            distanceM: Double?,
            calories: Int?,
            avgHeartRate: Int?,
            maxHeartRate: Int?,
            avgSpeed: Double?,
            avgInclinePercent: Double?,
            cadenceRpm: Int?,
            resistanceLevel: Double?,
            avgPowerWatts: Int?,
            strokeRateSpm: Int?,
            paceSecPer500m: Int?,
            floors: Int?,
            steps: Int?,
            machineType: String?,
            ocrConfidence: Double?,
            notes: String,
        ): CardioSession {
            inserts += "ocr"
            return keep(activityType, durationSec, distanceM, notes)
        }

        private fun keep(activityType: String, durationSec: Int?, distanceM: Double?, notes: String): CardioSession {
            val created = rows.first().copy(
                id = 900L + inserts.size,
                activityType = activityType,
                durationSec = durationSec,
                distanceM = distanceM,
                notes = notes,
            )
            rows += created
            published.value = rows.toList()
            return created
        }
    }
}
