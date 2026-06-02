package com.ayman.ecolift.ai

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.LEGACY_DEFAULT_MUSCLE_GROUPS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMuscleClassifierTest {
    @Test
    fun localClassifierIdentifiesCommonMovements() {
        assertEquals(
            "SHOULDERS",
            ExerciseMuscleClassifier.classifyLocally(Exercise(id = 1, name = "Lateral Raise Dumbbell"))?.muscleGroups
        )
        assertEquals(
            "BACK",
            ExerciseMuscleClassifier.classifyLocally(Exercise(id = 2, name = "Seated Cable Row"))?.muscleGroups
        )
        assertEquals(
            "QUADS",
            ExerciseMuscleClassifier.classifyLocally(Exercise(id = 3, name = "One Legged Extension Machine"))?.muscleGroups
        )
        assertEquals(
            "BACK",
            ExerciseMuscleClassifier.classifyLocally(Exercise(id = 4, name = "Pull-ups"))?.muscleGroups
        )
        assertEquals(
            "CHEST",
            ExerciseMuscleClassifier.classifyLocally(Exercise(id = 5, name = "Push-ups"))?.muscleGroups
        )
    }

    @Test
    fun classifierUsesRemoteOnlyForLocalMisses() = runTest {
        val remoteRequests = mutableListOf<List<Exercise>>()
        val classifier = ExerciseMuscleClassifier(
            apiKey = "test-key",
            baseUrl = "",
            model = "",
            remoteClassifier = { exercises ->
                remoteRequests += exercises
                exercises.map { exercise ->
                    MuscleClassification(
                        exerciseId = exercise.id,
                        muscleGroups = "FULL BODY",
                        confidence = 0.91,
                    )
                }
            },
        )

        val result = classifier.classifyBatch(
            listOf(
                Exercise(id = 1, name = "Bench Press"),
                Exercise(id = 2, name = "Mystery Machine"),
            )
        )

        assertEquals(listOf(2L), remoteRequests.single().map { it.id })
        assertEquals("CHEST", result.single { it.exerciseId == 1L }.muscleGroups)
        assertEquals("FULL BODY", result.single { it.exerciseId == 2L }.muscleGroups)
    }

    @Test
    fun classifierOnlyTargetsBlankOrLegacyDefaultLabels() {
        assertTrue(ExerciseMuscleClassifier.shouldClassify(Exercise(id = 1, name = "Curl")))
        assertTrue(
            ExerciseMuscleClassifier.shouldClassify(
                Exercise(id = 2, name = "Curl", muscleGroups = LEGACY_DEFAULT_MUSCLE_GROUPS)
            )
        )
        assertFalse(
            ExerciseMuscleClassifier.shouldClassify(
                Exercise(id = 3, name = "Curl", muscleGroups = "BICEPS")
            )
        )
    }

    @Test
    fun groupNormalizerRejectsUnknownOrUnhelpfulLabels() {
        assertEquals("CHEST", ExerciseMuscleClassifier.normalizeGroups("chest, triceps"))
        assertNull(ExerciseMuscleClassifier.normalizeGroups("OTHER"))
        assertNull(ExerciseMuscleClassifier.normalizeGroups("CHEST · RANDOM"))
    }
}
