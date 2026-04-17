# Phase 1: DbPatch Type System and Validation

## What was built

1. **`DbPatch` sealed interface** (`agent/model/DbPatch.kt`) — five patch types representing all mutations the agent can propose:
   - `LogSet` — log a new set (weighted or bodyweight)
   - `EditSet` — partial update on an existing set
   - `DeleteSet` — remove a set (destructive)
   - `MoveWorkoutDay` — move a workout day to a new date
   - `RenameExercise` — rename an exercise (destructive)

2. **`PatchValidator`** (`agent/patches/PatchValidator.kt`) — stateless business-rule validation:
   - Positive IDs, valid YYYY-MM-DD dates, positive weights/reps
   - Bodyweight consistency (no weight on bodyweight exercises)
   - EditSet must change at least one field
   - MoveWorkoutDay dates must differ
   - RenameExercise name not blank, ≤100 chars
   - `validateAll()` for batch validation (fails fast on first rejection)

3. **Unit tests** — 43 tests total, all passing:
   - `DbPatchTest` (11): serialization round-trips for every patch type, destructive flag coverage, polymorphic list round-trip
   - `PatchValidatorTest` (32): every patch type valid + every rejection path

## Schema adaptations from prompt

The prompt assumed `workoutId: Long` and `weightKg: Double`. Actual schema uses:
- `date: String` (YYYY-MM-DD) instead of workoutId — `WorkoutDay.date` is the PK
- `weightLbs: Int?` instead of weightKg: Double
- No `rpe` field exists on WorkoutSet
- `MoveWorkoutDay` operates on date strings, not numeric IDs

## Intentionally left out

- No Room interaction — that's Phase 2 (PatchService)
- No inverse patch computation — Phase 2
- `PatchValidator` does not check ID existence in DB — PatchService responsibility
- No `completed` field on EditSet — agent shouldn't toggle completion status

## Side fixes

Removed/fixed 7 pre-existing corrupted test files (content wrapped in JSON string literals) and 1 misplaced test directory (`ecolif/` typo). Fixed typo in `FuzzyMatcherTest`.
