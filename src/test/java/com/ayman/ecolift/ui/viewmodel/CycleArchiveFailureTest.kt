package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.WorkoutRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CycleArchiveFailureTest {
    @Test
    fun a_failed_delete_or_archive_says_so() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 7, 5)))
        val failing = object : WorkoutRepository by base.workout {
            override suspend fun deleteArchivedCycle(id: Long) = error("network down")
            override suspend fun archiveCurrentCycle(name: String, startDate: String, endDate: String): Long = error("network down")
        }
        val viewModel = CycleArchiveViewModel(object : AppRepos by base { override val workout: WorkoutRepository = failing })

        val delete = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        viewModel.deleteArchive(1L)
        assertEquals("Couldn't delete the archive.", delete.await().message)

        var archived = false
        val archive = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        viewModel.archiveCurrentCycle("Block 1", "2026-06-01", "2026-07-05", onArchived = { archived = true })
        assertEquals("Couldn't archive the cycle.", archive.await().message)
        assertEquals(false, archived, "onArchived must not run when the archive did not land")
    }
}
