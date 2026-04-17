# Phase 3: Read Tools and Recommendation Engine

## What was built

1. **`AgentTools` interface** (`agent/tools/AgentTools.kt`) — read-side contract with 6 methods: `findExercise`, `getRecentSets`, `getExerciseHistory`, `getSimilarExercises`, `suggestWeight`, `suggestTransferWeight`. Data classes: `ExerciseMatch`, `SetSummary`, `HistorySummary`, `SimilarExercise`, `WeightSuggestion`.

2. **`WeightRecommender`** (`agent/tools/WeightRecommender.kt`) — pure Kotlin, zero DB access, zero model inference:
   - reps ≥ target + 2 → +5 lbs
   - reps < target → -5 lbs (floored at 5)
   - otherwise → hold
   - Confidence: HIGH (≥3 sessions), MEDIUM (1 session), LOW (<1), NO_DATA

3. **`TransferRatios`** (`agent/tools/TransferRatios.kt`) — documented ratio table between movement patterns. Symmetric (looks up reverse if direct not found). Tune over time with real data.

4. **`ExerciseEmbeddingIndex`** (`agent/tools/ExerciseEmbeddingIndex.kt`) — Phase 3 stub using `ExercisePatternMatcher` for similarity. Same-pattern = 1.0, related-pattern via TransferRatios = normalized ratio, unknown = excluded. **Replace with EmbeddingGemma vectors in Phase 4** — interface is stable.

5. **`AgentToolsImpl`** (`agent/tools/AgentToolsImpl.kt`) — wires the above to Room DAOs:
   - `findExercise`: Levenshtein over all exercises, rejects if distance > max(3, queryLen/2)
   - `getRecentSets`: delegates to `getRecentHistoryForExercise`
   - `getExerciseHistory`: `getSetsSince`, computes distinct session count + top set
   - `getSimilarExercises`: loads full catalog, calls `ExerciseEmbeddingIndex.findSimilar`
   - `suggestWeight`: history → `WeightRecommender.suggest`
   - `suggestTransferWeight`: find similar with history → apply similarity score as transfer ratio → round to nearest 5 lbs

## Schema note

Weight is `weightLbs: Int?` (not kg). `WeightRecommender` step size = 5 lbs. Transfer rounding also snaps to nearest 5 lbs.

## Intentionally left out

- `getSimilarExercises(exerciseIdOrName: String, k)` — interface takes `exerciseId: Long` only (string lookup uses `findExercise` first). Simplifies impl.
- Pre-computed embedding cache in Room — deferred to Phase 4 with LiteRT
- Jaro-Winkler — existing `FuzzyMatcher` uses Levenshtein; no reason to add another distance function for Phase 3

## Tests

97 total across all phases, 0 failures:
- `WeightRecommenderTest` (15): increase/decrease/hold/bodyweight/confidence/bounds
- `ExerciseEmbeddingIndexTest` (9): ordering, exclusion, k limit, score range, empty catalog
- `AgentToolsImplTest` (15): all 6 tool methods, fuzzy match, transfer, edge cases
