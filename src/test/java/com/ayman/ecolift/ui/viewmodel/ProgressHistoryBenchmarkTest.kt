package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.demo.DemoGenAiEngine
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProgressHistoryBenchmarkTest {
    @Test
    fun first_projection_over_exported_history() {
        val path = System.getProperty("pc.history.fixture")
        assumeNotNull(path)
        val fixture = JSONObject(File(path!!).readText())
        val sets = fixture.getJSONArray("workout_set").rows().map {
            WorkoutSet(it.getLong("id"), it.getLong("exerciseId"), it.getString("date"), it.getInt("setNumber"),
                it.intOrNull("weightLbs"), it.intOrNull("reps"), it.getInt("isBodyweight") != 0,
                it.getInt("completed") != 0, it.intOrNull("restTimeSeconds"))
        }
        val exercises = fixture.getJSONArray("exercise").rows().map {
            Exercise(it.getLong("id"), it.getString("name"), it.getString("muscleGroups"), it.getInt("isBodyweight") != 0, it.getLong("createdAt"))
        }
        val days = fixture.getJSONArray("workout_day").rows().map {
            WorkoutDay(it.getString("date"), it.intOrNull("cycleSlotType"), it.intOrNull("cycleSlotOccurrence"),
                if (it.isNull("alternativeForDate")) null else it.getString("alternativeForDate"), it.longOrNull("cycleSlotId"))
        }
        val slots = fixture.getJSONArray("cycle_slot").rows().map {
            CycleSlot(it.getLong("id"), it.getString("name"), it.getInt("orderIndex"))
        }
        val members = fixture.getJSONArray("split_exercise").rows().map {
            SplitExercise(it.getLong("id"), it.getLong("splitId"), it.getLong("exerciseId"), it.getInt("orderIndex"))
        }
        val cycle = fixture.getJSONArray("cycle").getJSONObject(0)
        val settings = fixture.getJSONArray("user_settings").getJSONObject(0)
        val today = LocalDate(2026, 10, 23)
        val seed = DemoSeedData(today, exercises, sets, UserSettings(userBodyweightLbs = settings.intOrNull("user_bodyweight_lbs")?.div(10)),
            Cycle(cycle.getInt("id"), cycle.getInt("numTypes"), cycle.getInt("isActive") != 0, cycle.intOrNull("nextSessionType"),
                cycle.getString("startDate"), cycle.getString("name")), slots, members, days, emptyList(), emptyList(), emptyList(), SetQueries.usageCounts(sets))
        assertEquals(26235, sets.size)
        assertEquals(28, exercises.size)
        runVmTest {
            val repos = DemoRepos(seed)
            testScheduler.advanceUntilIdle()
            val store = ViewModelStore()
            val model = ProgressViewModel(repos, DemoGenAiEngine(), today = { today }, derivationDispatcher = Dispatchers.Main)
            store.put("benchmark", model)
            model.setVisible(true)
            try {
                val start = System.nanoTime()
                val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { model.uiState.collect() }
                testScheduler.runCurrent()
                val ready = assertIs<ProgressProjection.Ready>(model.uiState.value).state
                val elapsed = System.nanoTime() - start
                assertEquals(28, ready.exercises.size)
                assertEquals(28, ready.visibleExercises.size)
                val bench = ready.exercises.single { it.exerciseId == 1L }
                assertEquals("Bench Press", bench.name)
                assertEquals(5, bench.sessions)
                assertEquals("2026-10-23", bench.lastSessionIsoDate)
                assertEquals(26235, repos.sets.sets.value.size)
                println("PC_HISTORY_PROJECTION ns=$elapsed sets=${sets.size} lifts=${ready.exercises.size} panels=${ready.groupWall.panels.size} sha256=${fixture.getString("source_sha256")}")
                collector.cancel()
            } finally {
                store.clear()
                repos.close()
                testScheduler.runCurrent()
            }
        }
    }

    private fun JSONArray.rows() = (0 until length()).map(::getJSONObject)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
}
