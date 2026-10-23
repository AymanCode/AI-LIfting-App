# How the Android port is shaped

## Overview

EcoLift on Android stores a lifting log in Room and draws it with Jetpack Compose. The web app is a later Compose Multiplatform client of the same product. This port copies the web UI and the pure Kotlin it depends on, and keeps Room as the only database. Supabase, login, and the sync engine stay out.

## Key Concepts

`AppRepos` in `web/AppRepos.kt` is the bag of repositories the UI reads. `LocalRepos` provides it. Domain values such as `WorkoutSet` and `WorkoutExerciseCard` live in `data/Models.kt`. `DemoRepos` is the in-memory implementation the Android `RoomRepos` copies. Room entities are the storage shape, not the UI shape. `LocalAppServices` supplies `genAiEngine()` and `VisionService`.

## How It Works

The web UI collects state from repository `StateFlow`s. A mutation updates that memory and, on Android, writes the same change through a Room transaction first. `syncStatus` is the up-to-date value because there is no upload queue. Cards are one per exercise per day. The Postgres constraint `workout_exercise_card_user_date_exercise_unique` says so, and the set unique index stays `(exerciseId, date, setNumber)`.

On device, `MainActivity` installs `RoomRepos` and `AndroidAppServices`, then calls the web `AppNavigation`. Browser helpers become Android stand-ins. OCR uses the existing ML Kit engine. IronMind uses the existing Groq and Gemini Nano engines behind the web engine interface.

## Where Things Live

Web snapshot `57eda2a` holds the source under `composeApp/src/wasmJsMain/kotlin/com/ayman/ecolift`. Android code is `src/main/java/com/ayman/ecolift`. Room schema 19 is `schemas/com.ayman.ecolift.data.AppDatabase/19.json`. The new entities will live in `data/room`. New Android-only wiring will live in `com.ayman.ecolift.android`.

## Gotchas

The web set model has `syncId`, `rowRevision`, `cardId`, and `isWarmup`. The Room entity does not, yet. Copying `data/Models.kt` over the Android `WorkoutSet.kt` entity would delete the Room annotations. The entity has to move to `data.room` first.

Web package names stay, including `web.supabase` for interfaces. The Supabase classes under those files are stripped.

The old UI reads Room types directly. It is cheaper to delete it when the web UI is wired than to keep both graphs compiling.
