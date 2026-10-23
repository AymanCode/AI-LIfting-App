package com.ayman.ecolift.ui.navigation

import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.noun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.MonthPaceUtil
import com.ayman.ecolift.data.splits.buildEraBands
import com.ayman.ecolift.data.splits.tickerSegments
import com.ayman.ecolift.data.splits.volumeBrightThreshold
import com.ayman.ecolift.data.splits.volumeLightThreshold
import androidx.compose.runtime.mutableIntStateOf
import com.ayman.ecolift.ui.viewmodel.AvailableSessionUi
import com.ayman.ecolift.ui.viewmodel.DataPart
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.ui.viewmodel.CycleArchiveViewModel
import com.ayman.ecolift.ui.viewmodel.SplitGroupCardUi
import com.ayman.ecolift.ui.viewmodel.SplitTabMode
import com.ayman.ecolift.ui.viewmodel.SplitViewModel
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.EarnedShineTier
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.util.todayLocalDate
import com.ayman.ecolift.web.util.YearMonthW
import kotlinx.datetime.LocalDate

import com.ayman.ecolift.ui.theme.*

// ... rest of the file ...

// ============================================================================
// Stateful entry
// ============================================================================

private val SplitTabModeSaver = Saver<SplitTabMode, String>(
    save = { it.name }, restore = { SplitTabMode.valueOf(it) },
)
private val SplitMonthSaver = Saver<YearMonthW, String>(
    save = { it.atDay(1).toString() }, restore = { YearMonthW.from(LocalDate.parse(it)) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    onNavigateToLog: (splitId: Long) -> Unit = {},
    onNavigateToExerciseProgress: (exerciseId: Long) -> Unit = {},
    onNavigateToArchiveDetail: (archiveId: Long) -> Unit = {},
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    smartSuggestEnabled: Boolean = true,
    onSmartSuggestChange: (Boolean) -> Unit = {},
) {
    val repos = LocalRepos.current
    val viewModel: SplitViewModel = viewModel { SplitViewModel(repos) }
    val archiveViewModel: CycleArchiveViewModel = viewModel { CycleArchiveViewModel(repos) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val availableSessions by viewModel.availableSessions.collectAsStateWithLifecycle()
    val dayInfo by viewModel.dayInfo.collectAsStateWithLifecycle()
    val dayMarks by viewModel.dayMarks.collectAsStateWithLifecycle()
    val monthlyAverage by viewModel.personalMonthlyAverage.collectAsStateWithLifecycle()
    val archives by archiveViewModel.archives.collectAsStateWithLifecycle()
    var tabMode by rememberSaveable(stateSaver = SplitTabModeSaver) { mutableStateOf(SplitTabMode.CURRENT) }
    var displayedMonth by rememberSaveable(stateSaver = SplitMonthSaver) { mutableStateOf(YearMonthW.now()) }
    var monthFlip by rememberSaveable { mutableIntStateOf(1) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

    var editorSlotId by rememberSaveable { mutableStateOf<Long?>(null) }
    val deskGroupSlotId by DeskSplitDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(deskGroupSlotId, state.cards) {
        val target = deskGroupSlotId ?: return@LaunchedEffect
        if (state.cards.any { it.slotId == target }) {
            tabMode = SplitTabMode.CURRENT
            editorSlotId = target
            DeskSplitDeepLink.clear()
        }
    }

    // The dial, the sticker sheet and the cards all run in due order (spec 2026-09-28).
    val splitTypes = remember(state.cards) {
        state.cards.map { card ->
            SplitType(
                id = card.slotId,
                name = card.name,
                exerciseCount = card.members.size,
                lastRunLabel = card.lastSessionDate?.let { "last ${formatMonthDay(it)}" } ?: "not trained yet",
                colorIndex = card.colorIndex,
            )
        }
    }
    var renameTarget by remember { mutableStateOf<SplitGroupCardUi?>(null) }
    var retireTarget by remember { mutableStateOf<SplitGroupCardUi?>(null) }
    var fromWorkoutTarget by remember { mutableStateOf<SplitGroupCardUi?>(null) }

    val today = remember { todayLocalDate() }
    val displayedMonthInfo = remember(dayInfo, displayedMonth) {
        dayInfo.filterKeys { YearMonthW.from(it) == displayedMonth }
    }
    val lifetimeWorkoutCount = dayInfo.size
    val brightThreshold = remember(dayInfo) {
        volumeBrightThreshold(dayInfo.values.map { it.volumeLbs })
    }
    val lightThreshold = remember(dayInfo) {
        volumeLightThreshold(dayInfo.values.map { it.volumeLbs })
    }
    val tickerText = remember(displayedMonthInfo, displayedMonth, monthlyAverage, today) {
        val monthSessions = displayedMonthInfo.size
        val isCurrentMonth = displayedMonth == YearMonthW.from(today)
        tickerSegments(
            monthSessions = monthSessions,
            monthVolumeLbs = displayedMonthInfo.values.sumOf { it.volumeLbs },
            isCurrentMonth = isCurrentMonth,
            dayOfMonth = today.dayOfMonth,
            projected = MonthPaceUtil.projectedMonthCount(
                countSoFar = monthSessions,
                daysElapsed = today.dayOfMonth,
                daysInMonth = displayedMonth.lengthOfMonth(),
            ),
            monthlyAvg = monthlyAverage,
            lifetimeCount = lifetimeWorkoutCount,
            tierName = EarnedShineTier.from(lifetimeWorkoutCount).displayName,
        )
    }
    val eras = remember(archives, dayInfo, today) {
        buildEraBands(
            archives = archives,
            earliestWorkout = dayInfo.keys.minOrNull(),
            today = today,
        )
    }
    // The empty archive's ghost album counts from the same start the archive action defaults to.
    val currentCycleStart by produceState<LocalDate?>(null, archives, dayInfo.size) {
        value = runCatching { LocalDate.parse(archiveViewModel.defaultArchiveWindow().first) }.getOrNull()
    }

    CycleSplitScreen(
        splits = splitTypes,
        dayInfo = dayInfo,
        dayMarks = dayMarks,
        lightThreshold = lightThreshold,
        flipDirection = monthFlip,
        displayedMonth = displayedMonth,
        tickerText = tickerText,
        brightThreshold = brightThreshold,
        lifetimeWorkoutCount = lifetimeWorkoutCount,
        eras = eras,
        cards = state.cards,
        onPreviousMonth = {
            monthFlip = -1
            displayedMonth = displayedMonth.minusMonths(1)
        },
        onNextMonth = {
            monthFlip = 1
            displayedMonth = displayedMonth.plusMonths(1)
        },
        onAddSplit = {
            if (!DemoGuard.blockIfDemo()) showAddDialog = true
        },
        onEditSplit = { editorSlotId = it.slotId },
        smartSuggestEnabled = smartSuggestEnabled,
        onToggleSmartSuggest = { onSmartSuggestChange(!smartSuggestEnabled) },
        tabMode = tabMode,
        onTabModeChange = { tabMode = it },
        archives = archives,
        onOpenArchive = onNavigateToArchiveDetail,
        onArchiveCurrentCycle = {
            if (!DemoGuard.blockIfDemo()) showArchiveDialog = true
        },
        currentCycleStart = currentCycleStart,
        paletteChoice = paletteChoice,
        onPaletteChoiceChange = onPaletteChoiceChange,
    )

    val editorCard = editorSlotId?.let { id -> state.cards.firstOrNull { it.slotId == id && it.isSaved } }
    if (editorCard != null) {
        SplitEditorSheet(
            card = editorCard,
            canEdit = state.boardsReady,
            pending = editorCard.slotId in state.pendingSlots,
            onDismiss = { editorSlotId = null },
            onEdit = { edit -> viewModel.editMembers(editorCard.slotId, edit) },
            onAddLift = { name, onSaved -> viewModel.addTypedLift(editorCard.slotId, name, onSaved) },
            onRename = { if (!DemoGuard.blockIfDemo()) renameTarget = editorCard },
            onFromWorkout = { if (!DemoGuard.blockIfDemo()) fromWorkoutTarget = editorCard },
            onRetire = { if (!DemoGuard.blockIfDemo()) retireTarget = editorCard },
            // Start is seed-only on the Log (spec 2026-09-28); nothing advances a rotation.
            onStart = {
                if (!DemoGuard.blockIfDemo()) {
                    onNavigateToLog(editorCard.slotId)
                    editorSlotId = null
                }
            },
            onOpenLift = onNavigateToExerciseProgress,
        )
    }

    fromWorkoutTarget?.let { target ->
        SaveFromDayDialog(
            splitName = target.name,
            sessions = availableSessions,
            onDismiss = { fromWorkoutTarget = null },
            onPick = { date ->
                viewModel.saveSplitFromDate(target.slotId, date)
                fromWorkoutTarget = null
            },
        )
    }

    if (showAddDialog) {
        AddSplitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                if (!DemoGuard.blockIfDemo()) {
                    viewModel.addSplit(name)
                    showAddDialog = false
                }
            },
        )
    }

    renameTarget?.let { target ->
        RenameSplitSheet(
            initialName = if (target.isUnnamed) "" else target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameSplit(target.slotId, name)
                renameTarget = null
            },
        )
    }

    retireTarget?.let { target ->
        ConfirmRetireDialog(
            splitName = target.name,
            onDismiss = { retireTarget = null },
            onConfirm = {
                viewModel.retireSplit(target.slotId)
                retireTarget = null
                editorSlotId = null
            },
        )
    }

    if (showArchiveDialog) {
        ArchiveCycleDialog(
            loadDefaults = { archiveViewModel.defaultArchiveWindow() },
            checkOverlap = { start, end -> archiveViewModel.overlapCount(start, end) },
            onConfirm = { name, start, end ->
                if (!DemoGuard.blockIfDemo()) {
                    archiveViewModel.archiveCurrentCycle(name, start, end)
                    showArchiveDialog = false
                }
            },
            onDismiss = { showArchiveDialog = false },
        )
    }
}

// ============================================================================
// Dialogs
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSplitDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val palette = LocalGlassPalette.current
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.pageBottom,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.glassStrokeStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 56.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "New split",
                color = palette.ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Push, Pull, Legs…", color = palette.inkSubtle) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accentStrong,
                    unfocusedBorderColor = palette.glassStrokeStrong,
                    focusedLabelColor = palette.accentStrong,
                    unfocusedLabelColor = palette.inkSubtle,
                    cursorColor = palette.accentStrong,
                    focusedTextColor = palette.ink,
                    unfocusedTextColor = palette.ink,
                ),
            )
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentStrong,
                    contentColor = palette.pageBottom,
                    disabledContainerColor = palette.glassFillStrong,
                    disabledContentColor = palette.inkSubtle,
                ),
            ) {
                Text("Create", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "Aug 1, 2026" for a date typed as YYYY-MM-DD; null while it is not a date yet. */
internal fun archiveDateLabel(iso: String): String? =
    runCatching { LocalDate.parse(iso.trim()) }.getOrNull()?.let { date ->
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
    }

/** The archive sheet's data line: the window it freezes, and any archives it overlaps. */
internal fun archiveConsequence(start: String, end: String, overlap: Int): List<DataPart>? {
    val from = archiveDateLabel(start) ?: return null
    val to = archiveDateLabel(end) ?: return null
    return buildList {
        add(data(from))
        add(filler(" to "))
        add(data(to))
        add(filler(" frozen · sets outside don't count"))
        if (overlap > 0) {
            add(filler(" · overlaps "))
            add(data("$overlap"))
            add(filler(" ${noun(overlap, "archive")}"))
        }
    }
}

@Composable
private fun EspressoSheetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = LogUiFontFamily) },
        singleLine = true,
        isError = isError,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = LogDisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = EspressoPalette.ink,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EspressoPalette.legs,
            unfocusedBorderColor = EspressoPalette.hair2,
            errorBorderColor = EspressoPalette.danger,
            cursorColor = EspressoPalette.legs,
            focusedLabelColor = EspressoPalette.legs,
            unfocusedLabelColor = EspressoPalette.dim,
            errorLabelColor = EspressoPalette.danger,
            focusedTextColor = EspressoPalette.ink,
            unfocusedTextColor = EspressoPalette.ink,
            focusedContainerColor = EspressoPalette.panel2,
            unfocusedContainerColor = EspressoPalette.panel2,
            errorContainerColor = EspressoPalette.panel2,
        ),
    )
}

@Composable
private fun ArchiveCycleDialog(
    loadDefaults: suspend () -> Pair<String, String>,
    checkOverlap: suspend (String, String) -> Int,
    onConfirm: (name: String, start: String, end: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var overlap by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val defaults = loadDefaults()
        start = defaults.first
        end = defaults.second
        loaded = true
    }

    val validRange = remember(start, end) {
        runCatching { LocalDate.parse(start) <= LocalDate.parse(end) }
            .getOrDefault(false)
    }

    LaunchedEffect(start, end, loaded, validRange) {
        overlap = if (loaded && validRange) checkOverlap(start, end) else 0
    }

    EspressoConfirmSheet(
        title = "Archive cycle",
        consequence = if (validRange) archiveConsequence(start, end, overlap) else null,
        confirmLabel = "Archive",
        tone = ConfirmTone.Primary(EspressoPalette.legs),
        confirmEnabled = validRange,
        onConfirm = { onConfirm(name.trim(), start.trim(), end.trim()) },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EspressoSheetField(value = name, onValueChange = { name = it }, label = "Cycle name")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    EspressoSheetField(value = start, onValueChange = { start = it }, label = "Start", isError = loaded && !validRange)
                }
                Box(Modifier.weight(1f)) {
                    EspressoSheetField(value = end, onValueChange = { end = it }, label = "End", isError = loaded && !validRange)
                }
            }
            if (loaded && !validRange) {
                Text(
                    "YYYY-MM-DD · start ≤ end",
                    color = EspressoPalette.danger,
                    fontFamily = LogUiFontFamily,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun ConfirmRetireDialog(
    splitName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    EspressoConfirmSheet(
        title = "Retire $splitName?",
        consequence = splitRetireConsequence(),
        confirmLabel = "Retire",
        tone = ConfirmTone.Destructive,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * What retiring a split does (suppressed_at): it leaves the rotation for good, and under plan
 * decision 4 its days inside the memory window are written back as unattributed (Freestyle on the
 * calendar) while frozen days, older or inside an archived cycle, keep its sticker.
 */
internal fun splitRetireConsequence(): List<DataPart> = listOf(
    filler("Leaves the rotation · not relearned · recent days lose the sticker"),
)

/** What deleting a split takes: the split and its saved list; logged days stay. */
internal fun splitDeleteConsequence(liftCount: Int): List<DataPart> = buildList {
    if (liftCount > 0) {
        add(filler("The split and its "))
        add(data("$liftCount"))
        add(filler(if (liftCount == 1) " saved lift go. " else " saved lifts go. "))
    } else {
        add(filler("The split goes. "))
    }
    add(filler("Days already logged stay."))
}

/** "Wed, Sep 23" for a session row. */
private fun sessionDateLabel(iso: String): String =
    runCatching { espressoShortDate(LocalDate.parse(iso)) }.getOrDefault(iso)

@Composable
private fun SaveFromDayDialog(
    splitName: String,
    sessions: List<AvailableSessionUi>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    EspressoConfirmSheet(
        title = "Pick a workout day",
        consequence = listOf(filler("Its lifts go in "), data(splitName), filler(" · the rest become other options")),
        onDismiss = onDismiss,
    ) {
        if (sessions.isEmpty()) {
            EspressoEyebrow("No workouts")
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sessions.size, key = { sessions[it].date }) { i ->
                    val session = sessions[i]
                    val dateLabel = sessionDateLabel(session.date)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(EspressoPalette.panel2)
                            .semantics { contentDescription = "$dateLabel, ${countLabel(session.exerciseCount, "lift")}" }
                            .clickable { onPick(session.date) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dateLabel,
                                fontFamily = LogDisplayFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = EspressoPalette.ink,
                                modifier = Modifier.weight(1f),
                            )
                            DataText(
                                parts = listOf(data("${session.exerciseCount}"), filler(if (session.exerciseCount == 1) " lift" else " lifts")),
                                size = 12.sp,
                                data = EspressoPalette.dim,
                            )
                        }
                        if (session.preview.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                session.preview.joinToString(" · "),
                                fontFamily = LogUiFontFamily,
                                color = EspressoPalette.faint,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
