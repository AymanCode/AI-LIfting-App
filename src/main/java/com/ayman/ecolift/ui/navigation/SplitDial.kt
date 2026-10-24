package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.splitAccentColor
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The rotation as a board-game spinner (spec §02): the splits are printed wedges on a kraft disc,
 * a paper arrow on a brass pin points at the split up next. Drag to spin it and peek; release snaps
 * to a wedge; a tap nudges one split on; the back-to-today note brings it home. Peeking never
 * changes the rotation.
 */
@Composable
fun SplitDial(
    splits: List<SplitType>,
    currentIndex: Int,
    cycleEnabled: Boolean,
    onAddSplit: () -> Unit,
) {
    val count = splits.size
    val current = floorMod(currentIndex, count)
    val segment = if (count == 0) 360f else 360f / count
    val canSpin = cycleEnabled && count > 1
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val nameMeasurer = rememberTextMeasurer()

    // The arrow's heading in degrees, clockwise from 12 o'clock. Wedge i sits at i * segment.
    val heading = remember(splits, currentIndex) { Animatable(current * segment) }
    var peek by remember(splits, currentIndex) { mutableIntStateOf(0) }
    LaunchedEffect(splits, currentIndex, cycleEnabled) {
        peek = 0
        heading.snapTo(current * segment)
    }

    fun settle(target: Float) {
        val snapped = (target / segment).roundToInt() * segment
        peek = floorMod((snapped / segment).roundToInt() - current, count)
        scope.launch { heading.animateTo(snapped, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val disc = if (maxWidth < 340.dp) 212.dp else 232.dp
        val peekIndex = floorMod(current + peek, count)
        val shown = splits.getOrNull(peekIndex)
        Box(Modifier.fillMaxWidth().height(disc + 54.dp)) {
            // Disc and printed wedges.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .size(disc)
                    .shadow(16.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.6f))
                    .clip(CircleShape)
                    .drawBehind { drawCircle(StickerInk.kraft) }
                    .padding(9.dp)
                    .alpha(if (cycleEnabled || count == 0) 1f else 0.62f)
                    .semantics {
                        contentDescription = when {
                            count == 0 -> "Rotation dial, no splits yet"
                            !cycleEnabled -> "Rotation dial, cycle off"
                            else -> "Rotation dial, today: ${splits[current].name}"
                        }
                        if (canSpin) {
                            role = Role.Button
                            onClick(label = "Peek at the next split") { settle(heading.value + segment); true }
                        }
                    }
                    .pointerInput(canSpin, splits, currentIndex) {
                        if (!canSpin) return@pointerInput
                        detectTapGestures { settle(heading.value + segment) }
                    }
                    .pointerInput(canSpin, splits, currentIndex) {
                        if (!canSpin) return@pointerInput
                        var last = 0f
                        detectDragGestures(
                            onDragStart = { pos -> last = pointerDegrees(pos, size.width, size.height) },
                            onDrag = { change, _ ->
                                val now = pointerDegrees(change.position, size.width, size.height)
                                var delta = now - last
                                while (delta > 180f) delta -= 360f
                                while (delta < -180f) delta += 360f
                                last = now
                                change.consume()
                                scope.launch { heading.snapTo(heading.value + delta) }
                            },
                            onDragEnd = { settle(heading.value) },
                            onDragCancel = { settle(heading.value) },
                        )
                    },
            ) {
                SpinnerFace(splits = splits, cycleEnabled = cycleEnabled)
            }

            if (count > 0) {
                // The paper arrow turns about the pin.
                Canvas(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp + disc / 2 - 60.dp)
                        .size(width = 32.dp, height = 92.dp)
                        .alpha(if (cycleEnabled) 1f else 0.25f)
                        .graphicsLayer {
                            rotationZ = heading.value
                            transformOrigin = TransformOrigin(0.5f, 60f / 92f)
                        },
                ) {
                    val s = size.width / 32f
                    fun o(x: Float, y: Float) = Offset((x + 16f) * s, (y + 60f) * s)
                    val arrow = Path().apply {
                        moveTo(o(0f, -58f).x, o(0f, -58f).y)
                        lineTo(o(13f, -34f).x, o(13f, -34f).y)
                        lineTo(o(4.5f, -34f).x, o(4.5f, -34f).y)
                        lineTo(o(6f, 18f).x, o(6f, 18f).y)
                        lineTo(o(0f, 30f).x, o(0f, 30f).y)
                        lineTo(o(-6f, 18f).x, o(-6f, 18f).y)
                        lineTo(o(-4.5f, -34f).x, o(-4.5f, -34f).y)
                        lineTo(o(-13f, -34f).x, o(-13f, -34f).y)
                        close()
                    }
                    drawPath(arrow, Color.Black.copy(alpha = 0.35f), style = Stroke(3.5f * s))
                    drawPath(arrow, StickerInk.noteCream)
                    drawLine(StickerInk.ink.copy(alpha = 0.12f), o(0f, -58f), o(0f, 30f), strokeWidth = 1f * s)
                }
                // Brass split pin.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp + disc / 2 - 11.dp)
                        .size(22.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    0f to Color(0xFFF6DFA8),
                                    0.12f to Color(0xFFF6DFA8),
                                    0.35f to Color(0xFFD9B36C),
                                    0.7f to Color(0xFFA57D3A),
                                    1f to Color(0xFF6E5022),
                                    center = Offset(size.width * 0.35f, size.height * 0.3f),
                                    radius = size.width * 0.8f,
                                ),
                            )
                            drawCircle(Color(0xFF5E451D).copy(alpha = 0.8f), style = Stroke(1.dp.toPx()))
                        },
                )
            }

            if (!cycleEnabled && count > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp + disc / 2 - 19.dp)
                        .rotate(-8f)
                        .size(width = 270.dp, height = 38.dp)
                        .clip(WashiShape)
                        .drawBehind { drawRect(StickerInk.cream.copy(alpha = 0.86f)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("cycle off", style = StickerType.hand(24.sp, StickerInk.ink))
                }
            }

            if (shown != null) {
                val nameWidth = if (disc < 230.dp) 118.dp else 130.dp
                val nameWidthPx = with(LocalDensity.current) { nameWidth.roundToPx() }
                // The whole name, shrunk to fit beside the disc rather than cut at a word.
                val nameSize = remember(shown.name, nameWidthPx) {
                    var size = 22f
                    while (size > 13f &&
                        nameMeasurer.measure(
                            shown.name,
                            style = StickerType.display(size.sp, EspressoPalette.ink, (-0.4).sp),
                            softWrap = false,
                            maxLines = 1,
                        ).size.width > nameWidthPx
                    ) {
                        size -= 0.5f
                    }
                    size
                }
                Column(Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp).alpha(if (cycleEnabled) 1f else 0.25f)) {
                    Text(peekTag(peek, count), style = StickerType.hand(20.sp, StickerInk.print))
                    Text(
                        shown.name,
                        style = StickerType.display(nameSize.sp, EspressoPalette.ink, (-0.4).sp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp).width(nameWidth),
                    )
                    val data = SpanStyle(fontFamily = StickerType.display.fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = EspressoPalette.ink)
                    Text(
                        buildAnnotatedString {
                            withStyle(data) { append(shown.exerciseCount.toString()) }
                            append(" ex · ")
                            val ran = shown.lastRunLabel
                            if (ran.startsWith("last ")) {
                                append("last ")
                                withStyle(data) { append(ran.removePrefix("last ")) }
                            } else {
                                append(ran)
                            }
                        },
                        style = StickerType.body(11.sp, EspressoPalette.dim),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            StickyNote(
                color = StickerInk.noteCream,
                tiltDeg = 5f,
                tape = EspressoPalette.legs.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp)
                    .semantics { contentDescription = "Add a split"; role = Role.Button }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAddSplit),
            ) {
                Text("+ add a split", style = StickerType.hand(19.sp, StickerInk.ink))
            }

            if (peek != 0 && canSpin) {
                StickyNote(
                    color = StickerInk.amber,
                    tiltDeg = -4f,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 6.dp)
                        .semantics { contentDescription = "Back to today"; role = Role.Button }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            val home = current * segment
                            val turns = ((heading.value - home) / 360f).roundToInt()
                            settle(home + turns * 360f)
                        },
                ) {
                    Text("back to today", style = StickerType.hand(19.sp, StickerInk.ink))
                }
            }
        }
    }
}

@Composable
private fun SpinnerFace(splits: List<SplitType>, cycleEnabled: Boolean) {
    val measurer = rememberTextMeasurer()
    val count = splits.size
    Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        if (count == 0) {
            drawCircle(StickerInk.kraftCard, r, c)
            drawCircle(
                StickerInk.printDim, r * 0.82f, c,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))),
            )
            drawGrain()
            return@Canvas
        }
        val segment = 360f / count
        splits.indices.forEach { i ->
            val color = if (cycleEnabled) splitAccentColor(splits[i].colorIndex) else Color(0xFF6F665D)
            if (count == 1) {
                drawCircle(color, r, c)
            } else {
                drawArc(color, startAngle = -90f + i * segment - segment / 2f, sweepAngle = segment, useCenter = true)
            }
        }
        if (count > 1) {
            splits.indices.forEach { i ->
                val a = (-90f + i * segment - segment / 2f) * PI.toFloat() / 180f
                drawLine(StickerInk.kraft, c, Offset(c.x + r * cos(a), c.y + r * sin(a)), strokeWidth = 3.dp.toPx())
            }
        }
        for (t in 0 until 48) {
            val a = (t * 7.5f - 90f) * PI.toFloat() / 180f
            val r1 = r * (100f / 107f)
            val r2 = r * (if (t % 6 == 0) 94f else 97f) / 107f
            drawLine(StickerInk.ink.copy(alpha = 0.32f), Offset(c.x + r1 * cos(a), c.y + r1 * sin(a)), Offset(c.x + r2 * cos(a), c.y + r2 * sin(a)), strokeWidth = 1.2.dp.toPx())
        }
        drawCircle(
            StickerInk.ink.copy(alpha = 0.28f), r * 88f / 107f, c,
            style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))),
        )
        val nameSize = if (count == 1) 16f else 13.5f
        val labelRadius = r * (if (count == 1) 80f else 74f) / 107f
        val rimLimit = r * 0.95f
        splits.forEachIndexed { i, split ->
            val name = split.name.uppercase()
            // Letter spacing shrinks with the type so a smaller size always narrows the name.
            fun layoutAt(size: Float, room: Int? = null) = measurer.measure(
                name,
                style = StickerType.display(size.sp, StickerInk.ink, (1.2f * size / nameSize).sp),
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
                constraints = if (room == null) Constraints() else Constraints(maxWidth = room),
            )
            var size = nameSize
            var layout = layoutAt(size)
            val halfHeight = layout.size.height / 2f
            // Room along the rim: inside the disc and, with three or more splits, inside the wedge.
            var room = 2f * sqrt(max(0f, rimLimit * rimLimit - (labelRadius + halfHeight) * (labelRadius + halfHeight)))
            if (count > 2) room = min(room, 2f * (labelRadius - halfHeight) * tan((segment / 2f - 2f) * PI.toFloat() / 180f))
            while (layout.size.width > room && size > 9f) {
                size = max(9f, size - 0.5f)
                layout = layoutAt(size)
            }
            if (layout.size.width > room) layout = layoutAt(size, room.toInt())
            val deg = i * segment
            val a = (deg - 90f) * PI.toFloat() / 180f
            val p = Offset(c.x + labelRadius * cos(a), c.y + labelRadius * sin(a))
            // Printed along the rim like a board-game spinner; names on the lower half turn to stay upright.
            rotate(if (deg > 90f && deg < 270f) deg - 180f else deg, pivot = p) {
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
        }
        drawGrain(0.06f)
    }
}

private fun peekTag(peek: Int, count: Int): String = when {
    peek == 0 -> "up next"
    peek == 1 -> "then"
    peek == count - 1 -> "last in the cycle"
    else -> "after that"
}

/** Clockwise degrees from 12 o'clock for a pointer in a box. */
private fun pointerDegrees(position: Offset, width: Int, height: Int): Float {
    val x = position.x - width / 2f
    val y = position.y - height / 2f
    return (atan2(y, x) * 180f / PI.toFloat()) + 90f
}

private fun floorMod(value: Int, modulus: Int): Int {
    if (modulus <= 0) return 0
    return ((value % modulus) + modulus) % modulus
}
