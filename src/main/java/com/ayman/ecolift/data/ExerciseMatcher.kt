package com.ayman.ecolift.data

data class ExerciseMatchInput(
    val exercise: Exercise,
    val aliases: List<String> = emptyList(),
    val usageCount: Int = 0,
    val lastUsedDate: String? = null,
) {
    /** Tokenised once per input, so a cached input list costs no re-tokenising per keystroke. */
    internal val nameTokens: List<String> by lazy { ExerciseMatcher.matchTokens(exercise.name) }
    internal val aliasTokens: List<List<String>> by lazy { aliases.map(ExerciseMatcher::matchTokens) }
}

enum class ExerciseMatchReason {
    ExactName,
    ExactAlias,
    TokenEqual,
    TokenPrefix,
    TokenTypo,
}

data class ExerciseMatch(
    val exerciseId: Long,
    val displayName: String,
    val queryCoverage: Double,
    val nameCoverage: Double,
    val rankScore: Double,
    val reason: ExerciseMatchReason,
    val isLikelyDuplicate: Boolean,
)

object ExerciseMatcher {
    private val shorthand = mapOf(
        "db" to listOf("dumbbell"),
        "bb" to listOf("barbell"),
        "ohp" to listOf("overhead", "press"),
        "rdl" to listOf("romanian", "deadlift"),
        "sldl" to listOf("stiff", "leg", "deadlift"),
    )

    fun matchTokens(raw: String): List<String> = normalizedTokens(raw)
        .distinct()
        .sorted()

    fun matchKey(raw: String): String = matchTokens(raw).joinToString(" ")

    fun damerau(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val distances = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) distances[i][0] = i
        for (j in 0..b.length) distances[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                var distance = minOf(
                    distances[i - 1][j] + 1,
                    distances[i][j - 1] + 1,
                    distances[i - 1][j - 1] + substitutionCost,
                )
                if (
                    i > 1 &&
                    j > 1 &&
                    a[i - 1] == b[j - 2] &&
                    a[i - 2] == b[j - 1]
                ) {
                    distance = minOf(distance, distances[i - 2][j - 2] + 1)
                }
                distances[i][j] = distance
            }
        }

        return distances[a.length][b.length]
    }

    fun isTypoShape(a: String, b: String): Boolean {
        if (a.length != b.length) return true

        var firstDifference = -1
        var secondDifference = -1
        for (index in a.indices) {
            if (a[index] == b[index]) continue
            if (firstDifference == -1) {
                firstDifference = index
            } else if (secondDifference == -1) {
                secondDifference = index
            } else {
                return false
            }
        }

        return firstDifference >= 0 &&
            secondDifference == firstDifference + 1 &&
            a[firstDifference] == b[secondDifference] &&
            a[secondDifference] == b[firstDifference]
    }

    /**
     * [shortQueryPrefix] (search typing only, 2026-10-08): a one- or two-letter query token that
     * starts a candidate token scores 0.80, so "b" and "be" already list Bench Press instead of
     * nothing until the third letter. Duplicate detection never sets it.
     */
    fun tokenScore(queryToken: String, candidateToken: String, shortQueryPrefix: Boolean = false): Double {
        if (queryToken == candidateToken) return 1.00

        val shorterLength = minOf(queryToken.length, candidateToken.length)
        if (
            shorterLength >= 3 &&
            (candidateToken.startsWith(queryToken) || queryToken.startsWith(candidateToken))
        ) {
            return 0.85
        }

        if (shortQueryPrefix && queryToken.length <= 2 && candidateToken.startsWith(queryToken)) {
            return 0.80
        }

        if (queryToken.length >= 4 && candidateToken.length >= 4) {
            val budget = if (maxOf(queryToken.length, candidateToken.length) <= 6) 1 else 2
            if (damerau(queryToken, candidateToken) <= budget) return 0.70
        }

        return 0.00
    }

    fun rank(
        inputs: List<ExerciseMatchInput>,
        query: String,
        limit: Int = 6,
    ): List<ExerciseMatch> {
        if (limit <= 0) return emptyList()

        // Persisted keys de-duplicate through matchTokens, but ranking keeps occurrences so
        // repeated query words still consume separate one-to-one assignment slots.
        val queryTokens = normalizedTokens(query)
        if (queryTokens.isEmpty()) return emptyList()
        val queryHasRepeatedTokens = queryTokens.size != queryTokens.distinct().size
        val queryKey = queryTokens.distinct().sorted().joinToString(" ")

        return inputs.mapNotNull { input ->
            val nameTokens = input.nameTokens
            val nameKey = nameTokens.joinToString(" ")
            val exactName = !queryHasRepeatedTokens && queryKey == nameKey && nameTokens.isNotEmpty()
            if (exactName) {
                return@mapNotNull RankedMatch(
                    input = input,
                    match = exactMatch(input, ExerciseMatchReason.ExactName),
                )
            }

            var best = coverage(queryTokens, nameTokens, typoShapeGuard = false, shortQueryPrefix = true)
            var bestReason = reasonFor(best.weakestScore)
            for (aliasTokens in input.aliasTokens) {
                if (aliasTokens.isEmpty()) continue
                val aliasKey = aliasTokens.joinToString(" ")
                if (!queryHasRepeatedTokens && queryKey == aliasKey) {
                    return@mapNotNull RankedMatch(
                        input = input,
                        match = exactMatch(input, ExerciseMatchReason.ExactAlias),
                    )
                }

                val aliasCoverage = coverage(queryTokens, aliasTokens, typoShapeGuard = false, shortQueryPrefix = true)
                if (aliasCoverage.rankScore > best.rankScore) {
                    best = aliasCoverage
                    bestReason = reasonFor(best.weakestScore)
                }
            }

            if (best.queryCoverage < 0.60) return@mapNotNull null
            RankedMatch(
                input = input,
                match = ExerciseMatch(
                    exerciseId = input.exercise.id,
                    displayName = input.exercise.name,
                    queryCoverage = best.queryCoverage,
                    nameCoverage = best.nameCoverage,
                    rankScore = best.rankScore,
                    reason = bestReason,
                    isLikelyDuplicate = false,
                ),
            )
        }.sortedWith(rankingComparator)
            .take(limit)
            .map { it.match }
    }

    fun duplicateCandidate(
        target: ExerciseMatchInput,
        others: List<ExerciseMatchInput>,
        excludedIds: Set<Long> = emptySet(),
    ): ExerciseMatch? {
        val targetTokens = target.nameTokens
        if (targetTokens.isEmpty()) return null
        val targetKey = targetTokens.joinToString(" ")

        var best: RankedMatch? = null
        for (input in others) {
            if (input.exercise.id == target.exercise.id || input.exercise.id in excludedIds) continue

            val nameTokens = input.nameTokens
            var bestCoverage = coverage(targetTokens, nameTokens, typoShapeGuard = true)
            var reason = if (targetKey == nameTokens.joinToString(" ") && nameTokens.isNotEmpty()) {
                ExerciseMatchReason.ExactName
            } else {
                reasonFor(bestCoverage.weakestScore)
            }

            for (aliasTokens in input.aliasTokens) {
                if (aliasTokens.isEmpty()) continue
                val aliasCoverage = coverage(targetTokens, aliasTokens, typoShapeGuard = true)
                if (aliasCoverage.rankScore > bestCoverage.rankScore) {
                    bestCoverage = aliasCoverage
                    reason = if (targetKey == aliasTokens.joinToString(" ")) {
                        ExerciseMatchReason.ExactAlias
                    } else {
                        reasonFor(aliasCoverage.weakestScore)
                    }
                }
            }

            if (bestCoverage.queryCoverage < 0.85 || bestCoverage.nameCoverage < 0.85) continue
            val candidate = RankedMatch(
                input = input,
                match = ExerciseMatch(
                    exerciseId = input.exercise.id,
                    displayName = input.exercise.name,
                    queryCoverage = bestCoverage.queryCoverage,
                    nameCoverage = bestCoverage.nameCoverage,
                    rankScore = bestCoverage.rankScore,
                    reason = reason,
                    isLikelyDuplicate = true,
                ),
            )
            if (best == null || duplicateComparator.compare(candidate, best) < 0) best = candidate
        }

        return best?.match
    }

    private fun normalizedTokens(raw: String): List<String> {
        val words = raw.trim()
            .lowercase()
            .map { character ->
                if (character.isLetterOrDigit()) character else ' '
            }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }

        return words.flatMap { word ->
            val stemmed = if (word.length > 3 && word.endsWith("s") && !word.endsWith("ss")) {
                word.dropLast(1)
            } else {
                word
            }
            shorthand[stemmed] ?: listOf(stemmed)
        }
    }

    private fun coverage(
        queryTokens: List<String>,
        candidateTokens: List<String>,
        typoShapeGuard: Boolean,
        shortQueryPrefix: Boolean = false,
    ): Coverage {
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return Coverage()

        val assignment = maximumAssignment(queryTokens, candidateTokens, typoShapeGuard, shortQueryPrefix)
        val queryCoverage = assignment.sum / queryTokens.size
        val nameCoverage = assignment.sum / candidateTokens.size
        return Coverage(
            queryCoverage = queryCoverage,
            nameCoverage = nameCoverage,
            rankScore = 0.70 * queryCoverage + 0.30 * nameCoverage,
            weakestScore = assignment.weakestScore,
        )
    }

    private fun maximumAssignment(
        queryTokens: List<String>,
        candidateTokens: List<String>,
        typoShapeGuard: Boolean,
        shortQueryPrefix: Boolean,
    ): Assignment {
        val smallerIsQuery = queryTokens.size <= candidateTokens.size
        val smaller = if (smallerIsQuery) queryTokens else candidateTokens
        val larger = if (smallerIsQuery) candidateTokens else queryTokens

        if (smaller.size > 12 || larger.size > 64) {
            return greedyAssignment(queryTokens, candidateTokens, typoShapeGuard, shortQueryPrefix)
        }

        val stateCount = 1 shl smaller.size
        var sums = DoubleArray(stateCount) { -1.0 }
        var weakest = DoubleArray(stateCount) { 1.0 }
        sums[0] = 0.0

        for (largeIndex in larger.indices) {
            val nextSums = sums.copyOf()
            val nextWeakest = weakest.copyOf()
            for (mask in sums.indices) {
                if (sums[mask] < 0.0) continue
                for (smallIndex in smaller.indices) {
                    val bit = 1 shl smallIndex
                    if (mask and bit != 0) continue
                    val queryToken = if (smallerIsQuery) smaller[smallIndex] else larger[largeIndex]
                    val candidateToken = if (smallerIsQuery) larger[largeIndex] else smaller[smallIndex]
                    val score = guardedTokenScore(queryToken, candidateToken, typoShapeGuard, shortQueryPrefix)
                    if (score == 0.0) continue

                    val newMask = mask or bit
                    val newSum = sums[mask] + score
                    if (newSum > nextSums[newMask]) {
                        nextSums[newMask] = newSum
                        nextWeakest[newMask] = minOf(weakest[mask], score)
                    }
                }
            }
            sums = nextSums
            weakest = nextWeakest
        }

        var bestMask = 0
        for (mask in 1 until stateCount) {
            if (sums[mask] > sums[bestMask]) bestMask = mask
        }
        return Assignment(sums[bestMask], weakest[bestMask])
    }

    private fun greedyAssignment(
        queryTokens: List<String>,
        candidateTokens: List<String>,
        typoShapeGuard: Boolean,
        shortQueryPrefix: Boolean,
    ): Assignment {
        val pairs = mutableListOf<TokenPair>()
        for (queryIndex in queryTokens.indices) {
            for (candidateIndex in candidateTokens.indices) {
                val score = guardedTokenScore(
                    queryTokens[queryIndex],
                    candidateTokens[candidateIndex],
                    typoShapeGuard,
                    shortQueryPrefix,
                )
                if (score > 0.0) pairs += TokenPair(queryIndex, candidateIndex, score)
            }
        }
        pairs.sortWith(
            compareByDescending<TokenPair> { it.score }
                .thenBy { it.queryIndex }
                .thenBy { it.candidateIndex },
        )

        val usedQueries = BooleanArray(queryTokens.size)
        val usedCandidates = BooleanArray(candidateTokens.size)
        var sum = 0.0
        var weakest = 1.0
        for (pair in pairs) {
            if (usedQueries[pair.queryIndex] || usedCandidates[pair.candidateIndex]) continue
            usedQueries[pair.queryIndex] = true
            usedCandidates[pair.candidateIndex] = true
            sum += pair.score
            weakest = minOf(weakest, pair.score)
        }
        return Assignment(sum, weakest)
    }

    private fun guardedTokenScore(
        queryToken: String,
        candidateToken: String,
        typoShapeGuard: Boolean,
        shortQueryPrefix: Boolean,
    ): Double {
        val score = tokenScore(queryToken, candidateToken, shortQueryPrefix)
        return if (typoShapeGuard && score == 0.70 && !isTypoShape(queryToken, candidateToken)) {
            0.0
        } else {
            score
        }
    }

    private fun exactMatch(
        input: ExerciseMatchInput,
        reason: ExerciseMatchReason,
    ) = ExerciseMatch(
        exerciseId = input.exercise.id,
        displayName = input.exercise.name,
        queryCoverage = 1.0,
        nameCoverage = 1.0,
        rankScore = 1.0,
        reason = reason,
        isLikelyDuplicate = false,
    )

    private fun reasonFor(weakestScore: Double): ExerciseMatchReason = when {
        weakestScore >= 1.00 -> ExerciseMatchReason.TokenEqual
        weakestScore >= 0.80 -> ExerciseMatchReason.TokenPrefix
        else -> ExerciseMatchReason.TokenTypo
    }

    private val rankingComparator = Comparator<RankedMatch> { left, right ->
        right.match.rankScore.compareTo(left.match.rankScore)
            .takeIf { it != 0 }
            ?: right.input.usageCount.compareTo(left.input.usageCount)
                .takeIf { it != 0 }
            ?: compareLastUsedDate(left.input.lastUsedDate, right.input.lastUsedDate)
                .takeIf { it != 0 }
            ?: left.match.displayName.compareTo(right.match.displayName)
                .takeIf { it != 0 }
            ?: left.match.exerciseId.compareTo(right.match.exerciseId)
    }

    private val duplicateComparator = Comparator<RankedMatch> { left, right ->
        right.match.queryCoverage.compareTo(left.match.queryCoverage)
            .takeIf { it != 0 }
            ?: right.input.usageCount.compareTo(left.input.usageCount)
                .takeIf { it != 0 }
            ?: left.match.displayName.compareTo(right.match.displayName)
    }

    private fun compareLastUsedDate(left: String?, right: String?): Int = when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> right.compareTo(left)
    }

    private data class Coverage(
        val queryCoverage: Double = 0.0,
        val nameCoverage: Double = 0.0,
        val rankScore: Double = 0.0,
        val weakestScore: Double = 1.0,
    )

    private data class Assignment(
        val sum: Double,
        val weakestScore: Double,
    )

    private data class TokenPair(
        val queryIndex: Int,
        val candidateIndex: Int,
        val score: Double,
    )

    private data class RankedMatch(
        val input: ExerciseMatchInput,
        val match: ExerciseMatch,
    )
}
