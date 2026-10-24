package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** The drawn range: the data's own, widened symmetrically to at least [minSpan]. */
internal fun sparklineBounds(values: List<Float>, minSpan: Float): Pair<Float, Float> {
    val lo = values.min(); val hi = values.max()
    val span = hi - lo
    if (span >= minSpan) return lo to hi
    val mid = (lo + hi) / 2f
    return (mid - minSpan / 2f) to (mid + minSpan / 2f)
}

/**
 * [minSpan] null keeps the original scale: the series' own range, at least 1, from its minimum
 * at the bottom edge. A floor (the album's Deep cuts) draws within [sparklineBounds] instead.
 */
@Composable
internal fun MiniSparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier, minSpan: Float? = null) {
    if (values.size < 2) {
        Box(modifier)
        return
    }

    Canvas(modifier) {
        val minValue: Float
        val range: Float
        if (minSpan == null) {
            minValue = values.min()
            range = (values.max() - minValue).coerceAtLeast(1f)
        } else {
            val (lo, hi) = sparklineBounds(values, minSpan)
            minValue = lo
            range = hi - lo
        }
        val stepX = size.width / (values.size - 1)
        val padY = 2f
        val path = Path()

        values.forEachIndexed { index, value ->
            val x = stepX * index
            val yNorm = (value - minValue) / range
            val y = size.height - padY - yNorm * (size.height - padY * 2)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
