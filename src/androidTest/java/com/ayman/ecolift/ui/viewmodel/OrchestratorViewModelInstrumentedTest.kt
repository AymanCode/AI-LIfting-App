package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.WeightLbs
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrchestratorViewModelInstrumentedTest {

    @Test
    fun sendMessageImportsDatedWorkoutRowsThroughProductionViewModel() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(app)
        val calfId = getOrCreateExercise(db, "QA Calf Raise Machine")
        val lateralId = getOrCreateExercise(db, "QA Lateral Raise Machine")
        val initialCalfCount = db.workoutSetDao().getForDateAndExercise("2026-05-11", calfId).size
        val initialLateralCount = db.workoutSetDao().getForDateAndExercise("2026-05-11", lateralId).size
        val viewModel = OrchestratorViewModel(app)

        viewModel.updateInput(
            "5/11/26 QA Calf Raise Machine 90lbs for 12, 10, 8; " +
                "QA Lateral Raise Machine 45x14,45x12"
        )
        viewModel.sendMessage()

        waitUntil {
            db.workoutSetDao().getForDateAndExercise("2026-05-11", calfId).size >= initialCalfCount + 3 &&
                db.workoutSetDao().getForDateAndExercise("2026-05-11", lateralId).size >= initialLateralCount + 2
        }

        val calfSets = db.workoutSetDao().getForDateAndExercise("2026-05-11", calfId).drop(initialCalfCount)
        assertEquals(listOf(12, 10, 8), calfSets.map { it.reps })
        assertEquals(
            listOf(WeightLbs.fromWholePounds(90), WeightLbs.fromWholePounds(90), WeightLbs.fromWholePounds(90)),
            calfSets.map { it.weightLbs }
        )

        val lateralSets = db.workoutSetDao().getForDateAndExercise("2026-05-11", lateralId).drop(initialLateralCount)
        assertEquals(listOf(14, 12), lateralSets.map { it.reps })
        assertEquals(
            listOf(WeightLbs.fromWholePounds(45), WeightLbs.fromWholePounds(45)),
            lateralSets.map { it.weightLbs }
        )
    }

    @Test
    fun sendMessageSavesAndResolvesPendingReviewRowsThroughProductionViewModel() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(app)
        val hipId = getOrCreateExercise(db, "QA Hip Abduction")
        val initialHipSets = db.workoutSetDao().getForDateAndExercise("2026-05-10", hipId)
        val initialHipCount = initialHipSets.size
        val nextSetNumber = (initialHipSets.maxOfOrNull { it.setNumber } ?: 0) + 1
        val pendingDao = db.pendingReviewDao()
        pendingDao.getUnresolved().forEach { pendingDao.markResolved(it.id) }
        val pendingId = pendingDao.insert(
            PendingReview(
                rawInput = "QA Hip Abdction 150lbs for 10, 10, 10",
                dateLogged = "2026-05-10"
            )
        )
        val viewModel = OrchestratorViewModel(app)

        viewModel.updateInput("all pending review items are QA Hip Abduction")
        viewModel.sendMessage()

        waitUntil {
            db.workoutSetDao().getForDateAndExercise("2026-05-10", hipId).size >= initialHipCount + 3 &&
                pendingDao.getUnresolved().none { it.id == pendingId }
        }

        val hipSets = db.workoutSetDao().getForDateAndExercise("2026-05-10", hipId).drop(initialHipCount)
        assertEquals(listOf(nextSetNumber, nextSetNumber + 1, nextSetNumber + 2), hipSets.map { it.setNumber })
        assertEquals(listOf(10, 10, 10), hipSets.map { it.reps })
        assertEquals(
            listOf(WeightLbs.fromWholePounds(150), WeightLbs.fromWholePounds(150), WeightLbs.fromWholePounds(150)),
            hipSets.map { it.weightLbs }
        )
        assertTrue(pendingDao.getUnresolved().none { it.id == pendingId })
    }

    private suspend fun getOrCreateExercise(db: AppDatabase, name: String): Long {
        val existing = db.exerciseDao().getByExactName(name)
        if (existing != null) return existing.id
        db.exerciseDao().insert(Exercise(name = name, muscleGroups = "QA"))
        return requireNotNull(db.exerciseDao().getByExactName(name)).id
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(100)
            }
        }
    }
}
