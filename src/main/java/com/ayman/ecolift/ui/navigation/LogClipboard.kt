package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.BodyPartRegionMapper
import com.ayman.ecolift.data.suggestMuscleGroupFromKeywords
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.MarkerFontFamily
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.ui.viewmodel.ClipboardAnswer
import com.ayman.ecolift.ui.viewmodel.ClipboardCheck
import com.ayman.ecolift.ui.viewmodel.ClipboardLiftUi
import com.ayman.ecolift.ui.viewmodel.ClipboardPick
import com.ayman.ecolift.ui.viewmodel.ClipboardPickRequest
import com.ayman.ecolift.ui.viewmodel.ClipboardProgramUi
import com.ayman.ecolift.web.supabase.simpleNameKey
import com.ayman.ecolift.web.util.keyboardOverlap

import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

// ---------------------------------------------------------------------------------------------
// The rules, kept pure so they are tested without a screen.
// ---------------------------------------------------------------------------------------------

internal const val CLIP_RECENT_COUNT = 12
internal val ClipRestHeight = 62.dp

/** Index tabs: Recent, then the body areas. Core only shows when a lift is tagged Core. */
internal enum class ClipTab(val label: String, val groups: List<String>) {
    Recent("Recent", emptyList()),
    Chest("Chest", listOf("CHEST")),
    Back("Back", listOf("BACK")),
    Shoulders("Shoulders", listOf("SHOULDERS")),
    Arms("Arms", listOf("BICEPS", "TRICEPS", "FOREARMS")),
    Legs("Legs", listOf("QUADS", "HAMSTRINGS", "GLUTES", "CALVES")),
    Core("Core", listOf("CORE")),
}

internal fun clipboardTabs(lifts: List<ClipboardLiftUi>): List<ClipTab> =
    ClipTab.entries.filter { it != ClipTab.Core || lifts.any { lift -> lift.primaryGroup == "CORE" } }

internal data class ClipSection(val group: String?, val lifts: List<ClipboardLiftUi>)

/** Recent: the lifts with the latest checked sets. A body area: one section per muscle group. */
internal fun clipboardPage(tab: ClipTab, lifts: List<ClipboardLiftUi>): List<ClipSection> =
    if (tab == ClipTab.Recent) {
        listOf(ClipSection(null, lifts.filter { it.lastCheckedDate != null }.take(CLIP_RECENT_COUNT)))
    } else {
        tab.groups.mapNotNull { group ->
            lifts.filter { it.primaryGroup == group }.takeIf { it.isNotEmpty() }?.let { ClipSection(group, it) }
        }
    }

private val ClipMonths = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

/** "SEP 21" for a line; null for a lift never logged, which shows no date at all. */
internal fun clipboardDate(iso: String?): String? =
    iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { "${ClipMonths[it.monthNumber - 1]} ${it.dayOfMonth}" }

/** "Sep 21" inside a sentence. */
internal fun clipboardNoteDate(iso: String?): String? =
    clipboardDate(iso)?.let { it.take(1) + it.drop(1).lowercase() }

internal fun clipboardAddLabel(count: Int): String = if (count == 1) "lift" else "lifts"

/** A typed name gets a written-in line unless it already is a lift's name. */
internal fun clipboardShowsWrittenLine(query: String, lifts: List<ClipboardLiftUi>): Boolean {
    val typed = query.trim()
    if (typed.isEmpty()) return false
    val key = simpleNameKey(typed)
    return lifts.none { simpleNameKey(it.name) == key }
}

/** The muscle the app would give a new lift from its name, the same keyword guess it uses today. */
internal fun clipboardGuess(name: String): String? = suggestMuscleGroupFromKeywords(name)?.group

/** One box per lift in today's workout; a box fills once every set of that lift is checked. */
internal fun clipboardStubBoxes(exercises: List<ExerciseLog>): List<Boolean> =
    exercises.map { exercise -> exercise.sets.isNotEmpty() && exercise.sets.all { it.isCompleted } }

/** The due group sits at the front of the pocket; the rest follow in due order. */
internal fun clipboardPocketOrder(programs: List<ClipboardProgramUi>): List<ClipboardProgramUi> =
    programs.sortedWith(compareByDescending<ClipboardProgramUi> { it.isExpected }.thenBy { it.position })

/**
 * The clipboard's stamp (spec 2026-09-28): the day's proposal until a set is checked; once the day
 * belongs to a group ([attributedSlotId]) the stub shows that group instead, and a checked day with
 * no group has no stamp. While the Up next ticket is on screen ([ticketShown]) it already names the
 * split, so the stamp steps aside.
 */
internal fun clipboardStamp(
    programs: List<ClipboardProgramUi>,
    chosenSlotId: Long?,
    attributedSlotId: Long?,
    dayHasCheckedSet: Boolean,
    ticketShown: Boolean = false,
): ClipboardProgramUi? =
    if (ticketShown || attributedSlotId != null || dayHasCheckedSet) null
    else emptyDayProgram(programs, chosenSlotId)?.takeIf { it.lifts.isNotEmpty() }

/** The empty day's proposal: the group placed from the picker for [chosenSlotId], else the due group. */
internal fun emptyDayProgram(programs: List<ClipboardProgramUi>, chosenSlotId: Long?): ClipboardProgramUi? =
    programs.firstOrNull { it.slotId == chosenSlotId } ?: programs.firstOrNull { it.isExpected }

/** The requests Add sends: every ticked line, with its note and answer when it had one. */
internal fun clipboardRequests(
    ticks: List<ClipboardPick>,
    checks: Map<String, ClipboardCheck>,
    answers: Map<String, ClipboardAnswer>,
): List<ClipboardPickRequest> = ticks.map { pick ->
    ClipboardPickRequest(pick, checks[pick.key] as? ClipboardCheck.LookAlike, answers[pick.key])
}

// ---------------------------------------------------------------------------------------------
// Materials.
// ---------------------------------------------------------------------------------------------

private val ClipInk = Color(0xFF1C1816)
private val ClipPaper = Color(0xFFEFE7DA)
private val ClipCard = Color(0xFFFBF8F2)
private val ClipRule = Color(0xFFDACDB8)
private val ClipMargin = Color(0x80CE5C5C)
private val ClipStampInk = Color(0xFFB2461F)
private val ClipHilite = Color(0x94E9B44C)
private val ClipHandInk = Color(0xFF27479A)
private val ClipNote = Color(0xFFF5E8B4)
private val ClipKraft = Color(0xFFCDBB9A)
private val BoardTop = Color(0xFF44372E)
private val BoardBottom = Color(0xFF35291F)
private val BoardHole = Color(0xFF3B2F27)
private val PocketTop = Color(0xFF2F251E)
private val PocketBottom = Color(0xFF281F19)

private fun tabColor(tab: ClipTab): Color = when (tab) {
    ClipTab.Recent -> ClipKraft
    ClipTab.Chest -> EspressoPalette.chest
    ClipTab.Back -> EspressoPalette.back
    ClipTab.Shoulders -> EspressoPalette.shoulders
    ClipTab.Arms -> EspressoPalette.arms
    ClipTab.Legs -> EspressoPalette.legs
    ClipTab.Core -> EspressoPalette.core
}

/** The muscle colours, darkened so they hold up as ink on cream paper. */
private fun groupInk(group: String?): Color = when (BodyPartRegionMapper.mapGroupToRegion(group ?: "")) {
    BodyPartRegion.CHEST -> Color(0xFFB2461F)
    BodyPartRegion.BACK -> Color(0xFF3F5FC2)
    BodyPartRegion.SHOULDERS -> Color(0xFF6A4BB5)
    BodyPartRegion.ARMS -> Color(0xFFA8386A)
    BodyPartRegion.LEGS -> Color(0xFF3D7026)
    BodyPartRegion.CORE -> Color(0xFF8A6210)
    BodyPartRegion.UNKNOWN -> ClipInk.copy(alpha = 0.5f)
}

private val clipNameStyle by lazy {
    TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = ClipInk)
}
private val clipHandStyle by lazy {
    TextStyle(fontFamily = MarkerFontFamily, fontSize = 22.sp, lineHeight = 22.sp, color = ClipHandInk)
}
private val clipLabelStyle by lazy {
    TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, lineHeight = 10.sp, letterSpacing = 1.2.sp)
}
private val clipDateStyle by lazy {
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 12.sp, color = ClipInk.copy(alpha = 0.5f))
}
private val clipNumberStyle by lazy {
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 12.sp, color = ClipInk.copy(alpha = 0.45f))
}
private val clipFieldStyle by lazy {
    TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp, color = ClipInk)
}
private val clipPlaceholderStyle by lazy { clipFieldStyle.copy(fontSize = 15.5.sp, color = ClipInk.copy(alpha = 0.5f)) }
private val clipLinkStyle by lazy {
    TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, color = ClipInk.copy(alpha = 0.6f))
}
private val clipTabStyle by lazy {
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, lineHeight = 12.sp, color = EspressoPalette.onColor)
}
private val clipStampStyle by lazy {
    TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp, letterSpacing = 1.5.sp)
}
private val clipStampBigStyle by lazy { clipStampStyle.copy(fontSize = 12.sp, lineHeight = 13.sp, letterSpacing = 1.8.sp) }
private val clipEyebrowStyle by lazy {
    TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, lineHeight = 12.sp, letterSpacing = 2.sp, color = ClipInk.copy(alpha = 0.55f))
}
private val clipBandStyle by lazy {
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = 16.sp, color = EspressoPalette.onColor)
}
private val clipBandSmallStyle by lazy { clipBandStyle.copy(fontSize = 11.sp, lineHeight = 12.sp) }
private val clipBandMetaStyle by lazy {
    TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, lineHeight = 10.sp, letterSpacing = 1.5.sp, color = EspressoPalette.onColor)
}
private val clipNoteStyle by lazy {
    TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 19.sp, color = ClipInk)
}

/** A fixed speckle tiled over the paper, and faint streaks tiled over the hardboard. */
private val clipGrain: ImageBitmap by lazy {
    val side = 96
    val image = ImageBitmap(side, side)
    val canvas = GraphicsCanvas(image)
    val paint = Paint()
    val random = Random(2209)
    repeat(1500) {
        paint.color = Color(0.33f, 0.26f, 0.19f, 0.04f + random.nextFloat() * 0.12f)
        val x = random.nextInt(side).toFloat()
        val y = random.nextInt(side).toFloat()
        canvas.drawRect(x, y, x + 1f, y + 1f, paint)
    }
    image
}

private val boardFibre: ImageBitmap by lazy {
    val side = 128
    val image = ImageBitmap(side, side)
    val canvas = GraphicsCanvas(image)
    val paint = Paint()
    val random = Random(4407)
    repeat(260) {
        val light = random.nextBoolean()
        paint.color = if (light) Color(1f, 0.93f, 0.84f, 0.025f + random.nextFloat() * 0.035f) else Color(0f, 0f, 0f, 0.04f + random.nextFloat() * 0.05f)
        val x = random.nextInt(side).toFloat()
        val y = random.nextInt(side).toFloat()
        val length = 6f + random.nextFloat() * 30f
        canvas.drawRect(x, y, x + length, y + 1f, paint)
    }
    image
}

internal fun DrawScope.paperFill(color: Color = ClipPaper) {
    drawRect(color)
    drawRect(ShaderBrush(ImageShader(clipGrain, TileMode.Repeated, TileMode.Repeated)))
}

private fun DrawScope.hardboardFill(corner: Float) {
    drawRoundRect(Brush.verticalGradient(listOf(BoardTop, BoardBottom)), cornerRadius = CornerRadius(corner))
    drawRoundRect(ShaderBrush(ImageShader(boardFibre, TileMode.Repeated, TileMode.Repeated)), cornerRadius = CornerRadius(corner))
    drawLine(Color.White.copy(alpha = 0.08f), Offset(corner, 0.5f), Offset(size.width - corner, 0.5f), 1f)
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(corner, size.height - 0.5f), Offset(size.width - corner, size.height - 0.5f), 1f)
}

// ---------------------------------------------------------------------------------------------
// The clipboard.
// ---------------------------------------------------------------------------------------------

private sealed interface ClipMode {
    data object Rest : ClipMode
    data object Open : ClipMode
    data class Program(val slotId: Long) : ClipMode
}

private data class TornLine(
    val key: String,
    val name: String,
    val group: String?,
    val handwritten: Boolean,
    val bounds: Rect,
)

@Stable
private class ClipboardState {
    var mode: ClipMode by mutableStateOf(ClipMode.Rest)
    var programsOpen: Boolean by mutableStateOf(false)
    var tab: ClipTab by mutableStateOf(ClipTab.Recent)
    val ticks = mutableStateListOf<ClipboardPick>()
    val checks = mutableStateMapOf<String, ClipboardCheck>()
    val answers = mutableStateMapOf<String, ClipboardAnswer>()
    var checking: Int by mutableStateOf(0)
    var noteKey: String? by mutableStateOf(null)
    val skipped = mutableStateMapOf<Long, Set<Long>>()
    var torn: List<TornLine> by mutableStateOf(emptyList())
    var clippedSlotId: Long? by mutableStateOf(null)
    /** While a program clips on, its card keeps saying CLIP ON and rides up into the stub's slot. */
    var clipping: Long? by mutableStateOf(null)

    fun isTorn(key: String): Boolean = torn.any { it.key == key }

    fun isTicked(key: String): Boolean = ticks.any { it.key == key }

    /** A lift reads as ticked when it is itself ticked, or a written line was answered "Same" for it. */
    fun liftTicked(exerciseId: Long): Boolean =
        isTicked("id:$exerciseId") || ticks.any { pick ->
            answers[pick.key] == ClipboardAnswer.Same &&
                (checks[pick.key] as? ClipboardCheck.LookAlike)?.exerciseId == exerciseId
        }

    fun forget(key: String) {
        ticks.removeAll { it.key == key }
        checks.remove(key)
        answers.remove(key)
        if (noteKey == key) noteKey = null
    }

    fun clearTicks() {
        ticks.clear()
        checks.clear()
        answers.clear()
        noteKey = null
    }
}

@Composable
internal fun LogClipboard(
    currentDate: String,
    lifts: List<ClipboardLiftUi>,
    programs: List<ClipboardProgramUi>,
    loadedSlotId: Long?,
    todayExercises: List<ExerciseLog>,
    query: String,
    results: List<ExerciseSearchResult>,
    sheetHeight: Dp,
    onQueryChange: (String) -> Unit,
    onCheck: suspend (ClipboardPick) -> ClipboardCheck,
    onAdd: (List<ClipboardPickRequest>) -> Unit,
    onClipOn: (slotId: Long, skipped: Set<Long>) -> Unit,
    onUnclip: () -> Unit,
    onOpenChanged: (Boolean) -> Unit,
    stampedProgram: ClipboardProgramUi? = null,
    pickMode: Boolean = false,
    onPickLift: (Long) -> Unit = {},
    onPickDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state = remember(currentDate) { ClipboardState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    val haptic = rememberLightHaptic()
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val height = remember { Animatable(ClipRestHeight.value) }
    val openClock = remember { Animatable(1f) }
    val tearClock = remember { Animatable(1f) }
    val clipSnap = remember { Animatable(0f) }
    val cardRise = remember { Animatable(1f) }
    val lineBounds = remember { HashMap<String, Rect>() }
    var cardHeightPx by remember { mutableStateOf(0) }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var boardTopPx by remember { mutableStateOf(0f) }
    var canvasHeightPx by remember { mutableStateOf(0f) }
    val keyboard = keyboardOverlap()

    val pocket = remember(programs) { clipboardPocketOrder(programs) }
    val loadedProgram = programs.firstOrNull { it.slotId == loadedSlotId }
    // Null means no stamp: a checked day with no group, or no proposal (spec 2026-09-28).
    val stamped = stampedProgram
    val typing = focused || query.isNotBlank()
    val isOpen = state.mode != ClipMode.Rest
    // While the on-screen keyboard is up the sheet ends just above it, so the tear strip and the
    // look-alike note are never under it; that sheet may be shorter than the host's 360 dp floor,
    // down to the search line, a couple of lines and the strip.
    val aboveKeyboard = with(density) { (canvasHeightPx - boardTopPx).toDp() } - keyboard - 8.dp
    val sheetOpenHeight = (if (keyboard > 0.dp && canvasHeightPx > 0f) minOf(sheetHeight, aboveKeyboard) else sheetHeight)
        .coerceAtLeast(ClipRestHeight + 120.dp)

    LaunchedEffect(isOpen) { onOpenChanged(isOpen) }
    // The keyboard coming up or going down resizes the open sheet: the paper follows, once the
    // open or close already under way has finished, and never pulls a closing board back open.
    LaunchedEffect(sheetOpenHeight) {
        snapshotFlow { height.isRunning }.first { !it }
        if (state.mode != ClipMode.Rest && !closing && height.value != sheetOpenHeight.value) {
            height.animateTo(sheetOpenHeight.value, tween(220, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(currentDate) {
        height.snapTo(ClipRestHeight.value)
        onOpenChanged(false)
    }
    // The stub arrives under the clip: it snaps shut with one tick.
    LaunchedEffect(loadedSlotId) {
        if (loadedSlotId != null && loadedSlotId == state.clippedSlotId) {
            clipSnap.snapTo(0f)
            clipSnap.animateTo(1f, tween(620, easing = LinearEasing))
            haptic()
            state.clippedSlotId = null
        }
    }

    fun open() {
        closing = false
        if (state.mode == ClipMode.Rest) {
            state.programsOpen = false
            state.mode = ClipMode.Open
        }
        scope.launch {
            launch {
                openClock.snapTo(0f)
                openClock.animateTo(1f, tween(900, easing = LinearEasing))
            }
            height.animateTo(sheetOpenHeight.value, tween(540, easing = FastOutSlowInEasing))
        }
    }

    fun close(afterAdd: Boolean = false) {
        closing = true
        focusManager.clearFocus()
        if (query.isNotEmpty() && !afterAdd) onQueryChange("")
        scope.launch {
            height.animateTo(ClipRestHeight.value, tween(if (afterAdd) 440 else 320, easing = FastOutSlowInEasing))
            state.mode = ClipMode.Rest
            state.clipping = null
        }
    }

    fun openProgram(slotId: Long) {
        // The card is chosen at once; from rest it waits for the paper to feed out, then rises.
        val fromRest = state.mode == ClipMode.Rest
        if (fromRest) open()
        state.programsOpen = true
        state.mode = ClipMode.Program(slotId)
        scope.launch {
            cardRise.snapTo(0f)
            if (fromRest) delay(420)
            cardRise.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
    }

    // A swap reuses this exact search/catalog, but picking an existing line writes no sets.
    if (pickMode) {
        LaunchedEffect(Unit) { state.ticks.clear(); open() }
    }

    fun toggle(pick: ClipboardPick) {
        if (pickMode) {
            if (pick is ClipboardPick.Existing) {
                onPickLift(pick.exerciseId)
                onPickDismiss()
                close()
            }
            return
        }
        if (state.isTicked(pick.key)) {
            state.forget(pick.key)
            return
        }
        state.ticks += pick
        haptic()
        state.checking++
        scope.launch {
            val check = runCatching { onCheck(pick) }.getOrDefault(ClipboardCheck.Clear)
            state.checking--
            if (!state.isTicked(pick.key)) return@launch
            when (check) {
                is ClipboardCheck.Exact -> {
                    // A remembered spelling: the written line is that lift.
                    val index = state.ticks.indexOfFirst { it.key == pick.key }
                    val existing = ClipboardPick.Existing(check.exerciseId, check.name)
                    if (index >= 0) {
                        if (state.isTicked(existing.key)) state.ticks.removeAt(index) else state.ticks[index] = existing
                    }
                }
                is ClipboardCheck.LookAlike -> {
                    state.checks[pick.key] = check
                    if (state.answers[pick.key] == null) state.noteKey = pick.key
                }
                ClipboardCheck.Clear -> Unit
            }
        }
    }

    fun answer(key: String, answer: ClipboardAnswer) {
        state.answers[key] = answer
        if (state.noteKey == key) state.noteKey = null
        haptic()
    }

    fun tear(keys: List<String>, lines: Map<String, TornLine>) {
        state.torn = keys.mapNotNull { lines[it] }
        scope.launch {
            tearClock.snapTo(0f)
            tearClock.animateTo(1f, tween(760, easing = LinearEasing))
            state.torn = emptyList()
        }
    }

    fun add() {
        val requests = clipboardRequests(state.ticks, state.checks, state.answers)
        if (requests.isEmpty() || state.noteKey != null || state.checking > 0) return
        val torn = state.ticks.mapNotNull { pick ->
            lineBounds[pick.key]?.let { bounds ->
                val lift = (pick as? ClipboardPick.Existing)?.let { p -> lifts.firstOrNull { it.exerciseId == p.exerciseId } }
                TornLine(pick.key, pick.label, lift?.primaryGroup ?: clipboardGuess(pick.label), pick is ClipboardPick.Typed, bounds)
            }
        }
        onAdd(requests)
        haptic()
        tear(torn.map { it.key }, torn.associateBy { it.key })
        state.clearTicks()
        close(afterAdd = true)
    }

    fun clipOn(program: ClipboardProgramUi) {
        val skipped = state.skipped[program.slotId].orEmpty()
        val torn = program.lifts.filterNot { it.exerciseId in skipped }.mapNotNull { lift ->
            val key = "p:${program.slotId}:${lift.exerciseId}"
            lineBounds[key]?.let { TornLine(key, lift.name, lift.primaryGroup, false, it) }
        }
        state.clippedSlotId = program.slotId
        state.clipping = program.slotId
        onClipOn(program.slotId, skipped)
        state.skipped.remove(program.slotId)
        tear(torn.map { it.key }, torn.associateBy { it.key })
        close(afterAdd = true)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.value.dp)
            .onGloballyPositioned { coordinates ->
                root = coordinates
                // Read only while open: the list is held still then, so scrolling at rest never
                // recomposes the board.
                if (state.mode != ClipMode.Rest) {
                    val canvas = coordinates.findRootCoordinates()
                    boardTopPx = canvas.localPositionOf(coordinates, Offset.Zero).y
                    canvasHeightPx = canvas.size.height.toFloat()
                }
            }
            .zIndex(2f),
    ) {
        // The board, clipped so the paper feeds out as it grows.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(10.dp, RoundedCornerShape(14.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .drawBehind { hardboardFill(14.dp.toPx()) }
                .clip(RoundedCornerShape(14.dp)),
        ) {
            val renderOpen = isOpen || height.value > ClipRestHeight.value + 0.5f
            if (!renderOpen) {
                if (loadedProgram != null) {
                    ClipStub(
                        program = loadedProgram,
                        boxes = clipboardStubBoxes(todayExercises),
                        onOpenCard = { openProgram(loadedProgram.slotId) },
                        onSearch = { open() },
                    )
                } else {
                    ClipPad(stamped = stamped, onOpen = { open() }, onStamp = { program -> openProgram(program.slotId) })
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .height(sheetOpenHeight)
                        .graphicsLayer {
                            if (state.clipping != null && cardHeightPx > 0) {
                                val span = sheetOpenHeight.value - ClipRestHeight.value
                                val closed = 1f - ((height.value - ClipRestHeight.value) / span).coerceIn(0f, 1f)
                                val cardTop = size.height - 34.dp.toPx() - cardHeightPx
                                translationY = -(cardTop - 10.dp.toPx()) * reopenEaseInOutCubic(closed)
                            }
                        },
                ) {
                    ClipSheet(
                        state = state,
                        lifts = lifts,
                        results = results,
                        query = query,
                        typing = typing,
                        pocket = pocket,
                        loadedSlotId = loadedSlotId,
                        openClock = { openClock.value },
                        fieldFocus = fieldFocus,
                        onFocusChanged = { focused = it },
                        onQueryChange = onQueryChange,
                        onDone = { if (pickMode) onPickDismiss(); close() },
                        pickMode = pickMode,
                        onToggle = ::toggle,
                        onAnswer = ::answer,
                        onAdd = ::add,
                        onOpenProgram = ::openProgram,
                        registerLine = { key, coordinates ->
                            root?.let { rootCoordinates ->
                                if (coordinates.isAttached && rootCoordinates.isAttached) {
                                    lineBounds[key] = rootCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
                                }
                            }
                        },
                    )
                    val mode = state.mode
                    if (mode is ClipMode.Program) {
                        val program = programs.firstOrNull { it.slotId == mode.slotId }
                        if (program != null) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ClipInk.copy(alpha = 0.24f * cardRise.value))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        state.mode = ClipMode.Open
                                    }
                                    .semantics { contentDescription = "Put the program card back" },
                            )
                            ClipProgramCard(
                                program = program,
                                isLoaded = program.slotId == loadedSlotId && state.clipping != program.slotId,
                                tornKeys = state.torn.mapTo(HashSet()) { it.key },
                                skipped = state.skipped[program.slotId].orEmpty(),
                                rise = { cardRise.value },
                                onToggleLift = { exerciseId ->
                                    val current = state.skipped[program.slotId].orEmpty()
                                    state.skipped[program.slotId] = if (exerciseId in current) current - exerciseId else current + exerciseId
                                    haptic()
                                },
                                onBack = { state.mode = ClipMode.Open },
                                onClipOn = { clipOn(program) },
                                onUnclip = {
                                    onUnclip()
                                    close()
                                },
                                registerLine = { key, coordinates ->
                                    root?.let { rootCoordinates ->
                                        if (coordinates.isAttached && rootCoordinates.isAttached) {
                                            lineBounds[key] = rootCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 34.dp)
                                    .onSizeChanged { cardHeightPx = it.height },
                            )
                        }
                    }
                }
            }
        }

        ClipSteel(
            snap = { clipSnap.value },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-15).dp),
        )

        // Torn lines fall out of the board toward the workout below.
        state.torn.forEachIndexed { index, line ->
            TornStrip(line = line, index = index, clock = { tearClock.value })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// At rest: the pad, or the stub of a clipped-on program.
// ---------------------------------------------------------------------------------------------

@Composable
private fun ClipPad(
    stamped: ClipboardProgramUi?,
    onOpen: () -> Unit,
    onStamp: (ClipboardProgramUi) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(3.dp))
            .drawBehind {
                paperFill()
                val y = size.height - 9.dp.toPx()
                drawLine(ClipRule, Offset(41.dp.toPx(), y), Offset(size.width - 118.dp.toPx(), y), 1.dp.toPx())
            }
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Add a lift" }
            .padding(start = 14.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = ClipInk.copy(alpha = 0.55f), modifier = Modifier.size(17.dp))
        Text("Add a lift", style = clipPlaceholderStyle, maxLines = 1, modifier = Modifier.weight(1f))
        if (stamped != null) {
            ClipStamp(
                text = stamped.name.uppercase(),
                chevron = true,
                description = "Open ${stamped.name} program card",
                onClick = { onStamp(stamped) },
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.ClipStub(
    program: ClipboardProgramUi,
    boxes: List<Boolean>,
    onOpenCard: () -> Unit,
    onSearch: () -> Unit,
) {
    val color = splitAccentColor(program.colorIndex)
    val done = boxes.count { it }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 8.dp, top = 10.dp, end = 58.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .background(ClipCard)
            .clickable(role = Role.Button, onClick = onOpenCard)
            .semantics { contentDescription = "${program.name} clipped on, $done of ${boxes.size} lifts done. Open program card" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(34.dp).background(color).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(program.name.uppercase(), style = clipBandStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 10.dp)) {
                boxes.take(10).forEach { filled ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .then(if (filled) Modifier.background(EspressoPalette.onColor, RoundedCornerShape(1.5.dp)) else Modifier)
                            .drawBehind {
                                if (!filled) {
                                    drawRoundRect(EspressoPalette.onColor.copy(alpha = 0.6f), style = Stroke(1.4.dp.toPx()), cornerRadius = CornerRadius(1.5.dp.toPx()))
                                }
                            },
                    )
                }
            }
            Text("$done/${boxes.size}", style = clipBandStyle.copy(fontSize = 12.sp))
        }
        Perforation(hole = BoardHole)
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 10.dp, end = 8.dp)
            .size(width = 42.dp, height = 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .drawBehind { paperFill() }
            .clickable(role = Role.Button, onClick = onSearch)
            .semantics { contentDescription = "Add a lift" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = ClipInk.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Open: the sheet.
// ---------------------------------------------------------------------------------------------

@Composable
private fun BoxScope.ClipSheet(
    state: ClipboardState,
    lifts: List<ClipboardLiftUi>,
    results: List<ExerciseSearchResult>,
    query: String,
    typing: Boolean,
    pocket: List<ClipboardProgramUi>,
    loadedSlotId: Long?,
    openClock: () -> Float,
    fieldFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
    onToggle: (ClipboardPick) -> Unit,
    onAnswer: (String, ClipboardAnswer) -> Unit,
    onAdd: () -> Unit,
    onOpenProgram: (Long) -> Unit,
    registerLine: (String, LayoutCoordinates) -> Unit,
    pickMode: Boolean = false,
) {
    val showPocket = !pickMode && state.programsOpen && !typing && pocket.isNotEmpty()
    val step = if (pocket.size <= 1) 15f else min(15f, 90f / (pocket.size - 1))
    val pocketHeight = if (showPocket) (36f + step * (pocket.size - 1) + 30f).dp else 0.dp
    val liftsById = remember(lifts) { lifts.associateBy { it.exerciseId } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp + if (showPocket) pocketHeight + 6.dp else 0.dp)
            .clip(RoundedCornerShape(3.dp))
            .drawBehind { paperFill() },
    ) {
        ClipSearchLine(
            query = query,
            typing = typing,
            fieldFocus = fieldFocus,
            onFocusChanged = onFocusChanged,
            onQueryChange = onQueryChange,
            onDone = onDone,
            programsOpen = state.programsOpen,
            onTogglePrograms = if (!typing && pocket.isNotEmpty()) {
                { state.programsOpen = !state.programsOpen }
            } else null,
        )
        if (!typing) {
            ClipTabs(selected = state.tab, tabs = clipboardTabs(lifts), openClock = openClock, onSelect = { state.tab = it })
        }
        val ruleColor = if (typing) Color.Transparent else tabColor(state.tab)
        val scroll = rememberScrollState()
        LaunchedEffect(state.tab, typing) { scroll.scrollTo(0) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(ruleColor, size = Size(size.width, 3.dp.toPx()))
                        drawLine(ClipMargin, Offset(33.dp.toPx(), 0f), Offset(33.dp.toPx(), size.height), 1.dp.toPx())
                    }
                    .padding(top = 3.dp)
                    .verticalScroll(scroll)
                    .padding(bottom = if (state.ticks.isNotEmpty()) 64.dp else 12.dp),
            ) {
                var index = 0
                fun lineIndex() = index++
                if (typing) {
                    val shown = if (query.isBlank()) lifts.filter { it.lastCheckedDate != null }.take(6)
                    else results.mapNotNull { result -> result.exerciseId?.let { liftsById[it] } }
                    shown.forEach { lift ->
                        ClipLiftLine(state, lift, lineIndex(), openClock, onToggle, onAnswer, registerLine, pickMode)
                    }
                    if (!pickMode && clipboardShowsWrittenLine(query, lifts)) {
                        val typed = ClipboardPick.Typed(query.trim())
                        ClipWrittenLine(
                            pick = typed,
                            hidden = state.isTorn(typed.key),
                            ticked = state.isTicked(typed.key),
                            struck = state.answers[typed.key] == ClipboardAnswer.Same,
                            onToggle = { onToggle(typed) },
                            registerLine = registerLine,
                        )
                        ClipNoteIfOpen(state, typed.key, onAnswer)
                    }
                } else {
                    clipboardPage(state.tab, lifts).forEach { section ->
                        if (section.group != null) {
                            ClipGroupHeader(group = section.group, count = section.lifts.size)
                        }
                        section.lifts.forEach { lift ->
                            ClipLiftLine(state, lift, lineIndex(), openClock, onToggle, onAnswer, registerLine, pickMode)
                        }
                    }
                }
            }
            // A note whose line is on another page docks above the strip, so it can't be missed.
            val noteKey = state.noteKey
            val noteVisibleInline = noteKey != null && (
                (typing && noteKey.startsWith("new:")) ||
                    (noteKey.startsWith("id:") && isOnPage(noteKey, typing, query, results, lifts, state.tab))
                )
            if (noteKey != null && !noteVisibleInline) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp)) {
                    ClipNote(state, noteKey, onAnswer)
                }
            }
            if (!pickMode) ClipStrip(state = state, onAdd = onAdd, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showPocket) {
        ClipPocket(
            pocket = pocket,
            step = step,
            loadedSlotId = loadedSlotId,
            hiddenSlotId = (state.mode as? ClipMode.Program)?.slotId,
            openClock = openClock,
            onOpenProgram = onOpenProgram,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(pocketHeight),
        )
    }
}

private fun isOnPage(
    key: String,
    typing: Boolean,
    query: String,
    results: List<ExerciseSearchResult>,
    lifts: List<ClipboardLiftUi>,
    tab: ClipTab,
): Boolean {
    val id = key.removePrefix("id:").toLongOrNull() ?: return false
    return if (typing) {
        if (query.isBlank()) lifts.filter { it.lastCheckedDate != null }.take(6).any { it.exerciseId == id }
        else results.any { it.exerciseId == id }
    } else {
        clipboardPage(tab, lifts).any { section -> section.lifts.any { it.exerciseId == id } }
    }
}

@Composable
private fun ClipSearchLine(
    query: String,
    typing: Boolean,
    fieldFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
    programsOpen: Boolean,
    onTogglePrograms: (() -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .drawBehind { drawLine(ClipRule, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = ClipInk.copy(alpha = 0.55f), modifier = Modifier.size(17.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = clipFieldStyle,
            cursorBrush = SolidColor(ClipInk),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            // Dismissing the keyboard never adds anything; lines are ticked and added explicitly.
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(fieldFocus)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .semantics { contentDescription = "Type a lift" },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty() && !typing) Text("Add a lift", style = clipPlaceholderStyle, maxLines = 1)
                    inner()
                }
            },
        )
        if (onTogglePrograms != null) {
            Text(
                if (programsOpen) "Hide splits" else "Splits",
                style = clipLinkStyle,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onTogglePrograms)
                    .padding(vertical = 6.dp)
                    .semantics { contentDescription = if (programsOpen) "Hide splits" else "Show splits" },
            )
        }
        Text(
            "Done",
            style = clipLinkStyle,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onDone)
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                .semantics { contentDescription = "Done" },
        )
    }
}

private val ClipTabShape = GenericShape { size, _ ->
    moveTo(size.width * 0.08f, 0f)
    lineTo(size.width * 0.92f, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun ClipTabs(selected: ClipTab, tabs: List<ClipTab>, openClock: () -> Float, onSelect: (ClipTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val on = tab == selected
            Box(
                modifier = Modifier
                    .zIndex(if (on) 2f else 0f)
                    .height(if (on) 31.dp else 26.dp)
                    .graphicsLayer {
                        val t = reopenSeg(openClock() * 900f, 180f + index * 40f, 520f + index * 40f)
                        translationY = (1f - reopenBack(t, 1.4f)) * 14.dp.toPx()
                    }
                    .clip(ClipTabShape)
                    .background(if (on) tabColor(tab) else lerpDark(tabColor(tab)))
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics {
                        this.selected = on
                        contentDescription = tab.label
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tab.label, style = clipTabStyle, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun lerpDark(color: Color): Color = Color(color.red * 0.86f, color.green * 0.86f, color.blue * 0.86f, 1f)

@Composable
private fun ClipGroupHeader(group: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(ClipRule, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
            .padding(start = 42.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(group, style = clipLabelStyle.copy(fontSize = 9.sp, letterSpacing = 1.8.sp, color = groupInk(group)))
        Text("$count", style = clipNumberStyle)
    }
}

@Composable
private fun ClipLiftLine(
    state: ClipboardState,
    lift: ClipboardLiftUi,
    index: Int,
    openClock: () -> Float,
    onToggle: (ClipboardPick) -> Unit,
    onAnswer: (String, ClipboardAnswer) -> Unit,
    registerLine: (String, LayoutCoordinates) -> Unit,
    pickMode: Boolean = false,
) {
    val pick = ClipboardPick.Existing(lift.exerciseId, lift.name)
    val date = clipboardDate(lift.lastCheckedDate)
    ClipLine(
        name = lift.name,
        group = lift.primaryGroup,
        date = date,
        ticked = state.liftTicked(lift.exerciseId),
        hidden = state.isTorn(pick.key),
        description = buildString {
            if (pickMode) append("Pick ")
            append(lift.name)
            lift.primaryGroup?.let { append(", ").append(it.lowercase()) }
            if (date != null) append(", last done ").append(clipboardNoteDate(lift.lastCheckedDate))
        },
        onToggle = { onToggle(pick) },
        pickMode = pickMode,
        modifier = Modifier
            .drawWithContent {
                val t = clipLineReveal(openClock() * 900f, index)
                clipRect(right = size.width * t) { this@drawWithContent.drawContent() }
            }
            .onGloballyPositioned { registerLine(pick.key, it) },
    )
    ClipNoteIfOpen(state, pick.key, onAnswer)
}

/**
 * How far line [index] is written in, [clockMs] into the 900 ms open. Each line starts 45 ms after
 * the one above it; from the eleventh on (below the fold when the sheet opens) they share its
 * timing, so every line on a long tab is whole once the clock stops.
 */
internal fun clipLineReveal(clockMs: Float, index: Int): Float {
    val start = 150f + min(index, 10) * 45f
    return reopenEaseInOutCubic(reopenSeg(clockMs, start, start + 260f))
}

/** One ruled line: box, name, muscle, date. Ticking draws the pen tick and the highlighter. */
@Composable
private fun ClipLine(
    name: String,
    group: String?,
    date: String?,
    ticked: Boolean,
    description: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    leader: Boolean = false,
    showDate: Boolean = true,
    height: Dp = 32.dp,
    ruleColor: Color = ClipRule,
    dottedRule: Boolean = false,
    highlight: Boolean = true,
    hidden: Boolean = false,
    pickMode: Boolean = false,
) {
    val tick = remember { Animatable(if (ticked) 1f else 0f) }
    LaunchedEffect(ticked) {
        if (ticked) tick.animateTo(1f, tween(380, easing = LinearEasing)) else tick.animateTo(0f, tween(160))
    }
    val tilt = remember(name) { ((name.hashCode() % 13 + 13) % 13 - 6).toFloat() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .drawBehind {
                val y = size.height - 0.5f
                drawLine(
                    ruleColor,
                    Offset(0f, y),
                    Offset(size.width, y),
                    1.dp.toPx(),
                    pathEffect = if (dottedRule) PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx())) else null,
                )
            }
            .then(if (pickMode) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier.toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onToggle() }))
            .clearAndSetSemantics {
                contentDescription = description
                role = if (pickMode) Role.Button else Role.Checkbox
                stateDescription = if (pickMode) "Pick for today" else if (ticked) "Ticked" else "Not ticked"
            }
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PenBox(progress = { tick.value }, tilt = tilt)
        Spacer(Modifier.width(if (number != null) 7.dp else 15.dp))
        if (number != null) {
            Text(number.toString().padStart(2, '0'), style = clipNumberStyle, modifier = Modifier.width(22.dp))
        }
        // With a leader the name keeps its own width and the dots take the rest.
        Box(if (leader) Modifier.widthIn(max = 200.dp) else Modifier.weight(1f)) {
            Text(
                name,
                style = clipNameStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .drawBehind {
                        val h = if (highlight) reopenEaseOutCubic(reopenSeg(tick.value, 0.25f, 1f)) else 0f
                        if (h > 0f) {
                            drawRoundRect(
                                ClipHilite,
                                topLeft = Offset(-3.dp.toPx(), 3.dp.toPx()),
                                size = Size((size.width + 6.dp.toPx()) * h, size.height - 5.dp.toPx()),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                blendMode = BlendMode.Multiply,
                            )
                        }
                    },
            )
        }
        if (leader) {
            Canvas(Modifier.weight(1f).height(2.dp).padding(horizontal = 6.dp)) {
                drawLine(
                    ClipInk.copy(alpha = 0.3f),
                    Offset(0f, size.height / 2f),
                    Offset(size.width, size.height / 2f),
                    1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())),
                )
            }
        }
        if (group != null) {
            Text(group, style = clipLabelStyle.copy(color = groupInk(group)), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        if (showDate) {
            Text(date ?: "", style = clipDateStyle, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(44.dp).padding(start = 6.dp))
        }
    }
}

/** The printed box and, over it, the pen tick drawn in one stroke. */
@Composable
private fun PenBox(progress: () -> Float, tilt: Float) {
    Canvas(Modifier.size(13.dp)) {
        drawRoundRect(ClipInk.copy(alpha = 0.42f), style = Stroke(1.4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
        val stroke = reopenEaseOutCubic(reopenSeg(progress(), 0f, 0.45f))
        if (stroke <= 0f) return@Canvas
        val unit = 1.05.dp.toPx()
        val tick = Path().apply {
            moveTo(2.6f * unit, 10.2f * unit)
            cubicTo(4.3f * unit, 11.4f * unit, 5.6f * unit, 12.9f * unit, 6.7f * unit, 15.1f * unit)
            cubicTo(9.3f * unit, 9.3f * unit, 12.6f * unit, 5.3f * unit, 17.6f * unit, 2.3f * unit)
        }
        val measure = PathMeasure().apply { setPath(tick, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * stroke, drawn, true)
        translate(left = -3.dp.toPx(), top = -7.dp.toPx()) {
            rotate(tilt, pivot = Offset(10f * unit, 9f * unit)) {
                drawPath(drawn, ClipInk, style = Stroke(2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** A name that isn't one of your lifts, written in by hand with the app's muscle guess. */
@Composable
private fun ClipWrittenLine(
    pick: ClipboardPick.Typed,
    hidden: Boolean,
    ticked: Boolean,
    struck: Boolean,
    onToggle: () -> Unit,
    registerLine: (String, LayoutCoordinates) -> Unit,
) {
    val guess = clipboardGuess(pick.name)
    val tick = remember { Animatable(if (ticked) 1f else 0f) }
    val strike = remember { Animatable(if (struck) 1f else 0f) }
    LaunchedEffect(ticked, struck) {
        val on = ticked && !struck
        tick.animateTo(if (on) 1f else 0f, tween(if (on) 380 else 160, easing = LinearEasing))
    }
    LaunchedEffect(struck) { strike.animateTo(if (struck) 1f else 0f, tween(260, easing = FastOutSlowInEasing)) }
    val tilt = remember(pick.name) { ((pick.name.hashCode() % 13 + 13) % 13 - 6).toFloat() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .graphicsLayer { alpha = if (hidden) 0f else 1f - 0.5f * strike.value }
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(ClipHandInk.copy(alpha = 0.07f), Color.Transparent), endX = size.width * 0.72f))
                val y = size.height - 0.75f
                drawLine(
                    ClipInk.copy(alpha = 0.3f),
                    Offset(0f, y),
                    Offset(size.width, y),
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            .then(
                if (struck) Modifier else Modifier.toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onToggle() }),
            )
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(pick.name).append(", new lift")
                    append(if (guess != null) ", muscle guess ${guess.lowercase()}" else ", no muscle yet")
                    if (struck) append(", same as an existing lift")
                }
                role = Role.Checkbox
                stateDescription = if (ticked && !struck) "Ticked" else "Not ticked"
            }
            .onGloballyPositioned { registerLine(pick.key, it) }
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PenBox(progress = { tick.value }, tilt = tilt)
        Spacer(Modifier.width(15.dp))
        Box(Modifier.weight(1f)) {
            Text(
                pick.name,
                style = clipHandStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawWithContent {
                    val h = reopenEaseOutCubic(reopenSeg(tick.value, 0.25f, 1f))
                    if (h > 0f) {
                        drawRoundRect(
                            ClipHilite,
                            topLeft = Offset(-3.dp.toPx(), 4.dp.toPx()),
                            size = Size((size.width + 6.dp.toPx()) * h, size.height - 7.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            blendMode = BlendMode.Multiply,
                        )
                    }
                    drawContent()
                    val s = strike.value
                    if (s > 0f) {
                        val y = size.height * 0.55f
                        drawLine(ClipHandInk, Offset(-2.dp.toPx(), y), Offset(-2.dp.toPx() + (size.width + 4.dp.toPx()) * s, y), 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                },
            )
        }
        Text(
            if (guess != null) "$guess?" else "NO MUSCLE YET",
            style = clipLabelStyle.copy(color = if (guess != null) groupInk(guess) else ClipInk.copy(alpha = 0.45f)),
            maxLines = 1,
            modifier = Modifier
                .padding(start = 8.dp)
                .drawBehind {
                    if (guess != null) {
                        drawLine(
                            groupInk(guess),
                            Offset(0f, size.height),
                            Offset(size.width, size.height),
                            1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 1.5.dp.toPx())),
                        )
                    }
                },
        )
        ClipStamp(text = "NEW", small = true, rotation = -4f, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ClipNoteIfOpen(state: ClipboardState, key: String, onAnswer: (String, ClipboardAnswer) -> Unit) {
    if (state.noteKey == key) ClipNote(state, key, onAnswer)
}

/** A sticky note torn off its pad: a row of teeth along the top edge. */
private val ClipNoteShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tooth = with(density) { 4.dp.toPx() }
        val teeth = 25
        val path = Path().apply {
            moveTo(0f, tooth)
            for (i in 0 until teeth) {
                val x = size.width * (i + 0.5f) / teeth
                lineTo(x, if (i % 2 == 0) 0f else tooth)
            }
            lineTo(size.width, tooth)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/** "SAME LIFT?" over "Leg Extension · last Sep 18". Add waits until this is answered. */
@Composable
private fun ClipNote(state: ClipboardState, key: String, onAnswer: (String, ClipboardAnswer) -> Unit) {
    val check = state.checks[key] as? ClipboardCheck.LookAlike ?: return
    val appear = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { appear.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
    val when_ = clipboardNoteDate(check.lastCheckedDate)
    Column(
        modifier = Modifier
            .padding(start = 36.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
            .fillMaxWidth()
            .graphicsLayer {
                val t = appear.value
                alpha = t
                translationY = (1f - reopenBack(t, 1.3f)) * (-12).dp.toPx()
                rotationZ = -0.7f - (1f - t) * 3f
            }
            .shadow(6.dp, ClipNoteShape, clip = false, ambientColor = Color(0xFF3C280A), spotColor = Color(0xFF3C280A))
            .clip(ClipNoteShape)
            .background(ClipNote)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Text("SAME LIFT?", style = clipEyebrowStyle)
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold)) { append(check.name) }
                if (when_ != null) {
                    withStyle(SpanStyle(color = ClipInk.copy(alpha = 0.55f))) { append(" · last ") }
                    withStyle(SpanStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold)) { append(when_) }
                }
            },
            style = clipNoteStyle,
            modifier = Modifier.semantics {
                contentDescription = "Same lift? ${check.name}" + (when_?.let { ", last done $it" } ?: "")
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            ClipStamp(
                text = "NEW LIFT",
                color = ClipInk.copy(alpha = 0.6f),
                description = "New lift",
                onClick = { onAnswer(key, ClipboardAnswer.Different) },
            )
            ClipStamp(
                text = "SAME",
                description = "Same lift",
                onClick = { onAnswer(key, ClipboardAnswer.Same) },
            )
        }
    }
}

/** The tear strip: scissors on a perforation, and the stamp that adds everything ticked. */
@Composable
private fun ClipStrip(state: ClipboardState, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val count = state.ticks.size
    val shown by animateFloatAsState(if (count > 0) 0f else 1f, tween(280, easing = FastOutSlowInEasing), label = "clip_strip")
    val waiting = state.noteKey != null || state.checking > 0
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { translationY = shown * size.height * 1.06f }
            // One stable modifier whose value changes: swapping modifiers in and out left the
            // accessibility tree stale. An empty strip is off the paper and announces nothing.
            .semantics { if (count == 0) hideFromAccessibility() }
            .drawBehind {
                paperFill()
                drawLine(
                    ClipInk.copy(alpha = 0.38f),
                    Offset(0f, 0.75f),
                    Offset(size.width, 0.75f),
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(start = 16.dp, end = 12.dp, top = 4.dp),
    ) {
        Scissors(Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-13).dp))
        if (state.noteKey != null) {
            Text("ANSWER THE NOTE ↑", style = clipEyebrowStyle, modifier = Modifier.align(Alignment.CenterStart))
        } else {
            Text(
                "Clear",
                style = clipLinkStyle.copy(fontSize = 13.5.sp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(role = Role.Button) { state.clearTicks() }
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = "Clear ticks" },
            )
        }
        ClipStamp(
            big = true,
            enabled = !waiting && count > 0,
            description = "Add $count ${clipboardAddLabel(count)}",
            onClick = onAdd,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { style, color ->
            Text("ADD ", style = style, color = color)
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(220)) { h -> if (up) h else -h }) togetherWith
                        (slideOutVertically(tween(220)) { h -> if (up) -h else h })
                },
                label = "clip_count",
            ) { n -> Text("$n", style = style, color = color) }
            Text(" ${clipboardAddLabel(count).uppercase()}", style = style, color = color)
        }
    }
}

@Composable
private fun Scissors(modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp).drawBehind { paperFill() }) {
        val ink = ClipInk.copy(alpha = 0.6f)
        val w = size.width
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(ink, radius = w * 0.13f, center = Offset(w * 0.27f, w * 0.27f), style = stroke)
        drawCircle(ink, radius = w * 0.13f, center = Offset(w * 0.27f, w * 0.73f), style = stroke)
        drawLine(ink, Offset(w * 0.36f, w * 0.34f), Offset(w * 0.86f, w * 0.76f), 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(ink, Offset(w * 0.36f, w * 0.66f), Offset(w * 0.86f, w * 0.24f), 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------------
// Programs: the pocket and the card.
// ---------------------------------------------------------------------------------------------

@Composable
private fun ClipPocket(
    pocket: List<ClipboardProgramUi>,
    step: Float,
    loadedSlotId: Long?,
    hiddenSlotId: Long?,
    openClock: () -> Float,
    onOpenProgram: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.graphicsLayer {
            val t = reopenEaseOutCubic(reopenSeg(openClock() * 900f, 320f, 700f))
            alpha = t
            translationY = (1f - t) * 26.dp.toPx()
        },
    ) {
        val n = pocket.size
        pocket.forEachIndexed { index, program ->
            val front = index == 0
            val color = splitAccentColor(program.colorIndex)
            Box(
                modifier = Modifier
                    .zIndex(10f - index)
                    .padding(start = 5.dp, end = 5.dp, top = (step * (n - 1 - index)).dp)
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (program.slotId == hiddenSlotId) 0f else 1f }
                    .shadow(3.dp, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp), clip = false)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(color)
                    .clickable(role = Role.Button) { onOpenProgram(program.slotId) }
                    .semantics {
                        contentDescription = "Open ${program.name} program card, ${program.lifts.size} ${if (program.lifts.size == 1) "lift" else "lifts"}" +
                            if (program.slotId == loadedSlotId) ", clipped on" else ""
                    }
                    .padding(start = 12.dp, end = 12.dp, top = if (front) 10.dp else 3.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        program.name,
                        style = if (front) clipBandStyle.copy(fontSize = 13.5.sp) else clipBandSmallStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (front) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${program.lifts.size} ${if (program.lifts.size == 1) "LIFT" else "LIFTS"}", style = clipBandMetaStyle)
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = EspressoPalette.onColor, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        // The pocket's front, stitched, over the bottom of every card.
        Box(
            modifier = Modifier
                .zIndex(20f)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .shadow(6.dp, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 3.dp, bottomEnd = 3.dp), clip = false)
                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(PocketTop, PocketBottom)))
                    drawRect(ShaderBrush(ImageShader(boardFibre, TileMode.Repeated, TileMode.Repeated)))
                    drawLine(Color.White.copy(alpha = 0.09f), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
                    drawLine(
                        Color.White.copy(alpha = 0.16f),
                        Offset(8.dp.toPx(), 6.dp.toPx()),
                        Offset(size.width - 8.dp.toPx(), 6.dp.toPx()),
                        1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                },
        )
    }
}

@Composable
private fun ClipProgramCard(
    program: ClipboardProgramUi,
    isLoaded: Boolean,
    tornKeys: Set<String>,
    skipped: Set<Long>,
    rise: () -> Float,
    onToggleLift: (Long) -> Unit,
    onBack: () -> Unit,
    onClipOn: () -> Unit,
    onUnclip: () -> Unit,
    registerLine: (String, LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = splitAccentColor(program.colorIndex)
    val loading = program.lifts.count { it.exerciseId !in skipped }
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .graphicsLayer {
                val t = reopenEaseOutCubic(rise())
                translationY = (1f - t) * size.height * 0.9f
                rotationZ = -0.5f * t
                clip = true
                shape = RoundedCornerShape(6.dp)
            }
            .drawWithContent {
                val t = reopenEaseOutCubic(rise())
                val band = 38.dp.toPx()
                clipRect(bottom = band + (size.height - band) * t) { this@drawWithContent.drawContent() }
            }
            .shadow(14.dp, RoundedCornerShape(6.dp), clip = false)
            .clip(RoundedCornerShape(6.dp))
            .background(ClipCard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(38.dp).background(color).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(program.name.uppercase(), style = clipBandStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${program.lifts.size} ${if (program.lifts.size == 1) "LIFT" else "LIFTS"}", style = clipBandMetaStyle)
        }
        Perforation(hole = ClipPaper)
        if (program.lifts.isEmpty()) {
            Text(
                "NO LIFTS SAVED",
                style = clipEyebrowStyle,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
        program.lifts.forEachIndexed { index, lift ->
            ClipLine(
                name = lift.name,
                group = lift.primaryGroup,
                date = null,
                ticked = lift.exerciseId !in skipped,
                description = "${lift.name}, ${if (lift.exerciseId in skipped) "left out today" else "loads today"}",
                onToggle = { if (!isLoaded) onToggleLift(lift.exerciseId) },
                number = index + 1,
                leader = true,
                showDate = false,
                height = 30.dp,
                ruleColor = ClipInk.copy(alpha = 0.2f),
                dottedRule = true,
                highlight = false,
                hidden = "p:${program.slotId}:${lift.exerciseId}" in tornKeys,
                modifier = Modifier.onGloballyPositioned { registerLine("p:${program.slotId}:${lift.exerciseId}", it) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Back",
                style = clipLinkStyle,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(vertical = 6.dp)
                    .semantics { contentDescription = "Back" },
            )
            Spacer(Modifier.weight(1f))
            if (isLoaded) {
                ClipStamp(big = true, description = "Unclip ${program.name}", onClick = onUnclip) { style, ink ->
                    Text("UNCLIP", style = style, color = ink)
                }
            } else {
                ClipStamp(
                    big = true,
                    description = "Clip on ${program.name}, $loading ${if (loading == 1) "lift" else "lifts"}",
                    enabled = program.startable,
                    onClick = onClipOn,
                ) { style, ink ->
                    ClipIcon(ink)
                    Text("CLIP ON ", style = style, color = ink)
                    AnimatedContent(
                        targetState = loading,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(tween(220)) { h -> if (up) h else -h }) togetherWith
                                (slideOutVertically(tween(220)) { h -> if (up) -h else h })
                        },
                        label = "clip_on_count",
                    ) { n -> Text("$n", style = style, color = ink) }
                }
            }
        }
    }
}

@Composable
private fun ClipIcon(ink: Color) {
    Canvas(Modifier.padding(end = 5.dp).size(14.dp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.35f, w * 0.33f)
            lineTo(w * 0.35f, w * 0.69f)
            cubicTo(w * 0.35f, w * 0.83f, w * 0.65f, w * 0.83f, w * 0.65f, w * 0.69f)
            lineTo(w * 0.65f, w * 0.27f)
            cubicTo(w * 0.65f, w * 0.14f, w * 0.46f, w * 0.14f, w * 0.46f, w * 0.27f)
            lineTo(w * 0.46f, w * 0.66f)
        }
        drawPath(path, ink, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A row of punched holes along a tear; the hole shows whatever is behind the card. */
@Composable
private fun Perforation(hole: Color) {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val pitch = 9.dp.toPx()
        var x = 3.dp.toPx() + 5.dp.toPx()
        while (x < size.width) {
            drawCircle(hole, radius = 2.1.dp.toPx(), center = Offset(x, size.height / 2f))
            x += pitch
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ink, steel and the tear.
// ---------------------------------------------------------------------------------------------

/** A rubber stamp: one rectangle of ink, slightly crooked, with the paper showing through. */
@Composable
private fun ClipStamp(
    modifier: Modifier = Modifier,
    text: String? = null,
    big: Boolean = false,
    small: Boolean = false,
    chevron: Boolean = false,
    rotation: Float = if (big) -2f else -3f,
    color: Color = ClipStampInk,
    enabled: Boolean = true,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable (TextStyle, Color) -> Unit)? = null,
) {
    val style = when {
        big -> clipStampBigStyle
        small -> clipStampStyle.copy(fontSize = 9.sp, lineHeight = 10.sp, letterSpacing = 1.4.sp)
        else -> clipStampStyle
    }
    val ink = if (enabled) color else color.copy(alpha = color.alpha * 0.35f)
    Row(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // Worn ink: a speckle knocked out of the stamp.
                drawRect(ShaderBrush(ImageShader(clipGrain, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.DstOut)
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                        .semantics { if (description != null) contentDescription = description }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .drawBehind {
                drawRoundRect(ink, style = Stroke(1.6.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
            }
            .padding(
                start = if (big) 12.dp else if (small) 5.dp else 8.dp,
                end = if (big) 12.dp else if (chevron) 3.dp else if (small) 4.dp else 8.dp,
                top = if (big) 9.dp else if (small) 3.dp else 6.dp,
                bottom = if (big) 8.dp else if (small) 2.dp else 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content != null) {
            content(style, ink)
        } else if (text != null) {
            Text(text, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (chevron) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = ink, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** The steel clip. When a program clips on it lifts, then snaps shut. */
@Composable
private fun ClipSteel(snap: () -> Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(width = 108.dp, height = 32.dp)
            .graphicsLayer {
                val t = snap()
                transformOrigin = TransformOrigin(0.5f, 1f)
                val lift = when {
                    t <= 0f || t >= 1f -> 0f
                    t < 0.42f -> reopenEaseOutCubic(t / 0.42f)
                    else -> 1f - reopenBack(reopenSeg(t, 0.42f, 1f), 1.6f)
                }
                translationY = -7.dp.toPx() * lift
                scaleY = 1f - 0.22f * lift
            },
    ) {
        val u = size.width / 108f
        // Shadow on the paper, then the wire lever, then the plate.
        drawRoundRect(Color.Black.copy(alpha = 0.32f), topLeft = Offset(4f * u, 16f * u), size = Size(100f * u, 16f * u), cornerRadius = CornerRadius(5f * u))
        val wire = Path().apply {
            moveTo(31f * u, 15f * u)
            cubicTo(31f * u, 4.5f * u, 77f * u, 4.5f * u, 77f * u, 15f * u)
        }
        drawPath(
            wire,
            Brush.horizontalGradient(listOf(Color(0xFF6F6962), Color(0xFFF0EBE4), Color(0xFF6F6962)), startX = 31f * u, endX = 77f * u),
            style = Stroke(3.4f * u, cap = StrokeCap.Round),
        )
        val plate = RoundRect(Rect(3f * u, 13f * u, 105f * u, 29f * u), CornerRadius(5f * u))
        drawPath(
            Path().apply { addRoundRect(plate) },
            Brush.verticalGradient(
                0f to Color(0xFFEDE8E1),
                0.42f to Color(0xFFB3ADA5),
                0.58f to Color(0xFF8A847D),
                1f to Color(0xFFD2CCC4),
                startY = 13f * u,
                endY = 29f * u,
            ),
        )
        drawLine(Color.White.copy(alpha = 0.55f), Offset(9f * u, 14.3f * u), Offset(99f * u, 14.3f * u), 1f * u)
        drawLine(Color(0xFF5E5852).copy(alpha = 0.55f), Offset(8f * u, 25.2f * u), Offset(100f * u, 25.2f * u), 1f * u)
        drawCircle(Color(0xFF6D675F), radius = 2.1f * u, center = Offset(16f * u, 20.5f * u))
        drawCircle(Color(0xFF6D675F), radius = 2.1f * u, center = Offset(92f * u, 20.5f * u))
    }
}

/** A torn line: it peels, tips and drops out of the board, fading as it goes. */
@Composable
private fun TornStrip(line: TornLine, index: Int, clock: () -> Float) {
    val density = LocalDensity.current
    val tilt = if (index % 2 == 0) -1f else 1f
    Box(
        modifier = Modifier
            .zIndex(30f)
            .offset(x = with(density) { line.bounds.left.toDp() }, y = with(density) { line.bounds.top.toDp() })
            .size(width = with(density) { line.bounds.width.toDp() }, height = with(density) { line.bounds.height.toDp() })
            .graphicsLayer {
                val t = reopenSeg(clock() * 760f, index * 70f, index * 70f + 620f)
                val fall = reopenEaseInOutCubic(t)
                translationY = fall * fall * 240.dp.toPx() - reopenEaseOutCubic(reopenSeg(t, 0f, 0.25f)) * 8.dp.toPx()
                translationX = fall * 16.dp.toPx() * tilt
                rotationZ = tilt * 4f * fall
                alpha = if (t <= 0f) 1f else (1f - reopenSeg(t, 0.45f, 1f))
                shadowElevation = 6.dp.toPx() * (1f - fall)
            }
            .drawBehind { paperFill() }
            .padding(start = 10.dp, end = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PenBox(progress = { 1f }, tilt = 0f)
            Spacer(Modifier.width(15.dp))
            Text(
                line.name,
                style = if (line.handwritten) clipHandStyle else clipNameStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (line.group != null) {
                Text(line.group, style = clipLabelStyle.copy(color = groupInk(line.group)), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
