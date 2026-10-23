package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.UNCLASSIFIED_MUSCLE_GROUPS
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressSortTest {
    private val end = LocalDate(2026, 10, 14)
    private val start = progressWindowStart(TimeframeFilter.ONE_MONTH, end)

    private fun lift(
        name: String,
        points: Int,
        change: Float = 0f,
        last: String = "2026-10-07",
        muscle: String = "Quads",
        sessions: Int = points,
    ) = ProgressExerciseUi(
        exerciseId = name.hashCode().toLong(), name = name, muscleGroups = muscle,
        sessions = sessions, lastSessionDate = last, lastSessionIsoDate = last,
        lastSessionSummary = "100 × 8", changePercentage = change, trend = emptyList(),
        overview = QuietProgressOverview(points = List(points) {
            QuietProgressPoint("2026-10-${(it + 1).toString().padStart(2, '0')}", 100f, "100 lb × 8")
        }),
    )

    private fun names(rows: List<ProgressExerciseUi>, mode: ProgressSortMode, query: String = "") =
        organizeProgressExercises(rows, query, mode).map { it.name }

    private fun set(date: String, weight: Int = 1000, completed: Boolean = true, warmup: Boolean = false) =
        WorkoutSet(exerciseId = 1, date = date, setNumber = 1, weightLbs = weight,
            reps = 8, completed = completed, isWarmup = warmup)

    @Test fun everySortKeepsInsufficientDataAfterDecliningAndSteadyLifts() {
        val rows = listOf(
            lift("A Solo", 1, last = "2026-10-14", muscle = "Chest"),
            lift("B Empty", 0, last = "", muscle = "Back"),
            lift("C Changed load", 1, last = "2026-10-10", muscle = "Back", sessions = 4),
            lift("X Declining", 2, -10f),
            lift("Y Steady", 2),
            lift("Z Rising", 2, 10f, last = "2026-10-08", muscle = "Chest"),
        )
        assertEquals(listOf("Z Rising", "Y Steady", "X Declining", "A Solo", "B Empty", "C Changed load"), names(rows, ProgressSortMode.TREND))
        assertEquals(listOf("Z Rising", "X Declining", "Y Steady", "A Solo", "C Changed load", "B Empty"), names(rows, ProgressSortMode.RECENT))
        assertEquals(listOf("X Declining", "Y Steady", "Z Rising", "A Solo", "B Empty", "C Changed load"), names(rows, ProgressSortMode.NAME))
        assertEquals(listOf("X Declining", "Y Steady", "Z Rising", "C Changed load", "A Solo", "B Empty"), names(rows, ProgressSortMode.SESSIONS))
        assertEquals(listOf("Z Rising", "X Declining", "Y Steady", "B Empty", "C Changed load", "A Solo"), names(rows, ProgressSortMode.BODY_PART))
    }

    @Test fun searchAndAllInsufficientResultsKeepTheirSelectedSort() {
        val rows = listOf(lift("A Press", 1), lift("Z Press", 2, -10f), lift("B Row", 2, 5f))
        assertEquals(listOf("Z Press", "A Press"), names(rows, ProgressSortMode.NAME, "  pReSs  "))
        assertEquals(listOf("A Press"), names(rows, ProgressSortMode.RECENT, "A Press"))
        assertEquals(emptyList(), names(rows, ProgressSortMode.NAME, "missing"))
        val sparse = listOf(lift("Z Solo", 1), lift("A Empty", 0, last = ""))
        assertEquals(listOf("A Empty", "Z Solo"), names(sparse, ProgressSortMode.NAME))
        assertEquals(listOf("Z Solo", "A Empty"), names(sparse, ProgressSortMode.RECENT))
        assertEquals(emptyList(), names(emptyList(), ProgressSortMode.TREND))
    }

    @Test fun unclassifiedMusclesStayLastWithinEachDataBucket() {
        val rows = listOf(lift("A Known solo", 1, muscle = "Back"),
            lift("B Unknown solo", 1, muscle = UNCLASSIFIED_MUSCLE_GROUPS),
            lift("C Unknown trend", 2, muscle = UNCLASSIFIED_MUSCLE_GROUPS),
            lift("Z Known trend", 2, muscle = "Chest"))
        assertEquals(listOf("Z Known trend", "C Unknown trend", "A Known solo", "B Unknown solo"), names(rows, ProgressSortMode.BODY_PART))
    }

    @Test fun wideningTheWindowPromotesOnlyNewlyComparableHistory() {
        val history = listOf(set("2026-08-14"), set("2026-10-14"))
        val narrow = lift("A Older lift", 0).copy(overview = buildQuietProgressOverview(history, false, start, end))
        val wide = narrow.copy(overview = buildQuietProgressOverview(history, false,
            progressWindowStart(TimeframeFilter.THREE_MONTHS, end), end))
        val other = lift("Z Steady", 2)
        assertEquals(1, narrow.overview.points.size)
        assertEquals(2, wide.overview.points.size)
        assertEquals(listOf("Z Steady", "A Older lift"), names(listOf(narrow, other), ProgressSortMode.NAME))
        assertEquals(listOf("A Older lift", "Z Steady"), names(listOf(wide, other), ProgressSortMode.NAME))
    }

    @Test fun draftsWarmupsFutureAndRepeatedSetsDoNotCreateComparisons() {
        val history = listOf(set("2026-09-13"), set("2026-10-01", completed = false),
            set("2026-10-02", warmup = true), set("2026-10-14"),
            set("2026-10-14").copy(setNumber = 2), set("2026-10-15"))
        val sparse = lift("A Sparse", 0).copy(overview = buildQuietProgressOverview(history, false, start, end))
        assertEquals(listOf("2026-10-14"), sparse.overview.points.map { it.date })
        assertEquals(listOf("Z Steady", "A Sparse"), names(listOf(sparse, lift("Z Steady", 2)), ProgressSortMode.NAME))
    }

    @Test fun bodyweightSessionsAtDifferentLoadsNeedTwoComparablePoints() {
        val history = listOf(set("2026-09-20", 0), set("2026-10-10", 100))
        val sparse = lift("A Pull-up", 0, sessions = 2).copy(isBodyweight = true,
            overview = buildQuietProgressOverview(history, true, start, end))
        val comparable = sparse.copy(overview = buildQuietProgressOverview(history + set("2026-10-14", 100), true, start, end))
        assertEquals(listOf("2026-10-10"), sparse.overview.points.map { it.date })
        assertEquals(listOf("2026-10-10", "2026-10-14"), comparable.overview.points.map { it.date })
        assertEquals(listOf("Z Steady", "A Pull-up"), names(listOf(sparse, lift("Z Steady", 2)), ProgressSortMode.SESSIONS))
        assertEquals(listOf("A Pull-up", "Z Steady"), names(listOf(comparable, lift("Z Steady", 2)), ProgressSortMode.NAME))
    }
}
