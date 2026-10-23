package com.ayman.ecolift.ui.cardio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.CardioFieldRules
import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import com.ayman.ecolift.ui.viewmodel.CardioDraftLogic
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import com.ayman.ecolift.ui.viewmodel.ReadState
import com.ayman.ecolift.ui.viewmodel.SaveState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun CardioScanScreen(
    viewModel: CardioViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = rememberReducedMotion()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelScan()
        }
    }

    LaunchedEffect(state.toast, state.draft) {
        if (state.toast != null && state.draft == null) {
            onDone()
        }
    }

    val photoBitmap = remember(state.scanPhotoBase64) {
        state.scanPhotoBase64?.let { b64 ->
            runCatching {
                @OptIn(ExperimentalEncodingApi::class)
                val bytes = Base64.decode(b64)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                bitmap.asImageBitmap()
            }.getOrNull()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanTransition")
    val scanLineProgress by if (reducedMotion) {
        remember { mutableStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "scanLine",
        )
    }

    val skyGradient = remember {
        Brush.verticalGradient(
            0f to DawnPalette.skyTop,
            0.42f to DawnPalette.skyMiddle,
            1f to DawnPalette.skyBottom,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(skyGradient)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        if (state.scanning) {
            // Scanning in-progress state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, bottom = 24.dp),
            ) {
                // Top back bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DawnPalette.ink,
                        )
                    }
                    Text(
                        text = "Scan display",
                        fontFamily = SheetSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Light,
                        fontSize = 26.sp,
                        color = DawnPalette.ink,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Light card with photo & moving scan line
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = DawnPalette.panel,
                    border = BorderStroke(1.dp, DawnPalette.line),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap,
                                contentDescription = "Captured display",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DawnPalette.panel),
                            )
                        }

                        // Scanning laser line
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val y = size.height * scanLineProgress
                            drawLine(
                                color = DawnPalette.accent,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Reading the display…",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = LogUiFontFamily,
                    fontSize = 15.sp,
                    color = DawnPalette.muted,
                )
            }
        } else {
            // Results screen ("From the display")
            val draft = state.draft
            if (draft != null) {
                val spec = CardioActivitySpecs.specFor(draft.type)
                val fields = remember(draft.type) {
                    spec.fields.filter { it != CardioEntryField.NOTES }
                }

                val lastSession = remember(draft.type, state.sessions) {
                    CardioInsights.lastOnMachine(state.sessions, draft.type)
                }

                val machines = remember {
                    listOf(
                        CardioActivityType.TREADMILL,
                        CardioActivityType.BIKE,
                        CardioActivityType.ROW,
                        CardioActivityType.ELLIPTICAL,
                        CardioActivityType.STAIR_CLIMBER,
                    )
                }
                val selectedMachineIdx = machines.indexOf(draft.type).coerceAtLeast(0)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Top header: back arrow, title "From the display", right "Retake"
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IconButton(onClick = onCancel) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = DawnPalette.ink,
                                    )
                                }
                                Text(
                                    text = "From the display",
                                    fontFamily = SheetSerifFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 26.sp,
                                    color = DawnPalette.ink,
                                )
                            }
                            Text(
                                text = "Retake",
                                modifier = Modifier
                                    .clickable { viewModel.startScan() }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                fontFamily = LogUiFontFamily,
                                fontSize = 14.sp,
                                color = DawnPalette.muted,
                            )
                        }
                    }

                    // Photo thumbnail card with machine name, scan count summary
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = DawnPalette.panel,
                            border = BorderStroke(1.dp, DawnPalette.line),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (photoBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                                    ) {
                                        Image(
                                            bitmap = photoBitmap,
                                            contentDescription = "Display thumbnail",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0x22000000)),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = CardioActivitySpecs.specFor(draft.type).label,
                                            fontFamily = LogUiFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = DawnPalette.ink,
                                        )
                                    }
                                    Text(
                                        text = "${state.scanReadCount} of ${state.scanFieldCount} read · tap any to change",
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 12.sp,
                                        color = DawnPalette.accentText,
                                    )
                                }
                            }
                        }
                    }

                    // Low confidence warning: the scan may have misread a value.
                    if (state.scanLowConfidence) {
                        item {
                            DawnEyebrow(
                                "Check the values",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                color = DawnPalette.accentText,
                            )
                        }
                    }

                    // Machine chips row
                    item {
                        ChipRow(
                            labels = machines.map { CardioActivitySpecs.specFor(it).label },
                            selectedIndex = selectedMachineIdx,
                            onSelect = { idx -> viewModel.selectMachine(machines[idx]) },
                        )
                    }

                    // Field rows / in-place scrub fields
                    items(fields) { field ->
                        val isExpanded = (state.expandedField == field)
                        val readState = draft.readStates[field] ?: ReadState.READ
                        val curVal = draft.values[field]
                        val label = cardioFieldLabel(field, draft.type)
                        val unit = cardioFieldUnit(field, draft.type)

                        if (isExpanded) {
                            val lastDisplay = curVal?.let { CardioFieldRules.display(field, draft.type, it) } ?: ""
                            DawnScrubField(
                                label = label,
                                valueText = curVal?.let { CardioFieldRules.display(field, draft.type, it) } ?: "—",
                                unit = unit,
                                lastText = "read from photo: $lastDisplay",
                                selected = true,
                                onDrag = { ticks -> viewModel.scrub(field, ticks) },
                                onTapToType = { viewModel.beginTyping(field) },
                                onTapReuse = { viewModel.reuseLast(field) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.expandField(field) },
                                color = Color.Transparent,
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = label,
                                                fontFamily = LogUiFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp,
                                                color = if (readState == ReadState.CHECK) DawnPalette.accentText else DawnPalette.ink,
                                            )
                                            when (readState) {
                                                ReadState.CHECK -> {
                                                    Text(
                                                        text = "Check this value",
                                                        fontFamily = LogUiFontFamily,
                                                        fontSize = 11.5.sp,
                                                        color = DawnPalette.accentText,
                                                    )
                                                }
                                                ReadState.UNREAD -> {
                                                    val sub = if (field == CardioEntryField.AVG_HEART_RATE || field == CardioEntryField.MAX_HEART_RATE) {
                                                        "not read · optional"
                                                    } else {
                                                        val lastVal = curVal?.let { CardioFieldRules.display(field, draft.type, it) }
                                                        if (lastVal != null) {
                                                            "not read · last time $lastVal"
                                                        } else {
                                                            "not read"
                                                        }
                                                    }
                                                    Text(
                                                        text = sub,
                                                        fontFamily = LogUiFontFamily,
                                                        fontSize = 11.5.sp,
                                                        color = DawnPalette.muted,
                                                    )
                                                }
                                                ReadState.READ -> {
                                                    // No extra subtext needed
                                                }
                                            }
                                        }

                                        if (readState == ReadState.UNREAD) {
                                            Text(
                                                text = "drag to set ›",
                                                fontFamily = LogUiFontFamily,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 14.sp,
                                                color = DawnPalette.muted,
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                val disp = curVal?.let { CardioFieldRules.display(field, draft.type, it) } ?: "—"
                                                Text(
                                                    text = disp,
                                                    fontFamily = SheetSerifFontFamily,
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 18.sp,
                                                    color = if (readState == ReadState.CHECK) DawnPalette.accentText else DawnPalette.ink,
                                                )
                                                if (unit.isNotBlank()) {
                                                    Text(
                                                        text = unit,
                                                        fontFamily = LogUiFontFamily,
                                                        fontSize = 11.sp,
                                                        color = DawnPalette.muted,
                                                    )
                                                }
                                                Text(
                                                    text = "›",
                                                    fontFamily = LogUiFontFamily,
                                                    fontSize = 16.sp,
                                                    color = DawnPalette.muted,
                                                )
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(DawnPalette.line),
                                    )
                                }
                            }
                        }
                    }

                    // Bottom action pill & scans left hint
                    item {
                        Spacer(Modifier.height(12.dp))

                        // Build summary line for pill subtitle
                        val summaryParts = mutableListOf<String>()
                        draft.values[CardioEntryField.DURATION]?.let {
                            summaryParts.add(CardioFieldRules.display(CardioEntryField.DURATION, draft.type, it))
                        }
                        draft.values[CardioEntryField.DISTANCE_MILES]?.let {
                            summaryParts.add("${CardioFieldRules.display(CardioEntryField.DISTANCE_MILES, draft.type, it)} ${cardioFieldUnit(CardioEntryField.DISTANCE_MILES, draft.type)}")
                        }
                        draft.values[CardioEntryField.AVG_SPEED_MPH]?.let {
                            summaryParts.add("${CardioFieldRules.display(CardioEntryField.AVG_SPEED_MPH, draft.type, it)} mph")
                        }
                        draft.values[CardioEntryField.AVG_HEART_RATE]?.let {
                            summaryParts.add("${CardioFieldRules.display(CardioEntryField.AVG_HEART_RATE, draft.type, it)} bpm")
                        }
                        val summaryText = if (summaryParts.isNotEmpty()) summaryParts.joinToString(" · ") else null

                        DawnPill(
                            text = "Add to today",
                            subtext = summaryText,
                            enabled = CardioDraftLogic.canSave(draft),
                            loading = state.saveState == SaveState.SAVING,
                            onClick = { viewModel.save() },
                        )

                        Spacer(Modifier.height(10.dp))

                        val scansCountText = if (state.scansLeft != null) " ${state.scansLeft} scans left today." else ""
                        Text(
                            text = "The app does not keep your photo.$scansCountText",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontFamily = LogUiFontFamily,
                            fontSize = 12.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }

                // In-place typing sheet if active
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
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.commitTyped(typingField, typedText) },
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DawnPalette.accent,
                                unfocusedBorderColor = DawnPalette.line,
                                focusedTextColor = DawnPalette.ink,
                                unfocusedTextColor = DawnPalette.ink,
                            ),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.clearField(typingField)
                                    viewModel.commitTyped(typingField, "")
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Clear", color = DawnPalette.muted)
                            }
                            Button(
                                onClick = {
                                    viewModel.commitTyped(typingField, typedText)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = DawnPalette.accent),
                            ) {
                                Text("Done", color = DawnPalette.pillText)
                            }
                        }
                    }
                }
            } else {
                // If draft is null and not scanning (e.g. cancelled), call onCancel
                LaunchedEffect(Unit) {
                    onCancel()
                }
            }
        }
    }
}
