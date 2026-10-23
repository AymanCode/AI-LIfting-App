# Web UI port

This Android tree runs the EcoLift web UI from snapshot `57eda2a` on Room. The web Kotlin was copied in. Android supplies the database, the activity, and the device services.

## Packages

Copied packages keep the names they have on the web. That includes `com.ayman.ecolift.ui`, `com.ayman.ecolift.web`, and the domain types those files already import. New Android-only code goes in `com.ayman.ecolift.android`. When both trees have a file, the web copy wins.

Resource calls change at the copy. `Res.drawable.name` becomes `R.drawable.name`. A font file `Foo-Bar_Baz.ttf` becomes `R.font.foo_bar_baz`.

`ktlint_standard_no-wildcard-imports` is off under `src/main/java/com/ayman/ecolift/ui` and `src/main/java/com/ayman/ecolift/web`. Those files keep the imports from the snapshot, so a later copy stays diffable. `ktlint_standard_filename` is off for the same trees, and for `data/splits/YearEras.kt`, because the snapshot file names do not match ktlint's one-class file names. Other main-source trees stay explicit. Tests already turn the wildcard rule off.

## Android-only

- `com.ayman.ecolift.android.room` holds `RoomRepos` and `RoomStore`.
- `com.ayman.ecolift.android.services` holds `AndroidAppServices`, `HealthConnectImport`, and the on-device engines.
- `com.ayman.ecolift.android.platform` holds `AndroidHost`, the photo-picker registration, and the settings actions.
- `AppDatabase`, the DAOs, `Migrations`, and `DataBackupManager` stay the Android persistence layer.
- The Health Connect reader, ML Kit OCR, `GeminiNanoEngine`, and `GroqCloudAgent` stay.
- `MainActivity` and `PermissionsRationaleActivity` stay.

## Android divergences

- There is no login, no sync, and no offline queue. `CurrentAppMode` stays authenticated, so `DemoGuard` does not block. `syncStatus` stays up to date. `assistantChanges` is `NoAssistantChangeRepository`.
- Exercise swap is local. `exerciseSwaps` is `RoomExerciseSwapRepository`.
- `CardioViewModel` reads the Android photo pair as mime then base64. `photo.first` is the mime type and `photo.second` is the base64 payload. The snapshot reads base64 then mime.
- Compose BOM `2025.09.01` resolves `androidx.compose.material3:material3` 1.4.0. `SplitEditorSheet` passes `sheetGesturesEnabled = dragId == null`. Dragging a split row does not dismiss the sheet. Material3 1.3.2, from BOM `2025.08.00`, has no such parameter.
- `ProgressViewModel` takes `derivationDispatcher` and runs both derived flows on it. The default is `Dispatchers.Default`, so the first Progress open does not block the main thread for several seconds. Unit tests pass the `Main` dispatcher they install.
- IronMind settings adds Android backup, restore, local-backup recovery, and Health Connect rows. Restore confirms replacement before importing. Android's retained repository owner keeps the same graph across activity recreation and reports backup failures without closing the app.
- IM-2. IronMind shows the pending action's summary with Apply and Cancel on the desk and in chat. `AiScreen` wires `confirmPending` and `dismissPending` instead of projecting the action as statistics.
- IM-3. `AiScreen` collects applied chat changes and offers Undo through the existing snackbar host. Undo remains session-only and does not offer another Undo after success.
- IM-6. `HubViewModel` reads unresolved saved reviews directly from the repository, independently of session messages. Desk rows offer Resolve and Dismiss after restart, and `RoomPendingReviewRepository` saves the explicitly selected set and resolves its review in one transaction.

## Port a newer snapshot

1. Diff the new snapshot against `57eda2a`.
2. Copy changed Kotlin into the same packages. Leave `com.ayman.ecolift.android`, `AppDatabase`, the DAOs, `Migrations`, `DataBackupManager`, `MainActivity`, and `PermissionsRationaleActivity` in place.
3. Rewrite `Res.drawable` and font resource ids as above. Do not copy web auth, sync, or the service worker.
4. Re-apply the `CardioViewModel` photo-pair read, the `SplitEditorSheet` gesture flag, and the `ProgressViewModel` `derivationDispatcher` argument if the snapshot overwrites those files.
5. Run ktlint 1.5.0, then the slot Gradle tests.

## Notes

- [Room schema v20 and RoomRepos](web-port/room.md)
- [Android shims](web-port/shims.md)
- [Resources](web-port/resources.md)
- [Tests](web-port/tests.md)
- [How RoomRepos loads](web-port/room-how.md)

## Android divergences

- `PatchService` uses the optional `AtomicPatchRepository` hook on `RoomSetRepository`. A batch shares one Room draft, and its memory-only audit publishes after commit. Delete Undo restores the affected exercise-day group, including row identity and card metadata, and rejects changed groups.
- `AgentOrchestrator` filters explicit correction and delete dates before choosing a set, reads explicit set ordinals, checks model readiness before offering Try AI, and displays the recommender's suggested reps. `LogSetTextParser` expands weight-by-reps-by-count input. Agent input parsing rejects invalid or overflowing loads, and exercise matching requires every query word to fit the selected exercise. Unknown workout notation remains an editable draft.
