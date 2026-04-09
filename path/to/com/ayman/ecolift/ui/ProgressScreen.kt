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
import com.ayman.ecolift.ui.navigation.ProgressScreen.Companion.totalSetCount

@Composable
fun ProgressScreen(
    navController: NavController = rememberNavController(),
    exerciseRepository: ExerciseRepository,
    setRepository: SetRepository,
    workoutRepository: WorkoutRepository
) {
    val viewModel by remember { ViewModelProvider(navController, ProgressViewModel.Factory(exerciseRepository, setRepository, workoutRepository)).get(ProgressViewModel::class.java) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ExposedDropdownMenuBox(
                selectedValue = uiState.selectedExerciseId?.let { exerciseRepository.getById(it)?.canonicalName },
                onValueChange = { newSelected ->
                    viewModel.selectExercise(newSelected?.toLongOrNull() ?: 0L)
                }
            ) {
                DropdownMenuItem(
                    text = { Text("Select Exercise") },
                    onClick = {}
                )
                uiState.allExercises.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.canonicalName) },
                        onClick = { viewModel.selectExercise(exercise.id) }
                    )
                }
            }
        },
        content = { padding ->
            Column(modifier = Modifier.padding(padding)) {
                if (uiState.dataPoints.size < 2) {
                    Text(
                        "Not enough data yet — log a few more sessions with this exercise.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.centerHorizontally()
                    )
                } else {
                    val chart1Data = uiState.dataPoints.map { it.dateEpochDay to it.maxEstimated1RM }
                    val chart2Data = uiState.dataPoints.map { it.dateEpochDay to it.totalVolume }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CartesianChartHost {
                            LineCartesianLayer(
                                modelProducer = CartesianChartModelProducer(chart1Data, "Estimated 1RM (lb)")
                            )
                        }
                        Text("Estimated 1RM (lb)", fontWeight = FontWeight.Bold)

                        CartesianChartHost {
                            LineCartesianLayer(
                                modelProducer = CartesianChartModelProducer(chart2Data, "Total Volume (lb)")
                            )
                        }
                        Text("Total Volume (lb)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}
