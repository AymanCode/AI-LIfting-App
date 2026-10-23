# Architecture

EcoLift is a single-module Android app. The Compose screens are the web app's screens. They read `AppRepos`, and `RoomRepos` stores that state in Room. IronMind handles assistant requests.

## Runtime flow

1. `MainActivity` starts the same Compose tree the web app uses after sign-in. There is no login, invite, OAuth, or set-password route.
2. The tabs are Log, Progress, IronMind, Cardio, and Split.
3. Each screen owns a view model. The view model exposes immutable UI state through StateFlow and receives user actions through explicit methods.
4. Screens read `AppRepos` from `LocalRepos`. `RoomRepos` in `com.ayman.ecolift.android.room` loads Room into memory. It writes each change back inside a transaction before it publishes the new state. There is no account, no sync, and no offline queue. `syncStatus` is the up-to-date value. `cloudStamps` is empty. `syncReview` is empty.
5. IronMind routes text through `IntentRouter` and grounds requests with `AgentTools`. Writes are typed `DbPatch` values. The app validates a patch before `PatchService` applies it. `PatchService` records audit data for undo. `AndroidAppServices.genAiEngine()` lives in `com.ayman.ecolift.android.services`. On a debug build it can call Groq when `GROQ_API_KEY` is set. It can call Gemini Nano when the device has it.
6. Backup and restore stays `DataBackupManager`, opened from the settings sheet. The Health Connect reader stays. Settings gets an import row that calls it.
7. Backup JSON can be loaded into a DuckDB analytics mart for workout metrics and agent telemetry.

## Package boundaries

- `ui/`: the web screens and view models, including the log, progress, the IronMind desk, the split calendar and dial, and cardio. `ui/theme/` is the web theme. Font loading reads `res/font`, and shader warmup does nothing. `QuietDepth` uses a runtime shader on API 33 and above. Older devices get a plain gradient.
- `data/`: web workout models and the calculations the screens call.
- `data/room/`: Room entities, mapped to the web models. `AppDatabase`, the DAOs, `Migrations.kt`, and `DataBackupManager` are the Android persistence layer.
- `agent/`: IronMind routing, tools, patches, audit, and undo.
- `ai/`: exercise classification, and `GroqCloudAgent` for debug Groq calls.
- `cardio/`: cardio fields and parsers. `VisionService` runs ML Kit and `CardioOcrParser`.
- `web/`: `AppRepos`, `LocalRepos`, the repository interfaces, and demo data. Android does not ship sign-in. `CurrentAppMode` is the local signed-in user, so `DemoGuard` never blocks.
- `android/`: `RoomRepos`, `AndroidAppServices`, and the platform shims, under `com.ayman.ecolift.android`.
- `analytics/`: Python and DuckDB loader that models backup exports into fact tables, dimension tables, and data-quality views.

## Data model

The Room schema is version 20. Exported schemas live in `schemas/com.ayman.ecolift.data.AppDatabase`, including `20.json`. Version 20 adds the columns the web models need. It also adds tables for workout exercise cards, exercise aliases, and exercise distinct pairs.

Important entities:

- `Exercise`
- `WorkoutDay`
- `WorkoutSet`
- `Cycle` and `CycleSlot`
- `SplitExercise`
- `TempSessionSwap`
- `PendingReview`
- `AuditEntity`
- `AgentTurnLog`
- `WorkoutExerciseCard`
- `ExerciseAlias`
- `ExerciseDistinctPair`

Weights are stored through `WeightLbs`, which keeps values in tenths of a pound. The `12 -> 13` migration scales older stored weights into the current representation.

Backup exports include workout history, split and cycle data, pending reviews, audit entries, and agent turn logs. Import runs inside a Room transaction after creating a pre-import automatic backup.

## Agent Writes

Agent writes are represented as sealed `DbPatch` values instead of free-form database mutations. This keeps model output away from direct SQL and makes assistant behavior testable.

Safety controls:

- Deterministic routing handles routine requests before model fallback.
- Patch validation runs before any database transaction.
- Destructive actions require explicit confirmation.
- Patch application runs inside Room transactions.
- Audit rows store forward and inverse patches.
- Undo applies the stored inverse patch batch.

## Evaluation and Telemetry

The offline eval harness uses `src/test/resources/agent_eval/ironmind_eval_cases.jsonl` to measure routing source, intent accuracy, fallback behavior, patch-field accuracy, and destructive confirmation behavior without calling a live model. Additional prompt banks cover realistic offline prompts and opt-in live model rescue cases.

The DuckDB pipeline loads backup exports into these tables:

- `dim_exercise`
- `dim_date`
- `fact_workout_set`
- `fact_split_assignment`
- `fact_agent_turn`
- `fact_patch_audit`

Views cover weekly volume, PRs, workout adherence, undo rate, agent error rate, and data-quality issues.
