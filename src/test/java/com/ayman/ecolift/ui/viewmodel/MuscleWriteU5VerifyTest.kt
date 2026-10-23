package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.simpleNameKey
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.NotCommittedException
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Adversarial checks of stream B/F at the Log screen: a muscle write must not drop a new lift or show a raw code. */
class MuscleWriteU5VerifyTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun a_typed_new_lift_still_adds_when_its_inferred_muscle_write_is_refused() = runVmTest {
        val harness = Harness()
        harness.exercises.muscleFailure = { NotCommittedException(NotCommittedReason.StorageUnavailable) }
        harness.showDate()

        harness.viewModel.addExerciseFromInput("Nordic Curl")

        val state = harness.settle("Nordic Curl")
        assertEquals(listOf(NEW_ID), harness.sets.added, "message=${state.uiMessage}")
        assertEquals(null, state.uiMessage)
    }

    /** Offline, an inferred muscle write on the new lift fails with a fetch error, not a refusal. */
    @Test
    fun a_typed_new_lift_still_adds_when_its_inferred_muscle_write_fails_on_the_network() = runVmTest {
        val harness = Harness()
        harness.exercises.muscleFailure = { IllegalStateException("Failed to fetch $PRODUCTION_URL/rest/v1/exercise?id=eq.$it") }
        harness.showDate()

        harness.viewModel.addExerciseFromInput("Nordic Curl")

        val state = harness.settle("Nordic Curl")
        assertEquals(listOf(NEW_ID), harness.sets.added, "muscle writes=${harness.exercises.muscleWrites}; message=${state.uiMessage}")
        assertNoRawText(state.uiMessage)
    }

    /** Owner question: should a refused chosen muscle group name its reason. wasmJs kotlin.test.Ignore takes no message. */
    @Ignore
    @Test
    fun a_refused_muscle_group_the_person_chose_names_the_reason_without_a_raw_code() = runVmTest {
        val harness = Harness()
        harness.exercises.muscleFailure = { NotCommittedException(NotCommittedReason.StorageUnavailable) }
        harness.showDate()

        harness.viewModel.updateExerciseMuscleGroup(BENCH_PRESS, "SHOULDERS")

        val message = assertNotNull(harness.message())
        assertNoRawText(message)
        assertTrue("device storage unavailable" in message, "message=$message")
    }

    @Test
    fun a_network_failure_on_a_chosen_muscle_group_shows_no_raw_text() = runVmTest {
        val harness = Harness()
        harness.exercises.muscleFailure = { IllegalStateException("Failed to fetch $PRODUCTION_URL/rest/v1/exercise?id=eq.$it") }
        harness.showDate()

        harness.viewModel.updateExerciseMuscleGroup(BENCH_PRESS, "SHOULDERS")

        assertNoRawText(harness.message())
    }

    @Test
    fun a_typed_lift_that_fails_on_the_network_shows_the_designed_sentence_only() = runVmTest {
        val harness = Harness()
        harness.exercises.createFailure = IllegalStateException("Failed to fetch $PRODUCTION_URL/rest/v1/exercise")
        harness.showDate()

        harness.viewModel.addExerciseFromInput("Nordic Curl")

        val message = assertNotNull(harness.message())
        assertEquals("Couldn't add that exercise.", message)
        assertTrue("://" !in message && "Exception" !in message, message)
    }

    private fun assertNoRawText(message: String?) {
        if (message == null) return
        val raw = listOf("supabase.co", "Exception", "Failed to fetch") + NotCommittedReason.entries.map { it.name }
        assertTrue(raw.none { it in message }, "raw text in message: $message")
    }

    private class Harness {
        private val catalog = listOf(Exercise(BENCH_PRESS, "Bench Press", "CHEST"))
        private val base = DemoRepos(
            DemoSeedData(
                anchorDate = LocalDate.parse(DATE),
                exercises = catalog,
                sets = emptyList(),
                settings = UserSettings(),
                cycle = Cycle(numTypes = 0, isActive = false, startDate = null),
                slots = emptyList(),
                splitExercises = emptyList(),
                workoutDays = emptyList(),
                archives = emptyList(),
                cardio = emptyList(),
                pendingReviews = emptyList(),
                usageCounts = emptyMap(),
            ),
        )
        val exercises = Catalog(base.exercises, catalog)
        val sets = Sets(base.sets)
        val viewModel = LogViewModel(
            object : AppRepos by base {
                override val exercises: ExerciseRepository = this@Harness.exercises
                override val sets: SetRepository = this@Harness.sets
            },
            isOnline = { false },
            derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate,
        )

        suspend fun showDate() {
            viewModel.selectDate(DATE)
            withTimeout(TIMEOUT) { viewModel.uiState.first { it.currentDate == DATE } }
            yield()
        }

        suspend fun settle(name: String) = withTimeout(TIMEOUT) {
            viewModel.uiState.first { state ->
                state.uiMessage != null || state.exercises.any { it.name == name && it.sets.isNotEmpty() }
            }
        }

        suspend fun message(): String? = withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.uiMessage != null }.uiMessage
        }
    }

    private class Catalog(delegate: ExerciseRepository, initial: List<Exercise>) : ExerciseRepository by delegate {
        private val rows = MutableStateFlow(initial)
        override val exercises: StateFlow<List<Exercise>> = rows.asStateFlow()
        var muscleFailure: ((Long) -> Throwable?)? = null
        var createFailure: Throwable? = null
        val muscleWrites = mutableListOf<Long>()

        override suspend fun getAll(): List<Exercise> = rows.value
        override fun canonicalId(id: Long): Long = id
        override suspend fun getById(id: Long): Exercise? = rows.value.firstOrNull { it.id == id }
        override suspend fun getByIds(ids: Collection<Long>): List<Exercise> = rows.value.filter { it.id in ids }
        override suspend fun findExact(name: String): Exercise? =
            rows.value.firstOrNull { simpleNameKey(it.name) == simpleNameKey(name) }

        override suspend fun getOrCreate(name: String): Exercise =
            createFailure?.let { throw it } ?: findExact(name) ?: Exercise(id = NEW_ID, name = name.trim()).also { rows.value = rows.value + it }

        override suspend fun updateMuscleGroups(id: Long, muscleGroups: String) {
            muscleWrites += id
            muscleFailure?.invoke(id)?.let { throw it }
            rows.value = rows.value.map { if (it.id == id) it.copy(muscleGroups = muscleGroups) else it }
        }
    }

    private class Sets(delegate: SetRepository) : SetRepository by delegate {
        val added = mutableListOf<Long>()
        private val rows = MutableStateFlow(delegate.sets.value.toList())
        override val sets: StateFlow<List<WorkoutSet>> = rows.asStateFlow()
        private var nextId = -1L

        override suspend fun getAllSets(): List<WorkoutSet> = rows.value
        override suspend fun getSetsForDate(date: String): List<WorkoutSet> = rows.value.filter { it.date == date }
        override suspend fun addExerciseSession(
            date: String,
            exerciseId: Long,
            isBodyweightDefault: Boolean,
        ): LocalCommit<List<WorkoutSet>> {
            val row = WorkoutSet(id = nextId--, syncId = "u5-${-nextId}", exerciseId = exerciseId, date = date, setNumber = 1)
            rows.value = rows.value + row
            added += exerciseId
            return LocalCommit.Committed(listOf(row))
        }
    }

    private companion object {
        const val DATE = "2026-09-17"
        const val BENCH_PRESS = 30L
        const val NEW_ID = 31L
        const val TIMEOUT = 2_000L
        const val PRODUCTION_URL = "https://xzxnblmwdygeroylyqwt.supabase.co"
    }
}
