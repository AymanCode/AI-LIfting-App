# Room schema v20 and RoomRepos

Reference for the integrator. The Android app keeps one local user. There is no sync queue.

## Startup

Construct the graph after `AppDatabase.getInstance`, then await `loadAll()` before the UI collects any flow.

```kotlin
val repos = RoomRepos(db = database, scope = lifecycleScope)
lifecycleScope.launch {
    repos.loadAll()
    setContent { /* LocalRepos provides repos */ }
}
```

`loadAll()` reads every user table inside one Room transaction, then publishes the `StateFlow` values. `close()` cancels the learned-splits scope. `syncStatus` stays `SyncStatus.UpToDate`. `cloudStamps` stays an empty `CloudStamps`. `syncReview` stays `SyncReview.Empty`. `resolveSyncConflict`, `useExistingExercise`, and `retryExerciseCreate` keep the interface defaults.

`assistantChanges` is `NoAssistantChangeRepository`. `exerciseSwaps` is `NoExerciseSwapRepository`. Both match `DemoRepos`. A local exercise swap is not in this pass.

## The set index stays

The web does not allow the same exercise on two cards in one day.

`supabase/migrations/0015_workout_exercise_cards.sql` declares `workout_exercise_card_user_date_exercise_unique` on `(user_id, date, exercise_id)`. `SetRepository.seedExerciseSessionUnlocked` appends one more set when that exercise already has rows today. It does not open a second card. `replaceDayBatchV1` builds `cardsByExercise` with one card per `exerciseId`. Desired sets must have distinct natural keys `(date, exerciseId, setNumber)`.

Android has one user, so the card unique key is `(date, exerciseId)`. The existing unique index `index_workout_set_exerciseId_date_setNumber` stays. Migration 19 to 20 does not drop or rebuild it.

## New columns

Defaults are the values an upgraded v19 row gets. Names follow the table. `user_settings` stays snake case. Every other table stays camel case.

| Table | Column | Default | Why RoomRepos needs it |
| --- | --- | --- | --- |
| exercise | mergedIntoId INTEGER NULL | NULL | `canonicalId` and the visible exercise list. NULL means the row is still selectable. |
| workout_set | syncId TEXT NOT NULL | `''` | Domain identity the log UI copies. New rows get a UUID. |
| workout_set | rowRevision INTEGER NOT NULL | 0 | Bumped on each local update so an edit can see that the row changed. |
| workout_set | cardId TEXT NOT NULL | `''` | Points at `workout_exercise_card.id`. The migration backfill replaces the empty string. |
| workout_set | isWarmup INTEGER NOT NULL | 0 | `isCheckedWorking` ignores warm-up rows. |
| cycle_slot | archivedAt INTEGER NULL | NULL | Slot hidden because its cycle was archived. |
| cycle_slot | learned INTEGER NOT NULL | 0 | Learned-splits engine owns the membership. |
| cycle_slot | createdAt INTEGER NOT NULL | 0 | Epoch millis. 0 means the row predates learned splits. |
| cycle_slot | seedRevision INTEGER NOT NULL | 0 | Bumped when a hand-made member list is edited. |
| cycle_slot | anchorUpdatedAt INTEGER NULL | NULL | Last membership write. |
| cycle_slot | suppressedAt INTEGER NULL | NULL | User retired the split. Distinct from `archivedAt`. |
| cycle_slot | colorIndex INTEGER NOT NULL | 0 | Calendar colour kept for the life of the slot. |
| split_exercise | userAdded INTEGER NOT NULL | 0 | Member the user added on top of a learned list. |
| user_settings | time_zone TEXT NOT NULL | `'UTC'` | The migration writes `ZoneId.systemDefault()` when it adds the column and the row is still the untouched default. A missing settings row uses the device zone on insert. |
| user_settings | unit_system TEXT NOT NULL | `'LB'` | `UnitSystem` name. |
| user_settings | smart_suggest INTEGER NOT NULL | 1 | The web keeps this in localStorage. Android has no localStorage, so the column is the store. |

`cardId` is not a foreign key. Adding one would rebuild `workout_set`, and this migration stays additive. The repository inserts the card before the set in the same transaction.

## New tables

`workout_exercise_card` exists because the log UI reads `SetRepository.cards`, and a set's display mode and position live on the card, not on the set. One row per `(date, exerciseId)`.

| Column | Default |
| --- | --- |
| id TEXT primary key | none, supplied by the writer |
| date TEXT NOT NULL | none |
| exerciseId INTEGER NOT NULL, references exercise(id) on delete cascade | none |
| position INTEGER NOT NULL | none |
| displayMode TEXT NOT NULL | none. Values are `EXPANDED` and `COMPACT`. |
| origin TEXT NOT NULL | none. Values are `LEGACY`, `MANUAL`, `SPLIT`, `IMPORT`, `MCP`. |
| sourcePlanId TEXT NULL | NULL |
| rowRevision INTEGER NOT NULL | 1 |
| createdAt TEXT NOT NULL | `''` |
| updatedAt TEXT NOT NULL | `''` |

Indexes: unique `(date, exerciseId)`, unique `(date, position)`, index on `exerciseId`.

The migration inserts one `LEGACY` card for each distinct `(date, exerciseId)` already in `workout_set`. The id is `legacy-` plus the date plus `-` plus the exercise id. Position is the rank of `exerciseId` among that date's exercises. `displayMode` is `COMPACT` when every set in the group is completed, otherwise `EXPANDED`. It then sets `workout_set.cardId` to that id where `cardId` is still empty.

`exercise_alias` exists because rename and merge record the old name, and predictive match reads aliases.

| Column | Default |
| --- | --- |
| id INTEGER primary key | auto |
| exerciseId INTEGER NOT NULL, references exercise(id) on delete cascade | none |
| alias TEXT NOT NULL | none |
| normalizedKey TEXT NOT NULL | none |

Unique `(normalizedKey)`. One local user, so this matches the web unique `(user_id, normalized_key)`.

`exercise_distinct_pair` exists because "these are different exercises" must survive a later rename. The web keys the pair by id for that reason.

| Column | Default |
| --- | --- |
| id INTEGER primary key | auto |
| exerciseIdA INTEGER NOT NULL, references exercise(id) on delete cascade | none |
| exerciseIdB INTEGER NOT NULL, references exercise(id) on delete cascade | none |

Unique `(exerciseIdA, exerciseIdB)`. The repository orders the ids so `exerciseIdA < exerciseIdB`. Room entities cannot declare a CHECK constraint the exported schema keeps, so the ordering lives in the repository.

## Write session

`RoomRepos` holds one `RoomStore`. The store is the only writer of the in-memory lists. Repositories stage a new list, write the changed rows, and the store publishes after the outermost `withTransaction` returns. A throw discards the staged lists. Nested repository calls join the outer transaction and do not publish early. That is what keeps a split replacement from showing new sets when the day write still fails.

`loadAll()` and `refresh()` replace the lists from a single transaction. They do not interleave with a write. The store mutex is that lock.

Reads after `loadAll()` use the published lists. They do not call a DAO. `SetQueries`, `ExerciseMatcher`, and `ExerciseNames` are the pure helpers. Progress, volume, and history methods are filters over `sets`, the same filters `DemoSetRepository` uses.

## Repository to DAO

Each class implements one web interface and lives in `com.ayman.ecolift.android.room`.

`RoomExerciseRepository` owns exercises, aliases, and distinct pairs.

| Method | DAO work, then publish |
| --- | --- |
| refresh | `exerciseDao.getAll`, `aliasDao.getAll`, `distinctPairDao.getAll` |
| getOrCreate | insert exercise when no normalized name matches a visible or merged row |
| updateName | update exercise, insert alias for the previous name |
| updateMuscleGroups | update exercise |
| deleteExercise | delete exercise. Cascade removes aliases, pairs, split members, and sets. |
| recordAlias | insert alias, ignore when `normalizedKey` already exists |
| recordDistinctPair | insert the ordered pair, ignore when it exists |
| prepareMerge, mergeExercises | one transaction. Keeper stays visible. Loser gets `mergedIntoId`. Sets and cards move to the keeper. A natural-key collision is renumbered with the migration 18 to 19 ranking, lowest set number first and lowest id breaking ties. Split members move, dropping a duplicate `(splitId, exerciseId)`. Alias recorded when the caller passed one. |

`canonicalId`, `mergeGraph`, suggestions, and duplicate candidates read the published lists. No DAO.

`RoomSetRepository` owns sets and cards.

| Method | DAO work, then publish |
| --- | --- |
| refresh | `workoutSetDao.getAll`, `cardDao.getAll` |
| insert, upsertSet | insert or update the set. Create the day's card when missing. |
| update, updateSet | update the set. `rowRevision` increments. |
| delete, deleteSet | delete the set, renumber the survivors on that exercise and date, delete the card when no set remains |
| deleteSets | the same, once, for every id |
| updateCardDisplayMode | update the card mode and increment `rowRevision` |
| addSet | insert the next set number for that exercise and date, copying weight and reps from the requested set, else today's last set, else the previous session |
| addExerciseSession | when today is empty, copy the last completed session. Otherwise add one set. |
| addSetsForExercises | `addExerciseSession` for each id not already on that date |
| replaceDayBatch | replace that date's sets and cards. One card per exercise. Unique natural keys required. |
| cloneDay | copy template sets whose exercise is absent on the target, unchecked, new ids, new card ids |
| updateDate | move sets and cards from one date to the other. Fail if the target already has one of those exercises. |

`RoomWorkoutRepository` owns the cycle, slots, split members, workout days, and archives.

| Method | DAO work, then publish |
| --- | --- |
| refresh | cycle, slots, split exercises, workout days, archived cycles |
| addCycleSlot, renameCycleSlot, deleteCycleSlot | insert, update, or delete `cycle_slot`. Delete cascades split members. |
| appendSplitExercise, saveSplitMembers | replace that slot's `split_exercise` rows |
| saveCycle, setNextSessionType | update `cycle` |
| assignCycleSlot, clearCycleSlot, ensureWorkoutDay | upsert `workout_day` |
| moveWorkoutDay | update the day row and call `sets.updateDate` inside the same transaction |
| archiveCurrentCycle | insert `archived_cycle`, set `archivedAt` on the active slots |
| deleteArchivedCycle | delete one archive |
| upsertLearnedSlot, archiveLearnedSlot, suppressCycleSlot | update the slot columns and replace members when the call includes them |
| enqueueDayBatch, enqueueLearnedDayWrites | apply the day write now. There is no queue. |
| registerSmartSplit | insert the slot, its members, and the day assignments in one transaction |

Progress and archive summary methods read the published sets and days. No extra DAO.

`RoomUserSettingsRepository` owns the single `user_settings` row. Each setter updates that row and the flow. `setSmartSuggest` also replaces the learned-splits coordinator, because the coordinator reads the flag once at construction. The web reloads the page for the same reason.

`RoomCardioRepository` owns `cardio_sessions`. `saveManual` and `saveOcrConfirmed` insert. `update` and `delete` write that row. The flow is the full list. Calorie observation filters it.

`RoomPendingReviewRepository` owns `pending_review`. `add` inserts. `markResolved` sets `resolved`. `unresolved` filters the published list.

`LearnedSplitsCoordinator` is the foundation class, constructed with `persist = true`. It calls `RoomWorkoutRepository` for slot and day writes. `SplitReplacementCoordinator` is the foundation class. Its journal is in memory. `replaceSetDayBatch` and `enqueueDayBatch` run inside one `RoomStore` transaction, so a crash rolls the whole replacement back and `recoverPending` finds nothing to finish.

## Backup

`UserDataBackup` gains three lists, each defaulting to empty:

- `workoutExerciseCards`
- `exerciseAliases`
- `exerciseDistinctPairs`

`formatVersion` stays 1. kotlinx.serialization already uses `ignoreUnknownKeys`. Old v19 JSON has none of the new fields or lists, so decode fills the defaults and restore writes those defaults. New JSON written by v20 round-trips the new columns. Restore still deletes child tables before parents. Card rows are deleted before sets, and alias and pair rows before exercises.

## Version

`APP_DATABASE_VERSION` is 20. `versionCode` is 6. `versionName` is `1.5`. `schemas/.../20.json` is the Room export.
