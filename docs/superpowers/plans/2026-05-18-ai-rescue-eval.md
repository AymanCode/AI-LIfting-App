# AI Rescue Eval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small opt-in AI eval that measures whether a model can rescue realistic prompts that deterministic parsing intentionally leaves alone.

**Architecture:** Keep the 120-prompt offline eval as the frozen deterministic benchmark. Add a separate hard-case prompt bank and a JVM test harness that runs fixture validation by default, then runs live OpenAI-compatible API calls only when explicitly enabled through environment variables. The live path adapts the provider to `LocalGenAiEngine` so the app's existing `IntentRouter`, `AgentOrchestrator`, and `Prompts` remain the measured surface.

**Tech Stack:** Kotlin/JUnit, `LocalGenAiEngine`, `HttpURLConnection`, JSONL fixtures, Gradle unit tests.

---

### Task 1: Hard-Case Fixture

**Files:**
- Create: `src/test/resources/agent_eval/ironmind_ai_rescue_cases.jsonl`
- Test: `src/test/java/com/ayman/ecolift/agent/AiRescueEvalTest.kt`

- [ ] Add 20 to 25 realistic prompts focused on deterministic fallback or borderline cases.
- [ ] Include categories for hard logs, historical corrections, destructive requests, progress analysis, recommendations, and ambiguous machine logs.
- [ ] Mark expected target labels, confirmation expectations, and whether mutation is expected.
- [ ] Add a default test that validates the fixture shape and confirms the deterministic router does not already solve the hard-case-only subset.

### Task 2: OpenAI-Compatible Eval Engine

**Files:**
- Test: `src/test/java/com/ayman/ecolift/agent/AiRescueEvalTest.kt`

- [ ] Add a test-only `OpenAiCompatibleEvalEngine` implementing `LocalGenAiEngine`.
- [ ] Read `AI_RESCUE_EVAL_BASE_URL`, `AI_RESCUE_EVAL_API_KEY`, and `AI_RESCUE_EVAL_MODEL`.
- [ ] Default base URL to `https://api.groq.com/openai/v1` and model to `llama-3.3-70b-versatile`.
- [ ] Use chat completions with low temperature, JSON object response format, and a system prompt that frames IronMind as a safe workout coach and log editor.
- [ ] Retry `429`, `408`, and `5xx` responses with capped exponential backoff and `Retry-After` support.

### Task 3: Live Eval Report

**Files:**
- Test: `src/test/java/com/ayman/ecolift/agent/AiRescueEvalTest.kt`
- Output: `build/reports/agent-eval/ai-rescue-summary.json`

- [ ] Skip live calls unless `AI_RESCUE_EVAL_ENABLED=true`.
- [ ] Run no more than `AI_RESCUE_EVAL_MAX_CASES`, defaulting to 24.
- [ ] Measure total cases, model calls, API failures, crashes, route counts, target agreement, rescue rate, patch accuracy, destructive confirmation rate, unsafe silent mutation rate, and average latency.
- [ ] Write per-case outcomes without including API keys or provider request bodies.

### Task 4: Prompt and Docs

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/agent/engine/Prompts.kt`
- Modify: `README.md`
- Modify: `docs/TESTING.md`

- [ ] Strengthen log extraction wording so the model behaves like a careful coach/log editor, corrects obvious exercise spelling, converts spoken numbers, and refuses to invent missing weight or reps.
- [ ] Document the default offline test behavior.
- [ ] Document the opt-in live API command and rate-limit-safe environment variables.
- [ ] Clearly separate deterministic offline metrics from live API rescue metrics.
