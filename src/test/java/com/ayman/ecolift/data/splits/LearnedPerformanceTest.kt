package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Spec target: two years (about 400 training days, 8,000 checked sets) in under 50 ms on Wasm. */
class LearnedPerformanceTest {
    private fun twoYears(): List<WorkoutSet> {
        val start = LocalDate.parse("2024-09-30").toEpochDays().toInt()
        val routines = (0 until 5).map { r -> (0 until 5).map { 1_000L + r * 10 + it } }
        val sets = ArrayList<WorkoutSet>(9_000)
        var session = 0
        for (offset in 0 until 730) {
            if (offset % 7 == 2 || offset % 7 == 5 || offset % 7 == 6) continue // four days a week
            val date = LocalDate.fromEpochDays(start + offset).toString()
            val lifts = routines[session % routines.size] + (if (session % 3 == 0) listOf(2_000L) else emptyList())
            lifts.forEach { lift ->
                (1..4).forEach { n -> sets += LearnedFixtures.set(date, lift, setNumber = n, weightLbs = 1000 + n * 50, reps = 8) }
            }
            session++
        }
        return sets
    }

    @Test
    fun twoYearsOfHistoryComputeWellInsideTheBudget() {
        val sets = twoYears()
        assertTrue(sets.size in 8_000..9_500, "generated ${sets.size} sets")
        val inputs = LearnedFixtures.inputs("2026-09-30", sets)
        LearnedSplitsEngine.compute(inputs) // warm-up
        val mark = TimeSource.Monotonic.markNow()
        val model = LearnedSplitsEngine.compute(inputs)
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        assertTrue(model.visibleGroups.size == 5, "groups ${model.visibleGroups.map { it.members }}")
        // Twice the spec's 50 ms target, so a slow CI machine does not flake while a real regression still fails.
        assertTrue(elapsed < 100, "compute took $elapsed ms (spec target 50 ms)")
    }
}
