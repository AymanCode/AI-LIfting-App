# Testing

EcoLift currently uses local JVM unit tests for most business logic and ViewModel behavior, with Android instrumentation dependencies available for future device/emulator coverage.

## Run Tests

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
```

macOS/Linux:

```bash
./gradlew testDebugUnitTest
```

Build a debug APK:

```powershell
.\gradlew.bat assembleDebug
```

## Current Coverage

The unit test suite covers:

- Agent intent routing and edge cases
- Date extraction
- Patch validation and patch service behavior
- Audit/undo wiring through patch abstractions
- Agent read tools, recommendations, and exercise similarity
- Local GenAI interface contract and prompt helpers
- Fuzzy exercise matching
- Log and progress ViewModel calculations

## Known Gaps

- Room integration tests are not yet running as instrumentation tests.
- Compose UI tests are not yet included.
- Backup import/export would benefit from file-level instrumentation tests.
- Real on-device AI behavior needs device smoke tests with a model installed.

## Suggested Pre-Push Checklist

1. Run `.\gradlew.bat testDebugUnitTest`.
2. Run `.\gradlew.bat assembleDebug`.
3. Confirm Room schema changes are intentional and exported under `schemas/`.
4. Check `git status --short` for local files, model files, caches, or IDE settings.
5. If touching migrations, test upgrade behavior on an emulator or physical device with an older database.
