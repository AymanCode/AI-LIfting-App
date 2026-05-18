package com.ayman.ecolift.agent

import com.ayman.ecolift.data.WeightLbs

/**
 * Deterministic parser for one-exercise workout log messages.
 *
 * This intentionally handles the high-confidence cases that users jot quickly:
 * "bench 135x7,125x10", "bench 135 lbs for 12 reps, 10, 8", and
 * "3 sets of bench at 135 lb x 8". Multi-exercise parsing is left to a later
 * planner so the fast path does not over-guess.
 */
object LogSetTextParser {

    data class ParsedSet(
        val weightLbs: Int?,
        val reps: Int
    )

    data class ParsedLog(
        val exerciseQuery: String,
        val sets: List<ParsedSet>
    )

    fun parseOneExercise(text: String): ParsedLog? {
        val normalized = normalize(text)
        val consumed = mutableListOf<IntRange>()
        val sets = mutableListOf<ParsedSet>()

        parseWeightRepLists(normalized, consumed, sets)
        parseBareWeightRepLists(normalized, consumed, sets)
        parseBareWeightRepPairs(normalized, consumed, sets)
        parseCompactWeightReps(normalized, consumed, sets)

        val expanded = expandExplicitSetCount(normalized, sets)
        if (expanded.isEmpty()) return null

        val exerciseQuery = extractExerciseQuery(normalized, consumed)
        if (exerciseQuery.isBlank()) return null

        return ParsedLog(exerciseQuery = exerciseQuery, sets = expanded)
    }

    private fun normalize(text: String): String {
        return text
            .replace(Regex("""(^|[\s,;])\.(?=\d+\s*[xX])"""), "$1")
            .replace(Regex("""(?i),\s*and\s+"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun parseWeightRepLists(
        text: String,
        consumed: MutableList<IntRange>,
        sets: MutableList<ParsedSet>
    ) {
        for (match in WEIGHT_REP_LIST.findAll(text)) {
            val range = match.range
            if (range.overlaps(consumed)) continue

            val weight = match.groupValues[1].toStorageWeightOrNull() ?: continue
            val reps = NUMBER.findAll(match.groupValues[2])
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 1..100 }
                .toList()
            if (reps.isEmpty()) continue

            reps.forEach { sets.add(ParsedSet(weightLbs = weight, reps = it)) }
            consumed.add(range)
        }
    }

    private fun parseCompactWeightReps(
        text: String,
        consumed: MutableList<IntRange>,
        sets: MutableList<ParsedSet>
    ) {
        for (match in COMPACT_WEIGHT_REPS.findAll(text)) {
            val range = match.range
            if (range.overlaps(consumed)) continue

            val weightRaw = match.groupValues[1]
            val reps = match.groupValues[2].toIntOrNull() ?: continue
            if (reps !in 1..100) continue

            val firstNumber = weightRaw.toDoubleOrNull() ?: continue
            if (firstNumber <= 20.0) continue

            val weight = weightRaw.toStorageWeightOrNull() ?: continue
            sets.add(ParsedSet(weightLbs = weight, reps = reps))
            consumed.add(range)
        }
    }

    private fun parseBareWeightRepLists(
        text: String,
        consumed: MutableList<IntRange>,
        sets: MutableList<ParsedSet>
    ) {
        for (match in BARE_WEIGHT_REP_LIST.findAll(text)) {
            val range = match.range
            if (range.overlaps(consumed)) continue

            val weight = match.groupValues[1].toStorageWeightOrNull() ?: continue
            val reps = NUMBER.findAll(match.groupValues[2])
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 1..100 }
                .toList()
            if (reps.size < 2) continue

            reps.forEach { sets.add(ParsedSet(weightLbs = weight, reps = it)) }
            consumed.add(range)
        }
    }

    private fun parseBareWeightRepPairs(
        text: String,
        consumed: MutableList<IntRange>,
        sets: MutableList<ParsedSet>
    ) {
        for (match in BARE_WEIGHT_REP_PAIR.findAll(text)) {
            val range = match.range
            if (range.overlaps(consumed)) continue

            val weightRaw = match.groupValues[1]
            val reps = match.groupValues[2].toIntOrNull() ?: continue
            if (reps !in 1..100) continue

            val firstNumber = weightRaw.toDoubleOrNull() ?: continue
            if (firstNumber <= 20.0) continue

            val weight = weightRaw.toStorageWeightOrNull() ?: continue
            sets.add(ParsedSet(weightLbs = weight, reps = reps))
            consumed.add(range)
        }
    }

    private fun expandExplicitSetCount(text: String, sets: List<ParsedSet>): List<ParsedSet> {
        if (sets.size != 1) return sets
        val count = SET_COUNT.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return sets
        if (count !in 2..10) return sets
        return List(count) { sets.first() }
    }

    private fun extractExerciseQuery(text: String, consumed: List<IntRange>): String {
        val chars = text.toCharArray()
        for (range in consumed) {
            val start = range.first.coerceAtLeast(0)
            val end = range.last.coerceAtMost(chars.lastIndex)
            for (i in start..end) chars[i] = ' '
        }

        return String(chars)
            .replace(STOP_WORDS, " ")
            .replace(NUMBER, " ")
            .replace(UNIT_WORDS, " ")
            .replace(Regex("""[^A-Za-z0-9@ ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun String.toStorageWeightOrNull(): Int? {
        val value = trim().trimStart('.')
        if (value.isBlank()) return null
        return WeightLbs.parseInputToStorage(value)
    }

    private fun IntRange.overlaps(ranges: List<IntRange>): Boolean {
        return ranges.any { first <= it.last && it.first <= last }
    }

    private val WEIGHT_REP_LIST = Regex(
        """(?i)(\d+(?:\.\d+)?)\s*(?:lbs?|pounds?|pound|kg|kilos?)\b(?:\s+or\s+(?:lbs?|pounds?|pound|kg|kilos?))?\s*(?:for|x|by)\s+(\d{1,3}(?:\s*reps?)?(?:(?:\s*,\s*|\s+and\s+|\s+)\d{1,3}(?:\s*reps?)?)*)"""
    )
    private val BARE_WEIGHT_REP_LIST = Regex(
        """(?i)(?<![\d.])(\d{2,4}(?:\.\d+)?)\s*(?:for|x)\s+(\d{1,3}(?:\s*reps?)?(?:(?:\s*,\s*|\s+and\s+|\s+)\d{1,3}(?:\s*reps?)?)+)"""
    )
    private val BARE_WEIGHT_REP_PAIR = Regex(
        """(?i)(?<![\d.])(\d{2,4}(?:\.\d+)?)\s*(?:lbs?|pounds?|pound|kg|kilos?)?\s*(?:for|x)\s+(\d{1,3})(?!\d)"""
    )
    private val COMPACT_WEIGHT_REPS = Regex("""(?i)(?<![\d.])(\d{1,4}(?:\.\d+)?)\s*x\s*(\d{1,3})(?!\d)""")
    private val SET_COUNT = Regex("""(?i)\b(\d{1,2})\s+sets?\b""")
    private val NUMBER = Regex("""\b\d+(?:\.\d+)?\b""")
    private val UNIT_WORDS = Regex("""(?i)\b(?:lbs?|pounds?|pound|kg|kilos?)\b""")
    private val STOP_WORDS = Regex(
        """(?i)\b(?:i|did|do|done|just|finished|logged|log|record|recorded|add|added|set|sets|rep|reps|for|at|with|around|about|approximately|roughly|yesterday|today|tomorrow|on|my|the|a|an|of|exercise|exercises|forgot|to|some|last|this|or|and|by|from|was|then|week|night|time|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""
    )
}
