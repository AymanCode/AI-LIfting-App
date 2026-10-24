package com.ayman.ecolift.ui.navigation

import com.ayman.ecolift.ui.viewmodel.ProgressExerciseUi
import com.ayman.ecolift.ui.viewmodel.ProgressPointUi
import com.ayman.ecolift.ui.viewmodel.ProgressUiState
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressReportWindowTest {
    @Test
    fun all_report_spans_its_own_history_without_an_overview_list() {
        val end = LocalDate(2026, 10, 23)
        val state = ProgressUiState(
            selectedExerciseId = 10L,
            timeframe = TimeframeFilter.ALL_TIME,
            chartPoints = listOf(point("2026-10-23"), point("2001-01-01"), point("2014-01-01")),
        )

        val start = progressChartWindowStart(state, end)

        assertEquals(LocalDate(2001, 1, 1), start)
        assertEquals(0f, progressDateFraction(LocalDate(2001, 1, 1), start, end))
        assertEquals(0.50371313f, progressDateFraction(LocalDate(2014, 1, 1), start, end), 0.000001f)
        assertEquals(1f, progressDateFraction(end, start, end))
    }

    private fun point(date: String) = ProgressPointUi(date, date, 0, 100f, 1000, 5, 5, 5)

    @Test
    fun overview_all_keeps_the_earliest_exercise_session_date() {
        val end = LocalDate(2026, 10, 23)
        val state = ProgressUiState(
            timeframe = TimeframeFilter.ALL_TIME,
            exercises = listOf(exercise(1, "2010-06-15"), exercise(2, "2001-01-01")),
            chartPoints = listOf(point("1999-01-01")),
        )
        assertEquals(LocalDate(2001, 1, 1), progressChartWindowStart(state, end))
    }

    @Test
    fun selected_report_uses_its_history_even_when_the_overview_list_is_present() {
        val state = ProgressUiState(
            selectedExerciseId = 10,
            timeframe = TimeframeFilter.ALL_TIME,
            exercises = listOf(exercise(20, "2001-01-01")),
            chartPoints = listOf(point("2014-01-01"), point("2026-10-23")),
        )
        assertEquals(LocalDate(2014, 1, 1), progressChartWindowStart(state, LocalDate(2026, 10, 23)))
    }

    @Test
    fun finite_report_ranges_keep_calendar_boundaries_when_the_end_moves() {
        val expected = mapOf(
            TimeframeFilter.TWO_WEEKS to "2026-10-10",
            TimeframeFilter.ONE_MONTH to "2026-09-23",
            TimeframeFilter.THREE_MONTHS to "2026-07-23",
            TimeframeFilter.SIX_MONTHS to "2026-04-23",
            TimeframeFilter.ONE_YEAR to "2025-10-23",
        )
        for ((range, start) in expected) {
            val state = ProgressUiState(selectedExerciseId = 10, timeframe = range, chartPoints = listOf(point("2001-01-01")))
            assertEquals(LocalDate.parse(start), progressChartWindowStart(state, LocalDate(2026, 10, 23)), range.name)
        }
        val twoWeeks = ProgressUiState(selectedExerciseId = 10, timeframe = TimeframeFilter.TWO_WEEKS)
        assertEquals(LocalDate(2026, 9, 26), progressChartWindowStart(twoWeeks, LocalDate(2026, 10, 9)))
    }

    @Test
    fun empty_all_report_and_empty_overview_use_the_window_end() {
        val end = LocalDate(2026, 10, 23)
        assertEquals(end, progressChartWindowStart(ProgressUiState(timeframe = TimeframeFilter.ALL_TIME), end))
        assertEquals(end, progressChartWindowStart(ProgressUiState(selectedExerciseId = 10, timeframe = TimeframeFilter.ALL_TIME), end))
    }

    private fun exercise(id: Long, firstDate: String) = ProgressExerciseUi(
        exerciseId = id,
        name = "Lift $id",
        sessions = 1,
        lastSessionDate = "Oct 23",
        lastSessionIsoDate = "2026-10-23",
        lastSessionSummary = "100 lb x5",
        changePercentage = 0f,
        firstSessionIsoDate = firstDate,
        trend = emptyList(),
    )
}
