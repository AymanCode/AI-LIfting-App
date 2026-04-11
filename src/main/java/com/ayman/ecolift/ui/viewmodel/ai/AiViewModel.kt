package com.ayman.ecolift.ui.viewmodel.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.ayman.ecolift.ai.IronMindAgent
import com.ayman.ecolift.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class AiMessage(
    val text: String,
    val isUser: Boolean,
    val toolCall: ToolCall? = null
)

data class ToolCall(
    val name: String,
    val parameters: JSONObject,
    val confirmed: Boolean = false
)

data class AiUiState(
    val messages: List<AiMessage> = emptyList(),
    val isThinking: Boolean = false,
    val userInput: String = "",
    val isModelLoaded: Boolean = false,
    val modelPathHint: String = "",
    val errorMessage: String? = null,
    val capturedImageUri: Uri? = null
)

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val agent = IronMindAgent(application)
    private val context = application
    private val database = AppDatabase.getInstance(application)
    private val setRepository = SetRepository(database)
    private val exerciseRepository = ExerciseRepository(database)
    private val workoutRepository = WorkoutRepository(database)

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    init {
        // Auto-attempt start if model might be there
        startAi()
    }

    private fun checkModelPresence() {
        viewModelScope.launch {
            val isLoaded = agent.isInitialized()
            val modelFile = File(context.filesDir, "gemma.bin")
            _uiState.value = _uiState.value.copy(
                isModelLoaded = isLoaded,
                modelPathHint = modelFile.absolutePath,
                messages = if (isLoaded && _uiState.value.messages.isEmpty()) {
                    listOf(AiMessage("IronMind is active. How can I help?", false))
                } else _uiState.value.messages
            )
        }
    }

    fun startAi() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            try {
                agent.initialize()
                checkModelPresence()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Inference Error: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isThinking = false)
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true, errorMessage = null)
            val success = withContext(Dispatchers.IO) {
                try {
                    val destination = File(context.filesDir, "gemma.bin")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            if (success) {
                startAi()
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to copy model. Ensure you have ~1.5GB of free space.")
            }
            _uiState.value = _uiState.value.copy(isThinking = false)
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(userInput = text)
    }

    fun onImageCaptured(uri: Uri) {
        _uiState.value = _uiState.value.copy(capturedImageUri = uri)
        // In a real vision setup, we'd process the image here. 
        // For now, we'll prompt the user to describe what they see.
        val msg = AiMessage("I've received the photo of the machine. Is this a Chest Press, Fly, or something else? I can suggest an alternative for your workout.", false)
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + msg)
    }

    fun sendMessage() {
        val input = _uiState.value.userInput
        if (input.isBlank()) return

        val userMsg = AiMessage(input, true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            userInput = "",
            isThinking = true
        )

        viewModelScope.launch {
            // Fetch recent context
            val today = LocalDate.now().toString()
            val recentSets = setRepository.getSetsForDate(today)
            val contextData = if (recentSets.isNotEmpty()) {
                val dataStrings = recentSets.map { set ->
                    val ex = exerciseRepository.getById(set.exerciseId)?.name ?: "Unknown"
                    "- $ex: ${set.weightLbs} lbs x ${set.reps}"
                }
                "Today's Log:\n" + dataStrings.joinToString("\n")
            } else {
                "No workouts logged today yet."
            }

            val response = agent.processInput(input, contextData)
            parseResponse(response)
        }
    }

    private fun parseResponse(jsonStr: String) {
        try {
            // Regex guardrail: Find the first { and last } to ignore conversational noise
            val match = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
            val cleanJson = match?.value ?: jsonStr

            val json = JSONObject(cleanJson)
            if (json.has("tool")) {
                val toolName = json.getString("tool")
                val params = json.getJSONObject("parameters")
                
                // Logic Guardrail: Sanitize parameters
                if (toolName == "log_set" || toolName == "update_set_log") {
                    val weight = params.optInt("weight", -1)
                    val reps = params.optInt("reps", -1)
                    if (reps > 100 || weight > 1000) { // Safety limit for a single set
                         _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + AiMessage("I detected some unusual numbers ($weight lbs x $reps reps). Please verify them below.", false),
                            isThinking = false
                        )
                    }
                }

                val toolCall = ToolCall(toolName, params)
                val aiMsg = AiMessage("I've prepared this action. Tap values to adjust them if needed.", false, toolCall)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMsg,
                    isThinking = false
                )
            } else if (json.has("message")) {
                val aiMsg = AiMessage(json.getString("message"), false)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMsg,
                    isThinking = false
                )
            }
        } catch (e: Exception) {
            val aiMsg = AiMessage("I understood your request but had trouble formatting the action. Could you try rephrasing?", false)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMsg,
                isThinking = false
            )
        }
    }

    fun confirmTool(toolCall: ToolCall) {
        viewModelScope.launch {
            val resultMessage = executeTool(toolCall)
            // Mark as confirmed in UI
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map { 
                    if (it.toolCall == toolCall) it.copy(toolCall = toolCall.copy(confirmed = true), text = resultMessage) else it 
                }
            )
        }
    }

    private suspend fun executeTool(toolCall: ToolCall): String {
        return try {
            when (toolCall.name) {
                "suggest_alternative" -> {
                    val currentExName = toolCall.parameters.optString("current_exercise", "")
                    val targetExName = toolCall.parameters.optString("target_machine", "")
                    
                    val currentEx = exerciseRepository.findExact(currentExName)
                    val lastSet = currentEx?.let { setRepository.getLastForDateAndExercise(LocalDate.now().toString(), it.id) }
                        ?: currentEx?.let { setRepository.getMostRecentBeforeDate(it.id, LocalDate.now().toString()) }

                    if (lastSet != null) {
                        val weight = lastSet.weightLbs
                        val reps = lastSet.reps
                        val estimated1RM = weight * (1 + reps / 30f)
                        // Machines usually feel ~15% lighter/heavier depending on pulley. 
                        // We suggest starting at 80% of estimated 1RM for safety on a new machine.
                        val suggestedWeight = (estimated1RM * 0.8f).toInt()
                        "Since you can't do $currentExName, try the $targetExName. Aim for $suggestedWeight lbs for 10-12 reps to match your usual intensity."
                    } else {
                        "Try the $targetExName. Since I don't have your $currentExName history yet, start with a light weight and aim for a RPE 8."
                    }
                }
                "update_set_log" -> {
                    val exerciseName = toolCall.parameters.getString("exercise")
                    val date = toolCall.parameters.optString("date", LocalDate.now().toString())
                    val weight = toolCall.parameters.optInt("weight", -1)
                    val reps = toolCall.parameters.optInt("reps", -1)
                    
                    val exercise = exerciseRepository.findExact(exerciseName) 
                        ?: return "Could not find exercise '$exerciseName'."
                    
                    val sets = setRepository.getSetsForDate(date).filter { it.exerciseId == exercise.id }
                    
                    if (sets.isNotEmpty()) {
                        val lastSet = sets.last()
                        val updatedSet = lastSet.copy(
                            weightLbs = if (weight != -1) weight else lastSet.weightLbs,
                            reps = if (reps != -1) reps else lastSet.reps
                        )
                        setRepository.updateSet(updatedSet)
                        "Updated your last set of $exerciseName on $date."
                    } else {
                        "No logs found for $exerciseName on $date."
                    }
                }
                "modify_cycle" -> {
                    val sessionIndex = toolCall.parameters.optInt("next_session_index", -1)
                    if (sessionIndex != -1) {
                        val type = sessionIndex - 1
                        val today = LocalDate.now().toString()
                        val assignedDay = workoutRepository.assignCycleSlot(today, type)
                        
                        // Clone previous if exists
                        val previousOccurrence = (assignedDay.cycleSlotOccurrence ?: 1) - 1
                        if (previousOccurrence > 0) {
                            val previousDay = workoutRepository.getPreviousOccurrenceDay(today, type, previousOccurrence)
                            if (previousDay != null) {
                                setRepository.cloneDay(previousDay.date, today)
                            }
                        }
                        "Split updated. Today is now Session $sessionIndex."
                    } else "Invalid session index."
                }
                "calculate_1rm" -> {
                    val w = toolCall.parameters.optInt("weight", 0)
                    val r = toolCall.parameters.optInt("reps", 0)
                    if (r > 0) {
                        val oneRM = w * (1 + r / 30f)
                        "Based on $w lbs for $r reps, your estimated 1RM is ${oneRM.toInt()} lbs."
                    } else "Need weight and reps to calculate."
                }
                else -> "Action executed."
            }
        } catch (e: Exception) {
            "Failed to execute: ${e.message}"
        }
    }
}
