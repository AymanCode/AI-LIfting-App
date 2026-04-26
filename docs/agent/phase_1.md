# Phase 1: DbPatch Type System and Validation

## What Was Built

`DbPatch` is a sealed interface that represents every database mutation the agent can propose:

- `LogSet`
- `EditSet`
- `DeleteSet`
- `MoveWorkoutDay`
- `RenameExercise`

`PatchValidator` performs stateless business-rule validation before any patch reaches the database:

- Positive IDs
- Valid `YYYY-MM-DD` dates
- Positive weights and reps
- Bodyweight consistency
- At least one changed field for `EditSet`
- Different source and target dates for `MoveWorkoutDay`
- Non-blank exercise names with length limits
- Batch validation through `validateAll()`

## Schema Notes

The implementation follows the app's actual Room schema:

- Workout days are keyed by `date: String`, not by `workoutId`.
- Weights are stored as `weightLbs`, not kilograms.
- `WorkoutSet` does not currently have an RPE field.
- `MoveWorkoutDay` operates on date strings.

## Tests

Unit tests cover serialization, destructive flag behavior, polymorphic lists, valid patches, and rejection paths.

## Deferred Work

- Database existence checks belong in `PatchService`, not `PatchValidator`.
- Room round-trip coverage should be added as instrumentation tests.
