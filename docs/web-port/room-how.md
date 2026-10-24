# How the Android Room layer will hold the web repositories

## Overview

The web UI reads workouts, exercises, settings, cardio, and reviews only through `AppRepos`. On the web those interfaces are backed by Supabase and a sync queue. On Android the same interfaces will be backed by Room. `RoomRepos` loads the tables into memory once, and every mutation hits Room inside a transaction before the in-memory lists change.

## Key concepts

`AppRepos` is the bag of repositories the UI already calls. `RoomStore` is the single owner of the in-memory lists and the only place that publishes a `StateFlow`. A Room entity in `com.ayman.ecolift.data.room` is the row. A type in `com.ayman.ecolift.data` is the value the UI holds. Mappers cross that boundary. `SetQueries` decides set numbers and session copies. It does not know about Room.

## How it works

Startup calls `RoomRepos.loadAll()`. That reads every user table inside one transaction and then assigns the flows. The UI collects those flows.

A mutation stages the next lists, writes the changed rows, and assigns the flows only after the outermost transaction commits. A nested call, such as a split replacement writing sets and then the day, joins that transaction and does not publish in the middle. If the write throws, the staged lists are dropped and the flows stay as they were.

`LearnedSplitsCoordinator` is the foundation class with `persist` true. It asks `RoomWorkoutRepository` to write slots and days. `SplitReplacementCoordinator` is also the foundation class. Its journal is memory only, because the set write and the day write commit together.

There is no sync queue. `syncStatus` stays up to date. Assistant changes use the empty stand-in. Exercise swaps are `RoomExerciseSwapRepository`.

## Where things live

`src/main/java/com/ayman/ecolift/data/room` holds the Room entities. DAOs, `AppDatabase`, `Migrations`, and `DataBackupManager` stay in `com.ayman.ecolift.data`. `src/main/java/com/ayman/ecolift/android/room` holds `RoomRepos` and one class per repository interface. `schemas/com.ayman.ecolift.data.AppDatabase/21.json` is the exported schema. `docs/web-port/room.md` is the column and method reference.

## Gotchas

The web unique key on a card is `(user_id, date, exercise_id)`. Android has one user, so the key is `(date, exerciseId)`. The set unique index `(exerciseId, date, setNumber)` stays. A second card for the same exercise on the same day cannot be stored.

`cardId` is text without a foreign key. The migration is additive. The repository inserts the card before the set.

v19 backup JSON has none of the new fields. Decode defaults fill them. Restore still deletes children before parents.

`smartSuggest` is a column here. The web keeps that flag in localStorage. Flipping it replaces the learned-splits coordinator, which reads the flag once.
