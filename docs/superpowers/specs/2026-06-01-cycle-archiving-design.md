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
    val totalSessions: Int,           // distinct in-window days with >=1 completed set (all buckets incl. Unassigned)
    val totalVolumeLbs: Long,         // true pounds, completed in-window sets — see "Units" below
    val splitCount: Int,              // user-defined splits actually trained (usageCount>0); EXCLUDES Unassigned — see §5.5
    // Frozen payload:
    val snapshotSchemaVersion: Int,   // == CycleSnapshot.schemaVersion at write time
    val snapshotJson: String,         // serialized CycleSnapshot
)
```

> **Units (critical — `weightLbs` is stored in tenths):** `WorkoutSet.weightLbs` is pounds ×10
> (`WeightLbs.SCALE = 10`; `1850` = 185.0 lb), so a raw `sum(weightLbs*reps)` is **tenths of a
> pound-rep**, not pounds. The snapshot stores **display units (true pounds)** for every weight
> field, and **`CycleSnapshotBuilder` is the single conversion boundary** — it converts each set
> once via `WeightLbs.toLbs(weightLbs)` (= `weightLbs / 10.0`) before any sum/max. Concretely:
> - `volumeLbs` (all levels) `= round(Σ WeightLbs.toLbs(weightLbs) × reps)` → `Long`, true pounds.
>   This equals the existing `getVolumeHistory` SQL (`ROUND(SUM(weightLbs*reps)/10.0)`), so archive
>   volume and Progress sparkline numbers are on the same scale.
> - `topWeight` `= max(WeightLbs.toLbs(weightLbs))` → `Float` (preserves the .5 in e.g. 187.5).
> - `e1rm` `= max(toLbs(weightLbs) × (1 + reps/30))` → `Float`, true pounds.
>
> Nothing downstream divides again — the stored numbers are render-ready, never double-divided.

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
    val slotId: Long,             // original CycleSlot id; -1 == "Unassigned", -2 == "Deleted split" (§5.4)
    val name: String,             // frozen split name (or "Unassigned" / "Deleted split")
    val orderIndex: Int,          // real splits first, then Deleted split, then Unassigned last
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
    // Precomputed headline endpoints (start = first in-window session, end = last).
    // All weights are TRUE POUNDS (builder converts via WeightLbs.toLbs); see §4.2 "Units".
    val startE1rm: Float?,  val endE1rm: Float?,
    val startTopWeight: Float?, val endTopWeight: Float?,
    val startVolumeLbs: Long?, val endVolumeLbs: Long?,
)

@Serializable
data class SessionPoint(
    val date: String,
    val topWeight: Float?,        // max true-pound weight that day (completed sets); via WeightLbs.toLbs
    val bestE1rm: Float?,         // best Epley (true pounds) across completed sets that day
    val volumeLbs: Long,          // round(Σ toLbs(weightLbs)×reps) that day, true pounds, completed sets
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
    @Insert suspend fun insertAll(cycles: List<ArchivedCycle>)        // backup restore (§6.1)
    @Query("SELECT * FROM archived_cycle ORDER BY endDate DESC, id DESC")
    fun observeAll(): Flow<List<ArchivedCycle>>
    @Query("SELECT * FROM archived_cycle ORDER BY endDate DESC, id DESC")
    suspend fun getAll(): List<ArchivedCycle>                         // backup export (§6.1)
    @Query("SELECT * FROM archived_cycle WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ArchivedCycle?
    @Query("DELETE FROM archived_cycle WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM archived_cycle")
    suspend fun count(): Int
    // Overlap detection for the archive dialog. Inclusive ISO ranges overlap iff
    // NOT (existing.end < new.start OR existing.start > new.end). String compare is valid for ISO.
    @Query("SELECT COUNT(*) FROM archived_cycle WHERE NOT (endDate < :start OR startDate > :end)")
    suspend fun countOverlapping(start: String, end: String): Int
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

**Write-path fix — agent/imported logs must count.** The completed-only filter makes one
existing write path a problem: `PatchService.applyPatch` (`agent/patches/PatchService.kt`)
inserts a `WorkoutSet` from a `DbPatch.LogSet` without setting `completed`, so it defaults to
`false`. A `LogSet` represents a set the user states they *performed* ("log bench 185×5"), so
under the filter it would be silently dropped from the archive (and, today, it already fails to
count toward TodayScreen volume / completion %). Fix at the source:
1. Add `val completed: Boolean = true` to `DbPatch.LogSet` (`agent/model/DbPatch.kt`) — default
   `true` because logged == performed; an agent that ever logs a *planned* set can pass `false`.
2. Pass it through in `PatchService.applyPatch`'s `LogSet` branch (`completed = patch.completed`).
3. **`InverseComputer` must round-trip `completed`.** The inverse of a `DeleteSet` is a `LogSet`
   that restores the deleted row (`InverseComputer.kt`, the `DeleteSet` branch). Today it omits
   `completed`; once the field defaults to `true`, undoing the deletion of an *incomplete*
   (template/planned) set would resurrect it as completed. Set `completed = current.completed`
   in that inverse `LogSet` so undo is faithful. (The forward `LogSet` → `DeleteSet` inverse
   needs nothing, since `DeleteSet` carries no `completed`.)

This is **forward-fix only**: any sets the agent already inserted with `completed = false` stay
that way (we cannot reliably know which were performed, and a blind backfill is riskier than the
gap). Validation is unaffected by an added defaulted field.

### 5.3 Per-exercise endpoints

- Group in-window sets by `exerciseId`, then by `date` → `SessionPoint` per day.
- **start** values = the first (earliest) in-window `SessionPoint`; **end** = the last.
- **Convert weight once, up front:** for every set compute `w = WeightLbs.toLbs(weightLbs)`
  (true pounds). All formulas below use `w`, never raw `weightLbs` (which is tenths — see
  §4.2 "Units"):
  - e1RM per set = **Epley**: `w * (1 + reps/30)`; the day's `bestE1rm` = max over its sets.
  - `topWeight` = max `w` across the day's completed sets (`Float`, keeps .5).
  - `volumeLbs` = `round(Σ w × reps)` (`Long`, true pounds).
- Bodyweight sets (`isBodyweight = true` or `weightLbs == null`) contribute `0` weight to
  volume/e1RM and are flagged via `isBodyweight` so the card can label them rather than show
  "0 lb".

### 5.4 Bucketing sets into splits (+ Unassigned)

Every completed in-window set is assigned to exactly one bucket by its **day's** slot. Build a
`workoutDaysByDate: Map<String, WorkoutDay>` and `slotIds = slots.map { it.id }.toSet()`, then
resolve each set's bucket in priority order:

```kotlin
const val UNASSIGNED_SLOT_ID = -1L   // no slot metadata at all (never structured)
const val DELETED_SLOT_ID    = -2L   // resolved to a slot that no longer exists

val day = workoutDaysByDate[set.date]
val resolved: Long? = day?.cycleSlotId
    ?: day?.cycleSlotType?.let { slots.getOrNull(it)?.id }   // legacy rows: type is an index
val bucketId = when {
    resolved == null    -> UNASSIGNED_SLOT_ID
    resolved in slotIds -> resolved
    else                -> DELETED_SLOT_ID
}
```

This covers four real cases:
- **No `WorkoutDay` row** for the date → **Unassigned**. A `WorkoutDay` is only created by loading
  a split (`assignCycleSlot`); free-logging (`SetRepository.addSet`) and agent `LogSet` insert a
  `WorkoutSet` with **no** day row, so the map misses the date. Null-safe `map[date]?…` (not `!!`)
  keeps this from crashing.
- **`WorkoutDay` with both `cycleSlotId` and `cycleSlotType` null** → **Unassigned**.
- **Legacy structured day** (`cycleSlotId` null but `cycleSlotType` set) → map the type *index*
  back to a slot via `slots.getOrNull(type)?.id`, mirroring the app's own `resolveSlotType` /
  `getLatestAssignedDayBefore` convention (`WorkoutRepository.kt`). `assignCycleSlot` writes
  `cycleSlotType = indexOfFirst { it.id == slotId }`, and pre-`cycleSlotId` migration rows
  (`Migrations.kt`) have only the type — without this fallback every such day would be misfiled as
  Unassigned despite being structured work. (Caveat: the index is positional against the *current*
  slot order — the same ambiguity the live app already accepts after a reorder.)
- **Orphaned slot** (`resolved` points at a slot deleted since) → a frozen **"Deleted split"**
  bucket (`slotId = -2`). `deleteCycleSlot` removes only the `cycle_slot` row and `workout_day`
  has **no FK**, so the id dangles. This was structured work missing only its metadata, so keep it
  **distinct from Unassigned** (never-structured) rather than silently merging the two.

Both synthetic buckets sort after real splits (Deleted split, then Unassigned last). They make
the per-split sections **partition** all counted work, so per-split numbers reconcile exactly with
the cycle totals (totals = sum over every bucket). Each synthetic bucket is emitted only when it
holds ≥1 completed set.

> **Implication for free-loggers:** a user who never loads splits has **every** day in Unassigned
> and `splitCount = 0` (§5.5). The detail/list UI must render that gracefully (§7.4/§7.5) —
> Unassigned is the *primary* content then, not a muted footnote.

For each bucket (each `CycleSlot`, plus the synthetic Deleted split / Unassigned):
- `usageCount` = count of distinct in-window days in this bucket that have ≥1 completed set.
- `firstUsedDate` / `lastUsedDate` = min/max of those days' dates.
- `exercises` = exercises **actually performed** (completed) on those days, ordered by the
  saved `SplitExercise.orderIndex` when present, else first-seen. Reflects what they *did*,
  not the saved template. Per-exercise `sessions[]` / endpoints are scoped to this bucket's
  days (an exercise done in two splits appears once per split with that split's progress).
- A real split with no completed work in the window is still emitted (usageCount 0, empty
  exercises) so the user sees it existed; the synthetic Deleted split / Unassigned buckets are
  omitted entirely when empty.

### 5.5 Count definitions & edge cases

**Count definitions** (so list cards never show a surprising number):
- `usageCount` (per split) = distinct in-window days bucketed to that slot **with ≥1 completed
  set**. Days with only planned/incomplete sets don't count.
- `totalSessions` (cycle) = distinct in-window days with ≥1 completed set, across **all** buckets
  **including Unassigned** (so it reconciles with the raw data, not just structured days).
- `splitCount` (cycle) = number of **user-defined splits actually trained** = count of
  `SplitSnapshot` buckets with `usageCount > 0`, **excluding both synthetic buckets** (Deleted
  split, slotId -2; and Unassigned, slotId -1) and excluding zero-usage splits emitted only for
  visibility. A pure free-logger therefore has `splitCount = 0` (everything is Unassigned); the
  card must handle 0 (e.g. label "Unstructured" / show sessions+volume only) rather than render
  "0 splits" awkwardly.

**Edge cases:**
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
- Validates `start <= end` (hard block); warns on empty range.
- **Overlap warning (soft, non-blocking):** whenever start/end change, call
  `archivedCycleDao.countOverlapping(start, end)`. If `> 0`, show an inline caution under the
  date pickers — e.g. *"These dates overlap an existing archive. Sessions in the overlap will be
  counted in both cycles."* — but still allow Confirm. Rationale: the user defines their own
  cycle boundaries (a core design principle), and snapshots are independent frozen copies, so an
  intentional overlap corrupts nothing; we surface the consequence rather than forbid it.
  (Switching to a hard block is a one-line change — gate Confirm on `overlapCount == 0` — if the
  product later prefers strictness.)

### 7.4 Archive list (Archive view)

A `LazyColumn` of past cycles (newest first), each a card: name, date range, span, #
sessions, total volume, split count, and a small overall trend hint. Tap → detail screen.
Long-press or overflow → delete (with confirm). Reuses existing card styling (`Card`,
`AccentTeal`, `MiniSparkline`). When `splitCount == 0` (free-logger, all Unassigned), suppress
the "0 splits" chip and show sessions + volume only (optionally an "Unstructured" tag) so the
card never reads "0 splits".

### 7.5 Cycle snapshot detail screen (`CycleArchiveDetailScreen`)

New route. Renders the frozen snapshot:
- **Header**: name, date range, span days, total sessions, total volume.
- **Highlights**: biggest gainer / biggest regression (by e1RM delta), "most trained" split.
- **Per-split sections**: split name + `usageCount` + date range, then per-exercise rows in the
  same visual style as the live `ExerciseProgressRow`: name, start→end (e1RM / top weight /
  volume), signed % delta with up/down color (`AccentTeal` / `ErrorRed`), and a `MiniSparkline`
  from `sessions[]`. e1RM leads; tapping a row can expand to show top-weight & volume deltas too.
  The two synthetic buckets, when present, render last and visually muted — **Deleted split**
  (structured work whose split was removed) then **Unassigned** — so totals reconcile and stray
  days stay visible. **Exception:** if a synthetic bucket is the *only* content (free-logger,
  `splitCount == 0`), render it primary/full-strength (drop the muting) so the screen isn't one
  greyed-out block.
- Single-session / no-data exercises render the explanatory label, not a fake 0%.

> **Component reuse note:** the new row is a **new composable over `ExerciseSnapshot`** — the
> existing `ExerciseProgressRow` is `private` in `SplitScreen.kt` and bound to the live
> `SplitExerciseRef`, so it can't be reused directly. `MiniSparkline` *is* reusable but is
> currently `private` in `SplitScreen.kt` (3 in-file callers); **extract it to a shared file**
> (e.g. `ui/navigation/ProgressUiComponents.kt`), make it `internal`, and update those callers —
> see §9.

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
- `ui/navigation/CycleArchiveDetailScreen.kt` (incl. its own `ExerciseSnapshot` row composable)
- `ui/navigation/ProgressUiComponents.kt` (extracted shared `MiniSparkline`) — see §7.5
- `ui/viewmodel/CycleArchiveViewModel.kt`
- Tests: `src/test/java/.../CycleSnapshotBuilderTest.kt` (+ extend agent-patch tests for the
  `LogSet.completed` round-trip)

**Modified**
- `data/Cycle.kt` (+`startDate`, +`name`)
- `data/Migrations.kt` (Migration 13→14), `data/AppDatabase.kt` (version 14, register entity/DAO)
- `data/WorkoutSetDao.kt` (+`getSetsInRange`)
- `data/WorkoutRepository.kt` (transactional archive + observe/get/delete + active-start helpers; **fix `saveCycle` to `copy(...)`**)
- `data/DataBackupManager.kt` (+`archivedCycles` in payload/build/restore/clear/toResult, bump `APP_DB_VERSION` → 14) — see §6.1
- `agent/model/DbPatch.kt` (+`completed: Boolean = true` on `LogSet`) — see §5.2 write-path fix
- `agent/patches/PatchService.kt` (pass `completed = patch.completed` in the `LogSet` insert) — see §5.2
- `agent/patches/InverseComputer.kt` (DeleteSet→LogSet inverse: set `completed = current.completed`) — see §5.2
- `ui/navigation/CycleSplitScreenV2.kt` (Current/Archive toggle, archive action, header cleanup)
- `ui/navigation/SplitScreen.kt` (pass-through wiring for archive nav/actions; **extract `MiniSparkline` to the shared file and update its 3 callers**)
- `ui/navigation/AppNavigation.kt` (archive detail route)
- `ui/viewmodel/SplitViewModel.kt` (archive action + active-cycle state, if not in new VM)

## 10. Testing

**Unit (JVM, `src/test`) on `CycleSnapshotBuilder`:**
- Windowing cutoff: a set one day after `endDate` is excluded from end values & totals.
- **Completed filter:** incomplete (`completed = false`) sets are excluded from volume, e1RM,
  session counts, and exercise lists — feed a mix and assert only completed work counts.
- **Units (tenths → true pounds):** a set with `weightLbs = 1850` (= 185.0 lb) × 5 reps yields
  `volumeLbs == 925` (not 9250), `topWeight == 185.0f`, `e1rm ≈ 185 × (1 + 5/30)`. A `1875`
  (187.5 lb) set keeps the `.5` in `topWeight` — guards against the tenths bug.
- e1RM: 185×5 → 185×8 reports positive delta; top-weight unchanged.
- `usageCount` / `firstUsedDate` / `lastUsedDate` correct across multiple slot days, counting
  only days with ≥1 completed set.
- **Unassigned reconciliation — both cases:** (a) a set whose date has **no `WorkoutDay` row**
  in the input, and (b) a set on a `WorkoutDay` with `cycleSlotId = null` and `cycleSlotType =
  null`, both land in the Unassigned bucket (`slotId = -1`); sum of all buckets' volume/sets ==
  cycle totals. Use a null-safe map lookup so case (a) doesn't throw.
- **Legacy `cycleSlotType` bucketing:** a `WorkoutDay` with `cycleSlotId = null` but
  `cycleSlotType = 1` (and ≥2 slots provided) buckets into `slots[1]`, **not** Unassigned.
- **Orphaned slot → Deleted split:** a `WorkoutDay` whose resolved slot id is **not** in
  `slots` lands in the Deleted-split bucket (`slotId = -2`), kept distinct from Unassigned;
  a never-structured set in the same input still lands in Unassigned (`-1`).
- **`splitCount` definition:** with 2 trained splits + 1 zero-usage split + Deleted-split +
  Unassigned work, `splitCount == 2` (excludes the empty split and **both** synthetic buckets).
  An all-free-logger input → `splitCount == 0`, totals still non-zero.
- Single-session exercise → start == end, delta neutral.
- Bodyweight exercise → no crash, flagged, zero-weight handling.
- Empty cycle → zeroed totals, no crash.
- Exercises ordered by saved `SplitExercise.orderIndex` when present.

**Unit (JVM, `src/test`) on the agent patch layer** (reuses `noOpTransactionRunner`):
- Applying a `DbPatch.LogSet` inserts a `WorkoutSet` with `completed = true` (default), so
  agent-logged sets are counted by the archive's completed filter. Passing `completed = false`
  explicitly is honored.
- **`InverseComputer` round-trip:** deleting a set with `completed = false` then undoing
  (applying the inverse `LogSet`) restores it with `completed = false`, not `true`; deleting a
  `completed = true` set restores `true`. Guards the default-`true` field from corrupting undo.

**Instrumentation (`src/androidTest`):**
- **Migration 13→14**: extend the existing migration coverage
  (`DatabaseHardeningInstrumentedTest`) — open a v13 DB, run `Migration(13,14)`, assert
  `cycle.startDate`/`cycle.name` columns exist (nullable) and `archived_cycle` is created.
- **Backup round-trip**: insert an `ArchivedCycle`, export → wipe → import, assert it
  survives identically (extend the existing backup round-trip test).
- **Overlap query**: insert an archive for `[d1,d2]`, assert `countOverlapping` returns >0 for an
  overlapping range and 0 for an adjacent/disjoint one (boundary-inclusive).

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
- **Agent/imported `LogSet` is fixed at the source** to write `completed = true`, so AI-logged
  work counts (and stops silently missing from today's volume too) — forward-fix, no backfill.
  `InverseComputer`'s DeleteSet→LogSet inverse preserves `completed = current.completed` so undo
  stays faithful.
- Snapshot stores **true pounds** for all weights; `weightLbs` is tenths in the DB, so
  `CycleSnapshotBuilder` is the **single conversion boundary** (`WeightLbs.toLbs`). `topWeight`
  and `e1rm` are `Float` (keep .5); `volumeLbs` is a rounded `Long`. Nothing divides twice.
- Set→split bucketing resolves **`cycleSlotId` → legacy `cycleSlotType` index → orphan check**.
  Two synthetic buckets: **Unassigned** (`-1`, no slot metadata) and **Deleted split** (`-2`,
  resolved to a since-deleted slot — kept distinct because it was structured work). Legacy
  `cycleSlotType` rows map by positional index (mirrors the app's existing convention). Every
  bucket together partitions all counted work, so per-split numbers reconcile with totals.
- `splitCount` = **splits actually trained** (`usageCount > 0`, excluding **both** synthetic
  buckets); a pure free-logger reads `0` and the UI adapts.
- `MiniSparkline` is **extracted to a shared file** (was `private` in `SplitScreen.kt`) for the
  archive screen; the archive's exercise row is a new composable over `ExerciseSnapshot`.
- Overlapping archive date ranges are **warned, not blocked** (user owns their cycle
  boundaries; snapshots are independent). Hard-block is a one-line change if ever wanted.
- Archive insert + active-cycle reset are **atomic** (`db.withTransaction`).
- **`archived_cycle` is wired into `DataBackupManager`** (export/import/clear) — non-optional.
