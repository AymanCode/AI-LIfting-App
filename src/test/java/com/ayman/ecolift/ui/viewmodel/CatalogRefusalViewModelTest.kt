package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.ai.MuscleClassification
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseMatch
import com.ayman.ecolift.data.ExerciseMatchInput
import com.ayman.ecolift.data.ExerciseMatcher
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.simpleNameKey
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.NotCommittedException
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogRefusalViewModelTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun a_refused_clipboard_alias_shows_the_catalog_reason() = runVmTest {
        val harness = AddHarness()
        harness.exercises.aliasRefusal = NotCommittedReason.NeedsReconciliation
        harness.showDate()

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), ClipboardAnswer.Same)),
        )

        assertEquals(
            "Couldn't add Leg Extensions · changed on another device",
            harness.message(),
        )
        assertTrue(harness.sets.added.isEmpty())
    }

    @Test
    fun a_refused_inferred_muscle_on_a_clipboard_lift_still_adds_the_session() = runVmTest {
        val harness = AddHarness()
        harness.exercises.muscleRefusal = { NotCommittedReason.StorageUnavailable }
        harness.showDate()

        harness.viewModel.addClipboardPicks(listOf(ClipboardPickRequest(ClipboardPick.Typed("Cable Fly"))))

        val saved = withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { state ->
                state.uiMessage != null || state.exercises.any { it.name == "Cable Fly" && it.sets.isNotEmpty() }
            }
        }
        assertEquals(null, saved.uiMessage)
        assertEquals(listOf(31L), harness.sets.added)
        assertEquals("", harness.exercises.storedGroups(31L))
        assertEquals(1, saved.exercises.first { it.name == "Cable Fly" }.sets.size)
    }

    @Test
    fun a_typed_new_lift_still_adds_when_its_muscle_write_is_refused() = runVmTest {
        val harness = AddHarness(online = false)
        harness.exercises.muscleRefusal = { NotCommittedReason.NeedsReconciliation }
        harness.showDate()

        harness.viewModel.addExerciseFromInput("Nordic Curl")

        val saved = withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { state ->
                state.uiMessage != null || state.exercises.any { it.name == "Nordic Curl" && it.sets.isNotEmpty() }
            }
        }
        assertEquals(null, saved.uiMessage)
        assertEquals(listOf(31L), harness.sets.added)
        assertEquals("", harness.exercises.storedGroups(31L))
        assertEquals(1, saved.exercises.first { it.name == "Nordic Curl" }.sets.size)
    }

    @Test
    fun a_refused_first_classification_still_persists_the_second() = runVmTest {
        val harness = BatchHarness()
        harness.showDate()

        harness.viewModel.selectDate(NEXT_DATE)
        withTimeout(TIMEOUT) {
            while (harness.exercises.muscleWrites.isEmpty()) yield()
        }
        yield()

        assertEquals(listOf(ALPHA, BETA), harness.exercises.muscleWrites)
        assertEquals("", harness.exercises.storedGroups(ALPHA))
        assertEquals("CALVES", harness.exercises.storedGroups(BETA))
        assertEquals(null, harness.viewModel.uiState.value.uiMessage)
    }

    @Test
    fun a_clipboard_failure_that_is_not_a_catalog_refusal_shows_the_designed_sentence() = runVmTest {
        val harness = AddHarness()
        harness.exercises.aliasError = IllegalStateException("network down")
        harness.showDate()

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), ClipboardAnswer.Same)),
        )

        val message = assertNotNull(harness.message())
        assertEquals("Couldn't add Leg Extensions.", message)
        assertTrue("network down" !in message && "://" !in message && "Exception" !in message, message)
    }

    @Test
    fun a_refused_merge_decision_shows_the_catalog_reason() = runVmTest {
        val harness = AddHarness(online = true)
        harness.showDate()
        harness.viewModel.addExerciseFromInput("Leg Extensions")
        val pending = withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { it.pendingExerciseResolution != null }.pendingExerciseResolution
        }
        harness.exercises.aliasRefusal = NotCommittedReason.NeedsReconciliation

        harness.viewModel.confirmExercisesSame(assertNotNull(pending).requestId)

        assertEquals(
            "Couldn't save the exercise decision · changed on another device",
            harness.message(),
        )
        assertTrue(harness.sets.added.isEmpty())
    }

    @Test
    fun a_refused_backfill_keeps_later_lifts_and_shows_no_success() = runVmTest {
        val harness = BackfillHarness()
        harness.showDate()

        val squat = withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { state ->
                state.exercises.any { it.name == "Squat" && it.muscleGroups == "QUADS" }
            }.exercises.first { it.name == "Squat" }
        }
        val bench = harness.viewModel.uiState.value.exercises.first { it.name == "Bench Press" }

        assertEquals("QUADS", squat.muscleGroups)
        assertEquals("", bench.muscleGroups)
        assertEquals(null, harness.viewModel.uiState.value.uiMessage)

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Existing(BENCH_PRESS, "Bench Press"))),
        )
        val savedBench = withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { state ->
                state.exercises.first { it.name == "Bench Press" }.sets.size == 2
            }
        }
        assertEquals("", savedBench.exercises.first { it.name == "Bench Press" }.muscleGroups)
        assertEquals(null, savedBench.uiMessage)
        assertEquals(listOf(BENCH_PRESS), harness.sets.added)
    }

    @Test
    fun a_refused_classification_during_a_split_switch_keeps_the_new_workout_visible() = runVmTest {
        val harness = SwitchHarness()
        harness.showDate()
        harness.exercises.refuseReads = true
        val outcome = nextOutcome(harness.viewModel)

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        val applied = assertIs<SplitSwitchOutcome.Applied>(outcome.await())

        assertEquals(requestId, applied.requestId)
        assertEquals(listOf(EXERCISE_B), harness.sets.forDate(DATE).map { it.exerciseId })
        assertEquals(
            "Couldn't change the muscle group · changed on another device",
            withTimeout(TIMEOUT) { harness.viewModel.uiState.first { it.uiMessage != null }.uiMessage },
        )
    }

    @Test
    fun a_split_switch_classification_error_that_is_not_a_catalog_refusal_stays_quiet() = runVmTest {
        val harness = SwitchHarness()
        harness.showDate()
        harness.exercises.readError = IllegalStateException("network down")
        val messages = mutableListOf<String?>()
        val watch = launch { harness.viewModel.uiState.collect { messages += it.uiMessage } }
        val outcome = nextOutcome(harness.viewModel)

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        val applied = assertIs<SplitSwitchOutcome.Applied>(outcome.await())
        yield()
        watch.cancel()

        assertEquals(requestId, applied.requestId)
        assertEquals(listOf(EXERCISE_B), harness.sets.forDate(DATE).map { it.exerciseId })
        assertTrue(messages.none { it != null })
    }

    private fun lookAlike() = ClipboardCheck.LookAlike(LEG_EXTENSION, "Leg Extension", HISTORY_DATE)

    private fun TestScope.nextOutcome(viewModel: LogViewModel): Deferred<SplitSwitchOutcome> =
        async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(TIMEOUT) { viewModel.splitSwitchOutcomes.first() }
        }

    private class AddHarness(online: Boolean = true) {
        private val catalog = listOf(
            Exercise(LEG_EXTENSION, "Leg Extension", "QUADS"),
            Exercise(SEATED_LEG_CURL, "Seated Leg Curl", "HAMSTRINGS"),
            Exercise(BENCH_PRESS, "Bench Press", "CHEST"),
        )
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(ADD_DATE),
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
        )
        private val base = DemoRepos(seed)
        val exercises = CatalogDouble(base.exercises, catalog)
        val sets = RecordingSets(base.sets)
        val viewModel = LogViewModel(
            object : AppRepos by base {
                override val exercises: ExerciseRepository = this@AddHarness.exercises
                override val sets: SetRepository = this@AddHarness.sets
            },
            isOnline = { online },
        )

        suspend fun showDate() {
            viewModel.selectDate(ADD_DATE)
            withTimeout(TIMEOUT) { viewModel.uiState.first { it.currentDate == ADD_DATE } }
            yield()
        }

        suspend fun message(): String? = withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.uiMessage != null }.uiMessage
        }
    }

    private class BackfillHarness {
        private val catalog = listOf(
            Exercise(BENCH_PRESS, "Bench Press", ""),
            Exercise(SQUAT, "Squat", ""),
        )
        private val seededSets = listOf(
            WorkoutSet(id = 1, exerciseId = BENCH_PRESS, date = ADD_DATE, setNumber = 1, weightLbs = 1000, reps = 5, completed = false),
            WorkoutSet(id = 2, exerciseId = SQUAT, date = ADD_DATE, setNumber = 1, weightLbs = 1400, reps = 5, completed = false),
        )
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(ADD_DATE),
            exercises = catalog,
            sets = seededSets,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 0, isActive = false, startDate = null),
            slots = emptyList(),
            splitExercises = emptyList(),
            workoutDays = emptyList(),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = emptyMap(),
        )
        private val base = DemoRepos(seed)
        val exercises = CatalogDouble(base.exercises, catalog).apply {
            muscleRefusal = { id ->
                if (id == BENCH_PRESS) NotCommittedReason.NeedsReconciliation else null
            }
        }
        val sets = RecordingSets(base.sets)
        val viewModel = LogViewModel(
            object : AppRepos by base {
                override val exercises: ExerciseRepository = this@BackfillHarness.exercises
                override val sets: SetRepository = this@BackfillHarness.sets
            },
            isOnline = { false },
        )

        suspend fun showDate() {
            viewModel.selectDate(ADD_DATE)
            withTimeout(TIMEOUT) { viewModel.uiState.first { it.currentDate == ADD_DATE && it.exercises.size == 2 } }
            yield()
        }
    }

    private class CatalogDouble(
        private val delegate: ExerciseRepository,
        initial: List<Exercise>,
    ) : ExerciseRepository by delegate {
        private val rows = MutableStateFlow(initial)
        override val exercises: StateFlow<List<Exercise>> = rows.asStateFlow()
        var aliasRefusal: NotCommittedReason? = null
        var aliasError: Throwable? = null
        var muscleRefusal: ((Long) -> NotCommittedReason?)? = null
        var refuseReads = false
        var readError: Throwable? = null

        override suspend fun getAll(): List<Exercise> = rows.value
        override fun canonicalId(id: Long): Long = id
        override suspend fun getById(id: Long): Exercise? = rows.value.firstOrNull { it.id == id }
        override suspend fun getByIds(ids: Collection<Long>): List<Exercise> {
            readError?.let { throw it }
            if (refuseReads) throw NotCommittedException(NotCommittedReason.NeedsReconciliation)
            return rows.value.filter { it.id in ids }
        }

        override suspend fun findExact(name: String): Exercise? =
            rows.value.firstOrNull { simpleNameKey(it.name) == simpleNameKey(name) }

        override suspend fun getOrCreate(name: String): Exercise =
            findExact(name) ?: Exercise(id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1, name = name.trim()).also {
                rows.value = rows.value + it
            }

        val muscleWrites = mutableListOf<Long>()

        fun storedGroups(id: Long): String = rows.value.first { it.id == id }.muscleGroups

        override suspend fun updateMuscleGroups(id: Long, muscleGroups: String) {
            muscleWrites += id
            muscleRefusal?.invoke(id)?.let { throw NotCommittedException(it) }
            rows.value = rows.value.map { if (it.id == id) it.copy(muscleGroups = muscleGroups) else it }
        }

        override suspend fun recordAlias(exerciseId: Long, alias: String) {
            aliasError?.let { throw it }
            aliasRefusal?.let { throw NotCommittedException(it) }
        }

        override suspend fun duplicateCandidate(name: String): ExerciseMatch? = ExerciseMatcher.duplicateCandidate(
            target = ExerciseMatchInput(Exercise(id = Long.MIN_VALUE, name = name)),
            others = rows.value.map { ExerciseMatchInput(it) },
        )

        override suspend fun duplicateCandidate(exerciseId: Long): ExerciseMatch? {
            val inputs = rows.value.map { ExerciseMatchInput(it) }
            val target = inputs.firstOrNull { it.exercise.id == exerciseId } ?: return null
            return ExerciseMatcher.duplicateCandidate(target, inputs)
        }
    }

    private class RecordingSets(delegate: SetRepository) : SetRepository by delegate {
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
            val row = WorkoutSet(
                id = nextId--,
                syncId = "refusal-${-nextId}",
                exerciseId = exerciseId,
                date = date,
                setNumber = 1,
                completed = false,
            )
            rows.value = rows.value + row
            added += exerciseId
            return LocalCommit.Committed(listOf(row))
        }
    }

    private class BatchHarness {
        private val catalog = listOf(
            Exercise(ALPHA, "Alpha Lift", ""),
            Exercise(BETA, "Beta Lift", ""),
        )
        private val seededSets = listOf(
            WorkoutSet(id = 1, exerciseId = ALPHA, date = ADD_DATE, setNumber = 1, weightLbs = 100, reps = 8, completed = false),
            WorkoutSet(id = 2, exerciseId = BETA, date = ADD_DATE, setNumber = 1, weightLbs = 80, reps = 10, completed = false),
        )
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(ADD_DATE),
            exercises = catalog,
            sets = seededSets,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 0, isActive = false, startDate = null),
            slots = emptyList(),
            splitExercises = emptyList(),
            workoutDays = emptyList(),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = emptyMap(),
        )
        private val base = DemoRepos(seed)
        val exercises = CatalogDouble(base.exercises, catalog).apply {
            muscleRefusal = { id -> if (id == ALPHA) NotCommittedReason.NeedsReconciliation else null }
        }
        val sets = RecordingSets(base.sets)
        val viewModel = LogViewModel(
            object : AppRepos by base {
                override val exercises: ExerciseRepository = this@BatchHarness.exercises
                override val sets: SetRepository = this@BatchHarness.sets
            },
            isOnline = { false },
            classifyMuscles = {
                listOf(
                    MuscleClassification(ALPHA, "HAMSTRINGS", 0.91),
                    MuscleClassification(BETA, "CALVES", 0.93),
                )
            },
        )

        suspend fun showDate() {
            viewModel.selectDate(ADD_DATE)
            withTimeout(TIMEOUT) { viewModel.uiState.first { it.currentDate == ADD_DATE } }
            yield()
        }
    }

    private class SwitchHarness {
        private val slots = listOf(
            CycleSlot(SLOT_A, "A", 0),
            CycleSlot(SLOT_B, "B", 1),
        )
        private val splitExercises = listOf(
            SplitExercise(1, SLOT_A, EXERCISE_A, 0),
            SplitExercise(2, SLOT_B, EXERCISE_B, 0),
        )
        private val currentDay = WorkoutDay(
            date = DATE,
            cycleSlotType = 0,
            cycleSlotOccurrence = 2,
            cycleSlotId = SLOT_A,
        )
        private val history = listOf(
            WorkoutSet(id = 1, exerciseId = EXERCISE_A, date = SWITCH_HISTORY, setNumber = 1, weightLbs = 100, reps = 8, completed = true),
            WorkoutSet(id = 2, exerciseId = EXERCISE_B, date = SWITCH_HISTORY, setNumber = 1, weightLbs = 200, reps = 5, completed = true),
        )
        private val currentRows = listOf(
            WorkoutSet(id = CURRENT_A_SET_ID, exerciseId = EXERCISE_A, date = DATE, setNumber = 1, weightLbs = 100, reps = 8, completed = false),
        )
        private val catalog = listOf(
            Exercise(EXERCISE_A, "Exercise A", "CHEST"),
            Exercise(EXERCISE_B, "Exercise B", "BACK"),
            Exercise(EXERCISE_C, "Exercise C", "LEGS"),
        )
        private val days = listOf(
            WorkoutDay(SWITCH_HISTORY, cycleSlotType = 0, cycleSlotOccurrence = 1, cycleSlotId = SLOT_A),
            currentDay,
        )
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(DATE),
            exercises = catalog,
            sets = history + currentRows,
            settings = UserSettings(),
            cycle = Cycle(numTypes = slots.size, isActive = true, startDate = null),
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = days,
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(history + currentRows),
        )
        private val base = DemoRepos(seed)
        val exercises = CatalogDouble(base.exercises, catalog)
        val sets = MutableSets(base.sets, history + currentRows)
        private val workout = MutableWorkout(base.workout, seed.cycle, slots, splitExercises, days)
        private val coordinator = SplitReplacementCoordinator(
            journal = MemoryJournal(),
            replaceSetDayBatch = sets::replaceDayBatch,
            enqueueDayBatch = workout::enqueueDayBatch,
            withDrainLock = { block -> block() },
            kickEngine = {},
        )
        val viewModel = LogViewModel(
            object : AppRepos by base {
                override val exercises: ExerciseRepository = this@SwitchHarness.exercises
                override val sets: SetRepository = this@SwitchHarness.sets
                override val workout: WorkoutRepository = this@SwitchHarness.workout
                override val splitReplacement: SplitReplacementCoordinator = coordinator
            },
        )

        suspend fun showDate() {
            viewModel.selectDate(DATE)
            withTimeout(TIMEOUT) {
                viewModel.uiState.first { state ->
                    state.currentDate == DATE && state.exercises.sumOf { it.sets.size } == 1
                }
            }
            yield()
        }
    }

    private class MutableSets(
        delegate: SetRepository,
        initialSets: List<WorkoutSet>,
    ) : SetRepository by delegate {
        private val mutableSets = MutableStateFlow(initialSets.toList())
        override val sets: StateFlow<List<WorkoutSet>> = mutableSets.asStateFlow()
        private var nextId = -1L

        fun forDate(date: String): List<WorkoutSet> = SetQueries.forDate(mutableSets.value, date)

        override suspend fun getAllSets(): List<WorkoutSet> = mutableSets.value
        override suspend fun getSetsForDate(date: String): List<WorkoutSet> = forDate(date)
        override suspend fun getSetsByDate(date: String): List<WorkoutSet> = forDate(date)
        override fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> =
            mutableSets.map { SetQueries.forDate(it, date) }

        override suspend fun replaceDayBatch(
            date: String,
            desiredSets: List<WorkoutSet>,
            cardModes: Map<Long, WorkoutCardDisplayMode>,
            precondition: () -> Unit,
        ): LocalCommit<Unit> {
            precondition()
            mutableSets.value = mutableSets.value.filterNot { it.date == date } + desiredSets.map { it.copy(id = nextId--) }
            return LocalCommit.Committed(Unit)
        }
    }

    private class MutableWorkout(
        delegate: WorkoutRepository,
        cycleValue: Cycle,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,
        days: List<WorkoutDay>,
    ) : WorkoutRepository by delegate {
        private val mutableDays = MutableStateFlow(days.toList())
        private val mutableSlots = MutableStateFlow(slots.toList())
        private val mutableSplitExercises = MutableStateFlow(splitExercises.toList())
        override val cycle: StateFlow<Cycle> = MutableStateFlow(cycleValue).asStateFlow()

        override fun observeWorkoutDay(date: String): Flow<WorkoutDay?> =
            mutableDays.map { rows -> rows.firstOrNull { it.date == date } }

        override fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = mutableDays
        override fun observeCycleSlots(): Flow<List<CycleSlot>> = mutableSlots
        override suspend fun getCycleSlots(): List<CycleSlot> = mutableSlots.value
        override fun observeAllSplitExercises(): Flow<List<SplitExercise>> = mutableSplitExercises
        override fun observeSplitExercises(splitId: Long): Flow<List<SplitExercise>> =
            mutableSplitExercises.map { rows -> rows.filter { it.splitId == splitId }.sortedBy { it.orderIndex } }

        override suspend fun enqueueDayBatch(op: DayPendingWrite) {
            val day = WorkoutDay(
                date = op.date,
                cycleSlotType = op.cycleSlotType,
                cycleSlotOccurrence = op.cycleSlotOccurrence,
                alternativeForDate = op.alternativeForDate,
                cycleSlotId = op.cycleSlotId,
            )
            mutableDays.value = mutableDays.value.filterNot { it.date == op.date } + day
        }
    }

    private class MemoryJournal : SplitReplacementJournal {
        private var entries: List<PreparedSplitReplacement> = emptyList()

        override fun load(): Result<List<PreparedSplitReplacement>> = Result.success(entries.toList())

        override fun save(replacement: PreparedSplitReplacement): Result<Unit> = runCatching {
            entries = entries.filterNot { it.date == replacement.date } + replacement
        }

        override fun clear(date: String): Result<Unit> = runCatching {
            entries = entries.filterNot { it.date == date }
        }
    }

    private companion object {
        const val ADD_DATE = "2026-09-22"
        const val HISTORY_DATE = "2026-09-18"
        const val DATE = "2026-08-14"
        const val SWITCH_HISTORY = "2026-08-01"
        const val LEG_EXTENSION = 10L
        const val SEATED_LEG_CURL = 20L
        const val BENCH_PRESS = 30L
        const val SQUAT = 40L
        const val SLOT_A = 1L
        const val SLOT_B = 2L
        const val EXERCISE_A = 10L
        const val EXERCISE_B = 20L
        const val EXERCISE_C = 30L
        const val CURRENT_A_SET_ID = 101L
        const val ALPHA = 51L
        const val BETA = 52L
        const val NEXT_DATE = "2026-09-23"
        const val TIMEOUT = 2_000L
    }
}
