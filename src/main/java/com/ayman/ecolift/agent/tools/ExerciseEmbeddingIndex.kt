package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.ai.ExercisePatternMatcher
import com.ayman.ecolift.ai.MovementPattern
import com.ayman.ecolift.data.Exercise

/**
 * Similarity index over the exercise catalog.
 *
 * Phase 3 (current): pattern-based matching via ExercisePatternMatcher.
 *   - Same pattern → high similarity score
 *   - Adjacent patterns via TransferRatios → medium score
 *   - Unknown/unrelated → not returned
 *
 * Phase 4 replacement: real embedding vectors from EmbeddingGemma (308M, LiteRT-LM).
 * The interface here is stable — AgentToolsImpl calls findSimilar() and doesn't care
 * how similarity is computed.
 */
class ExerciseEmbeddingIndex {

    /**
     * Returns up to [k] exercises from [catalog] most similar to [queryExercise],
     * ordered by descending similarity score.
     */
    fun findSimilar(
        queryExercise: Exercise,
        catalog: List<Exercise>,
        k: Int = 5
    ): List<SimilarExercise> {
        val queryPattern = ExercisePatternMatcher.classify(queryExercise.name)

        return catalog
            .filter { it.id != queryExercise.id }
            .mapNotNull { candidate ->
                val candidatePattern = ExercisePatternMatcher.classify(candidate.name)
                val score = similarityScore(queryPattern, candidatePattern)
                if (score > 0.0) {
                    SimilarExercise(
                        exerciseId = candidate.id,
                        name = candidate.name,
                        similarityScore = score,
                        sharedPattern = candidatePattern.name
                    )
                } else null
            }
            .sortedByDescending { it.similarityScore }
            .take(k)
    }

    private fun similarityScore(source: MovementPattern, target: MovementPattern): Double {
        if (source == MovementPattern.Unknown || target == MovementPattern.Unknown) return 0.0
        if (source == target) return 1.0
        // Use transfer ratio as a proxy for similarity
        val ratio = TransferRatios.ratio(source, target) ?: return 0.0
        // Normalize: ratio=1.0 → 0.8, further from 1.0 → lower score (min 0.1)
        return (1.0 - kotlin.math.abs(1.0 - ratio)).coerceIn(0.1, 0.8)
    }
}
