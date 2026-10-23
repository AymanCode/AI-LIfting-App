package com.ayman.ecolift.agent.assist

import kotlin.math.abs

/**
 * Bounded, deterministic check that Groq prose does not contradict the facts.
 * It only validates the small fact set we passed in — it is NOT a general truth engine.
 * Any load/percent claim must match an allowed value; PR/trend claims must match the data.
 */
object AnalysisValidator {
    private val loadRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:lbs?|pounds?)""", RegexOption.IGNORE_CASE)
    private val percentRegex = Regex("""(\d+(?:\.\d+)?)\s*%""")
    private val recentLoadRegex = Regex("""(\d+(?:\.\d+)?)x""", RegexOption.IGNORE_CASE)
    private val prRegex = Regex("""\b(pr|personal record|all-time|lifetime best)\b""", RegexOption.IGNORE_CASE)
    private val upRegex = Regex("""\b(progress\w*|improv\w*|gain\w*|stronger|increase\w*|trending up|going up)\b""", RegexOption.IGNORE_CASE)
    private val downRegex = Regex("""\b(declin\w*|regress\w*|weaker|decreas\w*|drop\w*|trending down|going down)\b""", RegexOption.IGNORE_CASE)

    private const val LOAD_TOLERANCE = 1.0
    private const val PERCENT_TOLERANCE = 1.0
    private const val TREND_BAND = 2f

    fun validate(facts: ExerciseAnalysisFacts, prose: String): Boolean {
        val allowedLoads = buildList {
            facts.prDisplayLbs?.let { add(it.toDouble()) }
            facts.est1RmDisplayLbs?.let { add(it.toDouble()) }
            facts.recentSessions.forEach { s ->
                recentLoadRegex.findAll(s).forEach { add(it.groupValues[1].toDouble()) }
            }
        }
        for (m in loadRegex.findAll(prose)) {
            val v = m.groupValues[1].toDouble()
            if (allowedLoads.none { abs(it - v) <= LOAD_TOLERANCE }) return false
        }

        val allowedPct = listOfNotNull(facts.deltaPercent?.let { abs(it).toDouble() })
        for (m in percentRegex.findAll(prose)) {
            val v = m.groupValues[1].toDouble()
            if (allowedPct.none { abs(it - v) <= PERCENT_TOLERANCE }) return false
        }

        if (prRegex.containsMatchIn(prose) && facts.prDisplayLbs == null) return false

        val d = facts.deltaPercent
        if (upRegex.containsMatchIn(prose) && (d == null || d < TREND_BAND)) return false
        if (downRegex.containsMatchIn(prose) && (d == null || d > -TREND_BAND)) return false

        return true
    }
}
