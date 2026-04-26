# Phase 4: Local GenAI Engine

## What Was Built

`LocalGenAiEngine` is the stable interface used by higher-level agent code:

- `isReady`
- `warmup()`
- `streamText(prompt)`
- `generateStructured(prompt, schema)`
- `close()`

`MediaPipeGenAiEngine` implements that interface with `com.google.mediapipe:tasks-genai`. It resolves model files from app storage, warms up on `Dispatchers.IO`, avoids crashing when no model is present, and releases native resources on close.

`GeminiNanoEngine` provides an AICore integration point for supported devices.

`Prompts` centralizes prompt text for intent classification, patch generation, read-result formatting, explanation, and clarification flows.

## Runtime Behavior

The app should continue to work when no local model is available. Deterministic routing, read tools, patch validation, and patch application remain available. A ready model can improve formatting or future model-backed extraction.

## Manual Smoke Test

Use a real device or emulator when validating local model behavior:

- Place a compatible model file in the expected app-accessible model directory.
- Launch the app and open IronMind.
- Confirm `warmup()` does not crash.
- Confirm a simple text prompt returns a non-empty response.
- Confirm structured generation returns JSON-like text for a schema prompt.
- Close/reopen the screen and confirm resources can be reinitialized.

## Deferred Work

- Token-level streaming if the selected runtime supports it.
- Production model delivery and download UX.
- Broader device compatibility testing for MediaPipe and AICore paths.
