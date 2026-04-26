package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
@Ignore("Requires an Android test context or injected database; this is not a valid local JVM test yet.")
class LogViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LogViewModel
    private lateinit var application: Application

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mock(Application::class.java)
        // Note: In a real environment, we'd mock the database/DAOs properly.
        // For this test, we are focused on the "Local-First" logic in the ViewModel.
        viewModel = LogViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addSet should immediately update local session state with temporary ID`() = runTest {
        val exerciseId = 1L
        viewModel.addSet(exerciseId)
        
        val state = viewModel.uiState.first()
        val exercise = state.exercises.find { it.exerciseId == exerciseId }
        
        assertTrue("Exercise should be added to UI", exercise != null)
        assertTrue("Set should have a temporary negative ID", exercise!!.sets.first().id < 0)
        assertEquals("Set number should be 1", 1, exercise.sets.first().setNumber)
    }

    @Test
    fun `adjustWeight should update local state immediately and not persist to DB yet`() = runTest {
        val exerciseId = 1L
        viewModel.addSet(exerciseId)
        val tempId = viewModel.uiState.first().exercises.first().sets.first().id
        
        viewModel.adjustWeight(tempId, 10)
        
        val updatedState = viewModel.uiState.first()
        val updatedSet = updatedState.exercises.first().sets.first()
        
        assertEquals("Weight should be updated locally", 10, updatedSet.weightLbs)
        // Logic check: Persistence is only on 'toggleCompleted' or explicitly handled.
    }
}
