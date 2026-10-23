# Port rationale

## Problem

The Android app is the original EcoLift client. The web app copied it and then moved ahead, with a new theme, Log screen, splits, Progress, IronMind, and Cardio. Both clients are Kotlin and Compose. The Android database is Room at schema 19. The web database is Supabase, and the owner does not want that sync stack on the phone.

The non-obvious part is the boundary. The web UI does not talk to tables. It talks to `AppRepos` and to domain types in `data/Models.kt`. Room has to sit behind that boundary, or the port becomes a second UI written against the old entities.

## Usage (caller's view)

`MainActivity` builds one `RoomRepos` and one `AndroidAppServices`, then shows the web `AppNavigation`.

```kotlin
CompositionLocalProvider(
    LocalRepos provides roomRepos,
    LocalAppServices provides androidAppServices,
) {
    AppNavigation()
}
```

A screen reads sets the same way the web demo does.

```kotlin
val sets by LocalRepos.current.sets.observeAll().collectAsState()
```

A save writes Room inside a transaction, then publishes the new in-memory state. There is no sync queue. `syncStatus` stays up to date, `cloudStamps` stays empty, and `syncReview` stays empty.

## Shape

The domain types stay the web types in `com.ayman.ecolift.data`. `WorkoutSet`, `WorkoutExerciseCard`, `ExerciseAlias`, and `ExerciseDistinctPair` live there. Room entities move to `com.ayman.ecolift.data.room` with the same table and column names. A mapper at that boundary turns a row into a domain value and back. Per boundary-discipline, the UI never sees a Room entity.

`RoomRepos` is an `AppRepos`. It copies the in-memory `StateFlow` behavior of `DemoRepos`. Startup loads the tables once. Each mutation commits to Room before the flow emits. Per model-the-domain, the organizing structure is that repository registry plus the domain types, not a new state machine and not the old Android view models.

One card exists per exercise per day. Postgres enforces `unique (user_id, date, exercise_id)` on `workout_exercise_card` in `supabase/migrations/0015_workout_exercise_cards.sql`, and nothing later drops it. The set key stays `(exerciseId, date, setNumber)`, matching `workout_set_user_date_exercise_set_number_idx`. Android has one local user, so the index does not gain a user column or a `cardId` column.

Validation of backup JSON and of OCR text stays at those edges. Domain functions copied from the web stay pure.

The app always runs as the signed-in local user. `DemoGuard` does not block. Auth, login, invites, and OAuth routes are removed. Per make-operations-idempotent, migration 19 to 20 only adds columns with defaults and the new tables, and the debug demo seed runs only when `workout_set` is empty.

## Synthesis decision

The lead locked this shape in the assignment before this writer started. Parallel design exploration was not run here. The assignment says the decisions are closed and that this writer does not spawn other agents. The rejected alternative is recorded below so the choice is still visible.

## Tradeoffs accepted

- We accept a full in-memory copy of the log at startup in exchange for the same `StateFlow` behavior the web UI already uses.
- We accept two types for a set, the domain value and the Room row, in exchange for keeping web call sites unchanged.
- We accept deleting the old screens in the milestone that wires the web UI, in exchange for not maintaining two navigation graphs.
- We accept KSP 2.3.2 with Kotlin 2.2.20. KSP 2.3 is no longer tied to a Kotlin release, and this project already builds with Android Gradle Plugin 9.1.

## Alternatives considered

A second design would keep the Android Room entities as the UI model and rewrite each web screen to those fields. That exposes Room columns, missing web fields, and the old view models to every screen. It hides nothing. It lost because the web UI is already written against `AppRepos` and `Models.kt`. This was the only viable shape because the assignment requires those package names and a Room-backed `AppRepos`.

## Open questions and risks

The lead asked for no product questions to be reopened. Risks to watch while implementing:

- Web files that call browser APIs have to be shimmed or the debug build will not compile.
- Replacing `agent/` can break the existing eval tests until those tests call the web agent API.
- Old backups must still restore after the additive migration.

## Next implementation step

Bump the Android toolchain to Kotlin 2.2.20 and Compose 1.9, and prove the current app still tests and assembles before any web file is copied.
