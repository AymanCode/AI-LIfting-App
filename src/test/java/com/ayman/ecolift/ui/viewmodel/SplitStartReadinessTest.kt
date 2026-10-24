package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.SplitSwitchIntent
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SplitStartReadinessTest {
    @Before
    fun authenticated_local_test() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @After
    fun restore_mode() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun manual_start_waits_for_the_slot_projection_after_the_board_is_ready() = checkStart(gateSlots = true)

    @Test
    fun automatic_start_waits_for_the_anchor_projection_after_the_board_read_is_ready() = checkStart(gateSlots = false)

    private fun checkStart(gateSlots: Boolean) = runVmTest {
        val gate = CompletableDeferred<Unit>()
        val slots = listOf(CycleSlot(id = 5, name = "SP Manual", orderIndex = 0))
        val members = listOf(
            SplitExercise(id = 1, splitId = 5, exerciseId = 1, orderIndex = 0),
            SplitExercise(id = 2, splitId = 5, exerciseId = 2, orderIndex = 1),
        )
        val seed = DemoSeedData(
            anchorDate = LocalDate(2026, 10, 23),
            exercises = listOf(Exercise(id = 1, name = "Sp Gamma"), Exercise(id = 2, name = "Sp Alpha")),
            sets = emptyList(),
            settings = UserSettings(smartSuggest = false),
            cycle = Cycle(numTypes = 1, isActive = true, startDate = "2026-10-23"),
            slots = slots,
            splitExercises = members,
            workoutDays = emptyList(),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = emptyMap(),
        )
        val base = DemoRepos(seed)
        var written: List<WorkoutSet> = emptyList()
        val choice = SplitMemberChoice(5, 1, EffectiveSplitMembers.of(listOf(1, 2), emptyList()))
        val repository = object : WorkoutRepository by base.workout {
            override val splitMemberChoices = MutableStateFlow<SplitChoicesState>(
                SplitChoicesState.Ready(if (gateSlots) mapOf(5L to choice) else emptyMap(), confirmed = emptyMap()),
            )
            override fun observeCycleSlots() = flow {
                if (gateSlots) gate.await()
                emit(slots)
            }
            override fun observeAllSplitExercises() = flow {
                if (!gateSlots) gate.await()
                emit(members)
            }
        }
        val replacement = SplitReplacementCoordinator(
            journal = object : SplitReplacementJournal {
                override fun load() = Result.success(emptyList<PreparedSplitReplacement>())
                override fun save(replacement: PreparedSplitReplacement) = Result.success(Unit)
                override fun clear(date: String) = Result.success(Unit)
            },
            replaceSetDayBatch = { _, rows -> written = rows; LocalCommit.Committed(Unit) },
            enqueueDayBatch = {},
            withDrainLock = { it() },
            kickEngine = {},
        )
        val repos = object : AppRepos by base {
            override val workout = repository
            override val learnedSplits = object : LearnedSplitsSource {
                override val model = MutableStateFlow(LearnedModel.EMPTY.copy(smartSuggest = false))
            }
            override val splitReplacement = replacement
        }
        try {
            val viewModel = LogViewModel(repos, derivationContext = Dispatchers.Main.immediate)
            viewModel.selectDate("2026-10-23")
            val outcome = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000) { viewModel.splitSwitchOutcomes.first { it is SplitSwitchOutcome.Applied } }
            }
            val start = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.awaitSplitChoices(5)
                viewModel.loadProgram(5, emptySet(), SplitSwitchIntent.DeepLinkLoad)
            }
            assertFalse(start.isCompleted, "A ready board must not consume Start before its slot and anchors have loaded")
            gate.complete(Unit)
            val requestId = start.await()
            val applied = assertIs<SplitSwitchOutcome.Applied>(outcome.await())
            assertEquals(requestId, applied.requestId)
            assertEquals("2026-10-23", applied.date)
            assertEquals(listOf(1L, 2L), written.map { it.exerciseId })
        } finally {
            gate.complete(Unit)
            base.close()
        }
    }
}
