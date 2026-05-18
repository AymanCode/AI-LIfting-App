# Architecture

EcoLift is a single-module Android app built around Compose screens, ViewModels, Room repositories, and a bounded assistant layer.

## Runtime Flow

1. `MainActivity` launches the Compose app and `AppNavigation`.
2. Navigation exposes the main tabs: Log, Progress, IronMind, and Split. Backups are reachable from Progress.
3. Each screen owns a ViewModel that exposes immutable UI state through StateFlow and receives user actions through explicit methods.
4. ViewModels coordinate repositories and DAOs. Room stores exercises, workout days, workout sets, split and cycle data, pending reviews, audit entries, and agent turn logs.
5. IronMind routes text through `IntentRouter`, grounds requests with `AgentTools`, creates typed `DbPatch` operations, validates them, applies them through `PatchService`, and records audit data for undo.
6. Backup JSON can be loaded into a DuckDB analytics mart for workout metrics and agent telemetry.

## Package Boundaries

- `ui/navigation`: Compose screens and bottom navigation.
- `ui/viewmodel`: UI state models and screen orchestration.
- `data`: Room entities, DAOs, migrations, repositories, backup import/export, fuzzy matching, and weight formatting.
- `agent/router`: Deterministic intent classification and model fallback routing.
- `agent/tools`: Read-side workout queries, recommendations, transfer ratios, and exercise similarity.
- `agent/patches`: Patch validation, inverse patch computation, transactional apply, audit persistence, and undo.
- `agent/engine`: Interfaces and implementations for optional local GenAI.
- `ai`: Legacy and model-integration helpers retained for current app wiring.
- `analytics`: Python and DuckDB loader that models backup exports into fact tables, dimension tables, and data-quality views.

## Data Model

The Room schema is version 13. Exported schemas live in `schemas/com.ayman.ecolift.data.AppDatabase`.

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
