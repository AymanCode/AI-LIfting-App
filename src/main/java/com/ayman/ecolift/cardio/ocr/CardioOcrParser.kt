package com.ayman.ecolift.cardio.ocr

import kotlin.math.min

data class CardioOcrResult(
    val recognizedCardioScreen: Boolean,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val calories: Int? = null,
    val avgHeartRate: Int? = null,
    val avgSpeed: Double? = null,
    val machineType: String? = null,
    val confidence: Double = 0.0,
    val rawText: String = "",
)

object CardioOcrParser {
    private val durationRegex = Regex("""\b(\d{1,2})\s*[:.]\s*(\d{2})(?:\s*[:.]\s*(\d{2}))?\b""")
    private val labeledPartialDurationRegex = Regex("""\b(?:total\s+time|time|duration)\b.*?[:.]\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE)
    private val labeledBareDurationRegex = Regex("""\b(?:total\s+time|time|duration)\b\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)
    private val decimalRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)""")
    private val integerRegex = Regex("""\b(\d{1,4})\b""")

    fun parse(rawText: String): CardioOcrResult {
        val normalized = rawText
            .replace("\r", "\n")
            .replace("|", "I")
            .replace(Regex("""(?i)\bd\s*stanc[e3]\b"""), "distance")
            .replace(Regex("""(?i)\bdstanc[e3]\b"""), "distance")
            .replace(Regex("""(?i)\bcalorien\b"""), "calories")
            .replace(Regex("""(?i)\bheart\s*ate\b"""), "heart rate")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
        val lower = normalized.lowercase()
        val lines = normalized
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val lineContexts = lines.withFollowingValues()

        val machineType = detectMachineType(lower)
        val duration = parseDuration(lineContexts, normalized)
        val distance = parseDistance(lineContexts, lower)
        val calories = parseCalories(lineContexts)
        val heartRate = parseHeartRate(lineContexts)
        val speed = parseSpeed(lineContexts, lower)

        val fieldCount = listOf(duration, distance, calories, heartRate, speed).count { it != null }
        val hasCardioLanguage = listOf(
            "time", "dist", "distance", "cal", "kcal", "heart", "hr", "bpm",
            "speed", "pace", "incline", "mile", "km", "treadmill", "elliptical",
            "bike", "row", "stair",
        ).any { lower.contains(it) }

        val recognized = hasCardioLanguage && fieldCount >= 2
        val confidence = when {
            !hasCardioLanguage -> 0.0
            else -> min(0.95, 0.25 + fieldCount * 0.18 + if (machineType != null) 0.08 else 0.0)
        }

        return CardioOcrResult(
            recognizedCardioScreen = recognized,
            durationSec = duration,
            distanceM = distance,
            calories = calories,
            avgHeartRate = heartRate,
            avgSpeed = speed,
            machineType = machineType,
            confidence = confidence,
            rawText = normalized,
        )
    }

    private fun parseDuration(lines: List<String>, allText: String): Int? {
        val labeledLine = lines.firstNotNullOfOrNull { line ->
            if (line.contains("time", ignoreCase = true) || line.contains("duration", ignoreCase = true)) {
                durationRegex.find(line)?.let(::durationMatchToSeconds)
                    ?: labeledPartialDurationRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: labeledBareDurationRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            } else {
                null
            }
        }
        return labeledLine ?: durationRegex.find(allText)?.let(::durationMatchToSeconds)
    }

    private fun durationMatchToSeconds(match: MatchResult): Int? {
        val first = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val second = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val third = match.groupValues.getOrNull(3)?.toIntOrNull()
        return if (third != null) {
            first * 3600 + second * 60 + third
        } else {
            first * 60 + second
        }
    }

    private fun parseDistance(lines: List<String>, lower: String): Double? {
        val line = lines.firstOrNull {
            decimalRegex.containsMatchIn(it) &&
                (
                    it.contains("dist", ignoreCase = true) ||
                        it.contains("mile", ignoreCase = true) ||
                        it.contains(" km", ignoreCase = true) ||
                        it.endsWith("km", ignoreCase = true) ||
                        it.endsWith("mi", ignoreCase = true)
                    )
        } ?: return null
        val value = decimalRegex.find(line)?.groupValues?.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: return null
        val lineLower = line.lowercase()
        return when {
            lineLower.contains("km") -> value * 1000.0
            lineLower.contains("mi") || lineLower.contains("mile") -> value * 1609.344
            lower.contains(" km") -> value * 1000.0
            else -> value * 1609.344
        }
    }

    private fun parseCalories(lines: List<String>): Int? {
        val line = lines.firstOrNull {
            integerRegex.containsMatchIn(it) &&
                (
                    it.contains("cal", ignoreCase = true) ||
                        it.contains("kcal", ignoreCase = true) ||
                        it.contains("kj", ignoreCase = true)
                    )
        } ?: return null
        val value = integerRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return if (line.contains("kj", ignoreCase = true)) {
            (value / 4.184).toInt()
        } else {
            value
        }
    }

    private fun parseHeartRate(lines: List<String>): Int? {
        val line = lines.firstOrNull {
            integerRegex.containsMatchIn(it) &&
                (
                    it.contains("heart", ignoreCase = true) ||
                        it.contains("bpm", ignoreCase = true) ||
                        Regex("""\bhr\b""", RegexOption.IGNORE_CASE).containsMatchIn(it)
                    )
        } ?: return null
        return integerRegex.findAll(line)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .firstOrNull { it in 35..230 }
    }

    private fun parseSpeed(lines: List<String>, lower: String): Double? {
        val line = lines.firstOrNull {
            decimalRegex.containsMatchIn(it) &&
                (
                    it.contains("speed", ignoreCase = true) ||
                        it.contains("mph", ignoreCase = true) ||
                        it.contains("km/h", ignoreCase = true) ||
                        it.contains("kph", ignoreCase = true)
                    )
        } ?: return null
        val value = decimalRegex.find(line)?.groupValues?.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: return null
        val lineLower = line.lowercase()
        return when {
            lineLower.contains("km/h") || lineLower.contains("kph") -> value / 3.6
            lineLower.contains("mph") -> value * 0.44704
            lower.contains("km/h") -> value / 3.6
            else -> value * 0.44704
        }
    }

    private fun detectMachineType(lower: String): String? = when {
        lower.contains("treadmill") || lower.contains("incline") || lower.contains("pace") -> "treadmill"
        lower.contains("elliptical") || lower.contains("cross trainer") -> "elliptical"
        lower.contains("bike") || lower.contains("cycling") || lower.contains("rpm") -> "bike"
        lower.contains("row") || lower.contains("rowing") || lower.contains("stroke") -> "rower"
        lower.contains("stair") || lower.contains("stepmill") -> "stair_climber"
        else -> null
    }

    private fun List<String>.withFollowingValues(): List<String> =
        flatMapIndexed { index, line ->
            val next = getOrNull(index + 1)
            if (next != null && decimalRegex.containsMatchIn(next) && !decimalRegex.containsMatchIn(line)) {
                listOf(line, "$line $next")
            } else {
                listOf(line)
            }
        }
}
