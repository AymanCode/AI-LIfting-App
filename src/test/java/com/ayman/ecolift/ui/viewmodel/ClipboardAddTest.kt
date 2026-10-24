package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.android.room.chooseMergeKeeper
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseMatch
import com.ayman.ecolift.data.ExerciseMatchInput
import com.ayman.ecolift.data.ExerciseMatcher
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.ExerciseMergePreparationResult
import com.ayman.ecolift.web.supabase.ExerciseMergePreview
import com.ayman.ecolift.web.supabase.ExerciseMergeResult
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.simpleNameKey
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The clipboard's add path: a note's answer does what "Yes, same" / "Different" do today, ticked
 * lines go in one at a time in tick order, and nothing is saved while a note is unanswered.
 */
class ClipboardAddTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun a_written_name_that_looks_like_a_lift_asks_before_anything_is_saved() = runVmTest {
        val harness = Harness()

        val check = harness.viewModel.checkClipboardPick(ClipboardPick.Typed("Leg Extensions"))

        val lookAlike = assertIs<ClipboardCheck.LookAlike>(check)
        assertEquals(LEG_EXTENSION, lookAlike.exerciseId)
        assertEquals("Leg Extension", lookAlike.name)
        assertEquals(HISTORY_DATE, lookAlike.lastCheckedDate)
        assertTrue(harness.exercises.created.isEmpty())
        assertTrue(harness.sets.sessions.value.isEmpty())
    }

    @Test
    fun an_exact_name_is_that_lift_and_offline_skips_the_question() = runVmTest {
        val harness = Harness()
        val offline = Harness(online = false)

        assertEquals(
            ClipboardCheck.Exact(LEG_EXTENSION, "Leg Extension"),
            harness.viewModel.checkClipboardPick(ClipboardPick.Typed("leg  extension ")),
        )
        assertEquals(ClipboardCheck.Clear, offline.viewModel.checkClipboardPick(ClipboardPick.Typed("Leg Extensions")))
    }

    @Test
    fun same_adds_the_existing_lift_and_remembers_the_spelling() = runVmTest {
        val harness = Harness()
        harness.showDate()

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), ClipboardAnswer.Same)),
        )

        assertEquals(listOf(LEG_EXTENSION), harness.sets.awaitSessions(1))
        assertEquals(listOf(LEG_EXTENSION to "Leg Extensions"), harness.exercises.aliases)
        assertTrue(harness.exercises.created.isEmpty())
    }

    @Test
    fun new_lift_creates_it_and_records_the_pair_as_different() = runVmTest {
        val harness = Harness()
        harness.showDate()

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), ClipboardAnswer.Different)),
        )

        val added = harness.sets.awaitSessions(1).single()
        val created = harness.exercises.created.single()
        assertEquals("Leg Extensions", created.name)
        assertEquals(created.id, added)
        assertEquals(listOf(created.id to LEG_EXTENSION), harness.exercises.distinctPairs)
    }

    @Test
    fun a_new_name_without_a_look_alike_gets_the_keyword_muscle() = runVmTest {
        val harness = Harness()
        harness.showDate()

        harness.viewModel.addClipboardPicks(listOf(ClipboardPickRequest(ClipboardPick.Typed("Cable Fly"))))

        val added = harness.sets.awaitSessions(1).single()
        val created = harness.exercises.byId(added)
        assertEquals("Cable Fly", created?.name)
        assertEquals("CHEST", created?.muscleGroups)
    }

    @Test
    fun ticks_go_in_one_at_a_time_in_tick_order_and_an_unanswered_note_adds_nothing() = runVmTest {
        val harness = Harness()
        harness.showDate()

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), answer = null)),
        )
        harness.viewModel.addClipboardPicks(
            listOf(
                ClipboardPickRequest(ClipboardPick.Existing(SEATED_LEG_CURL, "Seated Leg Curl")),
                ClipboardPickRequest(ClipboardPick.Existing(BENCH_PRESS, "Bench Press")),
            ),
        )

        assertEquals(listOf(SEATED_LEG_CURL, BENCH_PRESS), harness.sets.awaitSessions(2))
        assertTrue(harness.exercises.aliases.isEmpty())
    }

    @Test
    fun search_focus_identifies_the_new_row_after_each_add_including_typed_lifts() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val focused = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
        val focus: (Long, String) -> Unit = { id, syncId -> focused.value += id to syncId }
        for (pick in listOf(
            ClipboardPick.Existing(BENCH_PRESS, "Bench Press"),
            ClipboardPick.Existing(BENCH_PRESS, "Bench Press"),
            ClipboardPick.Typed("Cable Fly"),
        )) {
            val before = harness.sets.sets.value.map { it.syncId }.toSet()
            val expectedCount = focused.value.size + 1
            harness.viewModel.addClipboardPicks(listOf(ClipboardPickRequest(pick)), focus)
            val target = withTimeout(TIMEOUT) { focused.first { it.size == expectedCount } }.last()
            val saved = harness.sets.sets.value.single { it.syncId == target.second }
            assertEquals(saved.exerciseId, target.first)
            assertTrue(saved.syncId !in before, "Search must focus the newly inserted row")
        }
    }

    @Test
    fun same_on_a_typed_name_still_adds_after_the_search_box_clears() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val gate = CompletableDeferred<Unit>()
        harness.exercises.aliasGate = gate

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), ClipboardAnswer.Same)),
        )
        assertTrue(harness.sets.sessions.value.isEmpty())
        harness.viewModel.updateExerciseInput("")
        gate.complete(Unit)

        assertEquals(listOf(LEG_EXTENSION), harness.sets.awaitSessions(1))
        harness.assertLogged(DATE, LEG_EXTENSION)
    }

    @Test
    fun same_on_an_existing_lift_still_adds_the_keeper_after_the_search_box_clears() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val gate = CompletableDeferred<Unit>()
        harness.exercises.mergeGate = gate

        harness.viewModel.addClipboardPicks(
            listOf(
                ClipboardPickRequest(
                    ClipboardPick.Existing(SEATED_LEG_CURL, "Seated Leg Curl"),
                    lookAlike(),
                    ClipboardAnswer.Same,
                ),
            ),
        )
        assertTrue(harness.sets.sessions.value.isEmpty())
        harness.viewModel.updateExerciseInput("")
        gate.complete(Unit)

        assertEquals(listOf(LEG_EXTENSION), harness.sets.awaitSessions(1))
        harness.assertLogged(DATE, LEG_EXTENSION)
        assertEquals(LEG_EXTENSION, harness.exercises.byId(SEATED_LEG_CURL)?.mergedIntoId)
    }

    @Test
    fun a_new_name_still_adds_after_the_search_box_clears() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val gate = CompletableDeferred<Unit>()
        harness.exercises.createGate = gate

        harness.viewModel.addClipboardPicks(listOf(ClipboardPickRequest(ClipboardPick.Typed("Zercher Squat"))))
        assertTrue(harness.sets.sessions.value.isEmpty())
        harness.viewModel.updateExerciseInput("")
        gate.complete(Unit)

        val added = harness.sets.awaitSessions(1).single()
        val created = harness.exercises.created.single()
        assertEquals("Zercher Squat", created.name)
        assertEquals(created.id, added)
        harness.assertLogged(DATE, created.id)
    }

    @Test
    fun clearing_the_search_during_the_first_line_still_adds_both_in_tick_order() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val gate = CompletableDeferred<Unit>()
        harness.exercises.aliasGate = gate

        harness.viewModel.addClipboardPicks(
            listOf(
                ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), ClipboardAnswer.Same),
                ClipboardPickRequest(ClipboardPick.Existing(BENCH_PRESS, "Bench Press")),
            ),
        )
        assertEquals(emptyList(), harness.sets.sessions.value)
        harness.viewModel.updateExerciseInput("")
        gate.complete(Unit)

        assertEquals(listOf(LEG_EXTENSION, BENCH_PRESS), harness.sets.awaitSessions(2))
        harness.assertLogged(DATE, LEG_EXTENSION)
        harness.assertLogged(DATE, BENCH_PRESS)
    }

    @Test
    fun changing_the_day_during_the_write_adds_nothing() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val gate = CompletableDeferred<Unit>()
        harness.exercises.aliasGate = gate

        harness.viewModel.addClipboardPicks(
            listOf(ClipboardPickRequest(ClipboardPick.Typed("Leg Extensions"), lookAlike(), ClipboardAnswer.Same)),
        )
        harness.viewModel.selectDate(NEXT_DATE)
        gate.complete(Unit)
        yield()

        assertTrue(harness.sets.sessions.value.isEmpty())
        assertTrue(harness.sets.sets.value.none { it.date == DATE || it.date == NEXT_DATE })
        assertTrue(harness.sets.cards.value.none { it.date == DATE || it.date == NEXT_DATE })
    }

    private fun lookAlike() = ClipboardCheck.LookAlike(LEG_EXTENSION, "Leg Extension", HISTORY_DATE)

    private class Harness(online: Boolean = true) {
        private val catalog = listOf(
            Exercise(LEG_EXTENSION, "Leg Extension", "QUADS"),
            Exercise(SEATED_LEG_CURL, "Seated Leg Curl", "HAMSTRINGS"),
            Exercise(BENCH_PRESS, "Bench Press", "CHEST"),
        )
        private val history = listOf(
            WorkoutSet(id = 1, exerciseId = LEG_EXTENSION, date = HISTORY_DATE, setNumber = 1, weightLbs = 1200, reps = 10, isBodyweight = false, completed = true),
            WorkoutSet(id = 2, exerciseId = SEATED_LEG_CURL, date = HISTORY_DATE, setNumber = 1, weightLbs = 900, reps = 12, isBodyweight = false, completed = true),
        )
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(DATE),
            exercises = catalog,
            sets = history,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 0, isActive = false, startDate = null),
            slots = emptyList(),
            splitExercises = emptyList(),
            workoutDays = emptyList(),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(history),
        )
        private val base = DemoRepos(seed)
        val sets = SessionRecordingSets(base.sets, history)
        val exercises = RecordingExercises(base.exercises, catalog, history)

        fun assertLogged(date: String, exerciseId: Long) {
            val card = sets.cards.value.single { it.date == date && it.exerciseId == exerciseId }
            val set = sets.sets.value.single { it.date == date && it.exerciseId == exerciseId }
            assertEquals(exerciseId, card.exerciseId)
            assertEquals(exerciseId, set.exerciseId)
        }
        val repos: AppRepos = object : AppRepos by base {
            override val sets: SetRepository = this@Harness.sets
            override val exercises: ExerciseRepository = this@Harness.exercises
        }
        val viewModel = LogViewModel(repos, isOnline = { online }, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate)

        suspend fun showDate() {
            viewModel.selectDate(DATE)
            withTimeout(TIMEOUT) { viewModel.uiState.first { it.currentDate == DATE } }
            yield()
        }
    }

    /** Records every lift added to the day and publishes its rows like the real repository. */
    private class SessionRecordingSets(
        private val delegate: SetRepository,
        initial: List<WorkoutSet>,
    ) : SetRepository by delegate {
        private val rows = MutableStateFlow(initial.toList())
        private val cardRows = MutableStateFlow<List<WorkoutExerciseCard>>(emptyList())
        override val sets: StateFlow<List<WorkoutSet>> = rows.asStateFlow()
        override val cards: StateFlow<List<WorkoutExerciseCard>> = cardRows.asStateFlow()
        val sessions = MutableStateFlow<List<Long>>(emptyList())
        private var nextId = -1L

        suspend fun awaitSessions(count: Int): List<Long> = withTimeout(TIMEOUT) { sessions.first { it.size >= count } }

        override suspend fun getAllSets(): List<WorkoutSet> = rows.value
        override fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> = rows.map { SetQueries.forDate(it, date) }
        override suspend fun getSetsForDate(date: String): List<WorkoutSet> = SetQueries.forDate(rows.value, date)
        override suspend fun getSetsByDate(date: String): List<WorkoutSet> = SetQueries.forDate(rows.value, date)
        override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> =
            SetQueries.completedHistoryBeforeDate(rows.value, exerciseId, beforeDate).take(100)

        override suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int? =
            SetQueries.maxCompletedWeightBefore(rows.value, exerciseId, beforeDate)

        override suspend fun addExerciseSession(date: String, exerciseId: Long, isBodyweightDefault: Boolean): LocalCommit<List<WorkoutSet>> {
            val row = WorkoutSet(id = nextId--, syncId = "clipboard-test-$nextId", exerciseId = exerciseId, date = date, setNumber = SetQueries.nextSetNumber(SetQueries.forDateAndExercise(rows.value, date, exerciseId)), isBodyweight = false, completed = false)
            rows.value = rows.value + row
            cardRows.value = cardRows.value + WorkoutExerciseCard(
                id = "clipboard-card-${row.syncId}",
                date = date,
                exerciseId = exerciseId,
                position = cardRows.value.count { it.date == date },
                displayMode = WorkoutCardDisplayMode.EXPANDED,
                origin = WorkoutCardOrigin.MANUAL,
            )
            sessions.value = sessions.value + exerciseId
            return LocalCommit.Committed(listOf(row))
        }
    }

    /** A catalog that creates, remembers spellings and records distinct pairs, all in memory. */
    private class RecordingExercises(
        private val delegate: ExerciseRepository,
        initial: List<Exercise>,
        private val history: List<WorkoutSet>,
    ) : ExerciseRepository by delegate {
        private val all = MutableStateFlow(initial.toList())
        override val exercises: StateFlow<List<Exercise>> = all.asStateFlow()
        val created = mutableListOf<Exercise>()
        val aliases = mutableListOf<Pair<Long, String>>()
        val distinctPairs = mutableListOf<Pair<Long, Long>>()
        var aliasGate: CompletableDeferred<Unit>? = null
        var mergeGate: CompletableDeferred<Unit>? = null
        var createGate: CompletableDeferred<Unit>? = null
        private var nextId = 1_000L

        fun byId(id: Long): Exercise? = all.value.firstOrNull { it.id == id }

        override suspend fun getAll(): List<Exercise> = all.value
        override fun canonicalId(id: Long): Long = id
        override suspend fun getById(id: Long): Exercise? = byId(id)
        override suspend fun getByIds(ids: Collection<Long>): List<Exercise> = all.value.filter { it.id in ids }
        override suspend fun findExact(name: String): Exercise? =
            all.value.firstOrNull { simpleNameKey(it.name) == simpleNameKey(name) }

        override suspend fun getOrCreate(name: String): Exercise {
            createGate?.await()
            return findExact(name) ?: Exercise(id = nextId++, name = name.trim()).also {
                created += it
                all.value = all.value + it
            }
        }

        override suspend fun updateMuscleGroups(id: Long, muscleGroups: String) {
            all.value = all.value.map { if (it.id == id) it.copy(muscleGroups = muscleGroups) else it }
        }

        override suspend fun duplicateCandidate(name: String): ExerciseMatch? = ExerciseMatcher.duplicateCandidate(
            target = ExerciseMatchInput(Exercise(id = Long.MIN_VALUE, name = name)),
            others = all.value.map { ExerciseMatchInput(it) },
        )

        override suspend fun duplicateCandidate(exerciseId: Long): ExerciseMatch? {
            val inputs = all.value.map { ExerciseMatchInput(it) }
            val target = inputs.firstOrNull { it.exercise.id == exerciseId } ?: return null
            return ExerciseMatcher.duplicateCandidate(target, inputs)
        }

        override suspend fun recordAlias(exerciseId: Long, alias: String) {
            aliasGate?.await()
            aliases += exerciseId to alias
        }

        override suspend fun prepareMerge(idA: Long, idB: Long): ExerciseMergePreparationResult {
            val a = all.value.first { it.id == idA }
            val b = all.value.first { it.id == idB }
            return ExerciseMergePreparationResult.Ready(
                ExerciseMergePreview(exerciseIdA = a.id, exerciseIdB = b.id, expectedKeeperId = chooseMergeKeeper(a, b, history)),
            )
        }

        override suspend fun mergeExercises(
            idA: Long,
            idB: Long,
            expectedKeeperId: Long,
            alias: String?,
        ): ExerciseMergeResult {
            mergeGate?.await()
            val a = all.value.first { it.id == idA }
            val b = all.value.first { it.id == idB }
            val keeperId = chooseMergeKeeper(a, b, history)
            if (keeperId != expectedKeeperId) return ExerciseMergeResult.KeeperDrift
            val loserId = if (keeperId == a.id) b.id else a.id
            all.value = all.value.map { row -> if (row.id == loserId) row.copy(mergedIntoId = keeperId) else row }
            return ExerciseMergeResult.Success(keeperId)
        }

        override suspend fun recordDistinctPair(idA: Long, idB: Long) {
            distinctPairs += idA to idB
        }
    }

    companion object {
        private const val DATE = "2026-09-22"
        private const val NEXT_DATE = "2026-09-23"
        private const val HISTORY_DATE = "2026-09-18"
        private const val LEG_EXTENSION = 10L
        private const val SEATED_LEG_CURL = 20L
        private const val BENCH_PRESS = 30L
        private const val TIMEOUT = 2_000L
    }
}
