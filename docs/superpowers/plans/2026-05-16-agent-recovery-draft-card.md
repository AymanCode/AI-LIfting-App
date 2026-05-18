# Agent Recovery Draft Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep IronMind useful when deterministic parsing fails and model inference is unavailable, delayed, or too expensive to spend automatically.

**Architecture:** The agent returns a typed recoverable failure instead of a generic clarification for likely workout logs. The ViewModel renders that failure as a draft card with explicit recovery actions, while normal sends use deterministic routing first and reserve model fallback for a user-triggered retry.

**Tech Stack:** Kotlin, Coroutines, Jetpack Compose, Room pending review storage, JUnit 4, Mockito Kotlin.

---

### Task 1: Agent Recovery Contract

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/agent/AgentTurn.kt`
- Create: `src/main/java/com/ayman/ecolift/agent/AgentRecoveryGuidance.kt`
- Test: `src/test/java/com/ayman/ecolift/agent/AgentRecoveryGuidanceTest.kt`

- [ ] Add `AgentTurn.RecoverableFailure` with original text, reason, suggested template, save date, and `canTryModel`.
- [ ] Add deterministic guidance helpers that identify likely workout-log failures without treating random text as a workout.
- [ ] Test that shorthand and spoken workout logs get draft guidance.
- [ ] Test that random unknown text still gets a normal clarification path.

### Task 2: Budget-Aware Agent Processing

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/agent/router/IntentRouter.kt`
- Modify: `src/main/java/com/ayman/ecolift/agent/AgentOrchestrator.kt`
- Test: `src/test/java/com/ayman/ecolift/agent/AgentOrchestratorTest.kt`

- [ ] Add `allowModelFallback` routing and extraction options.
- [ ] Keep existing default behavior for tests and non-UI callers.
- [ ] Make the UI path call deterministic-first processing.
- [ ] Return `RecoverableFailure` for likely log prompts that cannot be parsed without model help.
- [ ] Preserve explicit model retry by allowing a second call with `allowModelFallback = true`.

### Task 3: ViewModel Recovery Actions

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/AiViewModel.kt`
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/OrchestratorViewModel.kt`

- [ ] Add `AiRecoveryActionUi` to `AiMessageUi`.
- [ ] Push recovery payloads into chat when the agent returns `RecoverableFailure`.
- [ ] Implement `editRecoveryDraft`, `useRecoveryTemplate`, `saveRecoveryForReview`, and `tryRecoveryWithModel`.
- [ ] Insert saved failures into `pending_review` so the user can resolve them later.

### Task 4: Compose Draft Card

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/AiScreen.kt`
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/IronMindScreenV2.kt`

- [ ] Map recovery payloads from `AiUiState` to `IronMindMessage.AiMessage`.
- [ ] Render a compact draft section inside the AI bubble with the original text, suggested template, and actions.
- [ ] Wire `Edit`, `Use template`, `Save`, and `Try AI` actions back to `OrchestratorViewModel`.

### Task 5: Verification

**Commands:**
- `.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.AgentRecoveryGuidanceTest" --tests "com.ayman.ecolift.agent.AgentOrchestratorTest.*recover*"`
- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat assembleDebug`

- [ ] Watch new tests fail before implementation.
- [ ] Run focused tests after implementation.
- [ ] Run the full unit suite and APK build.
- [ ] Summarize what changed, why it exists, and how it supports the engineering story.
