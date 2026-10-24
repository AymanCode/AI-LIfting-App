package com.ayman.ecolift.data.progress

import kotlin.test.Test
import kotlin.test.assertEquals

class StepInferenceTest {
    @Test
    fun gcdInfersFivePoundStep() {
        assertEquals(
            50,
            inferWeightStepStored(
                distinctStoredWeights = listOf(1850, 1900, 2000),
                exerciseName = "Machine Press",
                topStoredWeight = 2000,
            ),
        )
    }

    @Test
    fun heavyBarbellFallbackUsesTenPoundStep() {
        assertEquals(
            100,
            inferWeightStepStored(
                distinctStoredWeights = listOf(2400),
                exerciseName = "Incline Barbell Bench",
                topStoredWeight = 2400,
            ),
        )
    }

    @Test
    fun dumbbellFallbackUsesFivePoundStep() {
        assertEquals(
            50,
            inferWeightStepStored(
                distinctStoredWeights = listOf(250),
                exerciseName = "Dumbbell Curl",
                topStoredWeight = 250,
            ),
        )
    }

    @Test
    fun implausibleGcdRoundsDownToNearestPlausibleStep() {
        assertEquals(
            50,
            inferWeightStepStored(
                distinctStoredWeights = listOf(1000, 1075, 1150),
                exerciseName = "Cable Row",
                topStoredWeight = 1150,
            ),
        )
    }

    @Test
    fun tinyGcdRoundsUpToMinimumPlausibleStep() {
        assertEquals(
            25,
            inferWeightStepStored(
                distinctStoredWeights = listOf(1000, 1020, 1040),
                exerciseName = "Cable Row",
                topStoredWeight = 1040,
            ),
        )
    }

    @Test
    fun emptyOrSingleMachineFallbackUsesFivePoundStep() {
        assertEquals(
            50,
            inferWeightStepStored(
                distinctStoredWeights = emptyList(),
                exerciseName = "Leg Press Machine",
                topStoredWeight = null,
            ),
        )
        assertEquals(
            50,
            inferWeightStepStored(
                distinctStoredWeights = listOf(1800),
                exerciseName = "Leg Press Machine",
                topStoredWeight = 1800,
            ),
        )
    }
}
