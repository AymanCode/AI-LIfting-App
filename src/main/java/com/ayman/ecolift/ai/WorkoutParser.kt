package com.ayman.ecolift.ai

import com.ayman.ecolift.data.WorkoutLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class WorkoutParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseWorkout(input: String): Flow<WorkoutLog?> = flow {
        emit(null)
    }
}
