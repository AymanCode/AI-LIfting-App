package com.ayman.ecolift.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class DayUndoCopyTest {
    private fun DayUndoCopy.line() = headline.joinToString("") { it.text } + " " + verb
    private fun DayUndoCopy.sub() = detail.joinToString("") { it.text }

    @Test fun started() { val c = startedCopy("Upper Strength", 3, 6, 18); assertEquals("Upper Strength started", c.line()); assertEquals("6 lifts · 18 sets", c.sub()); assertEquals(DayUndoTint.Split(3), c.tint) }

    @Test fun loaded_with_previous_split_and_checked() { val c = loadedCopy("Push A", 4, "Upper Strength", 18, 4); assertEquals("Push A loaded", c.line()); assertEquals("replaced Upper Strength · 4 checked sets", c.sub()) }

    @Test fun loaded_without_split_or_checked() { assertEquals("replaced 5 sets", loadedCopy("Push A", 4, null, 5, 0).sub()) }

    @Test fun cleared() { assertEquals("Workout cleared", clearedCopy(null, null, 3, 0).line()); assertEquals("18 sets · 4 checked", clearedCopy("Upper Strength", 3, 18, 4).sub()) }

    @Test fun added_one_and_many() {
        assertEquals("Leg Press added", addedCopy(listOf(7L to "Leg Press"), 4).line())
        assertEquals("1 set", addedCopy(listOf(7L to "Leg Press"), 1).sub())
        val many = addedCopy(listOf(7L to "Leg Press", 8L to "Hip Thrust"), 7)
        assertEquals("2 lifts added", many.line()); assertEquals("Leg Press, Hip Thrust", many.sub())
    }

    @Test fun removals() {
        assertEquals("Barbell Bench Press removed", liftRemovedCopy(1, "Barbell Bench Press", 4, 2).line())
        assertEquals("4 sets · 2 checked", liftRemovedCopy(1, "Barbell Bench Press", 4, 2).sub())
        assertEquals("Set 3 removed", setRemovedCopy(1, "Barbell Bench Press", 3, false).line())
        assertEquals("Warm-up removed", setRemovedCopy(1, "Barbell Bench Press", 1, true).line())
    }
}
