# Dated Workout Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users paste dated workout notes into IronMind so old workouts can be imported without manually rebuilding every set.

**Architecture:** Add a deterministic parser for dated workout imports and keep it separate from the existing one-exercise parser. The orchestrator will resolve parsed exercise names through `AgentTools`, convert high-confidence entries into `DbPatch.LogSet` batches, and return unresolved lines as pending-review drafts so the UI can preserve user data.

**Tech Stack:** Kotlin, JUnit 4, Mockito Kotlin, Room-backed pending reviews, existing `DbPatch` and `PatchService`.

---

### Task 1: Import Parser

**Files:**
- Create: `src/main/java/com/ayman/ecolift/agent/WorkoutImportTextParser.kt`
- Test: `src/test/java/com/ayman/ecolift/agent/WorkoutImportTextParserTest.kt`

- [ ] Parse date headers like `5/12/26`, `May 3 -`, `yesterday:`, and ISO dates.
- [ ] Inherit the current date for lines under a date header.
- [ ] Parse common note styles: `135x8`, `185 5 5 4`, `50s x 10, 10, 8`, and `135x10x3`.
- [ ] Preserve unclear lines as unresolved import lines.

### Task 2: Agent Import Application

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/agent/AgentTurn.kt`
- Modify: `src/main/java/com/ayman/ecolift/agent/AgentOrchestrator.kt`
- Test: `src/test/java/com/ayman/ecolift/agent/AgentOrchestratorTest.kt`

- [ ] Add an import-applied turn that carries pending-review drafts.
- [ ] Detect dated import text before normal routing.
- [ ] Convert matched entries to `DbPatch.LogSet` with correct date and set numbers.
- [ ] Save unknown or unclear rows as pending-review drafts.

### Task 3: ViewModel Pending Review Persistence

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/OrchestratorViewModel.kt`

- [ ] Persist pending-review drafts returned by the orchestrator.
- [ ] Show a concise chat summary explaining imported sets and review rows.
- [ ] Preserve undo behavior for imported patch batches.

### Task 4: Verification

**Commands:**
- `.\gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.agent.WorkoutImportTextParserTest" --tests "com.ayman.ecolift.agent.AgentOrchestratorTest.*import*"`
- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat assembleDebug`
