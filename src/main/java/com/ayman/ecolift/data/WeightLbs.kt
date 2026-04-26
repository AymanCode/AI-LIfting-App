package com.ayman.ecolift.data

import java.util.Locale
import kotlin.math.roundToInt

object WeightLbs {
    private const val SCALE = 10

    fun fromWholePounds(value: Int?): Int? = value?.times(SCALE)

    fun toLbs(value: Int?): Double = (value ?: 0).toDouble() / SCALE

    fun formatStored(value: Int?): String {
        if (value == null) return ""
        val rounded = value
        return if (rounded % SCALE == 0) {
            (rounded / SCALE).toString()
        } else {
            String.format(Locale.US, "%.1f", rounded.toDouble() / SCALE)
        }
    }

    fun parseInputToStorage(input: String): Int? {
        val cleaned = sanitizeInput(input).trim()
        if (cleaned.isEmpty() || cleaned == ".") return null
        val normalized = cleaned.removeSuffix(".")
        val numeric = normalized.toDoubleOrNull() ?: return null
        return (numeric * SCALE).roundToInt()
    }

    fun sanitizeInput(input: String): String {
        val builder = StringBuilder()
        var sawDot = false
        input.forEach { char ->
            when {
                char.isDigit() -> builder.append(char)
                char == '.' && !sawDot -> {
                    builder.append(char)
                    sawDot = true
                }
            }
        }
        return builder.toString()
    }
}
