package com.ayman.ecolift.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleSnapshotSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `snapshot round trips through json`() {
        val snapshot = CycleSnapshot(
            startDate = "2026-01-01",
            endDate = "2026-01-31",
            totals = CycleTotals(sessions = 2, totalVolumeLbs = 1_850L, totalSets = 2, spanDays = 31),
            splits = listOf(
                SplitSnapshot(
                    slotId = 10L,
                    bucketKind = SplitBucketKind.Real,
                    name = "Push",
                    orderIndex = 0,
                    firstUsedDate = "2026-01-05",
                    lastUsedDate = "2026-01-12",
                    usageCount = 2,
                    exercises = listOf(
                        ExerciseSnapshot(
                            exerciseId = 1L,
                            name = "Bench Press",
                            isBodyweight = false,
                            sessions = listOf(
                                SessionPoint("2026-01-05", 185f, 215.83f, 925L, 5, 1),
                            ),
                            startE1rm = 215.83f,
                            endE1rm = 225f,
                            startTopWeight = 185f,
                            endTopWeight = 195f,
                            startVolumeLbs = 925L,
                            endVolumeLbs = 925L,
                        ),
                    ),
                ),
            ),
        )

        val decoded = json.decodeFromString<CycleSnapshot>(json.encodeToString(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `older split json defaults bucket kind to real`() {
        val payload = """
            {"schemaVersion":1,"startDate":"2026-01-01","endDate":"2026-01-02",
             "totals":{"sessions":0,"totalVolumeLbs":0,"totalSets":0,"spanDays":2},
             "splits":[{"slotId":10,"name":"Push","orderIndex":0,
                        "firstUsedDate":null,"lastUsedDate":null,"usageCount":0,"exercises":[]}]}
        """.trimIndent()

        val decoded = json.decodeFromString<CycleSnapshot>(payload)

        assertEquals(SplitBucketKind.Real, decoded.splits.single().bucketKind)
    }
}
