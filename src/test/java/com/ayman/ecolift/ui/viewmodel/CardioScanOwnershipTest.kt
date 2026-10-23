package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.VisionService
import com.ayman.ecolift.web.ai.VisionStats
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.CardioRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioScanOwnershipTest {
    private fun repos() = DemoRepos(DemoData.generate(LocalDate(2026, 10, 23)).copy(cardio = emptyList()))
    private val unreadable = object : VisionService {
        override suspend fun scanStats(b64: String, mime: String): VisionStats? = null
        override suspend fun scansLeftToday(): Int? = null
    }

    @Test
    fun picker_wait_keeps_scan_alive_and_null_vision_shows_the_failure_draft() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        lateinit var result: (Pair<String, String>?) -> Unit
        val vm = CardioViewModel(repos(), unreadable, photoPicker = { result = it })
        vm.startScan()
        assertTrue(vm.uiState.value.scanning)
        assertNull(vm.uiState.value.draft)
        result("image/png" to "photo")
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.scanning)
        assertEquals("Couldn't read the photo.", vm.uiState.value.toast?.text)
        assertEquals(1800.0, vm.uiState.value.draft?.values?.get(CardioEntryField.DURATION))
        assertNull(vm.uiState.value.scanPhotoBase64)
    }

    @Test
    fun cancelled_picker_cannot_overwrite_a_later_manual_draft() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        lateinit var result: (Pair<String, String>?) -> Unit
        val vm = CardioViewModel(repos(), unreadable, photoPicker = { result = it })
        vm.startScan()
        vm.cancelScan()
        vm.startNewDraft()
        vm.commitTyped(CardioEntryField.DURATION, "20:00")
        result("image/png" to "old-photo")
        testScheduler.advanceUntilIdle()
        assertEquals(1200.0, vm.uiState.value.draft?.values?.get(CardioEntryField.DURATION))
        assertFalse(vm.uiState.value.draft!!.fromPhoto)
        assertNull(vm.uiState.value.toast)
        assertNull(vm.uiState.value.scanPhotoBase64)
    }

    @Test
    fun disposed_scan_cannot_clear_a_new_manual_draft() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val vm = CardioViewModel(repos(), unreadable, photoPicker = {})
        vm.startScan()
        vm.startNewDraft()
        vm.commitTyped(CardioEntryField.DURATION, "20:00")
        vm.cancelScan()
        assertEquals(1200.0, vm.uiState.value.draft?.values?.get(CardioEntryField.DURATION))
        assertFalse(vm.uiState.value.scanning)
    }

    @Test
    fun late_vision_result_cannot_replace_a_manual_draft() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val deferred = CompletableDeferred<VisionStats?>()
        val vision = object : VisionService {
            override suspend fun scanStats(b64: String, mime: String): VisionStats? {
                assertEquals("image/png", mime)
                assertEquals("photo", b64)
                return deferred.await()
            }
            override suspend fun scansLeftToday(): Int? = null
        }
        val vm = CardioViewModel(repos(), vision, photoPicker = { it("image/png" to "photo") })
        vm.startScan()
        vm.startNewDraft()
        vm.commitTyped(CardioEntryField.DURATION, "20:00")
        deferred.complete(VisionStats(activityType = "TREADMILL", durationSec = 192, confidence = 0.8))
        testScheduler.advanceUntilIdle()
        assertEquals(1200.0, vm.uiState.value.draft?.values?.get(CardioEntryField.DURATION))
        assertFalse(vm.uiState.value.draft!!.fromPhoto)
        assertNull(vm.uiState.value.scanPhotoBase64)
    }

    @Test
    fun cancelled_picker_exits_without_a_failure_or_draft() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        lateinit var result: (Pair<String, String>?) -> Unit
        val vm = CardioViewModel(repos(), unreadable, photoPicker = { result = it })
        vm.startScan()
        result(null)
        assertFalse(vm.uiState.value.scanning)
        assertNull(vm.uiState.value.draft)
        assertNull(vm.uiState.value.toast)
    }

    @Test
    fun notes_only_save_does_not_claim_activity_day_fill() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val base = repos()
        var saved: CardioSession? = null
        val cardio = object : CardioRepository by base.cardio {
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
            ): CardioSession = base.cardio.saveManual(
                date, activityType, activityLabel, durationSec, distanceM, calories, avgHeartRate, maxHeartRate,
                avgSpeed, avgInclinePercent, cadenceRpm, resistanceLevel, avgPowerWatts, strokeRateSpm, paceSecPer500m,
                floors, steps, notes,
            ).also { saved = it }
        }
        val repo = object : AppRepos by base { override val cardio = cardio }
        val vm = CardioViewModel(repo, unreadable)
        vm.startNewDraft()
        vm.clearField(CardioEntryField.DURATION)
        vm.setNotes("Machine was unavailable")
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Machine was unavailable", saved?.notes)
        assertNull(saved?.durationSec)
        assertEquals("Saved.", vm.uiState.value.toast?.text)
        assertNull(vm.uiState.value.toast?.emphasis)
    }
}
