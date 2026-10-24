package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitPatternDetectorTest {
    @Test
    fun eligibleDaysExcludeAssignedDaysByEitherField() {
        val sets = checkedDay("2026-09-01", 1, 2, 3) + checkedDay("2026-09-02", 1, 2, 3) + checkedDay("2026-09-03", 1, 2, 3)
        val days = listOf(
            WorkoutDay(date = "2026-09-01", cycleSlotId = 10),
            WorkoutDay(date = "2026-09-02", cycleSlotType = 0),
            WorkoutDay(date = "2026-09-03"),
        )
        assertEquals(listOf("2026-09-03"), smartSplitDays(sets, days, today = "2026-09-10").map { it.date })
    }

    @Test
    fun eligibleDaysExcludeTodayAndAnythingOlderThanFiftySixDays() {
        val sets = checkedDay("2026-09-10", 1, 2, 3) + checkedDay("2026-07-16", 1, 2, 3) + checkedDay("2026-07-15", 1, 2, 3)
        // today = 2026-09-10; 56 days back is 2026-07-16 (kept); 57 days back is 2026-07-15 (dropped)
        assertEquals(listOf("2026-07-16"), smartSplitDays(sets, emptyList(), today = "2026-09-10").map { it.date })
    }

    @Test
    fun eligibleDaysNeedThreeCheckedExercises() {
        val sets = checkedDay("2026-09-01", 1, 2) +
            checkedDay("2026-09-02", 1, 2) + checked("2026-09-02", 3, completed = false) +
            checkedDay("2026-09-03", 1, 2, 3)
        assertEquals(listOf("2026-09-03"), smartSplitDays(sets, emptyList(), today = "2026-09-10").map { it.date })
    }

    private fun checked(date: String, exerciseId: Long, completed: Boolean = true) =
        WorkoutSet(exerciseId = exerciseId, date = date, setNumber = 1, weightLbs = 1000, reps = 8, completed = completed)

    private fun checkedDay(date: String, vararg ids: Long) = ids.map { checked(date, it) }
}
