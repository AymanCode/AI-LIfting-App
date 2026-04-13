package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.theme.AccentTeal
import com.ayman.ecolift.ui.theme.AccentTeal07
import com.ayman.ecolift.ui.theme.AccentTeal10
import com.ayman.ecolift.ui.theme.AccentTeal12
import com.ayman.ecolift.ui.theme.AccentTeal18
import com.ayman.ecolift.ui.theme.AccentTeal35
import com.ayman.ecolift.ui.theme.BackgroundElevated
import com.ayman.ecolift.ui.theme.BackgroundPrimary
import com.ayman.ecolift.ui.theme.BackgroundSurface
import com.ayman.ecolift.ui.theme.BorderDefault
import com.ayman.ecolift.ui.theme.BorderSubtle
import com.ayman.ecolift.ui.theme.TextMuted
import com.ayman.ecolift.ui.theme.TextPrimary
import com.ayman.ecolift.ui.theme.TextSecondary
import com.ayman.ecolift.ui.viewmodel.SplitViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(viewModel: SplitViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var newSlotName by remember { mutableStateOf("") }

    if (showAddDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Add Workout Type", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = newSlotName,
                        onValueChange = { newSlotName = it },
                        placeholder = { Text("e.g. Push, Pull, Legs") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.addSlot(newSlotName)
                                newSlotName = ""
                                showAddDialog = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AccentTeal)
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Cycle / Split",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "WORKOUT ROTATION SETTINGS",
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Enable split cycle",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Pre-load exercises based on your rotation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
                Switch(
                    checked = uiState.isActive,
                    onCheckedChange = { viewModel.toggleActive() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentTeal,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = BackgroundElevated,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedBorderColor = BorderSubtle
                    )
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MY WORKOUTS",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "+ ADD TYPE",
                    modifier = Modifier.clickable { showAddDialog = true },
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentTeal,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (uiState.slots.isEmpty()) {
                Text(
                    "No workout types added yet. Add 'Push' or 'Legs' to start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            uiState.slots.forEach { slot ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(slot.label, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { viewModel.deleteSlot(slot.type.toLong()) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CounterPill(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
