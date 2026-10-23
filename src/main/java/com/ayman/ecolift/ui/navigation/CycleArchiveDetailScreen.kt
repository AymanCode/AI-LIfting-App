package com.ayman.ecolift.ui.navigation

import com.ayman.ecolift.web.util.countLabel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.progress.ScoreWeights
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.viewmodel.CycleArchiveViewModel
import com.ayman.ecolift.ui.viewmodel.formatArchiveDateRange
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.demo.DemoGuard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleArchiveDetailScreen(
    archiveId: Long,
    onBack: () -> Unit = {},
    onOpenLiftReport: (Long) -> Unit = {},
) {
    val repos = LocalRepos.current
    val viewModel: CycleArchiveViewModel = viewModel { CycleArchiveViewModel(repos) }
    LaunchedEffect(archiveId) { viewModel.loadArchive(archiveId) }

    val notes by viewModel.linerNotes.collectAsStateWithLifecycle()
    val name by viewModel.detailName.collectAsStateWithLifecycle()
    val volumeDefinition by viewModel.detailVolumeDefinition.collectAsStateWithLifecycle()
    val volumeLbs by viewModel.detailVolumeLbs.collectAsStateWithLifecycle()
    val core by viewModel.core.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val window by viewModel.window.collectAsStateWithLifecycle()
    val weights by viewModel.weights.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showWeights by remember { mutableStateOf(false) }
    var deepCutsExpanded by rememberSaveable(archiveId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val palette = LocalGlassPalette.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "FROM THE ARCHIVE",
                        color = palette.inkSubtle,
                        fontSize = 11.sp,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Archive",
                            tint = palette.ink,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!DemoGuard.blockIfDemo()) {
                            showDeleteConfirm = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete archive",
                            tint = palette.inkMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        val coreSnapshot = core
        val comparisonSnapshot = comparison
        val scoreSnapshot = score
        val notesSnapshot = notes
        if (coreSnapshot == null || notesSnapshot == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Loading…", color = palette.inkMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val scored = comparisonSnapshot != null &&
                    scoreSnapshot != null &&
                    comparisonSnapshot.comparedCount > 0
                item(key = "hero") {
                    ArchivePosterHero(
                        name = name.ifBlank { "Untitled cycle" },
                        dateRangeLabel = formatArchiveDateRange(
                            coreSnapshot.startDate,
                            coreSnapshot.endDate,
                        ),
                        weeklyCounts = notesSnapshot.weeklySets,
                        linerNotes = true,
                        score = if (scored) scoreSnapshot else null,
                        volumeLbs = volumeLbs,
                    )
                }
                item(key = "volume_definition") {
                    Text(volumeDefinition, color = palette.inkMuted, fontSize = 12.sp)
                }
                // An empty ticker gets no item at all: an empty item still takes the list's spacing.
                val tickerSegments = buildTickerSegments(coreSnapshot, comparisonSnapshot, volumeLbs, palette)
                if (tickerSegments.isNotEmpty()) {
                    item(key = "ticker") { ArchiveTicker(tickerSegments, scrolling = false) }
                }
                if (scored) {
                    item(key = "record") {
                        Column {
                            ArchiveRecordChips(comparisonSnapshot!!)
                            Text(
                                text = "Adjust weights",
                                color = palette.accentStrong,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showWeights = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                item(key = "setlist") {
                    ArchiveNotesHeading("Setlist", "${countLabel(notesSnapshot.setlist.size + notesSnapshot.earlyHints.size, "lift")}")
                }
                items(notesSnapshot.setlist, key = { "track_${it.exerciseId}" }) { track ->
                    ArchiveTrackRow(track, onOpenLiftReport)
                }
                if (notesSnapshot.earlyHints.isNotEmpty()) {
                    item(key = "early_hints") {
                        ArchiveNotesHeading("Early hints", countLabel(notesSnapshot.earlyHints.size, "lift"))
                    }
                    items(notesSnapshot.earlyHints, key = { "hint_${it.exerciseId}" }) { track ->
                        ArchiveTrackRow(track, onOpenLiftReport, compact = true)
                    }
                }
                if (notesSnapshot.credits.isNotEmpty()) {
                    item(key = "credits") {
                        ArchiveNotesHeading("New this chapter", "${countLabel(notesSnapshot.credits.size, "lift")}")
                    }
                    items(notesSnapshot.credits, key = { "credit_${it.exerciseId}" }) { track ->
                        ArchiveCreditRow(track, onOpenLiftReport, credit = true)
                    }
                }
                if (notesSnapshot.deepCuts.isNotEmpty()) {
                    item(key = "deepcuts") {
                        Text(
                            text = notesSnapshot.deepCutsLabel + if (deepCutsExpanded) " ⌃" else " ⌄",
                            color = palette.ink, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                                .semantics {
                                    contentDescription = notesSnapshot.deepCutsLabel
                                    stateDescription = if (deepCutsExpanded) "Expanded" else "Collapsed"
                                }
                                .clickable(role = Role.Button) { deepCutsExpanded = !deepCutsExpanded }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
                if (deepCutsExpanded) {
                    items(notesSnapshot.deepCuts, key = { "cut_${it.exerciseId}" }) { track ->
                        ArchiveCreditRow(track, onOpenLiftReport, credit = false)
                    }
                }
                item(key = "stats") { ArchiveStatGrid(coreSnapshot) }
                item(key = "versus") {
                    ArchiveVersusHeading(window = window, onWindowChange = viewModel::setWindow)
                }
                items(notesSnapshot.versus, key = { "versus_${it.exerciseId}" }) { track ->
                    ArchiveTrackRow(track, onOpenLiftReport)
                }
                item(key = "reel") {
                    HighlightReel(comparison = comparisonSnapshot)
                }
                item(key = "weeks") { SessionsPerWeek(coreSnapshot) }
                item(key = "rep_mix") { SetlistBar(coreSnapshot.repBuckets, title = "Rep-range mix") }
            }
        }
    }

    if (showWeights) {
        WeightTuningSheet(
            weights = weights,
            onChange = viewModel::setWeights,
            onReset = viewModel::resetWeights,
            onDismiss = { showWeights = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            modifier = Modifier.glassPanel(palette, RoundedCornerShape(24.dp), strong = true),
            containerColor = Color.Transparent,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            title = { Text("Delete this archive?", color = palette.ink) },
            text = {
                Text(
                    "Removes the saved snapshot. Logged workouts stay.",
                    color = palette.inkMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!DemoGuard.blockIfDemo()) {
                            showDeleteConfirm = false
                            viewModel.deleteArchive(archiveId)
                            onBack()
                        }
                    }
                ) {
                    Text("Delete", color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = palette.inkSubtle)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightTuningSheet(
    weights: ScoreWeights,
    onChange: (ScoreWeights) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = palette.scrim,
        dragHandle = { Spacer(Modifier.height(6.dp)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassPanel(palette, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strong = true)
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 28.dp)
        ) {
            Text("Adjust score weights", color = palette.ink, style = LogType.exerciseTitle)
            Spacer(Modifier.height(4.dp))
            Text(
                "The preset works for most lifters. Tune only if you know what you want to emphasize.",
                color = palette.inkMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            WeightSlider("Progression", weights.progression) { onChange(weights.copy(progression = it)) }
            WeightSlider("Momentum", weights.momentum) { onChange(weights.copy(momentum = it)) }
            WeightSlider("Consistency", weights.consistency) { onChange(weights.copy(consistency = it)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset) {
                Text("Reset to 40/35/25", color = palette.accentStrong)
            }
        }
    }
}

@Composable
private fun WeightSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    val palette = LocalGlassPalette.current
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = palette.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$value", color = palette.inkMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = palette.accentStrong,
                activeTrackColor = palette.accentStrong,
                inactiveTrackColor = palette.glassFillStrong,
            ),
        )
    }
}
