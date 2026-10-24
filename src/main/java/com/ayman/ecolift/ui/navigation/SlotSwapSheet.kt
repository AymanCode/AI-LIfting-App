package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.*
import com.ayman.ecolift.ui.viewmodel.*
import com.ayman.ecolift.web.util.noun

/** Opus's slot-history presentation, shared with the atomic active-workout flow. */
@Composable
internal fun SlotSwapSheet(
    ui: SwapSheetUi?,
    onPick: (Long) -> Unit,
    onDrop: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit = {},
    onSearch: (String) -> List<SwapCandidateUi> = { emptyList() },
) {
    SwapSheetFrame(ui, onDismiss = onDismiss) { shown, interactive, tight, closeFocus, dismiss ->
        var query by remember(shown.groupKey, shown.usualExerciseId) { mutableStateOf("") }
        var candidates by remember(shown.groupKey, shown.usualExerciseId) { mutableStateOf(onSearch("")) }
        SwapPickerContent(shown, candidates, query,
            onQuery = { query = it; candidates = onSearch(it) }, onPick = onPick,
            onDrop = onDrop, onUndo = onUndo, interactive = interactive, tight = tight,
            closeFocus = closeFocus, onDismiss = dismiss)
    }
}

@Composable
internal fun ColumnScope.SwapPickerContent(
    ui: SwapSheetUi,
    candidates: List<SwapCandidateUi>,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (Long) -> Unit,
    interactive: Boolean,
    tight: Boolean,
    closeFocus: FocusRequester,
    onDismiss: () -> Unit,
    onDrop: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    primaryActionLabel: ((String) -> String)? = null,
    retainedSets: Int = 0,
    busy: Boolean = false,
    error: String? = null,
) {
    val accent = splitAccentColor(ui.colorIndex)
    val focus = LocalFocusManager.current
    var editor by remember(ui.groupKey, ui.usualExerciseId) { mutableStateOf(query) }
    var selectedId by remember(ui.groupKey, ui.usualExerciseId) { mutableStateOf(ui.currentExerciseId) }
    val searching = editor.isNotBlank()
    val contextRows = if (searching) emptyList() else ui.rows
    val contextIds = contextRows.mapTo(mutableSetOf()) { it.exerciseId }
    val extraRows = if (searching || contextRows.isEmpty()) candidates
        else candidates.filter { it.remembered && it.exerciseId !in contextIds }
    val selectedName = contextRows.firstOrNull { it.exerciseId == selectedId }?.name
        ?: extraRows.firstOrNull { it.exerciseId == selectedId }?.name
    fun choose(id: Long) {
        if (!interactive) return
        if (primaryActionLabel == null) { focus.clearFocus(); onPick(id) } else {
            selectedId = id
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DataText(parts = if (ui.sessions.isEmpty()) listOf(filler("CHOOSE A LIFT")) else
            listOf(filler("LIFTS USED · "), data("${ui.sessions.size}"), filler(" ${noun(ui.sessions.size, "SESSION")}")) +
                (ui.sessionRange?.let { listOf(filler(" · "), data(it.uppercase())) } ?: emptyList()),
            size = 9.sp, dataSize = 10.sp, lineHeight = 13.sp, filler = EspressoPalette.dim, data = EspressoPalette.ink,
            modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, enabled = interactive, modifier = Modifier.size(44.dp).focusRequester(closeFocus)) {
            Icon(Icons.Default.Close, "Close swap sheet", tint = EspressoPalette.dim)
        }
    }
    Text(ui.title, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = if (tight) 21.sp else 24.sp, lineHeight = 27.sp, letterSpacing = (-.6).sp,
        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
    if (retainedSets > 0) DataText(
        parts = listOf(data("$retainedSets"), filler(" logged ${noun(retainedSets, "set stays", "sets stay")}")),
        size = 12.sp, filler = EspressoPalette.dim, data = EspressoPalette.ink,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 5.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (tight) 6.dp else 10.dp)
        .heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(EspressoPalette.panel2).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = EspressoPalette.dim)
        Spacer(Modifier.width(8.dp))
        BasicTextField(value = editor, onValueChange = { value -> editor = value.take(120); onQuery(editor) },
            enabled = interactive, singleLine = true,
            textStyle = TextStyle(fontFamily = LogUiFontFamily, fontSize = 15.sp, color = EspressoPalette.ink),
            cursorBrush = SolidColor(accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp).semantics { contentDescription = "Find an exercise to swap" },
            decorationBox = { field ->
                Box {
                if (editor.isEmpty()) Text("Any other lift · search all", fontFamily = LogUiFontFamily, fontSize = 14.sp, color = EspressoPalette.dim)
                field()
            }
            })
        if (editor.isNotEmpty()) IconButton(onClick = { editor = ""; onQuery("") }, enabled = interactive, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Close, "Clear swap search", Modifier.size(18.dp), tint = EspressoPalette.dim)
        } else Spacer(Modifier.width(12.dp))
    }
    if (busy) Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
        Text("Saving swap…", Modifier.padding(start = 10.dp), fontSize = 13.sp)
    }
    error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 5.dp), fontSize = 13.sp, color = Color(0xFFFFAB91)) }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (contextRows.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tableWidth = maxOf(maxWidth, (156 + ui.sessions.size * 20 + 84).dp)
            Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("LIFTS USED", style = EspressoType.eyebrow.copy(fontSize = 9.sp), color = EspressoPalette.dim, modifier = Modifier.weight(1f))
                    Row(Modifier.width((ui.sessions.size * 20).dp).semantics {
                        contentDescription = "Slot sessions, ${ui.sessions.joinToString(" ") { "${it.month} ${it.day}" }}"
                    }) {
                        ui.sessions.forEachIndexed { index, session ->
                            Column(Modifier.width(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (index == 0 || ui.sessions[index - 1].month != session.month) session.month else "", fontSize = 8.sp, color = EspressoPalette.dim)
                            DataText(listOf(data(session.day)), size = 10.sp, dataSize = 11.sp, data = EspressoPalette.ink)
                        }
                        }
                    }
                    Text("LAST TOP SET", style = EspressoType.eyebrow.copy(fontSize = 9.sp), color = EspressoPalette.dim, textAlign = TextAlign.End, modifier = Modifier.width(84.dp))
                }
                contextRows.forEach { row ->
                    SwapChoiceRow(row.exerciseId, row.name, selectedId, ui, accent, interactive, primaryActionLabel != null, { choose(row.exerciseId) },
                        detail = listOfNotNull(row.topSetLabel ?: "First time", row.lastDateLabel).joinToString(" · ")) {
                        Row(Modifier.width((ui.sessions.size * 20).dp)) { SessionUsageDots(row.sessionDots, accent) }
                        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.End) {
                            DataText(listOf(data(row.topSetLabel ?: "—")), size = 14.sp, dataSize = 14.sp, data = EspressoPalette.ink)
                            row.lastDateLabel?.let { DataText(listOf(data(it)), size = 11.sp, dataSize = 11.sp, data = EspressoPalette.dim) }
                        }
                    }
                }
            }
        }
        if (extraRows.isNotEmpty()) Text(if (searching) "MATCHING LIFTS" else "OTHER LIFTS",
            Modifier.padding(vertical = 8.dp), style = EspressoType.eyebrow.copy(fontSize = 9.sp), color = EspressoPalette.dim)
        extraRows.forEach { row ->
            SwapChoiceRow(row.exerciseId, row.name, selectedId, ui, accent, interactive, primaryActionLabel != null,
                { choose(row.exerciseId) }, detail = row.detail, showDetail = true) {}
        }
        if (contextRows.isEmpty() && extraRows.isEmpty() && searching) Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EspressoEyebrow("No matches")
            DataText(listOf(data("“${editor.trim()}”")), size = 14.sp, filler = EspressoPalette.dim, data = EspressoPalette.ink, maxLines = 2)
        }
        if (onDrop != null) SwapQuietAction("Drop for today", interactive, onDrop)
        if (ui.canUndo && onUndo != null) SwapQuietAction("Undo swap", interactive, onUndo)
    }
    if (primaryActionLabel != null) {
        val enabled = interactive && selectedId != ui.currentExerciseId && selectedName != null
        val label = selectedName?.let(primaryActionLabel) ?: "Choose a lift"
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp)).background(if (enabled) accent else accent.copy(alpha = .35f))
            .clickable(enabled = enabled, role = Role.Button) { focus.clearFocus(); onPick(selectedId) }
            .semantics { contentDescription = label }.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EspressoPalette.onColor, textAlign = TextAlign.Center)
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (tight) 8.dp else 14.dp), contentAlignment = Alignment.Center) {
        EspressoEyebrow("Today only")
    }
}

@Composable
private fun SwapChoiceRow(id: Long, name: String, selectedId: Long, ui: SwapSheetUi, accent: Color,
    interactive: Boolean, confirm: Boolean, onClick: () -> Unit, detail: String, showDetail: Boolean = false,
    trailing: @Composable RowScope.() -> Unit) {
    val current = id == ui.currentExerciseId
    val already = id != ui.currentExerciseId && id in ui.unavailableExerciseIds
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(EspressoPalette.panel2)
        .then(if (confirm && selectedId == id) Modifier.border(1.5.dp, accent, RoundedCornerShape(14.dp)) else Modifier)
        .clickable(enabled = interactive, role = Role.Button, onClick = onClick)
        .semantics {
            contentDescription = "${if (confirm) "Choose" else "Swap to"} $name, $detail" +
            if (current) ", ${if (confirm) "this card" else "today's pick"}" else if (already) ", already in this workout, reuse existing sets" else ""
        }
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(name, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp)
            if (showDetail) Text(detail, Modifier.padding(top = 4.dp), fontFamily = LogUiFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = EspressoPalette.dim)
            if (already) Text("REUSES SETS", style = EspressoType.eyebrow.copy(fontSize = 8.sp), color = EspressoPalette.dim)
            if (current) Text(if (confirm) "THIS CARD" else "TODAY'S PICK", style = EspressoType.eyebrow.copy(fontSize = 8.sp), color = EspressoPalette.ink,
                modifier = Modifier.padding(top = 5.dp).border(1.dp, accent, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
        }
        trailing()
    }
}

@Composable
private fun SwapQuietAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(label, fontFamily = LogUiFontFamily, fontSize = 13.sp, color = EspressoPalette.dim)
    }
}
