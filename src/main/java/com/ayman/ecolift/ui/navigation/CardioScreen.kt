package com.ayman.ecolift.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.viewmodel.CardioUiState
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardioScreen(
    viewModel: CardioViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.handleOcrCaptureResult(success)
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onHealthPermissionsResult(granted)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Cardio",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.ink,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkSubtle,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        item {
            CardioSummaryRow(state = state)
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(palette, RoundedCornerShape(18.dp), strong = true),
                color = Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val uri = viewModel.createOcrCaptureUri()
                                cameraLauncher.launch(uri)
                            },
                            enabled = !state.isOcrRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentStrong.copy(alpha = 0.92f),
                                contentColor = palette.pageBottom,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Camera", modifier = Modifier.padding(start = 8.dp))
                        }
                        Button(
                            onClick = viewModel::openManualEntry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.glassFillStrong,
                                contentColor = palette.ink,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Manual", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    val ocrMessage = state.ocrMessage
                    if (ocrMessage != null) {
                        Text(
                            text = ocrMessage,
                            color = palette.inkMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            HealthConnectCard(
                state = state,
                onConnect = {
                    if (state.healthConnectAvailable && !state.healthConnectPermissionGranted) {
                        healthPermissionLauncher.launch(viewModel.healthConnectPermissions)
                    } else {
                        viewModel.refreshHealthConnectCalories()
                    }
                },
            )
        }

        if (state.sessions.isNotEmpty()) {
            item {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(state.sessions, key = { it.id }) { session ->
                CardioSessionRow(
                    session = session,
                    onDelete = { viewModel.deleteSession(session.id) },
                )
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
        }
    }

    if (state.showEntrySheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeEntrySheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent,
            contentColor = palette.ink,
        ) {
            CardioEntrySheet(
                state = state,
                onActivityChange = viewModel::updateActivityType,
                onDurationChange = viewModel::updateDuration,
                onDistanceChange = viewModel::updateDistance,
                onCaloriesChange = viewModel::updateCalories,
                onHeartRateChange = viewModel::updateHeartRate,
                onNotesChange = viewModel::updateNotes,
                onSave = viewModel::saveEntry,
                onCancel = viewModel::closeEntrySheet,
            )
        }
    }
}

@Composable
private fun CardioSummaryRow(state: CardioUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricPill(
            label = "Logged",
            value = state.todayLoggedCalories.takeIf { it > 0 }?.let { "$it cal" } ?: "0 cal",
            icon = Icons.Filled.LocalFireDepartment,
            modifier = Modifier.weight(1f),
        )
        MetricPill(
            label = "Health",
            value = state.healthConnectCaloriesToday?.let { "$it cal" } ?: "—",
            icon = Icons.Filled.Favorite,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = modifier.glassPanel(palette, RoundedCornerShape(16.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(palette.accentStrong.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = palette.accentStrong, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(label, color = palette.inkSubtle, style = MaterialTheme.typography.labelSmall)
                Text(value, color = palette.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HealthConnectCard(
    state: CardioUiState,
    onConnect: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(16.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Health Connect", color = palette.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.healthConnectMessage
                        ?: if (state.healthConnectPermissionGranted) "Today’s calories are available." else "Read today’s burned calories.",
                    color = palette.inkSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onConnect) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (state.healthConnectPermissionGranted) "Refresh" else "Connect", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun CardioSessionRow(
    session: CardioSession,
    onDelete: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(14.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.activityType.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    color = palette.ink,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        session.durationSec?.let(::formatDuration),
                        session.distanceM?.let { "%.2f mi".format(it / 1609.344) },
                        session.calories?.let { "$it cal" },
                        session.avgHeartRate?.let { "$it bpm" },
                    ).joinToString(" · "),
                    color = palette.inkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = when (session.source) {
                    CardioSessionSource.OCR -> "OCR"
                    CardioSessionSource.HEALTH_CONNECT -> "HC"
                    else -> "Manual"
                },
                color = palette.inkSubtle,
                style = MaterialTheme.typography.labelSmall,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete cardio session", tint = palette.inkSubtle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioEntrySheet(
    state: CardioUiState,
    onActivityChange: (CardioActivityType) -> Unit,
    onDurationChange: (String) -> Unit,
    onDistanceChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onHeartRateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strong = true)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (state.entrySource == "ocr") "Confirm cardio" else "Log cardio",
            color = palette.ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardioActivityType.entries.forEach { type ->
                ActivityChip(
                    label = activityLabel(type),
                    selected = type == state.selectedActivityType,
                    onClick = { onActivityChange(type) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryField("Time", state.durationText, onDurationChange, "25:00", Modifier.weight(1f))
            EntryField("Miles", state.distanceText, onDistanceChange, "2.50", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryField("Calories", state.caloriesText, onCaloriesChange, "250", Modifier.weight(1f), KeyboardType.Number)
            EntryField("HR", state.heartRateText, onHeartRateChange, "140", Modifier.weight(1f), KeyboardType.Number)
        }
        OutlinedTextField(
            value = state.notesText,
            onValueChange = onNotesChange,
            label = { Text("Notes") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentStrong,
                    contentColor = palette.pageBottom,
                ),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun ActivityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) palette.accentStrong.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) palette.glassStrokeStrong else palette.glassStroke),
    ) {
        Text(
            text = label,
            color = if (selected) palette.accentStrong else palette.inkMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EntryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = fieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalGlassPalette.current.accentStrong,
    unfocusedBorderColor = LocalGlassPalette.current.glassStroke,
    focusedLabelColor = LocalGlassPalette.current.accentStrong,
    unfocusedLabelColor = LocalGlassPalette.current.inkSubtle,
    focusedTextColor = LocalGlassPalette.current.ink,
    unfocusedTextColor = LocalGlassPalette.current.ink,
    cursorColor = LocalGlassPalette.current.accentStrong,
)

private fun activityLabel(type: CardioActivityType): String = when (type) {
    CardioActivityType.RUN -> "Run"
    CardioActivityType.BIKE -> "Bike"
    CardioActivityType.ROW -> "Row"
    CardioActivityType.SWIM -> "Swim"
    CardioActivityType.WALK -> "Walk"
    CardioActivityType.ELLIPTICAL -> "Elliptical"
    CardioActivityType.STAIR_CLIMBER -> "Stairs"
    CardioActivityType.OTHER -> "Other"
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
