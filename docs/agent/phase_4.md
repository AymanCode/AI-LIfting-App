# Phase 4: Local GenAI Engine

## What was built

1. **`LocalGenAiEngine`** (`agent/engine/LocalGenAiEngine.kt`) — stable interface:
   - `isReady: Boolean`
   - `warmup()` — load weights on app start, not first interaction
   - `streamText(prompt): Flow<String>` — token stream (single-item for MediaPipe)
   - `generateStructured(prompt, schema): String` — JSON output with schema hint
   - `AutoCloseable` — release native resources

2. **`MediaPipeGenAiEngine`** (`agent/engine/MediaPipeGenAiEngine.kt`) — implementation backed by `com.google.mediapipe:tasks-genai:0.10.14`:
   - Model resolution: scans 6 candidate paths in `filesDir` and `externalFilesDir`
   - `warmup()`: runs on `Dispatchers.IO`, logs warning if model missing (doesn't crash)
   - `streamText()`: emits full response as single Flow item (MediaPipe has no true streaming)
   - `generateStructured()`: appends schema to prompt as plain-text instruction
   - `close()`: calls `LlmInference.close()` and nulls the reference

3. **`Prompts`** (`agent/engine/Prompts.kt`) — all prompt templates in one place:
   - `intentClassification` — 9-label enum classification
   - `patchGeneration` — structured JSON extraction with grounded context
   - `explanation` — one-sentence confirmation after patch apply
   - `formatReadResult` — plain-English summary of read tool output
   - `clarify` — generates a follow-up question

## Why not LiteRT-LM

`com.google.ai.edge.litertlm` was not production-stable on Maven Central as of the knowledge cutoff (May 2025). `MediaPipeGenAiEngine` is explicitly marked with a `// TODO: swap to LiteRtLmEngine` comment. The interface layer means the swap requires no changes above `MediaPipeGenAiEngine`.

## Acceptance — manual QA checklist

Unit tests cover the interface contract and all prompt templates (112 tests total, 0 failures). Smoke test requiring a real device:

- [ ] Place `gemma_e2b.task` in `filesDir/models/`
- [ ] Call `warmup()` from `Application.onCreate()` background coroutine
- [ ] `isReady` is `true` before any UI interaction
- [ ] `streamText("Say hello")` emits a non-empty string
- [ ] `generateStructured("Extract intent", "{}")` returns a string containing `{`
- [ ] `close()` doesn't crash; subsequent `warmup()` reinitializes correctly

## Intentionally left out

- Token-level streaming — not possible with MediaPipe 0.10.14
- Play on-device AI delivery — requires Google Play distribution setup, deferred
- Model download UI — out of scope for engine layer
