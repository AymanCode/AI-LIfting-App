# Portfolio Summary

EcoLift should read as more than a workout app. The recruiter-facing story is that I shipped quickly with AI coding agents, then proved engineering depth by adding the harnesses, tests, data protections, and analytics that make AI-generated behavior measurable and safe.

## Recruiter Read

In a short scan, this project should communicate:

- Fast shipping, backed by ownership of specs, module boundaries, and review.
- AI engineering, measured with offline evals instead of demo-only behavior.
- SWE depth, proven through CI, Room migrations, backup tests, typed patches, transactions, audit logs, and undo.
- Data engineering judgment, shown through DuckDB marts and data-quality views over local backup exports.

## Resume-Ready Bullets

Use the first three bullets for a tight project entry. Add the fourth when space allows or when applying to AI, platform, or data-leaning roles.

- Shipped a local-first Android workout MVP in 2 to 3 days by converting architecture specs into agent-executable tasks, module boundaries, and review gates for Kotlin, Room, and Compose.
- Built a 200-case offline agent eval harness to measure intent routing, slot extraction, fallback quality, patch-field accuracy, and destructive-action safety across workout requests.
- Cut routine model calls by routing common workout commands through deterministic Kotlin rules before LLM fallback, reaching 82.5 percent rule-path coverage and a 9 percent fallback rate in the latest 200-case local eval.
- Hardened AI-proposed workout edits with typed `DbPatch` validation, Room transactions, audit logs, one-tap undo, zero-loss migration tests, backup round-trips, and DuckDB telemetry.

## Role Framing

The best framing for this project is **system architect and harness engineer**.

Key responsibilities demonstrated:

- Defined agent boundaries so model output cannot mutate the database directly.
- Designed deterministic routing and fallback behavior for natural-language workout requests.
- Built typed mutation objects, validation gates, audit logs, and undo flows around AI-proposed edits.
- Added automated evals to measure assistant behavior instead of relying on demos.
- Hardened persistence with Room migrations, backup verification, and CI.
- Modeled exported app data in DuckDB for telemetry and data-quality checks.

## Interview Talking Points

- The agent is intentionally hybrid. Rules handle routine requests, model fallback handles ambiguous language, and clarification prevents unsafe guessing.
- `DbPatch` is the safety boundary. It converts AI intent into typed, reviewable operations before Room sees any write.
- The eval harness makes the AI layer measurable. It tracks intent accuracy, fallback rate, route-source coverage, patch-field accuracy, and destructive confirmation behavior.
- The persistence layer is local-first and reliability-focused. Migrations, backups, audit logs, undo, and test coverage support the data-integrity story.
- The DuckDB pipeline shows data engineering judgment without adding unnecessary cloud infrastructure to a local-first app.

## Current Verification Snapshot

Latest local agent eval summary:

```text
offline eval cases: 200
intent accuracy: 99.5%
route-source accuracy: 98.5%
rule-path coverage: 82.5%
fallback rate: 9%
destructive confirmation accuracy: 100%
patch-field accuracy: 98.2%
crashes: 0
```

Latest realistic offline prompt bank:

```text
prompts: 120
deterministic coverage: 87.5%
fallback rate: 12.5%
recoverable draft rate: 100%
safe fallback preservation: 100%
API calls: 0
```

Latest 24-prompt live AI rescue eval:

```text
successful cases: 24/24
target agreement: 100%
DB-ready mutation rate: 100%
model-output parse rate: 100%
unsafe silent mutations: 0
max completion tokens: 300
```

Core verification commands:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
python -m unittest analytics.test_load_backup
```
