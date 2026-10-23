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
- `LogViewModel` waits for the slot and anchor projections as well as the saved board before consuming Split's Start request. An unread anchor list is distinct from an empty one.
- Saved split boards live in `split_member_choice`, separately from learned anchors. Android backups include them; a full repository reload replaces cached board revisions and missing rows after restore. Exercise deletion removes board references in the same transaction.
- `CardioViewModel` reads the Android photo pair as mime then base64. `photo.first` is the mime type and `photo.second` is the base64 payload. The snapshot reads base64 then mime.
- Compose BOM `2025.09.01` resolves `androidx.compose.material3:material3` 1.4.0. `SplitEditorSheet` passes `sheetGesturesEnabled = dragId == null`. Dragging a split row does not dismiss the sheet. Material3 1.3.2, from BOM `2025.08.00`, has no such parameter.
- `ProgressViewModel` takes `derivationDispatcher` and runs both derived flows on it. The default is `Dispatchers.Default`, so the first Progress open does not block the main thread for several seconds. Unit tests pass the `Main` dispatcher they install.
- IronMind settings adds Android backup, restore, local-backup recovery, and Health Connect rows. Restore confirms replacement before importing. Android's retained repository owner keeps the same graph across activity recreation and reports backup failures without closing the app.
- IM-2. IronMind shows the pending action's summary with Apply and Cancel on the desk and in chat. `AiScreen` wires `confirmPending` and `dismissPending` instead of projecting the action as statistics.
- IM-3. `AiScreen` collects applied chat changes and offers Undo through the existing snackbar host. Undo remains session-only and does not offer another Undo after success.
- IM-6. `HubViewModel` reads unresolved saved reviews directly from the repository, independently of session messages. Desk rows offer Resolve and Dismiss after restart, and `RoomPendingReviewRepository` saves the explicitly selected set and resolves its review in one transaction.

## Android divergences

- Progress search text comes directly from the input flow. Chart and filter projections cannot replace a partially typed name with an older value.
- Progress shows a themed spinner and loading label while its initial projection is pending. Held-lift and ticket rows use a compact history summary from the existing session and next-step functions; full reports and the Split caller retain their original report results.
- The latest chart label uses the selected metric's formatter, so a Top set of 102.5 lb keeps its decimal.
- A cardio scan owns its request before the Android picker opens. Cancellation or a later manual draft invalidates late picker and OCR results, and an unreadable image shows the existing failure message on the scan screen.
- Typed cardio fields use their existing limits. Zero time and durations over six hours are invalid; a saved notes-only record does not claim an activity-day fill.
- The Cardio overview sums weekly duration in Long, including older large stored values. Shared domain aggregate types remain unchanged.

## Port a newer snapshot

1. Diff the new snapshot against `57eda2a`.
2. Copy changed Kotlin into the same packages. Leave `com.ayman.ecolift.android`, `AppDatabase`, the DAOs, `Migrations`, `DataBackupManager`, `MainActivity`, and `PermissionsRationaleActivity` in place.
3. Rewrite `Res.drawable` and font resource ids as above. Do not copy web auth, sync, or the service worker.
4. Re-apply the `CardioViewModel` photo-pair read, the `SplitEditorSheet` gesture flag, and the `ProgressViewModel` `derivationDispatcher` argument if the snapshot overwrites those files.
5. Re-apply the Android divergences above, then run ktlint 1.5.0 and the slot Gradle tests.

## Notes

- [Room schema v20 and RoomRepos](web-port/room.md)
- [Android shims](web-port/shims.md)
- [Resources](web-port/resources.md)
- [Tests](web-port/tests.md)
- [How RoomRepos loads](web-port/room-how.md)

## Android divergences

- `PatchService` uses the optional `AtomicPatchRepository` hook on `RoomSetRepository`. A batch shares one Room draft, and its memory-only audit publishes after commit. Delete Undo restores the affected exercise-day group, including row identity and card metadata, and rejects changed groups.
- `AgentOrchestrator` filters explicit correction and delete dates before choosing a set, reads explicit set ordinals, checks model readiness before offering Try AI, and displays the recommender's suggested reps. `LogSetTextParser` expands weight-by-reps-by-count input. Agent input parsing rejects invalid or overflowing loads, and exercise matching requires every query word to fit the selected exercise. Unknown workout notation remains an editable draft.

- Restore generation. `AppRepos.dataGeneration` is optional on the copied interface. Android publishes replacement after successful import, and `OrchestratorViewModel` clears its pending confirmation, chat cards and session Undo before accepting fresh requests. `PatchService` shares the restore lock and rejects actions from a previous generation.

- Agent text parsing. `AgentOrchestrator` removes recognised date phrases (`yesterday`, weekdays, `last saturday`, `May 10`, ISO dates) before it matches an exercise. `AgentToolsImpl.findExercise` matches a whole catalog name inside a query and treats `flat bench press` as bench press, while unknown names still become drafts. `LogSetTextParser` keeps sets in the order they were written when plain and counted notation are mixed. Correction and delete cards name the set that the patch changes. Delete Undo puts a card back through the day's card ordering. `RoomStore.write` publishes memory and the audit even when the caller is cancelled after the commit.
- Crosscut layout. `chromeClearance` pads tab content for the horizontal navigation bar and the display cutout. The same value drops the bottom-bar reserve while the keyboard is open. In landscape, IronMind hides its header and quick actions while the keyboard is open, so the composer and Send stay visible. `isSettingsVisible` is `rememberSaveable`, so rotation leaves the settings sheet open. The Log masthead count and the tab bar keep their old heights at font scale 1. Above that scale the rows grow, and each tab label stays inside its own slot.
- Log's Add a lift field keeps its characters in `ClipboardState`. Search still follows the view model. Android Back closes that open picker. The EST. 1RM headline shrinks, or uses a compact form, when the digits do not fit the column. The muscle and progress chips use a minimum height so large system text is not cut off.
- IronMind's composer in `AiScreen` keeps the same kind of local draft. Sending, a shortcut, recovery text, and a restore write that draft directly.
- GAP-5. An exercise Progress report on All starts at that exercise's earliest chart point. Overview All still starts at the earliest session in the overview list. The web snapshot reads the empty report exercise list and places earlier points at x=0.
- GAP-4. The progress chart reserves the widest right-axis tick label plus 12 dp, and at least 40 dp. Drawing, hit testing, and the point tooltip use that same width. The web snapshot keeps a fixed 40 dp column, so a label such as 1,000,000 can cover the latest marker.
