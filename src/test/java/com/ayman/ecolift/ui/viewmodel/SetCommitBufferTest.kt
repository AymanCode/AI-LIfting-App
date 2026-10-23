package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetCommitBufferTest {
    private fun row(id: Long, syncId: String, weight: Int, reps: Int = 5) =
        WorkoutSet(id = id, syncId = syncId, exerciseId = 1, date = "2026-10-08", setNumber = 1, weightLbs = weight, reps = reps)

    @Test
    fun repeated_steps_keep_the_latest_row_and_the_first_before() {
        val buffer = SetCommitBuffer()
        val original = row(1, "a", 1000)
        buffer.stage(row(1, "a", 1005), before = original)
        buffer.stage(row(1, "a", 1010), before = row(1, "a", 1005))
        val key = buffer.stage(row(1, "a", 1015), before = row(1, "a", 1010))

        val commit = buffer.take(key)
        assertEquals(1015, commit?.row?.weightLbs)
        assertEquals(original, commit?.beforeFirstStep)
        assertTrue(buffer.isEmpty)
        assertNull(buffer.take(key))
    }

    @Test
    fun flush_returns_every_set_in_first_step_order_and_empties() {
        val buffer = SetCommitBuffer()
        buffer.stage(row(2, "b", 500), before = null)
        buffer.stage(row(1, "a", 1000), before = null)
        buffer.stage(row(2, "b", 505), before = null)

        assertEquals(listOf("b" to 505, "a" to 1000), buffer.flush().map { it.row.syncId to it.row.weightLbs })
        assertTrue(buffer.isEmpty)
        assertEquals(emptyList(), buffer.flush())
    }

    @Test
    fun overlay_keeps_pending_values_over_an_echo_and_adopts_a_remapped_id() {
        val buffer = SetCommitBuffer()
        buffer.stage(row(-3, "a", 1015), before = null)
        val echo = listOf(row(42, "a", 1000), row(43, "b", 700))

        val shown = buffer.overlay(echo)

        assertEquals(listOf(42L to 1015, 43L to 700), shown.map { it.id to it.weightLbs })
        assertEquals(42L, buffer.flush().single().row.id)
        assertEquals(echo, buffer.overlay(echo), "An empty buffer passes rows through")
    }

    @Test
    fun rows_without_sync_id_key_on_id() {
        val buffer = SetCommitBuffer()
        buffer.stage(row(7, "", 100), before = null)
        buffer.stage(row(8, "", 200), before = null)
        assertEquals(listOf(7L, 8L), buffer.flush().map { it.row.id })
    }

    @Test
    fun overlay_lays_only_weight_and_reps_over_the_repository_row() {
        val buffer = SetCommitBuffer()
        buffer.stage(row(1, "a", 1015, reps = 7), before = null)
        val echo = row(1, "a", 1000).copy(completed = true, date = "2026-10-09")

        val shown = buffer.overlay(listOf(echo)).single()

        assertEquals(echo.copy(weightLbs = 1015, reps = 7), shown)
    }

    @Test
    fun rekey_moves_the_commit_keeps_its_order_and_forwards_the_old_key() {
        val buffer = SetCommitBuffer()
        val oldKey = buffer.stage(row(-5, "", 1005), before = row(-5, "", 1000))
        buffer.stage(row(2, "b", 500), before = null)
        val newKey = SetCommitBuffer.keyOf(row(205, "", 0))

        assertTrue(buffer.rekey(oldKey, newKey))
        assertTrue(buffer.has(newKey))
        assertEquals(newKey, buffer.resolve(oldKey))
        assertEquals(false, buffer.rekey(oldKey, newKey), "Nothing is left under the old key")

        // A later step under the new key joins the moved commit instead of opening a second one.
        buffer.stage(row(205, "", 1010), before = row(205, "", 1005))
        val commits = buffer.flush()
        assertEquals(listOf(205L to 1010, 2L to 500), commits.map { it.row.id to it.row.weightLbs })
        assertEquals(1000, commits.first().beforeFirstStep?.weightLbs)
        assertEquals(oldKey, buffer.resolve(oldKey), "Flushing forgets the forwards")
    }

    @Test
    fun take_through_an_old_key_finds_the_rekeyed_commit() {
        val buffer = SetCommitBuffer()
        val oldKey = buffer.stage(row(7, "", 100), before = null)
        buffer.rekey(oldKey, "sync:assigned")
        assertEquals(100, buffer.take(oldKey)?.row?.weightLbs)
        assertTrue(buffer.isEmpty)
    }
}
