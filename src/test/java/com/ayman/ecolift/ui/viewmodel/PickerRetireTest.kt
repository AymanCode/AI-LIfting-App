package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.DayAttribution
import com.ayman.ecolift.data.splits.LearnedGroup
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.WorkoutRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2026-10-10: the empty-day picker's X retires a split (the Splits tab's Retire). The split leaves
 * the picker at once; a failed write puts it back and says so.
 */
class PickerRetireTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun retiring_hides_the_split_at_once_and_writes_suppressed_at() = runVmTest {
        val suppressed = CompletableDeferred<Long>()
        val viewModel = viewModelOver { base ->
            object : WorkoutRepository by base {
                override suspend fun suppressCycleSlot(slotId: Long) { suppressed.complete(slotId) }
            }
        }
        val programs = backgroundScope.launch { viewModel.clipboardPrograms.collect {} }
        settle()
        assertEquals(listOf(PUSH_SLOT, UPPER_SLOT), viewModel.clipboardPrograms.value.map { it.slotId }.sorted())

        viewModel.retireSplit(UPPER_SLOT)
        settle()

        assertEquals(UPPER_SLOT, withTimeout(2_000) { suppressed.await() })
        assertEquals(listOf(PUSH_SLOT), viewModel.clipboardPrograms.value.map { it.slotId })
        programs.cancel()
    }

    @Test
    fun a_failed_retire_puts_the_split_back_and_says_so() = runVmTest {
        val viewModel = viewModelOver { base ->
            object : WorkoutRepository by base {
                override suspend fun suppressCycleSlot(slotId: Long) = error("network down")
            }
        }
        val programs = backgroundScope.launch { viewModel.clipboardPrograms.collect {} }
        settle()

        val failure = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        viewModel.retireSplit(UPPER_SLOT)
        assertEquals("Couldn't retire the split. Try again.", failure.await().message)
        settle()

        assertTrue(viewModel.clipboardPrograms.value.any { it.slotId == UPPER_SLOT }, "The split is back in the picker")
        programs.cancel()
    }

    /** Lets the view model's Main-dispatched collectors run (they are not on the test scheduler). */
    private suspend fun settle() = withContext(Dispatchers.Default) { delay(150) }

    private fun viewModelOver(workout: (WorkoutRepository) -> WorkoutRepository): LogViewModel {
        val rows = listOf(
            WorkoutSet(id = 1, syncId = "s1", exerciseId = BENCH_ID, date = LAST_TIME, setNumber = 1, weightLbs = 1350, reps = 5, completed = true),
        )
        val seed = DemoSeedData(
            anchorDate = LocalDate.parse(TODAY),
            exercises = listOf(Exercise(id = BENCH_ID, name = "Bench Press", muscleGroups = "CHEST")),
            sets = rows,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 2, isActive = false),
            slots = listOf(CycleSlot(id = PUSH_SLOT, name = "Push", orderIndex = 0), CycleSlot(id = UPPER_SLOT, name = "Upper", orderIndex = 1)),
            splitExercises = emptyList(),
            workoutDays = listOf(WorkoutDay(date = TODAY)),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(rows),
        )
        val base = DemoRepos(seed)
        val repos: AppRepos = object : AppRepos by base {
            override val workout: WorkoutRepository = workout(base.workout)
            override val learnedSplits: LearnedSplitsSource = object : LearnedSplitsSource {
                override val model: StateFlow<LearnedModel> = MutableStateFlow(MODEL)
            }
        }
        return LogViewModel(repos, isOnline = { false })
    }

    private companion object {
        const val BENCH_ID = 7L
        const val PUSH_SLOT = 1L
        const val UPPER_SLOT = 2L
        const val LAST_TIME = "2026-09-14"
        const val TODAY = "2026-09-21"

        val PUSH = LearnedGroup("push", PUSH_SLOT, "Push", 0, listOf(BENCH_ID), listOf(BENCH_ID), emptyList(),
            listOf(LAST_TIME), emptySet(), false, true, 0, false, false, false)
        // A hand-made split with no lifts in it, like the one that prompted the X.
        val UPPER = LearnedGroup("upper", UPPER_SLOT, "Upper", 1, emptyList(), emptyList(), emptyList(),
            emptyList(), emptySet(), false, false, 0, false, false, false)
        val MODEL = LearnedModel.EMPTY.copy(
            asOf = TODAY, memoryStart = "2026-09-01", groups = listOf(PUSH, UPPER), dueOrder = listOf("upper", "push"),
            dueGroupKey = "upper", attribution = mapOf(LAST_TIME to DayAttribution(LAST_TIME, "push", emptyList())),
        )
    }
}
