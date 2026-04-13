package com.ayman.ecolift.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutLog(
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val weight: Float
)
