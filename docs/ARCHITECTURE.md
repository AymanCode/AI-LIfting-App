# Architecture

EcoLift is a single-module Android app organized around Compose screens, ViewModels, repositories/DAOs, and an agent layer for assistant-driven workflows.

## Runtime Flow

1. `MainActivity` launches the Compose app and `AppNavigation`.
2. Navigation exposes the main tabs: Log, Progress, IronMind, and Split. Backups are reachable from Progress.
3. Each screen owns a ViewModel that exposes immutable UI state through StateFlow and accepts user actions as method calls.
4. ViewModels coordinate repositories and DAOs. Room stores exercises, workout days, sets, split/cycle data, pending reviews, backups, audit entries, and agent turn logs.
5. IronMind routes text through `IntentRouter`, uses `AgentTools` for read-side grounding, creates typed `DbPatch` write operations, validates them, applies them through `PatchService`, and records audit data for undo.

## Key Packages

- `ui/navigation`: Compose screens and bottom navigation.
- `ui/viewmodel`: UI state models and ViewModel orchestration.
- `data`: Room entities, DAOs, migrations, repositories, backup import/export, fuzzy matching, and weight formatting.
- `agent/router`: Deterministic intent classification and rule matching.
- `agent/tools`: Read-side workout queries, recommendations, transfer ratios, and exercise similarity.
- `agent/patches`: Patch validation, inverse patch computation, transactional apply, audit persistence, and undo.
- `agent/engine`: Abstractions and implementations for optional local GenAI.
- `ai`: Legacy/model integration helpers retained for current app wiring and future cleanup.

## Data Model

The Room schema is currently version 13. Exported schemas live in `schemas/com.ayman.ecolift.data.AppDatabase`.

Important entities include:

- `Exercise`
- `WorkoutDay`
- `WorkoutSet`
- `Cycle` and `CycleSlot`
- `SplitExercise`
- `TempSessionSwap`
- `PendingReview`
- `AuditEntity`
- `AgentTurnLog`

Weights are stored through the `WeightLbs` helper. The current migration chain includes a version 12 to 13 conversion that scales existing stored weights into the latest representation.

## Agent Safety Model

Agent writes are represented as sealed `DbPatch` values instead of free-form database mutations. Validation happens before transactions, destructive operations require explicit confirmation, and inverse patches are captured for undo.

This keeps the assistant layer testable and limits the blast radius of natural-language actions.
