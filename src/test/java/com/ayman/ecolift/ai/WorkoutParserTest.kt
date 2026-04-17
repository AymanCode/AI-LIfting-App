package com.ayman.ecolift.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutParserTest {

    private val parser = WorkoutParser()

    @Test
    fun `test parseWorkout currently returns null as stub`() = runTest {
        // Testing the current stub implementation behavior
        val result = parser.parseWorkout("bench 3x10 at 135").first()
        assert(result == null)
    }

    @Test
    fun `test parseWorkout with empty input`() = runTest {
        // Testing that even with empty input, the flow behaves predictably
        val result = parser.parseWorkout("").first()
        assert(result == null)
    }

    /**
     * TODO: Once the real parsing logic is implemented, 
     * add tests for valid workout strings like:
     * "bench press 3 sets of 10 at 135 lbs"
     * and edge cases like malformed text.
     */
}