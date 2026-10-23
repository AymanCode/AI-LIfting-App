package com.ayman.ecolift.web.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** Replaces String.format(Locale.US, "%,d", value) for wasm: 1234567 -> "1,234,567". */
fun groupThousands(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val digits = abs(value).toString()
    val grouped = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) grouped.append(',')
        grouped.append(c)
    }
    return "$sign$grouped"
}

fun groupThousands(value: Int): String = groupThousands(value.toLong())

/** Replaces String.format("%.<decimals>f", value): fixed decimals, ties round away from zero. */
fun formatFixed(value: Double, decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (abs(value) * scale).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / scale
    if (decimals == 0) return "$sign$whole"
    val frac = (scaled % scale).toString().padStart(decimals, '0')
    return "$sign$whole.$frac"
}

/** Replaces String.format(Locale.US, "%,.0f", value): round to whole + thousands separators. */
fun formatWholeGrouped(value: Double): String {
    val rounded = value.roundToLong()
    return groupThousands(rounded)
}

/** Recorded loads have tenths precision. Keep it so displayed receipts sum to their total. */
fun formatRecordedVolumeLbs(value: Double): String {
    val tenths = (value * 10).roundToLong()
    val whole = groupThousands(tenths / 10)
    val fraction = abs(tenths % 10)
    return if (fraction == 0L) whole else "$whole.$fraction"
}

fun formatVolumeLbsShort(lbs: Long): String = when {
    lbs >= 1_000_000 -> "${formatFixed(lbs / 1_000_000.0, 1)}M"
    lbs >= 10_000 -> "${groupThousands(lbs / 1_000)}k"
    else -> groupThousands(lbs)
}
