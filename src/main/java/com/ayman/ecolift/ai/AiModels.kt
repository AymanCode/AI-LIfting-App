package com.ayman.ecolift.ai

data class AiConversationTurn(
    val role: String,
    val message: String,
)

enum class AiToolName(val wireName: String) {
    None("none"),
    UpdateSetLog("update_set_log"),
    ModifyCycle("modify_cycle"),
    Calculate1Rm("calculate_1rm"),
    GetSplitAlternatives("get_split_alternatives"),
    CreateTempSwap("create_temp_swap"),
    AnalyzeEquipment("analyze_equipment"),
    EstimateRelativeLoad("estimate_relative_load");

    companion object {
        fun fromWireName(value: String?): AiToolName {
            return entries.firstOrNull { it.wireName == value } ?: None
        }
    }
}

data class AiToolCall(
    val tool: AiToolName,
    val requiresConfirmation: Boolean,
    val exercise: String? = null,
    val targetExercise: String? = null,
    val date: String? = null,
    val field: String? = null,
    val newValue: Int? = null,
    val setSelector: String? = null,
    val weight: Int? = null,
    val reps: Int? = null,
    val activeSessionType: Int? = null,
    val activeSessionLabel: String? = null,
    val targetSessionType: Int? = null,
    val targetSessionLabel: String? = null,
    val machineName: String? = null,
    val machineMechanics: String? = null,
)

data class AiModelOutput(
    val assistantMessage: String,
    val toolCall: AiToolCall? = null,
    val rawResponse: String,
)

data class GemmaStatus(
    val isReady: Boolean,
    val headline: String,
    val detail: String,
    val modelPath: String? = null,
)

data class AiRuntimeContext(
    val today: String,
    val cycleActive: Boolean,
    val cycleNumTypes: Int,
    val nextSessionLabel: String?,
    val currentSessionCompletionPercent: Int,
    val currentSessionSummary: String,
    val pendingReviewCount: Int,
    val availableExercises: List<String>,
    val lastSessionJson: String = "{}",
    val currentTargetSessionJson: String = "{}",
    val manifestJson: String = "{}",
)

data class AiActionPreview(
    val title: String,
    val detail: String,
    val confirmLabel: String = "Confirm",
)

data class AiExecutionResult(
    val title: String,
    val detail: String,
    val pendingToolCall: AiToolCall? = null,
    val pendingPreview: AiActionPreview? = null,
)
