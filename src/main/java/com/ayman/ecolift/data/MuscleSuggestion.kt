package com.ayman.ecolift.data

import com.ayman.ecolift.agent.tools.ExerciseEmbeddingIndex

data class MuscleSuggestion(
    val group: String,
    val isKeywordHit: Boolean,
)

private data class MuscleKeyword(
    val keyword: String,
    val group: String,
)

private val muscleKeywords = listOf(
    MuscleKeyword("incline bench", "CHEST"),
    MuscleKeyword("decline bench", "CHEST"),
    MuscleKeyword("bench press", "CHEST"),
    MuscleKeyword("chest press", "CHEST"),
    MuscleKeyword("pec deck", "CHEST"),
    MuscleKeyword("chest fly", "CHEST"),
    MuscleKeyword("fly", "CHEST"),
    MuscleKeyword("push up", "CHEST"),
    MuscleKeyword("pushup", "CHEST"),
    MuscleKeyword("dip", "CHEST"),
    MuscleKeyword("bench", "CHEST"),
    MuscleKeyword("chest", "CHEST"),
    MuscleKeyword("lat pulldown", "BACK"),
    MuscleKeyword("pull down", "BACK"),
    MuscleKeyword("pullup", "BACK"),
    MuscleKeyword("pull up", "BACK"),
    MuscleKeyword("chin up", "BACK"),
    MuscleKeyword("seated row", "BACK"),
    MuscleKeyword("barbell row", "BACK"),
    MuscleKeyword("dumbbell row", "BACK"),
    MuscleKeyword("t-bar row", "BACK"),
    MuscleKeyword("deadlift", "BACK"),
    MuscleKeyword("good morning", "BACK"),
    MuscleKeyword("back extension", "BACK"),
    MuscleKeyword("row", "BACK"),
    MuscleKeyword("pull", "BACK"),
    MuscleKeyword("lat", "BACK"),
    MuscleKeyword("shrug", "BACK"),
    MuscleKeyword("rear delt", "SHOULDERS"),
    MuscleKeyword("shoulder press", "SHOULDERS"),
    MuscleKeyword("overhead press", "SHOULDERS"),
    MuscleKeyword("military press", "SHOULDERS"),
    MuscleKeyword("lateral raise", "SHOULDERS"),
    MuscleKeyword("front raise", "SHOULDERS"),
    MuscleKeyword("upright row", "SHOULDERS"),
    MuscleKeyword("face pull", "SHOULDERS"),
    MuscleKeyword("arnold press", "SHOULDERS"),
    MuscleKeyword("shoulder", "SHOULDERS"),
    MuscleKeyword("overhead", "SHOULDERS"),
    MuscleKeyword("lateral", "SHOULDERS"),
    MuscleKeyword("delt", "SHOULDERS"),
    MuscleKeyword("biceps curl", "BICEPS"),
    MuscleKeyword("hammer curl", "BICEPS"),
    MuscleKeyword("preacher curl", "BICEPS"),
    MuscleKeyword("concentration curl", "BICEPS"),
    MuscleKeyword("curl", "BICEPS"),
    MuscleKeyword("bicep", "BICEPS"),
    MuscleKeyword("triceps extension", "TRICEPS"),
    MuscleKeyword("tricep extension", "TRICEPS"),
    MuscleKeyword("skull crusher", "TRICEPS"),
    MuscleKeyword("pushdown", "TRICEPS"),
    MuscleKeyword("pressdown", "TRICEPS"),
    MuscleKeyword("close grip", "TRICEPS"),
    MuscleKeyword("triceps", "TRICEPS"),
    MuscleKeyword("tricep", "TRICEPS"),
    MuscleKeyword("forearm curl", "FOREARMS"),
    MuscleKeyword("wrist curl", "FOREARMS"),
    MuscleKeyword("farmer", "FOREARMS"),
    MuscleKeyword("grip", "FOREARMS"),
    MuscleKeyword("forearm", "FOREARMS"),
    MuscleKeyword("back squat", "QUADS"),
    MuscleKeyword("front squat", "QUADS"),
    MuscleKeyword("hack squat", "QUADS"),
    MuscleKeyword("leg press", "QUADS"),
    MuscleKeyword("leg extension", "QUADS"),
    MuscleKeyword("bulgarian", "QUADS"),
    MuscleKeyword("split squat", "QUADS"),
    MuscleKeyword("lunge", "QUADS"),
    MuscleKeyword("step up", "QUADS"),
    MuscleKeyword("squat", "QUADS"),
    MuscleKeyword("quad", "QUADS"),
    MuscleKeyword("romanian deadlift", "HAMSTRINGS"),
    MuscleKeyword("rdl", "HAMSTRINGS"),
    MuscleKeyword("leg curl", "HAMSTRINGS"),
    MuscleKeyword("hamstring curl", "HAMSTRINGS"),
    MuscleKeyword("nordic", "HAMSTRINGS"),
    MuscleKeyword("hamstring", "HAMSTRINGS"),
    MuscleKeyword("hip thrust", "GLUTES"),
    MuscleKeyword("glute bridge", "GLUTES"),
    MuscleKeyword("kickback", "GLUTES"),
    MuscleKeyword("abductor", "GLUTES"),
    MuscleKeyword("glute", "GLUTES"),
    MuscleKeyword("calf raise", "CALVES"),
    MuscleKeyword("calves", "CALVES"),
    MuscleKeyword("calf", "CALVES"),
    MuscleKeyword("hanging leg raise", "CORE"),
    MuscleKeyword("leg raise", "CORE"),
    MuscleKeyword("crunch", "CORE"),
    MuscleKeyword("sit up", "CORE"),
    MuscleKeyword("plank", "CORE"),
    MuscleKeyword("russian twist", "CORE"),
    MuscleKeyword("woodchop", "CORE"),
    MuscleKeyword("pallof", "CORE"),
    MuscleKeyword("core", "CORE"),
    MuscleKeyword("abs", "CORE"),
    MuscleKeyword("ab", "CORE"),
).sortedWith(compareByDescending<MuscleKeyword> { it.keyword.length }.thenBy { it.keyword })

fun suggestMuscleGroupFromKeywords(exerciseName: String): MuscleSuggestion? {
    val normalizedName = " ${exerciseName.lowercase().replace(Regex("[^a-z0-9]+"), " ")} "
    val match = muscleKeywords.firstOrNull { keyword ->
        normalizedName.contains(" ${keyword.keyword} ")
    } ?: return null
    return MuscleSuggestion(match.group, isKeywordHit = true)
}

fun suggestMuscleGroup(
    exerciseName: String,
    taggedExercises: List<Exercise> = emptyList(),
): MuscleSuggestion? =
    suggestMuscleGroupFromKeywords(exerciseName)
        ?: suggestMuscleGroupFromSimilarExercise(exerciseName, taggedExercises)

private fun suggestMuscleGroupFromSimilarExercise(
    exerciseName: String,
    taggedExercises: List<Exercise>,
): MuscleSuggestion? {
    val catalog = taggedExercises
        .filter { selectableBodyPartParts(it.muscleGroups).isNotEmpty() }
    if (catalog.isEmpty()) return null

    val query = Exercise(id = Long.MIN_VALUE, name = exerciseName)
    val similar = ExerciseEmbeddingIndex()
        .findSimilar(query, catalog, k = 1)
        .firstOrNull() ?: return null
    val match = catalog.firstOrNull { it.id == similar.exerciseId } ?: return null
    val group = selectableBodyPartParts(match.muscleGroups).firstOrNull() ?: return null
    return MuscleSuggestion(group, isKeywordHit = false)
}
