package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseMatcherTest {
    @Test
    fun normalization_is_deterministic_and_expands_only_the_specified_shorthand() {
        assertEquals(
            listOf("curl", "dumbbell"),
            ExerciseMatcher.matchTokens("Dumbbell Curls"),
        )
        assertEquals(
            ExerciseMatcher.matchTokens("Dumbbell Curls"),
            ExerciseMatcher.matchTokens("DB Curl"),
        )
        assertTrue("press" in ExerciseMatcher.matchTokens("Bench Press"))
        assertEquals(
            ExerciseMatcher.matchKey("machine dip"),
            ExerciseMatcher.matchKey("Dip Machine"),
        )
        assertEquals(listOf("deck", "pec"), ExerciseMatcher.matchTokens("Pec-Deck"))
    }

    @Test
    fun damerau_distance_and_typo_shape_distinguish_slips_from_substitutions() {
        assertEquals(1, ExerciseMatcher.damerau("tricpe", "tricep"))
        assertEquals(1, ExerciseMatcher.damerau("machne", "machine"))
        assertTrue(ExerciseMatcher.isTypoShape("machne", "machine"))
        assertTrue(ExerciseMatcher.isTypoShape("tricpe", "tricep"))
        assertTrue(!ExerciseMatcher.isTypoShape("back", "hack"))
        assertTrue(!ExerciseMatcher.isTypoShape("pull", "push"))
    }

    @Test
    fun ranking_matches_tokens_aliases_usage_and_one_to_one_assignments() {
        val dipMachine = ExerciseMatchInput(Exercise(id = 1, name = "Dip Machine"))
        val machineDip = ExerciseMatchInput(Exercise(id = 2, name = "Machine Dip"))
        val benchPress = ExerciseMatchInput(Exercise(id = 3, name = "Bench Press"))
        val dipResults = ExerciseMatcher.rank(
            listOf(dipMachine, machineDip, benchPress),
            query = "dip",
        )
        assertEquals(listOf(1L, 2L), dipResults.map { it.exerciseId })
        assertTrue(dipResults.none { it.exerciseId == 3L })

        val reorderedResults = ExerciseMatcher.rank(
            listOf(dipMachine, machineDip, benchPress),
            query = "machine dip",
        )
        assertEquals(listOf(1L, 2L), reorderedResults.take(2).map { it.exerciseId })
        assertEquals(1.0, reorderedResults[0].rankScore)
        assertEquals(reorderedResults[0].rankScore, reorderedResults[1].rankScore)

        val aliased = ExerciseMatchInput(
            exercise = Exercise(id = 4, name = "Romanian Deadlift"),
            aliases = listOf("Hamstring Hinge"),
        )
        val exactAliasResults = ExerciseMatcher.rank(
            listOf(benchPress, aliased),
            query = "hamstring hinge",
        )
        assertEquals(4L, exactAliasResults.first().exerciseId)
        assertEquals(ExerciseMatchReason.ExactAlias, exactAliasResults.first().reason)

        val lessUsed = ExerciseMatchInput(
            exercise = Exercise(id = 5, name = "Dip Station"),
            usageCount = 2,
        )
        val moreUsed = ExerciseMatchInput(
            exercise = Exercise(id = 6, name = "Dip Platform"),
            usageCount = 9,
        )
        val usageResults = ExerciseMatcher.rank(listOf(lessUsed, moreUsed), query = "dip")
        assertEquals(listOf(6L, 5L), usageResults.map { it.exerciseId })

        val repeatedOnly = ExerciseMatcher.rank(listOf(benchPress), query = "press press")
        assertTrue(
            repeatedOnly.isEmpty(),
            "One candidate press can score only one query press, giving 0.5 query coverage",
        )
        val visibleRepeated = ExerciseMatcher.rank(listOf(benchPress), query = "bench press press")
        assertEquals(1, visibleRepeated.size)
        assertTrue(visibleRepeated.single().queryCoverage < 1.0)
    }

    @Test
    fun one_and_two_letter_queries_list_lifts_whose_words_start_with_them() {
        val benchPress = ExerciseMatchInput(Exercise(id = 1, name = "Bench Press"))
        val bentOverRow = ExerciseMatchInput(Exercise(id = 2, name = "Bent Over Row"))
        val squat = ExerciseMatchInput(Exercise(id = 3, name = "Squat"))
        val all = listOf(benchPress, bentOverRow, squat)

        assertEquals(listOf(1L, 2L), ExerciseMatcher.rank(all, query = "be").map { it.exerciseId })
        assertEquals(1L, ExerciseMatcher.rank(listOf(benchPress, squat), query = "be").single().exerciseId)
        assertEquals(listOf(1L, 2L), ExerciseMatcher.rank(all, query = "b").map { it.exerciseId })
        assertEquals(ExerciseMatchReason.TokenPrefix, ExerciseMatcher.rank(all, query = "be").first().reason)

        assertEquals(0.80, ExerciseMatcher.tokenScore("be", "bench", shortQueryPrefix = true))
        assertEquals(0.85, ExerciseMatcher.tokenScore("ben", "bench", shortQueryPrefix = true))
        assertEquals(0.00, ExerciseMatcher.tokenScore("be", "bench"), "Duplicate detection keeps the three-letter rule")
        assertEquals(0.00, ExerciseMatcher.tokenScore("bench", "be", shortQueryPrefix = true), "Only the query may be the short prefix")
    }

    @Test
    fun duplicate_detection_accepts_deletions_and_transpositions() {
        val deletion = ExerciseMatcher.duplicateCandidate(
            target = ExerciseMatchInput(Exercise(id = 10, name = "Dip Machne")),
            others = listOf(ExerciseMatchInput(Exercise(id = 11, name = "Machine Dip"))),
        )
        assertTrue(deletion != null)
        assertEquals(0.85, deletion?.queryCoverage ?: 0.0, 0.0000001)
        assertEquals(0.85, deletion?.nameCoverage ?: 0.0, 0.0000001)
        assertTrue(deletion?.isLikelyDuplicate == true)

        val transposition = ExerciseMatcher.duplicateCandidate(
            target = ExerciseMatchInput(
                Exercise(id = 12, name = "Behind The Back Tricpe Extension"),
            ),
            others = listOf(
                ExerciseMatchInput(
                    Exercise(id = 13, name = "Behind The Back Tricep Extension"),
                ),
            ),
        )
        assertTrue(transposition != null)
        assertEquals(0.94, transposition?.queryCoverage ?: 0.0, 0.0000001)
        assertEquals(0.94, transposition?.nameCoverage ?: 0.0, 0.0000001)
    }

    @Test
    fun duplicate_detection_rejects_substitutions_partial_names_and_excluded_ids() {
        assertNull(
            ExerciseMatcher.duplicateCandidate(
                target = ExerciseMatchInput(Exercise(id = 20, name = "Back Squat")),
                others = listOf(ExerciseMatchInput(Exercise(id = 21, name = "Hack Squat"))),
            ),
        )
        assertNull(
            ExerciseMatcher.duplicateCandidate(
                target = ExerciseMatchInput(Exercise(id = 22, name = "Pull Up")),
                others = listOf(ExerciseMatchInput(Exercise(id = 23, name = "Push Up"))),
            ),
        )
        assertNull(
            ExerciseMatcher.duplicateCandidate(
                target = ExerciseMatchInput(Exercise(id = 24, name = "Incline Bench Press")),
                others = listOf(
                    ExerciseMatchInput(Exercise(id = 25, name = "Decline Bench Press")),
                ),
            ),
        )
        assertNull(
            ExerciseMatcher.duplicateCandidate(
                target = ExerciseMatchInput(Exercise(id = 26, name = "Bench Press")),
                others = listOf(
                    ExerciseMatchInput(Exercise(id = 27, name = "Incline Bench Press")),
                ),
            ),
        )
        assertNull(
            ExerciseMatcher.duplicateCandidate(
                target = ExerciseMatchInput(Exercise(id = 28, name = "Dip Machne")),
                others = listOf(ExerciseMatchInput(Exercise(id = 29, name = "Machine Dip"))),
                excludedIds = setOf(29L),
            ),
        )
    }
}
