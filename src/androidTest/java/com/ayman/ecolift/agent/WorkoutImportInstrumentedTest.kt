package com.ayman.ecolift.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WeightLbs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutImportInstrumentedTest {
    private lateinit var db: AppDatabase
    private lateinit var agent: AgentOrchestrator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        agent = AgentOrchestrator(
            router = IntentRouter(engine = null),
            tools = AgentToolsImpl(db),
            patchApplier = PatchService(db, PatchValidator()),
            engine = null,
            today = { "2026-05-17" }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun datedImportPersistsMessyRowsThroughRoomAndFuzzyExerciseMatch() = runBlocking {
        val calfId = db.exerciseDao().insert(
            Exercise(name = "Standing Calf Raise Machine", muscleGroups = "CALVES")
        )
        val hipId = db.exerciseDao().insert(
            Exercise(name = "Hip Abduction", muscleGroups = "GLUTES")
        )

        val result = agent.process(
            """
            5/12/26
            Standing Calf Raise Machine 90 lbs for 12, 10, 8
            Hip Abdction 150lbs for 10, 10, 10
            """.trimIndent()
        )

        assertTrue("Expected ImportApplied but got $result", result is AgentTurn.ImportApplied)
        val applied = result as AgentTurn.ImportApplied
        assertEquals(6, applied.appliedPatchCount)
        assertEquals(0, applied.pendingReviews.size)
        assertNotNull(applied.auditId)

        val calfSets = db.workoutSetDao().getForDateAndExercise("2026-05-12", calfId)
        assertEquals(listOf(1, 2, 3), calfSets.map { it.setNumber })
        assertEquals(
            listOf(WeightLbs.fromWholePounds(90), WeightLbs.fromWholePounds(90), WeightLbs.fromWholePounds(90)),
            calfSets.map { it.weightLbs }
        )
        assertEquals(listOf(12, 10, 8), calfSets.map { it.reps })

        val hipSets = db.workoutSetDao().getForDateAndExercise("2026-05-12", hipId)
        assertEquals(listOf(1, 2, 3), hipSets.map { it.setNumber })
        assertEquals(
            listOf(WeightLbs.fromWholePounds(150), WeightLbs.fromWholePounds(150), WeightLbs.fromWholePounds(150)),
            hipSets.map { it.weightLbs }
        )
        assertEquals(listOf(10, 10, 10), hipSets.map { it.reps })
    }

    @Test
    fun datedImportKeepsUnsafeRowsForPendingReviewWithoutDroppingText() = runBlocking {
        val result = agent.process(
            """
            5/13/26
            mystery machine 3 plates x 12, 12
            """.trimIndent()
        )

        assertTrue("Expected ImportApplied but got $result", result is AgentTurn.ImportApplied)
        val applied = result as AgentTurn.ImportApplied
        assertEquals(0, applied.appliedPatchCount)
        assertEquals(1, applied.pendingReviews.size)
        assertEquals("mystery machine 3 plates x 12, 12", applied.pendingReviews.single().rawInput)
        assertEquals("2026-05-13", applied.pendingReviews.single().dateLogged)
        assertEquals(emptyList<com.ayman.ecolift.data.WorkoutSet>(), db.workoutSetDao().getForDate("2026-05-13"))
    }
}
