# RoomRepos rationale

## Problem

The web UI calls `AppRepos` and expects `StateFlow` lists. Android already stores the older shape in Room 19. The new columns and the three new tables have to survive an upgrade, and a backup exported by that older app has to restore. The non-obvious part is when the UI is allowed to see a write. A Room invalidation can fire before the surrounding transaction commits.

## Usage (caller's view)

The integrator builds the database, builds `RoomRepos`, and waits for `loadAll()` before composition.

```kotlin
val db = AppDatabase.getInstance(context)
val repos = RoomRepos(db = db, scope = lifecycleScope)
lifecycleScope.launch {
    repos.loadAll()
    setContent { EcoLiftApp(repos) }
}
```

A log screen collects `repos.sets.sets` and `repos.sets.cards`. Saving a set calls `repos.sets.updateSet(edited, observed)`. The returned `LocalCommit.Committed` means the row is in the database and the flow already shows it.

Switching a split calls `repos.splitReplacement.apply(...)`. The coordinator calls back into `repos.sets` and `repos.workout`. Those callbacks share one transaction.

## Shape

The core structure is `RoomStore`. It holds the current lists, a staged next copy, and a write depth. Repositories compute the next domain values with the pure helpers (`SetQueries`, `ExerciseMatcher`, `ExerciseNames`) and ask the store to persist a lambda. The lambda talks to DAOs. The store publishes only when depth returns to zero and the transaction has committed.

Validation sits on the way in. `replaceDayBatch` rejects a set whose date does not match, and it rejects duplicate natural keys. `recordDistinctPair` orders the two ids before insert. Inside the store, lists are already domain values.

The public surface is the existing `AppRepos` interfaces. Callers do not see DAOs, entities, or the write depth. That is the depth that matters. The implementation is large because the interfaces are large. Splitting a second cache beside the store would make two writers.

The store deliberately does not queue sync, does not open a second card for one exercise on one day, and does not implement exercise swap.

Mappers are the boundary. Per boundary discipline, SQL rows do not leak out, and the pure helpers do not import Room.

## Synthesis decision

Two shapes were written by this writer. Arena runners were not spawned. The assignment says a skill step that would spawn subagents is lead-only and must be done inline.

The base is the in-memory store that publishes after the transaction. It matches the task sentence for `loadAll()` and it keeps a split replacement atomic.

The rejected shape was one Room `Flow` query per observe method. It hides less. Callers would depend on invalidation timing, and a nested write could show sets before the day row exists. Nothing from that shape was grafted. The pure query helpers from the demo were kept, which both shapes wanted.

Both shapes were screened against the design red flags. The Flow shape leaks the storage schedule (information leakage) and splits ownership between Room and any cache a caller might add. The store shape has one writer. It is not a pass-through. The repository methods change the abstraction from "run this SQL" to "commit this user action".

## Tradeoffs accepted

We accept a full-table load at startup in exchange for reads that match `DemoRepos` and do not depend on Room's observer timing.

We accept an in-memory split journal in exchange for one transaction. A process kill rolls the replacement back instead of resuming a half-written day.

We accept no foreign key from `workout_set.cardId` in exchange for an additive migration.

We accept `NoExerciseSwapRepository` in exchange for staying inside the demo's sync-free surface. The report names that gap.

## Alternatives considered

Room `Flow` queries as the source of the `StateFlow`s lost. Callers would see a write when Room invalidates, which can be before the outer transaction commits. The interface would also expose query timing the web repositories do not have.

A sync queue that records `PendingWrite` and drains it into Room lost. The task says there is no sync queue, and Android is local-first.

## Open questions and risks

Should a later pass replace `NoExerciseSwapRepository` with a local swap that rewrites one day's sets and card? The demo does not. The lead can ask for that if the ported UI needs the button to work.

Writer A's `M2:` commit `68fccc1` moved the entities. `RoomMappers.kt` now copies `syncId`, `cardId`, `isWarmup`, `mergedIntoId`, and the cycle-slot flags. The mappers stayed in `data/room`.

## What landed

Schema 20, `MIGRATION_19_20`, and `RoomRepos` are on this branch. The integrator still constructs `RoomRepos` and awaits `loadAll()` before `setContent`.
