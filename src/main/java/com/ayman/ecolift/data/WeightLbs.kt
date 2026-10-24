package com.ayman.ecolift.data

import com.ayman.ecolift.web.util.formatTenths
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
            formatTenths(rounded)
        }
    }

    fun parseInputToStorage(input: String): Int? {
        val cleaned = sanitizeInput(input).trim()
        if (cleaned.isEmpty() || cleaned == ".") return null
        val normalized = cleaned.removeSuffix(".")
        val numeric = normalized.toDoubleOrNull() ?: return null
        return fromLbs(numeric)
    }

    fun fromLbs(value: Double): Int = (value * SCALE).roundToInt()

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
