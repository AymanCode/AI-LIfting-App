package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.progress.LiftTrend
import com.ayman.ecolift.data.progress.ScoreWeights
import com.ayman.ecolift.ui.theme.AccentTeal
import com.ayman.ecolift.ui.theme.BackgroundElevated
import com.ayman.ecolift.ui.theme.BackgroundPrimary
import com.ayman.ecolift.ui.theme.BackgroundSurface
import com.ayman.ecolift.ui.theme.ErrorRed
import com.ayman.ecolift.ui.theme.TextInactive
import com.ayman.ecolift.ui.theme.TextMuted
import com.ayman.ecolift.ui.theme.TextPrimary
import com.ayman.ecolift.ui.viewmodel.CycleArchiveViewModel
import com.ayman.ecolift.ui.viewmodel.formatArchiveDateRange
import kotlin.math.roundToInt

private val Mono = FontFamily.Monospace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleArchiveDetailScreen(
    archiveId: Long,
    viewModel: CycleArchiveViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    LaunchedEffect(archiveId) { viewModel.loadArchive(archiveId) }
    DisposableEffect(archiveId) {
        onDispose { viewModel.clearDetail() }
    }

    val name by viewModel.detailName.collectAsStateWithLifecycle()
    val core by viewModel.core.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val window by viewModel.window.collectAsStateWithLifecycle()
    val weights by viewModel.weights.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showWeights by remember { mutableStateOf(false) }
    var detailLift by remember { mutableStateOf<LiftTrend?>(null) }
    var sort by remember { mutableStateOf(TrendSort.VELOCITY) }

    Scaffold(
        containerColor = BackgroundPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = name.ifBlank { "Archived cycle" },
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete archive",
                            tint = TextMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundPrimary,
                ),
            )
        },
    ) { innerPadding ->
        val coreSnapshot = core
        val comparisonSnapshot = comparison
        val scoreSnapshot = score
        if (coreSnapshot == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Loading...", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "range") {
                    Column {
                        Text(
                            formatArchiveDateRange(coreSnapshot.startDate, coreSnapshot.endDate),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${coreSnapshot.spanDays} days · ${coreSnapshot.lifts.size} lifts",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                        )
                    }
                }
                item(key = "window") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "COMPARED TO YOUR PREVIOUS",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f),
                        )
                        WindowToggle(selected = window, onSelect = viewModel::setWindow)
                    }
                }
                if (comparisonSnapshot != null && scoreSnapshot != null && comparisonSnapshot.comparedCount > 0) {
                    item(key = "hero") {
                        CompositeHeroCard(
                            score = scoreSnapshot,
                            window = window,
                            onAdjustWeights = { showWeights = true },
                        )
                    }
                    item(key = "chips-eyebrow") {
                        SectionEyebrow("The cycle vs your previous ${window.label()}")
                    }
                    item(key = "chips") { StoryChips(coreSnapshot, comparisonSnapshot) }
                    item(key = "outcome-eyebrow") {
                        SectionEyebrow("Movement vs your previous ${window.label()}")
                    }
                    item(key = "outcome") { OutcomeBar(comparisonSnapshot) }
                    item(key = "ladder-eyebrow") {
                        SectionEyebrow("Every lift · ranked vs your previous ${window.label()}")
                    }
                    item(key = "ladder") { GainLadder(comparisonSnapshot) }
                } else if (comparisonSnapshot != null) {
                    item(key = "no-comparison") {
                        NoComparisonHeroCard(
                            comparison = comparisonSnapshot,
                            window = window,
                        )
                    }
                }
                item(key = "rep-eyebrow") { SectionEyebrow("Rep-range distribution · this cycle") }
                item(key = "rep") { RepDistribution(coreSnapshot.repBuckets) }
                item(key = "heat-eyebrow") { SectionEyebrow("Consistency · sessions over the span") }
                item(key = "heat") { ConsistencyHeatmap(coreSnapshot) }
                item(key = "trend-eyebrow") { SectionEyebrow("All lifts · trend grid") }
                item(key = "trend") {
                    TrendGrid(
                        core = coreSnapshot,
                        comparison = comparisonSnapshot,
                        sort = sort,
                        onSortChange = { sort = it },
                        onLiftClick = { detailLift = it },
                    )
                }
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

    detailLift?.let { lift ->
        LiftDetailSheet(
            lift = lift,
            vsPct = comparison?.lifts?.firstOrNull { it.exerciseId == lift.exerciseId }?.vsPct,
            onDismiss = { detailLift = null },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BackgroundSurface,
            title = { Text("Delete this archive?", color = TextPrimary) },
            text = {
                Text(
                    "This removes the saved snapshot. Your logged workouts are not affected.",
                    color = TextMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteArchive(archiveId)
                        onBack()
                    }
                ) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundSurface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Adjust score weights", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "The preset works for most lifters. Tune only if you know what you want to emphasize.",
                color = TextMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            WeightSlider("Progression", weights.progression) { onChange(weights.copy(progression = it)) }
            WeightSlider("Momentum", weights.momentum) { onChange(weights.copy(momentum = it)) }
            WeightSlider("Consistency", weights.consistency) { onChange(weights.copy(consistency = it)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset) {
                Text("Reset to preset (40 / 35 / 25)", color = AccentTeal)
            }
        }
    }
}

@Composable
private fun WeightSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$value", color = TextMuted, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = AccentTeal,
                activeTrackColor = AccentTeal,
                inactiveTrackColor = BackgroundElevated,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiftDetailSheet(
    lift: LiftTrend,
    vsPct: Float?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slope = lift.slopePerWeek
    val slopeColor = when {
        slope == null -> TextMuted
        slope > 0.05f -> AccentTeal
        slope < -0.05f -> ErrorRed
        else -> TextMuted
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundSurface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(lift.name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (lift.isBodyweight) "Bodyweight · ${lift.unitLabel}" else "Weighted · ${lift.unitLabel}",
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = Mono,
            )
            Spacer(Modifier.height(16.dp))
            MiniSparkline(
                values = lift.points.map { it.value },
                color = slopeColor,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat("Velocity", if (slope == null) "—" else "${if (slope >= 0f) "+" else ""}${"%.1f".format(slope)} ${lift.unitLabel}", slopeColor)
                DetailStat("Fit (R²)", if (lift.r2 != null) "%.2f".format(lift.r2) else "—", TextPrimary)
                DetailStat("vs window", formatPct(vsPct), if ((vsPct ?: 0f) >= 0f) AccentTeal else ErrorRed)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${lift.points.size} sessions logged this cycle.",
                color = TextInactive,
                fontSize = 11.sp,
                fontFamily = Mono,
            )
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), color = TextMuted, fontSize = 10.sp, fontFamily = Mono, letterSpacing = 1.sp)
    }
}
