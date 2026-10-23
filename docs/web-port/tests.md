# Web snapshot test inventory

Reference for the integrator. The tree is `composeApp/src/wasmJsTest/kotlin` in the web snapshot `57eda2a`. A recursive listing of `*.kt` on 2026-10-22 found 242 files. Counts below are that listing.

`testImplementation(kotlin("test"))` is already on the unit-test classpath in `build.gradle.kts`. `kotlinx-datetime` is already an `implementation` dependency. Ported tests keep `kotlin.test` and the same package path under `src/test/java`. The foundation list matches `python docs/web-port/copy_snapshot_tests.py --dry-run --bucket foundation`.

## Foundation, copied after M2

58 files copied into `src/test/java` on the same paths. They exercise `data`, `cardio`, `agent`, or `web/util`, and they do not import `ui`. `EdgeAiTest.kt` is a 59th copied file and is listed under the edge section.

- `com/ayman/ecolift/DayAttributionTest.kt`
- `com/ayman/ecolift/SplitTabMathTest.kt`
- `com/ayman/ecolift/agent/AgentOrchestratorTest.kt`
- `com/ayman/ecolift/agent/AgentParserTest.kt`
- `com/ayman/ecolift/agent/assist/AnalysisValidatorTest.kt`
- `com/ayman/ecolift/agent/assist/AssistServiceTest.kt`
- `com/ayman/ecolift/agent/assist/AssistTestDoubles.kt`
- `com/ayman/ecolift/agent/tools/ExerciseEmbeddingIndexTest.kt`
- `com/ayman/ecolift/agent/tools/WeightRecommenderTest.kt`
- `com/ayman/ecolift/cardio/CardioEntryParserTest.kt`
- `com/ayman/ecolift/cardio/CardioFieldRulesTest.kt`
- `com/ayman/ecolift/cardio/CardioInsightsTest.kt`
- `com/ayman/ecolift/cardio/CardioProgressTest.kt`
- `com/ayman/ecolift/cardio/CardioRecordsTest.kt`
- `com/ayman/ecolift/data/BodyPartRegionMapperTest.kt`
- `com/ayman/ecolift/data/CycleSlotAssignmentTest.kt`
- `com/ayman/ecolift/data/CycleSlotRetirementTest.kt`
- `com/ayman/ecolift/data/ExerciseMatcherTest.kt`
- `com/ayman/ecolift/data/LogicTest.kt`
- `com/ayman/ecolift/data/MonthPaceUtilTest.kt`
- `com/ayman/ecolift/data/MuscleGroupsTest.kt`
- `com/ayman/ecolift/data/MuscleSuggestionTest.kt`
- `com/ayman/ecolift/data/OneRepMaxTest.kt`
- `com/ayman/ecolift/data/SetNumberingTest.kt`
- `com/ayman/ecolift/data/SetSeedingLawTest.kt`
- `com/ayman/ecolift/data/WorkoutDatesTest.kt`
- `com/ayman/ecolift/data/hub/HubItemsTest.kt`
- `com/ayman/ecolift/data/progress/CycleProgressRollupTest.kt`
- `com/ayman/ecolift/data/progress/CycleProgressSmokeTest.kt`
- `com/ayman/ecolift/data/progress/EquivalenceUnitTest.kt`
- `com/ayman/ecolift/data/progress/MonthFiguresTest.kt`
- `com/ayman/ecolift/data/progress/MonthRollupTest.kt`
- `com/ayman/ecolift/data/progress/StepInferenceTest.kt`
- `com/ayman/ecolift/data/progress/VolumeEquivalenceTest.kt`
- `com/ayman/ecolift/data/progress/highlights/HighlightsStackTest.kt`
- `com/ayman/ecolift/data/progress/highlights/ProgressHighlightBuilderTest.kt`
- `com/ayman/ecolift/data/splits/AdaptiveSplitTest.kt`
- `com/ayman/ecolift/data/splits/DayMarksTest.kt`
- `com/ayman/ecolift/data/splits/LearnedArchetypesTest.kt`
- `com/ayman/ecolift/data/splits/LearnedAttributionTest.kt`
- `com/ayman/ecolift/data/splits/LearnedClusteringTest.kt`
- `com/ayman/ecolift/data/splits/LearnedFixtures.kt`
- `com/ayman/ecolift/data/splits/LearnedHistoryTest.kt`
- `com/ayman/ecolift/data/splits/LearnedNamingTest.kt`
- `com/ayman/ecolift/data/splits/LearnedPerformanceTest.kt`
- `com/ayman/ecolift/data/splits/LearnedSlotMatchingTest.kt`
- `com/ayman/ecolift/data/splits/LearnedWindowsTest.kt`
- `com/ayman/ecolift/data/splits/LearnedWorkedExampleTest.kt`
- `com/ayman/ecolift/data/splits/SplitChoicesEngineTest.kt`
- `com/ayman/ecolift/data/splits/SplitMemberChoicesTest.kt`
- `com/ayman/ecolift/data/splits/SplitPatternDetectorTest.kt`
- `com/ayman/ecolift/data/splits/SplitSeedBuilderTest.kt`
- `com/ayman/ecolift/data/splits/SplitSwitchTest.kt`
- `com/ayman/ecolift/data/splits/WorkedExample.kt`
- `com/ayman/ecolift/web/util/ClockLabelTest.kt`
- `com/ayman/ecolift/web/util/DatesTest.kt`
- `com/ayman/ecolift/web/util/NumbersTest.kt`
- `com/ayman/ecolift/web/util/PluralTest.kt`

Two of these paths already exist as Android unit tests. The web file replaces the Android file at the same path, because the web package wins where both trees have a file.

- `com/ayman/ecolift/agent/AgentOrchestratorTest.kt`
- `com/ayman/ecolift/cardio/CardioEntryParserTest.kt`

`SmartSetAdjusterTest.kt` collides under `ui/viewmodel` and waits for writer F.

## UI-coupled, port the view models after writer F

100 files import `ui` or live under `ui`. The second port step copies `ui/viewmodel` only. That directory has 55 files. The other 41 `ui` files are listed so the integrator can see them. They are not in the view-model step.

Four files sit outside `ui` and still import view-model or navigation types. They move with the F port, not with the foundation port.

- `com/ayman/ecolift/YearErasTest.kt`
- `com/ayman/ecolift/data/E1rmConsumerParityTest.kt`
- `com/ayman/ecolift/data/SmartSuggestToggleTest.kt`
- `com/ayman/ecolift/data/Y2E1rmPerfTest.kt`

### View-model files

- `com/ayman/ecolift/ui/viewmodel/ArchiveLinerNotesTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ArchiveWeeklyCacheTest.kt`
- `com/ayman/ecolift/ui/viewmodel/AssistantChangeListMappingTest.kt`
- `com/ayman/ecolift/ui/viewmodel/BufferedScrubBaseTest.kt`
- `com/ayman/ecolift/ui/viewmodel/CardTakenSwapTest.kt`
- `com/ayman/ecolift/ui/viewmodel/CardioViewModelDraftTest.kt`
- `com/ayman/ecolift/ui/viewmodel/CardioViewModelSaveCancellationTest.kt`
- `com/ayman/ecolift/ui/viewmodel/CatalogRefusalViewModelTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ClipboardAddTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ClipboardModelsTest.kt`
- `com/ayman/ecolift/ui/viewmodel/CycleArchiveFailureTest.kt`
- `com/ayman/ecolift/ui/viewmodel/DayContentSignatureTest.kt`
- `com/ayman/ecolift/ui/viewmodel/DayUndoCopyTest.kt`
- `com/ayman/ecolift/ui/viewmodel/DayUndoViewModelTest.kt`
- `com/ayman/ecolift/ui/viewmodel/EmphasisTest.kt`
- `com/ayman/ecolift/ui/viewmodel/EmptyDayLedgerTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ExerciseChangelogTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ExerciseSwapTest.kt`
- `com/ayman/ecolift/ui/viewmodel/HeldLiftsLandingTest.kt`
- `com/ayman/ecolift/ui/viewmodel/HubPolicyTest.kt`
- `com/ayman/ecolift/ui/viewmodel/IronMindDeskTest.kt`
- `com/ayman/ecolift/ui/viewmodel/LearnedTicketTest.kt`
- `com/ayman/ecolift/ui/viewmodel/LearningInputsTest.kt`
- `com/ayman/ecolift/ui/viewmodel/LogSetCarryOverTest.kt`
- `com/ayman/ecolift/ui/viewmodel/LogViewModelLocalCommitTest.kt`
- `com/ayman/ecolift/ui/viewmodel/LogViewModelMutationTest.kt`
- `com/ayman/ecolift/ui/viewmodel/LogViewModelStateTest.kt`
- `com/ayman/ecolift/ui/viewmodel/MonthInsightsTest.kt`
- `com/ayman/ecolift/ui/viewmodel/MonthReachTest.kt`
- `com/ayman/ecolift/ui/viewmodel/MonthSelectionTest.kt`
- `com/ayman/ecolift/ui/viewmodel/MonthSmartSuggestTest.kt`
- `com/ayman/ecolift/ui/viewmodel/MuscleGroupFailureTest.kt`
- `com/ayman/ecolift/ui/viewmodel/MuscleWriteU5VerifyTest.kt`
- `com/ayman/ecolift/ui/viewmodel/PickerRetireTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ProgressGroupWallTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ProgressLifetimeTotalsTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ProgressMonthDeltaTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ProgressSortTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ProgressSubscriptionTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ProgressWindowTest.kt`
- `com/ayman/ecolift/ui/viewmodel/PullUpSetEditIsolationTest.kt`
- `com/ayman/ecolift/ui/viewmodel/QuietProgressMathTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SessionE1rmHistoryTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SetCommitBufferTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SmartSetAdjusterTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SplitBoardViewModelTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SplitGroupCardsTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SplitLiftMetricsTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SplitMonthlyAverageTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SplitRetirementViewModelTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SplitSwitchViewModelTest.kt`
- `com/ayman/ecolift/ui/viewmodel/SyncRowTest.kt`
- `com/ayman/ecolift/ui/viewmodel/ViewModelMutationTestRepos.kt`
- `com/ayman/ecolift/ui/viewmodel/WarmupProgressTest.kt`
- `com/ayman/ecolift/ui/viewmodel/WorkoutCardOrderTest.kt`

### Other ui files, not in the view-model step

- `com/ayman/ecolift/ui/cardio/CardioMachineCardsTest.kt`
- `com/ayman/ecolift/ui/cardio/DawnScrubMathTest.kt`
- `com/ayman/ecolift/ui/hub/HubListStateTest.kt`
- `com/ayman/ecolift/ui/hub/HubRailTest.kt`
- `com/ayman/ecolift/ui/month/MonthSheetDataTest.kt`
- `com/ayman/ecolift/ui/navigation/AlbumRulesTest.kt`
- `com/ayman/ecolift/ui/navigation/AppNavigationHighlightsRouteTest.kt`
- `com/ayman/ecolift/ui/navigation/CompletedWorkoutFinishTest.kt`
- `com/ayman/ecolift/ui/navigation/CompletedWorkoutTicketsTest.kt`
- `com/ayman/ecolift/ui/navigation/CycleSoFarTest.kt`
- `com/ayman/ecolift/ui/navigation/ExerciseReportOriginTest.kt`
- `com/ayman/ecolift/ui/navigation/InlineSplitNameTest.kt`
- `com/ayman/ecolift/ui/navigation/IronMindDeepLinkTest.kt`
- `com/ayman/ecolift/ui/navigation/IronMindMastPartsTest.kt`
- `com/ayman/ecolift/ui/navigation/LogAuditPassTest.kt`
- `com/ayman/ecolift/ui/navigation/LogClipboardTest.kt`
- `com/ayman/ecolift/ui/navigation/LogEmptyDayTest.kt`
- `com/ayman/ecolift/ui/navigation/LogEmptyDayTicketTest.kt`
- `com/ayman/ecolift/ui/navigation/LogRestTabTest.kt`
- `com/ayman/ecolift/ui/navigation/LogScreenInteractionPolicyTest.kt`
- `com/ayman/ecolift/ui/navigation/LogUndoPillTest.kt`
- `com/ayman/ecolift/ui/navigation/NoticeClearanceTest.kt`
- `com/ayman/ecolift/ui/navigation/PendingSetDeletionTest.kt`
- `com/ayman/ecolift/ui/navigation/PosterGrainPixelsTest.kt`
- `com/ayman/ecolift/ui/navigation/ProgressExplainerLinesTest.kt`
- `com/ayman/ecolift/ui/navigation/ProgressPointSelectionTest.kt`
- `com/ayman/ecolift/ui/navigation/ProgressReportPathTest.kt`
- `com/ayman/ecolift/ui/navigation/PullUpLoggingRegressionTest.kt`
- `com/ayman/ecolift/ui/navigation/QuietProgressPickersTest.kt`
- `com/ayman/ecolift/ui/navigation/QuietTabDirectionTest.kt`
- `com/ayman/ecolift/ui/navigation/ResumeLandingTest.kt`
- `com/ayman/ecolift/ui/navigation/RotationLogScreenTest.kt`
- `com/ayman/ecolift/ui/navigation/SmartSplitRouteTest.kt`
- `com/ayman/ecolift/ui/navigation/UnsavedSetStripTest.kt`
- `com/ayman/ecolift/ui/navigation/WinsOnlyChipsTest.kt`
- `com/ayman/ecolift/ui/theme/DawnContrastTest.kt`
- `com/ayman/ecolift/ui/theme/EarnedShineTierTest.kt`
- `com/ayman/ecolift/ui/theme/EspressoDataLineTest.kt`
- `com/ayman/ecolift/ui/theme/ShaderWarmupTimingTest.kt`
- `com/ayman/ecolift/ui/theme/SplitAccentTest.kt`
- `com/ayman/ecolift/ui/theme/SyncDotLabelTest.kt`

## Out of scope, not ported

75 files cover Supabase, sync, or the offline engine. 3 files cover browser code. Sync tests live under `web/supabase/sync`. Auth is out of scope with Supabase.

### Supabase, sync, and offline

- `com/ayman/ecolift/web/auth/ConfirmedAccountTest.kt`
- `com/ayman/ecolift/web/auth/InviteEntryTest.kt`
- `com/ayman/ecolift/web/auth/NativeTransportFailureTest.kt`
- `com/ayman/ecolift/web/auth/OAuthConsentContractTest.kt`
- `com/ayman/ecolift/web/auth/OAuthConsentRouteTest.kt`
- `com/ayman/ecolift/web/offline/AccountDrainLockTest.kt`
- `com/ayman/ecolift/web/offline/CatalogWriteDecisionTest.kt`
- `com/ayman/ecolift/web/offline/CrossTabDeleteTest.kt`
- `com/ayman/ecolift/web/offline/DurableLogTest.kt`
- `com/ayman/ecolift/web/offline/ExerciseAssociationTest.kt`
- `com/ayman/ecolift/web/offline/LegacyQueueImporterTest.kt`
- `com/ayman/ecolift/web/offline/OfflineHydrationTest.kt`
- `com/ayman/ecolift/web/offline/OfflineRuntimeTest.kt`
- `com/ayman/ecolift/web/offline/OfflineStoreTest.kt`
- `com/ayman/ecolift/web/offline/OfflineTestSupport.kt`
- `com/ayman/ecolift/web/offline/OfflineUploadTest.kt`
- `com/ayman/ecolift/web/offline/ReviewCorrectionTest.kt`
- `com/ayman/ecolift/web/offline/RuntimeSchedulingTest.kt`
- `com/ayman/ecolift/web/offline/SessionBindingTest.kt`
- `com/ayman/ecolift/web/offline/StaleInputTest.kt`
- `com/ayman/ecolift/web/offline/SupabaseDurableTransportWireTest.kt`
- `com/ayman/ecolift/web/offline/UploadCorrectionTest.kt`
- `com/ayman/ecolift/web/offline/UploadScheduleTest.kt`
- `com/ayman/ecolift/web/offline/UploadTestSupport.kt`
- `com/ayman/ecolift/web/supabase/AccountBoundRefreshTest.kt`
- `com/ayman/ecolift/web/supabase/CardioRepositoryTest.kt`
- `com/ayman/ecolift/web/supabase/CatalogU5VerifyTest.kt`
- `com/ayman/ecolift/web/supabase/CloudHealthTest.kt`
- `com/ayman/ecolift/web/supabase/DayLayerScenarioTest.kt`
- `com/ayman/ecolift/web/supabase/DayRestoreTest.kt`
- `com/ayman/ecolift/web/supabase/DayUpsertBodyTest.kt`
- `com/ayman/ecolift/web/supabase/EssentialLoadTest.kt`
- `com/ayman/ecolift/web/supabase/ExerciseIdentityRepositoryTest.kt`
- `com/ayman/ecolift/web/supabase/ExerciseSwapRepositoryTest.kt`
- `com/ayman/ecolift/web/supabase/LearnedDayWritesTest.kt`
- `com/ayman/ecolift/web/supabase/LearnedSlotRowTest.kt`
- `com/ayman/ecolift/web/supabase/LearnedSplitsCoordinatorTest.kt`
- `com/ayman/ecolift/web/supabase/OnlineWriteBindingTest.kt`
- `com/ayman/ecolift/web/supabase/OnlineWriteU5VerifyTest.kt`
- `com/ayman/ecolift/web/supabase/PagedSelectTest.kt`
- `com/ayman/ecolift/web/supabase/QueueAccountSnapshotReadTest.kt`
- `com/ayman/ecolift/web/supabase/SetBatchDeleteTest.kt`
- `com/ayman/ecolift/web/supabase/SetBatchInsertTest.kt`
- `com/ayman/ecolift/web/supabase/SetLayerConsistencyTest.kt`
- `com/ayman/ecolift/web/supabase/SetLayerInvariantTest.kt`
- `com/ayman/ecolift/web/supabase/SetLayerOpenMutationTest.kt`
- `com/ayman/ecolift/web/supabase/SetLayerScenarioTest.kt`
- `com/ayman/ecolift/web/supabase/SetLayerSupportTest.kt`
- `com/ayman/ecolift/web/supabase/SetLayerTestSupport.kt`
- `com/ayman/ecolift/web/supabase/SetRepositoryLocalCommitTest.kt`
- `com/ayman/ecolift/web/supabase/SetSeedingScenarioTest.kt`
- `com/ayman/ecolift/web/supabase/SmartSplitRegistrationTest.kt`
- `com/ayman/ecolift/web/supabase/SplitChoiceBookTest.kt`
- `com/ayman/ecolift/web/supabase/SplitReplacementCoordinatorTest.kt`
- `com/ayman/ecolift/web/supabase/SupaCallTest.kt`
- `com/ayman/ecolift/web/supabase/sync/CardioPendingWriteTest.kt`
- `com/ayman/ecolift/web/supabase/sync/CardioSyncQueueTest.kt`
- `com/ayman/ecolift/web/supabase/sync/DayEnsureIfAbsentTest.kt`
- `com/ayman/ecolift/web/supabase/sync/DaySyncQueueTest.kt`
- `com/ayman/ecolift/web/supabase/sync/PendingWriteTest.kt`
- `com/ayman/ecolift/web/supabase/sync/RefreshCoalescerTest.kt`
- `com/ayman/ecolift/web/supabase/sync/RefreshSchedulerTest.kt`
- `com/ayman/ecolift/web/supabase/sync/SplitReplacementJournalTest.kt`
- `com/ayman/ecolift/web/supabase/sync/SyncEngineCardioTest.kt`
- `com/ayman/ecolift/web/supabase/sync/SyncEngineTest.kt`
- `com/ayman/ecolift/web/supabase/sync/SyncMathTest.kt`
- `com/ayman/ecolift/web/supabase/sync/SyncQueueTest.kt`
- `com/ayman/ecolift/web/supabase/sync/SyncStatusReducerTest.kt`
- `com/ayman/ecolift/web/supabase/sync/WorkoutCardMutationProtocolTest.kt`
- `com/ayman/ecolift/web/supabase/sync/WorkoutCardMutationQueueTest.kt`
- `com/ayman/ecolift/web/supabase/sync/WorkoutCardSplitOriginMutationTest.kt`
- `com/ayman/ecolift/web/supabase/sync/WorkoutEchoProofTest.kt`
- `com/ayman/ecolift/web/supabase/sync/WorkoutMutationQueueTest.kt`
- `com/ayman/ecolift/web/supabase/sync/WorkoutSetMutationProtocolTest.kt`
- `com/ayman/ecolift/web/supabase/sync/WriteFailureTest.kt`

### Browser

- `com/ayman/ecolift/web/AppModeTest.kt`
- `com/ayman/ecolift/web/DiagnosticsTest.kt`
- `com/ayman/ecolift/web/LogDeepLinkTest.kt`

## Demo tests

Writer F owns `web/demo`. These 5 files are not in the view-model step.

- `com/ayman/ecolift/web/demo/DemoAppServicesTest.kt`
- `com/ayman/ecolift/web/demo/DemoDataTest.kt`
- `com/ayman/ecolift/web/demo/DemoGuardViewModelTest.kt`
- `com/ayman/ecolift/web/demo/DemoHighlightsTest.kt`
- `com/ayman/ecolift/web/demo/DemoReposTest.kt`

## Edge parsers

`com/ayman/ecolift/web/ai/EdgeAiTest.kt` is copied. `EdgeAi.parseChatText` and `EdgeAi.parseVisionStats` are in the M2 tree. The Supabase chat and vision calls stay on the web client, and this test only covers the parsers.

## Eval tests already on Android

These three tests stay. The numbers in this section are the current pass checks.

`AgentEvalHarnessTest` reads `src/test/resources/agent_eval/ironmind_eval_cases.jsonl`. It requires 200 cases. It builds `IntentRouter` around a `LocalGenAiEngine` stand-in, calls `router.route`, and for cases with `runAgent` calls `AgentOrchestrator.process`. The checks are crashes equal to 0, intent accuracy at least 0.90, rule coverage at least 0.80, destructive confirmation accuracy equal to 1.0, and patch field accuracy at least 0.85.

`RealisticPromptBankOfflineEvalTest` reads `agent_eval/ironmind_realistic_prompt_bank.jsonl`. It requires 120 prompts, 0 model calls, 0 API calls, and 0 crashes. For each prompt it calls `PendingReviewCleanupParser.extractExerciseName`, `WorkoutImportTextParser.parse`, `WorkoutImportTextParser.looksLikeImport`, `IntentRouter.route` with model fallback off, and `AgentOrchestrator.process` with model fallback off. The test requires the rate fields to be present. It does not set a numeric floor on those rates.

`OfflineDeterministicDevSetTest` calls `RuleMatcher.match`, `PendingReviewCleanupParser.extractExerciseName`, `AgentRecoveryGuidance.looksLikeWorkoutLog`, and `LogSetTextParser.parseOneExercise`. Each case asserts one literal route, patch type, exercise name, or parsed set list.

The JSONL banks are `ironmind_eval_cases.jsonl`, `ironmind_realistic_prompt_bank.jsonl`, and `ironmind_ai_rescue_cases.jsonl`. The third bank belongs to `AiRescueEvalTest`, which is outside the three named evals.

## Android unit tests after M2

Same-path web files replaced the Android tests. Nothing else was deleted. UI unit tests stay until writer F deletes the old UI subjects. `GroqCloudAgentTest` and `cardio/ocr/CardioOcrParserTest` cover Android-only engines and stay.

Replaced in place by the web snapshot:

- `src/test/java/com/ayman/ecolift/agent/AgentOrchestratorTest.kt`
- `src/test/java/com/ayman/ecolift/cardio/CardioEntryParserTest.kt`

Adapted:

- `src/test/java/com/ayman/ecolift/data/CycleSlotRetirementTest.kt` drops `row_mapping_preserves_retirement_state`. That test constructed `CycleSlotRow`, and the Room row type was not ported. The three domain tests stay. They call `retireCycleSlots`, `activeCycleSlots`, `forActiveCycleSlots`, `nextActiveCycleSlotOrder`, and `withActiveCycleSlotCount`.

Kept as a second file, because the package differs:

- `src/test/java/com/ayman/ecolift/agent/ExerciseEmbeddingIndexTest.kt` beside `agent/tools/ExerciseEmbeddingIndexTest.kt`
- `src/test/java/com/ayman/ecolift/agent/WeightRecommenderTest.kt` beside `agent/tools/WeightRecommenderTest.kt`

Writer A had already edited these Android tests onto the M2 types. They stay.

- `AgentRecoveryGuidanceTest.kt`
- `AgentToolsImplTest.kt`
- `DateExtractorTest.kt`
- `PatchServiceTest.kt`
- `WeightRecommenderTest.kt`
- `ai/ExerciseMuscleClassifierTest.kt`
- `data/CycleSnapshotBuilderTest.kt`
- `data/progress/CycleProgressCalculatorTest.kt`

ktlint import order was applied to `AgentToolsImplTest.kt` and `PatchServiceTest.kt`.

## Ktlint

ktlint 1.5.0 is at `C:/Users/ayman/Documents/ecolift-android-wt/tools/ktlint`. SHA-256 `a16be01dcc480aab2f55f444b620142152f66e31564b3b9376506d624c28a2ad`. The check is `java -jar` over `src/**/*.kt` after `-F` on `src/test/**/*.kt`.

Disabled in `.editorconfig` because they fight the port across many files:

- `no-multi-spaces` aligns `when` arrows in a column.
- `parameter-list-wrapping` keeps a parameter on the opening line, same choice as `argument-list-wrapping`.
- `spacing-between-declarations-with-comments` keeps a comment on the declaration it describes.

Remaining violations after that pass. Writer G's `src/test` count is 0. Writer C room code and writer E android shims had none.

| Owner | Hits | Files | Rules |
| --- | --- | --- | --- |
| A foundation `src/main` outside `ui` | 111 | 45 | parameter-list-spacing 31, import-ordering 22, comma-spacing 10, condition-wrapping 8, if-else-bracing 7, annotation 7, no-unused-imports 5, no-blank-line-in-list 4, function-literal 4, filename 3, try-catch-finally-spacing 2, curly-spacing 2, no-consecutive-blank-lines 2, multiline-loop 2, no-blank-line-before-rbrace 1, spacing-between-declarations-with-annotations 1 |
| F `src/main` under `ui` | 7 | 7 | import-ordering 7 |
| G `src/test` | 0 | 0 | none |
