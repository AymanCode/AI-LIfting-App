# Testing

EcoLift uses JVM tests for business logic, Android instrumentation tests for Room/database reliability, and Python tests for the DuckDB analytics pipeline.

## Verification Commands

Run the core Android test suite:

```powershell
.\gradlew.bat testDebugUnitTest
```

Build a debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Run database instrumentation tests on an emulator or physical device:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Run the agent eval harness:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.AgentEvalHarnessTest"
Get-Content build\reports\agent-eval\summary.json
```

Run the realistic offline prompt-bank measurement:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.RealisticPromptBankOfflineEvalTest"
Get-Content build\reports\agent-eval\realistic-offline-summary.json
```

Run the AI rescue eval fixture check without network:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.AiRescueEvalTest"
```

Run the opt-in live API rescue eval for hard prompts:

```powershell
$env:AI_RESCUE_EVAL_ENABLED="true"
$env:GROQ_API_KEY="<provider key>"
$env:AI_RESCUE_EVAL_MODEL="llama-3.3-70b-versatile"
$env:AI_RESCUE_EVAL_MAX_CASES="12"
$env:AI_RESCUE_EVAL_CASE_DELAY_MS="5000"
.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.AiRescueEvalTest"
Get-Content build\reports\agent-eval\ai-rescue-summary.json
```

Optional live settings:

- `AI_RESCUE_EVAL_API_KEY` or `GROQ_API_KEY` can provide the provider key from the shell environment. `AI_RESCUE_EVAL_API_KEY` takes precedence.
- `AI_RESCUE_EVAL_BASE_URL` defaults to `https://api.groq.com/openai/v1`.
- `AI_RESCUE_EVAL_MAX_RETRIES` defaults to `3`.
- `AI_RESCUE_EVAL_INITIAL_BACKOFF_MS` defaults to `1500`.
- `AI_RESCUE_EVAL_CASE_DELAY_MS` defaults to `0`; use `5000` to space case starts by five seconds.
- `AI_RESCUE_EVAL_MIN_TARGET_AGREEMENT` defaults to `0.65`.

Provider keys are local-only. Do not commit `local.properties`, do not publish debug APKs built with a provider key, and use a backend proxy for any production cloud model integration. Release builds blank the Groq key field.

Run the DuckDB analytics test:

```powershell
python -m pip install -r analytics\requirements.txt
python -m unittest analytics.test_load_backup
```

## Current Coverage

The verification suite covers:

- Intent routing, model fallback, and rule-matcher edge cases.
- 200-case offline agent eval metrics for intent accuracy, route-source coverage, fallback rate, patch-field accuracy, and destructive confirmation behavior.
- 120-prompt realistic offline prompt bank for messy logs, dated imports, historical corrections, destructive requests, ambiguous rows, and read-only queries. The report records deterministic coverage, fallback rate, target agreement, mutation resolution, recoverable draft rate, destructive safety, category metrics, target metrics, and per-case outcomes without API calls.
- 24-prompt AI rescue eval bank for hard cases that deterministic routing often leaves to model fallback. The default test validates the fixture without network access. The opt-in live run measures model rescue rate, target agreement, mutation resolution, patch type accuracy, patch-field accuracy, destructive safety, rate-limit retries, API failures, and progress-analysis response coverage.
- AI rescue success is stricter than intent classification. A write case passes only when the target action is correct, confirmation behavior is correct, and expected fields such as exercise, date, weight, reps, and set count match the fixture. A read or clarify case passes only when it avoids mutation and returns a usable response instead of a generic unresolved message.
- Date extraction.
- Patch validation and patch service behavior.
- Audit and undo wiring.
- Agent read tools, recommendations, transfer ratios, and exercise similarity.
- Local GenAI interface contracts and prompt helpers.
- Fuzzy exercise matching.
- Log and progress ViewModel calculations.
- Room migrations from exported schema versions 8 through 13.
- Backup and import round-trips for workout, split, audit, and agent-turn data.
- DuckDB analytics loading, table counts, weekly volume, PRs, agent error rate, and data-quality views.

## CI

`.github/workflows/android-ci.yml` runs:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The workflow uploads unit test reports and the debug APK as artifacts. Instrumentation tests are kept as local or emulator-backed verification because they require a connected Android runtime.

## Known Gaps

- Compose UI tests are not yet included.
- Live API rescue evals are intentionally opt-in because they spend provider quota.
- Real on-device AI behavior still needs a smoke test with a local model installed.

## Pre-Push Checklist

1. Run `.\gradlew.bat testDebugUnitTest`.
2. Run `.\gradlew.bat assembleDebug`.
3. Run `.\gradlew.bat connectedDebugAndroidTest` if Room, migration, or backup behavior changed.
4. Run `python -m unittest analytics.test_load_backup` if backup JSON or analytics code changed.
5. Run the live AI rescue eval only when intentionally spending API quota.
6. Confirm Room schema changes are intentional and exported under `schemas/`.
7. Check `git status --short` for local files, model files, caches, or IDE settings.
