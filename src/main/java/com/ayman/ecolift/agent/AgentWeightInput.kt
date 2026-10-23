package com.ayman.ecolift.agent

import com.ayman.ecolift.data.WeightLbs

internal object AgentWeightInput {
    fun parse(raw: String, unit: String = ""): Int? {
        val value = raw.toDoubleOrNull() ?: return null
        val pounds = if (unit.startsWith("k", ignoreCase = true)) value * 2.20462 else value
        if (!pounds.isFinite() || pounds <= 0 || pounds * 10 > Int.MAX_VALUE) return null
        return WeightLbs.fromLbs(pounds).takeIf { it > 0 }
    }

    fun hasInvalidLoad(text: String): Boolean = LOAD.findAll(text).any { match ->
        parse(match.groupValues[1], match.groupValues[2]) == null
    }

    private val LOAD = Regex(
        """(?i)(?<![\d.])(-?\d+(?:\.\d+)?)\s*(?:(lbs?|pounds?|kg|kilos?)\b|(?:x|for)\s*\d+)""",
    )
}
