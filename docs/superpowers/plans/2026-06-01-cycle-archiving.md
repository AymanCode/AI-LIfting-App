# Cycle Archiving & Progress Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users archive a self-defined training cycle as an immutable JSON snapshot (per-split exercises, date range, usage count, and date-bounded per-exercise progress) and browse past cycles from a Current|Archive toggle on the Split tab.

**Architecture:** A pure `CycleSnapshotBuilder` (no Android/Room deps) is the single place that applies the window + completed-set filters and the tenths→true-pounds conversion, producing a `@Serializable CycleSnapshot`. The repository serializes that into a new `archived_cycle` Room table (migration 13→14) inside one transaction, alongside denormalized headline columns for the list. The agent write-path is fixed so AI-logged sets count as completed. UI mirrors the Progress tab's segmented control.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room 2.7.1 (KSP, `exportSchema = true`), kotlinx.serialization, JUnit4 + mockito-kotlin + kotlinx-coroutines-test (JVM `src/test`), androidx.room:room-testing + AndroidJUnit4 (`src/androidTest`).

---

## ⚠️ Before you start: working-tree hygiene

The working tree currently contains **unrelated uncommitted edits made by Codex** (calendar `workedDays` flow, scroll-chrome polish, release signing in `build.gradle.kts`) plus **uncommitted spec edits** that this plan is built on. Several files this plan modifies (`ui/viewmodel/SplitViewModel.kt`, `ui/navigation/SplitScreen.kt`, `ui/navigation/AppNavigation.kt`) already carry those edits.

**Rules for every commit in this plan:**
- Stage **only the exact files named in the task** (`git add <path> <path>`). Never `git add -A` / `git add .`.
- Before the first task, the operator should decide how to handle the pre-existing uncommitted changes (commit them as a separate "unrelated polish" commit, or stash them). Do not silently fold them into archiving commits.
- The spec at `docs/superpowers/specs/2026-06-01-cycle-archiving-design.md` is the source of truth; commit the confirmed spec edits before/with Task 1 if not already committed.

---

## File structure

**New files**

| File | Responsibility |
|---|---|
| `src/main/java/com/ayman/ecolift/data/CycleSnapshot.kt` | `@Serializable` frozen snapshot models + `ExerciseMeta` + `SplitBucketKind`. No logic. |
| `src/main/java/com/ayman/ecolift/data/CycleSnapshotBuilder.kt` | Pure builder: filters, bucketing, tenths→pounds conversion, totals, `splitCount`. |
| `src/main/java/com/ayman/ecolift/data/ArchivedCycle.kt` | `@Entity @Serializable` row for `archived_cycle`. |
| `src/main/java/com/ayman/ecolift/data/ArchivedCycleDao.kt` | DAO: insert/insertAll/observeAll/getAll/getById/delete/count/countOverlapping. |
| `src/main/java/com/ayman/ecolift/ui/navigation/ProgressUiComponents.kt` | Extracted shared `internal fun MiniSparkline(...)`. |
| `src/main/java/com/ayman/ecolift/ui/navigation/CycleArchiveDetailScreen.kt` | Detail screen + its own `ExerciseSnapshot` row + highlights. |
| `src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveViewModel.kt` | Observe list, default dates, overlap check, archive action, load-by-id, delete. |
| `src/test/java/com/ayman/ecolift/data/CycleSnapshotBuilderTest.kt` | JVM unit tests for the builder. |
| `src/test/java/com/ayman/ecolift/data/CycleSnapshotSerializationTest.kt` | JVM round-trip test for snapshot models. |
| `src/test/java/com/ayman/ecolift/data/WorkoutRepositoryTest.kt` | JVM mockito test for `saveCycle` field-preservation. |
| `src/test/java/com/ayman/ecolift/agent/InverseComputerTest.kt` | JVM mockito test for `completed` round-trip in DeleteSet inverse. |

**Modified files**

| File | Change |
|---|---|
| `data/Cycle.kt` | `+ startDate: String? = null`, `+ name: String? = null`. |
| `data/Migrations.kt` | `MIGRATION_13_14` + add to `ALL_MIGRATIONS`. |
| `data/AppDatabase.kt` | `version = 14`, register `ArchivedCycle` + `archivedCycleDao()`. |
| `data/WorkoutSetDao.kt` | `+ getSetsInRange(...)`, `+ getEarliestWorkoutDate()`. |
| `data/WorkoutRepository.kt` | Fix `saveCycle` to `copy(...)`; add archive/observe/get/delete + active-start/latest helpers. |
| `data/DataBackupManager.kt` | `+ archivedCycles` in payload/build/restore/clear/toResult; `APP_DB_VERSION = 14`. |
| `agent/model/DbPatch.kt` | `+ completed: Boolean = true` on `LogSet`. |
| `agent/patches/PatchService.kt` | Pass `completed = patch.completed` in `LogSet` insert. |
| `agent/patches/InverseComputer.kt` | DeleteSet→LogSet inverse: `completed = current.completed`. |
| `src/test/java/com/ayman/ecolift/agent/PatchServiceTest.kt` | `LogSet.completed` default + explicit-false tests. |
| `src/androidTest/.../data/DatabaseHardeningInstrumentedTest.kt` | Migration 13→14, backup round-trip extension, overlap query. |
| `ui/navigation/SplitScreen.kt` | Extract `MiniSparkline` + update 3 callers; wire Current/Archive + nav. |
| `ui/navigation/CycleSplitScreenV2.kt` | `SplitTabMode` toggle, archive list content, header cleanup. |
| `ui/navigation/AppNavigation.kt` | `cycleArchive/{archiveId}` route + `buildCycleArchiveRoute`. |
| `src/test/java/com/ayman/ecolift/ui/navigation/AppNavigationRoutesTest.kt` | Route-builder test. |

**Build/run commands** (macOS, from repo root `AI-LIfting-App/`):
- JVM unit tests: `./gradlew testDebugUnitTest`
- A single JVM test class: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.data.CycleSnapshotBuilderTest"`
- Instrumentation tests (needs a connected device/emulator): `./gradlew connectedDebugAndroidTest`
- Compile only (generates Room schema JSON): `./gradlew :assembleDebug` or `./gradlew compileDebugKotlin`

---

## Phase A — Snapshot models + pure builder (JVM, TDD)

### Task 1: Frozen snapshot models

**Files:**
- Create: `src/main/java/com/ayman/ecolift/data/CycleSnapshot.kt`
- Test: `src/test/java/com/ayman/ecolift/data/CycleSnapshotSerializationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ayman.ecolift.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleSnapshotSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `snapshot round-trips through json preserving all fields`() {
        val snapshot = CycleSnapshot(
            schemaVersion = 1,
            startDate = "2026-01-01",
            endDate = "2026-01-31",
            totals = CycleTotals(sessions = 3, totalVolumeLbs = 925L, totalSets = 5, spanDays = 31),
            splits = listOf(
                SplitSnapshot(
                    slotId = 10L,
                    bucketKind = SplitBucketKind.Real,
                    name = "Push",
                    orderIndex = 0,
                    firstUsedDate = "2026-01-02",
                    lastUsedDate = "2026-01-20",
                    usageCount = 2,
                    exercises = listOf(
                        ExerciseSnapshot(
                            exerciseId = 1L,
                            name = "Bench Press",
                            isBodyweight = false,
                            sessions = listOf(
                                SessionPoint("2026-01-02", 185.0f, 215.8f, 925L, 5, 1),
                            ),
                            startE1rm = 215.8f, endE1rm = 240.0f,
                            startTopWeight = 185.0f, endTopWeight = 205.0f,
                            startVolumeLbs = 925L, endVolumeLbs = 1230L,
                        ),
                    ),
                ),
            ),
        )

        val decoded = json.decodeFromString<CycleSnapshot>(json.encodeToString(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `older json without a newer field still decodes via defaults`() {
        // Simulates a v1 payload missing a hypothetical future field; bucketKind defaults to Real.
        val v1 = """
            {"schemaVersion":1,"startDate":"2026-01-01","endDate":"2026-01-02",
             "totals":{"sessions":0,"totalVolumeLbs":0,"totalSets":0,"spanDays":2},
             "splits":[{"slotId":-1,"name":"Unassigned","orderIndex":0,
                        "firstUsedDate":null,"lastUsedDate":null,"usageCount":0,"exercises":[]}]}
        """.trimIndent()

        val decoded = json.decodeFromString<CycleSnapshot>(v1)

        assertEquals(SplitBucketKind.Unassigned, /* slotId -1 still defaults bucketKind */ SplitBucketKind.valueOf(decoded.splits[0].bucketKind.name))
        assertEquals(SplitBucketKind.Real, decoded.splits[0].bucketKind) // defaulted because absent in json
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.data.CycleSnapshotSerializationTest"`
Expected: FAIL — compile error, `CycleSnapshot` (and friends) unresolved.

- [ ] **Step 3: Create the models**

```kotlin
package com.ayman.ecolift.data

import kotlinx.serialization.Serializable

/** Current snapshot payload schema version. Bump when adding fields (see design §8). */
const val CYCLE_SNAPSHOT_SCHEMA_VERSION = 1

@Serializable
data class CycleSnapshot(
    val schemaVersion: Int = CYCLE_SNAPSHOT_SCHEMA_VERSION,
    val startDate: String,
    val endDate: String,
    val totals: CycleTotals,
    val splits: List<SplitSnapshot>,
)

@Serializable
data class CycleTotals(
    val sessions: Int,            // distinct in-window days with >=1 completed set
    val totalVolumeLbs: Long,     // true pounds, completed sets only
    val totalSets: Int,           // completed sets only
    val spanDays: Int,            // endDate - startDate + 1 (inclusive)
)

@Serializable
data class SplitSnapshot(
    val slotId: Long,             // original CycleSlot id; -1 == Unassigned
    val bucketKind: SplitBucketKind = SplitBucketKind.Real,
    val name: String,             // frozen split name (or "Unassigned" / "Deleted split")
    val orderIndex: Int,          // real splits first, then Deleted bucket(s), then Unassigned last
    val firstUsedDate: String?,
    val lastUsedDate: String?,
    val usageCount: Int,          // distinct in-window days in this bucket with >=1 completed set
    val exercises: List<ExerciseSnapshot>,
)

@Serializable
enum class SplitBucketKind { Real, Deleted, Unassigned }

@Serializable
data class ExerciseSnapshot(
    val exerciseId: Long,
    val name: String,             // frozen exercise name
    val isBodyweight: Boolean,
    val sessions: List<SessionPoint>,   // per-day aggregates within window, date-ascending
    // Precomputed headline endpoints. All weights are TRUE POUNDS (see design §4.2 "Units").
    val startE1rm: Float?, val endE1rm: Float?,
    val startTopWeight: Float?, val endTopWeight: Float?,
    val startVolumeLbs: Long?, val endVolumeLbs: Long?,
)

@Serializable
data class SessionPoint(
    val date: String,
    val topWeight: Float?,        // max true-pound weight that day (completed weighted sets)
    val bestE1rm: Float?,         // best Epley (true pounds) across completed weighted sets
    val volumeLbs: Long,          // round(Σ toLbs(weightLbs)×reps) that day, true pounds
    val totalReps: Int,
    val setCount: Int,            // completed sets that day (includes bodyweight sets)
)

/** Builder input holder: frozen exercise metadata keyed by exerciseId. */
data class ExerciseMeta(
    val name: String,
    val isBodyweight: Boolean,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.data.CycleSnapshotSerializationTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayman/ecolift/data/CycleSnapshot.kt \
        src/test/java/com/ayman/ecolift/data/CycleSnapshotSerializationTest.kt
git commit -m "feat(archive): add frozen cycle snapshot models"
```

### Task 2: Pure `CycleSnapshotBuilder`

This is the heart of the feature: window + completed filters, tenths→true-pounds conversion (single boundary), bucketing (Real / legacy-type / Deleted / Unassigned), per-day/per-exercise endpoints, totals, and `splitCount`. Pure Kotlin — no Android/Room.

**Files:**
- Create: `src/main/java/com/ayman/ecolift/data/CycleSnapshotBuilder.kt`
- Test: `src/test/java/com/ayman/ecolift/data/CycleSnapshotBuilderTest.kt`

- [ ] **Step 1: Write the failing test suite**

```kotlin
package com.ayman.ecolift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleSnapshotBuilderTest {

    // ── helpers ──────────────────────────────────────────────────────────
    private fun set(
        exerciseId: Long, date: String, setNumber: Int,
        weightLbs: Int?, reps: Int?, completed: Boolean = true, isBodyweight: Boolean = false,
    ) = WorkoutSet(
        id = 0, exerciseId = exerciseId, date = date, setNumber = setNumber,
        weightLbs = weightLbs, reps = reps, isBodyweight = isBodyweight, completed = completed,
    )

    private fun meta(vararg pairs: Pair<Long, String>): Map<Long, ExerciseMeta> =
        pairs.associate { (id, name) -> id to ExerciseMeta(name, isBodyweight = false) }

    private fun day(date: String, slotId: Long? = null, slotType: Int? = null) =
        WorkoutDay(date = date, cycleSlotType = slotType, cycleSlotId = slotId)

    private val push = CycleSlot(id = 10L, name = "Push", orderIndex = 0)
    private val pull = CycleSlot(id = 20L, name = "Pull", orderIndex = 1)

    // ── windowing ────────────────────────────────────────────────────────
    @Test
    fun `set after endDate is excluded from totals and endpoints`() {
        val sets = listOf(
            set(1L, "2026-01-10", 1, 1850, 5),   // in window
            set(1L, "2026-02-01", 1, 3000, 5),   // 1 day after end -> excluded
        )
        val s = CycleSnapshotBuilder.build(
            startDate = "2026-01-01", endDate = "2026-01-31",
            slots = listOf(push), splitExercises = emptyList(),
            workoutDays = listOf(day("2026-01-10", slotId = 10L)),
            sets = sets, exerciseNames = meta(1L to "Bench"),
        )
        assertEquals(1, s.totals.sessions)
        assertEquals(925L, s.totals.totalVolumeLbs)               // only the in-window set
        val ex = s.splits.first { it.slotId == 10L }.exercises.single()
        assertEquals(185.0f, ex.endTopWeight)                      // not 300
    }

    // ── completed filter ─────────────────────────────────────────────────
    @Test
    fun `incomplete sets are excluded from volume sessions and exercises`() {
        val sets = listOf(
            set(1L, "2026-01-10", 1, 1850, 5, completed = true),
            set(1L, "2026-01-10", 2, 1850, 5, completed = false),  // planned -> ignored
            set(2L, "2026-01-12", 1, 2000, 5, completed = false),  // whole exercise planned
        )
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push), emptyList(),
            listOf(day("2026-01-10", slotId = 10L), day("2026-01-12", slotId = 10L)),
            sets, meta(1L to "Bench", 2L to "Row"),
        )
        assertEquals(1, s.totals.totalSets)                        // only the completed one
        assertEquals(925L, s.totals.totalVolumeLbs)
        val pushSplit = s.splits.first { it.slotId == 10L }
        assertEquals(1, pushSplit.exercises.size)                  // Row never happened
        assertEquals("Bench", pushSplit.exercises.single().name)
    }

    // ── units (tenths -> true pounds) ────────────────────────────────────
    @Test
    fun `weight stored in tenths converts to true pounds once`() {
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push), emptyList(),
            listOf(day("2026-01-10", slotId = 10L)),
            listOf(set(1L, "2026-01-10", 1, 1850, 5)),  // 185.0 lb x5
            meta(1L to "Bench"),
        )
        val sp = s.splits.first { it.slotId == 10L }.exercises.single().sessions.single()
        assertEquals(925L, sp.volumeLbs)                  // 185*5, NOT 9250
        assertEquals(185.0f, sp.topWeight)
        assertEquals(185.0f * (1f + 5f / 30f), sp.bestE1rm!!, 0.01f)
    }

    @Test
    fun `half pound weight keeps the point five`() {
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push), emptyList(),
            listOf(day("2026-01-10", slotId = 10L)),
            listOf(set(1L, "2026-01-10", 1, 1875, 3)),  // 187.5 lb
            meta(1L to "Bench"),
        )
        val sp = s.splits.first { it.slotId == 10L }.exercises.single().sessions.single()
        assertEquals(187.5f, sp.topWeight)
    }

    // ── e1rm progression ─────────────────────────────────────────────────
    @Test
    fun `e1rm endpoints reflect first vs last session`() {
        val sets = listOf(
            set(1L, "2026-01-05", 1, 1850, 5),   // start
            set(1L, "2026-01-25", 1, 1850, 8),   // end (more reps -> higher e1rm)
        )
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push), emptyList(),
            listOf(day("2026-01-05", slotId = 10L), day("2026-01-25", slotId = 10L)),
            sets, meta(1L to "Bench"),
        )
        val ex = s.splits.first { it.slotId == 10L }.exercises.single()
        assertTrue(ex.endE1rm!! > ex.startE1rm!!)
        assertEquals(ex.startTopWeight, ex.endTopWeight)   // top weight unchanged
    }

    // ── usage counts + dates ─────────────────────────────────────────────
    @Test
    fun `usageCount and first last dates count only days with completed sets`() {
        val sets = listOf(
            set(1L, "2026-01-05", 1, 1850, 5, completed = true),
            set(1L, "2026-01-12", 1, 1850, 5, completed = true),
            set(1L, "2026-01-20", 1, 1850, 5, completed = false),  // planned -> not counted
        )
        val days = listOf(
            day("2026-01-05", slotId = 10L),
            day("2026-01-12", slotId = 10L),
            day("2026-01-20", slotId = 10L),
        )
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31", listOf(push), emptyList(), days, sets, meta(1L to "Bench"),
        )
        val pushSplit = s.splits.first { it.slotId == 10L }
        assertEquals(2, pushSplit.usageCount)
        assertEquals("2026-01-05", pushSplit.firstUsedDate)
        assertEquals("2026-01-12", pushSplit.lastUsedDate)
    }

    // ── Unassigned: missing day row AND null-metadata day ────────────────
    @Test
    fun `sets with no day row or null slot land in Unassigned and reconcile`() {
        val sets = listOf(
            set(1L, "2026-01-05", 1, 1850, 5),                 // (a) no WorkoutDay row at all
            set(1L, "2026-01-06", 1, 1850, 5),                 // (b) day row with null slot
        )
        val days = listOf(day("2026-01-06", slotId = null, slotType = null))
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31", listOf(push), emptyList(), days, sets, meta(1L to "Bench"),
        )
        val unassigned = s.splits.single { it.bucketKind == SplitBucketKind.Unassigned }
        assertEquals(-1L, unassigned.slotId)
        assertEquals(2, unassigned.usageCount)
        // reconciliation: buckets partition all counted work
        assertEquals(s.totals.sessions, s.splits.sumOf { it.usageCount })
        assertEquals(s.totals.totalVolumeLbs, s.splits.sumOf { sp -> sp.exercises.sumOf { e -> e.sessions.sumOf { it.volumeLbs } } })
    }

    // ── legacy cycleSlotType bucketing ───────────────────────────────────
    @Test
    fun `legacy day with only cycleSlotType maps by positional index`() {
        // slotType = 1 -> slots[1] == pull (id 20), NOT Unassigned
        val days = listOf(day("2026-01-10", slotId = null, slotType = 1))
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push, pull), emptyList(), days,
            listOf(set(1L, "2026-01-10", 1, 1850, 5)), meta(1L to "Bench"),
        )
        assertEquals(1, s.splits.first { it.slotId == 20L }.usageCount)
        assertTrue(s.splits.none { it.bucketKind == SplitBucketKind.Unassigned })
    }

    // ── orphaned slot -> Deleted split (one bucket per dangling id) ───────
    @Test
    fun `orphaned slot ids each get their own Deleted bucket distinct from Unassigned`() {
        val days = listOf(
            day("2026-01-05", slotId = 99L),    // deleted split A
            day("2026-01-06", slotId = 98L),    // deleted split B
            day("2026-01-07", slotId = null),   // never structured -> Unassigned
        )
        val sets = listOf(
            set(1L, "2026-01-05", 1, 1850, 5),
            set(1L, "2026-01-06", 1, 1850, 5),
            set(1L, "2026-01-07", 1, 1850, 5),
        )
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31", listOf(push), emptyList(), days, sets, meta(1L to "Bench"),
        )
        val deleted = s.splits.filter { it.bucketKind == SplitBucketKind.Deleted }
        assertEquals(2, deleted.size)                                  // not merged
        assertEquals(listOf(98L, 99L), deleted.map { it.slotId })      // sorted by dangling id
        assertEquals(1, s.splits.count { it.bucketKind == SplitBucketKind.Unassigned })
    }

    // ── splitCount definition ────────────────────────────────────────────
    @Test
    fun `splitCount counts only trained real splits excluding synthetic and zero-usage`() {
        val legs = CycleSlot(id = 30L, name = "Legs", orderIndex = 2)   // never trained
        val days = listOf(
            day("2026-01-05", slotId = 10L),   // Push trained
            day("2026-01-06", slotId = 20L),   // Pull trained
            day("2026-01-07", slotId = 77L),   // Deleted split
            day("2026-01-08", slotId = null),  // Unassigned
        )
        val sets = (5..8).map { d -> set(1L, "2026-01-0$d", 1, 1850, 5) }
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push, pull, legs), emptyList(), days, sets, meta(1L to "Bench"),
        )
        assertEquals(2, CycleSnapshotBuilder.splitCount(s))   // Push+Pull; not Legs/Deleted/Unassigned
    }

    @Test
    fun `pure free logger has splitCount zero but non-zero totals`() {
        val sets = listOf(set(1L, "2026-01-05", 1, 1850, 5), set(1L, "2026-01-06", 1, 1850, 5))
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31", emptyList(), emptyList(), emptyList(), sets, meta(1L to "Bench"),
        )
        assertEquals(0, CycleSnapshotBuilder.splitCount(s))
        assertEquals(2, s.totals.sessions)
        assertTrue(s.totals.totalVolumeLbs > 0)
    }

    // ── single session ───────────────────────────────────────────────────
    @Test
    fun `single session exercise has equal start and end endpoints`() {
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push), emptyList(), listOf(day("2026-01-10", slotId = 10L)),
            listOf(set(1L, "2026-01-10", 1, 1850, 5)), meta(1L to "Bench"),
        )
        val ex = s.splits.first { it.slotId == 10L }.exercises.single()
        assertEquals(ex.startE1rm, ex.endE1rm)
        assertEquals(ex.startVolumeLbs, ex.endVolumeLbs)
        assertEquals(1, ex.sessions.size)
    }

    // ── bodyweight ───────────────────────────────────────────────────────
    @Test
    fun `bodyweight exercise is flagged and contributes zero weight`() {
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push), emptyList(), listOf(day("2026-01-10", slotId = 10L)),
            listOf(set(2L, "2026-01-10", 1, weightLbs = null, reps = 12, isBodyweight = true)),
            mapOf(2L to ExerciseMeta("Pull-up", isBodyweight = true)),
        )
        val ex = s.splits.first { it.slotId == 10L }.exercises.single()
        assertTrue(ex.isBodyweight)
        assertNull(ex.endTopWeight)
        assertEquals(0L, ex.endVolumeLbs)
        assertEquals(12, ex.sessions.single().totalReps)
    }

    // ── empty cycle ──────────────────────────────────────────────────────
    @Test
    fun `empty cycle yields zeroed totals and zero-usage real splits without crashing`() {
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push, pull), emptyList(), emptyList(), emptyList(), emptyMap(),
        )
        assertEquals(0, s.totals.sessions)
        assertEquals(0L, s.totals.totalVolumeLbs)
        assertEquals(31, s.totals.spanDays)
        assertEquals(2, s.splits.size)                       // both real splits still emitted
        assertTrue(s.splits.all { it.usageCount == 0 && it.exercises.isEmpty() })
    }

    // ── exercise ordering by saved orderIndex ────────────────────────────
    @Test
    fun `exercises follow saved split orderIndex when present`() {
        // Saved template says Row (id 2) before Bench (id 1); sets logged Bench first.
        val splitEx = listOf(
            SplitExercise(id = 1, splitId = 10L, exerciseId = 2L, orderIndex = 0),
            SplitExercise(id = 2, splitId = 10L, exerciseId = 1L, orderIndex = 1),
        )
        val sets = listOf(
            set(1L, "2026-01-10", 1, 1850, 5),
            set(2L, "2026-01-10", 2, 2000, 5),
        )
        val s = CycleSnapshotBuilder.build(
            "2026-01-01", "2026-01-31",
            listOf(push), splitEx, listOf(day("2026-01-10", slotId = 10L)), sets,
            meta(1L to "Bench", 2L to "Row"),
        )
        assertEquals(listOf("Row", "Bench"), s.splits.first { it.slotId == 10L }.exercises.map { it.name })
    }
}
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.data.CycleSnapshotBuilderTest"`
Expected: FAIL — `CycleSnapshotBuilder` unresolved.

- [ ] **Step 3: Implement the builder**

```kotlin
package com.ayman.ecolift.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * Pure builder for an immutable [CycleSnapshot]. No Android/Room dependencies.
 *
 * Applies BOTH gating rules itself so they stay unit-testable:
 *   1. window: startDate <= date <= endDate (ISO string compare == chronological)
 *   2. completed == true (planned/template sets never count)
 *
 * It is the SINGLE place that converts WorkoutSet.weightLbs (stored in tenths) to true
 * pounds via [WeightLbs.toLbs]; nothing downstream divides again (design §4.2 "Units").
 */
object CycleSnapshotBuilder {

    const val UNASSIGNED_SLOT_ID = -1L

    data class BucketKey(val slotId: Long, val kind: SplitBucketKind)

    fun build(
        startDate: String,
        endDate: String,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,
        workoutDays: List<WorkoutDay>,
        sets: List<WorkoutSet>,
        exerciseNames: Map<Long, ExerciseMeta>,
    ): CycleSnapshot {
        val inWindow = sets.filter { it.completed && it.date in startDate..endDate }

        val daysByDate = workoutDays.associateBy { it.date }
        val slotIds = slots.map { it.id }.toSet()

        fun bucketFor(s: WorkoutSet): BucketKey {
            val day = daysByDate[s.date]
            val resolved: Long? = day?.cycleSlotId
                ?: day?.cycleSlotType?.let { slots.getOrNull(it)?.id }   // legacy rows: type is an index
            return when {
                resolved == null -> BucketKey(UNASSIGNED_SLOT_ID, SplitBucketKind.Unassigned)
                resolved in slotIds -> BucketKey(resolved, SplitBucketKind.Real)
                else -> BucketKey(resolved, SplitBucketKind.Deleted)
            }
        }

        val setsByBucket: Map<BucketKey, List<WorkoutSet>> = inWindow.groupBy(::bucketFor)

        // splitId -> (exerciseId -> saved orderIndex)
        val savedOrder: Map<Long, Map<Long, Int>> = splitExercises
            .groupBy { it.splitId }
            .mapValues { (_, rows) -> rows.associate { it.exerciseId to it.orderIndex } }

        val splits = mutableListOf<SplitSnapshot>()

        // Real splits: emit ALL (even zero-usage), in slot order.
        val orderedSlots = slots.sortedBy { it.orderIndex }
        orderedSlots.forEachIndexed { index, slot ->
            splits += buildSplit(
                slotId = slot.id, kind = SplitBucketKind.Real, name = slot.name, orderIndex = index,
                bucketSets = setsByBucket[BucketKey(slot.id, SplitBucketKind.Real)].orEmpty(),
                savedOrder = savedOrder[slot.id].orEmpty(), exerciseNames = exerciseNames,
            )
        }

        // Deleted-split buckets: one per dangling id, sorted, only when non-empty.
        val deletedKeys = setsByBucket.keys
            .filter { it.kind == SplitBucketKind.Deleted }
            .sortedBy { it.slotId }
        deletedKeys.forEachIndexed { i, key ->
            splits += buildSplit(
                slotId = key.slotId, kind = SplitBucketKind.Deleted, name = "Deleted split",
                orderIndex = orderedSlots.size + i,
                bucketSets = setsByBucket.getValue(key),
                savedOrder = emptyMap(), exerciseNames = exerciseNames,
            )
        }

        // Unassigned bucket: last, only when non-empty.
        val unassigned = setsByBucket[BucketKey(UNASSIGNED_SLOT_ID, SplitBucketKind.Unassigned)].orEmpty()
        if (unassigned.isNotEmpty()) {
            splits += buildSplit(
                slotId = UNASSIGNED_SLOT_ID, kind = SplitBucketKind.Unassigned, name = "Unassigned",
                orderIndex = orderedSlots.size + deletedKeys.size,
                bucketSets = unassigned, savedOrder = emptyMap(), exerciseNames = exerciseNames,
            )
        }

        // Totals derived from the SAME per-day rounded values so buckets reconcile exactly.
        val totalVolume = splits.sumOf { sp -> sp.exercises.sumOf { e -> e.sessions.sumOf { it.volumeLbs } } }
        val totals = CycleTotals(
            sessions = inWindow.map { it.date }.distinct().size,
            totalVolumeLbs = totalVolume,
            totalSets = inWindow.size,
            spanDays = spanDays(startDate, endDate),
        )

        return CycleSnapshot(
            schemaVersion = CYCLE_SNAPSHOT_SCHEMA_VERSION,
            startDate = startDate, endDate = endDate, totals = totals, splits = splits,
        )
    }

    /** Trained user-defined splits: Real buckets with at least one completed-set day. */
    fun splitCount(snapshot: CycleSnapshot): Int =
        snapshot.splits.count { it.bucketKind == SplitBucketKind.Real && it.usageCount > 0 }

    private fun buildSplit(
        slotId: Long, kind: SplitBucketKind, name: String, orderIndex: Int,
        bucketSets: List<WorkoutSet>, savedOrder: Map<Long, Int>, exerciseNames: Map<Long, ExerciseMeta>,
    ): SplitSnapshot {
        val days = bucketSets.map { it.date }.distinct().sorted()
        val byExercise = bucketSets.groupBy { it.exerciseId }
        val firstSeen = bucketSets.map { it.exerciseId }.distinct()
        val orderedIds = firstSeen.sortedWith(
            compareBy({ savedOrder[it] ?: Int.MAX_VALUE }, { firstSeen.indexOf(it) }),
        )
        return SplitSnapshot(
            slotId = slotId, bucketKind = kind, name = name, orderIndex = orderIndex,
            firstUsedDate = days.firstOrNull(), lastUsedDate = days.lastOrNull(), usageCount = days.size,
            exercises = orderedIds.map { id ->
                buildExercise(id, byExercise.getValue(id), exerciseNames[id])
            },
        )
    }

    private fun buildExercise(exerciseId: Long, sets: List<WorkoutSet>, meta: ExerciseMeta?): ExerciseSnapshot {
        val sessions = sets.groupBy { it.date }.toSortedMap().map { (date, daySets) -> sessionPoint(date, daySets) }
        val start = sessions.firstOrNull()
        val end = sessions.lastOrNull()
        return ExerciseSnapshot(
            exerciseId = exerciseId,
            name = meta?.name ?: "Exercise #$exerciseId",
            isBodyweight = meta?.isBodyweight ?: false,
            sessions = sessions,
            startE1rm = start?.bestE1rm, endE1rm = end?.bestE1rm,
            startTopWeight = start?.topWeight, endTopWeight = end?.topWeight,
            startVolumeLbs = start?.volumeLbs, endVolumeLbs = end?.volumeLbs,
        )
    }

    private fun sessionPoint(date: String, daySets: List<WorkoutSet>): SessionPoint {
        var topWeight: Float? = null
        var bestE1rm: Float? = null
        var volume = 0.0
        var totalReps = 0
        for (s in daySets) {
            val reps = s.reps ?: 0
            totalReps += reps
            val weighted = !s.isBodyweight && s.weightLbs != null
            if (weighted) {
                val w = WeightLbs.toLbs(s.weightLbs)            // true pounds, single conversion
                val wf = w.toFloat()
                topWeight = maxOf(topWeight ?: wf, wf)
                val e1 = (w * (1.0 + reps / 30.0)).toFloat()    // Epley
                bestE1rm = maxOf(bestE1rm ?: e1, e1)
                volume += w * reps
            }
        }
        return SessionPoint(
            date = date, topWeight = topWeight, bestE1rm = bestE1rm,
            volumeLbs = volume.roundToLong(), totalReps = totalReps, setCount = daySets.size,
        )
    }

    private fun spanDays(startDate: String, endDate: String): Int = try {
        (ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1)
            .toInt().coerceAtLeast(0)
    } catch (e: Exception) {
        0
    }
}
```

- [ ] **Step 4: Run the suite to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.data.CycleSnapshotBuilderTest"`
Expected: PASS (all tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayman/ecolift/data/CycleSnapshotBuilder.kt \
        src/test/java/com/ayman/ecolift/data/CycleSnapshotBuilderTest.kt
git commit -m "feat(archive): add pure CycleSnapshotBuilder with window+completed+units rules"
```

---

## Phase B — Persistence layer (entity, DAO, DB version, migration, schema)

Phase A produced a pure JSON-emitting builder. Phase B gives it a home in Room: a new `archived_cycle` table that stores the serialized snapshot blob plus a handful of *denormalized* columns (so the archive list can render without parsing JSON, and so the overlap-warning query can run in SQL). Then we bump the DB to v14, write the migration, generate the schema, and copy the exact `CREATE TABLE` text back into the migration for byte-exact Room validation.

### Task 3: `ArchivedCycle` entity + `ArchivedCycleDao`

**Files:**
- Create: `src/main/java/com/ayman/ecolift/data/ArchivedCycle.kt`
- Create: `src/main/java/com/ayman/ecolift/data/ArchivedCycleDao.kt`

There is no unit test for this task — a bare entity + DAO has no logic to assert in a JVM test (Room generates the DAO impl at compile time, and querying it requires an instrumented DB). Its behavior is verified by the Phase F instrumentation tests (migration + backup round-trip + overlap query). The "verify" step here is a Kotlin compile.

- [ ] **Step 1: Create the `ArchivedCycle` entity**

The denormalized columns (`name`, `startDate`, `endDate`, `splitCount`, `totalVolumeLbs`, `totalSessions`, `archivedAt`) let the archive-list row and the overlap query read straight from columns. The full immutable snapshot lives in `snapshotJson`. `schemaVersion` is copied out of the blob so a future reader can branch on it without parsing.

```kotlin
package com.ayman.ecolift.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One archived training cycle. [snapshotJson] is the full immutable [CycleSnapshot]
 * (kotlinx-serialized); the remaining columns are denormalized projections so the
 * archive list and the overlap-warning query never have to parse the blob.
 */
@Entity(tableName = "archived_cycle")
data class ArchivedCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "startDate") val startDate: String, // ISO yyyy-MM-dd, inclusive
    @ColumnInfo(name = "endDate") val endDate: String,     // ISO yyyy-MM-dd, inclusive
    val splitCount: Int,
    val totalVolumeLbs: Long,
    val totalSessions: Int,
    val archivedAt: Long,          // epoch millis the archive was created
    val schemaVersion: Int,        // mirrors CycleSnapshot.schemaVersion in the blob
    val snapshotJson: String,
)
```

- [ ] **Step 2: Create the `ArchivedCycleDao`**

`observeAll` drives the archive list (newest first). `countOverlapping` powers the archive-dialog overlap warning: two date ranges `[aStart,aEnd]` and `[bStart,bEnd]` overlap iff `aStart <= bEnd AND bStart <= aEnd`. Because dates are zero-padded ISO strings, lexicographic `<=` equals chronological `<=`. `getAll`/`insertAll` exist for `DataBackupManager`.

```kotlin
package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchivedCycleDao {

    @Query("SELECT * FROM archived_cycle ORDER BY endDate DESC, id DESC")
    fun observeAll(): Flow<List<ArchivedCycle>>

    @Query("SELECT * FROM archived_cycle ORDER BY endDate DESC, id DESC")
    suspend fun getAll(): List<ArchivedCycle>

    @Query("SELECT * FROM archived_cycle WHERE id = :id")
    suspend fun getById(id: Long): ArchivedCycle?

    /** Count existing archives whose [startDate,endDate] overlaps [:start,:end] (inclusive). */
    @Query(
        "SELECT COUNT(*) FROM archived_cycle " +
            "WHERE startDate <= :end AND :start <= endDate"
    )
    suspend fun countOverlapping(start: String, end: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cycle: ArchivedCycle): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cycles: List<ArchivedCycle>)

    @Query("DELETE FROM archived_cycle WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (The DAO is not yet referenced by `AppDatabase`, so KSP won't generate its impl until Task 4 — this step only checks the source is syntactically valid Kotlin.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ayman/ecolift/data/ArchivedCycle.kt \
        src/main/java/com/ayman/ecolift/data/ArchivedCycleDao.kt
git commit -m "feat(archive): add ArchivedCycle entity and DAO"
```

### Task 4: Bump DB to v14, register the entity/DAO, write migration 13→14, generate & lock the schema

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/data/AppDatabase.kt:12-38` (entities list, `version`, new abstract DAO getter)
- Modify: `src/main/java/com/ayman/ecolift/data/Cycle.kt` (add `startDate`, `name`)
- Modify: `src/main/java/com/ayman/ecolift/data/Migrations.kt:135-154` (add `MIGRATION_13_14`, append to `ALL_MIGRATIONS`)
- Generates: `schemas/com.ayman.ecolift.data.AppDatabase/14.json`

This is the one task whose ordering matters: Room validates the migration's resulting schema against the entity-derived schema at first open. We write the entity changes + a *best-effort* migration, let KSP generate `14.json`, then copy the canonical `CREATE TABLE` text from `14.json` back into the migration so the two match byte-for-byte. The instrumented migration test (Task 9) is the real proof.

- [ ] **Step 1: Add the two nullable columns to `Cycle`**

The active cycle gains a `startDate` (when the current cycle began — set forward after each archive) and an optional `name`. Both nullable so the migration is a pure additive `ADD COLUMN ... DEFAULT NULL` and existing rows are untouched.

Current `Cycle.kt`:
```kotlin
@Entity(tableName = "cycle")
data class Cycle(
    @PrimaryKey val id: Int = 1,
    val numTypes: Int = 3,
    val isActive: Boolean = false,
    val nextSessionType: Int? = null,
)
```

Change to:
```kotlin
@Entity(tableName = "cycle")
data class Cycle(
    @PrimaryKey val id: Int = 1,
    val numTypes: Int = 3,
    val isActive: Boolean = false,
    val nextSessionType: Int? = null,
    /** ISO yyyy-MM-dd the current (active) cycle began; null until first set or first archive. */
    val startDate: String? = null,
    /** Optional user label for the current cycle; cleared when the cycle is archived. */
    val name: String? = null,
)
```

- [ ] **Step 2: Register the entity + DAO and bump the version in `AppDatabase.kt`**

In the `entities = [ ... ]` array (lines 13-24), add `ArchivedCycle::class,` after `AgentTurnLog::class,`. Change `version = 13` to `version = 14`. Add the DAO getter after `agentTurnLogDao()` (line 38):

```kotlin
    abstract fun agentTurnLogDao(): AgentTurnLogDao
    abstract fun archivedCycleDao(): ArchivedCycleDao
```

(`ArchivedCycle` and `ArchivedCycleDao` are in the same `com.ayman.ecolift.data` package as `AppDatabase`, so no import is needed.)

- [ ] **Step 3: Add `MIGRATION_13_14` and append it to `ALL_MIGRATIONS`**

Insert after `MIGRATION_12_13` (line 139). The `archived_cycle` `CREATE TABLE` below is the *expected* Room output for the Task 3 entity — Step 5 replaces it with the exact generated text if it differs.

```kotlin
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "cycle", "startDate", "TEXT DEFAULT NULL")
            addColumnIfMissing(db, "cycle", "name", "TEXT DEFAULT NULL")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `archived_cycle` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `endDate` TEXT NOT NULL,
                    `splitCount` INTEGER NOT NULL,
                    `totalVolumeLbs` INTEGER NOT NULL,
                    `totalSessions` INTEGER NOT NULL,
                    `archivedAt` INTEGER NOT NULL,
                    `schemaVersion` INTEGER NOT NULL,
                    `snapshotJson` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }
```

Then add `MIGRATION_13_14,` as the last element of the `ALL_MIGRATIONS` array (after `MIGRATION_12_13,`).

- [ ] **Step 4: Generate the schema**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL, and a new file `schemas/com.ayman.ecolift.data.AppDatabase/14.json` appears.

- [ ] **Step 5: Lock the migration to the generated schema**

Open `schemas/com.ayman.ecolift.data.AppDatabase/14.json`, find the `archived_cycle` entry, and read its `createSql` field. Replace the `${TABLE_NAME}` placeholder with `` `archived_cycle` `` and compare against the `CREATE TABLE` string in Step 3. If they differ (column order, a `DEFAULT`, a type), replace the migration's string with the canonical text so Room's validator sees an identical hash. Also confirm the `cycle` table entry now lists `startDate` and `name` as nullable `TEXT` columns. Re-run `./gradlew compileDebugKotlin` if you edited the migration.

- [ ] **Step 6: Verify the whole module still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit (include the generated schema)**

```bash
git add src/main/java/com/ayman/ecolift/data/AppDatabase.kt \
        src/main/java/com/ayman/ecolift/data/Cycle.kt \
        src/main/java/com/ayman/ecolift/data/Migrations.kt \
        schemas/com.ayman.ecolift.data.AppDatabase/14.json
git commit -m "feat(archive): bump DB to v14 with archived_cycle table and cycle date columns"
```

### Task 5: Add range + earliest-date queries to `WorkoutSetDao`

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/data/WorkoutSetDao.kt:199` (add two `@Query` methods before the closing brace)

`getSetsInRange` is the *only* set source the archive flow uses — it feeds every set in the cycle window to `CycleSnapshotBuilder.build`. `getEarliestWorkoutDate` backs `getActiveCycleStart()` (Task 6) when the cycle has never had an explicit start. Both are thin queries with no branching logic; like the DAO in Task 3 they're proven by instrumentation (Task 9), so there's no JVM unit test here — only a compile.

- [ ] **Step 1: Add the two queries**

Insert immediately after `getMaxWeightsForExercises(...)` (line 199), before the closing `}` of the interface:

```kotlin
    @Query(
        """
        SELECT * FROM workout_set
        WHERE date >= :start AND date <= :end
        ORDER BY date ASC, exerciseId ASC, setNumber ASC
        """
    )
    suspend fun getSetsInRange(start: String, end: String): List<WorkoutSet>

    @Query("SELECT MIN(date) FROM workout_set")
    suspend fun getEarliestWorkoutDate(): String?
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (KSP regenerates the DAO impl with the two new queries; a malformed SQL string fails here.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ayman/ecolift/data/WorkoutSetDao.kt
git commit -m "feat(archive): add getSetsInRange and getEarliestWorkoutDate queries"
```

---

## Phase C — Repository orchestration

Phase C wires the pure builder (A) to the persistence layer (B) inside `WorkoutRepository`. It does two things: (1) fixes a latent bug where `saveCycle` rebuilds the `Cycle` row from scratch and *drops* `nextSessionType` (and would drop the new `startDate`/`name`), and (2) adds the archive orchestration methods. Only `saveCycle` gets a JVM unit test — `archiveCurrentCycle` runs inside `db.withTransaction { }` (a `RoomDatabase` extension that can't be cleanly stubbed with Mockito), so it's covered by the Phase F instrumentation instead.

### Task 6: Fix `saveCycle`, add archive orchestration methods, regression-test `saveCycle`

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/data/WorkoutRepository.kt:1-4` (imports), `:56-66` (`saveCycle`), end of class (new methods)
- Test: `src/test/java/com/ayman/ecolift/data/WorkoutRepositoryTest.kt` (new)

- [ ] **Step 1: Write the failing regression test for `saveCycle`**

The current `saveCycle` builds `Cycle(id = 1, isActive = ..., numTypes = ..., nextSessionType = current.nextSessionType)` — which silently resets `startDate` and `name` to their `null` defaults. After Phase B those columns carry real data, so this is now a data-loss bug. The test pins the correct behavior: every field except the two being changed is preserved.

Create `src/test/java/com/ayman/ecolift/data/WorkoutRepositoryTest.kt`:

```kotlin
package com.ayman.ecolift.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for WorkoutRepository logic that does NOT use db.withTransaction.
 * archiveCurrentCycle wraps its work in withTransaction (a RoomDatabase extension that
 * can't be stubbed here) and is covered by androidTest instead.
 */
class WorkoutRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var cycleDao: CycleDao
    private lateinit var setDao: WorkoutSetDao
    private lateinit var repo: WorkoutRepository

    @Before
    fun setUp() {
        db = mock()
        cycleDao = mock()
        setDao = mock()
        whenever(db.cycleDao()).thenReturn(cycleDao)
        whenever(db.workoutSetDao()).thenReturn(setDao)
        repo = WorkoutRepository(db)
    }

    @Test
    fun `saveCycle preserves nextSessionType, startDate, and name`() = runTest {
        whenever(cycleDao.getCycle()).thenReturn(
            Cycle(
                id = 1, numTypes = 3, isActive = false, nextSessionType = 2,
                startDate = "2026-01-01", name = "Hypertrophy block",
            )
        )
        val captor = argumentCaptor<Cycle>()

        repo.saveCycle(isActive = true, numTypes = 4)

        verify(cycleDao).upsert(captor.capture())
        val saved = captor.firstValue
        assertEquals(1, saved.id)
        assertEquals(true, saved.isActive)
        assertEquals(4, saved.numTypes)
        assertEquals(2, saved.nextSessionType)
        assertEquals("2026-01-01", saved.startDate)
        assertEquals("Hypertrophy block", saved.name)
    }

    @Test
    fun `saveCycle coerces numTypes to at least 1`() = runTest {
        whenever(cycleDao.getCycle()).thenReturn(Cycle())
        val captor = argumentCaptor<Cycle>()

        repo.saveCycle(isActive = true, numTypes = 0)

        verify(cycleDao).upsert(captor.capture())
        assertEquals(1, captor.firstValue.numTypes)
    }

    @Test
    fun `getActiveCycleStart returns cycle startDate when present`() = runTest {
        whenever(cycleDao.getCycle()).thenReturn(Cycle(startDate = "2026-02-10"))

        assertEquals("2026-02-10", repo.getActiveCycleStart())
    }

    @Test
    fun `getActiveCycleStart falls back to earliest set date`() = runTest {
        whenever(cycleDao.getCycle()).thenReturn(Cycle(startDate = null))
        whenever(setDao.getEarliestWorkoutDate()).thenReturn("2025-12-25")

        assertEquals("2025-12-25", repo.getActiveCycleStart())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.data.WorkoutRepositoryTest"`
Expected: FAIL — `saveCycle preserves...` fails (saved.startDate is null, not "2026-01-01"); the two `getActiveCycleStart` tests fail to compile (method doesn't exist yet).

- [ ] **Step 3: Fix `saveCycle` and add the new methods**

First add imports at the top of `WorkoutRepository.kt` (after line 2):

```kotlin
import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
```

Replace `saveCycle` (lines 56-66) with a `copy`-based version that preserves every other field:

```kotlin
    suspend fun saveCycle(isActive: Boolean, numTypes: Int) {
        val current = getCycle()
        db.cycleDao().upsert(
            current.copy(
                isActive = isActive,
                numTypes = numTypes.coerceAtLeast(1),
            )
        )
    }
```

Add a private `Json` instance as the first member inside the class body (right after the `cycle` flow on line 7). `ignoreUnknownKeys = true` is what makes the stored blob forward-compatible: a snapshot written by a future schema with extra fields still deserializes in an older build.

```kotlin
    private val archiveJson = Json { ignoreUnknownKeys = true }
```

Then add the archive orchestration methods at the end of the class (before the final closing `}` on line 124):

```kotlin
    // ── Cycle archiving ──────────────────────────────────────────────

    fun observeArchivedCycles(): Flow<List<ArchivedCycle>> =
        db.archivedCycleDao().observeAll()

    suspend fun getArchivedCycle(id: Long): ArchivedCycle? =
        db.archivedCycleDao().getById(id)

    suspend fun deleteArchivedCycle(id: Long) =
        db.archivedCycleDao().deleteById(id)

    suspend fun countOverlappingArchives(start: String, end: String): Int =
        db.archivedCycleDao().countOverlapping(start, end)

    suspend fun getLatestWorkoutDate(): String? =
        db.workoutSetDao().getLatestWorkoutDate()

    /**
     * Start date of the active cycle: explicit [Cycle.startDate] if set, else the earliest
     * logged set, else today. Used to pre-fill the archive dialog's start field.
     */
    suspend fun getActiveCycleStart(): String {
        getCycle().startDate?.let { return it }
        db.workoutSetDao().getEarliestWorkoutDate()?.let { return it }
        return LocalDate.now().toString()
    }

    /**
     * Freeze the cycle window [startDate, endDate] (inclusive) into an immutable archive,
     * then advance the active cycle's start to the day after [endDate] and clear its name.
     * Returns the new archive row id.
     */
    suspend fun archiveCurrentCycle(name: String, startDate: String, endDate: String): Long =
        db.withTransaction {
            val slots = db.cycleSlotDao().getAll()
            val splitExercises = db.splitExerciseDao().getAll()
            val days = db.workoutDayDao().getAll()
            val sets = db.workoutSetDao().getSetsInRange(startDate, endDate)
            val ids = sets.map { it.exerciseId }.distinct()
            val exerciseNames = db.exerciseDao().getByIds(ids)
                .associate { it.id to ExerciseMeta(it.name, it.isBodyweight) }

            val snapshot = CycleSnapshotBuilder.build(
                startDate = startDate,
                endDate = endDate,
                slots = slots,
                splitExercises = splitExercises,
                workoutDays = days,
                sets = sets,
                exerciseNames = exerciseNames,
            )

            val newId = db.archivedCycleDao().insert(
                ArchivedCycle(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    splitCount = CycleSnapshotBuilder.splitCount(snapshot),
                    totalVolumeLbs = snapshot.totals.totalVolumeLbs,
                    totalSessions = snapshot.totals.sessions,
                    archivedAt = System.currentTimeMillis(),
                    schemaVersion = snapshot.schemaVersion,
                    snapshotJson = archiveJson.encodeToString(snapshot),
                )
            )

            val nextStart = runCatching {
                LocalDate.parse(endDate).plusDays(1).toString()
            }.getOrDefault(endDate)
            db.cycleDao().upsert(getCycle().copy(startDate = nextStart, name = null))

            newId
        }
```

- [ ] **Step 4: Run the suite to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.data.WorkoutRepositoryTest"`
Expected: PASS (all four tests).

- [ ] **Step 5: Confirm the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`ExerciseMeta`, `CycleSnapshotBuilder`, `ArchivedCycle`, `archivedCycleDao()` all resolve.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ayman/ecolift/data/WorkoutRepository.kt \
        src/test/java/com/ayman/ecolift/data/WorkoutRepositoryTest.kt
git commit -m "feat(archive): fix saveCycle field loss and add archive orchestration to repository"
```

---

## Phase D — Round-trip `completed` through the agent patch layer

The archive counts only `completed == true` sets (it's a record of work *done*, not planned). Two consequences for the agent patch layer:

1. **Agent-logged sets should count.** When the agent logs a set from a user report ("I benched 225×5"), that's work the user actually did — it should be `completed = true`. Today `PatchService` omits `completed`, so the inserted `WorkoutSet` falls back to its entity default of `false` and silently never counts. We add `completed` to `DbPatch.LogSet` defaulting to `true`.
2. **Undo of a delete must restore the original flag.** `InverseComputer` turns a `DeleteSet` into a `LogSet` that re-creates the row. If it doesn't carry the original `completed`, undoing the deletion of a *completed* set brings it back *uncompleted*. We pass `completed = current.completed` through the inverse.

**Serialization note:** the patch layer uses `Json { prettyPrint = false }`, i.e. `encodeDefaults = false`. So `completed = true` (the default) is *omitted* from the serialized JSON, while `completed = false` *is* written. Existing `DbPatchTest` round-trips therefore stay green (a LogSet built without `completed` encodes identically and decodes back to `true`). It also means the `completed`-preservation behavior must be asserted on the returned object, not on a serialized string — hence a dedicated `InverseComputerTest`.

### Task 7: Add `completed` to `LogSet`, wire it through `PatchService` + `InverseComputer`

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/agent/model/DbPatch.kt:14-22` (add field)
- Modify: `src/main/java/com/ayman/ecolift/agent/patches/PatchService.kt:117-127` (pass to insert)
- Modify: `src/main/java/com/ayman/ecolift/agent/patches/InverseComputer.kt:47-55` (carry through inverse)
- Modify: `src/test/java/com/ayman/ecolift/agent/PatchServiceTest.kt` (2 new tests)
- Test: `src/test/java/com/ayman/ecolift/agent/patches/InverseComputerTest.kt` (new)

- [ ] **Step 1: Write the failing tests**

Add these two tests to `PatchServiceTest.kt`, right after the `LogSet inverse stored as DeleteSet with inserted ID` test (line 140). They use `argumentCaptor<WorkoutSet>()` to inspect the row handed to `setDao.insert`:

```kotlin
    @Test
    fun `LogSet inserts completed=true by default`() = runTest {
        whenever(setDao.insert(any())).thenReturn(7L)
        whenever(auditDao.insert(any())).thenReturn(1L)
        val captor = argumentCaptor<WorkoutSet>()

        service.applyPatches(
            "req-completed-default",
            listOf(DbPatch.LogSet(exerciseId = 10L, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8)),
            false
        )

        verify(setDao).insert(captor.capture())
        assertTrue("Agent-logged set should default to completed=true", captor.firstValue.completed)
    }

    @Test
    fun `LogSet honors explicit completed=false`() = runTest {
        whenever(setDao.insert(any())).thenReturn(8L)
        whenever(auditDao.insert(any())).thenReturn(1L)
        val captor = argumentCaptor<WorkoutSet>()

        service.applyPatches(
            "req-completed-false",
            listOf(
                DbPatch.LogSet(
                    exerciseId = 10L, date = "2026-04-16", setNumber = 1,
                    weightLbs = 135, reps = 8, completed = false
                )
            ),
            false
        )

        verify(setDao).insert(captor.capture())
        assertFalse(captor.firstValue.completed)
    }
```

Create `src/test/java/com/ayman/ecolift/agent/patches/InverseComputerTest.kt`:

```kotlin
package com.ayman.ecolift.agent.patches

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.data.ExerciseDao
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.WorkoutSetDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Directly verifies inverse computation. The DeleteSet -> LogSet inverse must carry the
 * original `completed` flag so undoing a delete restores the set's completion state.
 * Asserted on the returned object (not serialized JSON) because kotlinx omits completed=true
 * (the default) under encodeDefaults=false.
 */
class InverseComputerTest {

    private lateinit var setDao: WorkoutSetDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var computer: InverseComputer

    @Before
    fun setUp() {
        setDao = mock()
        exerciseDao = mock()
        computer = InverseComputer(setDao, exerciseDao)
    }

    @Test
    fun `DeleteSet inverse preserves completed=true`() = runTest {
        whenever(setDao.getById(5L)).thenReturn(
            WorkoutSet(
                id = 5L, exerciseId = 10L, date = "2026-04-16", setNumber = 1,
                weightLbs = 1350, reps = 8, completed = true
            )
        )
        val inverse = computer.computeInverse(DbPatch.DeleteSet(setId = 5L))
        assertTrue(inverse is DbPatch.LogSet)
        assertTrue((inverse as DbPatch.LogSet).completed)
    }

    @Test
    fun `DeleteSet inverse preserves completed=false`() = runTest {
        whenever(setDao.getById(6L)).thenReturn(
            WorkoutSet(
                id = 6L, exerciseId = 10L, date = "2026-04-16", setNumber = 1,
                weightLbs = 1350, reps = 8, completed = false
            )
        )
        val inverse = computer.computeInverse(DbPatch.DeleteSet(setId = 6L))
        assertTrue(inverse is DbPatch.LogSet)
        assertFalse((inverse as DbPatch.LogSet).completed)
    }

    @Test
    fun `LogSet inverse is DeleteSet with inserted id`() = runTest {
        val inverse = computer.computeInverse(
            DbPatch.LogSet(exerciseId = 10L, date = "2026-04-16", setNumber = 1, weightLbs = 1350, reps = 8),
            insertedId = 77L
        )
        assertTrue(inverse is DbPatch.DeleteSet)
        assertEquals(77L, (inverse as DbPatch.DeleteSet).setId)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.agent.PatchServiceTest" --tests "com.ayman.ecolift.agent.patches.InverseComputerTest"`
Expected: FAIL — won't compile, because `DbPatch.LogSet` has no `completed` parameter and `LogSet.completed` property yet.

- [ ] **Step 3: Add the field and wire it through**

In `DbPatch.kt`, add `completed` to `LogSet` (after `isBodyweight`, line 20). All existing call sites use named arguments, so a defaulted field added here is safe:

```kotlin
    @Serializable
    data class LogSet(
        val exerciseId: Long,
        val date: String,           // "YYYY-MM-DD"
        val setNumber: Int,
        val weightLbs: Int?,        // null for bodyweight
        val reps: Int,
        val isBodyweight: Boolean = false,
        val completed: Boolean = true,   // agent-logged sets count as done unless told otherwise
        val restTimeSeconds: Int? = null
    ) : DbPatch
```

In `PatchService.kt`, the `LogSet` branch (lines 117-127), add `completed = patch.completed` to the `WorkoutSet(...)`:

```kotlin
            is DbPatch.LogSet -> {
                setDao.insert(
                    WorkoutSet(
                        exerciseId = patch.exerciseId,
                        date = patch.date,
                        setNumber = patch.setNumber,
                        weightLbs = patch.weightLbs,
                        reps = patch.reps,
                        isBodyweight = patch.isBodyweight,
                        completed = patch.completed,
                        restTimeSeconds = patch.restTimeSeconds
                    )
                )
            }
```

In `InverseComputer.kt`, the `DeleteSet` branch (lines 47-55), add `completed = current.completed`:

```kotlin
            DbPatch.LogSet(
                exerciseId = current.exerciseId,
                date = current.date,
                setNumber = current.setNumber,
                weightLbs = current.weightLbs,
                reps = current.reps ?: 0,
                isBodyweight = current.isBodyweight,
                completed = current.completed,
                restTimeSeconds = current.restTimeSeconds
            )
```

- [ ] **Step 4: Run the new tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.agent.PatchServiceTest" --tests "com.ayman.ecolift.agent.patches.InverseComputerTest"`
Expected: PASS.

- [ ] **Step 5: Run the rest of the agent suite to confirm no regression**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.agent.*"`
Expected: PASS. In particular `DbPatchTest`'s serialization round-trips stay green: a `LogSet` built without `completed` encodes the same (the `true` default is omitted) and decodes back to `true`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ayman/ecolift/agent/model/DbPatch.kt \
        src/main/java/com/ayman/ecolift/agent/patches/PatchService.kt \
        src/main/java/com/ayman/ecolift/agent/patches/InverseComputer.kt \
        src/test/java/com/ayman/ecolift/agent/PatchServiceTest.kt \
        src/test/java/com/ayman/ecolift/agent/patches/InverseComputerTest.kt
git commit -m "feat(archive): round-trip completed flag through agent patch layer"
```

---

## Phase E — Backup/restore wiring

`DataBackupManager.clearUserTables` wipes every user table *before* a restore. Any table not wired into the export → import → clear trio silently vanishes whenever the user restores a backup. The `archived_cycle` table must join the trio so archives survive a restore. There's no clean JVM unit test for this (it's all DAO + file I/O); correctness is proven by the Phase F instrumentation round-trip (Task 9). This task is pure wiring, verified by a compile.

### Task 8: Include `archived_cycle` in backup export, import, and clear

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/data/DataBackupManager.kt:42-44` (`UserDataBackup` field), `:59` (`APP_DB_VERSION`), `:177-205` (`buildSnapshot`), `:207-222` (`restoreSnapshot`), `:224-237` (`clearUserTables`), `:268-283` (`toResult`)

- [ ] **Step 1: Bump the backup format's DB version**

Change line 59 from `private const val APP_DB_VERSION = 13` to:

```kotlin
    private const val APP_DB_VERSION = 14
```

- [ ] **Step 2: Add the `archivedCycles` field to `UserDataBackup`**

Append to the `UserDataBackup` data class (after `agentTurns`, line 43). The `= emptyList()` default is essential — it lets a *pre-archive* backup file (which has no `archivedCycles` key) still deserialize:

```kotlin
    val auditEntries: List<AuditEntity> = emptyList(),
    val agentTurns: List<AgentTurnLog> = emptyList(),
    val archivedCycles: List<ArchivedCycle> = emptyList(),
)
```

- [ ] **Step 3: Export archives in `buildSnapshot`**

In `buildSnapshot` (line 177), read the archives after `agentTurns` (line 186) and pass them into the `UserDataBackup(...)` constructor (after `agentTurns = agentTurns,`, line 203):

```kotlin
        val agentTurns = db.agentTurnLogDao().getAll()
        val archivedCycles = db.archivedCycleDao().getAll()
        val cycle = db.cycleDao().getCycle()
```

```kotlin
            auditEntries = auditEntries,
            agentTurns = agentTurns,
            archivedCycles = archivedCycles,
        )
```

- [ ] **Step 4: Import archives in `restoreSnapshot`**

In `restoreSnapshot` (line 207), add after the `agentTurns` insert (line 220):

```kotlin
            if (snapshot.agentTurns.isNotEmpty()) db.agentTurnLogDao().insertAll(snapshot.agentTurns)
            if (snapshot.archivedCycles.isNotEmpty()) db.archivedCycleDao().insertAll(snapshot.archivedCycles)
```

- [ ] **Step 5: Clear the table in `clearUserTables`**

In `clearUserTables` (line 224), add the `DELETE` after `agent_turn_log` (line 235), before the `sqlite_sequence` reset. `archived_cycle` has no foreign keys, so ordering is irrelevant for constraints:

```kotlin
        writableDb.execSQL("DELETE FROM `agent_turn_log`")
        writableDb.execSQL("DELETE FROM `archived_cycle`")
        writableDb.execSQL("DELETE FROM sqlite_sequence")
```

- [ ] **Step 6: Count archives in `toResult`**

In `toResult` (line 268), add `archivedCycles.size` to the `entryCount` sum (after `agentTurns.size`, line 277):

```kotlin
            agentTurns.size +
            archivedCycles.size +
            if (cycle != null) 1 else 0
```

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/ayman/ecolift/data/DataBackupManager.kt
git commit -m "feat(archive): include archived_cycle in backup export/import/clear"
```

---

## Phase F — Instrumentation (the real proof for B/C/E)

The pure builder is unit-tested (Phase A). But the migration, the DAO queries, `archiveCurrentCycle`'s transaction, the overlap query, and the backup round-trip all need a real SQLite engine. We extend the existing `DatabaseHardeningInstrumentedTest` rather than create a new file — it already owns the `MigrationTestHelper`, the `createDb`/`seedBackupSource` helpers, and teardown.

`runMigrationsAndValidate(name, 14, true, …)` is itself the byte-exact schema check: if Task 4's migration `CREATE TABLE archived_cycle` (or the `cycle` `ALTER`s) don't match the entity-derived `14.json`, it throws. So the migration test below is both a data-preservation check and the validation that Task 4 Step 5 was done right.

These run on a device/emulator: `./gradlew connectedDebugAndroidTest`.

### Task 9: Migration 13→14, archive round-trip, and overlap-query instrumentation

**Files:**
- Modify: `src/androidTest/java/com/ayman/ecolift/data/DatabaseHardeningInstrumentedTest.kt` (new test methods + extend `seedBackupSource` and the round-trip assertions)

`ArchivedCycle` and `CYCLE_SNAPSHOT_SCHEMA_VERSION` are in package `com.ayman.ecolift.data`, the same package as this test — no new imports needed.

- [ ] **Step 1: Add the migration 13→14 test**

Add this method after `migration12To13PreservesSplitAssignmentsAndScalesWeights` (line 92). It seeds a v13 `cycle` row, migrates to 14 (which validates the schema), and asserts the old data survived and the new surface exists:

```kotlin
    @Test
    fun migration13To14AddsCycleDateColumnsAndArchivedCycleTable() {
        val dbName = "migration-13.db"
        migrationHelper.createDatabase(dbName, 13).apply {
            execSQL("INSERT INTO cycle (id, numTypes, isActive, nextSessionType) VALUES (1, 3, 1, 2)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            dbName,
            14,
            true,
            *Migrations.ALL_MIGRATIONS,
        )

        // Existing cycle data survives the additive migration.
        assertEquals(2, migrated.longFor("SELECT nextSessionType FROM cycle WHERE id = 1"))
        // New nullable columns exist (selecting them would throw if absent) and default to NULL.
        assertEquals(null, migrated.stringFor("SELECT startDate FROM cycle WHERE id = 1"))
        assertEquals(null, migrated.stringFor("SELECT name FROM cycle WHERE id = 1"))
        // New table exists.
        assertNotNull(
            migrated.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'archived_cycle'")
        )
        migrated.close()
    }
```

- [ ] **Step 2: Run it to verify it passes**

Run: `./gradlew connectedDebugAndroidTest --tests "com.ayman.ecolift.data.DatabaseHardeningInstrumentedTest.migration13To14AddsCycleDateColumnsAndArchivedCycleTable"`
Expected: PASS. A failure here with a schema-mismatch error means Task 4 Step 5 (locking the migration to the generated `14.json`) was skipped or wrong — fix the migration string, not the test.

- [ ] **Step 3: Extend `seedBackupSource` to insert an archive**

Add to the end of `seedBackupSource` (after the `agentTurnLogDao().insert(...)` block, line 218). The `snapshotJson` is opaque to the backup path (stored/retrieved verbatim, never parsed), so a placeholder is fine:

```kotlin
        db.archivedCycleDao().insertAll(
            listOf(
                ArchivedCycle(
                    id = 700L,
                    name = "Spring Block",
                    startDate = "2026-04-01",
                    endDate = "2026-04-30",
                    splitCount = 2,
                    totalVolumeLbs = 12_345L,
                    totalSessions = 8,
                    archivedAt = 3L,
                    schemaVersion = CYCLE_SNAPSHOT_SCHEMA_VERSION,
                    snapshotJson = "{}",
                )
            )
        )
```

- [ ] **Step 4: Extend the backup round-trip assertions**

In `backupRoundTripPreservesWorkoutAndAgentAuditData` (line 148), bump the entry-count floor and add archive assertions. Change line 160 from `assertTrue(exportResult.entryCount >= 9)` to:

```kotlin
        assertTrue(exportResult.entryCount >= 10)
```

And add after the `agentTurnLogDao` assertion (line 166):

```kotlin
        assertEquals(source.archivedCycleDao().getAll().size, target.archivedCycleDao().getAll().size)
        assertEquals("Spring Block", target.archivedCycleDao().getById(700L)?.name)
```

- [ ] **Step 5: Add the overlap-query test**

Add this method after the round-trip test (line 169). It pins the inclusive-overlap semantics the archive dialog's warning depends on:

```kotlin
    @Test
    fun countOverlappingDetectsOverlapInclusiveAndIgnoresDisjoint() = runTest {
        val db = createDb(BACKUP_TARGET_DB)
        db.archivedCycleDao().insert(
            ArchivedCycle(
                id = 0L, name = "April", startDate = "2026-04-01", endDate = "2026-04-30",
                splitCount = 1, totalVolumeLbs = 1L, totalSessions = 1,
                archivedAt = 1L, schemaVersion = CYCLE_SNAPSHOT_SCHEMA_VERSION, snapshotJson = "{}",
            )
        )
        val dao = db.archivedCycleDao()

        // Straddles the existing range.
        assertEquals(1, dao.countOverlapping("2026-04-15", "2026-05-15"))
        // Shares only the end boundary -> inclusive overlap.
        assertEquals(1, dao.countOverlapping("2026-04-30", "2026-05-10"))
        // Shares only the start boundary -> inclusive overlap.
        assertEquals(1, dao.countOverlapping("2026-03-01", "2026-04-01"))
        // Fully before, disjoint.
        assertEquals(0, dao.countOverlapping("2026-02-01", "2026-03-31"))
        // Fully after, disjoint.
        assertEquals(0, dao.countOverlapping("2026-05-01", "2026-05-31"))
    }
```

- [ ] **Step 6: Run the whole instrumented class**

Run: `./gradlew connectedDebugAndroidTest --tests "com.ayman.ecolift.data.DatabaseHardeningInstrumentedTest"`
Expected: PASS (all tests, including the pre-existing ones).

- [ ] **Step 7: Commit**

```bash
git add src/androidTest/java/com/ayman/ecolift/data/DatabaseHardeningInstrumentedTest.kt
git commit -m "test(archive): instrument migration 13to14, archive backup round-trip, overlap query"
```

---

## Phase G — UI: Current/Archive toggle, archive list, archive dialog, detail screen, title cleanup

Everything below the data layer. The Split tab gains a `Current | Archive` segmented toggle at the top (mirroring the Progress tab's `Progress | Splits` control). **Current** shows today's split content (unchanged). **Archive** lists past cycles and offers an "Archive current cycle" action that opens a dialog with editable date range + name + an overlap warning. Tapping an archive opens a read-only detail screen with the per-cycle progress snapshot.

**TDD boundary:** Compose UI and `AndroidViewModel` aren't cleanly JVM-unit-testable. We extract the genuinely testable logic into pure functions — date-range formatting, the `ArchivedCycle → ArchiveCardUi` mapping, and the nav-route builder — and cover those with JVM tests. The Compose wiring (toggle, dialog, detail layout, header removal) is verified manually via the Phase H checklist.

`ArchiveCardUi`, `formatArchiveDateRange`, and `SplitTabMode` are new types referenced across Tasks 11–15; they're defined once in Task 11 and reused verbatim.

### Task 10: Extract `MiniSparkline` into a shared component file

The archive detail screen (Task 14) reuses the volume sparkline. `MiniSparkline` is currently `private` in `SplitScreen.kt` with three callers. Extract it to a new shared file as `internal`, delete the private original, and leave the three call sites unchanged (same name, same signature, same package).

**Files:**
- Create: `src/main/java/com/ayman/ecolift/ui/navigation/ProgressUiComponents.kt`
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/SplitScreen.kt:1546-1571` (delete the private fn)

This is a pure refactor — no behavior change, so no new test. The three existing call sites (`SplitScreen.kt:676`, `:1271`, `:1329`) compile against the new `internal` function because it lives in the same package.

- [ ] **Step 1: Create the shared component file**

```kotlin
package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Minimal line sparkline shared by the Split tab and the cycle-archive detail screen.
 * Renders nothing (an empty Box) when there are fewer than two points.
 */
@Composable
internal fun MiniSparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val maxV = values.max()
        val minV = values.min()
        val range = (maxV - minV).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        val padY = 2f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val yNorm = (v - minV) / range
            val y = size.height - padY - yNorm * (size.height - padY * 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
```

- [ ] **Step 2: Delete the private original from `SplitScreen.kt`**

Remove the entire `private fun MiniSparkline(...)` block (lines 1546-1571, the `@Composable` annotation through its closing brace). Leave the three call sites untouched — they now resolve to the `internal` function in `ProgressUiComponents.kt`. Do NOT touch the dead `SplitScreenContent`/`Header` code elsewhere in the file.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If you see "unresolved reference: MiniSparkline", a call site is in a different package — it shouldn't be; all three are in `SplitScreen.kt` (same package).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ayman/ecolift/ui/navigation/ProgressUiComponents.kt \
        src/main/java/com/ayman/ecolift/ui/navigation/SplitScreen.kt
git commit -m "refactor(split): extract MiniSparkline to shared component for reuse"
```

### Task 11: `CycleArchiveViewModel` + pure UI models (`SplitTabMode`, `ArchiveCardUi`, `formatArchiveDateRange`)

Split the archive UI state into two files: a pure-JVM models file (testable) and the `AndroidViewModel` (wiring, manual-verified). The pure file holds the toggle enum, the list-card UI model, and the date-range formatter. The VM owns the reactive archive list, detail load (parses `snapshotJson`), delete, default-window computation, overlap check, and the archive action.

**Files:**
- Create: `src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModels.kt`
- Create: `src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveViewModel.kt`
- Test: `src/test/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModelsTest.kt`

- [ ] **Step 1: Write the failing test for the pure models**

`formatArchiveDateRange` uses `Locale.US` formatters so output is deterministic across devices. Create `src/test/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModelsTest.kt`:

```kotlin
package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.ArchivedCycle
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleArchiveModelsTest {

    @Test
    fun `formatArchiveDateRange same year omits year on start`() {
        assertEquals("Apr 1 – Apr 30, 2026", formatArchiveDateRange("2026-04-01", "2026-04-30"))
    }

    @Test
    fun `formatArchiveDateRange cross year shows both years`() {
        assertEquals("Dec 28, 2025 – Jan 5, 2026", formatArchiveDateRange("2025-12-28", "2026-01-05"))
    }

    @Test
    fun `formatArchiveDateRange falls back to raw on bad input`() {
        assertEquals("not-a-date – 2026-04-30", formatArchiveDateRange("not-a-date", "2026-04-30"))
    }

    @Test
    fun `toCardUi maps denormalized columns and formats the range`() {
        val card = ArchivedCycle(
            id = 7L, name = "Spring Block", startDate = "2026-04-01", endDate = "2026-04-30",
            splitCount = 3, totalVolumeLbs = 98_765L, totalSessions = 12,
            archivedAt = 0L, schemaVersion = 1, snapshotJson = "{}",
        ).toCardUi()

        assertEquals(7L, card.id)
        assertEquals("Spring Block", card.name)
        assertEquals("Apr 1 – Apr 30, 2026", card.dateRangeLabel)
        assertEquals(3, card.splitCount)
        assertEquals(12, card.sessionCount)
        assertEquals(98_765L, card.totalVolumeLbs)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.CycleArchiveModelsTest"`
Expected: FAIL — won't compile (`formatArchiveDateRange`, `toCardUi`, `ArchiveCardUi` don't exist yet).

- [ ] **Step 3: Create the pure models file**

```kotlin
package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.ArchivedCycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Top toggle on the Split tab: live cycle vs. archived cycles. */
enum class SplitTabMode { CURRENT, ARCHIVE }

/** Lightweight row model for the archive list — read straight from denormalized columns. */
data class ArchiveCardUi(
    val id: Long,
    val name: String,
    val dateRangeLabel: String,
    val splitCount: Int,
    val sessionCount: Int,
    val totalVolumeLbs: Long,
)

private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MONTH_DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * "Apr 1 – Apr 30, 2026" when both ends share a year, "Dec 28, 2025 – Jan 5, 2026" otherwise.
 * Falls back to "start – end" if either string isn't an ISO date.
 */
fun formatArchiveDateRange(startIso: String, endIso: String): String {
    val start = runCatching { LocalDate.parse(startIso) }.getOrNull()
    val end = runCatching { LocalDate.parse(endIso) }.getOrNull()
    if (start == null || end == null) return "$startIso – $endIso"
    val startLabel = if (start.year == end.year) start.format(MONTH_DAY) else start.format(MONTH_DAY_YEAR)
    return "$startLabel – ${end.format(MONTH_DAY_YEAR)}"
}

fun ArchivedCycle.toCardUi(): ArchiveCardUi = ArchiveCardUi(
    id = id,
    name = name,
    dateRangeLabel = formatArchiveDateRange(startDate, endDate),
    splitCount = splitCount,
    sessionCount = totalSessions,
    totalVolumeLbs = totalVolumeLbs,
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.CycleArchiveModelsTest"`
Expected: PASS (all four).

- [ ] **Step 5: Create the `CycleArchiveViewModel`**

Mirrors `SplitViewModel`'s construction (`AppDatabase.getInstance` + `WorkoutRepository`). `archives` is the only reactive stream; everything else is on-demand. The suspend `defaultArchiveWindow`/`overlapCount` are called from the dialog via `rememberCoroutineScope` (Task 13).

```kotlin
package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

class CycleArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = WorkoutRepository(db)
    private val archiveJson = Json { ignoreUnknownKeys = true }

    /** Archive list, newest first, mapped to lightweight card models. */
    val archives: StateFlow<List<ArchiveCardUi>> =
        repo.observeArchivedCycles()
            .map { rows -> rows.map { it.toCardUi() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow<CycleSnapshot?>(null)
    /** Parsed snapshot for the currently open detail screen; null while loading or on parse failure. */
    val detail: StateFlow<CycleSnapshot?> = _detail.asStateFlow()

    private val _detailName = MutableStateFlow("")
    val detailName: StateFlow<String> = _detailName.asStateFlow()

    fun loadArchive(id: Long) {
        viewModelScope.launch {
            val row = repo.getArchivedCycle(id)
            _detail.value = row?.let {
                runCatching { archiveJson.decodeFromString<CycleSnapshot>(it.snapshotJson) }.getOrNull()
            }
            _detailName.value = row?.name ?: ""
        }
    }

    fun clearDetail() {
        _detail.value = null
        _detailName.value = ""
    }

    fun deleteArchive(id: Long) {
        viewModelScope.launch { repo.deleteArchivedCycle(id) }
    }

    /** Default archive window: active-cycle start → latest logged workout (or today). */
    suspend fun defaultArchiveWindow(): Pair<String, String> {
        val start = repo.getActiveCycleStart()
        val end = repo.getLatestWorkoutDate() ?: LocalDate.now().toString()
        return start to end
    }

    /** Count of existing archives whose date range overlaps [start, end] (inclusive). */
    suspend fun overlapCount(start: String, end: String): Int =
        repo.countOverlappingArchives(start, end)

    fun archiveCurrentCycle(name: String, start: String, end: String, onArchived: () -> Unit = {}) {
        viewModelScope.launch {
            repo.archiveCurrentCycle(name, start, end)
            onArchived()
        }
    }
}
```

- [ ] **Step 6: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModels.kt \
        src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveViewModel.kt \
        src/test/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModelsTest.kt
git commit -m "feat(archive): add CycleArchiveViewModel and pure archive UI models"
```

### Task 12: Current/Archive toggle + archive list in `CycleSplitScreen`

Add the segmented toggle (pinned above the content, mirroring the Progress tab) and an Archive branch that lists past cycles with an "Archive current cycle" CTA. The "Split" title bar stays for now — it's removed last (Task 15). New `CycleSplitScreen` params get defaults so the `@Preview` and any other caller still compile. The archive-dialog (`onArchiveCurrentCycle`) and detail navigation (`onOpenArchive`) are wired as pass-throughs here; the dialog itself arrives in Task 13 and the detail screen in Task 14.

This is Compose wiring — verified by compile here and by the Phase H manual checklist. No new unit test.

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/CycleSplitScreenV2.kt` (imports, `CycleSplitScreen` signature + body, two new private composables)
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/SplitScreen.kt:103-148` (add archive VM + toggle state, pass new params)

- [ ] **Step 1: Add imports to `CycleSplitScreenV2.kt`**

After `import androidx.compose.material3.Scaffold` (line 44) add:

```kotlin
import androidx.compose.material3.Surface
```

After `import com.ayman.ecolift.ui.theme.bounceClick` (line 68) add:

```kotlin
import com.ayman.ecolift.ui.viewmodel.ArchiveCardUi
import com.ayman.ecolift.ui.viewmodel.SplitTabMode
```

- [ ] **Step 2: Extend the `CycleSplitScreen` signature**

Replace the parameter list (lines 656-665) with the version below — five new params, all defaulted:

```kotlin
fun CycleSplitScreen(
    splits: List<SplitType>,
    gymDaysThisMonth: Map<YearMonth, Set<Int>>,
    splitCycleEnabled: Boolean,
    currentSplitIndex: Int,
    onToggleSplitCycle: (Boolean) -> Unit,
    onLoadWorkout: () -> Unit,
    onEditSplit: (SplitType) -> Unit,
    onAddSplit: () -> Unit,
    onSplitOptions: (SplitType) -> Unit,
    tabMode: SplitTabMode = SplitTabMode.CURRENT,
    onTabModeChange: (SplitTabMode) -> Unit = {},
    archives: List<ArchiveCardUi> = emptyList(),
    onOpenArchive: (Long) -> Unit = {},
    onArchiveCurrentCycle: () -> Unit = {},
) {
```

- [ ] **Step 3: Restructure the Scaffold body to pin the toggle and branch on `tabMode`**

Replace the entire `LazyColumn(...) { ... }` block (lines 699-750) with a `Column` that pins the toggle above a `LazyColumn`, and switch content on `tabMode`:

```kotlin
        Column(modifier = Modifier.padding(innerPadding)) {
            SplitTabToggle(
                selected = tabMode,
                onSelect = onTabModeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (tabMode) {
                    SplitTabMode.CURRENT -> {
                        item {
                            GymCalendarCard(
                                gymDays = currentGymDays,
                                displayedMonth = displayedMonth,
                                onPreviousMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                                onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) }
                            )
                        }
                        item {
                            SplitCycleToggleCard(
                                enabled = splitCycleEnabled,
                                onToggle = onToggleSplitCycle
                            )
                        }
                        if (splits.isNotEmpty() && splitCycleEnabled) {
                            item {
                                val currentSplit = splits.getOrNull(currentSplitIndex)
                                if (currentSplit != null) {
                                    TodaySplitHeroCard(
                                        splitName = currentSplit.name,
                                        dayLabel = "Day ${currentSplitIndex + 1} of ${splits.size}",
                                        exerciseCount = currentSplit.exerciseCount,
                                        lastRunLabel = currentSplit.lastRunLabel,
                                        onLoadWorkout = onLoadWorkout,
                                        onEditSplit = { onEditSplit(currentSplit) }
                                    )
                                }
                            }
                            item {
                                RotationCycleRow(
                                    splits = splits.map { it.name },
                                    currentIndex = currentSplitIndex
                                )
                            }
                        }
                        item {
                            MySplitsSection(
                                splits = splits,
                                currentSplitIndex = currentSplitIndex,
                                splitCycleEnabled = splitCycleEnabled,
                                onAddSplit = onAddSplit,
                                onSplitOptions = onSplitOptions
                            )
                        }
                    }
                    SplitTabMode.ARCHIVE -> {
                        item {
                            Button(
                                onClick = onArchiveCurrentCycle,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4DB6AC))
                            ) {
                                Text("Archive current cycle", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (archives.isEmpty()) {
                            item {
                                Text(
                                    text = "No archived cycles yet. Archive your current cycle to snapshot its progress.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                                )
                            }
                        } else {
                            itemsIndexed(archives) { _, card ->
                                ArchiveListCard(card = card, onClick = { onOpenArchive(card.id) })
                            }
                        }
                    }
                }
            }
        }
```

- [ ] **Step 4: Add the two private composables**

Add these right after `CycleSplitScreen`'s closing brace (after line 752), before `@Preview`. `SplitTabToggle` is a direct mirror of `ProgressOrganizationControl`; `ArchiveListCard` is annotated `@OptIn` because the clickable `Card(onClick=)` overload is experimental:

```kotlin
@Composable
private fun SplitTabToggle(
    selected: SplitTabMode,
    onSelect: (SplitTabMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SplitTabMode.values().forEach { mode ->
                val isSelected = mode == selected
                TextButton(
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) Color(0xFF4DB6AC).copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (isSelected) Color(0xFF1C1C1E) else Color(0xFF8E8E93)
                    )
                ) {
                    Text(
                        text = when (mode) {
                            SplitTabMode.CURRENT -> "Current"
                            SplitTabMode.ARCHIVE -> "Archive"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveListCard(card: ArchiveCardUi, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = card.name.ifBlank { "Untitled cycle" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1C1E)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = card.dateRangeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8E8E93)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${card.splitCount} splits · ${card.sessionCount} sessions · ${"%,d".format(card.totalVolumeLbs)} lb",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1C1C1E)
            )
        }
    }
}
```

- [ ] **Step 5: Wire the toggle + archive list in the `SplitScreen` state wrapper**

In `SplitScreen.kt`, add imports (near the other `com.ayman.ecolift.ui.viewmodel` import for `SplitViewModel`):

```kotlin
import com.ayman.ecolift.ui.viewmodel.CycleArchiveViewModel
import com.ayman.ecolift.ui.viewmodel.SplitTabMode
```

Extend the `SplitScreen` signature (lines 103-107) to add the archive VM and a detail-navigation callback:

```kotlin
fun SplitScreen(
    viewModel: SplitViewModel = viewModel(),
    archiveViewModel: CycleArchiveViewModel = viewModel(),
    onNavigateToLog: (splitId: Long) -> Unit = {},
    onNavigateToExerciseProgress: (exerciseId: Long) -> Unit = {},
    onNavigateToArchiveDetail: (archiveId: Long) -> Unit = {},
) {
```

After `val workedDays by viewModel.workedDays.collectAsStateWithLifecycle()` (line 110) add:

```kotlin
    val archives by archiveViewModel.archives.collectAsStateWithLifecycle()
    var tabMode by remember { mutableStateOf(SplitTabMode.CURRENT) }
```

Add the five new params to the `CycleSplitScreen(...)` call (after `onSplitOptions = { detailSplitId = it.id }`, line 147):

```kotlin
        onSplitOptions = { detailSplitId = it.id },
        tabMode = tabMode,
        onTabModeChange = { tabMode = it },
        archives = archives,
        onOpenArchive = onNavigateToArchiveDetail,
        onArchiveCurrentCycle = { /* opens dialog — wired in Task 13 */ },
    )
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/ayman/ecolift/ui/navigation/CycleSplitScreenV2.kt \
        src/main/java/com/ayman/ecolift/ui/navigation/SplitScreen.kt
git commit -m "feat(archive): add Current/Archive toggle and archive list to Split tab"
```

### Task 13: Archive dialog with editable date range, name, and overlap warning

A `Dialog` (mirroring `AddSplitDialog`'s `Dialog`+`Card` style and theme colors) that pre-fills the date range from `defaultArchiveWindow()`, lets the user edit the name and both dates, live-checks overlap against existing archives, and disables confirm on an invalid range. On confirm it calls `CycleArchiveViewModel.archiveCurrentCycle`; the reactive `archives` flow then refreshes the list.

All needed symbols (`Dialog`, `LaunchedEffect`, `OutlinedTextField`, and theme colors via the wildcard `com.ayman.ecolift.ui.theme.*` import) are already imported in `SplitScreen.kt` — no new imports. Compose wiring, manual-verified (Phase H). No new unit test (date validity is a one-line `LocalDate.parse` guard; overlap semantics are already covered by the Task 9 instrumentation).

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/SplitScreen.kt` (dialog state + render in `SplitScreen`, new `ArchiveCycleDialog` composable)

- [ ] **Step 1: Add dialog state and wire the CTA**

In `SplitScreen`, add the dialog flag next to the other `remember` state (after the `tabMode` line added in Task 12):

```kotlin
    var showArchiveDialog by remember { mutableStateOf(false) }
```

Replace the Task 12 stub `onArchiveCurrentCycle = { /* opens dialog — wired in Task 13 */ },` in the `CycleSplitScreen(...)` call with:

```kotlin
        onArchiveCurrentCycle = { showArchiveDialog = true },
```

- [ ] **Step 2: Render the dialog**

Add after the `AddSplitDialog` block (after line 158, the `if (showAddDialog) { ... }` close):

```kotlin
    if (showArchiveDialog) {
        ArchiveCycleDialog(
            loadDefaults = { archiveViewModel.defaultArchiveWindow() },
            checkOverlap = { s, e -> archiveViewModel.overlapCount(s, e) },
            onConfirm = { name, start, end ->
                archiveViewModel.archiveCurrentCycle(name, start, end)
                showArchiveDialog = false
            },
            onDismiss = { showArchiveDialog = false },
        )
    }
```

- [ ] **Step 3: Add the `ArchiveCycleDialog` composable**

Add right after `AddSplitDialog` (after line 1401). The two `LaunchedEffect`s load defaults once and recompute overlap whenever a date changes:

```kotlin
@Composable
private fun ArchiveCycleDialog(
    loadDefaults: suspend () -> Pair<String, String>,
    checkOverlap: suspend (String, String) -> Int,
    onConfirm: (name: String, start: String, end: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var overlap by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val (s, e) = loadDefaults()
        start = s
        end = e
        loaded = true
    }
    LaunchedEffect(start, end, loaded) {
        overlap = if (loaded) checkOverlap(start, end) else 0
    }

    val validRange = remember(start, end) {
        runCatching { !LocalDate.parse(start).isAfter(LocalDate.parse(end)) }.getOrDefault(false)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
            border = BorderStroke(1.dp, BorderDefault),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Archive cycle", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Freeze this cycle's progress between two dates. Sets logged outside the range aren't counted.",
                    color = TextInactive,
                    fontSize = 11.sp,
                )
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BackgroundElevated,
                    unfocusedContainerColor = BackgroundElevated,
                    cursorColor = AccentTeal,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Cycle name (optional)", color = TextInactive) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Start (YYYY-MM-DD)", color = TextInactive) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("End (YYYY-MM-DD)", color = TextInactive) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                if (loaded && !validRange) {
                    Text(
                        "Enter valid dates with start on or before end.",
                        color = ErrorRed,
                        fontSize = 11.sp,
                    )
                }
                if (overlap > 0) {
                    Text(
                        "Heads up: this range overlaps $overlap existing ${if (overlap == 1) "archive" else "archives"}.",
                        color = ErrorRed,
                        fontSize = 11.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextInactive) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name.trim(), start.trim(), end.trim()) },
                        enabled = validRange,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentTeal,
                            contentColor = BackgroundPrimary,
                        ),
                    ) { Text("Archive") }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayman/ecolift/ui/navigation/SplitScreen.kt
git commit -m "feat(archive): add archive-cycle dialog with editable range and overlap warning"
```

### Task 14: Read-only archive detail screen + nav route

Tapping an archive card opens a read-only detail screen showing the per-cycle progress snapshot: cycle totals, then each split with its exercises and a date-bounded e1RM (or volume) trend sparkline. All numbers come straight from the frozen `CycleSnapshot` — already in true pounds (the builder is the only conversion boundary), so the screen does **no** weight conversion. A new `cycleArchive/{archiveId}` route hosts it; the `SplitScreen.onNavigateToArchiveDetail` callback added in Task 12 is finally wired to navigate there. A delete action (with confirmation) on the detail screen exercises `CycleArchiveViewModel.deleteArchive`.

**Files:**
- Create: `src/main/java/com/ayman/ecolift/ui/navigation/CycleArchiveDetailScreen.kt`
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModels.kt` (add `formatSignedLbs`)
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/AppNavigation.kt:107` (route builder), `:198-213` (wire `onNavigateToArchiveDetail`), and add the new `composable`
- Test: `src/test/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModelsTest.kt` (add a `formatSignedLbs` case)
- Test: `src/test/java/com/ayman/ecolift/ui/navigation/AppNavigationRoutesTest.kt` (add a route case)

- [ ] **Step 1: Add the failing test for `formatSignedLbs`**

Append this `@Test` inside the existing `CycleArchiveModelsTest` class (before its closing `}`):

```kotlin
    @Test
    fun `formatSignedLbs shows sign, rounds, and treats near-zero as no change`() {
        assertEquals("+25 lb", formatSignedLbs(25f))
        assertEquals("+13 lb", formatSignedLbs(12.6f))
        assertEquals("-12 lb", formatSignedLbs(-12f))
        assertEquals("no change", formatSignedLbs(0f))
        assertEquals("no change", formatSignedLbs(0.3f))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.CycleArchiveModelsTest"`
Expected: FAIL — won't compile (`formatSignedLbs` doesn't exist yet).

- [ ] **Step 3: Add `formatSignedLbs` to `CycleArchiveModels.kt`**

Append to the end of `src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModels.kt`:

```kotlin
/**
 * Formats an e1RM / weight delta in TRUE POUNDS as a signed headline:
 * "+25 lb", "-12 lb", or "no change" for anything inside ±0.5 lb. Rounds to whole pounds.
 */
fun formatSignedLbs(delta: Float): String = when {
    delta >= 0.5f -> "+%.0f lb".format(delta)
    delta <= -0.5f -> "%.0f lb".format(delta)
    else -> "no change"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.CycleArchiveModelsTest"`
Expected: PASS (all five tests).

- [ ] **Step 5: Add the failing route test**

Append this `@Test` inside the existing `AppNavigationRoutesTest` class (before its closing `}`):

```kotlin
    @Test
    fun `cycle archive route carries selected archive id`() {
        assertEquals("cycleArchive/7", buildCycleArchiveRoute(7L))
    }
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.ui.navigation.AppNavigationRoutesTest"`
Expected: FAIL — won't compile (`buildCycleArchiveRoute` doesn't exist yet).

- [ ] **Step 7: Add `buildCycleArchiveRoute` to `AppNavigation.kt`**

In `src/main/java/com/ayman/ecolift/ui/navigation/AppNavigation.kt`, add the builder right after `buildProgressRouteForExercise` (line 107):

```kotlin
internal fun buildProgressRouteForExercise(exerciseId: Long): String = "progress/$exerciseId"

internal fun buildCycleArchiveRoute(archiveId: Long): String = "cycleArchive/$archiveId"
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ayman.ecolift.ui.navigation.AppNavigationRoutesTest"`
Expected: PASS (all four tests).

- [ ] **Step 9: Create the detail screen**

This screen lives in package `com.ayman.ecolift.ui.navigation`, so it calls the `internal MiniSparkline` from `ProgressUiComponents.kt` (Task 10) directly — no import. It reuses the existing exercise-card visual language (`BackgroundElevated` card, 64×22 sparkline, teal-up / red-down delta coloring). Create `src/main/java/com/ayman/ecolift/ui/navigation/CycleArchiveDetailScreen.kt`:

```kotlin
package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.ExerciseSnapshot
import com.ayman.ecolift.data.SplitSnapshot
import com.ayman.ecolift.ui.theme.AccentTeal
import com.ayman.ecolift.ui.theme.BackgroundElevated
import com.ayman.ecolift.ui.theme.BackgroundPrimary
import com.ayman.ecolift.ui.theme.BackgroundSurface
import com.ayman.ecolift.ui.theme.ErrorRed
import com.ayman.ecolift.ui.theme.TextInactive
import com.ayman.ecolift.ui.theme.TextMuted
import com.ayman.ecolift.ui.theme.TextPrimary
import com.ayman.ecolift.ui.viewmodel.CycleArchiveViewModel
import com.ayman.ecolift.ui.viewmodel.formatArchiveDateRange
import com.ayman.ecolift.ui.viewmodel.formatSignedLbs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleArchiveDetailScreen(
    archiveId: Long,
    viewModel: CycleArchiveViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    LaunchedEffect(archiveId) { viewModel.loadArchive(archiveId) }
    DisposableEffect(archiveId) { onDispose { viewModel.clearDetail() } }

    val snapshot by viewModel.detail.collectAsStateWithLifecycle()
    val name by viewModel.detailName.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = name.ifBlank { "Archived cycle" },
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete archive",
                            tint = TextMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundPrimary,
                ),
            )
        },
    ) { innerPadding ->
        val cycle = snapshot
        if (cycle == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Loading…", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") { CycleSummaryCard(cycle) }
                cycle.splits.forEach { split ->
                    item(key = "split-${split.slotId}-${split.bucketKind}") {
                        SplitSectionHeader(split)
                    }
                    items(
                        items = split.exercises,
                        key = { ex -> "ex-${split.slotId}-${split.bucketKind}-${ex.exerciseId}" },
                    ) { ex -> ArchiveExerciseCard(ex) }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BackgroundSurface,
            title = { Text("Delete this archive?", color = TextPrimary) },
            text = {
                Text(
                    "This removes the saved snapshot. Your logged workouts are not affected.",
                    color = TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteArchive(archiveId)
                    onBack()
                }) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
        )
    }
}

@Composable
private fun CycleSummaryCard(cycle: CycleSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                formatArchiveDateRange(cycle.startDate, cycle.endDate),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text("${cycle.totals.spanDays} days", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryStat("Sessions", cycle.totals.sessions.toString())
                SummaryStat("Volume", "${"%,d".format(cycle.totals.totalVolumeLbs)} lb")
                SummaryStat("Sets", cycle.totals.totalSets.toString())
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), color = TextMuted, fontSize = 10.sp, letterSpacing = 0.06.sp)
    }
}

@Composable
private fun SplitSectionHeader(split: SplitSnapshot) {
    val range = if (split.firstUsedDate != null && split.lastUsedDate != null) {
        formatArchiveDateRange(split.firstUsedDate, split.lastUsedDate)
    } else {
        "not used this cycle"
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Text(split.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "${split.usageCount} ${if (split.usageCount == 1) "session" else "sessions"} · $range",
            color = TextMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ArchiveExerciseCard(ex: ExerciseSnapshot) {
    // Weighted lifts trend on e1RM; bodyweight (or weightless) lifts trend on volume.
    val isWeighted = !ex.isBodyweight && ex.endE1rm != null
    val trend: List<Float> = if (isWeighted) {
        ex.sessions.map { it.bestE1rm ?: 0f }
    } else {
        ex.sessions.map { it.volumeLbs.toFloat() }
    }
    val delta: Float = if (isWeighted) {
        (ex.endE1rm ?: 0f) - (ex.startE1rm ?: 0f)
    } else {
        ((ex.endVolumeLbs ?: 0L) - (ex.startVolumeLbs ?: 0L)).toFloat()
    }
    val deltaColor = when {
        delta > 0.5f -> AccentTeal
        delta < -0.5f -> ErrorRed
        else -> TextMuted
    }
    val sparkColor = when {
        delta > 0.5f -> AccentTeal
        delta < -0.5f -> ErrorRed
        else -> TextInactive
    }
    val headline = if (isWeighted) {
        "e1RM ${formatLbsShort(ex.startE1rm)} → ${formatLbsShort(ex.endE1rm)}  (${formatSignedLbs(delta)})"
    } else {
        "Volume ${"%,d".format(ex.startVolumeLbs ?: 0L)} → ${"%,d".format(ex.endVolumeLbs ?: 0L)} lb"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundElevated),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(headline, color = deltaColor, fontSize = 11.sp)
            }
            MiniSparkline(
                values = trend,
                color = sparkColor,
                modifier = Modifier.width(64.dp).height(22.dp),
            )
        }
    }
}

private fun formatLbsShort(value: Float?): String =
    if (value == null) "—" else "%.0f".format(value)
```

- [ ] **Step 10: Wire the route and the Split → detail navigation in `AppNavigation.kt`**

First, pass the detail-navigation callback into `SplitScreen` by adding `onNavigateToArchiveDetail` to the `composable("split")` block (after the `onNavigateToExerciseProgress = { ... }` lambda, line ~211):

```kotlin
                    onNavigateToExerciseProgress = { exerciseId ->
                        navController.navigate(buildProgressRouteForExercise(exerciseId)) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                        }
                    },
                    onNavigateToArchiveDetail = { archiveId ->
                        navController.navigate(buildCycleArchiveRoute(archiveId))
                    },
```

Then register the detail destination. Add this `composable` immediately after the `composable("split") { ... }` block closes and before `composable("backups")`:

```kotlin
            composable(
                route = "cycleArchive/{archiveId}",
                arguments = listOf(navArgument("archiveId") { type = NavType.LongType })
            ) { entry ->
                CycleArchiveDetailScreen(
                    archiveId = entry.arguments?.getLong("archiveId") ?: -1L,
                    onBack = { navController.popBackStack() },
                )
            }
```

`NavType`, `navArgument`, and `composable` are already imported in this file (used by the `log/{splitId}` and `progress/{exerciseId}` routes). The detail screen is pushed onto the back stack (no `popUpTo`), so the device back button / top-bar arrow returns to the Split tab's Archive view — matching how `backups` is navigated.

- [ ] **Step 11: Verify everything compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/ayman/ecolift/ui/navigation/CycleArchiveDetailScreen.kt \
        src/main/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModels.kt \
        src/main/java/com/ayman/ecolift/ui/navigation/AppNavigation.kt \
        src/test/java/com/ayman/ecolift/ui/viewmodel/CycleArchiveModelsTest.kt \
        src/test/java/com/ayman/ecolift/ui/navigation/AppNavigationRoutesTest.kt
git commit -m "feat(archive): add read-only cycle archive detail screen and route"
```

### Task 15: Remove the redundant "Split" title bar (do this LAST)

With the Current/Archive toggle pinned at the top of the Split tab, the `CenterAlignedTopAppBar` showing "Split / Cycle rotation" is redundant — it duplicates the bottom-nav label and steals vertical space. Remove it so the toggle sits directly under the status bar, exactly mirroring the Progress tab (`ProgressScreen`, `ProgressScreenV2.kt:697-714`, which has no `topBar` and applies `.statusBarsPadding()` to its body `Column`).

This is done **last** because the toggle (Task 12) must already occupy the top of the layout before the title bar is removed — otherwise there's an intermediate state with no top affordance at all.

Pure layout change — verified by compile and the Phase H visual check. No unit test.

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/CycleSplitScreenV2.kt` (remove 2 imports, remove `topBar`, add insets to body `Column`)

- [ ] **Step 1: Remove the two now-unused top-app-bar imports**

In `CycleSplitScreenV2.kt`, delete these import lines (line 37 and line 49). After this task they have no remaining references; `statusBarsPadding` (line 19) stays because it moves to the body `Column`.

```kotlin
import androidx.compose.material3.CenterAlignedTopAppBar
```

```kotlin
import androidx.compose.material3.TopAppBarDefaults
```

- [ ] **Step 2: Drop the `topBar` and add window insets to the body `Column`**

Find the `Scaffold` opening through the body `Column` line (the `topBar` block is unchanged from the original file; the `Column(modifier = Modifier.padding(innerPadding))` line is what Task 12 produced). Replace this exact block:

```kotlin
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                windowInsets = WindowInsets(0),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Split",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1C1E)
                        )
                        Text(
                            text = "Cycle rotation",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4DB6AC)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF2F0EB)
                )
            )
        },
        containerColor = Color(0xFFF2F0EB)
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
```

with this (no `topBar`; body `Column` now owns `.fillMaxSize().statusBarsPadding()` so the toggle clears the status bar and the weighted `LazyColumn` still has a bounded height):

```kotlin
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFFF2F0EB)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .statusBarsPadding()
        ) {
```

The closing braces of the `Column` and `Scaffold` are unchanged.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL, with no "unused import" warnings for `CenterAlignedTopAppBar` / `TopAppBarDefaults`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ayman/ecolift/ui/navigation/CycleSplitScreenV2.kt
git commit -m "refactor(split): remove redundant Split title bar in favor of top toggle"
```

---

## Phase H — Manual verification (the parts unit/instrumented tests can't cover)

These checks confirm the user-facing behavior and the spec's headline guarantees. Run after all prior tasks are green.

- [ ] **Step 1: Full automated suite is green**

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```
Expected: BUILD SUCCESSFUL for both. (The instrumented run needs a connected device/emulator.) If you only want the archive-related instrumented coverage: `./gradlew connectedDebugAndroidTest --tests "com.ayman.ecolift.data.DatabaseHardeningInstrumentedTest"`.

- [ ] **Step 2: Install and inspect the Split tab chrome**

Build & install (`./gradlew installDebug`). Open the **Split** tab. Confirm:
- No "Split / Cycle rotation" title bar; the **Current | Archive** toggle sits at the top, just under the status bar (visually matches the Progress tab's toggle position).
- **Current** mode shows the unchanged content: gym calendar, cycle on/off card, today's split hero (when enabled), rotation row, and "My splits".

- [ ] **Step 3: Archive the current cycle**

Switch to **Archive**. With no archives yet, confirm the empty state + "Archive current cycle" button. Tap it:
- The dialog opens with an empty **name**, a **start** date equal to the active cycle's start (first-ever logged day on a fresh cycle), and an **end** date equal to your latest logged workout (or today).
- Set the end date before the start date → the range error shows and **Archive** is disabled.
- Restore a valid range → confirm. A new card appears in the Archive list showing name, formatted range, and "N splits · M sessions · V lb".

- [ ] **Step 4: Confirm the live cycle advanced past the archived window**

Re-open the "Archive current cycle" dialog. The default **start** should now be the day *after* the previous end date (the repo set `cycle.startDate = endDate + 1 day`). This proves the next cycle won't double-count the archived window.

- [ ] **Step 5: Open the archive detail and verify the snapshot**

Tap the archive card:
- Header shows the cycle name, date range, and totals (sessions / volume / sets).
- Each split section lists its frozen name, usage count, and per-split date range, followed by its exercises.
- Each exercise shows an `e1RM start → end (±X lb)` (weighted) or `Volume start → end lb` (bodyweight) line and a sparkline, colored teal for gains / red for regressions.
- **Units sanity:** a lift you did at 200 lb shows `200`, not `2000` and not `20` — confirming the tenths→true-pounds conversion happened exactly once in `CycleSnapshotBuilder`.

- [ ] **Step 6: Prove the date-bounding guarantee (the "200 then 300" case)**

Log a new set the day *after* the archived `endDate` at a clearly higher weight than anything in the cycle. Re-open the same archive's detail. Its numbers must be **unchanged** — the post-window set is not counted. This is the core promise of the feature.

- [ ] **Step 7: Prove agent-logged sets count (Phase D)**

Have IronMind (the agent) log a set within an un-archived window, then archive that window. The agent-logged set must appear in the snapshot totals (it defaults to `completed = true`).

- [ ] **Step 8: Delete an archive**

On the detail screen, tap the trash icon → confirm. You return to the Archive list and the card is gone. Re-open the app to confirm it stays deleted.

- [ ] **Step 9: Backup / restore round-trip**

In Backups, export a backup that includes at least one archive. Then either restore it over the current data or reinstall and restore:
- Archives reappear with intact detail.
- Restoring an **older, pre-archive** backup file (one with no `archivedCycles` key) still succeeds without error — the `= emptyList()` default makes the field optional.

---

## Done

All tasks complete: the snapshot model + pure builder (A), persistence with migration 13→14 (B), repository orchestration (C), the `completed` round-trip through the agent patch layer (D), backup/restore wiring (E), instrumentation (F), the Current/Archive UI with detail screen (G), and the title-bar cleanup + manual QA (H).
