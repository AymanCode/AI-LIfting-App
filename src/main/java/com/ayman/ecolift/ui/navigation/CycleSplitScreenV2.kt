package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import com.ayman.ecolift.ui.theme.bounceClick
import com.ayman.ecolift.ui.viewmodel.ArchiveCardUi
import com.ayman.ecolift.ui.viewmodel.SplitTabMode

data class SplitType(
    val id: Long,
    val name: String,
    val exerciseCount: Int = 0,
    val lastRunLabel: String = ""
)

@Composable
fun GymCalendarCard(
    gymDays: Set<LocalDate>,
    displayedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Previous Month",
                        tint = Color(0xFF171A1C)
                    )
                }
                Text(
                    text = "${displayedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${displayedMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF171A1C)
                )
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Next Month",
                        tint = Color(0xFF171A1C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Days of week header
            val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF66706E),
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid computation
            val daysInGrid = remember(displayedMonth) { buildGymCalendarGrid(displayedMonth) }

            val today = LocalDate.now()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (col in 0 until 7) {
                            val date = daysInGrid[row * 7 + col]
                            val isCurrentMonth = date.year == displayedMonth.year && date.monthValue == displayedMonth.monthValue
                            val isGymDay = isGymCalendarDateWorked(date, gymDays)
                            val isToday = date == today

                            if (isGymDay && isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color(0xFF149C8A), CircleShape)
                                        .background(Color(0xFF171A1C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Color(0xFF149C8A), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = Color(0xFF149C8A),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (isGymDay) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF171A1C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentMonth) Color(0xFF171A1C) else Color(0xFF66706E).copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Summary Footer
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${countGymDaysInMonth(gymDays, displayedMonth)} workouts this month",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF171A1C).copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SplitCycleToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Loop,
                contentDescription = null,
                tint = Color(0xFF171A1C),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Split Cycle",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF171A1C)
                )
                Text(
                    text = "Pre-load exercises based on your rotation",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF66706E)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF149C8A)
                )
            )
        }
    }
}

@Composable
fun TodaySplitHeroCard(
    splitName: String,
    dayLabel: String,
    exerciseCount: Int,
    lastRunLabel: String,
    onLoadWorkout: () -> Unit,
    onEditSplit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF149C8A))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF149C8A),
                    letterSpacing = 0.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF66706E)
                )
            }
            
            Text(
                text = splitName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A1C),
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                text = "$exerciseCount exercises · $lastRunLabel",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF66706E),
                modifier = Modifier.padding(top = 4.dp)
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color(0xFF171A1C).copy(alpha = 0.1f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLoadWorkout,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF149C8A),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Start Workout",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                OutlinedButton(
                    onClick = onEditSplit,
                    modifier = Modifier
                        .width(96.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF149C8A)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF149C8A)
                    )
                ) {
                    Text(text = "Edit", color = Color(0xFF149C8A), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun RotationCycleRow(
    splits: List<String>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF171A1C).copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ROTATION",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF66706E),
                modifier = Modifier.padding(bottom = 10.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                splits.forEachIndexed { index, name ->
                    val isActive = index == currentIndex
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isActive) Color(0xFF171A1C) else Color.Transparent)
                            .border(
                                BorderStroke(1.dp, if (isActive) Color.Transparent else Color(0xFF171A1C).copy(alpha = 0.2f)),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) Color.White else Color(0xFF171A1C).copy(alpha = 0.45f)
                        )
                    }
                    
                    if (index < splits.size - 1) {
                        Text(
                            text = "→",
                            color = Color(0xFF66706E),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            
            Text(
                text = "Cycle repeats after ${splits.size} days",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF66706E),
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun SplitListItem(
    split: SplitType,
    isToday: Boolean,
    exerciseCount: Int,
    lastRunLabel: String,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = Color(0xFF171A1C).copy(alpha = 0.35f),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 12.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = split.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF171A1C)
                    )
                    
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF149C8A).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFF149C8A).copy(alpha = 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "TODAY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF149C8A)
                            )
                        }
                    }
                }
                
                Text(
                    text = "$exerciseCount exercises · $lastRunLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF66706E),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF66706E),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MySplitsSection(
    splits: List<SplitType>,
    currentSplitIndex: Int,
    splitCycleEnabled: Boolean,
    onAddSplit: () -> Unit,
    onSplitOptions: (SplitType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MY SPLITS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF66706E)
            )
            TextButton(
                onClick = onAddSplit,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color(0xFF149C8A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp).bounceClick(onClick = onAddSplit)
            ) {
                Text("+ Add Split", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }

        // List or Empty State
        if (splits.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.FitnessCenter,
                    contentDescription = null,
                    tint = Color(0xFF149C8A),
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No splits yet",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF171A1C)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap '+ Add Split' above to create your first workout type",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF66706E),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = "Hold to reorder",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF66706E).copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                splits.forEachIndexed { index, split ->
                    val isToday = splitCycleEnabled && index == currentSplitIndex
                    SplitListItem(
                        split = split,
                        isToday = isToday,
                        exerciseCount = split.exerciseCount,
                        lastRunLabel = split.lastRunLabel,
                        onOptionsClick = { onSplitOptions(split) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleSplitScreen(
    splits: List<SplitType>,
    gymDays: Set<LocalDate>,
    splitCycleEnabled: Boolean,
    currentSplitIndex: Int,
    onToggleSplitCycle: (Boolean) -> Unit,
    onLoadWorkout: () -> Unit,
    onEditSplit: (SplitType) -> Unit,
    onAddSplit: () -> Unit,
    onSplitOptions: (SplitType) -> Unit,
    tabMode: SplitTabMode = SplitTabMode.CURRENT,
    onTabModeChange: (SplitTabMode) -> Unit = {},
    archives: List<ArchiveCardUi> = emptyList(),
    onOpenArchive: (Long) -> Unit = {},
    onArchiveCurrentCycle: () -> Unit = {},
) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFFF4F6F5)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SplitTabToggle(
                selected = tabMode,
                onSelect = onTabModeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (tabMode) {
                    SplitTabMode.CURRENT -> {
                        item {
                            GymCalendarCard(
                                gymDays = gymDays,
                                displayedMonth = displayedMonth,
                                onPreviousMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                                onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) }
                            )
                        }
                        item {
                            SplitCycleToggleCard(
                                enabled = splitCycleEnabled,
                                onToggle = onToggleSplitCycle
                            )
                        }

                        if (splits.isNotEmpty() && splitCycleEnabled) {
                            item {
                                val currentSplit = splits.getOrNull(currentSplitIndex)
                                if (currentSplit != null) {
                                    TodaySplitHeroCard(
                                        splitName = currentSplit.name,
                                        dayLabel = "Day ${currentSplitIndex + 1} of ${splits.size}",
                                        exerciseCount = currentSplit.exerciseCount,
                                        lastRunLabel = currentSplit.lastRunLabel,
                                        onLoadWorkout = onLoadWorkout,
                                        onEditSplit = { onEditSplit(currentSplit) }
                                    )
                                }
                            }
                            item {
                                RotationCycleRow(
                                    splits = splits.map { it.name },
                                    currentIndex = currentSplitIndex
                                )
                            }
                        }

                        item {
                            MySplitsSection(
                                splits = splits,
                                currentSplitIndex = currentSplitIndex,
                                splitCycleEnabled = splitCycleEnabled,
                                onAddSplit = onAddSplit,
                                onSplitOptions = onSplitOptions
                            )
                        }
                    }

                    SplitTabMode.ARCHIVE -> {
                        item {
                            Button(
                                onClick = onArchiveCurrentCycle,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF149C8A))
                            ) {
                                Text(
                                    text = "Archive current cycle",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (archives.isEmpty()) {
                            item {
                                Text(
                                    text = "No archived cycles yet. Archive your current cycle to snapshot its progress.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF66706E),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = archives,
                                key = { _, card -> card.id },
                                contentType = { _, _ -> "archiveCard" }
                            ) { _, card ->
                                ArchiveListCard(card = card, onClick = { onOpenArchive(card.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTabToggle(
    selected: SplitTabMode,
    onSelect: (SplitTabMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SplitTabMode.values().forEach { mode ->
                val isSelected = mode == selected
                TextButton(
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) {
                            Color(0xFF149C8A).copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (isSelected) Color(0xFF171A1C) else Color(0xFF66706E)
                    )
                ) {
                    Text(
                        text = when (mode) {
                            SplitTabMode.CURRENT -> "Current"
                            SplitTabMode.ARCHIVE -> "Archive"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveListCard(card: ArchiveCardUi, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = card.name.ifBlank { "Untitled cycle" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A1C)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = card.dateRangeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF66706E)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${card.splitCount} splits · ${card.sessionCount} sessions · ${"%,d".format(card.totalVolumeLbs)} lb",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF171A1C)
            )
        }
    }
}

internal fun buildGymCalendarGrid(displayedMonth: YearMonth): List<LocalDate> {
    val firstOfMonth = displayedMonth.atDay(1)
    val daysInMonth = displayedMonth.lengthOfMonth()
    val firstDayOfWeek = firstOfMonth.dayOfWeek.value % 7
    val grid = mutableListOf<LocalDate>()

    val previousMonth = displayedMonth.minusMonths(1)
    for (i in firstDayOfWeek downTo 1) {
        grid.add(previousMonth.atEndOfMonth().minusDays((i - 1).toLong()))
    }

    for (i in 1..daysInMonth) {
        grid.add(displayedMonth.atDay(i))
    }

    val nextMonth = displayedMonth.plusMonths(1)
    var nextDay = 1
    while (grid.size < 42) {
        grid.add(nextMonth.atDay(nextDay++))
    }

    return grid
}

internal fun isGymCalendarDateWorked(date: LocalDate, gymDays: Set<LocalDate>): Boolean =
    date in gymDays

internal fun countGymDaysInMonth(gymDays: Set<LocalDate>, displayedMonth: YearMonth): Int =
    gymDays.count { YearMonth.from(it) == displayedMonth }

@Preview(showBackground = true)
@Composable
fun CycleSplitScreenPreview() {
    MaterialTheme {
        CycleSplitScreen(
            splits = listOf(
                SplitType(1, "Push", 6, "2 days ago"),
                SplitType(2, "Pull", 5, "Yesterday"),
                SplitType(3, "Legs", 4, "Never run")
            ),
            gymDays = setOf(1, 3, 5, 8, 10, 12, 15, 17, 20, 22).mapTo(mutableSetOf()) {
                YearMonth.now().atDay(it)
            },
            splitCycleEnabled = true,
            currentSplitIndex = 0,
            onToggleSplitCycle = {},
            onLoadWorkout = {},
            onEditSplit = {},
            onAddSplit = {},
            onSplitOptions = {}
        )
    }
}
