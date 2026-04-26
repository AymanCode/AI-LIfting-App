# EcoLift

EcoLift is a local-first Android workout tracker built as an AI-engineering portfolio project. The app combines production-style Android architecture with an experimental on-device assistant, IronMind, that can interpret natural-language workout requests, ground them against local data, and convert them into validated database actions.

This project was built with heavy use of AI coding agents. The core workflow was design-document driven: features were specified in enough detail for an agent to implement them, then iterated through build errors, tests, and manual review. The result is both a usable mobile app and a practical case study in agentic software development.

## Why This Project Matters

EcoLift is intended to demonstrate more than prompt usage. It focuses on the engineering patterns needed to make AI-assisted features reliable:

- Deterministic intent routing before model fallback.
- Typed database patch objects instead of free-form model writes.
- Validation and confirmation gates for destructive actions.
- Audit logging and undo for assistant-applied changes.
- Local-first storage with no account or cloud dependency.
- Testable boundaries between UI, data, agent tools, and model engines.
- Documentation that captures architecture and implementation phases for future agent work.

## Product Features

- Fast workout logging with fuzzy exercise search, quick-add suggestions, inline exercise renaming, weight/reps controls, and completed-set tracking.
- Split and cycle management for Push/Pull/Legs-style routines, custom split ordering, and saved exercises per split.
- Progress views with exercise search, trend sparklines, estimated strength metrics, and detailed exercise history.
- Rest timer support that stays available while logging the next set.
- Local backup/export/import flow for workout history and split data.
- IronMind assistant for workout logging, history questions, alternatives, weight recommendations, confirmation-gated edits, and undo.

## IronMind Agent

IronMind is the app's assistant layer. It is designed around a safety-first pipeline:

```text
user text
  -> intent routing
  -> local data grounding
  -> typed DbPatch generation
  -> validation
  -> confirmation gate when destructive
  -> transactional apply
  -> audit log and undo
```

The assistant can still operate without a loaded local model because the routing and tools are deterministic Kotlin code. Local model integrations through MediaPipe GenAI and Android AICore are isolated behind engine interfaces so model-backed behavior can be added or swapped without rewriting the app.

## AI-Assisted Development Approach

The project was implemented through structured design documents and agent-ready implementation phases. Each phase described the target behavior, data contracts, validation rules, tests, and acceptance criteria clearly enough for a coding agent to execute against the existing codebase.

Examples of agent-oriented implementation artifacts:

- `docs/agent/phase_1.md`: typed patch system and validation rules.
- `docs/agent/phase_2.md`: transactional patch application, audit rows, and inverse patches.
- `docs/agent/phase_3.md`: read tools, recommendations, and exercise similarity.
- `docs/agent/phase_4.md`: local GenAI engine abstraction and prompt boundaries.

This mirrors a realistic AI-engineering workflow: define precise system constraints, give the coding agent bounded tasks, verify behavior with tests/builds, and keep model behavior behind explicit interfaces.

## Tech Stack

- Kotlin, Coroutines, StateFlow
- Jetpack Compose, Material 3, Navigation Compose
- Room database with exported schemas and migrations through version 13
- MVVM-style ViewModels with repository/data-access layers
- MediaPipe GenAI and Android AICore integration points
- JUnit, Mockito Kotlin, kotlinx-coroutines-test, AndroidX test dependencies

## App Structure

```text
src/main/java/com/ayman/ecolift
  agent/      IronMind routing, tools, patch validation/application, audit log, local GenAI interfaces
  ai/         Legacy and model-integration helpers
  data/       Room entities, DAOs, repositories, migrations, backups, fuzzy matching
  ui/         Compose entry point, navigation screens, theme
  ui/viewmodel
              Screen state and user-action orchestration
src/test/java/com/ayman/ecolift
  agent/      Intent routing, patching, recommendations, date extraction, orchestrator tests
  data/       Fuzzy matcher tests
  ui/         ViewModel and calculation tests
schemas/      Exported Room schemas
docs/         Architecture, testing, and implementation notes
```

## Development Setup

Requirements:

- Android Studio or Android SDK installed locally
- JDK 17
- Android SDK 35

Local files that should not be committed:

- `local.properties` with your Android SDK path
- `.env` if using local AI tooling
- model files such as `.task`, `.bin`, `.gguf`, or `.litertlm`

Run tests and build:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

On macOS/Linux:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Current Verification

- JVM unit tests cover agent routing, patch validation, patch application boundaries, recommendations, date parsing, fuzzy matching, and progress calculations.
- `assembleDebug` verifies the Android app packages successfully.
- Remaining gaps are documented in [Testing](docs/TESTING.md), including Compose UI tests, Room instrumentation tests, backup import/export tests, and real on-device AI smoke tests.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Testing](docs/TESTING.md)
- [Agent implementation notes](docs/agent/phase_1.md)

## Repository Hygiene

The project intentionally ignores build outputs, IDE files, local settings, generated caches, debug signing artifacts, and large model files. Before publishing a GitHub release or PR, run the unit tests and check `git status --short` for accidental local files.
