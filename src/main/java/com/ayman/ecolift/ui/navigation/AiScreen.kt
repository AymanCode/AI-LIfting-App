package com.ayman.ecolift.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.ai.AiMessage
import com.ayman.ecolift.ui.viewmodel.ai.AiViewModel
import com.ayman.ecolift.ui.viewmodel.ai.ToolCall
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(viewModel: AiViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            uiState.capturedImageUri?.let { viewModel.onImageCaptured(it) }
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "machine_capture.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            viewModel.onImageCaptured(uri)
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            // Handle provider error
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("IronMind AI", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.isModelLoaded) {
                ModelMissingView(
                    onPickFile = { filePickerLauncher.launch("*/*") },
                    isThinking = uiState.isThinking,
                    errorMessage = uiState.errorMessage
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.messages) { message ->
                        ChatMessage(message, onConfirm = { viewModel.confirmTool(it) })
                    }
                    
                    if (uiState.isThinking) {
                        item {
                            ThinkingIndicator()
                        }
                    }
                }

                ChatInput(
                    value = uiState.userInput,
                    onValueChange = { viewModel.onInputChange(it) },
                    onSend = { viewModel.sendMessage() },
                    onCameraClick = { launchCamera() }
                )
            }
        }
    }
}

@Composable
private fun ModelMissingView(onPickFile: () -> Unit, isThinking: Boolean, errorMessage: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Text(
                "IronMind is offline",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "To enable AI features, you need to provide a compatible model file (e.g., .bin or .litertlm from Hugging Face or Kaggle).",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            
            if (isThinking) {
                CircularProgressIndicator()
                Text("Setting up model...", style = MaterialTheme.typography.labelSmall)
            } else {
                Button(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Select Model File")
                }
            }
        }
    }
}

@Composable
private fun ChatMessage(message: AiMessage, onConfirm: (ToolCall) -> Unit) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bgColor,
            shape = shape,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                message.toolCall?.let { tool ->
                    Spacer(Modifier.height(12.dp))
                    ToolPreviewCard(tool, onConfirm)
                }
            }
        }
    }
}

@Composable
private fun ToolPreviewCard(tool: ToolCall, onConfirm: (ToolCall) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = when (tool.name) {
                "update_set_log" -> "Update Workout Log"
                "modify_cycle" -> "Change Workout Cycle"
                "calculate_1rm" -> "Calculate 1RM"
                "suggest_alternative" -> "Suggest Alternative"
                else -> "App Action"
            }
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            
            // Param summary
            val summary = when (tool.name) {
                "update_set_log" -> {
                    val exercise = tool.parameters.optString("exercise", "Unknown")
                    val weight = tool.parameters.optInt("weight", -1)
                    val reps = tool.parameters.optInt("reps", -1)
                    "Update $exercise: " + listOfNotNull(
                        if (weight != -1) "$weight lbs" else null,
                        if (reps != -1) "$reps reps" else null
                    ).joinToString(", ")
                }
                "suggest_alternative" -> {
                    val current = tool.parameters.optString("current_exercise", "Current")
                    val target = tool.parameters.optString("target_machine", "Target")
                    "Switch $current to $target"
                }
                else -> tool.parameters.toString()
            }
            Text(summary, style = MaterialTheme.typography.bodySmall)
            
            if (!tool.confirmed) {
                Button(
                    onClick = { onConfirm(tool) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm Action")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(4.dp))
                    Text("Executed", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("IronMind is thinking...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCameraClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onCameraClick) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Take photo",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask IronMind anything...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            FloatingActionButton(
                onClick = onSend,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
