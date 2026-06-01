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
    val totalSessions: Int,           // distinct workout days with >=1 completed set, within window
    val totalVolumeLbs: Long,         // sum(weightLbs*reps) over COMPLETED in-window sets, true pounds
    val splitCount: Int,
    // Frozen payload:
    val snapshotSchemaVersion: Int,   // == CycleSnapshot.schemaVersion at write time
    val snapshotJson: String,         // serialized CycleSnapshot
)
```

> **Volume units:** stored as **true pound-volume** (`sum(weightLbs*reps)`, no division).
> Field names use the `Lbs` suffix to make the unit explicit. This deliberately does **not**
> reuse the `getVolumeHistory` convention (which divides by 10 to keep sparkline numbers
> small) — that scaling is a *display* concern. If a sparkline needs small numbers it scales
> at render time; stored data stays in real pounds so totals are unambiguous and never
> double-divided.

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
    val sessions: Int,            // distinct days with >=1 completed set
    val totalVolumeLbs: Long,     // true pounds, completed sets only
    val totalSets: Int,           // completed sets only
    val spanDays: Int,            // endDate - startDate + 1
)

@Serializable
data class SplitSnapshot(
    val slotId: Long,             // original CycleSlot id; -1 == the synthetic "Unassigned" bucket
    val name: String,             // frozen split name (or "Unassigned")
    val orderIndex: Int,          // Unassigned sorts last
    val firstUsedDate: String?,   // "from what day"  (null if never used in window)
    val lastUsedDate: String?,    // "to what day"
    val usageCount: Int,          // "how many times used" = distinct in-window days tagged to this slot WITH >=1 completed set
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
    val startVolumeLbs: Long?, val endVolumeLbs: Long?,
)

@Serializable
data class SessionPoint(
    val date: String,
    val topWeight: Int?,          // max weightLbs that day (completed sets)
    val bestE1rm: Float?,         // best Epley across completed sets that day
    val volumeLbs: Long,          // sum(weightLbs*reps) that day, true pounds, completed sets
    val totalReps: Int,
    val setCount: Int,            // completed sets that day
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
        sets: List<WorkoutSet>,                // in-window sets; MAY include completed=false — builder filters
        exerciseNames: Map<Long, ExerciseMeta>,// id -> ExerciseMeta(name, isBodyweight), frozen
    ): CycleSnapshot
}
```

`ExerciseMeta` is a tiny builder-input holder (`data class ExerciseMeta(val name: String,
val isBodyweight: Boolean)`). The ViewModel/repo fetches the inputs (range query in §6),
builds the name/meta map from `ExerciseDao.getByIds(...)`, and calls this. The builder is
the single place that applies the completed-set filter (§5.2), keeping the rule testable.

### 5.2 The windowing + completed-set rule

Two filters define what counts:
1. **Window:** only sets with `startDate <= date <= endDate` (string compare works for ISO
   dates). A cycle ending at 200 lb bench with a 300 lb bench the next day excludes the 300.
2. **Completed only:** only sets with `completed == true`. The app persists *template/planned*
   sets (`SetRepository.addExerciseSession`, `cloneDay`) with `completed = false`; the user
   toggles completion when a set is actually performed. Counting incomplete sets would
   inflate exercises, volume, e1RM, and session counts with work that never happened.

The builder applies the completed filter itself (`sets.filter { it.completed }`) so the rule
is unit-testable; the DAO range query may return all in-window sets.

> **Deliberate divergence:** the existing live progress queries (`getVolumeHistory`,
> `getVolumesSince`, `getMaxWeightBeforeDate`, …) do **not** filter `completed`, so live
> Progress-tab numbers can include planned sets. The archive intentionally diverges to report
> only performed work. We are **not** changing those existing queries in this iteration (out
> of scope); a brief note in the spec flags the inconsistency for a future cleanup.

### 5.3 Per-exercise endpoints

- Group in-window sets by `exerciseId`, then by `date` → `SessionPoint` per day.
- **start** values = the first (earliest) in-window `SessionPoint`; **end** = the last.
- e1RM per set = **Epley**: `weight * (1 + reps/30)`, take the max across the day's sets.
- Weight conversion uses the existing `WeightLbs.toLbs(weightLbs)` helper; bodyweight sets
  (`isBodyweight = true` or `weightLbs == null`) contribute `0` weight to volume/e1RM and
  are flagged via `isBodyweight` so the card can label them rather than show "0 lb".

### 5.4 Bucketing sets into splits (+ Unassigned)

Every completed in-window set is assigned to exactly one bucket by its **day's** slot:
`slot = WorkoutDay[set.date].cycleSlotId`. Since `WorkoutDay.cycleSlotId` is **nullable**,
sets on untagged days go into a synthetic **"Unassigned"** bucket (`slotId = -1`, sorts
last). This guarantees the per-split sections **partition** all counted work, so the
per-split numbers reconcile exactly with the cycle totals (totals = sum over all buckets,
including Unassigned). Without this, untagged days would inflate totals but appear in no
split section.

For each bucket (each `CycleSlot`, plus Unassigned):
- `usageCount` = count of distinct in-window days in this bucket that have ≥1 completed set.
- `firstUsedDate` / `lastUsedDate` = min/max of those days' dates.
- `exercises` = exercises **actually performed** (completed) on those days, ordered by the
  saved `SplitExercise.orderIndex` when present, else first-seen. Reflects what they *did*,
  not the saved template. Per-exercise `sessions[]` / endpoints are scoped to this bucket's
  days (an exercise done in two splits appears once per split with that split's progress).
- A split with no completed work in the window is still emitted (usageCount 0, empty
  exercises) so the user sees it existed; Unassigned is omitted entirely when empty.

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

`WorkoutSetDao` (returns all in-window sets; builder applies the completed filter so
exclusion stays unit-testable):
```kotlin
@Query("SELECT * FROM workout_set WHERE date >= :start AND date <= :end ORDER BY date ASC, exerciseId ASC, setNumber ASC")
suspend fun getSetsInRange(start: String, end: String): List<WorkoutSet>
```

`WorkoutRepository`:
- `suspend fun archiveCurrentCycle(name, startDate, endDate): Long` — **runs inside
  `db.withTransaction { }`**: build snapshot → insert `ArchivedCycle` → reset active cycle
  (`cycle.startDate` = `endDate + 1`, `cycle.name` = null), all atomic. Without the
  transaction, a crash/cancel mid-way could insert an archive without advancing the active
  start (double-counting next cycle) or advance the start without saving the archive (lost
  cycle). Splits are **left untouched** (non-destructive).
- `fun observeArchivedCycles(): Flow<List<ArchivedCycle>>`
- `suspend fun getArchivedCycle(id): ArchivedCycle?`
- `suspend fun deleteArchivedCycle(id)`
- `suspend fun getActiveCycleStart(): String` — `cycle.startDate` or earliest logged date.

**Gotcha — preserve new `Cycle` fields:** `WorkoutRepository.saveCycle` currently rebuilds
the row manually (`Cycle(id=1, isActive=…, numTypes=…, nextSessionType=current.nextSessionType)`),
which would **wipe** the new `startDate`/`name` on every toggle. Change it to
`current.copy(isActive = …, numTypes = …)` so the new fields survive. (`setNextSessionType`
and `syncCycleSlotCount` already use `copy(...)`, so they're fine.)

### 6.1 Backup / restore support (REQUIRED — else archives are silently lost)

`DataBackupManager` enumerates every user table for export/import/clear. A new table not
added here would survive normal use but **vanish on any restore/import** (because
`clearUserTables` wipes the DB first and restore only re-inserts what the payload lists).
Required changes:

1. `UserDataBackup`: add `val archivedCycles: List<ArchivedCycle> = emptyList()` (defaulted,
   so older backup JSON still decodes with `ignoreUnknownKeys`/default).
2. `buildSnapshot`: `archivedCycles = db.archivedCycleDao().getAll()` (add a `getAll()` to the
   DAO for this).
3. `restoreSnapshot`: `if (snapshot.archivedCycles.isNotEmpty()) db.archivedCycleDao().insertAll(...)`.
4. `clearUserTables`: add `DELETE FROM \`archived_cycle\``.
5. Bump `APP_DB_VERSION` `13 → 14` to match the Room version.
6. `toResult()`: include `archivedCycles.size` in `entryCount`.
7. The backup round-trip test (§10) must assert archived cycles survive export → import.

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
  The **"Unassigned"** bucket (if present) renders as a final, visually muted section so the
  totals reconcile and stray logged days are still visible.
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
- `data/ArchivedCycle.kt`, `data/ArchivedCycleDao.kt` (incl. `getAll()` + `insertAll()` for backup)
- `data/CycleSnapshot.kt` (snapshot models + `ExerciseMeta`)
- `data/CycleSnapshotBuilder.kt` (pure builder)
- `ui/navigation/CycleArchiveDetailScreen.kt`
- `ui/viewmodel/CycleArchiveViewModel.kt`
- Tests: `src/test/java/.../CycleSnapshotBuilderTest.kt`

**Modified**
- `data/Cycle.kt` (+`startDate`, +`name`)
- `data/Migrations.kt` (Migration 13→14), `data/AppDatabase.kt` (version 14, register entity/DAO)
- `data/WorkoutSetDao.kt` (+`getSetsInRange`)
- `data/WorkoutRepository.kt` (transactional archive + observe/get/delete + active-start helpers; **fix `saveCycle` to `copy(...)`**)
- `data/DataBackupManager.kt` (+`archivedCycles` in payload/build/restore/clear/toResult, bump `APP_DB_VERSION` → 14) — see §6.1
- `ui/navigation/CycleSplitScreenV2.kt` (Current/Archive toggle, archive action, header cleanup)
- `ui/navigation/SplitScreen.kt` (pass-through wiring for archive nav/actions)
- `ui/navigation/AppNavigation.kt` (archive detail route)
- `ui/viewmodel/SplitViewModel.kt` (archive action + active-cycle state, if not in new VM)

## 10. Testing

**Unit (JVM, `src/test`) on `CycleSnapshotBuilder`:**
- Windowing cutoff: a set one day after `endDate` is excluded from end values & totals.
- **Completed filter:** incomplete (`completed = false`) sets are excluded from volume, e1RM,
  session counts, and exercise lists — feed a mix and assert only completed work counts.
- e1RM: 185×5 → 185×8 reports positive delta; top-weight unchanged.
- `usageCount` / `firstUsedDate` / `lastUsedDate` correct across multiple slot days, counting
  only days with ≥1 completed set.
- **Unassigned reconciliation:** sets on `cycleSlotId = null` days land in the Unassigned
  bucket; sum of all buckets' volume/sets == cycle totals.
- Single-session exercise → start == end, delta neutral.
- Bodyweight exercise → no crash, flagged, zero-weight handling.
- Empty cycle → zeroed totals, no crash.
- Exercises ordered by saved `SplitExercise.orderIndex` when present.

**Instrumentation (`src/androidTest`):**
- **Migration 13→14**: extend the existing migration coverage
  (`DatabaseHardeningInstrumentedTest`) — open a v13 DB, run `Migration(13,14)`, assert
  `cycle.startDate`/`cycle.name` columns exist (nullable) and `archived_cycle` is created.
- **Backup round-trip**: insert an `ArchivedCycle`, export → wipe → import, assert it
  survives identically (extend the existing backup round-trip test).

**Manual (run the app):**
- Archive a cycle with edited start/end dates; confirm card numbers match logged data,
  ignore out-of-window sets, and ignore not-completed template sets.
- Current/Archive toggle switches views; header no longer shows redundant "Split".
- Delete an archive; list updates; active cycle continues uninterrupted.
- Toggle the cycle switch after archiving; confirm `startDate`/`name` are preserved.

## 11. Decisions locked in

- Unit of archiving = **one cycle holding all splits**, with per-split breakdowns inside.
- Cycle window = active start (defaulted, editable) → user-chosen end (both confirmable at
  archive time).
- Storage = **frozen JSON snapshot** + denormalized headline columns.
- Metrics = e1RM + total volume headline, top weight + full per-session series stored.
- Archiving is **non-destructive**: live splits carry into the next cycle.
- **Completed sets only** count toward the snapshot (template/planned sets excluded);
  intentionally diverges from the unfiltered live progress queries.
- Volume stored as **true pounds** (`*Lbs` fields), no `/10` scaling — scaling is display-only.
- Untagged days go in a synthetic **"Unassigned"** bucket so per-split numbers reconcile with
  totals.
- Archive insert + active-cycle reset are **atomic** (`db.withTransaction`).
- **`archived_cycle` is wired into `DataBackupManager`** (export/import/clear) — non-optional.
