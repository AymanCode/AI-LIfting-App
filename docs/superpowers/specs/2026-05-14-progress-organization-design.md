# Progress Organization Design

## Goal

The Progress tab should stay useful as workout history grows. Instead of showing one long unorganized exercise list, it will offer organization modes that help the user quickly understand progress across exercises and splits.

## Scope

This change adds two organization modes:

- By Progress: default view. Shows all logged exercises ordered by progress trend.
- By Split: shows one split at a time, with left/right navigation and swipe navigation if the current Compose setup supports it cleanly.

Body part organization is intentionally out of scope for this pass. The app has a `muscleGroups` field, but existing values are not reliable enough to make that view useful yet.

## User Experience

The Progress tab opens in By Progress mode. A compact segmented control near the top switches between Progress and Split.

In By Progress mode, the app shows every exercise with progress data, sorted by strongest positive trend first, then neutral trend, then negative trend. Search filters the visible list without changing the selected organization mode.

In By Split mode, the app does not stack split sections vertically. It shows exactly one split screen at a time so the user can understand that split at a glance. The header includes the split name and position, such as "Split 2 of 5". Left and right controls move between splits. Swipe navigation should be added when possible; if the installed Compose version does not provide the pager API cleanly, the first implementation may keep arrow navigation and leave swipe as a focused follow-up.

Exercises that are not assigned to a split remain visible in By Progress mode. They do not appear in By Split mode.

## Data Flow

`ProgressViewModel` will continue to own the Progress tab UI state. It already derives per-exercise progress summaries from workout sets. It will also observe cycle slots and saved split exercise rows so each progress exercise can be associated with zero or more splits.

The view model should expose:

- Selected organization mode.
- Search query.
- Current split index for By Split mode.
- Progress-sorted exercise list.
- Split pages, each containing one split name and that split's progress exercises.

The Compose wrapper will map this state into existing Progress V2 UI models and actions.

## Testing

Add unit tests around pure derivation logic before production changes:

- By Progress sorts positive trends first, then neutral, then negative.
- Search filters the progress list.
- Split pages contain only exercises assigned to each split.
- Split index navigation clamps or wraps consistently at the ends.

Existing progress calculation tests should keep passing.
