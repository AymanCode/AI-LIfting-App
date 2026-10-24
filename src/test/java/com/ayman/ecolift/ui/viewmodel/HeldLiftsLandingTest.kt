package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.splits.LearnedFixtures
import com.ayman.ecolift.data.splits.LearnedSplitsEngine
import com.ayman.ecolift.data.splits.WorkedExample
import com.ayman.ecolift.ui.navigation.heldRowDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeldLiftsLandingTest {
    private val model = LearnedSplitsEngine.compute(WorkedExample.inputs())
    private fun sections(query: String = "") =
        buildHeldSections(model, WorkedExample.AS_OF, WorkedExample.exercises, WorkedExample.sets, { it }, query)

    @Test
    fun heldLiftsSitUnderTheirGroupsInDueOrderThenSolo() {
        val all = sections()
        assertEquals(listOf("Chest · Arms", "Legs", "Upper 1", "Legs", "Solo"), all.map { it.title })
        assertEquals(listOf("Bench Press", "Hip Abduction", "Incline Dumbell Hammer", "Pec Deck"), all.last().rows.map { it.name })
        assertTrue(all.flatMap { it.rows }.none { it.name == "Standing Behind The Back Skull Crusher" })
    }

    @Test
    fun fadedMembersComeLastInTheirGroup() {
        val upper = sections().single { it.title == "Upper 1" }
        assertEquals("Incline Bench Press", upper.rows.last().name)
        assertTrue(upper.rows.last().faded)
        assertEquals(1, upper.rows.count { it.faded })
    }

    @Test
    fun rowsCarryTheTicketsTopSetAndStake() {
        val lat = sections().flatMap { it.rows }.single { it.name == "Lat Pulldown" }
        assertEquals("140×10", lat.topSetLabel)
        assertEquals("beat 10", stakeText(lat.stake!!))
    }

    @Test
    fun searchFiltersEverySectionAndDropsEmptyOnes() {
        assertEquals(listOf("Legs", "Legs"), sections("leg").map { it.title })
        assertTrue(sections("nothing like this").isEmpty())
    }

    @Test
    fun everydayLiftsHaveTheirOwnSection() {
        val dates = (1..14).map { "2026-09-${(it + 10).toString().padStart(2, '0')}" }
        val sets = dates.flatMapIndexed { i, date ->
            if (i % 2 == 0) LearnedFixtures.day(date, 1, 2, 3, 9) else LearnedFixtures.day(date, 4, 5, 6, 9)
        }
        val m = LearnedSplitsEngine.compute(LearnedFixtures.inputs("2026-09-28", sets))
        val exercises = listOf(1L, 2L, 3L, 4L, 5L, 6L, 9L).map { Exercise(it, "Lift $it", "CHEST") }
        val built = buildHeldSections(m, "2026-09-28", exercises, sets, { it })
        assertEquals(listOf("Chest", "Chest", "Every session"), built.map { it.title })
        assertEquals(listOf("Lift 9"), built.last().rows.map { it.name })
    }

    @Test
    fun filtered_held_lift_keeps_its_literal_ticket_values() {
        val filtered = sections("lat pulldown")
        assertEquals(listOf("Upper 1"), filtered.map { it.title })
        val row = filtered.single().rows.single()
        assertEquals("Lat Pulldown", row.name)
        assertEquals("140\u00d710", row.topSetLabel)
        assertEquals(StakeUi.Beat(10), row.stake)
        assertEquals(false, row.faded)
    }

    @Test
    fun noHeldLiftsMeansNoSections() {
        val m = LearnedSplitsEngine.compute(LearnedFixtures.inputs("2026-09-28", LearnedFixtures.day("2026-09-27", 1)))
        assertTrue(buildHeldSections(m, "2026-09-28", emptyList(), emptyList(), { it }).isEmpty())
    }

    @Test
    fun aHeldRowReadsAsALinkToItsReport() {
        val lat = sections().flatMap { it.rows }.single { it.name == "Lat Pulldown" }
        assertEquals("Open Lat Pulldown report, 140×10, beat 10", heldRowDescription(lat))
    }
}
