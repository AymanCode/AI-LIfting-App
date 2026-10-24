package com.ayman.ecolift.ui.viewmodel

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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun viewModel(book: SessionBook): CardioViewModel {
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 6, 21)))
        val repos = object : AppRepos by base {
            override val cardio: CardioRepository = book.repository(base.cardio)
        }
        return CardioViewModel(repos, DemoAppServices().vision)
    }
}

private class SessionBook(original: CardioSession) {
    val rows = mutableListOf(original)
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
