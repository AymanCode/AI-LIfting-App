package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The longest name a split can carry. Longer input is refused, not cut. */
internal const val SPLIT_NAME_MAX_LENGTH = 24

/** What leaving the name editor does with the draft. */
internal sealed interface SplitNameCommit {
    /** Blank after trimming: the saved name writes back in and nothing is saved. */
    data object Revert : SplitNameCommit

    /** Trimmed to the saved name: nothing is saved. */
    data object Unchanged : SplitNameCommit

    /** A new, trimmed, non-blank name to save. */
    data class Save(val name: String) : SplitNameCommit
}

internal fun splitNameCommit(draft: String, saved: String): SplitNameCommit {
    val trimmed = draft.trim()
    return when {
        trimmed.isEmpty() -> SplitNameCommit.Revert
        trimmed == saved.trim() -> SplitNameCommit.Unchanged
        else -> SplitNameCommit.Save(trimmed)
    }
}

internal fun acceptsSplitNameInput(text: String): Boolean = text.length <= SPLIT_NAME_MAX_LENGTH

/**
 * How wide the name's tap target is at rest: the printed name's longest line plus a little slack,
 * never narrower than a finger and never past the band. The band beside the name stays the sheet's.
 */
internal fun splitNameHitWidth(lineRights: List<Float>, slackPx: Float, minPx: Float, availPx: Float): Float =
    ((lineRights.maxOrNull() ?: 0f) + slackPx).coerceAtLeast(minPx).coerceAtMost(availPx.coerceAtLeast(0f))

/**
 * The one edit between [old] and [new] as (start, oldEnd, newEnd): `old[start, oldEnd)` was
 * replaced by `new[start, newEnd)`. A removal has `start == newEnd`, an insertion `start == oldEnd`.
 */
internal fun splitNameDiff(old: String, new: String): Triple<Int, Int, Int> {
    val shorter = min(old.length, new.length)
    var start = 0
    while (start < shorter && old[start] == new[start]) start++
    var suffix = 0
    while (suffix < shorter - start && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
    return Triple(start, old.length - suffix, new.length - suffix)
}

internal enum class SplitNamePhase { Rest, Editing, Soaking }

private const val NAME_SP = 30f
private const val NAME_MIN_SP = 18f
private const val NAME_LINE_SP = 31f
private const val ROLL_MS = 320
private const val DAB_MS = 100
private const val INK_MS = 420
private const val RELINK_STAGGER_MS = 25
private const val SOAK_MS = 550
private const val CAP_RATIO = 0.72f
private const val DESCENDER_RATIO = 0.26f
private val TapeEase = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
private val DabEase = CubicBezierEasing(0f, 0f, 0.2f, 1f)
private val TapePaper = Color(0xFFF7F3EB)
/** The name's tap target at rest: the printed name plus this slack, never narrower than a finger. */
private val NAME_HIT_SLACK = 10.dp
private val NAME_HIT_MIN = 48.dp

private fun nameStyle(sp: Float) = TextStyle(
    fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
    fontSize = sp.sp, lineHeight = (sp + 1f).sp, letterSpacing = (-0.9).sp,
)

/** The largest size (30 sp down to 18 sp) at which [text] fits one line in [availPx]. */
private fun fitNameSp(measurer: TextMeasurer, text: String, availPx: Float): Float {
    if (availPx <= 0f) return NAME_SP
    var sp = NAME_SP
    while (sp > NAME_MIN_SP && measurer.measure(text, nameStyle(sp), maxLines = 1, softWrap = false).size.width > availPx) sp -= 1f
    return sp
}

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** One letter inking in after it was typed (or relinked). Each has its own clock. */
private class InkLetter(val index: Int) {
    val progress = Animatable(0f)
}

/** One letter removed by ⌫, fading out under the growing patch. */
private class GhostLetter(val char: String, val x: Float) {
    val alpha = Animatable(1f)
}

private sealed interface NameChange {
    /** One edit from the field. [wasAll] means the whole name was selected before the keystroke. */
    data class Edit(val prev: String, val next: String, val wasAll: Boolean) : NameChange
    data class Commit(val draft: String) : NameChange
}

/**
 * The split name on the Log suggested sheet. The text field is always composed over the name, the
 * same way the "Add a lift" search field is, so a tap lands on the input inside the user's gesture
 * and the iPhone keyboard opens. Focus starts edit mode with the whole name selected: the first
 * keystroke rolls white-out tape across it, ⌫ dabs a patch over the letter, and typed letters land
 * on the white and ink in one by one. Leaving the field (Done or tapping away) saves.
 */
@Composable
internal fun InlineSplitName(
    name: String,
    isUnnamed: Boolean,
    phase: MutableState<SplitNamePhase>,
    onCommitName: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    fun dur(ms: Int) = if (reduced) 0 else ms
    val haptic = rememberLightHaptic()
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val changes = remember { Channel<NameChange>(Channel.UNLIMITED) }

    var shown by remember { mutableStateOf(name) }
    var field by remember { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
    var hasBeenFocused by remember { mutableStateOf(false) }
    var avail by remember { mutableFloatStateOf(0f) }
    var patchOn by remember { mutableStateOf(false) }
    var rolling by remember { mutableStateOf(false) }
    val patchLeft = remember { Animatable(0f) }
    val patchRight = remember { Animatable(0f) }
    val soak = remember { Animatable(1f) }
    val inks = remember { mutableStateListOf<InkLetter>() }
    val ghosts = remember { mutableStateListOf<GhostLetter>() }

    fun geometry(text: String): TextLayoutResult {
        val sp = fitNameSp(measurer, text, avail)
        return measurer.measure(text, nameStyle(sp), maxLines = 1, softWrap = false)
    }

    val fit = fitNameSp(measurer, shown, avail)
    val geo = remember(shown, fit) { measurer.measure(shown, nameStyle(fit), maxLines = 1, softWrap = false) }
    val inkKey = inks.map { it.index }
    val rollFadeKey = if (rolling) List(shown.length) { i ->
        val b = geo.getBoundingBox(i)
        val a = 1f - clamp01((patchRight.value - b.left) / (0.6f * max(b.width, 1f)))
        (a * 10f).roundToInt()
    } else emptyList()

    val drawLayout = remember(shown, fit, inkKey, rolling, rollFadeKey) {
        val annotated = buildAnnotatedString {
            append(shown)
            if (rolling) rollFadeKey.forEachIndexed { i, step ->
                if (step < 10) addStyle(SpanStyle(color = SheetPaper.ink.copy(alpha = step / 10f)), i, i + 1)
            }
            // Letters still inking are drawn by the overlay, so the base layer leaves them out.
            inkKey.forEach { if (it < shown.length) addStyle(SpanStyle(color = Color.Transparent), it, it + 1) }
        }
        measurer.measure(annotated, nameStyle(fit), maxLines = 1, softWrap = false)
    }

    fun requestCommit() {
        if (phase.value != SplitNamePhase.Editing) return
        phase.value = SplitNamePhase.Soaking
        changes.trySend(NameChange.Commit(field.text))
        focusManager.clearFocus()
    }

    fun onInput(next: TextFieldValue) {
        if (phase.value != SplitNamePhase.Editing) return
        if (!acceptsSplitNameInput(next.text)) return
        val wasAll = field.text.isNotEmpty() && field.selection.min == 0 && field.selection.max == field.text.length
        if (!wasAll && next.text == field.text) {
            field = next
            return
        }
        changes.trySend(NameChange.Edit(prev = field.text, next = next.text, wasAll = wasAll))
        field = next
    }

    fun inkLetters(range: IntRange, staggerMs: Int) {
        for (i in range) {
            val ink = InkLetter(i)
            inks.add(ink)
            val delayMs = (i - range.first) * staggerMs
            scope.launch {
                if (delayMs > 0) delay(dur(delayMs).toLong())
                ink.progress.animateTo(1f, tween(dur(INK_MS)))
                inks.remove(ink)
            }
        }
    }

    fun applyText(next: String, inserted: IntRange?, staggerMs: Int) {
        shown = next
        if (patchOn) scope.launch { patchRight.snapTo(max(patchRight.value, geometry(next).size.width.toFloat())) }
        if (inserted != null && !inserted.isEmpty()) inkLetters(inserted, staggerMs)
    }

    suspend fun applyEdit(e: NameChange.Edit) {
        if (e.wasAll) {
            // One stroke of tape over the whole name, left to right.
            val textRight = geometry(shown).size.width.toFloat()
            patchOn = true
            rolling = true
            patchLeft.snapTo(0f)
            patchRight.snapTo(0f)
            patchRight.animateTo(textRight + 3f, tween(dur(ROLL_MS), easing = TapeEase))
            rolling = false
            haptic()
            applyText(e.next, if (e.next.isNotEmpty()) e.next.indices else null, 0)
            return
        }
        val before = shown
        val (start, oldEnd, newEnd) = splitNameDiff(before, e.next)
        if (oldEnd > start) {
            // A dab: the patch grows leftward over the removed letters, and each removed letter fades under it.
            val g = geometry(before)
            val left = g.getBoundingBox(start).left
            val right = g.getBoundingBox(oldEnd - 1).right
            if (!patchOn) {
                patchOn = true
                patchLeft.snapTo(right)
                patchRight.snapTo(right)
            }
            patchRight.snapTo(max(patchRight.value, right))
            scope.launch { patchLeft.animateTo(min(patchLeft.value, left), tween(dur(DAB_MS), easing = DabEase)) }
            for (i in start until oldEnd) {
                val ghost = GhostLetter(before[i].toString(), g.getBoundingBox(i).left)
                ghosts.add(ghost)
                scope.launch {
                    ghost.alpha.animateTo(0f, tween(dur(DAB_MS), easing = DabEase))
                    ghosts.remove(ghost)
                }
            }
            inks.removeAll { it.index >= start }
            haptic()
        }
        if (newEnd > start) haptic()
        applyText(e.next, if (newEnd > start) start until newEnd else null, 0)
    }

    suspend fun applyCommit(draft: String) {
        when (val c = splitNameCommit(draft, name)) {
            SplitNameCommit.Revert -> {
                applyText(name, name.indices, RELINK_STAGGER_MS)
                delay(dur(INK_MS + name.length * RELINK_STAGGER_MS).toLong())
            }
            SplitNameCommit.Unchanged -> Unit
            is SplitNameCommit.Save -> {
                onCommitName(c.name)
                shown = c.name
            }
        }
        // The white soaks into the band, then the name is at rest.
        soak.snapTo(1f)
        soak.animateTo(0f, tween(dur(SOAK_MS)))
        patchOn = false
        phase.value = SplitNamePhase.Rest
    }

    LaunchedEffect(Unit) {
        for (change in changes) {
            when (change) {
                is NameChange.Edit -> applyEdit(change)
                is NameChange.Commit -> applyCommit(change.draft)
            }
        }
    }

    val editing = phase.value == SplitNamePhase.Editing
    val soaking = phase.value == SplitNamePhase.Soaking
    val restLayout = remember(name, avail) {
        measurer.measure(
            text = name, style = nameStyle(NAME_SP), overflow = TextOverflow.Ellipsis,
            softWrap = true, maxLines = 2, constraints = Constraints(maxWidth = avail.roundToInt().coerceAtLeast(0)),
        )
    }
    val restLines = restLayout.lineCount.coerceAtLeast(1)
    val heightDp: Dp = (restLines * NAME_LINE_SP).dp
    val density = LocalDensity.current
    val restHitWidth: Dp = with(density) {
        splitNameHitWidth(
            lineRights = (0 until restLayout.lineCount).map { restLayout.getLineRight(it) },
            slackPx = NAME_HIT_SLACK.toPx(), minPx = NAME_HIT_MIN.toPx(), availPx = avail,
        ).toDp()
    }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availPx = with(LocalDensity.current) { maxWidth.toPx() }
            SideEffect { avail = availPx }
            Box(Modifier.fillMaxWidth().height(heightDp)) {
                if (!editing && !soaking) {
                    Text(
                        text = name,
                        fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 30.sp, lineHeight = 31.sp, letterSpacing = (-0.9).sp,
                        color = SheetPaper.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                } else {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics { contentDescription = "Split name, $shown" },
                    ) {
                        val fs = fit.sp.toPx()
                        val baseline = geo.getLineBaseline(0)
                        val capTop = baseline - CAP_RATIO * fs
                        val pad = 2.dp.toPx()
                        val top = capTop - pad
                        val bottom = baseline + DESCENDER_RATIO * fs
                        val soakAlpha = if (soaking) soak.value else 1f

                        if (editing && field.text.isNotEmpty() && field.selection.min == 0 && field.selection.max == field.text.length) {
                            val w = geo.size.width.toFloat()
                            drawRect(Color.White.copy(alpha = 0.16f), Offset(-4.dp.toPx(), top - 2.dp.toPx()), Size(w + 8.dp.toPx(), bottom - top + 4.dp.toPx()))
                            drawRect(
                                SheetPaper.ink.copy(alpha = 0.45f), Offset(-4.dp.toPx(), top - 2.dp.toPx()),
                                Size(w + 8.dp.toPx(), bottom - top + 4.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                            )
                        }

                        if (patchOn) {
                            val l = patchLeft.value
                            val r = max(patchRight.value, if (rolling) 0f else geo.size.width.toFloat())
                            if (r > l) {
                                val w = r - l
                                val h = bottom - top
                                drawRoundRect(Color.Black.copy(alpha = 0.18f * soakAlpha), Offset(l, top + 1.5.dp.toPx()), Size(w, h), CornerRadius(3.dp.toPx()))
                                drawRoundRect(TapePaper.copy(alpha = soakAlpha), Offset(l, top), Size(w, h), CornerRadius(3.dp.toPx()))
                                drawRoundRect(TapePaper.copy(alpha = soakAlpha), Offset(l + 0.6.dp.toPx(), top + 0.5.dp.toPx()), Size(w - 1.2.dp.toPx(), h - 0.6.dp.toPx()), CornerRadius(2.dp.toPx()))
                            }
                        }

                        drawText(drawLayout, color = SheetPaper.ink.copy(alpha = soakAlpha))

                        // Each typed letter inks in on its own clock, so fast typing shows several at once.
                        for (ink in inks.toList()) {
                            val i = ink.index
                            if (i >= shown.length) continue
                            val p = ink.progress.value
                            if (p >= 1f) continue
                            val box = geo.getBoundingBox(i)
                            val s = 1.08f - 0.08f * p
                            val dy = -5.dp.toPx() * (1f - p)
                            withTransform({
                                translate(0f, dy)
                                scale(s, s, pivot = Offset(box.center.x, capTop + CAP_RATIO * fs / 2f))
                            }) {
                                drawText(
                                    textMeasurer = measurer, text = shown[i].toString(), topLeft = Offset(box.left, 0f),
                                    style = nameStyle(fit).copy(
                                        color = SheetPaper.ink.copy(alpha = (0.2f + 0.8f * min(1f, p / 0.35f)) * soakAlpha),
                                        shadow = Shadow(SheetPaper.ink.copy(alpha = 0.9f * (1f - p)), Offset(1f, 0f), 4f * (1f - p)),
                                    ),
                                )
                            }
                        }

                        // Letters removed by ⌫ fade out where they were, under the patch.
                        for (ghost in ghosts.toList()) {
                            drawText(
                                textMeasurer = measurer, text = ghost.char, topLeft = Offset(ghost.x, 0f),
                                style = nameStyle(fit).copy(color = SheetPaper.ink.copy(alpha = ghost.alpha.value * soakAlpha)),
                            )
                        }

                        if (rolling && patchOn) {
                            val x = patchRight.value
                            drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(x - 3.5.dp.toPx(), top), Size(9.dp.toPx(), bottom - top), CornerRadius(5.dp.toPx()))
                            drawRoundRect(
                                Brush.horizontalGradient(listOf(Color(0xFFD9D2C6), Color.White, Color(0xFFCFC7BA))),
                                Offset(x - 4.5.dp.toPx(), top - 1.dp.toPx()), Size(9.dp.toPx(), bottom - top + 2.dp.toPx()), CornerRadius(5.dp.toPx()),
                            )
                        }
                    }
                }

                // Always composed, like the "Add a lift" field: a tap lands on the input and focuses it inside the gesture.
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Color.Transparent, backgroundColor = Color.Transparent,
                    ),
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = ::onInput,
                        textStyle = nameStyle(fit).copy(color = Color.Transparent),
                        singleLine = true,
                        cursorBrush = SolidColor(SheetPaper.ink),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { requestCommit() }),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) { inner() }
                        },
                        modifier = Modifier
                            // At rest the field is only as wide as the printed name: the band beside it stays the sheet's.
                            .then(if (editing || soaking) Modifier.fillMaxWidth() else Modifier.width(restHitWidth))
                            .fillMaxHeight()
                            .focusRequester(focus)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    hasBeenFocused = true
                                    if (phase.value == SplitNamePhase.Rest) {
                                        // Focus is the entry into edit mode: select the whole name, ready to write over it.
                                        shown = name
                                        field = TextFieldValue(name, TextRange(0, name.length))
                                        phase.value = SplitNamePhase.Editing
                                        // The tap that focused the field also places a caret when it lifts; select the whole name after it.
                                        scope.launch {
                                            delay(dur(300).toLong())
                                            val f = field
                                            if (phase.value == SplitNamePhase.Editing && f.text == name && !(f.selection.min == 0 && f.selection.max == name.length)) {
                                                field = f.copy(selection = TextRange(0, name.length))
                                            }
                                        }
                                    }
                                } else if (hasBeenFocused) {
                                    requestCommit()
                                }
                            }
                            .semantics { contentDescription = if (editing) "Name for this split" else "Rename $name" },
                    )
                }
            }
        }
        if (isUnnamed && phase.value == SplitNamePhase.Rest) {
            Spacer(Modifier.height(8.dp))
            NameItChip(onClick = { focus.requestFocus() })
        }
    }
}

@Composable
private fun NameItChip(onClick: () -> Unit) {
    Box(
        Modifier
            .height(24.dp)
            .background(SheetPaper.chip, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { role = Role.Button; contentDescription = "Name this split" }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "NAME IT", fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp, color = SheetPaper.ink, maxLines = 1,
        )
    }
}

