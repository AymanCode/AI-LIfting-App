package com.ayman.ecolift.agent

import com.ayman.ecolift.data.WeightLbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutImportTextParserTest {

    private fun lbs(value: Int): Int = WeightLbs.fromWholePounds(value)!!

    @Test
    fun `parses date headers and inherited exercise lines`() {
        val draft = WorkoutImportTextParser.parse(
            text = """
                5/12/26
                Bench 135x8, 155x5, 155x4
                Incline dumbbell 50s x 10, 10, 8
            """.trimIndent(),
            defaultDate = "2026-05-17"
        )

        requireNotNull(draft)
        assertEquals(2, draft.entries.size)
        assertEquals("2026-05-12", draft.entries[0].date)
        assertEquals("Bench", draft.entries[0].exerciseQuery)
        assertEquals(listOf(lbs(135), lbs(155), lbs(155)), draft.entries[0].sets.map { it.weightLbs })
        assertEquals(listOf(8, 5, 4), draft.entries[0].sets.map { it.reps })
        assertEquals("Incline dumbbell", draft.entries[1].exerciseQuery)
        assertEquals(listOf(10, 10, 8), draft.entries[1].sets.map { it.reps })
    }

    @Test
    fun `parses month date inline notes with old gym shorthand`() {
        val draft = WorkoutImportTextParser.parse(
            text = "May 3 - bench 185 5 5 4; rows 135x10x3; curls 30s 12/10/8",
            defaultDate = "2026-05-17"
        )

        requireNotNull(draft)
        assertEquals(3, draft.entries.size)
        assertTrue(draft.entries.all { it.date == "2026-05-03" })
        assertEquals("bench", draft.entries[0].exerciseQuery)
        assertEquals(listOf(5, 5, 4), draft.entries[0].sets.map { it.reps })
        assertEquals("rows", draft.entries[1].exerciseQuery)
        assertEquals(listOf(10, 10, 10), draft.entries[1].sets.map { it.reps })
        assertEquals("curls", draft.entries[2].exerciseQuery)
        assertEquals(listOf(12, 10, 8), draft.entries[2].sets.map { it.reps })
    }

    @Test
    fun `preserves unclear lines for pending review`() {
        val draft = WorkoutImportTextParser.parse(
            text = """
                Yesterday:
                bench 135x8
                leg press 3 plates x 12, 12
            """.trimIndent(),
            defaultDate = "2026-05-17"
        )

        requireNotNull(draft)
        assertEquals(1, draft.entries.size)
        assertEquals("2026-05-16", draft.entries[0].date)
        assertEquals(1, draft.unresolvedLines.size)
        assertEquals("leg press 3 plates x 12, 12", draft.unresolvedLines[0].rawLine)
        assertEquals("2026-05-16", draft.unresolvedLines[0].date)
    }
}
