package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.*
import com.ayman.ecolift.data.progress.MonthRollup
import com.ayman.ecolift.web.supabase.sync.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class WarmupProgressTest {
    @Test fun eachMetricEvidenceComesFromItsOwnWorkingSet() {
        val rows = listOf(set("2026-08-01", weight = 1500, reps = 3),
            set("2026-08-01", weight = 1200, reps = 12), set("2026-08-01", true, 2000, 30))
        val point = buildProgressChartPoints(rows, false, null).single()
        assertEquals("120 × 12", point.estimateEvidence)
        assertEquals("120 × 12", point.repEvidence)
        assertEquals(1500, point.maxWeight)
        assertEquals(3, point.repsAtTopWeight)
        assertEquals(2, point.workingSetCount)
        assertEquals(15, point.totalWorkingReps)
    }

    private fun set(date: String, warmup: Boolean = false, weight: Int = 1350, reps: Int = 8, completed: Boolean = true) =
        WorkoutSet(exerciseId = 1, date = date, setNumber = 1, weightLbs = weight, reps = reps, completed = completed, isWarmup = warmup)

    @Test fun warmupOnlySessionCannotCreateADrop() {
        val working = listOf(set("2026-08-01"), set("2026-08-10", weight = 1400))
        val withWarmupDay = working + set("2026-08-15", warmup = true, weight = 450)
        assertEquals(buildProgressChartPoints(working, false, null), buildProgressChartPoints(withWarmupDay, false, null))
    }

    @Test fun evenHeavyWarmupsAreExcludedFromEveryProgressMetric() {
        val working = set("2026-08-01")
        val warmup = working.copy(id = 2, setNumber = 2, weightLbs = 9000, reps = 30, isWarmup = true)
        val points = buildProgressChartPoints(listOf(working, warmup), false, null)
        assertEquals(1080, points.single().volume)
        assertEquals(1350, points.single().maxWeight)
        assertEquals(8, points.single().maxReps)
        val stats = buildProgressStats(listOf(working, warmup), TimeframeFilter.ALL_TIME, false, null, LocalDate.parse("2026-08-31"))
        assertEquals(135f, stats.currentPrLbs)
        assertEquals(buildProgressChartPoints(listOf(working), false, null), points)
    }

    @Test fun togglingBackRestoresTheActualSession() {
        val warmup = set("2026-08-15", true, 450)
        assertTrue(buildProgressChartPoints(listOf(warmup), false, null).isEmpty())
        assertEquals(1, buildProgressChartPoints(listOf(warmup.copy(isWarmup = false)), false, null).size)
    }

    @Test fun loggedVolumeAndWorkingVolumeAreExplicitAndRoundOnlyAfterAccumulation() {
        val rows = listOf(set("2026-08-01", weight = 453, reps = 1), set("2026-08-01", weight = 453, reps = 1), set("2026-08-01", true, 1000, 1), set("2026-08-01", weight = 9999, completed = false))
        assertEquals(190.6, WorkoutMetrics.loggedVolumeLbs(rows), 0.0001)
        assertEquals(90.6, WorkoutMetrics.workingVolumeLbs(rows), 0.0001)
        assertEquals(191L, WorkoutMetrics.roundedVolume(rows))
        val month = MonthRollup.build(2026, 8, rows, listOf(Exercise(id = 1, name = "Bench")), emptyList(), emptyList(), 180)
        assertEquals(WorkoutMetrics.loggedVolumeLbs(rows), month.totalLoadLbs)
    }

    @Test fun profileWeightNeverRewritesBodyweightHistory() {
        val rows = listOf(set("2026-08-01", weight = 150).copy(isBodyweight = true))
        assertEquals(buildProgressChartPoints(rows, true, 150), buildProgressChartPoints(rows, true, 220))
        assertEquals(0f, buildProgressChartPoints(rows, true, 220).single().estimated1RM)
        assertEquals(120, buildProgressChartPoints(rows, true, 220).single().volume)
    }

    @Test fun warmupRoleSurvivesOfflineAndSplitCopies() {
        val row = set("2026-08-01", true)
        assertTrue(row.toQueuedSetDraft().toWorkoutSet().isWarmup)
        val copies = SetQueries.buildLastSessionSetCopies("2026-08-10", 1, listOf(row))
        assertTrue(copies.single().isWarmup)
        assertFalse(copies.single().completed)
    }

    @Test fun archiveTotalsAndDetailsExcludeWarmupOnlySessions() {
        val rows = listOf(set("2026-08-01"), set("2026-08-01", true), set("2026-08-02", true))
        val snapshot = CycleSnapshotBuilder.build("2026-08-01", "2026-08-31", emptyList(), emptyList(), emptyList(), rows, emptyMap())
        assertEquals(WorkoutMetrics.WORKING_VOLUME_DEFINITION, snapshot.volumeDefinition)
        assertEquals(1, snapshot.totals.sessions)
        assertEquals(1, snapshot.totals.totalSets)
        assertEquals(1080L, snapshot.totals.totalVolumeLbs)
    }

    @Test fun bodyweightBestSetUsesRepsToBreakEqualLoadTies() {
        val rows = listOf(set("2026-08-01", weight = 0, reps = 8), set("2026-08-02", weight = 0, reps = 12))
        val stats = buildProgressStats(rows, TimeframeFilter.ALL_TIME, true, null, LocalDate.parse("2026-08-31"))
        assertTrue(stats.bestSetLabel.contains("12"))
    }

    @Test fun cardWritesAlwaysTransmitRequiredSchemaVersion() {
        val split = MarkWorkoutCardSplitV1Params(mutationId = "mutation", payloadHash = "hash", cardId = "card", expectedCardRevision = 1)
        val display = MutateWorkoutCardDisplayV1Params(mutationId = "mutation", payloadHash = "hash", cardId = "card", expectedCardRevision = 1, displayMode = WorkoutCardDisplayMode.COMPACT)
        assertEquals(1, Json.encodeToJsonElement(MarkWorkoutCardSplitV1Params.serializer(), split).jsonObject.getValue("p_schema_version").jsonPrimitive.int)
        assertEquals(1, Json.encodeToJsonElement(MutateWorkoutCardDisplayV1Params.serializer(), display).jsonObject.getValue("p_schema_version").jsonPrimitive.int)
    }

    @Test fun warmupsNeverDriveOrReceiveWorkingSetSuggestions() {
        val warmup = set("2026-08-01", true).copy(id = 1, completed = false)
        val working = warmup.copy(id = 2, setNumber = 2, isWarmup = false)
        assertTrue(buildSmartAdjustmentsFromSource(emptyMap(), listOf(warmup, working), warmup.copy(weightLbs = 2000), warmup, SmartSetPropagationPolicy.SUGGEST_LATER_SETS).isEmpty())
        val laterWarmup = warmup.copy(setNumber = 3)
        assertTrue(buildSmartAdjustmentsFromSource(emptyMap(), listOf(working, laterWarmup), working.copy(weightLbs = 2000), working, SmartSetPropagationPolicy.SUGGEST_LATER_SETS).isEmpty())
    }
}
