package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ayman.ecolift.data.splits.SplitMemberEdit
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoGrabber
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.SplitCardMemberUi
import com.ayman.ecolift.ui.viewmodel.SplitGroupCardUi
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.web.util.keyboardOverlap
import kotlinx.coroutines.launch

internal fun splitEditorSummary(card: SplitGroupCardUi): List<DataPart> = buildList {
    add(data("${card.sessions}"))
    add(filler(if (card.sessions == 1) " session" else " sessions"))
    val last = card.lastSessionDate ?: return@buildList
    add(filler(" · last "))
    add(data(formatMonthDay(last)))
    if (card.estimatedMinutes > 0) {
        add(filler(" · ~"))
        add(data("${card.estimatedMinutes}"))
        add(filler(" min"))
    }
}

/**
 * Every change is one [SplitMemberEdit]; the card it reads already shows a board being saved.
 * [canEdit] is false until the saved boards are read; [pending] holds Start while a save is out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitEditorSheet(
    card: SplitGroupCardUi,
    canEdit: Boolean,
    pending: Boolean,
    onDismiss: () -> Unit,
    onEdit: (SplitMemberEdit) -> Unit,
    onAddLift: (name: String, onSaved: () -> Unit) -> Unit,
    onRename: () -> Unit,
    onFromWorkout: () -> Unit,
    onRetire: () -> Unit,
    onStart: () -> Unit,
    onOpenLift: (exerciseId: Long) -> Unit,
) {
    val accent = splitAccentColor(card.colorIndex)
    val ids = card.members.map { it.exerciseId }
    var dragId by remember(ids) { mutableStateOf<Long?>(null) }
    var dragOffset by remember(ids) { mutableFloatStateOf(0f) }
    val rowBounds = remember(ids) { mutableStateMapOf<Long, Pair<Float, Float>>() }
    val keyboard = keyboardOverlap()
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf<Rect?>(null) }
    val handles = remember { HashMap<Long, LayoutCoordinates>() }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    // Near the top or bottom of the list while dragging, the list scrolls and the row stays under
    // the finger, so a lift can reach a row that was off screen.
    LaunchedEffect(dragId) {
        if (dragId == null) return@LaunchedEffect
        while (true) {
            val bounds = viewport
            val step = when {
                bounds == null -> 0f
                pointerY > bounds.bottom - edge -> 12f
                pointerY < bounds.top + edge -> -12f
                else -> 0f
            }
            if (step != 0f) dragOffset += scroll.scrollBy(step)
            withFrameNanos { }
        }
    }
    val edit: (SplitMemberEdit) -> Unit = { if (canEdit) onEdit(it) }

    fun drop() {
        val id = dragId ?: return
        val (top, height) = rowBounds[id] ?: return
        val center = top + dragOffset + height / 2
        val before = ids.filter { it != id }.firstOrNull { other ->
            val (otherTop, otherHeight) = rowBounds[other] ?: return@firstOrNull false
            center < otherTop + otherHeight / 2
        }
        dragId = null
        dragOffset = 0f
        edit(SplitMemberEdit.MoveBefore(id, before))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = dragId == null,
        containerColor = EspressoPalette.panel,
        contentColor = EspressoPalette.ink,
        scrimColor = EspressoPalette.mast.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 0.dp,
        dragHandle = { EspressoGrabber() },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .onGloballyPositioned { viewport = it.boundsInWindow() }
                    .verticalScroll(scroll, enabled = dragId == null)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                EditorHeader(card, accent, onRename)
                Column {
                    SectionLabel("IN THIS SPLIT", card.members.size)
                    Column {
                        card.members.forEachIndexed { index, member ->
                            val id = member.exerciseId
                            val dragged = dragId == id
                            LiftRow(
                                member = member,
                                accent = accent,
                                onOpen = { onOpenLift(id) },
                                modifier = Modifier
                                    .onGloballyPositioned { rowBounds[id] = it.positionInParent().y to it.size.height.toFloat() }
                                    .zIndex(if (dragged) 1f else 0f)
                                    .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                                    .background(if (dragged) EspressoPalette.panel2 else Color.Transparent),
                                leading = {
                                    DragHandle(
                                        name = member.name,
                                        moveUp = if (index > 0) ({ edit(SplitMemberEdit.MoveBefore(id, ids[index - 1])) }) else null,
                                        moveDown = if (index < ids.lastIndex) ({ edit(SplitMemberEdit.MoveBefore(id, ids.getOrNull(index + 2))) }) else null,
                                        modifier = Modifier
                                            .onGloballyPositioned { handles[id] = it }
                                            .pointerInput(ids, canEdit) {
                                                if (!canEdit) return@pointerInput
                                                detectDragGestures(
                                                    onDragStart = { start ->
                                                        dragId = id
                                                        dragOffset = 0f
                                                        pointerY = handles[id]?.localToWindow(start)?.y ?: 0f
                                                    },
                                                    onDragEnd = { drop() },
                                                    onDragCancel = { dragId = null; dragOffset = 0f },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragOffset += amount.y
                                                        handles[id]?.let { pointerY = it.localToWindow(change.position).y }
                                                    },
                                                )
                                            },
                                    )
                                },
                                trailing = {
                                    RoundAction(
                                        label = "Move ${member.name} to other options",
                                        dashed = null,
                                        onClick = { edit(SplitMemberEdit.Exclude(id)) },
                                    ) { Text("−", style = StickerType.body(20.sp, EspressoPalette.ink)) }
                                },
                            )
                        }
                    }
                }
                if (card.otherOptions.isNotEmpty()) {
                    Column {
                        SectionLabel("OTHER OPTIONS", card.otherOptions.size)
                        card.otherOptions.forEach { member ->
                            LiftRow(
                                member = member,
                                accent = accent,
                                onOpen = { onOpenLift(member.exerciseId) },
                                leading = { Spacer(Modifier.width(44.dp)) },
                                trailing = {
                                    RoundAction(
                                        label = "Add ${member.name} to ${card.name}",
                                        dashed = accent,
                                        onClick = { edit(SplitMemberEdit.Include(member.exerciseId)) },
                                    ) { Text("+", style = StickerType.body(20.sp, accent)) }
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AddLiftField(enabled = canEdit, onAdd = onAddLift, modifier = Modifier.weight(1f))
                    FromWorkoutButton(onFromWorkout)
                }
                Text(
                    "Retire split",
                    style = StickerType.body(13.sp, EspressoPalette.danger).copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .semantics { contentDescription = "Retire split"; role = Role.Button }
                        .clickable(onClick = onRetire)
                        .padding(vertical = 13.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(EspressoPalette.ink.copy(alpha = 0.09f)))
            StartButton(
                card = card,
                accent = accent,
                enabled = canEdit && !pending && card.isSaved && card.members.isNotEmpty(),
                onStart = onStart,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp + keyboard),
            )
        }
    }
}

@Composable
private fun EditorHeader(card: SplitGroupCardUi, accent: Color, onRename: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text("EDIT SPLIT", style = StickerType.print(10.5.sp, EspressoPalette.dim, 1.7.sp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.name,
                style = StickerType.display(30.sp, EspressoPalette.ink, (-0.6).sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Rename ${card.name}"; role = Role.Button }
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = EspressoPalette.dim, modifier = Modifier.size(18.dp))
            }
        }
        DataText(
            parts = splitEditorSummary(card),
            size = 13.sp,
            dataSize = 13.sp,
            filler = EspressoPalette.dim,
            data = EspressoPalette.ink,
        )
    }
}

@Composable
private fun SectionLabel(title: String, count: Int) {
    Text(
        buildAnnotatedString {
            append(title)
            withStyle(SpanStyle(color = EspressoPalette.dim)) { append("  $count") }
        },
        style = StickerType.print(11.sp, EspressoPalette.ink, 1.7.sp),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun LiftRow(
    member: SplitCardMemberUi,
    accent: Color,
    onOpen: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .drawBehind { drawRect(EspressoPalette.ink.copy(alpha = 0.09f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = liftDescription(member); role = Role.Button }
                .clickable(onClick = onOpen)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(member.name, style = StickerType.body(15.sp, EspressoPalette.ink).copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                member.metrics.topSetLabel?.let {
                    Text(it, style = StickerType.body(13.sp, lerpSoft(accent)).copy(fontWeight = FontWeight.ExtraBold))
                }
                member.metrics.deltaPercent?.let { DeltaMark(it) }
            }
        }
        if (member.metrics.workingVolume.isNotEmpty()) {
            TrendBars(member.metrics.workingVolume, accent, Modifier.padding(horizontal = 10.dp).width(34.dp).height(18.dp))
        }
        trailing()
    }
}

private fun lerpSoft(accent: Color): Color = androidx.compose.ui.graphics.lerp(accent, Color.White, 0.55f)

@Composable
private fun DragHandle(name: String, moveUp: (() -> Unit)?, moveDown: (() -> Unit)?, modifier: Modifier) {
    Box(
        modifier
            .size(44.dp)
            .semantics {
                contentDescription = "Reorder $name"
                customActions = listOfNotNull(
                    moveUp?.let { CustomAccessibilityAction("Move up") { it(); true } },
                    moveDown?.let { CustomAccessibilityAction("Move down") { it(); true } },
                )
            }
            .drawBehind {
                val dot = 1.6.dp.toPx()
                val step = 5.dp.toPx()
                val left = size.width / 2 - step / 2
                val top = size.height / 2 - step
                for (column in 0..1) {
                    for (row in 0..2) {
                    drawCircle(EspressoPalette.dim, dot, androidx.compose.ui.geometry.Offset(left + column * step, top + row * step))
                }
                }
            },
    )
}

@Composable
private fun RoundAction(label: String, dashed: Color?, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (dashed == null) {
                    Modifier.background(EspressoPalette.ink.copy(alpha = 0.06f)).border(1.dp, EspressoPalette.ink.copy(alpha = 0.1f), CircleShape)
                } else {
                    Modifier.drawBehind {
                        drawRoundRect(
                            dashed,
                            cornerRadius = CornerRadius(size.minDimension / 2),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                },
            )
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The add field's draft. A saved name leaves it empty, unless something newer was typed while it saved. */
internal class AddLiftDraft {
    var text by mutableStateOf("")

    fun submit(): String? = text.trim().ifEmpty { null }

    fun saved(submitted: String) {
        if (text.trim() == submitted) text = ""
    }
}

@Composable
private fun AddLiftField(enabled: Boolean, onAdd: (String, () -> Unit) -> Unit, modifier: Modifier) {
    val draft = remember { AddLiftDraft() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    fun submit() {
        if (!enabled) return
        val submitted = draft.submit() ?: return
        onAdd(submitted) { draft.saved(submitted) }
    }
    BasicTextField(
        value = draft.text,
        onValueChange = { draft.text = it },
        singleLine = true,
        enabled = enabled,
        textStyle = StickerType.body(14.sp, EspressoPalette.ink),
        cursorBrush = SolidColor(EspressoPalette.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = modifier
            .height(48.dp)
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { if (it.isFocused) scope.launch { bringIntoView.bringIntoView() } }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                    submit()
                    true
                } else {
                    false
                }
            }
            .semantics { contentDescription = "Add a lift" },
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .drawBehind {
                        drawRoundRect(
                            EspressoPalette.ink.copy(alpha = 0.22f),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = EspressoPalette.dim, modifier = Modifier.size(16.dp))
                Box(Modifier.weight(1f)) {
                    if (draft.text.isEmpty()) Text("Add a lift", style = StickerType.body(14.sp, EspressoPalette.dim))
                    field()
                }
            }
        },
    )
}

@Composable
private fun FromWorkoutButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(48.dp)
            .clip(shape)
            .background(EspressoPalette.ink.copy(alpha = 0.06f))
            .border(1.dp, EspressoPalette.ink.copy(alpha = 0.1f), shape)
            .semantics { contentDescription = "From a workout"; role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = EspressoPalette.ink, modifier = Modifier.size(16.dp))
        Text("From a workout", style = StickerType.body(13.sp, EspressoPalette.ink).copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun StartButton(card: SplitGroupCardUi, accent: Color, enabled: Boolean, onStart: () -> Unit, modifier: Modifier) {
    val count = card.members.size
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (enabled) accent else accent.copy(alpha = 0.35f))
            .semantics {
                contentDescription = "Start ${card.name}, $count ${if (count == 1) "lift" else "lifts"}"
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, onClick = onStart),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildAnnotatedString {
                append("Start ${card.name}")
                withStyle(SpanStyle(fontSize = 10.5.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)) {
                    append("  · $count ${if (count == 1) "LIFT" else "LIFTS"}")
                }
            },
            style = StickerType.body(15.sp, EspressoPalette.onColor).copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
