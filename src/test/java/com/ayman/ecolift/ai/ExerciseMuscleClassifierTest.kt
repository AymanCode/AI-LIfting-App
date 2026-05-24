package com.ayman.ecolift.ai

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.LEGACY_DEFAULT_MUSCLE_GROUPS
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
            "BACK · BICEPS",
            ExerciseMuscleClassifier.classifyLocally(Exercise(id = 2, name = "Seated Cable Row"))?.muscleGroups
        )
        assertEquals(
            "QUADS",
            ExerciseMuscleClassifier.classifyLocally(Exercise(id = 3, name = "One Legged Extension Machine"))?.muscleGroups
        )
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
        assertEquals("CHEST · TRICEPS", ExerciseMuscleClassifier.normalizeGroups("chest, triceps"))
        assertNull(ExerciseMuscleClassifier.normalizeGroups("OTHER"))
        assertNull(ExerciseMuscleClassifier.normalizeGroups("CHEST · RANDOM"))
    }
}
