# Phase 3: Read Tools and Recommendation Engine

## What Was Built

`AgentTools` defines the read-side contract the assistant uses to ground answers in local workout data:

- `findExercise`
- `getRecentSets`
- `getExerciseHistory`
- `getSimilarExercises`
- `suggestWeight`
- `suggestTransferWeight`

`WeightRecommender` is a pure Kotlin recommendation helper. It looks at recent performance and suggests increasing, decreasing, or holding weight in 5 lb steps, with confidence levels based on available history.

`TransferRatios` documents movement-pattern ratios for estimating a starting point when moving between similar exercises.

`ExerciseEmbeddingIndex` currently uses `ExercisePatternMatcher` as a deterministic similarity implementation. The interface is stable enough to swap in vector embeddings later.

`AgentToolsImpl` connects the tool interface to Room DAOs and recommendation helpers.

## Schema Notes

Weights use `weightLbs`. Recommendation steps and transfer rounding snap to 5 lb increments.

## Tests

Unit tests cover recommendation rules, similarity ordering and filtering, fuzzy matching, transfer suggestions, and the main `AgentToolsImpl` methods.

## Deferred Work

- Pre-computed embedding storage.
- Device-backed tests for larger catalogs and real database queries.
- More personalized recommendation logic using longer-term progression history.
