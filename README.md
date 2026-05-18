# EcoLift

[![Android CI](https://github.com/AymanCode/AI-LIfting-App/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AymanCode/AI-LIfting-App/actions/workflows/android-ci.yml)

EcoLift is a local-first Android workout tracker built with Kotlin, Jetpack Compose, and Room. It supports fast workout logging, dated workout imports, split management, progress views, local backups, and IronMind, an assistant that can interpret natural-language workout requests without giving a model direct database access.

The app is designed around a simple constraint: workout data should remain useful and recoverable even when AI is unavailable or uncertain. Common requests run through deterministic Kotlin paths first. Ambiguous requests can fall back to a model, and unresolved logs are preserved as editable drafts instead of being discarded.

## What It Does

- Log exercises, sets, reps, weights, and rest periods from a mobile-first Compose UI.
- Search exercises with fuzzy matching and keep completed-set history.
- Import older workout logs from plain text, including mixed formats like `135x8`, `135 lb x 8`, and `135 for 8 8 6`.
- Manage split routines, cycle slots, custom ordering, and saved exercises per split.
- Review progress through exercise history, trend summaries, estimated strength metrics, sparklines, detailed set history, and local backup/restore.

## IronMind Assistant

IronMind handles workout requests that are annoying to do manually, especially historical edits and larger text imports. Examples include:

- `yesterday calf raises 90 for 12 10 8`
- `i did Bechh Press 135x7,125x10,.85x5`
- `for last saturday my deadlift top set should say three fifteen for four not 275`
- `delete the extra squat set from May 10`
- `what should I use on incline dumbbell press if bench is 185x5`

The assistant is intentionally bounded. It can classify intent, ground requests against local workout data, and propose typed database patches, but it cannot write arbitrary SQL or mutate Room tables directly.

```text
user text
  -> deterministic intent routing
  -> optional model fallback for ambiguous language
  -> local data grounding
  -> typed DbPatch generation
  -> validation
  -> confirmation gate for destructive actions
  -> transactional Room apply
  -> audit log and undo
```

## Data Safety

Writes flow through `DbPatch`, a sealed set of typed mutation objects for actions such as log set, edit set, delete set, move workout day, and rename exercise. This keeps assistant behavior testable and keeps model output away from direct database writes.

Safety controls include:

- deterministic routing before model fallback
- confidence-gated model extraction for messy logs
- typed patch validation before apply
- destructive-action confirmation
- Room transactions for atomic writes
- audit rows with forward and inverse patches
- one-tap undo through inverse patch replay
- recoverable draft cards when a request cannot be safely parsed

## Agent Evaluation

The agent is tested with JSONL prompt banks instead of relying only on manual demos. The reports are generated locally and are not committed as build artifacts.

- `ironmind_eval_cases.jsonl`: 200 offline cases for routing, intent accuracy, fallback behavior, patch-field accuracy, and destructive safety.
- `ironmind_realistic_prompt_bank.jsonl`: 120 realistic prompts for messy historical logs, dated imports, corrections, destructive requests, ambiguous rows, and read-only questions.
- `ironmind_ai_rescue_cases.jsonl`: 24 hard prompts for live model rescue on cases that deterministic routing is allowed to leave unresolved.

Recent local runs:

- 200-case offline eval: 99.5% intent accuracy and 9% fallback rate.
- 120-prompt realistic offline bank: 87.5% deterministic coverage and 12.5% fallback rate.
- 24-prompt live AI rescue eval: 24/24 successful cases, 100% target agreement, 100% DB-ready mutation rate, 100% model-output parse rate, 0 unsafe silent mutations.

Full reports are written to `build/reports/agent-eval/summary.json`, `build/reports/agent-eval/realistic-offline-summary.json`, and `build/reports/agent-eval/ai-rescue-summary.json`.

The live eval uses capped completions, retry/backoff, and optional spacing between prompts. A live case only passes when the output is usable by the app: write cases must produce expected patch fields, destructive requests must require confirmation, read cases must avoid mutation, and ambiguous cases must preserve the original user text.

## Analytics

The `analytics/` folder contains a DuckDB loader for backup exports. It turns local backup JSON into fact and dimension tables that can be queried outside the app.

Tables:

- `dim_exercise`
- `dim_date`
- `fact_workout_set`
- `fact_split_assignment`
- `fact_agent_turn`
- `fact_patch_audit`

Views cover weekly volume, personal records, workout adherence, undo rate, agent error rate, and data-quality issues.

## Tech Stack

- Kotlin, Coroutines, StateFlow
- Jetpack Compose, Material 3, Navigation Compose
- Room with exported schemas and migrations through version 13
- MVVM-style ViewModels with repository and DAO boundaries
- MediaPipe GenAI and Android AICore integration points
- JUnit, Mockito Kotlin, AndroidX Test, Room migration testing
- Python and DuckDB for local analytics
- GitHub Actions for unit tests and debug APK builds

## Run Locally

Requirements:

- Android Studio or Android SDK
- JDK 17
- Android SDK 35
- Python 3.11 or newer for analytics tests

Local files that should not be committed:

- `local.properties`
- `.env`
- model files such as `.task`, `.bin`, `.gguf`, or `.litertlm`

Run the Android unit tests and build a debug APK:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Run Android instrumentation tests on an emulator or physical device:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Run the offline agent eval:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.AgentEvalHarnessTest"
Get-Content build\reports\agent-eval\summary.json
```

Run the realistic offline prompt bank:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.RealisticPromptBankOfflineEvalTest"
Get-Content build\reports\agent-eval\realistic-offline-summary.json
```

Run the AI rescue fixture check without network:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.AiRescueEvalTest"
```

Run the full 24-prompt live AI rescue eval when intentionally spending API quota:

```powershell
$env:AI_RESCUE_EVAL_ENABLED="true"
$env:GROQ_API_KEY="<provider key>"
$env:AI_RESCUE_EVAL_MODEL="llama-3.3-70b-versatile"
$env:AI_RESCUE_EVAL_MAX_CASES="24"
$env:AI_RESCUE_EVAL_CASE_DELAY_MS="5000"
$env:AI_RESCUE_EVAL_MAX_TOKENS="300"
$env:AI_RESCUE_EVAL_MAX_RETRIES="4"
$env:AI_RESCUE_EVAL_INITIAL_BACKOFF_MS="3000"
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.AiRescueEvalTest"
Get-Content build\reports\agent-eval\ai-rescue-summary.json
```

The live eval uses an OpenAI-compatible chat completions endpoint. `AI_RESCUE_EVAL_API_KEY` or `GROQ_API_KEY` can provide the provider key from the shell environment, with `AI_RESCUE_EVAL_API_KEY` taking precedence. `AI_RESCUE_EVAL_BASE_URL` defaults to `https://api.groq.com/openai/v1`.

Provider keys are for local development and evals only. `local.properties` is ignored, release builds blank the Groq key field, and a production-distributed app should route cloud model calls through a backend proxy rather than shipping a provider key in the client.

Run the DuckDB analytics pipeline:

```powershell
python -m pip install -r analytics\requirements.txt
python analytics\load_backup.py --input analytics\fixtures\sample_backup.json --out build\analytics\sample.duckdb
python -m unittest analytics.test_load_backup
```

## Project Structure

```text
src/main/java/com/ayman/ecolift
  agent/      IronMind routing, tools, patches, audit, undo, and local GenAI interfaces
  ai/         Legacy and model-integration helpers retained for current wiring
  data/       Room entities, DAOs, repositories, migrations, backups, and fuzzy matching
  ui/         Compose entry point, navigation screens, theme, and ViewModels

src/test/java/com/ayman/ecolift
  agent/      Router, orchestrator, patch, recommendation, and eval tests
  data/       Fuzzy matcher tests
  ui/         ViewModel and navigation tests

src/androidTest/java/com/ayman/ecolift
  data/       Room migration and backup round-trip instrumentation tests

analytics/    DuckDB loader, sample backup fixture, and pipeline tests
schemas/      Exported Room schemas
docs/         Architecture, testing, and implementation docs
```

## Documentation

- [Project Summary](docs/PORTFOLIO.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Testing](docs/TESTING.md)
- [Agent implementation notes](docs/agent/phase_1.md)

## Repository Notes

Build outputs, IDE files, local settings, generated caches, debug signing artifacts, Python caches, DuckDB files, and large model files are ignored. Before publishing changes, run the verification commands and check `git status --short` for accidental local artifacts.
