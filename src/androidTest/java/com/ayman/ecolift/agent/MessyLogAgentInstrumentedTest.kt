package com.ayman.ecolift.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.android.room.RoomRepos
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.domain
import com.ayman.ecolift.data.WeightLbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessyLogAgentInstrumentedTest {
    private lateinit var db: AppDatabase
    private val reposScope = CoroutineScope(SupervisorJob())
    private lateinit var repos: RoomRepos

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repos = RoomRepos(db = db, scope = reposScope)
    }

    @After
    fun tearDown() {
        if (::repos.isInitialized) repos.close()
        reposScope.cancel()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun messyShorthandLogWritesMultipleSetsThroughPatchService() = runBlocking {
        val exerciseId = db.exerciseDao().domain().insert(
            Exercise(name = "Bench Press", muscleGroups = "CHEST")
        )
        repos.loadAll()
        val agent = AgentOrchestrator(
            router = IntentRouter(engine = null),
            tools = AgentToolsImpl(repos),
            patchApplier = PatchService(repos, PatchValidator(), InMemoryAuditStore()),
            engine = null,
            today = { "2026-05-16" }
        )

        val result = agent.process("i did Bechh Press 135x7,125x10,.85x5.")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        val sets = db.workoutSetDao().getForDateAndExercise("2026-05-16", exerciseId)
        assertEquals(3, sets.size)
        assertEquals(listOf(1, 2, 3), sets.map { it.setNumber })
        assertEquals(
            listOf(
                WeightLbs.fromWholePounds(135),
                WeightLbs.fromWholePounds(125),
                WeightLbs.fromWholePounds(85)
            ),
            sets.map { it.weightLbs }
        )
        assertEquals(listOf(7, 10, 5), sets.map { it.reps })
    }
}
