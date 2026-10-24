package com.ayman.ecolift.data.splits

import com.ayman.ecolift.web.util.formatFixed
import com.ayman.ecolift.web.util.formatVolumeLbsShort
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun tickerSegments(
    monthSessions: Int,
    monthVolumeLbs: Long,
    isCurrentMonth: Boolean,
    dayOfMonth: Int,
    projected: Int,
    monthlyAvg: Double,
    lifetimeCount: Int,
    tierName: String,
): List<String> = buildList {
    add("$monthSessions ${if (monthSessions == 1) "SESSION" else "SESSIONS"}")
    add("${formatVolumeLbsShort(monthVolumeLbs).uppercase()} LB MOVED")
    if (isCurrentMonth && dayOfMonth >= 7 && monthSessions > 0) {
        add("ON PACE FOR ~$projected")
    }
    add("AVG ${formatFixed(monthlyAvg, 1)}/MO")
    // The lifetime count and the shine tier left the product with Espresso; the parameters stay
    // so callers keep compiling.
}

internal fun dialSnapIndex(totalDeg: Float, segmentCount: Int): Int {
    if (segmentCount <= 0) return 0
    val segmentDegrees = 360f / segmentCount
    return floorMod((-totalDeg / segmentDegrees).roundToInt(), segmentCount)
}

internal fun dialAngleFor(index: Int, segmentCount: Int): Float {
    if (segmentCount <= 0) return 0f
    return index * -(360f / segmentCount)
}

internal fun dialShortestHome(peekIndex: Int, segmentCount: Int): Int {
    if (segmentCount <= 1) return 0
    val normalized = floorMod(peekIndex, segmentCount)
    val backward = -normalized
    val forward = segmentCount - normalized
    return if (forward <= abs(backward)) forward else backward
}

internal fun isDialTap(maxAngularMoveDeg: Float): Boolean = abs(maxAngularMoveDeg) < 8f

private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus
