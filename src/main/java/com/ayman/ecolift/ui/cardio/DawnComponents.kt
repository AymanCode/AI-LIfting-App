package com.ayman.ecolift.ui.cardio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.CardioFieldRules
import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.cardio.DayDot
import com.ayman.ecolift.cardio.ProgressPoint
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.viewmodel.DataPart
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.*

fun machineIcon(type: CardioActivityType): ImageVector = when (type) {
    CardioActivityType.TREADMILL -> Icons.Filled.Speed
    CardioActivityType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    CardioActivityType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    CardioActivityType.ROW -> Icons.Filled.Rowing
    CardioActivityType.SWIM -> Icons.Filled.Pool
    CardioActivityType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    CardioActivityType.ELLIPTICAL -> Icons.Filled.AllInclusive
    CardioActivityType.STAIR_CLIMBER -> Icons.Filled.Stairs
    CardioActivityType.OTHER -> Icons.Filled.MoreHoriz
}

internal fun scrubTicks(dragPx: Float, tickPx: Float): Int {
    if (tickPx <= 0f) return 0
    val ticks = floor(abs(dragPx) / tickPx).toInt()
    return if (dragPx >= 0f) ticks else -ticks
}

fun sessionMetaLine(session: CardioSession): String {
    val type = CardioActivitySpecs.fromStoredType(session.activityType)
    val distancePart = session.distanceM?.let { distM ->
        val distVal = CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, distM)
        val formatted = CardioFieldRules.display(CardioEntryField.DISTANCE_MILES, type, distVal)
        val unit = if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) "m" else "mi"
        "$formatted $unit"
    }

    val mainMetricPart: String? = when (type) {
        CardioActivityType.TREADMILL, CardioActivityType.RUN, CardioActivityType.WALK -> {
            if (session.avgSpeed != null) {
                val mph = CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, session.avgSpeed)
                "${CardioFieldRules.display(CardioEntryField.AVG_SPEED_MPH, type, mph)} mph"
            } else {
                val pace = CardioInsights.derivedPaceSec(session)
                if (pace != null) {
                    val m = pace / 60
                    val s = pace % 60
                    "$m:${s.toString().padStart(2, '0')} /mi"
                } else {
                    null
                }
            }
        }
        CardioActivityType.ROW -> {
            val split = session.paceSecPer500m ?: CardioInsights.derivedPaceSec(session)
            if (split != null) {
                val m = split / 60
                val s = split % 60
                "$m:${s.toString().padStart(2, '0')} /500m"
            } else if (session.strokeRateSpm != null) {
                "${session.strokeRateSpm} spm"
            } else {
                null
            }
        }
        CardioActivityType.BIKE -> {
            if (session.avgPowerWatts != null) {
                "${session.avgPowerWatts} W"
            } else if (session.cadenceRpm != null) {
                "${session.cadenceRpm} rpm"
            } else if (session.avgSpeed != null) {
                val mph = CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, session.avgSpeed)
                "${CardioFieldRules.display(CardioEntryField.AVG_SPEED_MPH, type, mph)} mph"
            } else {
                null
            }
        }
        CardioActivityType.ELLIPTICAL -> {
            if (session.avgPowerWatts != null) {
                "${session.avgPowerWatts} W"
            } else if (session.cadenceRpm != null) {
                "${session.cadenceRpm} rpm"
            } else if (session.resistanceLevel != null) {
                "lvl ${CardioFieldRules.display(CardioEntryField.RESISTANCE_LEVEL, type, session.resistanceLevel)}"
            } else {
                null
            }
        }
        CardioActivityType.STAIR_CLIMBER -> {
            if (session.floors != null) "${session.floors} floors" else null
        }
        CardioActivityType.SWIM -> {
            val pace = CardioInsights.derivedPaceSec(session)
            if (pace != null) {
                val m = pace / 60
                val s = pace % 60
                "$m:${s.toString().padStart(2, '0')} /100m"
            } else {
                null
            }
        }
        CardioActivityType.OTHER -> {
            if (session.avgSpeed != null) {
                val mph = CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, session.avgSpeed)
                "${CardioFieldRules.display(CardioEntryField.AVG_SPEED_MPH, type, mph)} mph"
            } else {
                null
            }
        }
    }

    val inclinePart = session.avgInclinePercent?.let { inc ->
        "${CardioFieldRules.display(CardioEntryField.AVG_INCLINE_PERCENT, type, inc)}%"
    }

    val hrPart = session.avgHeartRate?.let { hr ->
        "$hr bpm"
    }

    return listOfNotNull(distancePart, mainMetricPart, inclinePart, hrPart).joinToString(" · ")
}

@Composable
fun DawnTitle(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 44.sp,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = SheetSerifFontFamily,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Light,
        fontSize = size,
        lineHeight = (size.value * 1.1f).sp,
        color = DawnPalette.ink,
    )
}

/** Dawn's kicker: small tracked caps in the quiet ink, for a state that needs a name and nothing more. */
@Composable
fun DawnEyebrow(text: String, modifier: Modifier = Modifier, color: Color = DawnPalette.muted) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontFamily = LogUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.8.sp,
        color = color,
        maxLines = 1,
    )
}

/** Dawn's data line: numbers, dates and names in serif ink, the words between them quiet. */
fun dawnDataLine(parts: List<DataPart>, size: TextUnit = 12.sp): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        val style = if (part.isData) {
            SpanStyle(fontFamily = SheetSerifFontFamily, fontSize = (size.value + 1f).sp, color = DawnPalette.ink)
        } else {
            SpanStyle(fontFamily = LogUiFontFamily, fontSize = size, color = DawnPalette.muted)
        }
        withStyle(style) { append(part.text) }
    }
}

@Composable
fun DawnPill(
    text: String,
    subtext: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled && !loading) 1f else 0.5f
    val bg = DawnPalette.accent.copy(alpha = alpha)
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape,
        color = bg,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = DawnPalette.pillText,
                    strokeWidth = 2.dp,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = text,
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = DawnPalette.pillText,
                    )
                    if (!subtext.isNullOrBlank()) {
                        Text(
                            text = subtext,
                            fontFamily = LogUiFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = DawnPalette.pillText.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DawnGhostPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, DawnPalette.line),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = LogUiFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = DawnPalette.ink,
            )
        }
    }
}

@Composable
fun DawnLink(
    text: String,
    onClick: () -> Unit,
) {
    val prefix = if (text.startsWith("or ")) "or " else ""
    val linkText = if (prefix.isNotEmpty()) text.removePrefix("or ") else text

    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                fontFamily = LogUiFontFamily,
                fontSize = 13.sp,
                color = DawnPalette.muted,
            )
        }
        Text(
            text = linkText,
            fontFamily = LogUiFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = DawnPalette.ink,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/**
 * "or [camera] scan the machine": the scan entry carries a drawn camera instead of the sentence
 * that used to explain what scanning does (audit pass 2026-09-25).
 */
@Composable
fun DawnScanLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "or scan the machine" }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "or",
            fontFamily = LogUiFontFamily,
            fontSize = 13.sp,
            color = DawnPalette.muted,
        )
        Spacer(Modifier.width(8.dp))
        CameraGlyph(Modifier.size(width = 18.dp, height = 15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "scan the machine",
            fontFamily = LogUiFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = DawnPalette.ink,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** A line camera: body, viewfinder bump, lens, and a small flash dot in the accent. */
@Composable
fun CameraGlyph(modifier: Modifier = Modifier, color: Color = DawnPalette.ink) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val bodyTop = size.height * 0.24f
        val bumpW = size.width * 0.34f
        val bumpLeft = size.width * 0.30f
        val body = Path().apply {
            moveTo(stroke / 2f + 2.dp.toPx(), bodyTop)
            lineTo(bumpLeft, bodyTop)
            lineTo(bumpLeft + size.width * 0.06f, stroke / 2f)
            lineTo(bumpLeft + bumpW - size.width * 0.06f, stroke / 2f)
            lineTo(bumpLeft + bumpW, bodyTop)
            lineTo(size.width - stroke / 2f - 2.dp.toPx(), bodyTop)
            quadraticTo(size.width - stroke / 2f, bodyTop, size.width - stroke / 2f, bodyTop + 2.dp.toPx())
            lineTo(size.width - stroke / 2f, size.height - stroke / 2f - 2.dp.toPx())
            quadraticTo(size.width - stroke / 2f, size.height - stroke / 2f, size.width - stroke / 2f - 2.dp.toPx(), size.height - stroke / 2f)
            lineTo(stroke / 2f + 2.dp.toPx(), size.height - stroke / 2f)
            quadraticTo(stroke / 2f, size.height - stroke / 2f, stroke / 2f, size.height - stroke / 2f - 2.dp.toPx())
            lineTo(stroke / 2f, bodyTop + 2.dp.toPx())
            quadraticTo(stroke / 2f, bodyTop, stroke / 2f + 2.dp.toPx(), bodyTop)
            close()
        }
        drawPath(body, color = color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val lensCenter = Offset(size.width / 2f, bodyTop + (size.height - bodyTop) / 2f)
        drawCircle(color = color, radius = (size.height - bodyTop) * 0.30f, center = lensCenter, style = Stroke(width = stroke))
        drawCircle(color = DawnPalette.accent, radius = 1.1.dp.toPx(), center = Offset(size.width * 0.82f, bodyTop + 2.6.dp.toPx()))
    }
}

/**
 * The arc's key, drawn with the arc's own marks: a small dot growing to a big one (longer), a ringed
 * dot (two sessions in a day) and the dashed ring (today, nothing yet). It replaced a sentence that
 * explained the same three marks in words (audit pass 2026-09-25).
 */
@Composable
fun ArcLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Bigger dot, longer session. Ringed dot, two sessions in a day. Dashed ring, today."
        },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(label = "longer", glyphWidth = 28.dp) {
            val cy = size.height / 2f
            drawCircle(color = DawnPalette.ink, radius = 2.2.dp.toPx(), center = Offset(2.4.dp.toPx(), cy))
            drawCircle(color = DawnPalette.ink, radius = 3.3.dp.toPx(), center = Offset(11.dp.toPx(), cy))
            drawCircle(color = DawnPalette.ink, radius = 4.6.dp.toPx(), center = Offset(size.width - 4.8.dp.toPx(), cy))
        }
        LegendItem(label = "2 in a day", glyphWidth = 14.dp) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = DawnPalette.ink, radius = 3.4.dp.toPx(), center = c)
            drawCircle(color = DawnPalette.ink, radius = 6.4.dp.toPx(), center = c, style = Stroke(width = 1.1.dp.toPx()))
        }
        LegendItem(label = "today", glyphWidth = 12.dp) {
            drawCircle(
                color = DawnPalette.ink,
                radius = 4.8.dp.toPx(),
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()), 0f),
                ),
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    glyphWidth: androidx.compose.ui.unit.Dp,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = glyphWidth, height = 14.dp), onDraw = glyph)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = SheetSerifFontFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 14.sp,
            color = DawnPalette.muted,
        )
    }
}

@Composable
fun DawnSectionTitle(
    text: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text,
            fontFamily = SheetSerifFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            color = DawnPalette.ink,
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                fontFamily = LogUiFontFamily,
                fontSize = 11.sp,
                color = DawnPalette.muted,
            )
        }
    }
}

@Composable
fun WeekArc(
    dots: List<DayDot>,
    today: LocalDate,
    showSun: Boolean,
    onDotTap: (DayDot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val maxMinutes = dots.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val scale = widthPx / 346f
        val heightPx = 190f * scale
        val heightDp = with(density) { heightPx.toDp() }

        val cx = 173f * scale
        val cy = 150f * scale
        val pathRadius = 98f * scale
        val sunRadius = 82f * scale
        val letterRadius = 116f * scale
        val clipY = 140f * scale

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
        ) {
            // Sun disc or radial glow
            if (showSun) {
                clipRect(top = 0f, bottom = clipY, left = 0f, right = size.width) {
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFB36B), DawnPalette.accent),
                            startY = cy - sunRadius,
                            endY = cy,
                        ),
                        radius = sunRadius,
                        center = Offset(cx, cy),
                    )
                }
            } else {
                clipRect(top = 0f, bottom = clipY, left = 0f, right = size.width) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(DawnPalette.accentSoft, Color.Transparent),
                            center = Offset(cx, cy),
                            radius = sunRadius,
                        ),
                        radius = sunRadius,
                        center = Offset(cx, cy),
                    )
                }
            }

            // Dotted arc guideline (radius 98 from 180° to 0°)
            val arcPath = Path()
            arcPath.addArc(
                oval = androidx.compose.ui.geometry.Rect(
                    left = cx - pathRadius,
                    top = cy - pathRadius,
                    right = cx + pathRadius,
                    bottom = cy + pathRadius,
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
            )
            drawPath(
                path = arcPath,
                color = DawnPalette.ink.copy(alpha = 0.18f),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(2f * scale, 4f * scale),
                        0f,
                    ),
                ),
            )

            // Seven dots and day letters at angles 180° - i*30°
            dots.forEachIndexed { i, dot ->
                if (i in 0..6) {
                    val angleDeg = 180f - (i * 30f)
                    val rad = angleDeg * (PI / 180.0)
                    val dotX = cx + (pathRadius * cos(rad)).toFloat()
                    val dotY = cy - (pathRadius * sin(rad)).toFloat()

                    val isToday = (dot.date == today || i == 6)
                    if (dot.count == 0) {
                        if (isToday) {
                            drawCircle(
                                color = DawnPalette.ink,
                                radius = 5f * scale,
                                center = Offset(dotX, dotY),
                                style = Stroke(
                                    width = 1.5f * scale,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(2f * scale, 2f * scale),
                                        0f,
                                    ),
                                ),
                            )
                        } else {
                            drawCircle(
                                color = DawnPalette.ink.copy(alpha = 0.3f),
                                radius = 3.5f * scale,
                                center = Offset(dotX, dotY),
                                style = Stroke(width = 1f * scale),
                            )
                        }
                    } else {
                        val r = (4f + 2f * (dot.minutes.toFloat() / maxMinutes)).coerceIn(4f, 6f) * scale
                        val dotColor = if (isToday) DawnPalette.ink else (dot.machine?.let { DawnPalette.machine(it) } ?: DawnPalette.ink)
                        drawCircle(
                            color = dotColor,
                            radius = r,
                            center = Offset(dotX, dotY),
                        )
                        if (dot.count >= 2) {
                            drawCircle(
                                color = dotColor,
                                radius = r + 4f * scale,
                                center = Offset(dotX, dotY),
                                style = Stroke(width = 1.2f * scale),
                            )
                        }
                    }

                    // Day letter at radius 116
                    val letterX = cx + (letterRadius * cos(rad)).toFloat()
                    val letterY = cy - (letterRadius * sin(rad)).toFloat()
                    val dayLetter = when (dot.date.dayOfWeek) {
                        DayOfWeek.MONDAY -> "M"
                        DayOfWeek.TUESDAY -> "T"
                        DayOfWeek.WEDNESDAY -> "W"
                        DayOfWeek.THURSDAY -> "T"
                        DayOfWeek.FRIDAY -> "F"
                        DayOfWeek.SATURDAY -> "S"
                        DayOfWeek.SUNDAY -> "S"
                    }
                    val textResult = textMeasurer.measure(
                        text = dayLetter,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = LogUiFontFamily,
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) DawnPalette.ink else DawnPalette.muted,
                        ),
                    )
                    drawText(
                        textLayoutResult = textResult,
                        topLeft = Offset(
                            letterX - textResult.size.width / 2f,
                            letterY - textResult.size.height / 2f,
                        ),
                    )
                }
            }

            // Horizon line at y = 140, ink at 35% alpha
            drawLine(
                color = DawnPalette.ink.copy(alpha = 0.35f),
                start = Offset(0f, clipY),
                end = Offset(size.width, clipY),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Overlay: 44dp hit targets for dots
        dots.forEachIndexed { i, dot ->
            if (i in 0..6) {
                val angleDeg = 180f - (i * 30f)
                val rad = angleDeg * (PI / 180.0)
                val dotX = cx + (pathRadius * cos(rad)).toFloat()
                val dotY = cy - (pathRadius * sin(rad)).toFloat()

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { dotX.toDp() } - 22.dp,
                            y = with(density) { dotY.toDp() } - 22.dp,
                        )
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = dot.count > 0,
                            onClick = { onDotTap(dot) },
                        )
                )
            }
        }
    }
}

@Composable
fun SessionRow(
    session: CardioSession,
    whenLabel: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val type = CardioActivitySpecs.fromStoredType(session.activityType)
    val machineColor = DawnPalette.machine(type)
    val machineName = session.activityLabel ?: CardioActivitySpecs.specFor(type).label
    val meta = sessionMetaLine(session)

    val durationText = session.durationSec?.let { sec ->
        val m = sec / 60
        val s = sec % 60
        val h = m / 60
        if (h > 0) "$h:${(m % 60).toString().padStart(2, '0')}"
        else "$m:${s.toString().padStart(2, '0')}"
    } ?: "—"

    val calText = session.calories?.let { "$it cal" } ?: "—"
    val bg = if (highlighted) DawnPalette.panel else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, shape = if (highlighted) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp))
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left column 60dp: day word in serif italic 16sp muted
                Box(modifier = Modifier.width(60.dp)) {
                    Text(
                        text = whenLabel,
                        fontFamily = SheetSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 16.sp,
                        color = DawnPalette.muted,
                    )
                }

                // Middle: machine dot 7dp + name 15sp medium; meta line 11.5sp muted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(machineColor, CircleShape),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = machineName,
                            fontFamily = LogUiFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = DawnPalette.ink,
                        )
                    }
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = meta,
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.5.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }

                // Right: duration serif 28sp; calories 11sp muted under it, end-aligned
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = durationText,
                        fontFamily = SheetSerifFontFamily,
                        fontSize = 28.sp,
                        color = DawnPalette.ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = calText,
                        fontFamily = LogUiFontFamily,
                        fontSize = 11.sp,
                        color = DawnPalette.muted,
                        textAlign = TextAlign.End,
                    )
                }
            }

            // 1dp bottom hairline in line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DawnPalette.line),
            )
        }
    }
}

@Composable
fun DawnScrubField(
    label: String,
    valueText: String,
    unit: String,
    lastText: String?,
    selected: Boolean,
    onDrag: (ticks: Int) -> Unit,
    onTapToType: () -> Unit,
    onTapReuse: (() -> Unit)? = null,
    optional: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tickPx = with(density) { 26.dp.toPx() }
    var accumulated by remember { mutableStateOf(0f) }

    val isBlankValue = valueText.isBlank() || valueText == "—"
    val showReuse = isBlankValue && onTapReuse != null

    val annotatedLast = lastText?.let { t ->
        buildAnnotatedString {
            val deltaIdx = t.indexOfAny(charArrayOf('+', '−', '-'))
            if (deltaIdx >= 0) {
                withStyle(SpanStyle(color = DawnPalette.muted)) {
                    append(t.substring(0, deltaIdx))
                }
                withStyle(SpanStyle(color = DawnPalette.accentText, fontWeight = FontWeight.SemiBold)) {
                    append(t.substring(deltaIdx))
                }
            } else {
                withStyle(SpanStyle(color = DawnPalette.muted)) {
                    append(t)
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$label $valueText $unit. Swipe up or down to change"
                customActions = listOf(
                    CustomAccessibilityAction("Increase") { onDrag(1); true },
                    CustomAccessibilityAction("Decrease") { onDrag(-1); true },
                )
            }
            .pointerInput(tickPx) {
                detectVerticalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                        val ticks = scrubTicks(-accumulated, tickPx)
                        if (ticks != 0) {
                            onDrag(ticks)
                            accumulated += ticks * tickPx
                        }
                    },
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = DawnPalette.panel,
        border = BorderStroke(1.dp, if (selected) DawnPalette.accent else DawnPalette.line),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (showReuse) onTapReuse() else onTapToType()
                        },
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        fontFamily = LogUiFontFamily,
                        fontSize = 11.sp,
                        color = DawnPalette.muted,
                    )
                    // An empty optional field says so on itself rather than in a sentence under the form.
                    if (optional && isBlankValue) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "optional",
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            color = DawnPalette.muted,
                            modifier = Modifier
                                .border(1.dp, DawnPalette.line, CircleShape)
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))

                if (showReuse) {
                    Text(
                        text = "last $lastText · tap to reuse".replace("last last", "last"),
                        fontFamily = LogUiFontFamily,
                        fontSize = 15.sp,
                        color = DawnPalette.accentText,
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (valueText.isBlank()) "—" else valueText,
                            fontFamily = SheetSerifFontFamily,
                            fontSize = 46.sp,
                            lineHeight = 48.sp,
                            // An empty field's dash is a placeholder, not a value: it stays quiet.
                            color = if (isBlankValue) DawnPalette.line else DawnPalette.ink,
                        )
                        if (unit.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = unit,
                                fontFamily = LogUiFontFamily,
                                fontSize = 11.sp,
                                color = DawnPalette.muted,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }

                if (!showReuse && annotatedLast != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = annotatedLast,
                        fontFamily = LogUiFontFamily,
                        fontSize = 12.sp,
                    )
                }
            }

            // Right rail: a drawn scale between two step chevrons, so the field reads as something
            // you drag (audit pass 2026-09-25 replaced the "Drag to change" sentence with this).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .width(40.dp)
                    .padding(start = 4.dp),
            ) {
                StepChevron(up = true, onClick = { onDrag(1) })

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(56.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val tickCount = 9
                        val tickSpacing = size.height / (tickCount - 1)
                        val tickStroke = 1.dp.toPx()
                        for (i in 0 until tickCount) {
                            val y = i * tickSpacing
                            val major = i % 4 == 0
                            val tickWidth = if (major) 12.dp.toPx() else 7.dp.toPx()
                            drawLine(
                                color = DawnPalette.muted.copy(alpha = if (major) 0.55f else 0.35f),
                                start = Offset(size.width - tickWidth, y),
                                end = Offset(size.width, y),
                                strokeWidth = tickStroke,
                                cap = StrokeCap.Round,
                            )
                        }

                        if (lastText != null) {
                            val markerHeight = 3.dp.toPx()
                            val markerWidth = 14.dp.toPx()
                            val markerY = size.height / 2f
                            drawRoundRect(
                                color = DawnPalette.accent,
                                topLeft = Offset(size.width - markerWidth, markerY - markerHeight / 2f),
                                size = Size(markerWidth, markerHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                            )
                        }
                    }

                    if (lastText != null) {
                        Text(
                            text = "last",
                            fontFamily = LogUiFontFamily,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            color = DawnPalette.muted,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 17.dp),
                        )
                    }
                }

                StepChevron(up = false, onClick = { onDrag(-1) })
            }
        }
    }
}

/** A drawn step chevron with a 32dp target; one tap moves the value one step. */
@Composable
private fun StepChevron(up: Boolean, onClick: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(32.dp, 22.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = if (up) "Increase" else "Decrease" },
    ) {
        val w = 9.dp.toPx()
        val h = 5.dp.toPx()
        val cx = size.width - 6.dp.toPx()
        val cy = size.height / 2f
        val path = Path().apply {
            if (up) {
                moveTo(cx - w / 2f, cy + h / 2f); lineTo(cx, cy - h / 2f); lineTo(cx + w / 2f, cy + h / 2f)
            } else {
                moveTo(cx - w / 2f, cy - h / 2f); lineTo(cx, cy + h / 2f); lineTo(cx + w / 2f, cy - h / 2f)
            }
        }
        drawPath(path, color = DawnPalette.ink.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun DawnToast(
    text: AnnotatedString,
    action: String?,
    onAction: () -> Unit,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Surface(
            shape = CircleShape,
            color = DawnPalette.sheet,
            border = BorderStroke(1.dp, DawnPalette.line),
            modifier = Modifier
                .padding(horizontal = 22.dp, vertical = 8.dp),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    fontFamily = LogUiFontFamily,
                    fontSize = 12.sp,
                    color = DawnPalette.ink,
                    modifier = Modifier.weight(1f).semantics { contentDescription = text.text },
                )
                if (!action.isNullOrBlank()) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = action,
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DawnPalette.muted,
                        modifier = Modifier.clickable(onClick = onAction),
                    )
                }
            }
        }
    }
}

@Composable
fun MonthArc(
    fraction: Float,
    value: String,
    unit: String,
    caption: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp),
    ) {
        // Half arc: the full circle is 88dp, the canvas shows only its upper half.
        Canvas(modifier = Modifier.width(88.dp).height(46.dp)) {
            val strokeWidth = 3.dp.toPx()
            val diameter = size.width - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = DawnPalette.ink.copy(alpha = 0.15f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                color = DawnPalette.accent,
                startAngle = 180f,
                sweepAngle = 180f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = SheetSerifFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 22.sp,
                color = DawnPalette.ink,
            )
            Text(
                text = " $unit",
                fontFamily = LogUiFontFamily,
                fontSize = 11.sp,
                color = DawnPalette.muted,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Text(
            text = caption,
            fontFamily = LogUiFontFamily,
            fontSize = 11.sp,
            color = DawnPalette.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun DawnLineChart(
    points: List<ProgressPoint>,
    excluded: List<ProgressPoint>,
    higherIsBetter: Boolean,
    formatValue: (Double) -> String,
    firstLabel: String,
    midLabel: String,
    lastLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftPad = 8.dp.toPx()
                val rightPad = 8.dp.toPx()
                val topPad = 12.dp.toPx()
                val bottomPad = 16.dp.toPx()
                val chartWidth = size.width - leftPad - rightPad
                val chartHeight = size.height - topPad - bottomPad

                // 3 faint gridlines
                for (step in 0..2) {
                    val y = topPad + chartHeight * (step / 2f)
                    drawLine(
                        color = DawnPalette.line.copy(alpha = 0.5f),
                        start = Offset(leftPad, y),
                        end = Offset(size.width - rightPad, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                if (points.isNotEmpty()) {
                    val minVal = points.minOf { it.value }
                    val maxVal = points.maxOf { it.value }
                    val range = (maxVal - minVal).coerceAtLeast(0.001)

                    val coords = points.mapIndexed { idx, p ->
                        val x = if (points.size == 1) leftPad + chartWidth / 2f
                        else leftPad + (chartWidth * idx / (points.size - 1).toFloat())
                        val norm = ((p.value - minVal) / range).toFloat()
                        val y = (topPad + chartHeight * (1f - norm)).coerceIn(topPad, topPad + chartHeight)
                        Offset(x, y)
                    }

                    // Fill path
                    val fillPath = Path().apply {
                        moveTo(coords.first().x, topPad + chartHeight)
                        coords.forEach { lineTo(it.x, it.y) }
                        lineTo(coords.last().x, topPad + chartHeight)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(DawnPalette.accent.copy(alpha = 0.25f), Color.Transparent),
                            startY = topPad,
                            endY = topPad + chartHeight,
                        ),
                    )

                    // Line path
                    val linePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 1 until coords.size) {
                            lineTo(coords[i].x, coords[i].y)
                        }
                    }
                    drawPath(
                        path = linePath,
                        color = DawnPalette.accent,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Dots on valid points
                    coords.forEachIndexed { i, pt ->
                        val isLast = (i == coords.size - 1)
                        drawCircle(
                            color = DawnPalette.accent,
                            radius = 4.dp.toPx(),
                            center = pt,
                        )
                        if (isLast) {
                            drawCircle(
                                color = DawnPalette.ink,
                                radius = 6.dp.toPx(),
                                center = pt,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                }

                // Excluded points on the baseline
                if (excluded.isNotEmpty()) {
                    val totalSlots = (points.size + excluded.size).coerceAtLeast(2)
                    excluded.forEachIndexed { idx, _ ->
                        val exX = leftPad + (chartWidth * (idx + 0.5f) / totalSlots)
                        val baselineY = topPad + chartHeight
                        drawCircle(
                            color = DawnPalette.muted,
                            radius = 3.5.dp.toPx(),
                            center = Offset(exX, baselineY),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
        }

        // X labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(firstLabel, fontFamily = LogUiFontFamily, fontSize = 11.sp, color = DawnPalette.muted)
            Text(midLabel, fontFamily = LogUiFontFamily, fontSize = 11.sp, color = DawnPalette.muted)
            Text(lastLabel, fontFamily = LogUiFontFamily, fontSize = 11.sp, color = DawnPalette.muted)
        }
    }
}

@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(width = 80.dp, height = 30.dp)
    ) {
        if (values.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.4f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.dp.toPx(),
            )
            return@Canvas
        }

        val minVal = values.min()
        val maxVal = values.max()
        val range = (maxVal - minVal).coerceAtLeast(0.001)

        val coords = values.mapIndexed { idx, v ->
            val x = (size.width * idx / (values.size - 1).toFloat())
            val norm = ((v - minVal) / range).toFloat()
            val y = size.height * (1f - norm)
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 1 until coords.size) {
                lineTo(coords[i].x, coords[i].y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = coords.last(),
        )
    }
}

@Composable
fun ChipRow(
    labels: List<String>,
    selectedIndex: Int,
    disabled: Set<Int> = emptySet(),
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isSelected = (i == selectedIndex)
            val isDisabled = disabled.contains(i)
            val bg = when {
                isSelected -> DawnPalette.accentSoft
                isDisabled -> DawnPalette.panel.copy(alpha = 0.4f)
                else -> DawnPalette.panel
            }
            val textColor = when {
                isSelected -> DawnPalette.accentText
                isDisabled -> DawnPalette.muted.copy(alpha = 0.4f)
                else -> DawnPalette.muted
            }
            Surface(
                shape = CircleShape,
                color = bg,
                border = BorderStroke(1.dp, if (isSelected) DawnPalette.accent else DawnPalette.line),
                modifier = Modifier.clickable(enabled = !isDisabled) { onSelect(i) },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = textColor,
                    fontSize = 13.sp,
                    fontFamily = LogUiFontFamily,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun DawnSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x443A203F))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume clicks on sheet */ },
                ),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = DawnPalette.sheet,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 24.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Grabber
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(DawnPalette.line, CircleShape)
                )
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}
