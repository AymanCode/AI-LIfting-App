package com.ayman.ecolift.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSetAdjusterTest {
    @Test
    fun shiftsOtherSetsByTheSameRepDifference() {
        assertEquals(
            4,
            calculateSmartSuggestedReps(
                sourceNewReps = 5,
                sourceBaselineReps = 8,
                targetBaselineReps = 7,
                sourceSetNumber = 1,
                targetSetNumber = 2,
            )
        )

        assertEquals(
            2,
            calculateSmartSuggestedReps(
                sourceNewReps = 5,
                sourceBaselineReps = 8,
                targetBaselineReps = 5,
                sourceSetNumber = 1,
                targetSetNumber = 3,
            )
        )
    }

    @Test
    fun keepsALaterSetsOffsetWhenTheEditedSetGoesUp() {
        assertEquals(
            12,
            calculateSmartSuggestedReps(
                sourceNewReps = 10,
                sourceBaselineReps = 8,
                targetBaselineReps = 10,
                sourceSetNumber = 1,
                targetSetNumber = 2,
            )
        )
    }

    @Test
    fun movesAnEarlierSetByTheSameRepDifference() {
        assertEquals(
            13,
            calculateSmartSuggestedReps(
                sourceNewReps = 10,
                sourceBaselineReps = 5,
                targetBaselineReps = 8,
                sourceSetNumber = 3,
                targetSetNumber = 1,
            )
        )
    }

    @Test
    fun fallsBackToSourceRepsWhenNoLoadedPatternExists() {
        assertEquals(
            6,
            calculateSmartSuggestedReps(
                sourceNewReps = 6,
                sourceBaselineReps = null,
                targetBaselineReps = null,
                sourceSetNumber = 1,
                targetSetNumber = 2,
            )
        )
    }
}
