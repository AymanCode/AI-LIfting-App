package com.ayman.ecolift.agent.engine

/**
 * Prompt templates for the agent engine.
 *
 * All prompts are plain strings — no model-specific tokens.
 * If the underlying model requires special tokens (e.g. <start_of_turn>),
 * wrap them in the engine implementation, not here.
 */
object Prompts {

    /**
     * Intent classification prompt.
     * Returns one of the intent labels as a single word.
     */
    fun intentClassification(userText: String, recentContext: String = ""): String = buildString {
        appendLine("You are a workout app assistant. Classify the user's intent into exactly one label.")
        appendLine()
        appendLine("Labels:")
        appendLine("  LogSet          - user wants to log a new exercise set")
        appendLine("  EditSet         - user wants to edit/correct a logged set")
        appendLine("  DeleteSet       - user wants to delete a set")
        appendLine("  MoveWorkoutDay  - user wants to move a workout to a different date")
        appendLine("  RenameExercise  - user wants to rename an exercise")
        appendLine("  AskRecommendation - user asks how much weight to use")
        appendLine("  AskSimilar      - user asks for similar or alternative exercises")
        appendLine("  AskHistory      - user asks about past performance for a specific exercise (PR, last session, best set)")
        appendLine("  QueryDate       - user asks what exercises/workout they did on a specific date or day")
        appendLine("  QueryProgress   - user asks how an exercise is trending or improving over time")
        appendLine("  Clarify         - unclear, need more info")
        appendLine()
        if (recentContext.isNotBlank()) {
            appendLine("Recent context: $recentContext")
            appendLine()
        }
        appendLine("User: $userText")
        appendLine()
        appendLine("Intent (one word only):")
    }

    /**
     * Patch generation prompt for write intents.
     * [intentLabel] is the classified intent type.
     * [groundedContext] is JSON from read tools (exercise found, recent sets, etc.).
     * [patchSchema] is the JSON schema of the target DbPatch subtype.
     */
    fun patchGeneration(
        userText: String,
        intentLabel: String,
        groundedContext: String,
        patchSchema: String
    ): String = buildString {
        appendLine("You are a workout data assistant. Extract structured patch data from the user's request.")
        appendLine("Intent: $intentLabel")
        appendLine()
        appendLine("Grounded context (from database):")
        appendLine(groundedContext)
        appendLine()
        appendLine("User request: $userText")
        appendLine()
        appendLine("Output ONLY valid JSON matching this schema (no explanation, no markdown):")
        appendLine(patchSchema)
    }

    /**
     * Explanation prompt — generates a short human-readable response after applying patches.
     * [appliedPatches] is a plain-English summary of what changed.
     */
    fun explanation(userText: String, appliedPatches: String): String = buildString {
        appendLine("You are a terse workout assistant. Write a one-sentence confirmation.")
        appendLine("User asked: $userText")
        appendLine("What happened: $appliedPatches")
        appendLine()
        appendLine("Confirmation (one sentence, no markdown):")
    }

    /**
     * Read result formatting prompt — formats query results into plain English.
     * [queryType] describes what was queried (e.g. "exercise history", "weight recommendation").
     * [resultJson] is the raw JSON result from AgentTools.
     */
    fun formatReadResult(queryType: String, resultJson: String, userText: String): String = buildString {
        appendLine("You are a terse workout assistant. Summarize this data for the user.")
        appendLine("Query type: $queryType")
        appendLine("User asked: $userText")
        appendLine("Data: $resultJson")
        appendLine()
        appendLine("Summary (2-3 sentences max, no markdown):")
    }

    /**
     * Clarification prompt — generates a follow-up question when intent is unclear.
     */
    fun clarify(userText: String): String = buildString {
        appendLine("You are a workout assistant. The user's request is unclear.")
        appendLine("Ask ONE short clarifying question to resolve the ambiguity.")
        appendLine("User: $userText")
        appendLine()
        appendLine("Clarifying question:")
    }
}
