package com.ayman.ecolift.data

private val BODYWEIGHT_NAME_PATTERNS = listOf(
    Regex("""\bpull[\s-]?ups?\b""", RegexOption.IGNORE_CASE),
    Regex("""\bchin[\s-]?ups?\b""", RegexOption.IGNORE_CASE),
    Regex("""\bpush[\s-]?ups?\b""", RegexOption.IGNORE_CASE),
    Regex("""\bdips?\b""", RegexOption.IGNORE_CASE),
    Regex("""\bmuscle[\s-]?ups?\b""", RegexOption.IGNORE_CASE),
    Regex("""\binverted\s+rows?\b""", RegexOption.IGNORE_CASE),
    Regex("""\bplanks?\b""", RegexOption.IGNORE_CASE),
)

private val LOADED_MACHINE_PATTERN =
    Regex("""\b(machine|pulldowns?|pressdowns?|pushdowns?)\b""", RegexOption.IGNORE_CASE)

fun looksLikeBodyweightExerciseName(name: String): Boolean {
    if (LOADED_MACHINE_PATTERN.containsMatchIn(name)) return false
    return BODYWEIGHT_NAME_PATTERNS.any { it.containsMatchIn(name) }
}

fun normalizedBodyweightLoad(weightLbs: Int?): Int? =
    weightLbs?.takeIf { it > 0 }

fun normalizedUserBodyweightLbs(bodyweightLbs: Int?): Int? =
    bodyweightLbs?.takeIf { it > 0 }
