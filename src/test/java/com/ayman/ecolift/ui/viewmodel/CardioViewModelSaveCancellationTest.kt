package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoAppServices
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.CardioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CardioViewModelSaveCancellationTest {
    @Test
    fun cancellation_does_not_set_failed_save_state() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val baseRepos = DemoRepos(DemoData.generate(LocalDate(2026, 6, 21)))
        val throwingCardioRepo = object : CardioRepository by baseRepos.cardio {
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
                throw CancellationException("Cancelled")
            }
        }
        val appRepos = object : com.ayman.ecolift.web.AppRepos by baseRepos {
            override val cardio: CardioRepository = throwingCardioRepo
        }
        val viewModel = CardioViewModel(appRepos, DemoAppServices().vision)
        viewModel.startNewDraft()
        viewModel.save()
        testScheduler.advanceUntilIdle()

        assertNotEquals(SaveState.FAILED, viewModel.uiState.value.saveState)
        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun local_exception_sets_failed_save_state() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val baseRepos = DemoRepos(DemoData.generate(LocalDate(2026, 6, 21)))
        val throwingCardioRepo = object : CardioRepository by baseRepos.cardio {
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
                throw IllegalStateException("Local error")
            }
        }
        val appRepos = object : com.ayman.ecolift.web.AppRepos by baseRepos {
            override val cardio: CardioRepository = throwingCardioRepo
        }
        val viewModel = CardioViewModel(appRepos, DemoAppServices().vision)
        viewModel.startNewDraft()
        viewModel.save()
        testScheduler.advanceUntilIdle()

        assertEquals(SaveState.FAILED, viewModel.uiState.value.saveState)
        assertEquals("Couldn't save.", viewModel.uiState.value.saveError)
    }
}
