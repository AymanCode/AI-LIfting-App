package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.ai.AiActionPreview
import com.ayman.ecolift.ai.AiConversationTurn
import com.ayman.ecolift.ai.AiExecutionResult
import com.ayman.ecolift.ai.AiRuntimeContext
import com.ayman.ecolift.ai.AiToolCall
import com.ayman.ecolift.ai.AiToolExecutor
import com.ayman.ecolift.ai.GemmaAgent
import com.ayman.ecolift.ai.GymContextManifestRepository
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.PendingReviewRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.TempSessionSwapRepository
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutRepository
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val exerciseRepository = ExerciseRepository(database)
    private val workoutRepository = WorkoutRepository(database)
    private val setRepository = SetRepository(database)
    private val tempSessionSwapRepository = TempSessionSwapRepository(database)
    private val pendingReviewRepository = PendingReviewRepository(database)
    private val manifestRepository = GymContextManifestRepository(application, database)
    private val gemmaAgent = GemmaAgent(application)
    private val toolExecutor = AiToolExecutor(
        exerciseRepository = exerciseRepository,
        setRepository = setRepository,
        workoutRepository = workoutRepository,
        tempSessionSwapRepository = tempSessionSwapRepository,
        manifestRepository = manifestRepository,
    )

    private val today = WorkoutDates.today()
    private val input = MutableStateFlow("")
    private val messages = MutableStateFlow(
        listOf(
            AiMessageUi(
                id = 1L,
                isUser = false,
                text = "IronMind is ready to parse local workout commands. Ask for a correction, a split override, or a quick 1RM estimate.",
            )
        )
    )
    private val pendingToolCall = MutableStateFlow<AiToolCall?>(null)
    private val pendingPreview = MutableStateFlow<AiActionPreview?>(null)
    private val selectedImageUri = MutableStateFlow<Uri?>(null)
    private val isWorking = MutableStateFlow(false)
    private val modelStatus = MutableStateFlow(gemmaAgent.getStatus())
    private var nextMessageId = 2L

    private val currentDay = workoutRepository.observeWorkoutDay(today)
    private val currentSets = setRepository.observeSetsForDate(today)
    private val workoutDays = workoutRepository.observeAllWorkoutDays()
    private val allSets = setRepository.allSets

    private val coreRuntimeSnapshot = combine(
        workoutRepository.cycle,
        currentDay,
        currentSets,
        exerciseRepository.exercises,
        pendingReviewRepository.unresolved,
    ) { cycle, workoutDay, setsForToday, exercises, pendingReviews ->
        val totalSets = setsForToday.size
        val completedSets = setsForToday.count { it.completed }
        val completion = if (totalSets == 0) 0 else ((completedSets * 100f) / totalSets).toInt()
        val summary = when {
            totalSets == 0 -> "No sets logged today yet."
            else -> "$completedSets of $totalSets sets marked complete today."
        }
        val nextLabel = cycle.nextSessionType?.let(::cycleTypeLabel)
            ?: workoutDay?.cycleSlotType?.let { slotType ->
                if (cycle.numTypes > 0) cycleTypeLabel((slotType + 1) % cycle.numTypes) else null
            }
        CoreRuntimeSnapshot(
            cycle = cycle,
            currentDay = workoutDay,
            currentSets = setsForToday,
            exercises = exercises,
            pendingReviewCount = pendingReviews.size,
            nextSessionLabel = nextLabel,
            currentSessionCompletionPercent = completion,
            currentSessionSummary = summary,
        )
    }

    private val runtimeSnapshot: StateFlow<AiRuntimeContext> = combine(
        coreRuntimeSnapshot,
        workoutDays,
        allSets,
    ) { core, workoutDays, allSets ->
        AiRuntimeContext(
            today = today,
            cycleActive = core.cycle.isActive,
            cycleNumTypes = core.cycle.numTypes,
            nextSessionLabel = core.nextSessionLabel,
            currentSessionCompletionPercent = core.currentSessionCompletionPercent,
            currentSessionSummary = core.currentSessionSummary,
            pendingReviewCount = core.pendingReviewCount,
            availableExercises = core.exercises.map { it.name },
            lastSessionJson = buildLastSessionJson(today, workoutDays, allSets, core.exercises),
            currentTargetSessionJson = buildCurrentTargetSessionJson(
                today = today,
                cycle = core.cycle,
                currentDay = core.currentDay,
                currentSets = core.currentSets,
                workoutDays = workoutDays,
                allSets = allSets,
                exercises = core.exercises,
            ),
            manifestJson = manifestRepository.buildManifest(today).toString(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiRuntimeContext(
            today = today,
            cycleActive = false,
            cycleNumTypes = 3,
            nextSessionLabel = null,
            currentSessionCompletionPercent = 0,
            currentSessionSummary = "No sets logged today yet.",
            pendingReviewCount = 0,
            availableExercises = emptyList(),
            lastSessionJson = "{}",
            currentTargetSessionJson = "{}",
            manifestJson = "{}",
        ),
    )

    private val composerPresentationState = combine(
        input,
        pendingPreview,
        selectedImageUri,
        isWorking,
        modelStatus,
    ) { inputValue, preview, imageUri, working, status ->
        ComposerPresentationState(
            input = inputValue,
            pendingPreview = preview,
            attachedImageLabel = imageUri?.lastPathSegment?.substringAfterLast('/') ?: imageUri?.toString(),
            isWorking = working,
            statusHeadline = status.headline,
            statusDetail = status.detail,
            statusPath = status.modelPath,
            isModelReady = status.isReady,
        )
    }

    private val presentationState = combine(
        messages,
        composerPresentationState,
    ) { messageList, composer ->
        PartialAiPresentationState(
            input = composer.input,
            messages = messageList,
            pendingPreview = composer.pendingPreview,
            attachedImageLabel = composer.attachedImageLabel,
            isWorking = composer.isWorking,
            statusHeadline = composer.statusHeadline,
            statusDetail = composer.statusDetail,
            statusPath = composer.statusPath,
            isModelReady = composer.isModelReady,
        )
    }

    val uiState: StateFlow<AiUiState> = combine(
        presentationState,
        runtimeSnapshot,
    ) { presentation, runtime ->
        AiUiState(
            statusHeadline = presentation.statusHeadline,
            statusDetail = presentation.statusDetail,
            modelPath = presentation.statusPath,
            isModelReady = presentation.isModelReady,
            messages = presentation.messages,
            shortcuts = buildShortcuts(runtime),
            input = presentation.input,
            attachedImageLabel = presentation.attachedImageLabel,
            pendingAction = presentation.pendingPreview?.let {
                AiPendingActionUi(
                    title = it.title,
                    detail = it.detail,
                    confirmLabel = it.confirmLabel,
                )
            },
            isWorking = presentation.isWorking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiUiState(),
    )

    fun updateInput(value: String) {
        input.value = value
    }

    fun applyShortcut(prompt: String) {
        input.value = prompt
    }

    fun attachImage(uri: Uri?) {
        selectedImageUri.value = uri
    }

    fun clearAttachedImage() {
        selectedImageUri.value = null
    }

    fun sendMessage() {
        val prompt = input.value.trim()
        if (prompt.isEmpty()) return
        input.value = ""
        appendMessage(isUser = true, text = prompt)
        pendingToolCall.value = null
        pendingPreview.value = null
        val attachedImage = selectedImageUri.value
        modelStatus.value = gemmaAgent.getStatus()
        if (!modelStatus.value.isReady) {
            appendMessage(
                isUser = false,
                text = buildMissingModelMessage(modelStatus.value.modelPath),
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            isWorking.value = true
            val runtime = runtimeSnapshot.value
            val history = messages.value.map { message ->
                AiConversationTurn(
                    role = if (message.isUser) "user" else "assistant",
                    message = message.text,
                )
            }
            val result = gemmaAgent.respond(prompt, history, runtime, attachedImage)
            result.onSuccess { output ->
                selectedImageUri.value = null
                if (output.assistantMessage.isNotBlank()) {
                    appendMessage(isUser = false, text = output.assistantMessage)
                }
                val toolCall = output.toolCall
                when {
                    toolCall == null -> Unit
                    toolCall.requiresConfirmation -> {
                        pendingToolCall.value = toolCall
                        pendingPreview.value = toolExecutor.preview(toolCall)
                    }
                    else -> {
                        val execution = toolExecutor.execute(toolCall)
                        appendToolExecution(execution)
                    }
                }
            }.onFailure { error ->
                appendMessage(
                    isUser = false,
                    text = error.message ?: "Gemma failed to produce a valid local action.",
                    isError = true,
                )
            }
            isWorking.value = false
        }
    }

    fun confirmPendingAction() {
        val toolCall = pendingToolCall.value ?: return
        pendingToolCall.value = null
        pendingPreview.value = null
        viewModelScope.launch {
            isWorking.value = true
            val execution = toolExecutor.execute(toolCall)
            appendToolExecution(execution)
            isWorking.value = false
        }
    }

    fun dismissPendingAction() {
        pendingToolCall.value = null
        pendingPreview.value = null
        appendMessage(
            isUser = false,
            text = "No database changes were made.",
        )
    }

    private fun appendToolExecution(result: AiExecutionResult) {
        appendMessage(
            isUser = false,
            text = "${result.title}: ${result.detail}",
        )
        pendingToolCall.value = result.pendingToolCall
        pendingPreview.value = result.pendingPreview
        modelStatus.value = gemmaAgent.getStatus()
    }

    private fun appendMessage(isUser: Boolean, text: String, isError: Boolean = false) {
        messages.update { current ->
            current + AiMessageUi(
                id = nextMessageId++,
                isUser = isUser,
                text = text,
                isError = isError,
            )
        }
    }

    private fun buildShortcuts(runtime: AiRuntimeContext): List<AiShortcutUi> {
        val shortcuts = mutableListOf<AiShortcutUi>()
        if (runtime.currentSessionCompletionPercent in 1..99) {
            shortcuts += AiShortcutUi(
                title = "Finish today",
                subtitle = "Use the current session context for a quick coaching suggestion.",
                prompt = "I'm ${runtime.currentSessionCompletionPercent}% through today's workout. Suggest a quick final burnout set.",
            )
        }
        if (runtime.nextSessionLabel != null) {
            shortcuts += AiShortcutUi(
                title = "Check next split",
                subtitle = "Preview or override the next workout session.",
                prompt = "What is my next workout after today, and can you switch it to ${runtime.nextSessionLabel} if needed?",
            )
        }
        if (runtime.pendingReviewCount > 0) {
            shortcuts += AiShortcutUi(
                title = "Review mistakes",
                subtitle = "Use the pending review queue as context.",
                prompt = "I have ${runtime.pendingReviewCount} pending exercise review items. What should I clean up first?",
            )
        }
        shortcuts += AiShortcutUi(
            title = "Fix a log",
            subtitle = "Correct a historical weight or rep mistake.",
            prompt = "My max weight for Bench Press on 2026-03-12 was actually 235, not 225. Fix that.",
        )
        shortcuts += AiShortcutUi(
            title = "Smart swap",
            subtitle = "Preserve progression if a station is blocked.",
            prompt = "Bench is full. Find the best swap from another split day and stage it for this week only.",
        )
        return shortcuts.take(4)
    }

    private fun buildMissingModelMessage(modelPath: String?): String {
        return buildString {
            append("Gemma E2B is not ready on this device yet.")
            if (!modelPath.isNullOrBlank()) {
                append(" Expected model path: ")
                append(modelPath)
            }
        }
    }

    private fun buildLastSessionJson(
        today: String,
        workoutDays: List<WorkoutDay>,
        allSets: List<WorkoutSet>,
        exercises: List<Exercise>,
    ): String {
        val lastDate = allSets.asSequence()
            .map(WorkoutSet::date)
            .filter { it < today }
            .maxOrNull()
            ?: return "{}"
        val lastDay = workoutDays.firstOrNull { it.date == lastDate }
        return buildSessionJson(
            date = lastDate,
            slotType = lastDay?.cycleSlotType,
            slotOccurrence = lastDay?.cycleSlotOccurrence,
            setsForDate = allSets.filter { it.date == lastDate },
            exercises = exercises,
        ).toString()
    }

    private fun buildCurrentTargetSessionJson(
        today: String,
        cycle: Cycle,
        currentDay: WorkoutDay?,
        currentSets: List<WorkoutSet>,
        workoutDays: List<WorkoutDay>,
        allSets: List<WorkoutSet>,
        exercises: List<Exercise>,
    ): String {
        if (currentSets.isNotEmpty() || currentDay?.cycleSlotType != null) {
            return buildSessionJson(
                date = today,
                slotType = currentDay?.cycleSlotType,
                slotOccurrence = currentDay?.cycleSlotOccurrence,
                setsForDate = currentSets,
                exercises = exercises,
            ).toString()
        }
        if (!cycle.isActive || cycle.numTypes <= 0) {
            return "{}"
        }
        val targetType = cycle.nextSessionType ?: nextExpectedSlotType(today, cycle.numTypes, workoutDays)
        val templateDay = workoutDays
            .filter { it.date < today && it.cycleSlotType == targetType }
            .maxByOrNull { it.date }
        return buildSessionJson(
            date = today,
            slotType = targetType,
            slotOccurrence = templateDay?.cycleSlotOccurrence?.plus(1),
            setsForDate = templateDay?.let { day -> allSets.filter { it.date == day.date } }.orEmpty(),
            exercises = exercises,
            templateFromDate = templateDay?.date,
        ).toString()
    }

    private fun buildSessionJson(
        date: String,
        slotType: Int?,
        slotOccurrence: Int?,
        setsForDate: List<WorkoutSet>,
        exercises: List<Exercise>,
        templateFromDate: String? = null,
    ): JSONObject {
        val exerciseMap = exercises.associateBy(Exercise::id)
        val exerciseArray = JSONArray(
            setsForDate
                .groupBy { it.exerciseId }
                .entries
                .sortedBy { entry -> entry.value.minOfOrNull(WorkoutSet::setNumber) ?: Int.MAX_VALUE }
                .map { (exerciseId, sets) ->
                    val orderedSets = sets.sortedBy(WorkoutSet::setNumber)
                    JSONObject()
                        .put("name", exerciseMap[exerciseId]?.name ?: "Unknown")
                        .put(
                            "sets",
                            JSONArray(
                                orderedSets.map { set ->
                                    JSONObject()
                                        .put("setNumber", set.setNumber)
                                        .put("weightLbs", set.weightLbs)
                                        .put("reps", set.reps)
                                        .put("isBodyweight", set.isBodyweight)
                                        .put("completed", set.completed)
                                }
                            )
                        )
                }
        )
        return JSONObject()
            .put("date", date)
            .put("slotLabel", slotType?.let(::cycleTypeLabel) ?: "Unassigned")
            .put("slotOccurrence", slotOccurrence)
            .put("templateFromDate", templateFromDate)
            .put("exerciseCount", exerciseArray.length())
            .put("exercises", exerciseArray)
    }

    private fun nextExpectedSlotType(
        today: String,
        numTypes: Int,
        workoutDays: List<WorkoutDay>,
    ): Int {
        val latestAssigned = workoutDays
            .filter { it.date < today && it.cycleSlotType != null }
            .maxByOrNull(WorkoutDay::date)
        return latestAssigned?.cycleSlotType?.let { (it + 1) % numTypes } ?: 0
    }
}

private data class PartialAiPresentationState(
    val input: String,
    val messages: List<AiMessageUi>,
    val pendingPreview: AiActionPreview?,
    val attachedImageLabel: String?,
    val isWorking: Boolean,
    val statusHeadline: String,
    val statusDetail: String,
    val statusPath: String?,
    val isModelReady: Boolean,
)

private data class ComposerPresentationState(
    val input: String,
    val pendingPreview: AiActionPreview?,
    val attachedImageLabel: String?,
    val isWorking: Boolean,
    val statusHeadline: String,
    val statusDetail: String,
    val statusPath: String?,
    val isModelReady: Boolean,
)

private data class CoreRuntimeSnapshot(
    val cycle: Cycle,
    val currentDay: WorkoutDay?,
    val currentSets: List<WorkoutSet>,
    val exercises: List<Exercise>,
    val pendingReviewCount: Int,
    val nextSessionLabel: String?,
    val currentSessionCompletionPercent: Int,
    val currentSessionSummary: String,
)
