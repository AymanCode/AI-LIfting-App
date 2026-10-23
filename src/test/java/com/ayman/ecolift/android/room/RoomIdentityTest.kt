package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomIdentityTest {
    @Test
    fun renumberSetsRanksLowestNumberThenLowestId() {
        val sets = listOf(
            set(id = 207, setNumber = 3, reps = 10),
            set(id = 194, setNumber = 3, reps = 12),
            set(id = 206, setNumber = 2, reps = 10),
        )

        val ranked = renumberSets(sets)

        assertEquals(listOf(206L, 194L, 207L), ranked.map { it.id })
        assertEquals(listOf(1, 2, 3), ranked.map { it.setNumber })
        assertEquals(listOf(10, 12, 10), ranked.map { it.reps })
    }

    @Test
    fun chooseMergeKeeperPrefersMoreCheckedSetsThenLaterDateThenOlderRowThenLowerId() {
        val older = Exercise(id = 1, name = "Bench", createdAt = 10)
        val newer = Exercise(id = 2, name = "Bench Press", createdAt = 20)
        val moreSets = listOf(
            set(id = 1, exerciseId = 2, date = "2026-01-01"),
            set(id = 2, exerciseId = 2, date = "2026-01-02"),
            set(id = 3, exerciseId = 1, date = "2026-02-01"),
        )
        assertEquals(2L, chooseMergeKeeper(older, newer, moreSets))

        val laterDate = listOf(
            set(id = 1, exerciseId = 1, date = "2026-01-01"),
            set(id = 2, exerciseId = 2, date = "2026-03-01"),
        )
        assertEquals(2L, chooseMergeKeeper(older, newer, laterDate))

        val sameHistory = listOf(
            set(id = 1, exerciseId = 1, date = "2026-01-01"),
            set(id = 2, exerciseId = 2, date = "2026-01-01"),
        )
        assertEquals(1L, chooseMergeKeeper(older, newer, sameHistory))

        val sameAge = older.copy(createdAt = 20)
        assertEquals(1L, chooseMergeKeeper(sameAge, newer, sameHistory))
    }

    @Test
    fun orderDistinctPairSortsIdsAndDropsASelfPair() {
        assertEquals(4L to 9L, orderDistinctPair(9, 4))
        assertNull(orderDistinctPair(4, 4))
    }

    @Test
    fun mergeSetFieldsKeepsUnchangedValuesFromTheStoredRow() {
        val observed = set(id = 5, reps = 5, weightLbs = 100)
        val current = observed.copy(reps = 8, rowRevision = 3, syncId = "sync-5")
        val requested = observed.copy(weightLbs = 120, syncId = "ignored")

        val merged = mergeSetFields(observed, current, requested)

        assertEquals(8, merged.reps)
        assertEquals(120, merged.weightLbs)
        assertEquals(5L, merged.id)
        assertEquals("sync-5", merged.syncId)
        assertEquals(4L, merged.rowRevision)
    }

    private fun set(
        id: Long,
        exerciseId: Long = 1,
        date: String = "2026-05-17",
        setNumber: Int = 1,
        reps: Int? = 8,
        weightLbs: Int? = 100,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        reps = reps,
        weightLbs = weightLbs,
        completed = true,
    )
}
