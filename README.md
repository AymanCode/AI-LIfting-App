# EcoLift

A workout tracking Android app built with Kotlin, Jetpack Compose, and Room.

## What it does

EcoLift lets you log, review, and track your strength training progress:

- **Today** - Start a workout session, log sets (exercise, weight, reps)
- **History** - Browse past workout sessions by date
- **Progress** - View stats like total workouts and best lifts

### Smart exercise matching

Exercise names are fuzzy-matched using the Jaro-Winkler algorithm, so typing "bench pres" still finds "Bench Press". Exercises support aliases for common name variations.

## Architecture

```
src/main/java/com/ayman/ecolift/
├── data/
│   ├── Exercise.kt          # Exercise entity (name, aliases, timestamps)
│   ├── Workout.kt           # Workout session entity (date, start/end time)
│   ├── WorkoutSet.kt        # Individual set (exercise, weight, reps)
│   ├── *Dao.kt              # Room DAOs for each entity
│   ├── *Repository.kt       # Business logic layer
│   ├── FuzzyMatcher.kt      # Jaro-Winkler fuzzy name matching
│   └── AppDatabase.kt       # Room database singleton
└── ui/
    ├── MainActivity.kt       # Entry point, sets up theme and navigation
    ├── navigation/
    │   ├── AppNavigation.kt  # NavHost with today/history/progress routes
    │   ├── TodayScreen.kt    # Current workout logging screen
    │   ├── HistoryScreen.kt  # Past workouts list screen
    │   └── ProgressScreen.kt # Stats and progress screen
    └── theme/
        ├── DarkTheme.kt      # Dark color scheme
        ├── Typography.kt     # Font styles
        └── Shapes.kt         # Corner radius definitions
```

## Tech stack

| Layer | Library |
|-------|---------|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Database | Room (SQLite) with KSP |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle 8.0.2, Kotlin 1.8.21 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

## Building

1. Install [Android Studio](https://developer.android.com/studio) (includes JDK 17 and Android SDK)
2. Open this project folder in Android Studio
3. Let Gradle sync complete
4. Run on an emulator or physical device (API 26+)

## Data model

Three Room entities with foreign key relationships:

- **Exercise** - canonical name + comma-separated aliases
- **Workout** - one per day, tracks session start/end time via epoch day
- **WorkoutSet** - belongs to a workout, links to an exercise, stores weight (lbs), reps, and order

Deleting a workout cascades to delete its sets.

## Development notes

This project was built in a single day using a local LLM (qwen2.5-coder) via [aider](https://aider.chat), with Claude Code as the orchestrator for testing and project structure.
