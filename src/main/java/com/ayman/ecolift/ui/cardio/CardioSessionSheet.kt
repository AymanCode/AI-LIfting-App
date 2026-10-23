package com.ayman.ecolift.ui.cardio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.CardioFieldRules
import com.ayman.ecolift.cardio.SessionDeltas
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.web.util.DAY_ABBREV
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private fun formatDeltaVal(field: CardioEntryField, type: CardioActivityType, raw: Double): String {
    val rule = CardioFieldRules.rule(field, type)
    return if (rule.decimals == 0) {
        abs(raw.roundToInt()).toString()
    } else {
        CardioFieldRules.display(field, type, abs(raw))
    }
}

@Composable
fun CardioSessionSheet(
    session: CardioSession,
    deltas: SessionDeltas,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMachine: () -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteConfirming by remember { mutableStateOf(false) }

    LaunchedEffect(deleteConfirming) {
        if (deleteConfirming) {
            delay(3000)
            deleteConfirming = false
        }
    }

    val type = CardioActivitySpecs.fromStoredType(session.activityType)
    val machineLabel = CardioActivitySpecs.specFor(type).label
    val today = todayLocalDate()

    val whenPrefix = when (session.date) {
        today.toString() -> "today"
        today.minusDays(1L).toString() -> "yesterday"
        else -> runCatching { LocalDate.parse(session.date) }.getOrNull()?.let {
            "${DAY_ABBREV[it.dayOfWeek.ordinal]}, ${MONTH_ABBREV[it.monthNumber - 1]} ${it.dayOfMonth}"
        } ?: session.date
    }

    val sourceLabel = if (session.source == CardioSessionSource.OCR) "from a photo" else "by hand"

    val durationText = session.durationSec?.let { sec ->
        val m = sec / 60
        val s = sec % 60
        val h = m / 60
        if (h > 0) "$h:${(m % 60).toString().padStart(2, '0')}"
        else "$m:${s.toString().padStart(2, '0')}"
    } ?: "—"

    val distanceText = session.distanceM?.let { distM ->
        val distVal = CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, distM)
        val formatted = CardioFieldRules.display(CardioEntryField.DISTANCE_MILES, type, distVal)
        val unit = if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) "m" else "mi"
        "$formatted $unit"
    }

    val derivedPace = deltas.derivedPaceSec
    val paceText = derivedPace?.let { pace ->
        val m = pace / 60
        val s = pace % 60
        val unit = when (type) {
            CardioActivityType.ROW -> "/500 m"
            CardioActivityType.SWIM -> "/100 m"
            else -> "per mile"
        }
        "$m:${s.toString().padStart(2, '0')} $unit"
    }

    // Grid fields: fields present on the session (excluding duration, distance, notes)
    data class GridItem(val label: String, val value: String, val deltaText: String, val deltaImproved: Boolean)

    val gridItems = mutableListOf<GridItem>()

    fun addGridItem(field: CardioEntryField, label: String, valueStr: String) {
        val deltaObj = deltas.deltas.firstOrNull { it.field == field }
        val deltaText: String
        val improved: Boolean
        if (deltas.previous == null) {
            deltaText = "first on this machine"
            improved = false
        } else if (deltaObj != null) {
            val sign = if (deltaObj.delta >= 0) "+" else "−"
            val numPart = formatDeltaVal(field, type, deltaObj.delta)
            val prevDay = runCatching { LocalDate.parse(deltas.previous.date) }.getOrNull()?.let { DAY_ABBREV[it.dayOfWeek.ordinal] }
            val isZero = numPart.all { it == '0' || it == '.' || it == ':' }
            deltaText = when {
                isZero && prevDay != null -> "same as $prevDay"
                isZero -> "same"
                prevDay != null -> "$sign$numPart vs $prevDay"
                else -> "$sign$numPart"
            }
            improved = deltaObj.improved && !isZero
        } else {
            deltaText = "—"
            improved = false
        }
        gridItems.add(GridItem(label, valueStr, deltaText, improved))
    }

    if (session.avgSpeed != null) {
        val mph = CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, session.avgSpeed)
        addGridItem(CardioEntryField.AVG_SPEED_MPH, "speed", "${CardioFieldRules.display(CardioEntryField.AVG_SPEED_MPH, type, mph)} mph")
    }
    if (session.avgInclinePercent != null) {
        addGridItem(CardioEntryField.AVG_INCLINE_PERCENT, "incline", "${CardioFieldRules.display(CardioEntryField.AVG_INCLINE_PERCENT, type, session.avgInclinePercent)}%")
    }
    if (session.avgHeartRate != null) {
        addGridItem(CardioEntryField.AVG_HEART_RATE, "heart rate", "${session.avgHeartRate} bpm")
    }
    if (session.calories != null) {
        addGridItem(CardioEntryField.CALORIES, "calories", "${session.calories}")
    }
    if (session.avgPowerWatts != null) {
        addGridItem(CardioEntryField.AVG_POWER_WATTS, "power", "${session.avgPowerWatts} W")
    }
    if (session.cadenceRpm != null) {
        addGridItem(CardioEntryField.CADENCE_RPM, "cadence", "${session.cadenceRpm} rpm")
    }
    if (session.resistanceLevel != null) {
        addGridItem(CardioEntryField.RESISTANCE_LEVEL, "resistance", "lvl ${CardioFieldRules.display(CardioEntryField.RESISTANCE_LEVEL, type, session.resistanceLevel)}")
    }
    if (session.floors != null) {
        addGridItem(CardioEntryField.FLOORS, "floors", "${session.floors}")
    }
    if (session.strokeRateSpm != null) {
        addGridItem(CardioEntryField.STROKE_RATE_SPM, "stroke rate", "${session.strokeRateSpm} spm")
    }
    if (session.paceSecPer500m != null) {
        val m = session.paceSecPer500m / 60
        val s = session.paceSecPer500m % 60
        addGridItem(CardioEntryField.PACE_500M, "split", "$m:${s.toString().padStart(2, '0')}")
    }
    if (session.steps != null) {
        addGridItem(CardioEntryField.STEPS, "steps", "${session.steps}")
    }

    DawnSheet(onDismiss = onDismiss) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = machineLabel,
                fontFamily = LogUiFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = DawnPalette.accentText,
                modifier = Modifier.clickable(onClick = onMachine),
            )
            Text(
                text = " · $whenPrefix · $sourceLabel",
                fontFamily = LogUiFontFamily,
                fontSize = 13.sp,
                color = DawnPalette.muted,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Hero duration & distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = durationText,
                fontFamily = SheetSerifFontFamily,
                fontSize = 60.sp,
                lineHeight = 60.sp,
                color = DawnPalette.ink,
            )
            if (distanceText != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = distanceText,
                        fontFamily = SheetSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        color = DawnPalette.ink,
                    )
                    if (paceText != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = paceText,
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3-column grid of other fields
        if (gridItems.isNotEmpty()) {
            val chunked = gridItems.chunked(3)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                chunked.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (i in 0..2) {
                            if (i < rowItems.size) {
                                val item = rowItems[i]
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.value,
                                        fontFamily = SheetSerifFontFamily,
                                        fontSize = 18.sp,
                                        color = DawnPalette.ink,
                                    )
                                    Text(
                                        text = item.label,
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 11.sp,
                                        color = DawnPalette.muted,
                                    )
                                    Text(
                                        text = item.deltaText,
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = if (item.deltaImproved) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (item.deltaImproved) DawnPalette.accentText else DawnPalette.muted,
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Hint line
        Spacer(Modifier.height(14.dp))
        if (deltas.previous != null) {
            val prevDate = runCatching { LocalDate.parse(deltas.previous.date) }.getOrNull()
            val prevDateStr = prevDate?.let {
                "${DAY_ABBREV[it.dayOfWeek.ordinal]} ${MONTH_ABBREV[it.monthNumber - 1]} ${it.dayOfMonth}"
            } ?: deltas.previous.date
            Text(
                text = dawnDataLine(listOf(filler("vs last "), data(prevDateStr)), 11.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            DawnEyebrow("First on this machine", Modifier.fillMaxWidth())
        }

        // Buttons
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = {
                    if (deleteConfirming) {
                        onDelete()
                    } else {
                        deleteConfirming = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, DawnPalette.line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (deleteConfirming) "Tap again to delete" else "Delete",
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (deleteConfirming) DawnPalette.accentText else DawnPalette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Surface(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, DawnPalette.line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Edit",
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = DawnPalette.ink,
                    )
                }
            }

            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1.3f)
                    .height(46.dp),
                shape = CircleShape,
                color = DawnPalette.accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Done",
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = DawnPalette.pillText,
                    )
                }
            }
        }
    }
}
