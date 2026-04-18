package com.ayman.ecolift.agent.router

enum class PatchType {
    LogSet, EditSet, DeleteSet, MoveWorkoutDay, RenameExercise
}

enum class ReadType {
    AskRecommendation, AskSimilar, AskHistory, QueryDate, QueryProgress
}

sealed interface Intent {
    data class Write(val patchType: PatchType, val rawText: String) : Intent
    data class Read(val queryType: ReadType, val rawText: String) : Intent
    data class Clarify(val question: String) : Intent
}
