# Project Summary

EcoLift is a local-first Android workout tracker with an assistant layer called IronMind. The first MVP was built in 2 to 3 days, then the project was expanded around safer assistant behavior, database reliability, local backups, and offline analytics.

The main engineering choices are:

- keep workout data local by default
- route common assistant requests through deterministic Kotlin code before model fallback
- represent assistant writes as typed `DbPatch` values instead of raw database operations
- validate patches before Room transactions
- require confirmation for destructive changes
- record audit rows and inverse patches for undo
- keep unresolved user text as a recoverable draft when parsing is unsafe
- load backup exports into DuckDB for workout and agent telemetry

## Agent Flow

IronMind routes user text through `IntentRouter`, uses `AgentTools` to ground requests against local data, and produces `DbPatch` objects for write operations. The model layer is optional. If deterministic parsing cannot safely resolve a request, the app either falls back to a model or preserves the original input for review.

The assistant supports routine logging, dated imports, historical corrections, destructive changes with confirmation, progress questions, similar-exercise lookup, and weight recommendations.

## Evaluation

The agent evals are split into three prompt banks:

- `ironmind_eval_cases.jsonl`: 200 offline cases for routing, intent accuracy, fallback behavior, patch-field accuracy, and destructive confirmation behavior.
- `ironmind_realistic_prompt_bank.jsonl`: 120 realistic prompts for messy historical logs, dated imports, corrections, destructive requests, ambiguous rows, and read-only questions.
- `ironmind_ai_rescue_cases.jsonl`: 24 hard prompts for live model rescue when deterministic routing leaves a case unresolved.

Recent local runs:

```text
200-case offline eval: 99.5% intent accuracy, 9% fallback rate
120-prompt realistic offline bank: 87.5% deterministic coverage, 12.5% fallback rate
24-prompt live AI rescue eval: 24/24 successful cases, 0 unsafe silent mutations
```

Full reports are written under `build/reports/agent-eval/`.

## Data and Persistence

Room stores exercises, workout days, workout sets, split and cycle data, pending review rows, audit entries, and agent turn logs. The current schema version is 13, with exported schemas under `schemas/`.

Backup export/import includes workout history, split data, pending reviews, audit rows, and agent turns. Import creates a pre-import automatic backup and runs the restore inside a Room transaction.

The DuckDB loader in `analytics/` turns backup JSON into dimensions, fact tables, and views for weekly volume, personal records, adherence, undo rate, agent error rate, and data-quality checks.

## Verification Commands

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
python -m unittest analytics.test_load_backup
```
