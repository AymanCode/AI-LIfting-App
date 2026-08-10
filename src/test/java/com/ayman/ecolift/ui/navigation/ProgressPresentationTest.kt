package com.ayman.ecolift.ui.navigation

import com.ayman.ecolift.ui.viewmodel.ProgressMetric
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressPresentationTest {

    @Test
    fun `blank muscle groups prompt identification`() {
        assertTrue(progressNeedsMuscleGroup(""))
        assertEquals("Identify muscle group", progressMuscleGroupLabel(""))
    }

    @Test
    fun `known muscle groups render as readable labels`() {
        assertFalse(progressNeedsMuscleGroup("CHEST · TRICEPS"))
        assertEquals("Chest + Triceps", progressMuscleGroupLabel("CHEST · TRICEPS"))
    }

    @Test
    fun `trend label only appears for real movement`() {
        assertEquals("+7.5%", progressTrendLabel(7.5f))
        assertEquals("-2.0%", progressTrendLabel(-2f))
        assertNull(progressTrendLabel(0f))
    }

    @Test
    fun `chart insight summarizes metric movement without advice copy`() {
        val points = listOf(
            ExerciseDataPoint(LocalDate.of(2026, 4, 1), estimatedOneRm = 240f, maxWeight = 180f, totalVolume = 8_000f),
            ExerciseDataPoint(LocalDate.of(2026, 5, 1), estimatedOneRm = 258f, maxWeight = 185f, totalVolume = 9_500f),
        )

        assertEquals(
            "Estimated 1RM up 18 lbs over 3M",
            progressChartInsight(points, ProgressMetric.ESTIMATED_1RM, TimeframeFilter.THREE_MONTHS),
        )
        assertEquals(
            "Volume up 1,500 lbs over 3M",
            progressChartInsight(points, ProgressMetric.VOLUME, TimeframeFilter.THREE_MONTHS),
        )
    }
}
