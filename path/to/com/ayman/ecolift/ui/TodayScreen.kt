package com.ayman.ecolift.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayman.ecolift.R
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.ui.navigation.TodayScreen.Companion.totalSetCount

@Composable
fun TodayScreen(
    navController: NavController = rememberNavController(),
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    setRepository: SetRepository
) {
    val viewModel by remember { ViewModelProvider(navController, TodayViewModel.Factory(exerciseRepository, workoutRepository, setRepository)).get(TodayViewModel::class.java) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today)) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Robot, contentDescription = "Quick log")
                    }
                    TextButton(onClick = { viewModel.endWorkout() }) {
                        Text("End", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(it) },
        content = { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.today_date), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = {}) {}
                    TextButton(onClick = {}) { Text("End", fontWeight = FontWeight.Bold) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchChange(it) },
                    placeholder = { Text(stringResource(R.string.search_or_add_exercise)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.searchQuery.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.searchResults.forEach { exercise ->
                            Chip(
                                onClick = { viewModel.selectExercise(exercise) },
                                label = { Text(exercise.canonicalName) }
                            )
                        }

                        if (uiState.showCreateOption) {
                            Chip(
                                onClick = { viewModel.createAndSelectExercise(uiState.searchQuery.trim()) },
                                label = { Text("+ Create ${uiState.searchQuery}") }
                            )
                        }
                    }
                } else if (uiState.recentExercises.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Chip(
                            onClick = {},
                            label = { Text("Recent") }
                        )

                        uiState.recentExercises.forEach { exercise ->
                            Chip(
                                onClick = { viewModel.selectExercise(exercise) },
                                label = { Text(exercise.canonicalName) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.activeExercise != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(uiState.activeExercise?.name ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.clearActiveExercise() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1.0f)
                            ) {
                                Text("Weight", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("${uiState.weight.toInt()} lb", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            }

                            Column(
                                modifier = Modifier.weight(1.0f)
                            ) {
                                Text("Reps", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("${uiState.reps}", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.logSet() },
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("LOG SET", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.groupedSets.isEmpty()) {
                    Text(
                        "No sets logged yet. Search for an exercise above.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.centerHorizontally()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        uiState.groupedSets.forEach { group ->
                            item {
                                Text(group.exercise.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))

                                HorizontalFlow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    group.sets.forEach { set ->
                                        Chip(
                                            onClick = { viewModel.deleteSet(set.id) },
                                            label = { Text("${set.weightLb.toInt()} x ${set.reps}") }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    )
}
