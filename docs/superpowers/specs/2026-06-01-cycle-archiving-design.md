# Cycle Archiving & Progress Snapshot — Design

**Date:** 2026-06-01
**Status:** Approved for planning
**Area:** Split tab (data + UI)

## 1. Summary

Let users **archive a training cycle** — a self-defined period during which they ran a
particular rendition of their splits. Archiving freezes an immutable snapshot of what
they did: for each split, the exercises performed, the date range it was used, and how
many times it was used; and a per-exercise progress summary (estimated 1RM, top weight,
volume) bounded strictly to the cycle's date window. Users can then browse past cycles
and view a "snapshot card" showing how they progressed during each one.

The feature is designed to be **future-proof**: new metrics or displayed fields can be
added later without a database migration.

## 2. Terminology (important — naming collision)

The existing `Cycle` entity (`data/Cycle.kt`, table `cycle`, singleton `id=1`) is **not** a
training period — it is a global rotation toggle (`isActive`, `numTypes`, `nextSessionType`).
A "split" in code is a `CycleSlot` row (`cycle_slot`), e.g. "Push", "Pull", "Legs".

To avoid collision, the new training-period concept is named **ArchivedCycle**
(user-facing: "Cycle" / "Past cycle"). The *active* (current, not-yet-archived) cycle is
represented by adding a start date + optional name to the existing `cycle` singleton row.

| Concept | Code | User-facing |
|---|---|---|
| A saved workout template (Push/Pull/…) | `CycleSlot` | "Split" |
| Global rotation toggle / current cycle metadata | `Cycle` (singleton) | (invisible) + "Current cycle" |
| A frozen past training period | `ArchivedCycle` (new) | "Past cycle" / "Archive" |

## 3. Goals & non-goals

**Goals**
- Archive the current cycle in one action, with confirmable/editable start and end dates.
- Per-split breakdown: exercises done, first→last used date, usage count.
- Per-exercise progress snapshot (e1RM, top weight, volume; start vs end within window).
- Browse past cycles; view a progress "snapshot card" per cycle.
- Snapshot is **immutable** — later edits/deletes to live workouts never change a past cycle.
- Extensible: add a new displayed metric without a DB migration.

**Non-goals (this iteration)**
- Editing/merging/splitting an already-archived cycle (delete-only is enough for now).
- Cloud sync of archives (local Room only, consistent with the app).
- AI commentary on cycles (can layer on later from the stored snapshot).
- Comparing two cycles side by side (future; the data model supports it later).

## 4. Data model

### 4.1 Migration (Room v13 → v14)

1. Add two nullable columns to the existing `cycle` table:
   - `startDate TEXT` — the active cycle's start (ISO `yyyy-MM-dd`). `NULL` → treat the
     earliest logged `workout_set.date` as the effective start.
   - `name TEXT` — optional user label for the active cycle.
2. Create the `archived_cycle` table (below).

Add a hand-written `Migration(13, 14)` to `data/Migrations.kt`, bump `version = 14` in
`AppDatabase.kt`, and register `ArchivedCycle` + `archivedCycleDao()`. `exportSchema = true`
is already set, so the v14 schema JSON will be generated under `schemas/`.

### 4.2 New entity: `ArchivedCycle`

```kotlin
@Entity(tableName = "archived_cycle")
@Serializable
data class ArchivedCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // user label or auto ("Cycle 1", date-range fallback)
    val startDate: String,            // ISO yyyy-MM-dd, inclusive
    val endDate: String,              // ISO yyyy-MM-dd, inclusive
    val archivedAt: Long,             // epoch millis, when the snapshot was taken
    // Denormalized headline fields — drive the archive LIST without parsing JSON:
    val totalSessions: Int,           // distinct workout days within window
    val totalVolumeTenths: Long,      // sum(weight*reps)/10 within window (matches existing volume scaling)
    val splitCount: Int,
    // Frozen payload:
    val snapshotSchemaVersion: Int,   // == CycleSnapshot.schemaVersion at write time
    val snapshotJson: String,         // serialized CycleSnapshot
)
```

> Note: volume is stored in tenths to match the existing app convention
> (`getVolumeHistory` divides by 10). Display divides back as needed.

### 4.3 Frozen snapshot models (`data/CycleSnapshot.kt`, `@Serializable`)

```kotlin
@Serializable
data class CycleSnapshot(
    val schemaVersion: Int = 1,
    val startDate: String,
    val endDate: String,
    val totals: CycleTotals,
    val splits: List<SplitSnapshot>,
)

@Serializable
data class CycleTotals(
    val sessions: Int,            // distinct days
    val totalVolumeTenths: Long,
    val totalSets: Int,
    val spanDays: Int,            // endDate - startDate + 1
)

@Serializable
data class SplitSnapshot(
    val slotId: Long,             // original CycleSlot id (may be deleted later; informational)
    val name: String,             // frozen split name
    val orderIndex: Int,
    val firstUsedDate: String?,   // "from what day"  (null if never used in window)
    val lastUsedDate: String?,    // "to what day"
    val usageCount: Int,          // "how many times used" = distinct days tagged to this slot
    val exercises: List<ExerciseSnapshot>,
)

@Serializable
data class ExerciseSnapshot(
    val exerciseId: Long,
    val name: String,             // frozen exercise name
    val isBodyweight: Boolean,
    val sessions: List<SessionPoint>,   // per-day aggregates within window, date-ascending
    // Precomputed headline endpoints (start = first in-window session, end = last):
    val startE1rm: Float?,  val endE1rm: Float?,
    val startTopWeight: Int?, val endTopWeight: Int?,
    val startVolumeTenths: Long?, val endVolumeTenths: Long?,
)

@Serializable
data class SessionPoint(
    val date: String,
    val topWeight: Int?,          // max weightLbs that day
    val bestE1rm: Float?,         // best Epley across sets that day
    val volumeTenths: Long,       // sum(weight*reps)/10 that day
    val totalReps: Int,
    val setCount: Int,
)
```

The `sessions[]` list is the future-proofing core: any new metric that is a function of
per-day aggregates can be computed from stored data when displaying an old archive.

### 4.4 DAO: `ArchivedCycleDao`

```kotlin
@Dao
interface ArchivedCycleDao {
    @Insert suspend fun insert(cycle: ArchivedCycle): Long
    @Query("SELECT * FROM archived_cycle ORDER BY endDate DESC, id DESC")
    fun observeAll(): Flow<List<ArchivedCycle>>
    @Query("SELECT * FROM archived_cycle WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ArchivedCycle?
    @Query("DELETE FROM archived_cycle WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM archived_cycle")
    suspend fun count(): Int
}
```

## 5. Snapshot computation

### 5.1 Pure builder (`data/CycleSnapshotBuilder.kt`)

A pure function with no Android/Room dependencies, for trivial unit testing:

```kotlin
object CycleSnapshotBuilder {
    fun build(
        startDate: String,
        endDate: String,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,   // saved template lists (for ordering/empty splits)
        workoutDays: List<WorkoutDay>,         // for usage counts + which slot each day was
        sets: List<WorkoutSet>,                // ALL sets within [startDate, endDate]
        exerciseNames: Map<Long, ExerciseMeta>,// id -> (name, isBodyweight), frozen
    ): CycleSnapshot
}
```

The ViewModel/repo fetches the inputs (new range query in §6) and calls this.

### 5.2 The windowing rule

Only sets with `startDate <= date <= endDate` (string compare works for ISO dates) are
considered. This enforces the requirement directly: a cycle ending at 200 lb bench with a
300 lb bench logged the next day excludes the 300 — it falls outside the window.

### 5.3 Per-exercise endpoints

- Group in-window sets by `exerciseId`, then by `date` → `SessionPoint` per day.
- **start** values = the first (earliest) in-window `SessionPoint`; **end** = the last.
- e1RM per set = **Epley**: `weight * (1 + reps/30)`, take the max across the day's sets.
- Weight conversion uses the existing `WeightLbs.toLbs(weightLbs)` helper; bodyweight sets
  (`isBodyweight = true` or `weightLbs == null`) contribute `0` weight to volume/e1RM and
  are flagged via `isBodyweight` so the card can label them rather than show "0 lb".

### 5.4 Per-split fields

For each `CycleSlot`:
- `usageCount` = count of distinct in-window `WorkoutDay` rows with `cycleSlotId == slot.id`.
- `firstUsedDate` / `lastUsedDate` = min/max of those days' dates.
- `exercises` = exercises **actually performed** on those days (union of `sets` exerciseIds
  on the slot's days, ordered by the saved `SplitExercise.orderIndex` when present, else by
  first-seen). This reflects what they *did*, not just the saved template.

### 5.5 Edge cases

- **Empty cycle** (no sets in window): still archivable; totals zero, splits show usage 0.
  Archive action warns ("No workouts logged in this range — archive anyway?").
- **Single-session exercise**: start == end; delta = 0. Card shows "1 session — not enough
  to compare" instead of a misleading 0%.
- **Exercise/split deleted after archive**: names are frozen in the snapshot, so display is
  unaffected. `slotId`/`exerciseId` are informational only.
- **start value of 0** (e.g. all-bodyweight): delta % is `null`; card shows absolute change
  or "—".
- **Sets spanning a day exactly on the boundary**: boundaries are inclusive.

## 6. Repository & query additions

`WorkoutSetDao`:
```kotlin
@Query("SELECT * FROM workout_set WHERE date >= :start AND date <= :end ORDER BY date ASC, exerciseId ASC, setNumber ASC")
suspend fun getSetsInRange(start: String, end: String): List<WorkoutSet>
```

`WorkoutRepository`:
- `suspend fun archiveCurrentCycle(name, startDate, endDate): Long` — builds the snapshot,
  inserts `ArchivedCycle`, then resets the active cycle (`cycle.startDate` = `endDate + 1`,
  `cycle.name` = null). Splits are **left untouched** (non-destructive).
- `fun observeArchivedCycles(): Flow<List<ArchivedCycle>>`
- `suspend fun getArchivedCycle(id): ArchivedCycle?`
- `suspend fun deleteArchivedCycle(id)`
- `suspend fun getActiveCycleStart(): String` — `cycle.startDate` or earliest logged date.

## 7. UI

### 7.1 Split tab top toggle: Current | Archive

Mirror the Progress tab's existing segmented control
(`ProgressOrganizationModeV2` PROGRESS/SPLIT, rendered in `ProgressScreenV2.kt`
~lines 801–826). Add a new mode enum (e.g. `SplitTabMode { CURRENT, ARCHIVE }`) to the
live Split screen `CycleSplitScreen` (`ui/navigation/CycleSplitScreenV2.kt`):
- **Current** → existing split/rotation UI (unchanged) + an "End cycle & archive" action.
- **Archive** → the past-cycles list (§7.3).

### 7.2 Header cleanup (do last)

The live header is a `CenterAlignedTopAppBar` in `CycleSplitScreenV2.kt` (~line 674) showing
title "Split" + subtitle "Cycle rotation". The bottom nav already labels this tab "Split",
so the title is redundant. Replace it so the top no longer repeats "Split" — match whatever
treatment the Progress tab now uses (e.g. drop the redundant title, keep the segmented
toggle as the top element). This is the lowest-priority item per the user; do it last.

> Implementer note: `SplitScreen.kt` also contains a legacy `SplitScreenContent` +
> `Header()` ("Cycle / Split" / "WORKOUT ROTATION SETTINGS") that is **not** on the live
> path (`SplitScreen` delegates to `CycleSplitScreen`). Edit the live `CycleSplitScreenV2.kt`
> header, not the dead one. Confirm before touching either.

### 7.3 Archive action + dialog

In the Current view, an "End cycle & archive" affordance opens a dialog that:
- Shows the **detected start date** (active cycle start) and **detected end date** (today or
  last logged day), **both editable** via date pickers (user's explicit ask).
- Optional cycle name field (defaults to "Cycle N" or the date range).
- Confirm → `archiveCurrentCycle(...)`, toast/snackbar confirmation, switch to Archive view.
- Validates `start <= end`; warns on empty range.

### 7.4 Archive list (Archive view)

A `LazyColumn` of past cycles (newest first), each a card: name, date range, span, #
sessions, total volume, split count, and a small overall trend hint. Tap → detail screen.
Long-press or overflow → delete (with confirm). Reuses existing card styling (`Card`,
`AccentTeal`, `MiniSparkline`).

### 7.5 Cycle snapshot detail screen (`CycleArchiveDetailScreen`)

New route. Renders the frozen snapshot:
- **Header**: name, date range, span days, total sessions, total volume.
- **Highlights**: biggest gainer / biggest regression (by e1RM delta), "most trained" split.
- **Per-split sections**: split name + `usageCount` + date range, then per-exercise rows
  reusing the existing progress-row style: name, start→end (e1RM / top weight / volume),
  signed % delta with up/down color (`AccentTeal` / `ErrorRed`), and a `MiniSparkline` from
  `sessions[]`. e1RM leads; tapping a row can expand to show top-weight & volume deltas too.
- Single-session / no-data exercises render the explanatory label, not a fake 0%.

### 7.6 Navigation

Add routes for the archive detail screen in `AppNavigation.kt`; wire the Split screen's
Archive list item → detail. ViewModel: `CycleArchiveViewModel` (observe list, load one by
id, delete). The active-cycle/archive actions can live on the existing `SplitViewModel` or a
dedicated VM — implementer's call; keep `SplitViewModel` from ballooning.

## 8. Future-proofing mechanism

- New displayed metric derivable from per-day aggregates → add a field to `ExerciseSnapshot`
  (nullable/defaulted) + bump `CycleSnapshot.schemaVersion`. New archives populate it; old
  archives recompute it on read from their stored `sessions[]`, or show "—". **No DB
  migration.**
- If a future metric needs data not in `sessions[]`, extend `SessionPoint` (defaulted field)
  and bump the version; old archives degrade gracefully.
- `snapshotSchemaVersion` on the row lets the reader branch on payload shape.

## 9. Files touched

**New**
- `data/ArchivedCycle.kt`, `data/ArchivedCycleDao.kt`
- `data/CycleSnapshot.kt` (snapshot models)
- `data/CycleSnapshotBuilder.kt` (pure builder)
- `ui/navigation/CycleArchiveDetailScreen.kt`
- `ui/viewmodel/CycleArchiveViewModel.kt`
- Tests: `src/test/java/.../CycleSnapshotBuilderTest.kt`

**Modified**
- `data/Cycle.kt` (+`startDate`, +`name`)
- `data/Migrations.kt` (Migration 13→14), `data/AppDatabase.kt` (version 14, register entity/DAO)
- `data/WorkoutSetDao.kt` (+`getSetsInRange`)
- `data/WorkoutRepository.kt` (archive + observe/get/delete + active-start helpers)
- `ui/navigation/CycleSplitScreenV2.kt` (Current/Archive toggle, archive action, header cleanup)
- `ui/navigation/SplitScreen.kt` (pass-through wiring for archive nav/actions)
- `ui/navigation/AppNavigation.kt` (archive detail route)
- `ui/viewmodel/SplitViewModel.kt` (archive action + active-cycle state, if not in new VM)

## 10. Testing

**Unit (JVM, `src/test`) on `CycleSnapshotBuilder`:**
- Windowing cutoff: a set one day after `endDate` is excluded from end values & totals.
- e1RM: 185×5 → 185×8 reports positive delta; top-weight unchanged.
- `usageCount` / `firstUsedDate` / `lastUsedDate` correct across multiple slot days.
- Single-session exercise → start == end, delta neutral.
- Bodyweight exercise → no crash, flagged, zero-weight handling.
- Empty cycle → zeroed totals, no crash.
- Exercises ordered by saved `SplitExercise.orderIndex` when present.

**Manual (run the app):**
- Archive a cycle with edited start/end dates; confirm card numbers match logged data and
  ignore out-of-window sets.
- Current/Archive toggle switches views; header no longer shows redundant "Split".
- Delete an archive; list updates; active cycle continues uninterrupted.

## 11. Decisions locked in

- Unit of archiving = **one cycle holding all splits**, with per-split breakdowns inside.
- Cycle window = active start (defaulted, editable) → user-chosen end (both confirmable at
  archive time).
- Storage = **frozen JSON snapshot** + denormalized headline columns.
- Metrics = e1RM + total volume headline, top weight + full per-session series stored.
- Archiving is **non-destructive**: live splits carry into the next cycle.
