package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen() {
    var cycleEnabled by remember { mutableStateOf(true) }
    val cycleDays = remember { mutableStateListOf("Day 1", "Day 2", "Day 3") }
    var editorOpen by remember { mutableStateOf(false) }
    var draftDays by remember { mutableStateOf(cycleDays.toList()) }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text("Edit Split") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    draftDays.forEachIndexed { index, name ->
                        OutlinedTextField(
                            value = name,
                            onValueChange = { updated ->
                                draftDays = draftDays.toMutableList().also { it[index] = updated }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Day ${index + 1}") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        cycleDays.clear()
                        cycleDays.addAll(draftDays)
                        editorOpen = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editorOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = "Workout Cycle",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Cycle",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = cycleEnabled,
                            onCheckedChange = { cycleEnabled = it }
                        )
                    }
                    Text(
                        text = if (cycleEnabled) {
                            "Automate your routine by defining your training split."
                        } else {
                            "Enable cycle mode to get routine suggestions."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cycleEnabled) {
                        Button(
                            onClick = {
                                draftDays = cycleDays.toList()
                                editorOpen = true
                            }
                        ) {
                            Text("Edit Split")
                        }
                    }
                }
            }
        }

        itemsIndexed(cycleDays) { index, day ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Day ${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = day,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (index == 0) "Next" else "Ready",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
