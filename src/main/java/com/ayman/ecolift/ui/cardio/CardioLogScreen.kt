package com.ayman.ecolift.ui.cardio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.CardioFieldRules
import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.viewmodel.CardioDraftLogic
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import com.ayman.ecolift.ui.viewmodel.SaveState
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.round

internal fun cardioFieldLabel(field: CardioEntryField, type: CardioActivityType): String = when (field) {
    CardioEntryField.DURATION -> "Time"
    CardioEntryField.DISTANCE_MILES -> if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) "Distance (m)" else "Distance (mi)"
    CardioEntryField.FLOORS -> "Floors"
    CardioEntryField.AVG_HEART_RATE -> "Avg heart rate"
    CardioEntryField.MAX_HEART_RATE -> "Max heart rate"
    CardioEntryField.AVG_SPEED_MPH -> "Speed"
    CardioEntryField.AVG_INCLINE_PERCENT -> "Incline"
    CardioEntryField.CALORIES -> "Calories"
    CardioEntryField.CADENCE_RPM -> "Cadence"
    CardioEntryField.RESISTANCE_LEVEL -> "Resistance"
    CardioEntryField.AVG_POWER_WATTS -> "Power"
    CardioEntryField.STROKE_RATE_SPM -> "Stroke rate"
    CardioEntryField.PACE_500M -> "Split / 500m"
    CardioEntryField.STEPS -> "Steps"
    CardioEntryField.NOTES -> "Notes"
}

/** Fields the form marks "optional" while they are empty: heart rate is the one people often lack. */
internal fun cardioFieldIsOptional(field: CardioEntryField): Boolean =
    field == CardioEntryField.AVG_HEART_RATE || field == CardioEntryField.MAX_HEART_RATE

internal fun cardioFieldUnit(field: CardioEntryField, type: CardioActivityType): String = when (field) {
    CardioEntryField.DURATION -> ""
    CardioEntryField.DISTANCE_MILES -> if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) "m" else "mi"
    CardioEntryField.FLOORS -> "floors"
    CardioEntryField.AVG_HEART_RATE, CardioEntryField.MAX_HEART_RATE -> "bpm"
    CardioEntryField.AVG_SPEED_MPH -> "mph"
    CardioEntryField.AVG_INCLINE_PERCENT -> "%"
    CardioEntryField.CALORIES -> "cal"
    CardioEntryField.CADENCE_RPM -> "rpm"
    CardioEntryField.RESISTANCE_LEVEL -> "level"
    CardioEntryField.AVG_POWER_WATTS -> "W"
    CardioEntryField.STROKE_RATE_SPM -> "spm"
    CardioEntryField.PACE_500M -> "/500m"
    CardioEntryField.STEPS -> "steps"
    CardioEntryField.NOTES -> ""
}

internal fun cardioFieldLastText(
    field: CardioEntryField,
    type: CardioActivityType,
    currentVal: Double?,
    lastSession: CardioSession?,
): String? {
    if (lastSession == null) return null
    val lastVal: Double = when (field) {
        CardioEntryField.DURATION -> lastSession.durationSec?.toDouble()
        CardioEntryField.DISTANCE_MILES -> lastSession.distanceM?.let { CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, it) }
        CardioEntryField.FLOORS -> lastSession.floors?.toDouble()
        CardioEntryField.AVG_HEART_RATE -> lastSession.avgHeartRate?.toDouble()
        CardioEntryField.MAX_HEART_RATE -> lastSession.maxHeartRate?.toDouble()
        CardioEntryField.AVG_SPEED_MPH -> lastSession.avgSpeed?.let { CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, it) }
        CardioEntryField.AVG_INCLINE_PERCENT -> lastSession.avgInclinePercent
        CardioEntryField.CALORIES -> lastSession.calories?.toDouble()
        CardioEntryField.CADENCE_RPM -> lastSession.cadenceRpm?.toDouble()
        CardioEntryField.RESISTANCE_LEVEL -> lastSession.resistanceLevel
        CardioEntryField.AVG_POWER_WATTS -> lastSession.avgPowerWatts?.toDouble()
        CardioEntryField.STROKE_RATE_SPM -> lastSession.strokeRateSpm?.toDouble()
        CardioEntryField.PACE_500M -> lastSession.paceSecPer500m?.toDouble()
        CardioEntryField.STEPS -> lastSession.steps?.toDouble()
        CardioEntryField.NOTES -> null
    } ?: return null

    val lastDisp = CardioFieldRules.display(field, type, lastVal)
    if (currentVal == null) {
        return lastDisp
    }
    val cur: Double = currentVal

    return when (field) {
        CardioEntryField.DURATION -> {
            val diff = round(cur - lastVal).toLong()
            if (diff == 0L) {
                "last $lastDisp"
            } else {
                val sign = if (diff > 0) "+" else "−"
                val absDiff = abs(diff)
                val m = absDiff / 60
                val s = absDiff % 60
                val deltaStr = "$sign$m:${s.toString().padStart(2, '0')}"
                "last $lastDisp $deltaStr"
            }
        }
        else -> {
            val diff = cur - lastVal
            if (abs(diff) < 1e-4) {
                "last $lastDisp"
            } else {
                val sign = if (diff > 0) "+" else "−"
                val deltaVal = abs(diff)
                val deltaStr = "$sign${CardioFieldRules.display(field, type, deltaVal)}"
                "last $lastDisp $deltaStr"
            }
        }
    }
}

@Composable
fun CardioLogScreen(
    viewModel: CardioViewModel,
    editingId: Long?,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var hasSaved by remember { mutableStateOf(false) }

    LaunchedEffect(editingId) {
        if (editingId != null && state.draft?.editingId != editingId) {
            viewModel.startEditDraft(editingId)
        } else if (editingId == null && state.draft == null) {
            viewModel.startNewDraft()
        }
    }

    LaunchedEffect(state.draft, hasSaved) {
        if (hasSaved && state.draft == null) {
            onDone()
        }
    }

    val draft = state.draft
    if (draft == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DawnPalette.accent)
        }
        return
    }

    val isEdit = draft.editingId != null
    val lastSession = remember(state.sessions, draft.type) {
        CardioInsights.lastOnMachine(state.sessions, draft.type)
    }
    val prefillDateStr = remember(lastSession) {
        lastSession?.let { s ->
            runCatching {
                val d = LocalDate.parse(s.date)
                d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            }.getOrNull()
        }
    }

    val machineOrder = remember(state.sessions) {
        CardioInsights.defaultMachineOrder(state.sessions)
    }
    val cards = remember(machineOrder, draft.type) { machineCardOrder(machineOrder, draft.type) }
    var showMoreMachinesSheet by remember { mutableStateOf(false) }

    val primaryFields = remember(draft.type) {
        CardioFieldRules.primaryFields(draft.type)
    }
    val moreFields = remember(draft.type) {
        CardioFieldRules.moreFields(draft.type)
    }
    var moreExpanded by remember(draft.type) {
        mutableStateOf(
            moreFields.any { it != CardioEntryField.NOTES && draft.values.containsKey(it) } ||
            draft.notes.isNotBlank()
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        IconButton(onClick = onDone) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DawnPalette.ink,
                            )
                        }
                        Column {
                            Text(
                                text = if (isEdit) "Edit session" else "Log a session",
                                fontFamily = SheetSerifFontFamily,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                                fontSize = 24.sp,
                                color = DawnPalette.ink,
                            )
                            if (!isEdit && prefillDateStr != null) {
                                Text(
                                    text = "prefilled from $prefillDateStr",
                                    fontFamily = LogUiFontFamily,
                                    fontSize = 12.sp,
                                    color = DawnPalette.muted,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.cancelDraft()
                            onDone()
                        }
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = LogUiFontFamily,
                            fontSize = 14.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }
            }

            // Machine cards: the most-used machines, each with its last session, then More.
            item {
                MachineCardRow(
                    cards = cards,
                    selected = draft.type,
                    sessions = state.sessions,
                    hiddenCount = CardioActivitySpecs.entryOrder.size - cards.size,
                    onSelect = { viewModel.selectMachine(it) },
                    onMore = { showMoreMachinesSheet = true },
                )
            }

            // Primary scrub fields
            items(primaryFields.size) { index ->
                val field = primaryFields[index]
                val label = cardioFieldLabel(field, draft.type)
                val unit = cardioFieldUnit(field, draft.type)
                val value = draft.values[field]
                val valueText = if (value != null) CardioFieldRules.display(field, draft.type, value) else ""
                val lastText = cardioFieldLastText(field, draft.type, value, lastSession)
                val isBlank = value == null
                val hasLastVal = lastSession != null && lastText != null
                val onTapReuse: (() -> Unit)? = if (isBlank && hasLastVal) {
                    { viewModel.reuseLast(field) }
                } else {
                    null
                }

                Column {
                    DawnScrubField(
                        label = label,
                        valueText = valueText,
                        unit = unit,
                        lastText = lastText,
                        selected = false,
                        onDrag = { ticks -> viewModel.scrub(field, ticks) },
                        onTapToType = { viewModel.beginTyping(field) },
                        onTapReuse = onTapReuse,
                        optional = cardioFieldIsOptional(field),
                    )
                    if (draft.invalid.contains(field)) {
                        Text(
                            text = "Enter a number",
                            fontFamily = LogUiFontFamily,
                            fontSize = 12.sp,
                            color = DawnPalette.accentText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                        )
                    }
                }
            }

            // More row / fields
            if (moreFields.isNotEmpty()) {
                if (!moreExpanded) {
                    item {
                        val summary = moreFields.joinToString(", ") {
                            if (it == CardioEntryField.NOTES) "a note" else cardioFieldLabel(it, draft.type).lowercase()
                        }.replaceFirstChar { it.uppercase() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { moreExpanded = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$summary · Add",
                                fontFamily = LogUiFontFamily,
                                fontSize = 13.sp,
                                color = DawnPalette.accentText,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    items(moreFields.size) { index ->
                        val field = moreFields[index]
                        if (field == CardioEntryField.NOTES) {
                            OutlinedTextField(
                                value = draft.notes,
                                onValueChange = { viewModel.setNotes(it) },
                                label = { Text("Notes", fontFamily = LogUiFontFamily, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DawnPalette.panel,
                                    unfocusedContainerColor = DawnPalette.panel,
                                    focusedBorderColor = DawnPalette.accent,
                                    unfocusedBorderColor = DawnPalette.line,
                                    focusedTextColor = DawnPalette.ink,
                                    unfocusedTextColor = DawnPalette.ink,
                                    cursorColor = DawnPalette.ink,
                                ),
                                minLines = 2,
                                maxLines = 4,
                            )
                        } else {
                            val label = cardioFieldLabel(field, draft.type)
                            val unit = cardioFieldUnit(field, draft.type)
                            val value = draft.values[field]
                            val valueText = if (value != null) CardioFieldRules.display(field, draft.type, value) else ""
                            val lastText = cardioFieldLastText(field, draft.type, value, lastSession)
                            val isBlank = value == null
                            val hasLastVal = lastSession != null && lastText != null
                            val onTapReuse: (() -> Unit)? = if (isBlank && hasLastVal) {
                                { viewModel.reuseLast(field) }
                            } else {
                                null
                            }

                            Column {
                                DawnScrubField(
                                    label = label,
                                    valueText = valueText,
                                    unit = unit,
                                    lastText = lastText,
                                    selected = false,
                                    onDrag = { ticks -> viewModel.scrub(field, ticks) },
                                    onTapToType = { viewModel.beginTyping(field) },
                                    onTapReuse = onTapReuse,
                                    optional = cardioFieldIsOptional(field),
                                )
                                if (draft.invalid.contains(field)) {
                                    Text(
                                        text = "Enter a number",
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 12.sp,
                                        color = DawnPalette.accentText,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save Pill
            item {
                Spacer(Modifier.height(8.dp))
                val durationVal = draft.values[CardioEntryField.DURATION]
                val saveMainText = when {
                    isEdit -> "Save changes"
                    durationVal != null -> {
                        val durDisplay = CardioFieldRules.display(CardioEntryField.DURATION, draft.type, durationVal)
                        val machineLabel = CardioActivitySpecs.labelFor(draft.type).lowercase()
                        "Save $durDisplay on the $machineLabel"
                    }
                    else -> "Save session"
                }

                val today = todayLocalDate()
                val todayStr = today.toString()
                val sessionsToday = state.sessions.count { it.date == todayStr }
                val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                val dotStatus = if (sessionsToday > 0) "· ${countLabel(sessionsToday + 1, "session")} today" else "· $dayName's dot fills in"

                val distVal = draft.values[CardioEntryField.DISTANCE_MILES]
                val distUnit = if (draft.type == CardioActivityType.ROW || draft.type == CardioActivityType.SWIM) "m" else "mi"
                val distStr = if (distVal != null) "${CardioFieldRules.display(CardioEntryField.DISTANCE_MILES, draft.type, distVal)} $distUnit" else null
                val floorsVal = draft.values[CardioEntryField.FLOORS]
                val floorsStr = if (floorsVal != null) "${CardioFieldRules.display(CardioEntryField.FLOORS, draft.type, floorsVal)} floors" else null
                val hrVal = draft.values[CardioEntryField.AVG_HEART_RATE]
                val hrStr = if (hrVal != null) "${CardioFieldRules.display(CardioEntryField.AVG_HEART_RATE, draft.type, hrVal)} bpm" else null

                val metricParts = listOfNotNull(distStr ?: floorsStr, hrStr).joinToString(" · ")
                val saveSubtext = if (isEdit) {
                    if (metricParts.isNotBlank()) metricParts else null
                } else {
                    if (metricParts.isNotBlank()) "$metricParts $dotStatus" else dotStatus.removePrefix("· ")
                }

                val canSave = CardioDraftLogic.canSave(draft) && state.saveState != SaveState.SAVING

                if (state.saveError != null) {
                    Text(
                        text = state.saveError ?: "Couldn't save.",
                        fontFamily = LogUiFontFamily,
                        fontSize = 13.sp,
                        color = DawnPalette.accentText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }

                DawnPill(
                    text = saveMainText,
                    subtext = saveSubtext,
                    enabled = canSave,
                    loading = state.saveState == SaveState.SAVING,
                    onClick = {
                        hasSaved = true
                        viewModel.save()
                    },
                )
            }
        }

        // Tap to type sheet
        val typingField = state.typingField
        if (typingField != null) {
            val currentVal = draft.values[typingField]
            var typedText by remember(typingField) {
                mutableStateOf(currentVal?.let { CardioFieldRules.display(typingField, draft.type, it) } ?: "")
            }
            DawnSheet(onDismiss = { viewModel.commitTyped(typingField, typedText) }) {
                Text(
                    text = "Enter ${cardioFieldLabel(typingField, draft.type).lowercase()}",
                    fontFamily = SheetSerifFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    color = DawnPalette.ink,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DawnPalette.panel,
                        unfocusedContainerColor = DawnPalette.panel,
                        focusedBorderColor = DawnPalette.accent,
                        unfocusedBorderColor = DawnPalette.line,
                        focusedTextColor = DawnPalette.ink,
                        unfocusedTextColor = DawnPalette.ink,
                        cursorColor = DawnPalette.ink,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DawnGhostPill(
                        text = "Clear",
                        onClick = {
                            viewModel.clearField(typingField)
                            viewModel.commitTyped(typingField, "")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DawnPill(
                        text = "Done",
                        onClick = {
                            viewModel.commitTyped(typingField, typedText)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // More Machines sheet
        if (showMoreMachinesSheet) {
            DawnSheet(onDismiss = { showMoreMachinesSheet = false }) {
                Text(
                    text = "Choose machine",
                    fontFamily = SheetSerifFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    color = DawnPalette.ink,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                CardioActivitySpecs.entryOrder.forEach { type ->
                    val spec = CardioActivitySpecs.specFor(type)
                    val isSelected = (draft.type == type)
                    Surface(
                        onClick = {
                            viewModel.selectMachine(type)
                            showMoreMachinesSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) DawnPalette.accent.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(DawnPalette.machine(type), CircleShape)
                                )
                                Column {
                                    Text(
                                        text = spec.label,
                                        fontFamily = LogUiFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = DawnPalette.ink,
                                    )
                                    Text(
                                        text = spec.supportingLabel,
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 12.sp,
                                        color = DawnPalette.muted,
                                    )
                                }
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = DawnPalette.accentText,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A sideways row of machine cards (audit pass 2026-09-25): the old five tiles squeezed each name
 * into a fifth of the width. Each card is roomy enough for the full name and the last session on
 * that machine, and More sits at the end with the count of machines it holds.
 */
@Composable
private fun MachineCardRow(
    cards: List<CardioActivityType>,
    selected: CardioActivityType,
    sessions: List<CardioSession>,
    hiddenCount: Int,
    onSelect: (CardioActivityType) -> Unit,
    onMore: () -> Unit,
) {
    val today = todayLocalDate()
    val rowState = rememberLazyListState()
    // A machine picked from the More sheet joins the row after the top four; bring it into view.
    LaunchedEffect(selected) {
        val index = cards.indexOf(selected)
        if (index >= 0 && rowState.layoutInfo.visibleItemsInfo.none { it.index == index && it.offset >= 0 && it.offset + it.size <= rowState.layoutInfo.viewportEndOffset }) {
            rowState.animateScrollToItem(index)
        }
    }
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cards, key = { it.name }) { type ->
            MachineCard(
                type = type,
                last = remember(sessions, type, today) { machineLast(sessions, type, today) },
                isSelected = type == selected,
                onClick = { onSelect(type) },
            )
        }
        item(key = "more") {
            MoreMachinesCard(hiddenCount = hiddenCount, onClick = onMore)
        }
    }
}

private val MachineCardWidth = 118.dp
private val MachineCardHeight = 96.dp

@Composable
private fun MachineCard(
    type: CardioActivityType,
    last: MachineLast?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val name = CardioActivitySpecs.labelFor(type)
    val machineColor = DawnPalette.machine(type)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) DawnPalette.accentSoft else DawnPalette.panel,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) DawnPalette.accent else DawnPalette.line),
        modifier = Modifier
            .width(MachineCardWidth)
            .height(MachineCardHeight)
            .semantics {
                contentDescription = name
                this.selected = isSelected
            },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp)) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(machineColor.copy(alpha = if (isSelected) 0.22f else 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = machineIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = machineColor,
                    )
                }
                Column {
                    Text(
                        text = name,
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        color = if (isSelected) DawnPalette.accentText else DawnPalette.ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(1.dp))
                    MachineLastLine(last)
                }
            }
            if (isSelected) {
                SelectedTick(Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/** "30:00 · Wed": the time in Dawn's serif numerals, the day quiet beside it. */
@Composable
private fun MachineLastLine(last: MachineLast?) {
    val text = buildAnnotatedString {
        if (last == null) {
            withStyle(SpanStyle(fontFamily = SheetSerifFontFamily, fontSize = 15.sp, color = DawnPalette.muted)) { append("—") }
        } else {
            withStyle(SpanStyle(fontFamily = SheetSerifFontFamily, fontSize = 15.sp, color = DawnPalette.ink)) { append(last.duration) }
            withStyle(SpanStyle(fontFamily = LogUiFontFamily, fontSize = 11.sp, color = DawnPalette.muted)) { append(" · ${last.whenLabel}") }
        }
    }
    Text(text = text, maxLines = 1, softWrap = false, lineHeight = 18.sp)
}

/** A drawn check in a filled accent disc: the selected card says so in shape, not only colour. */
@Composable
private fun SelectedTick(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        drawCircle(color = DawnPalette.accent)
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.67f)
            lineTo(size.width * 0.73f, size.height * 0.36f)
        }
        drawPath(path, color = DawnPalette.pillText, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun MoreMachinesCard(hiddenCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, DawnPalette.line),
        modifier = Modifier
            .width(84.dp)
            .height(MachineCardHeight)
            .semantics { contentDescription = "More" },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val r = 1.8.dp.toPx()
                val gap = 6.dp.toPx()
                val cy = size.height / 2f
                for (i in -1..1) {
                    drawCircle(color = DawnPalette.muted, radius = r, center = Offset(size.width / 2f + i * gap, cy))
                }
            }
            Column {
                Text(
                    text = "More",
                    fontFamily = LogUiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    color = DawnPalette.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "+$hiddenCount",
                    fontFamily = SheetSerifFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    color = DawnPalette.muted,
                    maxLines = 1,
                )
            }
        }
    }
}
