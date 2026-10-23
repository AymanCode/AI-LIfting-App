# Android shims

The ported UI keeps its web package calls. Android implements those calls in the same packages.

## Register the activity

In `MainActivity.onCreate`, after `super.onCreate` and before `setContent`, call both lines.

```kotlin
AndroidHost.install(this)
PhotoPickerBridge.register(this)
```

`PhotoPickerBridge.register` calls `registerForActivityResult`. Call it before the activity reaches the started state. Register again on each new activity instance. A process-wide launcher from the previous instance does not survive recreation.

## Handle a new intent

In `onNewIntent`, store the intent, then refresh the log date.

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    AndroidHost.install(this)
}
```

A second `install` on the same instance only refreshes the `?date=` search. It does not add another destroy observer.

## Provide services, haptics, and fonts

In the composition root, call `loadAppFonts()` before any UI reads the font families. Provide `AndroidAppServices(context)` through `LocalAppServices`. Provide `remember { WebHapticFeedback() }` through `LocalHapticFeedback`.

`loadAppFonts` assigns `QuietUiFontFamily` and the highlight and sheet families from `res/font`. The log, display, poster, and marker families stay as the vals already built in `LogTypography.kt`.

## Import Health Connect sessions

The reader that ships today only reads today's calories. `HealthConnectImport` reads exercise sessions from the last 14 days through that same client. It counts a session only when its Health Connect uid was not already stored. A second run still upserts, so a newer record can fill missing fields, and that run does not count the row again.

Until `android.permission.health.READ_EXERCISE` is granted, `importRecentSessions` returns 0. Do not invent a session from the calorie total.

```kotlin
val importer = HealthConnectImport(
    reader = reader,
    existingHealthConnectUids = { repository.getAll().mapNotNull { it.hcUid }.toSet() },
    upsert = { repository.upsertHealthConnect(it) },
)
val added = importer.importRecentSessions()
```

Request `importer.exerciseSessionReadPermission()` before the import. The manifest entry is `android.permission.health.READ_EXERCISE`. Writer F owns the manifest. The row that calls this import is for the integrator. This change does not add that row.

## What stays a no-op

Shader warm-up returns immediately. Tab transition timing stays at 230 ms and 17 dp, because navigation still reads those constants. `CurrentAppMode` stays `Authenticated`, so `DemoGuard` does not block.
