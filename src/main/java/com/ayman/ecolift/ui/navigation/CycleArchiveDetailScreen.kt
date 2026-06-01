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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.ExerciseSnapshot
import com.ayman.ecolift.data.SplitSnapshot
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
import com.ayman.ecolift.ui.viewmodel.formatSignedLbs

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

    val snapshot by viewModel.detail.collectAsStateWithLifecycle()
    val name by viewModel.detailName.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
        val cycle = snapshot
        if (cycle == null) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") { CycleSummaryCard(cycle) }
                cycle.splits.forEach { split ->
                    item(key = "split-${split.slotId}-${split.bucketKind}") {
                        SplitSectionHeader(split)
                    }
                    items(
                        items = split.exercises,
                        key = { exercise -> "exercise-${split.slotId}-${split.bucketKind}-${exercise.exerciseId}" },
                        contentType = { "archiveExercise" },
                    ) { exercise ->
                        ArchiveExerciseCard(exercise)
                    }
                }
            }
        }
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

@Composable
private fun CycleSummaryCard(cycle: CycleSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                formatArchiveDateRange(cycle.startDate, cycle.endDate),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text("${cycle.totals.spanDays} days", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryStat("Sessions", cycle.totals.sessions.toString())
                SummaryStat("Volume", "${"%,d".format(cycle.totals.totalVolumeLbs)} lb")
                SummaryStat("Sets", cycle.totals.totalSets.toString())
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SplitSectionHeader(split: SplitSnapshot) {
    val range = if (split.firstUsedDate != null && split.lastUsedDate != null) {
        formatArchiveDateRange(split.firstUsedDate, split.lastUsedDate)
    } else {
        "not used this cycle"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
    ) {
        Text(split.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "${split.usageCount} ${if (split.usageCount == 1) "session" else "sessions"} - $range",
            color = TextMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ArchiveExerciseCard(exercise: ExerciseSnapshot) {
    val weighted = !exercise.isBodyweight && exercise.endE1rm != null
    val trend = if (weighted) {
        exercise.sessions.map { it.bestE1rm ?: 0f }
    } else {
        exercise.sessions.map { it.volumeLbs.toFloat() }
    }
    val delta = if (weighted) {
        (exercise.endE1rm ?: 0f) - (exercise.startE1rm ?: 0f)
    } else {
        ((exercise.endVolumeLbs ?: 0L) - (exercise.startVolumeLbs ?: 0L)).toFloat()
    }
    val trendColor = when {
        delta > 0.5f -> AccentTeal
        delta < -0.5f -> ErrorRed
        else -> TextInactive
    }
    val headline = if (weighted) {
        "e1RM ${formatLbsShort(exercise.startE1rm)} -> ${formatLbsShort(exercise.endE1rm)} (${formatSignedLbs(delta)})"
    } else {
        "Volume ${"%,d".format(exercise.startVolumeLbs ?: 0L)} -> ${"%,d".format(exercise.endVolumeLbs ?: 0L)} lb"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundElevated),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(headline, color = trendColor, fontSize = 11.sp)
            }
            MiniSparkline(
                values = trend,
                color = trendColor,
                modifier = Modifier
                    .width(64.dp)
                    .height(22.dp),
            )
        }
    }
}

private fun formatLbsShort(value: Float?): String =
    if (value == null) "-" else "%.0f".format(value)
