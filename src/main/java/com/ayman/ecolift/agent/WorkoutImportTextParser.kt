package com.ayman.ecolift.agent

import com.ayman.ecolift.data.WeightLbs
import java.time.LocalDate

/**
 * Parses pasted workout notes into dated import entries.
 *
 * Use case: a user who is moving into EcoLift often has old workouts in Notes,
 * texts, spreadsheets, or another tracker. Manually recreating those sessions is
 * nuisance work, especially when every row requires date navigation, exercise
 * search, weight entry, and rep entry. This parser handles common high-confidence
 * note formats locally so the app can import safe rows without spending model
 * calls, while preserving unclear rows for pending review instead of dropping
 * the user's data.
 */
object WorkoutImportTextParser {

    data class ParsedSet(
        val weightLbs: Int?,
        val reps: Int
    )

    data class Entry(
        val date: String,
        val exerciseQuery: String,
        val sets: List<ParsedSet>,
        val rawLine: String
    )

    data class UnresolvedLine(
        val date: String,
        val rawLine: String,
        val reason: String
    )

    data class Draft(
        val entries: List<Entry>,
        val unresolvedLines: List<UnresolvedLine>
    )

    fun looksLikeImport(text: String, defaultDate: String): Boolean {
        val lines = normalizedLines(text)
        if (lines.isEmpty()) return false
        val today = LocalDate.parse(defaultDate)
        return lines.size > 1 && lines.any { extractDatePrefix(it, today) != null } ||
            lines.any { line ->
                val datePrefix = extractDatePrefix(line, today)
                datePrefix != null && datePrefix.remainder.isNotBlank() && line.contains(";")
            }
    }

    fun parse(text: String, defaultDate: String): Draft? {
        val lines = normalizedLines(text)
        if (lines.isEmpty()) return null

        val today = LocalDate.parse(defaultDate)
        var currentDate = defaultDate
        val entries = mutableListOf<Entry>()
        val unresolved = mutableListOf<UnresolvedLine>()

        for (line in lines) {
            val dated = extractDatePrefix(line, today)
            val content = if (dated != null) {
                currentDate = dated.date
                dated.remainder
            } else {
                line
            }
            if (content.isBlank()) continue

            for (segment in splitSegments(content)) {
                val parsed = parseSegment(segment)
                if (parsed == null) {
                    unresolved += UnresolvedLine(
                        date = currentDate,
                        rawLine = segment,
                        reason = "Could not safely parse weights and reps."
                    )
                } else {
                    entries += Entry(
                        date = currentDate,
                        exerciseQuery = parsed.exerciseQuery,
                        sets = parsed.sets,
                        rawLine = segment
                    )
                }
            }
        }

        if (entries.isEmpty() && unresolved.isEmpty()) return null
        return Draft(entries = entries, unresolvedLines = unresolved)
    }

    private data class DatePrefix(val date: String, val remainder: String)
    private data class ParsedSegment(val exerciseQuery: String, val sets: List<ParsedSet>)

    private fun normalizedLines(text: String): List<String> =
        text.lines()
            .map { it.trim().trimStart('-', '*').trim() }
            .filter { it.isNotBlank() }

    private fun splitSegments(content: String): List<String> =
        content.split(";")
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotBlank() }

    private fun parseSegment(segment: String): ParsedSegment? {
        parseWeightRepsCount(segment)?.let { return it }
        parseWeightRepListWithSuffix(segment)?.let { return it }
        LogSetTextParser.parseOneExercise(segment)?.let { parsed ->
            return ParsedSegment(
                exerciseQuery = parsed.exerciseQuery,
                sets = parsed.sets.map { ParsedSet(it.weightLbs, it.reps) }
            )
        }
        parseBareWeightRepList(segment)?.let { return it }
        return null
    }

    private fun parseWeightRepsCount(segment: String): ParsedSegment? {
        val match = WEIGHT_REPS_COUNT.matchEntire(segment) ?: return null
        val exercise = cleanExercise(match.groupValues[1])
        val weight = match.groupValues[2].toStorageWeightOrNull() ?: return null
        val reps = match.groupValues[3].toIntOrNull()?.takeIf { it in 1..100 } ?: return null
        val count = match.groupValues[4].toIntOrNull()?.takeIf { it in 2..10 } ?: return null
        if (exercise.isBlank()) return null
        return ParsedSegment(exercise, List(count) { ParsedSet(weight, reps) })
    }

    private fun parseWeightRepListWithSuffix(segment: String): ParsedSegment? {
        val match = WEIGHT_REP_LIST_WITH_SUFFIX.matchEntire(segment) ?: return null
        val exercise = cleanExercise(match.groupValues[1])
        val weight = match.groupValues[2].toStorageWeightOrNull() ?: return null
        val reps = parseReps(match.groupValues[3])
        if (exercise.isBlank() || reps.isEmpty()) return null
        return ParsedSegment(exercise, reps.map { ParsedSet(weight, it) })
    }

    private fun parseBareWeightRepList(segment: String): ParsedSegment? {
        val match = BARE_WEIGHT_REP_LIST.matchEntire(segment) ?: return null
        val exercise = cleanExercise(match.groupValues[1])
        val weight = match.groupValues[2].toStorageWeightOrNull() ?: return null
        val reps = parseReps(match.groupValues[3])
        if (exercise.isBlank() || reps.isEmpty()) return null
        return ParsedSegment(exercise, reps.map { ParsedSet(weight, it) })
    }

    private fun parseReps(raw: String): List<Int> =
        REP_NUMBER.findAll(raw)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..100 }
            .toList()

    private fun cleanExercise(raw: String): String =
        raw.trim()
            .replace(Regex("""\s+"""), " ")
            .trim(':', '-', ',', '.')
            .trim()

    private fun String.toStorageWeightOrNull(): Int? =
        trim().trimEnd('s', 'S').takeIf { it.isNotBlank() }?.let(WeightLbs::parseInputToStorage)

    private fun extractDatePrefix(line: String, today: LocalDate): DatePrefix? {
        ISO_DATE_PREFIX.matchEntire(line)?.let { match ->
            return DatePrefix(match.groupValues[1], match.groupValues[2].trim())
        }

        NUMERIC_DATE_PREFIX.matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val yearRaw = match.groupValues[3].takeIf { it.isNotBlank() }
            val year = when {
                yearRaw == null -> today.year
                yearRaw.length == 2 -> 2000 + yearRaw.toInt()
                else -> yearRaw.toInt()
            }
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
            return DatePrefix(date.toString(), match.groupValues[4].trim())
        }

        MONTH_DATE_PREFIX.matchEntire(line.lowercase())?.let { match ->
            val month = MONTHS.indexOf(match.groupValues[1]) + 1
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val yearRaw = match.groupValues[3].takeIf { it.isNotBlank() }
            val year = when {
                yearRaw == null -> today.year
                yearRaw.length == 2 -> 2000 + yearRaw.toInt()
                else -> yearRaw.toInt()
            }
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
            return DatePrefix(date.toString(), match.groupValues[4].trim())
        }

        RELATIVE_DATE_PREFIX.matchEntire(line.lowercase())?.let { match ->
            val phrase = match.groupValues[1]
            val date = DateExtractor.extract(phrase, today) ?: return null
            return DatePrefix(date, match.groupValues[2].trim())
        }

        return null
    }

    private val ISO_DATE_PREFIX = Regex("""(?i)^\s*(\d{4}-\d{2}-\d{2})\s*(?::|-)?\s*(.*)$""")
    private val NUMERIC_DATE_PREFIX = Regex("""(?i)^\s*(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\s*(?::|-)?\s*(.*)$""")
    private val MONTH_DATE_PREFIX = Regex("""^\s*(january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{2,4}))?\s*(?::|-)?\s*(.*)$""")
    private val RELATIVE_DATE_PREFIX = Regex("""^\s*((?:last\s+)?(?:today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday))\s*(?::|-)?\s*(.*)$""")
    private val WEIGHT_REPS_COUNT = Regex("""(?i)^(.+?)\s+(\d{1,4}(?:\.\d+)?)s?\s*x\s*(\d{1,3})\s*x\s*(\d{1,2})$""")
    private val WEIGHT_REP_LIST_WITH_SUFFIX = Regex("""(?i)^(.+?)\s+(\d{1,4}(?:\.\d+)?)s?\s*(?:lbs?|pounds?)?\s*(?:x|for)\s+(.+)$""")
    private val BARE_WEIGHT_REP_LIST = Regex("""(?i)^(.+?)\s+(\d{1,4}(?:\.\d+)?)s?\s+((?:\d{1,3}\s*(?:[,/ ]|and)?\s*)+)$""")
    private val REP_NUMBER = Regex("""\b\d{1,3}\b""")
    private val MONTHS = listOf(
        "january",
        "february",
        "march",
        "april",
        "may",
        "june",
        "july",
        "august",
        "september",
        "october",
        "november",
        "december"
    )
}
