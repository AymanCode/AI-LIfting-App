# Progress Organization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Organize the Progress tab by progress by default and add a one-split-at-a-time split progress view with arrow and swipe navigation.

**Architecture:** Keep Progress tab state in `ProgressViewModel`, add pure derivation helpers for sorting/filtering/split pages, and render those modes in `ProgressScreenV2.kt`. The existing exercise detail screen remains unchanged.

**Tech Stack:** Kotlin, Android Compose, Material 3, Room flows, JUnit 4.

---

### Task 1: Add Progress Organization Models And Derivation Tests

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/UiModels.kt`
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/ProgressViewModel.kt`
- Test: `src/test/java/com/ayman/ecolift/ui/viewmodel/ProgressCalculationsTest.kt`

- [ ] **Step 1: Write failing tests**

Add tests for the desired pure behavior:

```kotlin
@Test
fun `organizeProgressExercises sorts positives first then neutral then negative`() {
    val exercises = listOf(
        progressExercise(1, "Flat", 0f),
        progressExercise(2, "Falling", -4f),
        progressExercise(3, "Climbing", 12f),
        progressExercise(4, "Small positive", 1f),
    )

    val result = organizeProgressExercises(exercises, "")

    assertEquals(listOf("Climbing", "Small positive", "Flat", "Falling"), result.map { it.name })
}

@Test
fun `organizeProgressExercises filters by search query`() {
    val exercises = listOf(
        progressExercise(1, "Bench Press", 10f),
        progressExercise(2, "Back Squat", 5f),
    )

    val result = organizeProgressExercises(exercises, "bench")

    assertEquals(listOf("Bench Press"), result.map { it.name })
}

@Test
fun `buildProgressSplitPages keeps each split isolated`() {
    val exercises = listOf(
        progressExercise(1, "Bench Press", 10f),
        progressExercise(2, "Back Squat", 5f),
        progressExercise(3, "Unassigned Curl", 20f),
    )
    val splits = listOf(
        ProgressSplitSource(10, "Push", listOf(1)),
        ProgressSplitSource(20, "Legs", listOf(2)),
    )

    val pages = buildProgressSplitPages(exercises, splits, "")

    assertEquals(2, pages.size)
    assertEquals("Push", pages[0].name)
    assertEquals(listOf("Bench Press"), pages[0].exercises.map { it.name })
    assertEquals("Legs", pages[1].name)
    assertEquals(listOf("Back Squat"), pages[1].exercises.map { it.name })
}

@Test
fun `normalizeSplitIndex clamps index to available pages`() {
    assertEquals(0, normalizeProgressSplitIndex(-1, 3))
    assertEquals(1, normalizeProgressSplitIndex(1, 3))
    assertEquals(2, normalizeProgressSplitIndex(7, 3))
    assertEquals(0, normalizeProgressSplitIndex(7, 0))
}

private fun progressExercise(
    id: Long,
    name: String,
    change: Float,
) = ProgressExerciseUi(
    exerciseId = id,
    name = name,
    sessions = 1,
    lastSessionDate = "May 14",
    lastSessionSummary = "100 x 10",
    changePercentage = change,
    trend = listOf(100),
)
```

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.ProgressCalculationsTest"`

Expected: compilation fails because `organizeProgressExercises`, `ProgressSplitSource`, `buildProgressSplitPages`, and `normalizeProgressSplitIndex` do not exist.

- [ ] **Step 3: Add minimal models and helpers**

Add to `UiModels.kt`:

```kotlin
enum class ProgressOrganizationMode {
    PROGRESS, SPLIT
}

@Immutable
data class ProgressSplitPageUi(
    val splitId: Long,
    val name: String,
    val exercises: List<ProgressExerciseUi>,
)
```

Add to `ProgressViewModel.kt`:

```kotlin
internal data class ProgressSplitSource(
    val splitId: Long,
    val name: String,
    val exerciseIds: List<Long>,
)

internal fun organizeProgressExercises(
    exercises: List<ProgressExerciseUi>,
    searchQuery: String,
): List<ProgressExerciseUi> {
    val query = searchQuery.trim()
    return exercises
        .asSequence()
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(
            compareByDescending<ProgressExerciseUi> { it.changePercentage }
                .thenBy { it.name.lowercase(Locale.US) }
        )
        .toList()
}

internal fun buildProgressSplitPages(
    exercises: List<ProgressExerciseUi>,
    splits: List<ProgressSplitSource>,
    searchQuery: String,
): List<ProgressSplitPageUi> {
    val byId = exercises.associateBy { it.exerciseId }
    return splits.mapNotNull { split ->
        val splitExercises = organizeProgressExercises(
            exercises = split.exerciseIds.mapNotNull { byId[it] },
            searchQuery = searchQuery,
        )
        if (splitExercises.isEmpty()) {
            null
        } else {
            ProgressSplitPageUi(split.splitId, split.name, splitExercises)
        }
    }
}

internal fun normalizeProgressSplitIndex(index: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return index.coerceIn(0, pageCount - 1)
}
```

- [ ] **Step 4: Run tests and verify GREEN**

Run: `./gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.ProgressCalculationsTest"`

Expected: all tests in `ProgressCalculationsTest` pass.

### Task 2: Wire Progress Organization State

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/UiModels.kt`
- Modify: `src/main/java/com/ayman/ecolift/ui/viewmodel/ProgressViewModel.kt`

- [ ] **Step 1: Extend `ProgressUiState`**

Add these fields with defaults:

```kotlin
val organizationMode: ProgressOrganizationMode = ProgressOrganizationMode.PROGRESS,
val searchQuery: String = "",
val visibleExercises: List<ProgressExerciseUi> = emptyList(),
val splitPages: List<ProgressSplitPageUi> = emptyList(),
val selectedSplitIndex: Int = 0,
```

- [ ] **Step 2: Add mutable UI inputs in `ProgressViewModel`**

Add:

```kotlin
private val organizationMode = MutableStateFlow(ProgressOrganizationMode.PROGRESS)
private val searchQuery = MutableStateFlow("")
private val selectedSplitIndex = MutableStateFlow(0)
private val workoutRepository = WorkoutRepository(database)
```

- [ ] **Step 3: Observe split sources**

Combine cycle slots and split exercise rows into `ProgressSplitSource` values:

```kotlin
private val splitSources = combine(
    workoutRepository.observeCycleSlots(),
    workoutRepository.observeAllSplitExercises(),
) { slots, rows ->
    val rowsBySplit = rows.groupBy { it.splitId }
    slots.map { slot ->
        ProgressSplitSource(
            splitId = slot.id,
            name = slot.name,
            exerciseIds = rowsBySplit[slot.id].orEmpty()
                .sortedBy { it.orderIndex }
                .map { it.exerciseId },
        )
    }
}
```

- [ ] **Step 4: Include organization data in `uiState`**

Update the `combine(...)` for `uiState` to include `organizationMode`, `searchQuery`, `selectedSplitIndex`, and `splitSources`. Derive:

```kotlin
val visibleExercises = organizeProgressExercises(exercises, query)
val splitPages = buildProgressSplitPages(exercises, splits, query)
val normalizedSplitIndex = normalizeProgressSplitIndex(splitIndex, splitPages.size)
```

Then set those fields on `ProgressUiState`.

- [ ] **Step 5: Add actions**

Add:

```kotlin
fun setOrganizationMode(mode: ProgressOrganizationMode) { organizationMode.value = mode }
fun setSearchQuery(query: String) { searchQuery.value = query }
fun setSelectedSplitIndex(index: Int) { selectedSplitIndex.value = index }
fun showPreviousSplit() { selectedSplitIndex.value = normalizeProgressSplitIndex(selectedSplitIndex.value - 1, uiState.value.splitPages.size) }
fun showNextSplit() { selectedSplitIndex.value = normalizeProgressSplitIndex(selectedSplitIndex.value + 1, uiState.value.splitPages.size) }
```

- [ ] **Step 6: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.ProgressCalculationsTest"`

Expected: pass.

### Task 3: Render Progress And Split Modes

**Files:**
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/ProgressScreen.kt`
- Modify: `src/main/java/com/ayman/ecolift/ui/navigation/ProgressScreenV2.kt`

- [ ] **Step 1: Update wrapper mapping**

In `ProgressScreen.kt`, map `uiState.visibleExercises` for Progress mode and map selected split page for Split mode. Pass `uiState.searchQuery`, `uiState.organizationMode`, `uiState.splitPages`, `uiState.selectedSplitIndex`, and view model callbacks into V2.

- [ ] **Step 2: Add V2 mode enum**

Add to `ProgressScreenV2.kt`:

```kotlin
enum class ProgressOrganizationModeV2 { PROGRESS, SPLIT }

data class ProgressSplitPage(
    val id: Long,
    val name: String,
    val exercises: List<ProgressExercise>,
)
```

- [ ] **Step 3: Add segmented organization control**

In list `ProgressScreen(...)`, add a two-button segmented control under the top app bar and above search:

```kotlin
ProgressOrganizationModeV2.values().forEach { mode ->
    TextButton(
        onClick = { onOrganizationModeChange(mode) },
        modifier = Modifier.weight(1f),
    ) {
        Text(if (mode == ProgressOrganizationModeV2.PROGRESS) "Progress" else "Split")
    }
}
```

Use existing colors and typography from the screen.

- [ ] **Step 4: Add split pager content**

Use Compose foundation pager:

```kotlin
val pagerState = rememberPagerState(initialPage = selectedSplitIndex) { splitPages.size }

LaunchedEffect(selectedSplitIndex, splitPages.size) {
    if (splitPages.isNotEmpty() && pagerState.currentPage != selectedSplitIndex) {
        pagerState.animateScrollToPage(selectedSplitIndex)
    }
}

LaunchedEffect(pagerState.currentPage) {
    if (pagerState.currentPage != selectedSplitIndex) {
        onSelectedSplitIndexChange(pagerState.currentPage)
    }
}

HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
    SplitProgressPage(...)
}
```

If pager imports fail in this project, replace with `pointerInput` + `detectHorizontalDragGestures` and keep the same public V2 function signature.

- [ ] **Step 5: Add arrow controls and one-split-at-a-time layout**

The split mode header should show:

```text
<  Push  Split 1 of 4  >
```

The list below contains only the exercises for the current split. Do not render all split pages in one vertical list.

- [ ] **Step 6: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.ayman.ecolift.ui.viewmodel.ProgressCalculationsTest"`

Expected: pass.

### Task 4: Compile And Verify

**Files:**
- No new code unless compile errors require small fixes in touched files.

- [ ] **Step 1: Compile debug build**

Run: `./gradlew.bat assembleDebug`

Expected: build succeeds.

- [ ] **Step 2: Run unit tests**

Run: `./gradlew.bat testDebugUnitTest`

Expected: all unit tests pass.

- [ ] **Step 3: Inspect diff**

Run: `git diff -- src/main/java/com/ayman/ecolift/ui/viewmodel/UiModels.kt src/main/java/com/ayman/ecolift/ui/viewmodel/ProgressViewModel.kt src/main/java/com/ayman/ecolift/ui/navigation/ProgressScreen.kt src/main/java/com/ayman/ecolift/ui/navigation/ProgressScreenV2.kt src/test/java/com/ayman/ecolift/ui/viewmodel/ProgressCalculationsTest.kt`

Expected: diff only contains Progress organization changes.
