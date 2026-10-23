package com.ayman.ecolift.ui.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hub.UncheckedSetsItem
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.web.util.MONTH_ABBREV
import kotlinx.datetime.LocalDate
import kotlin.math.abs

enum class HubField { Weight, Reps }

/**
 * weightLbs is stored in tenths of a pound (see [WeightLbs]), so one 5 lb scrub tick is 50 stored
 * units. Passing the Log screen's literal 5 here would move the bar half a pound per tick and print
 * a 200 lb bench as "2000".
 */
private val WEIGHT_STEP_STORED = WeightLbs.fromWholePounds(5) ?: 50

/**
 * Returns the new value after a horizontal drag, or null when travel is under one tick.
 *
 * Ticks are measured against [tickPx] (26.dp). Dragging right (positive [dragPx]) INCREASES
 * the value, dragging left (negative [dragPx]) DECREASES it. Null [current] is treated as 0.
 * The result is clamped at 0.
 */
fun railScrub(current: Int?, dragPx: Float, tickPx: Float, step: Int): Int? {
    if (tickPx <= 0f || abs(dragPx) < tickPx) return null
    val ticks = (dragPx / tickPx).toInt()
    if (ticks == 0) return null
    val base = current ?: 0
    val result = base + (ticks * step)
    return result.coerceAtLeast(0)
}

/**
 * The Log screen's workout card hosted directly on the notification slip surface.
 *
 * Stateless presentation composable. Rendered without an outer enclosing card box to avoid
 * rectangle-in-rectangle nesting on the slip.
 *
 * Deliberately headless: no grab handle, no muscle chip, no exercise name. The notification this
 * card opens inside already carries all three, and repeating them was the single biggest reason the
 * expanded state read as two cards stacked on each other rather than one.
 */
@Composable
fun HubWorkoutCard(
    item: UncheckedSetsItem,
    lastTopSet: String?,
    bestTopSet: String?,
    onSetValueChange: (setId: Long, weightLbs: Int?, reps: Int?) -> Unit,
    onToggleChecked: (setId: Long) -> Unit,
    onRemoveSet: (setId: Long) -> Unit,
    onTapField: (setId: Long, field: HubField) -> Unit,
    onAddSet: () -> Unit,
    onConfirm: () -> Unit,
    onClearOpenSets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val allSets = (item.referenceSets + item.openSets).sortedBy { it.setNumber }
    val nextSetNumber = (allSets.maxOfOrNull { it.setNumber } ?: 0) + 1

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // What the exercise looked like the last time it was checked, as a target.
        if (lastTopSet != null || bestTopSet != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lastTopSet != null) {
                        LastTopSetChip(value = lastTopSet, palette = palette)
                    }
                    if (bestTopSet != null) {
                        BestTopSetChip(value = bestTopSet, palette = palette)
                    }
                }
                Text(
                    text = "BEFORE ${humanHubDate(item.date)}",
                    color = palette.inkSubtle,
                    fontFamily = LogUiFontFamily,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }

        // Checked sets first as read-only reference, then the rows still open.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Reference rows (dimmed, dashed outline, non-editable)
            item.referenceSets.forEach { refSet ->
                ReferenceSetRow(
                    set = refSet,
                    isBodyweight = item.isBodyweight,
                )
            }

            // Open rows (interactive)
            item.openSets.forEach { openSet ->
                OpenSetRow(
                    set = openSet,
                    isBodyweight = item.isBodyweight,
                    onSetValueChange = onSetValueChange,
                    onToggleChecked = onToggleChecked,
                    onRemoveSet = { onRemoveSet(openSet.id) },
                    onTapField = onTapField,
                )
            }
        }

        // A set they did but never entered at all.
        AddSetRow(
            nextSetNumber = nextSetNumber,
            onAddSet = onAddSet,
        )


        ActionButtonsRow(
            onConfirm = onConfirm,
            onClearOpenSets = onClearOpenSets,
        )
    }
}

@Composable
private fun LastTopSetChip(
    value: String,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = EspressoPalette.panel2,
        contentColor = EspressoPalette.last,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "LAST",
                fontFamily = LogUiFontFamily,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = EspressoPalette.last,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = value,
                fontFamily = LogUiFontFamily,
                fontSize = 12.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = EspressoPalette.last,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BestTopSetChip(
    value: String,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
) {
    val targetInk = EspressoPalette.onColor
    Surface(
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = EspressoPalette.up,
        contentColor = targetInk,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BEAT $value",
                fontFamily = LogUiFontFamily,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = targetInk,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ReferenceSetRow(
    set: WorkoutSet,
    isBodyweight: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val isBw = set.isBodyweight || isBodyweight
    val weightText = when {
        isBw && (set.weightLbs == null || set.weightLbs == 0) -> "BW"
        set.weightLbs != null -> WeightLbs.formatStored(set.weightLbs)
        else -> "—"
    }
    val repsText = set.reps?.toString() ?: "—"
    val weightLabel = if (isBw) "BW +" else "lb"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.60f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = set.setNumber.toString(),
            color = palette.complete,
            fontFamily = LogUiFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp),
            textAlign = TextAlign.Center,
        )
        ReferenceRail(
            value = weightText,
            label = weightLabel,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "×",
            color = palette.inkSubtle.copy(alpha = 0.50f),
            fontFamily = LogUiFontFamily,
            fontSize = 12.sp,
        )
        ReferenceRail(
            value = repsText,
            label = "reps",
            modifier = Modifier.weight(1f),
        )
        ReferenceCheckBox()
    }
}

@Composable
private fun ReferenceRail(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val railShape = RoundedCornerShape(9.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(railShape)
            .border(1.dp, EspressoPalette.hair2, railShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                color = palette.inkSubtle.copy(alpha = 0.50f),
                fontFamily = LogUiFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
            Text(
                text = value,
                color = palette.inkMuted.copy(alpha = 0.70f),
                fontFamily = LogUiFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ReferenceCheckBox(
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(shape)
            .background(EspressoPalette.up, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Completed",
            tint = EspressoPalette.onColor,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun OpenSetRow(
    set: WorkoutSet,
    isBodyweight: Boolean,
    onSetValueChange: (setId: Long, weightLbs: Int?, reps: Int?) -> Unit,
    onToggleChecked: (setId: Long) -> Unit,
    onRemoveSet: () -> Unit,
    onTapField: (setId: Long, field: HubField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val isBw = set.isBodyweight || isBodyweight
    val weightPlaceholder = if (isBw) "BW" else "0"
    val weightLabel = if (isBw) "BW +" else "lb"

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 300.dp
        val rowSpacing = if (isCompact) 5.dp else 8.dp
        val railPadding = if (isCompact) 7.dp else 9.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            Text(
                text = set.setNumber.toString(),
                color = palette.inkSubtle,
                fontFamily = LogUiFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(18.dp),
                textAlign = TextAlign.Center,
            )
            HubRail(
                value = set.weightLbs,
                label = weightLabel,
                placeholder = weightPlaceholder,
                step = WEIGHT_STEP_STORED,
                format = { WeightLbs.formatStored(it) },
                fill = palette.load,
                fillStrong = palette.loadStrong,
                isMissingValue = set.weightLbs == null,
                horizontalPadding = railPadding,
                onValueChange = { newWeight ->
                    onSetValueChange(set.id, newWeight, set.reps)
                },
                onTap = {
                    onTapField(set.id, HubField.Weight)
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "×",
                color = palette.inkSubtle,
                fontFamily = LogUiFontFamily,
                fontSize = 12.sp,
            )
            HubRail(
                value = set.reps,
                label = "reps",
                placeholder = "0",
                step = 1,
                fill = palette.rep,
                fillStrong = palette.repStrong,
                isMissingValue = set.reps == null,
                horizontalPadding = railPadding,
                onValueChange = { newReps ->
                    onSetValueChange(set.id, set.weightLbs, newReps)
                },
                onTap = {
                    onTapField(set.id, HubField.Reps)
                },
                modifier = Modifier.weight(1f),
            )
            RemoveSetButton(
                setNumber = set.setNumber,
                onRemoveSet = onRemoveSet,
            )
            OpenCheckBox(
                setNumber = set.setNumber,
                onToggleChecked = { onToggleChecked(set.id) },
            )
        }
    }
}

@Composable
private fun RemoveSetButton(
    setNumber: Int,
    onRemoveSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onRemoveSet,
        modifier = modifier
            .size(30.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Remove set $setNumber"
            },
        shape = shape,
        color = Color.Transparent,
        contentColor = EspressoPalette.faint,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = EspressoPalette.faint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HubRail(
    value: Int?,
    label: String,
    placeholder: String,
    step: Int,
    fill: Color,
    fillStrong: Color,
    isMissingValue: Boolean,
    horizontalPadding: Dp = 9.dp,
    onValueChange: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    val palette = LocalGlassPalette.current
    val density = LocalDensity.current
    val tickPx = with(density) { 26.dp.toPx() }
    var isScrubbing by remember { mutableStateOf(false) }
    var dragCarry by remember { mutableStateOf(0f) }
    val railShape = RoundedCornerShape(9.dp)

    val displayText = value?.let(format) ?: placeholder
    val isShowingPlaceholder = value == null

    // Espresso: a quiet tile; the one being scrubbed (or still empty) gets an amber ring.
    val strokeColor = when {
        isScrubbing -> EspressoPalette.down
        isMissingValue -> EspressoPalette.down.copy(alpha = 0.75f)
        else -> Color.Transparent
    }
    val strokeWidth = 1.5.dp

    val baseFill = Brush.horizontalGradient(listOf(EspressoPalette.panel2, EspressoPalette.panel2))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(railShape)
            .background(baseFill, railShape)
            .border(width = strokeWidth, color = strokeColor, shape = railShape)
            .pointerInput(value, tickPx, step) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragCarry = 0f
                    var hasScrubbed = false
                    var lastX = down.position.x

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            if (!hasScrubbed) {
                                onTap()
                            }
                            break
                        }
                        if (!change.pressed) {
                            break
                        }
                        val currentX = change.position.x
                        val deltaX = currentX - lastX
                        lastX = currentX
                        dragCarry += deltaX

                        val scrubbed = railScrub(value, dragCarry, tickPx, step)
                        if (scrubbed != null) {
                            change.consume()
                            hasScrubbed = true
                            isScrubbing = true
                            val ticks = (dragCarry / tickPx).toInt()
                            dragCarry -= ticks * tickPx
                            onValueChange(scrubbed)
                        }
                    }
                    isScrubbing = false
                    dragCarry = 0f
                }
            },
    ) {
        // Faint vertical tread hatching
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val pixelsPerUnit = tickPx / step.toFloat()
            val numericValue = (value ?: 0).toFloat()
            val absoluteOffsetPx = (numericValue * pixelsPerUnit) + dragCarry
            val tickSpacing = tickPx
            val shift = ((absoluteOffsetPx % tickSpacing) + tickSpacing) % tickSpacing
            var x = shift - tickSpacing

            while (x < width + tickSpacing) {
                val distanceFromCenter = abs(x - width / 2f)
                val edgeFade = (1f - (distanceFromCenter / (width / 2f))).coerceIn(0f, 1f)
                val curvedFade = edgeFade * edgeFade
                val alpha = curvedFade * (if (isScrubbing) 0.28f else 0.16f)
                if (alpha > 0.01f) {
                    drawLine(
                        color = palette.ink.copy(alpha = alpha),
                        start = Offset(x, height * 0.25f),
                        end = Offset(x, height * 0.75f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                x += tickSpacing
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                color = palette.inkSubtle.copy(alpha = 0.80f),
                fontFamily = LogUiFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
            Text(
                text = displayText,
                color = if (isShowingPlaceholder) EspressoPalette.faint else EspressoPalette.ink,
                fontFamily = LogDisplayFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun OpenCheckBox(
    setNumber: Int,
    onToggleChecked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(shape)
            .border(width = 1.5.dp, color = EspressoPalette.hair2, shape = shape)
            .clickable(onClick = onToggleChecked)
            .semantics {
                role = Role.Checkbox
                contentDescription = "Check set $setNumber"
                stateDescription = "Incomplete"
            },
    )
}

@Composable
private fun AddSetRow(
    nextSetNumber: Int,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val containerShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .drawBehind {
                drawRoundRect(
                    color = EspressoPalette.hair2,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .clickable(onClick = onAddSet)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = EspressoPalette.dim,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Add set $nextSetNumber",
            color = EspressoPalette.dim,
            fontFamily = LogUiFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionButtonsRow(
    onConfirm: () -> Unit,
    onClearOpenSets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClearOpenSets,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = shape,
            color = EspressoPalette.panel2,
            contentColor = EspressoPalette.down,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Clear open sets",
                    fontFamily = LogDisplayFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EspressoPalette.down,
                )
            }
        }

        Surface(
            onClick = onConfirm,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = shape,
            color = EspressoPalette.up,
            contentColor = EspressoPalette.onColor,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Check open sets",
                    fontFamily = LogDisplayFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EspressoPalette.onColor,
                )
            }
        }
    }
}

/** "2026-08-17" -> "17 Aug". The raw ISO date is storage, not something to show a lifter. */
private fun humanHubDate(dateStr: String): String {
    val ld = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
    val month = MONTH_ABBREV.getOrNull(ld.month.ordinal) ?: ld.month.name.take(3)
    return "${ld.day} $month"
}
