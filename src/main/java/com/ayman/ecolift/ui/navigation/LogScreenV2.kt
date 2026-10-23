package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.ayman.ecolift.ui.viewmodel.PendingExerciseResolutionUi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.BodyPartRegionMapper
import com.ayman.ecolift.data.selectableBodyPartGroups
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.data.splits.SplitSwitchTarget
import com.ayman.ecolift.ui.theme.AnimatedCounter
import com.ayman.ecolift.ui.theme.AnimatedVolumeCounter
import com.ayman.ecolift.ui.theme.DefaultLogGlassPalette
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoEyebrow
import com.ayman.ecolift.ui.theme.EspressoIconButton
import com.ayman.ecolift.ui.theme.EspressoMasthead
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.ui.theme.espressoLongDate
import com.ayman.ecolift.ui.theme.espressoShortDate
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.LogGlassPalette
import com.ayman.ecolift.ui.theme.LogMaterialTypography
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.bounceClick
import com.ayman.ecolift.ui.theme.CarveDepth
import com.ayman.ecolift.ui.theme.carvedPanel
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.theme.palette
import com.ayman.ecolift.ui.theme.rememberHeavyHaptic
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import com.ayman.ecolift.ui.viewmodel.E1rmPointUi
import com.ayman.ecolift.ui.viewmodel.GhostSetUi
import com.ayman.ecolift.ui.viewmodel.RecommendationState
import com.ayman.ecolift.ui.viewmodel.PendingSplitSwitchConfirmation
import com.ayman.ecolift.ui.viewmodel.ClipboardCheck
import com.ayman.ecolift.ui.viewmodel.ClipboardLiftUi
import com.ayman.ecolift.ui.viewmodel.ClipboardPick
import com.ayman.ecolift.ui.viewmodel.ClipboardPickRequest
import com.ayman.ecolift.ui.viewmodel.ClipboardProgramUi
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.defaultTicketInclusion
import com.ayman.ecolift.ui.viewmodel.ticketInclusionKey
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.ui.viewmodel.DayUndoState
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.ui.viewmodel.DayUndoCopy
import com.ayman.ecolift.ui.viewmodel.DayUndoTint
import com.ayman.ecolift.ui.viewmodel.liftRemovedCopy
import com.ayman.ecolift.ui.viewmodel.setRemovedCopy
import com.ayman.ecolift.ui.theme.ConfirmTone
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoConfirmSheet
import com.ayman.ecolift.ui.theme.EspressoGrabber
import com.ayman.ecolift.ui.theme.espressoButtonShadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.drawText
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.util.noun
import com.ayman.ecolift.web.util.YearMonthW
import com.ayman.ecolift.web.util.formatTrimmedDecimal
import com.ayman.ecolift.web.util.isBefore
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.daysUntil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class LoggedSet(
    val id: Long,
    val syncId: String = "",
    val exerciseId: Long = 0L,
    val setNumber: Int,
    val weight: String,
    val reps: String,
    val isBodyweight: Boolean,
    val isCompleted: Boolean,
    val suggestedWeight: String? = null,
    val suggestedReps: String? = null,
    val hasAssistSuggestion: Boolean = false,
    val inputWarning: String? = null,
    val restSeconds: Int? = null,
    val isWarmup: Boolean = false,
)

/**
 * Stable identity for a set while its delete is staged behind the Undo snackbar.
 *
 * Newly-added rows can be rendered with an optimistic, temporary database id and then receive
 * their authoritative id while the snackbar is still open. Keeping only [originalId] would make
 * that row reappear and would eventually ask the repository to delete an id that no longer exists.
 * [syncId] is the primary identity across that remap; exercise + set number is the compatibility
 * fallback for older rows that do not have a sync id yet.
 */
@Immutable
internal data class PendingSetDeletionRef(
    val originalId: Long,
    val syncId: String,
    val exerciseId: Long,
    val setNumber: Int,
)

/** How much of the screen bottom the rest tab and Undo pill cover above the bar; a lifted Log row clears it. */
internal fun logOverlayHeight(pillUp: Boolean, restUp: Boolean): Dp = when {
    pillUp -> LogUndoPillHeight + if (restUp) RestTabHeight + 8.dp else 14.dp
    restUp -> RestTabHeight
    else -> 0.dp
}

internal fun pendingSetDeletionRef(set: LoggedSet): PendingSetDeletionRef =
    PendingSetDeletionRef(
        originalId = set.id,
        syncId = set.syncId,
        exerciseId = set.exerciseId,
        setNumber = set.setNumber,
    )

internal fun samePendingSetDeletion(
    first: PendingSetDeletionRef,
    second: PendingSetDeletionRef,
): Boolean {
    if (first.originalId != 0L && first.originalId == second.originalId) return true
    if (first.syncId.isNotBlank() && second.syncId.isNotBlank()) {
        return first.syncId == second.syncId
    }
    // A settled row that has a durable sync id is never equivalent to a legacy row merely because
    // a later insert reused the same logical set number. That replacement belongs to the user.
    if (first.syncId.isNotBlank() || second.syncId.isNotBlank()) return false
    return first.exerciseId != 0L &&
        first.exerciseId == second.exerciseId &&
        first.setNumber == second.setNumber &&
        // Logical-address fallback exists only for an optimistic/unsaved legacy row whose id can
        // legitimately be replaced. Two settled positive ids describe two different database rows.
        (first.originalId <= 0L || second.originalId <= 0L)
}

private fun matchingPendingSetDeletionRows(
    ref: PendingSetDeletionRef,
    exercises: List<ExerciseLog>,
): List<LoggedSet> {
    val sets = exercises.asSequence().flatMap { it.sets.asSequence() }.toList()
    if (ref.syncId.isNotBlank()) {
        val sameSyncId = sets.filter { it.syncId == ref.syncId }
        if (sameSyncId.isNotEmpty()) return sameSyncId
    }

    // Zero is an unset sentinel, not identity. Matching it would hide/delete every unsaved row.
    if (ref.originalId != 0L) {
        sets.firstOrNull { it.id == ref.originalId }?.let { return listOf(it) }
    }

    // Never retarget a settled deletion to a later row occupying the same set number. Only an
    // optimistic legacy row may use the logical address while its temporary id is being remapped.
    if (ref.originalId > 0L || ref.exerciseId == 0L) return emptyList()
    return sets.filter {
        it.exerciseId == ref.exerciseId &&
            it.setNumber == ref.setNumber &&
            it.syncId.isBlank()
    }
}

internal fun resolvePendingSetDeletionId(
    ref: PendingSetDeletionRef,
    exercises: List<ExerciseLog>,
): Long {
    val matches = matchingPendingSetDeletionRows(ref, exercises)
    return matches.firstOrNull { it.id > 0L && it.id != ref.originalId }?.id
        ?: matches.firstOrNull { it.id > 0L }?.id
        ?: matches.firstOrNull { it.id != ref.originalId }?.id
        ?: matches.firstOrNull()?.id
        ?: ref.originalId
}

internal fun pendingSetDeletionIds(
    refs: Set<PendingSetDeletionRef>,
    exercises: List<ExerciseLog>,
): Set<Long> = buildSet {
    refs.forEach { ref ->
        // Keep the optimistic id hidden if it briefly coexists with the authoritative row.
        add(ref.originalId)
        matchingPendingSetDeletionRows(ref, exercises).forEach { add(it.id) }
    }
}

/** True while [ref]'s row is still on the day — a committing ref stays hidden until this is false. */
internal fun pendingSetDeletionPresent(ref: PendingSetDeletionRef, exercises: List<ExerciseLog>): Boolean =
    matchingPendingSetDeletionRows(ref, exercises).isNotEmpty()

/** What the day shows, for "the next change": hidden sets and sync bookkeeping are left out. */
internal fun logVisibleSignature(exercises: List<ExerciseLog>, hiddenIds: Set<Long>): List<String> =
    exercises.flatMap { ex ->
        val visible = ex.sets.filter { it.id !in hiddenIds }
        if (visible.isEmpty()) emptyList()
        else visible.map { "${ex.exerciseId}|${it.setNumber}|${it.weight}|${it.reps}|${it.isBodyweight}|${it.isCompleted}|${it.isWarmup}|${it.restSeconds}" } +
            "${ex.exerciseId}|mode|${ex.displayMode}"
    }.sorted()

/** The pill's ring colour for a Day Undo offer: the split's colour, the lift's region, or ink. */
internal fun undoRingColor(tint: DayUndoTint, exercises: List<ExerciseLog>, programs: List<ClipboardProgramUi>): Color = when (tint) {
    is DayUndoTint.Split -> programs.firstOrNull { it.slotId == tint.slotId }?.let { splitAccentColor(it.colorIndex) } ?: EspressoPalette.ink
    is DayUndoTint.Lift -> exercises.firstOrNull { it.exerciseId == tint.exerciseId }?.let { restTabColor(it.muscleGroups) } ?: EspressoPalette.ink
    DayUndoTint.Ink -> EspressoPalette.ink
}

/**
 * Resolve a set interaction across the optimistic-id -> server-id handoff.
 *
 * A non-blank sync id is authoritative. If that identity disappears, a different row at the same
 * exercise/set number is a replacement, not a valid target for a still-open keypad. When both the
 * optimistic and authoritative copies overlap briefly, prefer the positive server id so edits do
 * not keep writing through a stale temporary id.
 */
internal fun resolveLoggedSetInteraction(
    exercises: List<ExerciseLog>,
    setId: Long,
    setSyncId: String,
    exerciseId: Long,
    setNumber: Int,
): LoggedSet? {
    val sets = exercises.asSequence().flatMap { it.sets.asSequence() }.toList()
    if (setSyncId.isNotBlank()) {
        val sameSyncId = sets.filter { it.syncId == setSyncId }
        return sameSyncId.firstOrNull { it.id > 0L }
            ?: sameSyncId.firstOrNull { it.id == setId }
            ?: sameSyncId.firstOrNull()
    }

    if (setId != 0L) {
        sets.firstOrNull { it.id == setId }?.let { return it }
    }

    // Legacy optimistic rows predate sync ids. Limit their compatibility fallback to other blank-
    // sync rows so a newly-created replacement can never inherit an old gesture/keypad session.
    if (setId > 0L || exerciseId == 0L) return null
    return sets.firstOrNull {
        it.exerciseId == exerciseId &&
            it.setNumber == setNumber &&
            it.syncId.isBlank()
    }
}

/** Correction mode belongs to the card, not to whether every row is completed this frame. */
internal fun retainedCorrectionReviewCardId(
    reviewingCardId: String?,
    exercises: List<ExerciseLog>,
): String? = reviewingCardId?.takeIf { cardId -> exercises.any { it.cardId == cardId } }

@Immutable
data class ExerciseLog(
    val cardId: String,
    val cardPosition: Int,
    val displayMode: WorkoutCardDisplayMode,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroups: String,
    val suggestedMuscleGroup: String? = null,
    val lastTopSet: String?,
    val bestTopSet: String?,
    val showBaselineHint: Boolean = false,
    val isNewPB: Boolean = false,
    val recommendationState: RecommendationState = RecommendationState.Idle,
    val ghostSets: List<GhostSetUi> = emptyList(),
    val lastSessionDate: String? = null,
    val e1rmHistory: List<E1rmPointUi> = emptyList(),
    // The user's bodyweight setting (whole lb), for bodyweight exercises' e1RM caption.
    val userBodyweightLbs: Int? = null,
    val sets: List<LoggedSet>,
    val showTakenChip: Boolean = false,
    val takenGroupKey: String? = null,
    val takenChipLabel: String = "Machine taken?",
)

data class ExerciseSearchResult(
    val exerciseId: Long?,
    val name: String,
    val muscleGroups: String,
)

private enum class NumberInputKind { Weight, Reps }

private data class NumberInputTarget(
    val setId: Long,
    val setSyncId: String,
    val exerciseId: Long,
    val setNumber: Int,
    val kind: NumberInputKind,
    val value: String,
    val replaceOnNextKey: Boolean = false,
)

private data class SetActionTarget(
    val exerciseId: Long,
    val set: LoggedSet,
)

private data class InlineSetEditTarget(
    val setId: Long,
    val kind: NumberInputKind,
)


private fun LazyListState.isAtTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

private fun LazyListState.isAtBottom(): Boolean {
    val last = layoutInfo.visibleItemsInfo.lastOrNull()
    return layoutInfo.totalItemsCount == 0 ||
        (last != null &&
            last.index == layoutInfo.totalItemsCount - 1 &&
            last.offset + last.size <= layoutInfo.viewportEndOffset)
}

/** Lays the content out [px] taller than its slot, overhanging the bottom edge, while the slot keeps its own size. */
private fun Modifier.extendBelow(px: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        if (px <= 0 || !constraints.hasBoundedHeight) constraints
        else constraints.copy(minHeight = constraints.minHeight + px, maxHeight = constraints.maxHeight + px)
    )
    layout(placeable.width, (placeable.height - px.coerceAtLeast(0)).coerceAtLeast(0)) { placeable.place(0, 0) }
}

private fun Modifier.backgroundChromeGestureStrip(
    enabled: Boolean,
    onRevealChrome: () -> Unit
): Modifier = pointerInput(enabled, onRevealChrome) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startPosition = down.position
        var lastPosition = startPosition
        var isUp = false

        while (!isUp) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
            lastPosition = change.position
            if (change.changedToUpIgnoreConsumed()) {
                isUp = true
            }
        }

        val distance = (lastPosition - startPosition).getDistance()
        val touchSlop = viewConfiguration.touchSlop

        if (distance < touchSlop) {
            onRevealChrome()
        }
    }
}

/**
 * Material's [Icons.AutoMirrored.Outlined.KeyboardArrowLeft] is a stubby, heavy glyph that at 42dp
 * carried the same visual weight as the date title it flanks. This is the same gesture drawn
 * thinner over a taller, more acute path.
 */
@Composable
private fun NavChevron(
    pointsForward: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(19.dp)) {
        // Authored in a 24x24 viewport, scaled to whatever box we're given.
        val s = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val path = Path().apply {
            if (pointsForward) {
                moveTo(p(9f, 4f).x, p(9f, 4f).y)
                lineTo(p(17f, 12f).x, p(17f, 12f).y)
                lineTo(p(9f, 20f).x, p(9f, 20f).y)
            } else {
                moveTo(p(15f, 4f).x, p(15f, 4f).y)
                lineTo(p(7f, 12f).x, p(7f, 12f).y)
                lineTo(p(15f, 20f).x, p(15f, 20f).y)
            }
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * A day-stepping button. Carved rather than raised (it's a control, not content), with the outer
 * corners rounder than the inner ones so it leans the way it travels, and an accent rail on the
 * outboard edge only.
 *
 * [isDimmed] means "there's probably nothing over there" — a shallower carve and a grey rail. It is
 * deliberately **not** disabled: these two buttons are the only way to change days (the page has no
 * horizontal swipe; the sole detectHorizontalDragGestures belongs to the exercise card), so an
 * invisible or inert target isn't an option.
 */
@Composable
fun LogTopBar(
    dateLabel: String,
    cycleSlotLabel: String?,
    selectedDate: LocalDate,
    workedDays: Set<LocalDate>,
    onDateClick: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onGoToToday: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier,
    exerciseCount: Int = 0,
    setsLogged: Int = 0,
    setsTotal: Int = 0,
    titleColor: Color = EspressoPalette.chest,
) {
    // Espresso masthead (spec §Log): kicker, the day as the title in the day's colour, the two day
    // arrows, and one line of context. Tapping the title still opens the calendar.
    val today = remember { todayLocalDate() }
    val dayOffset = remember(selectedDate, today) { today.daysUntil(selectedDate) }
    val isAwayFromToday = dayOffset != 0
    val nextDay = remember(selectedDate) { selectedDate.plusDays(1) }
    // Dim forward only when there's genuinely nothing over there: a future day with no logged sets.
    val nextDayIsDead = nextDay > today && !workedDays.contains(nextDay)
    var slideDirection by remember { mutableStateOf(0) }
    val magnitude = abs(dayOffset)
    val kicker = if (!isAwayFromToday) {
        "LOG · ${espressoLongDate(selectedDate)}"
    } else {
        "LOG · $magnitude DAY${if (magnitude > 1) "S" else ""} ${if (dayOffset < 0) "AGO" else "AHEAD"}"
    }
    val title = if (!isAwayFromToday) "Today" else dateLabel
    // The exercise and set count used to sit on a line of its own under the title. Ayman
    // (2026-09-21): that line "is kind of a waste of space", so it rides the kicker row instead,
    // right-aligned. Ayman (2026-09-25): it stays there, but its numbers get emphasis and the sets
    // become a small meter. An empty day shows no count at all; its empty state speaks for it.
    val kickerShort = if (!isAwayFromToday) "LOG · ${espressoShortDate(selectedDate).uppercase()}" else kicker
    val kickerBare = kickerShort.removePrefix("LOG · ")
    val slot = cycleSlotLabel?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()

    BoxWithConstraints(modifier = modifier.statusBarsPadding()) {
        // Nothing here may ellipsize: try the fullest wording first and step down until the
        // kicker and the count share the row.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxPx = constraints.maxWidth - with(density) { (22.dp * 2 + 12.dp).roundToPx() }
        val (kickerText, count) = remember(kicker, kickerShort, kickerBare, slot, exerciseCount, setsLogged, setsTotal, maxPx) fit@{
            fun width(text: String) = measurer.measure(text, style = EspressoType.kicker, softWrap = false, maxLines = 1).size.width
            fun width(count: LogMastCount): Int {
                val (before, after) = logMastCountParts(count)
                val meter = with(density) { (logMastMeterWidthDp(count.total).dp + if (count.total > 0) 11.dp else 0.dp).roundToPx() }
                return measurer.measure(logMastLine(before), softWrap = false, maxLines = 1).size.width + meter +
                    (if (after.isEmpty()) 0 else measurer.measure(logMastLine(after), softWrap = false, maxLines = 1).size.width)
            }
            if (exerciseCount == 0) return@fit Pair<String, LogMastCount?>(kicker, null)
            val full = LogMastCount(slot, exerciseCount, setsLogged, setsTotal, setsWord = true)
            val candidates = listOfNotNull(
                slot?.let { kicker to full },
                kicker to full.copy(slot = null),
                kickerShort to full.copy(slot = null),
                kickerShort to full.copy(slot = null, setsWord = false),
                kickerBare to full.copy(slot = null, setsWord = false),
            )
            val fitted = candidates.firstOrNull { (left, right) -> width(left) + width(right) <= maxPx } ?: candidates.last()
            Pair<String, LogMastCount?>(fitted.first, fitted.second)
        }

        EspressoMasthead(
            kicker = kickerText,
            kickerEndContent = count?.let { c ->
                { LogMastCountRow(count = c, color = titleColor) }
            },
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    val dir = slideDirection
                    if (dir == 0) {
                        fadeIn(animationSpec = tween(durationMillis = 240)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 140))
                    } else {
                        val easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
                        (slideInHorizontally(
                            animationSpec = tween(durationMillis = 240, easing = easing),
                        ) { fullWidth -> dir * (fullWidth / 4).coerceAtLeast(14) } +
                            fadeIn(animationSpec = tween(durationMillis = 240))) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 120))
                    }
                },
                label = "log_date_label",
            ) { label ->
                Text(
                    text = label,
                    style = if (label == "Today") EspressoType.mastTitle else EspressoType.mastTitleSmall,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDateClick)
                        .semantics { contentDescription = "$label. Open calendar" },
                )
            }
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAwayFromToday) {
                    // The one navigation dead-end this screen had: off today, the only way back
                    // was the calendar sheet. Same 38dp button as the arrows so the row stays level.
                    EspressoIconButton(
                        contentDescription = "Today",
                        onClick = {
                            slideDirection = if (dayOffset < 0) 1 else -1
                            onGoToToday()
                        },
                    ) {
                        Text(text = "Today", style = EspressoType.chip, color = EspressoPalette.ink, maxLines = 1, softWrap = false)
                    }
                }
                EspressoIconButton(
                    contentDescription = "Previous Day",
                    onClick = {
                        slideDirection = -1
                        onPreviousDay()
                    },
                ) {
                    NavChevron(pointsForward = false, tint = EspressoPalette.ink)
                    if (dayOffset > 0) HomeDot(Modifier.align(Alignment.TopEnd))
                }
                EspressoIconButton(
                    contentDescription = "Next Day",
                    onClick = {
                        slideDirection = 1
                        onNextDay()
                    },
                ) {
                    NavChevron(pointsForward = true, tint = if (nextDayIsDead) EspressoPalette.faint else EspressoPalette.ink)
                    if (dayOffset < 0) HomeDot(Modifier.align(Alignment.TopStart))
                }
            }
        },
        )
    }
}

/** The masthead's set meter: one tick per set up to this many, then one continuous bar. */
internal const val LOG_MAST_METER_TICK_MAX = 12
private const val LOG_MAST_TICK_DP = 3f
private const val LOG_MAST_TICK_GAP_DP = 2f
private const val LOG_MAST_BAR_DP = 40f

internal fun logMastMeterIsContinuous(total: Int): Boolean = total > LOG_MAST_METER_TICK_MAX

/** Drawn width of the meter; none when the day has no sets. */
internal fun logMastMeterWidthDp(total: Int): Float = when {
    total <= 0 -> 0f
    logMastMeterIsContinuous(total) -> LOG_MAST_BAR_DP
    else -> total * LOG_MAST_TICK_DP + (total - 1) * LOG_MAST_TICK_GAP_DP
}

/**
 * Logged and total working sets on the day, for the kicker's count and meter. Warm-ups are left out:
 * one skipped is fine, so it must not read as unfinished work beside a finished workout.
 */
internal fun logMastSetCounts(exercises: List<ExerciseLog>, pendingDeletedSetIds: Set<Long>): Pair<Int, Int> {
    val working = exercises.flatMap { ex -> ex.sets.filter { it.id !in pendingDeletedSetIds && !it.isWarmup } }
    return working.count { it.isCompleted } to working.size
}

/** What the kicker row counts, and how much of the wording fits. */
internal data class LogMastCount(
    val slot: String?,
    val exercises: Int,
    val logged: Int,
    val total: Int,
    val setsWord: Boolean,
)

/**
 * The count as data and words, split around the meter: "4 EXERCISES · " | meter | "8/9 SETS".
 * Numbers are data (Clash, ink); the words stay in the kicker's quiet voice.
 */
internal fun logMastCountParts(count: LogMastCount): Pair<List<DataPart>, List<DataPart>> {
    val before = buildList {
        count.slot?.let { add(filler("$it · ")) }
        add(data("${count.exercises}"))
        add(filler(" EXERCISE${if (count.exercises == 1) "" else "S"}"))
        if (count.total > 0) add(filler(" ·"))
    }
    val after = if (count.total <= 0) emptyList() else buildList {
        add(data("${count.logged}/${count.total}"))
        if (count.setsWord) add(filler(" SETS"))
    }
    return before to after
}

/** Spoken form of the count: the meter is drawn, so the words carry it for a screen reader. */
internal fun logMastCountDescription(count: LogMastCount): String = buildString {
    count.slot?.let { append(it.lowercase().replaceFirstChar { c -> c.uppercase() }).append(", ") }
    append("${count.exercises} exercise${if (count.exercises == 1) "" else "s"}")
    if (count.total > 0) append(", ${count.logged} of ${count.total} sets logged")
}

private fun logMastLine(parts: List<DataPart>) = buildAnnotatedString {
    parts.forEach { part ->
        val style = if (part.isData) {
            SpanStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.2.sp, color = EspressoPalette.ink)
        } else {
            EspressoType.kicker.toSpanStyle().copy(color = EspressoPalette.faint)
        }
        withStyle(style) { append(part.text) }
    }
}

@Composable
private fun LogMastCountRow(count: LogMastCount, color: Color, modifier: Modifier = Modifier) {
    val (before, after) = remember(count) { logMastCountParts(count) }
    // The row keeps the kicker's 12sp line so the masthead is exactly as tall as before; the
    // Clash digits are a touch larger and simply share that line.
    val line = TextStyle(lineHeight = 12.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(12.dp)
            .clearAndSetSemantics { contentDescription = logMastCountDescription(count) },
    ) {
        Text(text = logMastLine(before), style = line, maxLines = 1, softWrap = false)
        if (count.total > 0) {
            Spacer(Modifier.width(6.dp))
            LogMastSetMeter(logged = count.logged, total = count.total, color = color)
            Spacer(Modifier.width(5.dp))
            Text(text = logMastLine(after), style = line, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * One tick per set: logged ticks filled in the day's colour, the rest faint outlines. Past
 * [LOG_MAST_METER_TICK_MAX] sets the ticks would be too fine to read, so it becomes one bar.
 */
@Composable
private fun LogMastSetMeter(logged: Int, total: Int, color: Color) {
    val filled by animateFloatAsState(
        targetValue = if (total > 0) logged.toFloat() / total else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "log_mast_meter",
    )
    Canvas(modifier = Modifier.width(logMastMeterWidthDp(total).dp).height(10.dp)) {
        val outline = Stroke(width = 1.dp.toPx())
        val faint = EspressoPalette.faint
        if (logMastMeterIsContinuous(total)) {
            val h = 4.dp.toPx()
            val top = (size.height - h) / 2f
            val r = CornerRadius(h / 2f)
            drawRoundRect(faint, topLeft = Offset(0.5f, top), size = Size(size.width - 1f, h), cornerRadius = r, style = outline)
            val w = size.width * filled
            if (w > 0f) drawRoundRect(color, topLeft = Offset(0f, top), size = Size(w, h), cornerRadius = r)
        } else {
            val tick = LOG_MAST_TICK_DP.dp.toPx()
            val gap = LOG_MAST_TICK_GAP_DP.dp.toPx()
            val r = CornerRadius(1.dp.toPx())
            repeat(total) { i ->
                val x = i * (tick + gap)
                if (i < logged) {
                    drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(tick, size.height), cornerRadius = r)
                } else {
                    val half = outline.width / 2f
                    drawRoundRect(
                        faint,
                        topLeft = Offset(x + half, half),
                        size = Size(tick - outline.width, size.height - outline.width),
                        cornerRadius = r,
                        style = outline,
                    )
                }
            }
        }
    }
}

/**
 * Space the list reserves under the Log masthead: the masthead's own height as measured on the
 * 390dp preview build (124dp with the 50sp "Today" title, 106dp with a dated title and its 38dp
 * buttons) plus the spec's 16dp before the search bar. Constants rather than a measurement so the
 * list never remeasures while it scrolls.
 */
internal fun logListTopPadding(isToday: Boolean): Dp = (if (isToday) 124.dp else 106.dp) + 16.dp

@Composable
private fun BoxScope.HomeDot(modifier: Modifier) {
    Box(modifier = modifier.padding(5.dp).size(5.dp).clip(CircleShape).background(EspressoPalette.chest))
}

private fun formatLoggedSetLoad(set: LoggedSet): String =
    if (set.isBodyweight) {
        if (set.weight.isBlank()) "BW" else "BW + ${set.weight}"
    } else {
        set.weight.ifBlank { "-" }
    }

private fun completedLoadLabel(set: LoggedSet): String =
    if (set.isBodyweight) {
        formatLoggedSetLoad(set)
    } else {
        "${set.weight.ifBlank { "-" }} lb"
    }

private val MuscleGroupChoices = selectableBodyPartGroups

internal fun primaryMuscleGroup(muscleGroups: String): String? =
    selectableBodyPartParts(muscleGroups).firstOrNull()

internal fun formatMuscleGroupLabel(muscleGroups: String): String {
    val groups = selectableBodyPartParts(muscleGroups)

    if (groups.isEmpty()) return "Choose category"

    return groups.joinToString(" + ") { group ->
        group
            .lowercase()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.uppercase() else char.toString()
                }
            }
    }
}

@Composable
private fun NumberStepperButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .fillMaxHeight(),
        color = Color.Transparent,
        contentColor = Color(0xFF66706E),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF66706E)
            )
        }
    }
}

@Composable
private fun CustomNumberKeyboard(
    value: String,
    kind: NumberInputKind,
    isBodyweight: Boolean,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onToggleBodyweight: () -> Unit,
    onSwitchField: () -> Unit,
    onDone: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier,
) {
    val label = when {
        kind == NumberInputKind.Weight && isBodyweight -> "Added load"
        kind == NumberInputKind.Weight -> "Weight"
        else -> "Reps"
    }
    val placeholder = when {
        kind == NumberInputKind.Weight && isBodyweight -> "BW"
        kind == NumberInputKind.Weight -> "LB"
        else -> "REPS"
    }
    val displayValue = when {
        kind == NumberInputKind.Weight && isBodyweight && value.isBlank() -> "BW"
        value.isBlank() -> placeholder
        else -> value
    }
    val isPlaceholder = value.isBlank() && !(kind == NumberInputKind.Weight && isBodyweight)
    val switchIcon = if (kind == NumberInputKind.Weight) {
        Icons.AutoMirrored.Outlined.KeyboardArrowRight
    } else {
        Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    }
    val switchLabel = if (kind == NumberInputKind.Weight) "Reps" else "Weight"
    val switchDescription = if (kind == NumberInputKind.Weight) "Switch to reps" else "Switch to weight"
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = palette.glassFillStrong,
        border = BorderStroke(1.dp, palette.glassStrokeStrong),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NumberKeyboardDisplayField(
                    label = label,
                    value = displayValue,
                    isPlaceholder = isPlaceholder,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                NumberKeyboardSwitchKey(
                    imageVector = switchIcon,
                    label = switchLabel,
                    contentDescription = switchDescription,
                    onClick = onSwitchField,
                    palette = palette,
                    modifier = Modifier
                        .width(88.dp)
                        .height(54.dp)
                )
            }

            KeyboardKeyRow {
                NumberKeyboardKey("1", onClick = { onKey("1") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("2", onClick = { onKey("2") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("3", onClick = { onKey("3") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey(
                    label = "BW",
                    onClick = onToggleBodyweight,
                    color = if (isBodyweight) palette.accent.copy(alpha = 0.18f) else palette.glassFill,
                    contentColor = if (isBodyweight) palette.accentStrong else palette.ink,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
            KeyboardKeyRow {
                NumberKeyboardKey("4", onClick = { onKey("4") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("5", onClick = { onKey("5") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("6", onClick = { onKey("6") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("DEL", onClick = onBackspace, palette = palette, modifier = Modifier.weight(1f))
            }
            KeyboardKeyRow {
                NumberKeyboardKey("7", onClick = { onKey("7") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("8", onClick = { onKey("8") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("9", onClick = { onKey("9") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("CLR", onClick = onClear, palette = palette, modifier = Modifier.weight(1f))
            }
            KeyboardKeyRow {
                NumberKeyboardKey(
                    label = ".",
                    onClick = { if (kind == NumberInputKind.Weight) onKey(".") },
                    enabled = kind == NumberInputKind.Weight,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                NumberKeyboardKey("0", onClick = { onKey("0") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("00", onClick = { onKey("00") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey(
                    "DONE",
                    onClick = onDone,
                    color = palette.accentStrong,
                    contentColor = Color.White,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NumberKeyboardDisplayField(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        color = palette.glassFillStrong,
        border = BorderStroke(1.dp, palette.glassStroke),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.inkSubtle,
                maxLines = 1
            )
            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPlaceholder) palette.inkSubtle else palette.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NumberKeyboardSwitchKey(
    imageVector: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = palette.glassFill,
        contentColor = palette.ink,
        border = BorderStroke(1.dp, palette.glassStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = palette.ink,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun KeyboardKeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun NumberKeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    contentColor: Color? = null,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    enabled: Boolean = true,
) {
    val effectiveColor = color ?: palette.glassFill
    val effectiveContentColor = if (enabled) {
        contentColor ?: palette.ink
    } else {
        palette.inkSubtle.copy(alpha = 0.42f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) effectiveColor else palette.glassFill.copy(alpha = 0.45f),
        contentColor = effectiveContentColor,
        border = BorderStroke(1.dp, palette.glassStroke.copy(alpha = if (enabled) 1f else 0.45f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = effectiveContentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun GlassSetActionSheet(
    target: SetActionTarget,
    palette: LogGlassPalette,
    onComplete: () -> Unit,
    onAddCopiedSet: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strong = true)
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Set ${target.set.setNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = "${formatLoggedSetLoad(target.set)} lb · ${target.set.reps.ifBlank { "-" }} ${noun(target.set.reps.toIntOrNull() ?: 0, "rep")}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkSubtle
            )
        }

        GlassSheetActionButton(
            label = if (target.set.isCompleted) "Mark incomplete" else "Mark complete",
            palette = palette,
            onClick = onComplete
        )
        GlassSheetActionButton(
            label = "Add copied set",
            palette = palette,
            onClick = onAddCopiedSet
        )
        GlassSheetActionButton(
            label = "Delete set",
            palette = palette,
            isDanger = true,
            onClick = onDelete
        )
    }
}

@Composable
private fun GlassSheetActionButton(
    label: String,
    palette: LogGlassPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isDanger) palette.danger.copy(alpha = 0.12f) else palette.glassFill,
        border = BorderStroke(1.dp, if (isDanger) palette.danger.copy(alpha = 0.32f) else palette.glassStroke),
        contentColor = if (isDanger) palette.danger else palette.ink
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDanger) palette.danger else palette.ink
            )
        }
    }
}

/** Standard plates in pounds, heaviest first, and the bar they go on. */
internal val PLATE_SIZES_LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
internal const val PLATE_BAR_LB = 45.0

/**
 * How [totalLb] goes on a [barLb] bar: the plates on one sleeve, heaviest first (the other sleeve
 * mirrors it), and what a sleeve is still short when the load is not a multiple of 2.5 a side.
 */
internal data class PlateBreakdown(
    val totalLb: Double,
    val barLb: Double,
    val perSide: List<Double>,
    val shortPerSideLb: Double,
) {
    val underBar: Boolean get() = totalLb < barLb
    val loadedLb: Double get() = barLb + 2 * perSide.sum()
}

internal fun plateBreakdown(totalLb: Double, barLb: Double = PLATE_BAR_LB): PlateBreakdown {
    if (totalLb <= barLb) return PlateBreakdown(totalLb, barLb, emptyList(), 0.0)
    var remaining = (totalLb - barLb) / 2.0
    val plates = mutableListOf<Double>()
    for (plate in PLATE_SIZES_LB) {
        while (remaining + 1e-9 >= plate) {
            plates += plate
            remaining -= plate
        }
    }
    return PlateBreakdown(totalLb, barLb, plates, if (remaining > 1e-6) remaining else 0.0)
}

/**
 * Whether a lift is loaded on a barbell. Exercises carry no equipment field, so the name decides:
 * it says "barbell" (or the "BB" shorthand the search already expands).
 */
internal fun isBarbellLift(name: String): Boolean {
    val words = name.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    return "barbell" in words || "bb" in words
}

internal fun formatPlateNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else formatTrimmedDecimal(value)

private const val PLATE_SHEET_MAX_DRAWN = 16

/** A plate's drawn height and thickness, in dp, by denomination: bigger plate, bigger disc. */
private fun plateHeightDp(lb: Double): Float = when {
    lb >= 45.0 -> 118f
    lb >= 35.0 -> 104f
    lb >= 25.0 -> 88f
    lb >= 10.0 -> 66f
    lb >= 5.0 -> 52f
    else -> 42f
}

/** Bumper colours as gyms paint them (45 blue, 35 yellow, 25 green), in the app's own tones. */
private fun plateColor(lb: Double): Color = when {
    lb >= 55.0 -> EspressoPalette.chest
    lb >= 45.0 -> EspressoPalette.back
    lb >= 35.0 -> EspressoPalette.core
    lb >= 25.0 -> EspressoPalette.legs
    lb >= 10.0 -> EspressoPalette.ink
    lb >= 5.0 -> EspressoPalette.dim
    else -> EspressoPalette.faint
}

private fun plateThicknessDp(lb: Double): Float = when {
    lb >= 45.0 -> 22f
    lb >= 35.0 -> 20f
    lb >= 25.0 -> 18f
    lb >= 10.0 -> 15f
    lb >= 5.0 -> 13f
    else -> 12f
}

/**
 * The plate calculator: the total big, then one sleeve of the bar drawn with its plates, each
 * sized by weight and labelled, and the bar as a data line. No "per side" formula to decode.
 */
@Composable
private fun PlateSheet(weightText: String) {
    val total = weightText.toDoubleOrNull() ?: 0.0
    val breakdown = remember(total) { plateBreakdown(total) }
    val textMeasurer = rememberTextMeasurer()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 28.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Plates for ${formatPlateNumber(total)} pounds. ")
                    if (breakdown.perSide.isEmpty()) append("Bar only.")
                    else append("Each side: ${breakdown.perSide.joinToString(", ") { formatPlateNumber(it) }}.")
                }
            },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatPlateNumber(total),
                fontFamily = LogDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                lineHeight = 46.sp,
                letterSpacing = (-2).sp,
                color = EspressoPalette.ink,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "lb",
                fontFamily = LogUiFontFamily,
                fontSize = 15.sp,
                color = EspressoPalette.dim,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        val labelStyle = TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        // A typo like 99999 lb would ask for thousands of plates, each measured and drawn every
        // frame. Past what a real sleeve holds, say how many instead of drawing them.
        if (breakdown.perSide.size > PLATE_SHEET_MAX_DRAWN) {
            DataText(
                parts = listOf(data(breakdown.perSide.size.toString()), filler(" plates a side, more than a sleeve holds")),
                size = 14.sp,
                lineHeight = 19.sp,
                filler = EspressoPalette.dim,
                data = EspressoPalette.ink,
            )
        } else Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val labelRoom = 18.dp.toPx()
            val midY = (size.height - labelRoom) / 2f
            val gap = 4.dp.toPx()
            val plates = breakdown.perSide
            val natural = plates.sumOf { plateThicknessDp(it).dp.toPx().toDouble() }.toFloat() + gap * plates.size
            // The stack sits in the middle of the sheet: the collar moves left as plates are added,
            // and a very big stack thins out rather than running off the sleeve.
            val minCollar = 40.dp.toPx()
            val room = size.width - minCollar - 28.dp.toPx()
            val squeeze = if (natural > room && natural > 0f) room / natural else 1f
            val stack = natural * squeeze
            val collarX = ((size.width - stack) / 2f).coerceIn(minCollar, size.width / 2f)
            // Shaft from the left edge to the collar, then the wider sleeve out to the right.
            drawRoundRect(
                color = EspressoPalette.hair2,
                topLeft = Offset(0f, midY - 3.dp.toPx()),
                size = Size(collarX, 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            val sleeveH = 12.dp.toPx()
            drawRoundRect(
                color = EspressoPalette.hair2,
                topLeft = Offset(collarX, midY - sleeveH / 2f),
                size = Size(size.width - collarX, sleeveH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                color = EspressoPalette.dim,
                topLeft = Offset(collarX - 7.dp.toPx(), midY - 15.dp.toPx()),
                size = Size(7.dp.toPx(), 30.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            // Plates, heaviest against the collar, each as tall as its weight is heavy, and each
            // labelled under the stack. Labels that would touch step up above the plate instead.
            var x = collarX + 2.dp.toPx()
            var lastLabelEnd = Float.NEGATIVE_INFINITY
            plates.forEach { plate ->
                val w = plateThicknessDp(plate).dp.toPx() * squeeze
                val h = plateHeightDp(plate).dp.toPx()
                val top = midY - h / 2f
                val corner = CornerRadius(minOf(3.dp.toPx(), w / 2f))
                drawRoundRect(color = plateColor(plate), topLeft = Offset(x, top), size = Size(w, h), cornerRadius = corner)
                // One hairline down the face so neighbouring plates read as separate discs.
                drawLine(
                    color = EspressoPalette.onColor.copy(alpha = 0.18f),
                    start = Offset(x + w * 0.5f, top + 6.dp.toPx()),
                    end = Offset(x + w * 0.5f, top + h - 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = textMeasurer.measure(formatPlateNumber(plate), style = labelStyle)
                val cx = x + w / 2f
                val below = cx - label.size.width / 2f > lastLabelEnd + 2.dp.toPx()
                val labelTop = if (below) midY + plateHeightDp(PLATE_SIZES_LB.first()).dp.toPx() / 2f + 6.dp.toPx() else top - label.size.height - 4.dp.toPx()
                drawText(
                    textLayoutResult = label,
                    color = if (below) EspressoPalette.ink else EspressoPalette.dim,
                    topLeft = Offset(cx - label.size.width / 2f, labelTop.coerceIn(0f, size.height - label.size.height)),
                )
                if (below) lastLabelEnd = cx + label.size.width / 2f
                x += w + gap * squeeze
            }
        }
        Spacer(Modifier.height(14.dp))
        DataText(
            parts = plateSheetDataLine(breakdown),
            size = 14.sp,
            lineHeight = 19.sp,
            filler = EspressoPalette.dim,
            data = EspressoPalette.ink,
        )
    }
}

/** The line under the drawing: the bar, and anything the drawing cannot show. */
internal fun plateSheetDataLine(breakdown: PlateBreakdown): List<DataPart> = buildList {
    add(data(formatPlateNumber(breakdown.barLb)))
    add(filler(" lb bar"))
    when {
        breakdown.underBar -> {
            add(filler(" · "))
            add(data(formatPlateNumber(breakdown.totalLb)))
            add(filler(" lb is under the bar"))
        }
        breakdown.perSide.isEmpty() -> add(filler(" only"))
        breakdown.shortPerSideLb > 0.0 -> {
            add(filler(" · "))
            add(data(formatPlateNumber(breakdown.shortPerSideLb)))
            add(filler(" lb a side needs micro plates"))
        }
    }
}

private fun replacementCandidate(previous: String, candidate: String): String {
    if (previous.isBlank() || candidate == previous) return candidate
    val inserted = when {
        candidate.startsWith(previous) -> candidate.removePrefix(previous)
        candidate.endsWith(previous) -> candidate.removeSuffix(previous)
        candidate.contains(previous) -> candidate.replaceFirst(previous, "")
        else -> candidate
    }
    return normalizeReplacementCandidate(inserted)
}

private fun followUpReplacementCandidate(previous: String, candidate: String): String {
    if (previous.isBlank()) return candidate
    val normalized = normalizeReplacementCandidate(candidate)
    if (normalized == candidate) return candidate
    val trimmedNormalized = normalized.trimStart('0')
    return if (
        normalized.startsWith(previous) ||
        trimmedNormalized.startsWith(previous)
    ) {
        normalized
    } else {
        candidate
    }
}

private fun normalizeReplacementCandidate(candidate: String): String {
    // Some Android decimal keyboards report a whole-text replacement as "old.0N"
    // when driven by synthetic input. Treat that as the new integer the user typed.
    return if (
        candidate.startsWith(".0") &&
        candidate.drop(2).isNotBlank() &&
        candidate.drop(2).all { it.isDigit() }
    ) {
        candidate.drop(2)
    } else {
        candidate
    }
}

private fun sanitizeNumberInput(input: String, allowDecimal: Boolean): String {
    val builder = StringBuilder()
    var sawDot = false
    input.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            allowDecimal && char == '.' && !sawDot -> {
                builder.append(char)
                sawDot = true
            }
        }
    }
    return builder.toString()
}

private fun formatRestDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) formatMinutesSeconds(minutes, remainingSeconds) else "${remainingSeconds}s"
}

fun formatVolume(v: Int): String = if (v >= 1000) "${v / 1000}.${(v % 1000) / 100}k" else "$v"

private fun formatMinutesSeconds(minutes: Int, seconds: Int): String =
    "$minutes:${seconds.toString().padStart(2, '0')}"

@Composable
private fun StatPill(value: String, label: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF171A1C)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF66706E),
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun AnimatedStatPill(targetValue: Int, label: String, useVolumeFormat: Boolean = false) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (useVolumeFormat) {
            AnimatedVolumeCounter(
                targetValue = targetValue,
                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF171A1C)),
                fontWeight = FontWeight.Bold,
            )
        } else {
            AnimatedCounter(
                targetValue = targetValue,
                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF171A1C)),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF66706E),
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun ExerciseResolutionPrompt(
    pending: PendingExerciseResolutionUi,
    onSame: () -> Unit,
    onDifferent: () -> Unit,
    palette: LogGlassPalette,
) {
    val firstName = pending.selectedName ?: pending.typedName
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.glassFillStrong),
        border = BorderStroke(1.dp, palette.accentStrong.copy(alpha = 0.34f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EspressoEyebrow("Same lift?")
                DataText(
                    parts = listOf(data(firstName), filler(" · "), data(pending.candidateName)),
                    size = 14.sp,
                    filler = palette.inkMuted,
                    data = palette.ink,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSame,
                    enabled = !pending.isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentStrong,
                        contentColor = palette.pageTop,
                    ),
                ) {
                    if (pending.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = palette.pageTop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Yes, same")
                }
                TextButton(onClick = onDifferent, enabled = !pending.isBusy) {
                    Text("Different", color = palette.ink)
                }
            }
        }
    }
}

/** How many of a split's lifts the empty day lists before folding the rest into "+N more". */
internal const val LOG_EMPTY_LIFT_ROWS = 5

/** Lifts shown in full, and how many fold into "+N more" (never a lone "+1 more"). */
internal fun logEmptyLiftRows(count: Int): Pair<Int, Int> =
    if (count <= LOG_EMPTY_LIFT_ROWS + 1) count to 0 else LOG_EMPTY_LIFT_ROWS to count - LOG_EMPTY_LIFT_ROWS

/** "Wed, Sep 23": how Log copy names a day. */
internal fun logRepeatDateLabel(iso: String): String =
    runCatching { espressoShortDate(LocalDate.parse(iso)) }.getOrDefault(iso)

/** A new account: nothing to start, so one line and a drawn arrow up to the clipboard. */
@Composable
internal fun LogEmptyFirstLift() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
    ) {
        Canvas(Modifier.size(width = 18.dp, height = 34.dp)) {
            val color = EspressoPalette.faint
            val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val x = size.width / 2f
            drawLine(
                color, Offset(x, 2.dp.toPx()), Offset(x, size.height),
                strokeWidth = stroke.width, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
            )
            val head = Path().apply {
                moveTo(x - 6.dp.toPx(), 8.dp.toPx())
                lineTo(x, 2.dp.toPx())
                lineTo(x + 6.dp.toPx(), 8.dp.toPx())
            }
            drawPath(head, color, style = stroke)
        }
        Spacer(Modifier.height(12.dp))
        EspressoEyebrow("No lifts")
    }
}

@Composable
private fun LogCalendarSheet(
    selectedDate: LocalDate,
    workedDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette
) {
    val today = remember { todayLocalDate() }
    var visibleMonth by remember(selectedDate) { mutableStateOf(YearMonthW.from(selectedDate)) }
    val weekLabels = remember { listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") }
    val firstDayOfMonth = visibleMonth.atDay(1)
    val leadingDays = firstDayOfMonth.dayOfWeek.isoDayNumber % 7
    val gridStart = firstDayOfMonth.minusDays(leadingDays.toLong())
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(sheetShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.glassFillStrong.copy(alpha = 0.98f),
                        palette.pageBottom.copy(alpha = 0.96f),
                        palette.pageTop.copy(alpha = 0.94f)
                    )
                ),
                sheetShape
            )
            .border(1.dp, palette.glassStrokeStrong, sheetShape)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = palette.ink
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = visibleMonth.label(),
                    style = LogType.dateTitle,
                    color = palette.ink
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onDateSelected(today) }) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentStrong
                        )
                    }
                    TextButton(onClick = {
                        onRefresh()
                        onClose()
                    }) {
                        Text(
                            text = "Refresh",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentStrong
                        )
                    }
                }
            }

            IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = palette.ink
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.inkSubtle,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(6) { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(7) { day ->
                        val date = gridStart.plusDays((week * 7 + day).toLong())
                        LogCalendarDay(
                            date = date,
                            selectedDate = selectedDate,
                            today = today,
                            isInVisibleMonth = YearMonthW.from(date) == visibleMonth,
                            isWorkedDay = workedDays.contains(date),
                            onDateSelected = onDateSelected,
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "Close",
                color = palette.inkSubtle,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LogCalendarDay(
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    isInVisibleMonth: Boolean,
    isWorkedDay: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    val isSelected = date == selectedDate
    val isToday = date == today

    val contentColor = when {
        isSelected -> Color.White
        isInVisibleMonth -> palette.ink
        else -> palette.inkSubtle.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = { onDateSelected(date) }),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.accentStrong)
            )
        } else if (isToday) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, palette.glassStrokeStrong, CircleShape)
            )
        }

        Text(
            text = date.day.toString(),
            style = LogType.calendarDay,
            fontWeight = if (isSelected || isToday || isWorkedDay) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center
        )

        if (isWorkedDay && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(palette.accentStrong)
            )
        }
    }
}

/**
 * Loading a split over a day that has sets, or clearing it, removes those sets, so it asks first.
 * The sheet says what goes and from which day; Cancel keeps everything.
 */
@Composable
private fun ConfirmSplitSwitchSheet(
    pending: PendingSplitSwitchConfirmation,
    onCancel: (Long) -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val isClear = pending.request.target == SplitSwitchTarget.Clear
    val requestId = pending.request.requestId
    EspressoConfirmSheet(
        title = if (isClear) "Clear workout?" else "Load ${pending.targetLabel}?",
        consequence = splitSwitchConsequence(pending.affectedSetCount, pending.request.date),
        confirmLabel = if (isClear) "Clear" else "Load",
        tone = ConfirmTone.Destructive,
        onConfirm = { onConfirm(requestId) },
        onDismiss = { onCancel(requestId) },
    )
}

/** "3 sets from Wed, Sep 23 will be removed." with the count and the day as data. */
internal fun splitSwitchConsequence(affectedSetCount: Int, isoDate: String): List<DataPart> = listOf(
    data("$affectedSetCount"),
    filler(if (affectedSetCount == 1) " set from " else " sets from "),
    data(logRepeatDateLabel(isoDate)),
    filler(" will be removed."),
)

/**
 * A set or exercise removal staged behind the Undo pill. [refs] are the rows hidden while it
 * stands; [signature] is what the day looked like right after staging it, so any further change
 * (a set logged, another removal, a date change...) is detected and commits this one first.
 */
@Immutable
private data class RemovalOffer(
    val key: Int,
    val copy: DayUndoCopy,
    val refs: Set<PendingSetDeletionRef>,
    val signature: List<String>,
)

private fun saveDeletionRefs(refs: Set<PendingSetDeletionRef>): List<Any> = refs.flatMap {
    listOf(it.originalId, it.syncId, it.exerciseId, it.setNumber)
}

private fun restoreDeletionRefs(values: List<Any>): Set<PendingSetDeletionRef> = values.chunked(4).map {
    PendingSetDeletionRef(it[0] as Long, it[1] as String, it[2] as Long, it[3] as Int)
}.toSet()

private val PendingDeletionRefsSaver = listSaver<Set<PendingSetDeletionRef>, Any>(
    save = { saveDeletionRefs(it) },
    restore = { restoreDeletionRefs(it) },
)

// Save only scalar data; a report push must not commit or silently undo a staged removal.
private val RemovalOfferSaver = listSaver<RemovalOffer?, Any>(
    save = { offer ->
        if (offer == null) emptyList() else listOf(
            offer.key,
            offer.copy.headline.flatMap { listOf(it.text, it.isData) },
            offer.copy.verb,
            offer.copy.detail.flatMap { listOf(it.text, it.isData) },
            when (offer.copy.tint) {
                is DayUndoTint.Split -> "split"
                is DayUndoTint.Lift -> "lift"
                DayUndoTint.Ink -> "ink"
            },
            when (val tint = offer.copy.tint) {
                is DayUndoTint.Split -> tint.slotId
                is DayUndoTint.Lift -> tint.exerciseId
                DayUndoTint.Ink -> 0L
            },
            saveDeletionRefs(offer.refs),
            offer.signature,
        )
    },
    restore = { values ->
        if (values.isEmpty()) null else {
            val headline = (values[1] as List<*>).chunked(2).map { DataPart(it[0] as String, it[1] as Boolean) }
            val detail = (values[3] as List<*>).chunked(2).map { DataPart(it[0] as String, it[1] as Boolean) }
            val tint = when (values[4]) {
                "split" -> DayUndoTint.Split(values[5] as Long)
                "lift" -> DayUndoTint.Lift(values[5] as Long)
                else -> DayUndoTint.Ink
            }
            RemovalOffer(
                values[0] as Int,
                DayUndoCopy(headline, values[2] as String, detail, tint),
                restoreDeletionRefs((values[6] as List<*>).map { requireNotNull(it) }),
                (values[7] as List<*>).map { it as String },
            )
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LogScreen(
    initialExerciseId: Long? = null,
    onReturnToProgress: (() -> Unit)? = null,
    onOpenExerciseProgress: (Long, Int) -> Unit = { _, _ -> },
    isReportOpen: () -> Boolean = { false },
    currentDate: String,
    dateLabel: String,
    cycleSlotLabel: String?,
    selectedSplitId: Long?,
    exercises: List<ExerciseLog>,
    searchQuery: String,
    searchResults: List<ExerciseSearchResult>,
    pendingExerciseResolution: PendingExerciseResolutionUi?,
    clipboardLifts: List<ClipboardLiftUi>,
    clipboardPrograms: List<ClipboardProgramUi>,
    exerciseSwap: com.ayman.ecolift.ui.viewmodel.ExerciseSwapUi? = null,
    exerciseSwapUndo: com.ayman.ecolift.ui.viewmodel.ExerciseSwapUndoUi? = null,
    swapUndoBusy: Boolean = false,
    onOpenExerciseSwap: (Long) -> Unit = {},
    onSwapQuery: (String) -> Unit = {},
    onSearchSwapLibrary: (Long, Long?, String) -> List<com.ayman.ecolift.ui.viewmodel.SwapCandidateUi> = { _, _, _ -> emptyList() },
    onDismissExerciseSwap: () -> Unit = {},
    onSwapExercise: (Long, (Long, String?) -> Unit) -> Unit = { _, _ -> },
    onUndoExerciseSwap: ((Long, String?) -> Unit) -> Unit = {},
    chosenEmptyDaySlot: Map<String, Long> = emptyMap(),
    onChooseEmptyDaySlot: (String, Long) -> Unit = { _, _ -> },
    putAwayEmptyDay: Set<String> = emptySet(),
    onPutAwayEmptyDay: (String) -> Unit = {},
    onBringBackEmptyDay: (String) -> Unit = {},
    onCheckClipboardPick: suspend (ClipboardPick) -> ClipboardCheck,
    onAddClipboardPicks: (List<ClipboardPickRequest>, (Long, String) -> Unit) -> Unit,
    onClipProgram: (Long, Set<Long>) -> Long?,
    ticketInclusion: Map<String, Set<Long>> = emptyMap(),
    emptyDayLedger: com.ayman.ecolift.ui.viewmodel.EmptyDayLedgerUi = com.ayman.ecolift.ui.viewmodel.EmptyDayLedgerUi(),
    onSwapTicketRow: (ClipboardProgramUi, Long, Long) -> Unit = { _, _, _ -> },
    onDropTicketRow: (ClipboardProgramUi, Long) -> Unit = { _, _ -> },
    onUndoTicketSwap: (ClipboardProgramUi, Long) -> Unit = { _, _ -> },
    onToggleTicketRow: (ClipboardProgramUi, Long) -> Unit = { _, _ -> },
    onStartProposal: (ClipboardProgramUi) -> Long? = { null },
    onRenameProposal: (Long, String) -> Unit = { _, _ -> },
    onRetireSplit: (Long) -> Unit = {},
    totalSets: Int,
    totalVolumeLbs: Int,
    workedDays: Set<LocalDate> = emptySet(),
    onDateSelected: (String) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    pendingSplitSwitch: PendingSplitSwitchConfirmation?,
    onClearSplit: () -> Long?,
    onConfirmSplitSwitch: (Long) -> Unit,
    onCancelSplitSwitch: (Long) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onConfirmExercisesSame: (Long) -> Unit,
    onConfirmExercisesDifferent: (Long) -> Unit,
    onAddSet: (Long) -> Unit,
    onAddSetFrom: (Long, Long) -> Unit,
    onCompleteSet: (Long, () -> Unit) -> Unit,
    onWeightChange: (Long, String) -> Unit,
    onWeightStep: (Long, Int) -> Unit,
    onRepsChange: (Long, String) -> Unit,
    onRepsStep: (Long, Int) -> Unit,
    onToggleBodyweight: (Long) -> Unit,
    onToggleWarmup: (Long) -> Unit = {},
    onSetFocused: (Long) -> Unit,
    onFinishExercise: (Long) -> Unit,
    onCardDisplayModeChange: (String, WorkoutCardDisplayMode) -> Unit,
    onMuscleGroupChange: (Long, String) -> Unit,
    restTimerSeconds: State<Int?>,
    onCancelRestTimer: () -> Unit,
    restExerciseId: Long? = null,
    onRecommend: (Long) -> Unit = {},
    onAssistSuggestionConsumed: (setId: Long) -> Unit = {},
    onCheckInput: (setId: Long, exerciseId: Long, weightStored: Int?, reps: Int?, isBodyweight: Boolean) -> Unit = { _, _, _, _, _ -> },
    onDismissInputWarning: (setId: Long) -> Unit = {},
    onRefresh: () -> Unit = {},
    logGestureHintSessions: Int = 0,
    onGestureHintShown: (String) -> Unit = {},
    uiMessage: String? = null,
    onUiMessageShown: () -> Unit = {},
    dayUndo: DayUndoState? = null,
    onUndoDayChange: (Long) -> Unit = {},
    onDismissDayUndo: (Long) -> Unit = {},
    onDeleteSets: (List<Long>, (LocalCommit<Unit>) -> Unit) -> Unit = { _, _ -> },
    chromeReveal: ChromeRevealState = ChromeRevealState(),
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    palette: LogGlassPalette = paletteChoice.palette(),
    modifier: Modifier = Modifier
) {
    val pendingSetDeletionRefsState = rememberSaveable(stateSaver = PendingDeletionRefsSaver) {
        mutableStateOf(emptySet<PendingSetDeletionRef>())
    }
    var pendingSetDeletionRefs by pendingSetDeletionRefsState
    val pendingDeletedSetIds = pendingSetDeletionIds(
        refs = pendingSetDeletionRefs,
        exercises = exercises,
    )
    // A removal (set or exercise) is staged behind its own Undo offer until the next content
    // change commits it in one batch — see flushPendingDeletes() below.
    var removalOffer by rememberSaveable(stateSaver = RemovalOfferSaver) { mutableStateOf<RemovalOffer?>(null) }
    var nextRemovalKey by rememberSaveable { mutableIntStateOf(0) }
    var plateSheetWeight by rememberSaveable { mutableStateOf<String?>(null) }
    var isCalendarVisible by rememberSaveable { mutableStateOf(false) }
    var activeNumberInput by remember { mutableStateOf<NumberInputTarget?>(null) }
    var activeInlineEdit by remember { mutableStateOf<InlineSetEditTarget?>(null) }
    var activeSetActions by remember { mutableStateOf<SetActionTarget?>(null) }
    var reviewingCompletedCardId by rememberSaveable(currentDate) { mutableStateOf<String?>(null) }
    // While the clipboard is open it fills the screen, so the Log under it stays put at the top.
    var isClipboardOpen by remember { mutableStateOf(false) }
    var isPickerOpen by remember { mutableStateOf(false) }
    // The clip slot's resting bounds, written by layout and read once when a pick flies in: not state,
    // so scrolling never recomposes the screen through it.
    val slotBounds = remember { arrayOfNulls<Rect>(1) }
    var retireTarget by remember { mutableStateOf<ClipboardProgramUi?>(null) }
    var swapTarget by remember(currentDate) { mutableStateOf<Pair<String, Long>?>(null) }
    var placingCard by remember { mutableStateOf<ClipboardProgramUi?>(null) }
    val contentScale by animateFloatAsState(
        targetValue = if (isPickerOpen && placingCard == null) 0.94f else 1.0f,
        animationSpec = tween(if (placingCard != null) 200 else 300, easing = FastOutSlowInEasing),
    )
    var openedInitialExercise by rememberSaveable(currentDate, initialExerciseId) { mutableStateOf(false) }
    LaunchedEffect(exercises, initialExerciseId) {
        if (!openedInitialExercise && initialExerciseId != null) exercises.firstOrNull { it.exerciseId == initialExerciseId }?.let {
            reviewingCompletedCardId = it.cardId
            openedInitialExercise = true
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val plateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val calendarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val setActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // rememberLazyListState uses LazyListState.Saver in this back-stack entry.
    val listState = rememberLazyListState()
    var lastDisplayedDate by rememberSaveable { mutableStateOf(currentDate) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    chromeReveal.hideDistancePx = with(density) { 80.dp.toPx() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val selectedCalendarDate = remember(currentDate) {
        runCatching { LocalDate.parse(currentDate) }.getOrDefault(todayLocalDate())
    }
    val latestOnDeleteSets = rememberUpdatedState(onDeleteSets)
    val latestExercises = rememberUpdatedState(exercises)

    // Commit the staged removal (if any): one batch delete, and the refs it hid stay hidden until
    // their sets are actually gone from `exercises` (see the LaunchedEffect(exercises) below) —
    // a committed set must never flash back onto the day for a frame while the delete is in flight.
    fun flushPendingDeletes() {
        val staged = removalOffer ?: return
        removalOffer = null
        val ids = staged.refs.map { resolvePendingSetDeletionId(it, latestExercises.value) }.distinct()
        latestOnDeleteSets.value(ids) { result ->
            if (result is LocalCommit.NotCommitted) {
                pendingSetDeletionRefsState.value = pendingSetDeletionRefsState.value.filterNot { ref ->
                    staged.refs.any { samePendingSetDeletion(it, ref) }
                }.toSet()
            }
        }
    }

    // Drop a hidden ref once it is no longer staged AND its row has actually left the day — a
    // staged ref stays hidden (it is still undoable); a committing ref stays hidden until its
    // delete has actually landed. Reads `latestExercises.value` rather than the `exercises`
    // parameter directly inside the effect body — see the "illegal cast" note by flushPendingDeletes.
    LaunchedEffect(exercises) {
        val staged = removalOffer?.refs.orEmpty()
        val keep = pendingSetDeletionRefsState.value.filter { ref ->
            staged.any { samePendingSetDeletion(it, ref) } || pendingSetDeletionPresent(ref, latestExercises.value)
        }.toSet()
        if (keep != pendingSetDeletionRefsState.value) pendingSetDeletionRefsState.value = keep
    }

    LaunchedEffect(currentDate) {
        // Re-entering the same day restores its saved scroll and pending removal.
        if (lastDisplayedDate != currentDate) {
            lastDisplayedDate = currentDate
            flushPendingDeletes()
            listState.scrollToItem(0)
        }
        activeNumberInput = null
        activeInlineEdit = null
        activeSetActions = null
        chromeReveal.locked = false
        chromeReveal.snap(1f)
    }

    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        onUiMessageShown()
    }

    // A fresh bulk-action Undo (split start/load/clear/repeat/add) settles any removal still
    // standing — only one Undo offer is shown at a time.
    //
    // `latestDayUndo`/`latestVisibleSignature` below read through `rememberUpdatedState` rather
    // than capturing `dayUndo`/`visibleSignature` directly inside these LaunchedEffect bodies.
    // `DayUndoOffer` (like `ExerciseLog`'s `List`) is Compose-unstable — an unannotated data
    // class holding `List`/`Set` fields — and on this wasmJs target, capturing an unstable
    // parameter straight inside a lambda argument to a composable call (here, `LaunchedEffect`'s
    // `block`) corrupted an unrelated later `remember` slot in this same function: starting a
    // split (which is what first makes `dayUndo` non-null) crashed the very next recomposition
    // inside `animateDpAsState` for `animatedKeyboardPart`, with an "illegal cast" — reproduced
    // and bisected down to exactly this pattern. `State<T>` itself is always stable, so reading
    // `.value` from a `rememberUpdatedState` wrapper sidesteps it; `latestExercises` above already
    // used this for the same reason.
    val dayOffer = dayUndo?.offer
    val latestDayUndo = rememberUpdatedState(dayOffer)
    LaunchedEffect(dayOffer?.id) {
        if (latestDayUndo.value != null) flushPendingDeletes()
    }

    // Any other change to what the day shows (a set logged, a weight edited...) also commits a
    // standing removal — its Undo is only valid until "the next change".
    val visibleSignature = logVisibleSignature(exercises, pendingDeletedSetIds)
    val latestVisibleSignature = rememberUpdatedState(visibleSignature)
    LaunchedEffect(visibleSignature) {
        val staged = removalOffer
        if (staged != null && latestVisibleSignature.value != staged.signature) flushPendingDeletes()
    }

    // Leaving the tab (backgrounding, switching apps) commits a standing removal so it cannot be
    // silently lost or wrongly undone after the app comes back.
    val latestIsReportOpen = rememberUpdatedState(isReportOpen)
    DisposableEffect(lifecycleOwner, focusManager) {
        val observer = LifecycleEventObserver { _, event ->
            // A full-screen report is a temporary visit within this tab: preserve Undo and drafts.
            // Real tab departures and backgrounding keep their existing commit behavior.
            if ((event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) && !latestIsReportOpen.value()) {
                flushPendingDeletes()
                activeNumberInput = null
                activeInlineEdit = null
                activeSetActions = null
                plateSheetWeight = null
                isCalendarVisible = false
                focusManager.clearFocus()
                chromeReveal.locked = false
                chromeReveal.snap(1f)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (!latestIsReportOpen.value()) flushPendingDeletes()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val orderedExercises = exercises
        // An exercise card exists only as long as it has at least one set. When the last set is
        // swiped away it sits in pendingDeletedSetIds during the undo window — drop the whole card
        // immediately instead of leaving an empty "add a set" shell. Undo restores the set, which
        // re-includes the exercise and brings the card back.
        .filter { exercise -> exercise.sets.any { it.id !in pendingDeletedSetIds } }
    val dayFinished = shouldShowRotationSummary(
        selectedDate = selectedCalendarDate,
        today = todayLocalDate(),
        exercises = orderedExercises,
        pendingDeletedSetIds = pendingDeletedSetIds,
    )
    val showCompletedSummary = dayFinished && reviewingCompletedCardId == null
    val completedWorkoutNumber = rotationWorkoutNumber(workedDays, selectedCalendarDate)

    LaunchedEffect(reviewingCompletedCardId, orderedExercises) {
        val retained = retainedCorrectionReviewCardId(reviewingCompletedCardId, orderedExercises)
        if (retained != reviewingCompletedCardId) reviewingCompletedCardId = retained
    }

    fun resolveNumberTarget(target: NumberInputTarget): LoggedSet? {
        return resolveLoggedSetInteraction(
            exercises = exercises,
            setId = target.setId,
            setSyncId = target.setSyncId,
            exerciseId = target.exerciseId,
            setNumber = target.setNumber,
        )
    }

    val activeTarget = activeNumberInput
    val activeSet = activeTarget?.let(::resolveNumberTarget)
    val isNumberKeyboardVisible = activeTarget != null && activeSet != null
    // The open clipboard fills the room down to the bar, so the tab steps aside while it is up.
    val isRestRunning by remember(restTimerSeconds) {
        derivedStateOf { restTimerSeconds.value != null }
    }
    val showRestTimer = isRestRunning && !isNumberKeyboardVisible && !isClipboardOpen
    val restTimerInset = if (showRestTimer) RestTabListInset else 0.dp
    // The Log screen ends at the bottom bar's top edge, but the list runs on under the bar to the
    // screen's edge: the bar covers that strip while it is up, and when scrolling slides it away the
    // workout fills the room instead of leaving an empty band. Measured rather than assumed, so it is
    // zero wherever nothing sits below the screen (the on-screen keyboard, a report visit).
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    var underBarPx by remember { mutableIntStateOf(0) }
    val underBar = with(LocalDensity.current) { underBarPx.toDp() }
    val keyboardHeightPadding = 306.dp
    // Content padding is kept CONSTANT with respect to chrome visibility. Hiding or
    // showing the top bar / bottom nav must never remeasure or resize the list while
    // the user is mid-scroll — that relayout-per-frame is what made scrolling stutter.
    // Instead the chrome fades over the list (see the AnimatedVisibility blocks below).
    // Room for the collapsible top bar and bottom nav is always reserved; while you
    // scroll through the middle that reserved space just sits off-screen, and the bars
    // fade back in over it when you reach an edge.
    val listTopPadding = logListTopPadding(isToday = selectedCalendarDate == todayLocalDate())
    // The pill shows whichever Undo is standing: a staged removal (set or exercise) takes
    // priority over a bulk-action offer from the view model, since staging one already dismissed
    // any bulk offer (see requestDeleteSet/requestDeleteExercise above).
    val removal = removalOffer
    val pillModel: LogUndoPillModel? = when {
        removal != null -> LogUndoPillModel("rm:${removal.key}", removal.copy, undoRingColor(removal.copy.tint, exercises, clipboardPrograms))
        dayOffer != null -> LogUndoPillModel("vm:${dayOffer.id}", dayOffer.copy, undoRingColor(dayOffer.copy.tint, exercises, clipboardPrograms))
        else -> null
    }
    // Only the custom number keyboard changes the bottom inset through an animation — that's a
    // deliberate tap, never a scroll, so animating it is safe and smooth. The pill's own inset is
    // added on top unanimated: a padding animation would relayout the whole list every frame,
    // which is exactly the kind of per-exercise-removal lag this feature exists to fix.
    // Both are measured from the screen's edge, where the list now ends ([underBar]).
    val targetListBottomPadding = if (isNumberKeyboardVisible) {
        keyboardHeightPadding + underBar
    } else {
        navigationBarPadding + 76.dp
    }
    val animatedKeyboardPart by animateDpAsState(
        targetValue = targetListBottomPadding,
        animationSpec = tween(durationMillis = 180),
        label = "log_list_bottom_padding"
    )
    // The pill and the rest tab float over the list like the bar does, with no strip of the
    // viewport kept free for them (Ayman, 2026-10-08: that strip read as a black box that cut the
    // card short and grew when the bar slid away). The end of the list keeps room for them, so the
    // last row can always be scrolled clear of whichever is up.
    val pillInset = if (pillModel != null) 72.dp else 0.dp
    val listBottomPadding = animatedKeyboardPart + pillInset + restTimerInset

    // As each one comes up, the card's Log row is lifted just clear of it if it would land on that
    // row (logging a set raises the tab right where the next Log button sits; starting a split puts
    // the pill over Log set 1), where a tap would hit the tab or undo instead of logging.
    val logRowSpot = remember { RotationLogRowSpot() }
    suspend fun liftLogRowClear(overlayHeight: Dp, underBarToo: Boolean = false) {
        // A card that arrives together with the overlay (starting a split) takes a few frames to place.
        var frames = 0
        while (frames < 45 && (frames < 2 || logRowSpot.bottomPx <= 0f)) {
            withFrameNanos { }
            frames++
        }
        if (logRowSpot.bottomPx <= 0f) return
        val barTop = (windowHeightPx - underBarPx).toFloat()
        val clearLine = barTop - with(density) { (overlayHeight + 8.dp).toPx() }
        val reach = if (underBarToo) Float.MAX_VALUE else barTop
        val lift = rotationLogRowLiftPx(logRowSpot.topPx, logRowSpot.bottomPx, reach, clearLine)
        if (lift > 0f) listState.animateScrollBy(lift)
    }
    var restTimerWasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showRestTimer) {
        val needsLift = showRestTimer && !restTimerWasShown
        restTimerWasShown = showRestTimer
        if (needsLift) liftLogRowClear(logOverlayHeight(pillUp = false, restUp = true))
    }
    var undoPillWasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pillModel != null) {
        val needsLift = pillModel != null && !undoPillWasShown
        undoPillWasShown = pillModel != null
        if (needsLift) liftLogRowClear(logOverlayHeight(pillUp = true, restUp = showRestTimer))
    }

    // The bars hide proportionally to movement of this list only. An exercise's inner set ledger
    // is independently scrollable and must never make the global chrome disappear.
    LaunchedEffect(listState, chromeReveal) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val deltaY = when {
                    index == previousIndex -> (previousOffset - offset).toFloat()
                    index > previousIndex -> -chromeReveal.hideDistancePx
                    else -> chromeReveal.hideDistancePx
                }
                chromeReveal.applyScrollDelta(deltaY)
                previousIndex = index
                previousOffset = offset
            }
    }

    // This effect only reacts to the start/stop of a gesture — a single boolean that
    // flips at most twice per swipe — so it never runs per-frame. On settle it snaps the
    // bars to fully shown or hidden so you never rest on a half-faded bar.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling && !chromeReveal.locked) {
                    val target = when {
                        listState.isAtTop() || listState.isAtBottom() -> 1f
                        chromeReveal.reveal >= 0.5f -> 1f
                        else -> 0f
                    }
                    chromeReveal.animateTo(target)
                }
            }
    }

    // The number fields leave a touch to the page while it is moving or has only just stopped.
    val rotationPageScroll = remember { RotationPageScroll() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { rotationPageScroll.update(it) }
    }

    fun revealChrome() {
        scope.launch { chromeReveal.animateTo(1f) }
    }

    fun closePlateSheet() {
        plateSheetWeight = null
        chromeReveal.locked = false
        revealChrome()
    }

    fun closeCalendarSheet() {
        isCalendarVisible = false
        chromeReveal.locked = false
        revealChrome()
    }

    fun openCalendarSheet() {
        activeNumberInput = null
        activeInlineEdit = null
        plateSheetWeight = null
        focusManager.clearFocus()
        chromeReveal.locked = true
        chromeReveal.snap(1f)
        isCalendarVisible = true
    }

    fun requestSplitSwitch(request: () -> Long?): Long? {
        // Commit any staged removal first, so the confirm sheet's affected-set count only counts
        // what is still actually on the day.
        flushPendingDeletes()
        activeNumberInput = null
        activeInlineEdit = null
        activeSetActions = null
        plateSheetWeight = null
        isCalendarVisible = false
        focusManager.clearFocus()
        chromeReveal.locked = false
        revealChrome()
        return request()
    }

    fun clipProgram(slotId: Long, skipped: Set<Long>) {
        requestSplitSwitch { onClipProgram(slotId, skipped) }
    }

    fun selectCalendarDate(date: LocalDate) {
        flushPendingDeletes()
        activeNumberInput = null
        activeInlineEdit = null
        plateSheetWeight = null
        activeSetActions = null
        activeInlineEdit = null
        focusManager.clearFocus()
        isCalendarVisible = false
        onDateSelected(date.toString())
        chromeReveal.locked = false
        revealChrome()
    }

    fun goToPreviousDay() {
        flushPendingDeletes()
        onPreviousDay()
    }

    fun goToNextDay() {
        flushPendingDeletes()
        onNextDay()
    }

    fun commitNumberInput(target: NumberInputTarget, rawValue: String) {
        if (DemoGuard.blockIfDemo()) return
        val set = resolveNumberTarget(target) ?: return
        val resolvedTarget = target.copy(
            setId = set.id,
            setSyncId = set.syncId,
            exerciseId = set.exerciseId,
            setNumber = set.setNumber,
        )
        val allowDecimal = target.kind == NumberInputKind.Weight
        val maxLength = if (allowDecimal) 6 else 3
        val nextValue = sanitizeNumberInput(rawValue, allowDecimal).take(maxLength)
        activeNumberInput = resolvedTarget.copy(value = nextValue, replaceOnNextKey = false)
        when (target.kind) {
            NumberInputKind.Weight -> onWeightChange(set.id, nextValue)
            NumberInputKind.Reps -> onRepsChange(set.id, nextValue)
        }
        // Trigger sanity-check after each commit (advisory, never blocks).
        val currentWeight = if (target.kind == NumberInputKind.Weight) {
            nextValue
        } else {
            set.suggestedWeight ?: set.weight
        }
        val currentReps = if (target.kind == NumberInputKind.Reps) {
            nextValue
        } else {
            set.suggestedReps ?: set.reps
        }
        onCheckInput(
            set.id,
            set.exerciseId,
            WeightLbs.parseInputToStorage(currentWeight),
            currentReps.filter { it.isDigit() }.toIntOrNull(),
            set.isBodyweight,
        )
    }

    fun activateNumberInput(setId: Long, kind: NumberInputKind) {
        val set = exercises.asSequence()
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.id == setId } ?: return
        if (DemoGuard.blockIfDemo()) return
        activeSetActions = null
        activeInlineEdit = null
        focusManager.clearFocus()
        chromeReveal.locked = true
        scope.launch { chromeReveal.animateTo(0f) }
        onSetFocused(setId)
        activeNumberInput = NumberInputTarget(
            setId = setId,
            setSyncId = set.syncId,
            exerciseId = set.exerciseId,
            setNumber = set.setNumber,
            kind = kind,
            value = if (kind == NumberInputKind.Weight) {
                set.suggestedWeight ?: set.weight
            } else {
                set.suggestedReps ?: set.reps
            },
            replaceOnNextKey = true,
        )
    }

    fun appendNumberInput(rawKey: String) {
        val target = activeNumberInput ?: return
        val allowDecimal = target.kind == NumberInputKind.Weight
        if (rawKey == "." && (!allowDecimal || target.value.contains("."))) return
        val candidate = when {
            target.replaceOnNextKey -> rawKey
            target.value == "0" && rawKey != "." -> rawKey
            else -> target.value + rawKey
        }
        commitNumberInput(target, candidate)
    }

    fun switchNumberInputField() {
        val target = activeNumberInput ?: return
        val set = resolveNumberTarget(target) ?: return
        val nextKind = if (target.kind == NumberInputKind.Weight) NumberInputKind.Reps else NumberInputKind.Weight
        activeNumberInput = target.copy(
            setId = set.id,
            setSyncId = set.syncId,
            exerciseId = set.exerciseId,
            setNumber = set.setNumber,
            kind = nextKind,
            value = if (nextKind == NumberInputKind.Weight) {
                set.suggestedWeight ?: set.weight
            } else {
                set.suggestedReps ?: set.reps
            },
            replaceOnNextKey = true,
        )
        onSetFocused(set.id)
    }

    fun toggleInlineEdit(setId: Long, kind: NumberInputKind) {
        if (DemoGuard.blockIfDemo()) return
        activeNumberInput = null
        activeSetActions = null
        focusManager.clearFocus()
        chromeReveal.locked = false
        onSetFocused(setId)
        activeInlineEdit = if (activeInlineEdit?.setId == setId && activeInlineEdit?.kind == kind) {
            null
        } else {
            InlineSetEditTarget(setId, kind)
        }
    }

    fun requestDeleteSet(set: LoggedSet) {
        val ref = pendingSetDeletionRef(set)
        if (pendingSetDeletionRefsState.value.any { samePendingSetDeletion(it, ref) }) return
        if (DemoGuard.blockIfDemo()) return
        val refs = setOf(ref)
        val owner = exercises.first { ex -> ex.sets.any { it.id == set.id } }
        val copy = setRemovedCopy(set.exerciseId, owner.exerciseName, rotationSetOrdinal(owner.sets, set), set.isWarmup)
        activeSetActions = null; activeNumberInput = null; activeInlineEdit = null
        flushPendingDeletes()                                   // one Undo at a time
        dayOffer?.let { onDismissDayUndo(it.id) }                // a removal replaces a bulk-action Undo
        val hidden = pendingSetDeletionRefsState.value + refs
        pendingSetDeletionRefsState.value = hidden
        removalOffer = RemovalOffer(
            key = ++nextRemovalKey,
            copy = copy,
            refs = refs,
            signature = logVisibleSignature(latestExercises.value, pendingSetDeletionIds(hidden, latestExercises.value)),
        )
    }

    // Swipe-left to remove the whole card: stage every set into the same pending-delete window that
    // single-set deletes use, so the existing "card disappears when it has no sets" filter drops it,
    // and the Undo pill restores them all. Removed (deleted) sets leave history entirely, so they
    // never seed a future session — which is exactly the "don't reference a removed card" behaviour.
    fun requestDeleteExercise(exerciseName: String, sets: List<LoggedSet>) {
        val refs = mutableListOf<PendingSetDeletionRef>()
        sets.forEach { set ->
            val ref = pendingSetDeletionRef(set)
            val alreadyPending = pendingSetDeletionRefsState.value.any {
                samePendingSetDeletion(it, ref)
            }
            val alreadyIncluded = refs.any { samePendingSetDeletion(it, ref) }
            if (!alreadyPending && !alreadyIncluded) refs += ref
        }
        if (refs.isEmpty()) return
        if (DemoGuard.blockIfDemo()) return
        val copy = liftRemovedCopy(refs.first().exerciseId, exerciseName, sets.size, sets.count { it.isCompleted })
        activeSetActions = null; activeNumberInput = null; activeInlineEdit = null
        flushPendingDeletes()                                   // one Undo at a time
        dayOffer?.let { onDismissDayUndo(it.id) }                // a removal replaces a bulk-action Undo
        val hidden = pendingSetDeletionRefsState.value + refs
        pendingSetDeletionRefsState.value = hidden
        removalOffer = RemovalOffer(
            key = ++nextRemovalKey,
            copy = copy,
            refs = refs.toSet(),
            signature = logVisibleSignature(latestExercises.value, pendingSetDeletionIds(hidden, latestExercises.value)),
        )
    }

    LaunchedEffect(activeTarget, activeSet?.id, activeSet?.syncId) {
        val target = activeTarget ?: return@LaunchedEffect
        val resolved = activeSet
        if (resolved == null) {
            activeNumberInput = null
            chromeReveal.locked = false
            revealChrome()
        } else if (target.setId != resolved.id || target.setSyncId != resolved.syncId) {
            activeNumberInput = target.copy(
                setId = resolved.id,
                setSyncId = resolved.syncId,
                exerciseId = resolved.exerciseId,
                setNumber = resolved.setNumber,
            )
        }
    }

    // The pager tracks the card it is showing by key, not by raw page index (see the note by
    // `RotationExercisePager`'s `focusExerciseId` parameter), so undoing a removal can leave the
    // pager on whatever card slid into the removed one's place instead of the card Undo just
    // brought back. `pendingPagerFocusExerciseId` names which exercise to show; `pagerFocusRequest`
    // is a plain counter that only ever goes up, so the request is never "consumed" by writing
    // back to the same value the pager's effect is keyed on — nothing here needs it reset to null.
    // The clipboard can push the pager out of the lazy viewport. Its set selection belongs
    // to the day, so opening and cancelling search must not discard it with the lazy item.
    val rotationSetSelections = rememberSaveable(currentDate, saver = RotationSetSelectionsSaver) { mutableStateMapOf<String, RotationSetSelection>() }
    var pendingPagerFocusExerciseId by remember(currentDate) { mutableStateOf<Long?>(null) }
    var pendingPagerFocusSetSyncId by remember(currentDate) { mutableStateOf<String?>(null) }
    var pagerFocusRequest by remember { mutableIntStateOf(0) }

    fun undoRemoval() {
        val staged = removalOffer
        if (staged != null) {
            removalOffer = null
            pendingSetDeletionRefsState.value = pendingSetDeletionRefsState.value
                .filterNot { ref -> staged.refs.any { samePendingSetDeletion(it, ref) } }.toSet()
            pendingPagerFocusExerciseId = staged.refs.firstOrNull()?.exerciseId
            pendingPagerFocusSetSyncId = null
            pagerFocusRequest++
        }
    }
    // Held for the 700 ms "Undone" beat: the pill keeps showing what was just undone instead of
    // instantly jumping to whatever offer (if any) replaces it.
    //
    // Keyed on `undoneEpoch` (an Int that only ever counts up), not on `undoneModel` itself: a
    // `LaunchedEffect(undoneModel) { delay(...); undoneModel = null }` — the effect's own key
    // variable written from inside its own body — reliably froze the whole screen's recomposition
    // on this wasmJs target the moment the delay elapsed (confirmed by instrumentation: the
    // composable that reads `undoneModel` recomposed exactly once more with the correct "Undone"
    // state and then never again, with no thrown error). `undoneEpoch` never gets written by this
    // effect, so it can never cause its own restart.
    var undoneModel by remember { mutableStateOf<LogUndoPillModel?>(null) }
    var undoneEpoch by remember { mutableIntStateOf(0) }
    var restoredOfferId by remember { mutableStateOf<Long?>(null) }
    val hasUndoneModel = undoneModel != null
    val latestUndoState = rememberUpdatedState(dayUndo)
    LaunchedEffect(dayUndo) {
        val restored = latestUndoState.value as? DayUndoState.Restored ?: return@LaunchedEffect
        if (restoredOfferId != restored.offer.id) {
            restoredOfferId = restored.offer.id
            undoneModel = LogUndoPillModel("vm:${restored.offer.id}", restored.offer.copy,
                undoRingColor(restored.offer.copy.tint, latestExercises.value, clipboardPrograms))
            undoneEpoch++
        }
    }
    LaunchedEffect(undoneEpoch) {
        if (hasUndoneModel) {
            val completedOfferId = restoredOfferId
            delay(LOG_UNDO_UNDONE_MS)
            undoneModel = null
            completedOfferId?.let(onDismissDayUndo)
        }
    }
    // The 700 ms "Undone" pill covers the same spot as an offer, so the strip lift clears whichever is drawn.
    LaunchedEffect(logRowSpot.stripArrivals) {
        val pillUp = (undoneModel ?: pillModel) != null && activeTarget == null && !isClipboardOpen
        if (logRowSpot.stripArrivals > 0) liftLogRowClear(logOverlayHeight(pillUp, showRestTimer), underBarToo = true)
    }
    fun undoCurrent() {
        val model = pillModel ?: return
        if (undoneModel != null) return
        if (removalOffer != null) {
            undoneModel = model
            undoneEpoch++
            undoRemoval()
        } else if (dayUndo is DayUndoState.Offered) {
            onUndoDayChange(dayUndo.offer.id)
        }
    }

    MaterialTheme(typography = LogMaterialTypography) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    underBarPx = (windowHeightPx - coordinates.boundsInWindow().bottom).roundToInt().coerceAtLeast(0)
                }
        ) {
            // The open clipboard fills the room between the masthead and the bottom nav.
            val clipboardSheetHeight = (
                maxHeight - statusBarPadding - listTopPadding - (navigationBarPadding + 76.dp) - 8.dp
            ).coerceIn(360.dp, 660.dp)
            // Focusing the clipboard's field asks the list to bring it into view, which would slide
            // the sheet under the masthead; the list is held at the top instead.
            LaunchedEffect(isClipboardOpen) {
                if (!isClipboardOpen) return@LaunchedEffect
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .collect { (index, offset) -> if (index != 0 || offset != 0) listState.scrollToItem(0) }
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = !isClipboardOpen && !isPickerOpen,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarPadding)
                    .extendBelow(underBarPx)
                    .imePadding()
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                        transformOrigin = TransformOrigin(0.5f, 0.4f)
                    },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = listTopPadding,
                    end = 16.dp,
                    bottom = listBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (onReturnToProgress != null) {
                item(key = "return_to_progress") {
                    TextButton(onClick = onReturnToProgress, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text("‹ Back to Progress", color = palette.accentStrong)
                    }
                }
            }
            // The clipboard owns one stable slot: search, the lifts to tick and the programs to clip
            // on. Opening it never removes it or the workout underneath from composition.
            item(key = "clipboard", contentType = "clipboard") {
                val showPrograms = !showCompletedSummary && reviewingCompletedCardId == null
                // The stamp names the proposal until a set is checked; after that the day's group
                // shows as the stub (selectedSplitId), and a checked day with no group has no stamp.
                // While the Up next ticket is on screen (the empty_day item below) it names the split,
                // so the stamp steps aside.
                val dayHasCheckedSet = orderedExercises.any { exercise -> exercise.sets.any { it.isCompleted } }
                val ticketProgram = emptyDayProgram(clipboardPrograms, chosenEmptyDaySlot[currentDate])
                val ticketShown = orderedExercises.isEmpty() &&
                    ticketProgram != null && ticketProgram.rows.isNotEmpty() &&
                    pendingSetDeletionRefsState.value.isEmpty() &&
                    currentDate !in putAwayEmptyDay
                val stampedProgram = clipboardStamp(clipboardPrograms, chosenEmptyDaySlot[currentDate], selectedSplitId, dayHasCheckedSet, ticketShown)
                LogClipboard(
                    currentDate = currentDate,
                    lifts = clipboardLifts,
                    programs = if (showPrograms) clipboardPrograms else emptyList(),
                    loadedSlotId = if (showPrograms) selectedSplitId else null,
                    todayExercises = orderedExercises,
                    query = searchQuery,
                    results = searchResults,
                    sheetHeight = clipboardSheetHeight,
                    onQueryChange = onSearchQueryChange,
                    onCheck = onCheckClipboardPick,
                    onAdd = { requests ->
                        reviewingCompletedCardId = null
                        onAddClipboardPicks(requests) { exerciseId, setSyncId ->
                            pendingPagerFocusExerciseId = exerciseId
                            pendingPagerFocusSetSyncId = setSyncId
                            pagerFocusRequest++
                            latestExercises.value.firstOrNull { it.exerciseId == exerciseId }
                                ?.takeIf { it.displayMode == WorkoutCardDisplayMode.COMPACT }
                                ?.let { onCardDisplayModeChange(it.cardId, WorkoutCardDisplayMode.EXPANDED) }
                        }
                    },
                    onClipOn = ::clipProgram,
                    onUnclip = { requestSplitSwitch { onClearSplit() } },
                    onOpenChanged = { open ->
                        isClipboardOpen = open
                        if (open) {
                            activeNumberInput = null
                            activeInlineEdit = null
                            chromeReveal.locked = false
                        }
                        revealChrome()
                    },
                    stampedProgram = stampedProgram,
                )
            }
            if (pendingExerciseResolution != null) {
                item(key = "exercise_resolution", contentType = "exercise_resolution") {
                    ExerciseResolutionPrompt(
                        pending = pendingExerciseResolution,
                        onSame = { onConfirmExercisesSame(pendingExerciseResolution.requestId) },
                        onDifferent = { onConfirmExercisesDifferent(pendingExerciseResolution.requestId) },
                        palette = palette,
                    )
                }
            }
            // One exercise per page. Horizontal means "next lift" and nothing else; parking a
            // card drops it out of the rotation and the pager closes the gap. This replaces the
            // vertical stack of tall cards, and with it the swipe-up-to-delete on a set row.
            item(key = "rotation_pager", contentType = "rotation") {
                CompositionLocalProvider(LocalRotationPageScroll provides rotationPageScroll, LocalRotationLogRowSpot provides logRowSpot) {
                    RotationExercisePager(
                        exercises = orderedExercises,
                        selectedSetByCard = rotationSetSelections,
                        onSwapExercise = { ex ->
                            flushPendingDeletes()
                            activeNumberInput = null
                            activeInlineEdit = null
                            focusManager.clearFocus()
                            onOpenExerciseSwap(ex.exerciseId)
                        },
                        swapUndoExerciseId = exerciseSwapUndo?.targetId,
                        onUndoSwap = {
                            onUndoExerciseSwap { id, syncId ->
                                reviewingCompletedCardId = orderedExercises.firstOrNull { it.exerciseId == id }?.cardId
                                pendingPagerFocusExerciseId = id
                                pendingPagerFocusSetSyncId = syncId
                                pagerFocusRequest++
                            }
                        },
                        splitLabel = cycleSlotLabel.orEmpty(),
                        showCompletedSummary = showCompletedSummary,
                        dayFinished = dayFinished,
                        completedWorkoutNumber = completedWorkoutNumber,
                        summaryDate = selectedCalendarDate,
                        totalVolumeLbs = totalVolumeLbs,
                        reviewingCompletedCardId = reviewingCompletedCardId,
                        palette = palette,
                        pendingDeletedSetIds = pendingDeletedSetIds,
                        onWeightStep = onWeightStep,
                        onRepsStep = onRepsStep,
                        onCompleteSet = onCompleteSet,
                        onToggleWarmup = onToggleWarmup,
                        // Seed a new set from the one you just did, not from last week's set of the
                        // same number: after 2x11 today, set 3 opens at 11, not at last session's 10.
                        onAddSet = { ex, previous ->
                            if (previous != null) {
                                onAddSetFrom(ex.exerciseId, previous.id)
                            } else {
                                onAddSet(ex.exerciseId)
                            }
                        },
                        onRemoveSet = { set -> requestDeleteSet(set) },
                        onRemoveExercise = { ex ->
                            requestDeleteExercise(ex.exerciseName, ex.sets)
                        },
                        onWeightInput = { setId -> activateNumberInput(setId, NumberInputKind.Weight) },
                        onRepsInput = { setId -> activateNumberInput(setId, NumberInputKind.Reps) },
                        onRecommend = onRecommend,
                        onMuscleGroupChange = { ex, group -> onMuscleGroupChange(ex.exerciseId, group) },
                        onPark = { ex ->
                            onFinishExercise(ex.exerciseId)
                            onCardDisplayModeChange(ex.cardId, WorkoutCardDisplayMode.COMPACT)
                        },
                        onUnpark = { ex ->
                            pendingPagerFocusExerciseId = ex.exerciseId
                            pendingPagerFocusSetSyncId = null
                            pagerFocusRequest++
                            onCardDisplayModeChange(ex.cardId, WorkoutCardDisplayMode.EXPANDED)
                        },
                        onOpenCompletedExercise = { ex ->
                            reviewingCompletedCardId = ex.cardId
                            onCardDisplayModeChange(ex.cardId, WorkoutCardDisplayMode.EXPANDED)
                        },
                        onExitCompletedReview = { reviewingCompletedCardId = null },
                        onTaken = { ex ->
                            flushPendingDeletes()
                            activeNumberInput = null
                            activeInlineEdit = null
                            focusManager.clearFocus()
                            onOpenExerciseSwap(ex.exerciseId)
                        },
                        onOpenExerciseProgress = onOpenExerciseProgress,
                        onOpenPlates = { lbs ->
                            activeNumberInput = null
                            activeInlineEdit = null
                            chromeReveal.locked = true
                            plateSheetWeight = formatTrimmedDecimal(lbs.toDouble())
                        },
                        teachScrub = logGestureHintSessions == 0,
                        onScrubTaught = { onGestureHintShown(currentDate) },
                        focusExerciseId = pendingPagerFocusExerciseId,
                        focusSetSyncId = pendingPagerFocusSetSyncId,
                        focusRequest = pagerFocusRequest,
                    )
                }
            }
            if (orderedExercises.isEmpty()) {
                item(key = "empty_day", contentType = "empty_day") {
                    val program = emptyDayProgram(clipboardPrograms, chosenEmptyDaySlot[currentDate])
                    if (program == null || program.rows.isEmpty()) {
                        LogEmptyFallback(
                            programs = clipboardPrograms,
                            onStart = { chosen ->
                                reviewingCompletedCardId = null
                                requestSplitSwitch { onStartProposal(chosen) }
                            },
                        )
                    } else if (pendingSetDeletionRefsState.value.isEmpty()) {
                        LogEmptyDay(
                            program = emptyDayLedger.programs.firstOrNull { emptyDayLedger.date == currentDate && it.groupKey == program.groupKey } ?: program,
                            allPrograms = clipboardPrograms,
                            included = ticketInclusion[ticketInclusionKey(currentDate, program.groupKey)]
                                ?: defaultTicketInclusion(program),
                            shownDate = currentDate,
                            pickerOpen = isPickerOpen,
                            placingColor = placingCard?.let { splitAccentColor(it.colorIndex) },
                            putAway = currentDate in putAwayEmptyDay,
                            hangUnderClipboard = pendingExerciseResolution == null,
                            onStart = { chosen ->
                                reviewingCompletedCardId = null
                                requestSplitSwitch { onStartProposal(chosen) }
                            },
                            onToggleRow = onToggleTicketRow,
                            onSwapRow = { chosen, usualId -> swapTarget = chosen.groupKey to usualId },
                            onRenameProposal = { chosen, name -> onRenameProposal(chosen.slotId, name) },
                            onOpenPicker = { isPickerOpen = true },
                            onPutAway = { onPutAwayEmptyDay(currentDate) },
                            onBringBack = { onBringBackEmptyDay(currentDate) },
                            // Resting bounds only: the list scales while the picker is open.
                            onSlotPositioned = { rect -> if (!isPickerOpen) slotBounds[0] = rect },
                        )
                    }
                }
            }
        }

            val mastSets = logMastSetCounts(orderedExercises, pendingDeletedSetIds)
            // Always composed; the scroll-linked fade is a draw-phase graphicsLayer read
            // (no recomposition while scrolling). At reveal 0 it slides fully off the top
            // edge, so the hidden bar can't intercept taps over the list.
            LogTopBar(
                dateLabel = dateLabel,
                cycleSlotLabel = cycleSlotLabel,
                selectedDate = selectedCalendarDate,
                workedDays = workedDays,
                exerciseCount = orderedExercises.size,
                setsLogged = mastSets.first,
                setsTotal = mastSets.second,
                onDateClick = ::openCalendarSheet,
                onPreviousDay = ::goToPreviousDay,
                onNextDay = ::goToNextDay,
                onGoToToday = { selectCalendarDate(todayLocalDate()) },
                palette = palette,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val r = chromeReveal.reveal
                        alpha = r
                        translationY = -(1f - r) * size.height
                        scaleX = contentScale
                        scaleY = contentScale
                        transformOrigin = TransformOrigin(0.5f, 0.4f)
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(16.dp)
                    .fillMaxHeight()
                    .backgroundChromeGestureStrip(
                        enabled = !isNumberKeyboardVisible,
                        onRevealChrome = ::revealChrome
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(16.dp)
                    .fillMaxHeight()
                    .backgroundChromeGestureStrip(
                        enabled = !isNumberKeyboardVisible,
                        onRevealChrome = ::revealChrome
                    )
            )

            // The tab grows out of the bar's top edge (the Log screen ends there) and, when scrolling
            // slides the bar away, drops with it to the screen edge instead of fading out with it.
            val restTabColor = remember(exercises, restExerciseId) {
                restTabColor(exercises.firstOrNull { it.exerciseId == restExerciseId }?.muscleGroups)
            }
            val bottomBarPx = with(LocalDensity.current) { (AppBottomBarHeight + navigationBarPadding).toPx() }
            RestTab(
                elapsedSeconds = restTimerSeconds,
                color = restTabColor,
                visible = showRestTimer,
                onStop = onCancelRestTimer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = (1f - chromeReveal.reveal) * bottomBarPx },
            )

            // Centred above the tab bar (14 dp), or above the rest tab instead when it is showing
            // (8 dp above its 32 dp height). Rides the tab bar exactly like the rest tab above.
            //
            // This Box's own bottom edge is already the bar's TOP edge — RestTab above needs no
            // bottom padding at all for the same reason ("the Log screen ends at the bar's top
            // edge"). Adding AppBottomBarHeight + navigationBarPadding here counted the bar's
            // height a second time and pushed the pill roughly a bar's height too high, up over
            // the cards.
            val pillLift = if (showRestTimer) 32.dp + 8.dp else 14.dp
            val pillShown = (undoneModel ?: pillModel) != null && activeTarget == null && !isClipboardOpen
            val noticeClearance = LocalNoticeClearance.current
            val clearanceOwner = remember { Any() }
            val pillClearance = if (pillShown) pillLift + LogUndoPillHeight else 0.dp
            DisposableEffect(noticeClearance, pillClearance) {
                noticeClearance?.set(clearanceOwner, pillClearance)
                onDispose { noticeClearance?.set(clearanceOwner, 0.dp) }
            }
            LogUndoPill(
                model = undoneModel ?: pillModel,
                undone = undoneModel != null,
                visible = activeTarget == null && !isClipboardOpen,
                onUndo = ::undoCurrent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = pillLift)
                    .graphicsLayer { translationY = (1f - chromeReveal.reveal) * bottomBarPx },
            )

            if (activeTarget != null && activeSet != null) {
                CustomNumberKeyboard(
                    value = activeTarget.value,
                    kind = activeTarget.kind,
                    isBodyweight = activeSet.isBodyweight,
                    onKey = ::appendNumberInput,
                    onBackspace = {
                        commitNumberInput(activeTarget.copy(replaceOnNextKey = false), activeTarget.value.dropLast(1))
                    },
                    onClear = {
                        commitNumberInput(activeTarget.copy(replaceOnNextKey = false), "")
                    },
                    onToggleBodyweight = {
                        if (!DemoGuard.blockIfDemo()) {
                            val willBeBodyweight = !activeSet.isBodyweight
                            onToggleBodyweight(activeSet.id)
                            activeNumberInput = activeTarget.copy(
                                setId = activeSet.id,
                                setSyncId = activeSet.syncId,
                                exerciseId = activeSet.exerciseId,
                                setNumber = activeSet.setNumber,
                                value = when {
                                    activeTarget.kind != NumberInputKind.Weight -> activeTarget.value
                                    willBeBodyweight -> activeSet.weight
                                    else -> activeSet.weight
                                },
                                replaceOnNextKey = true,
                            )
                        }
                    },
                    onSwitchField = ::switchNumberInputField,
                    onDone = {
                        activeNumberInput = null
                        chromeReveal.locked = false
                        revealChrome()
                    },
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navigationBarPadding)
                )
            }

            ExerciseSwapSheet(
                state = exerciseSwap,
                undoBusy = swapUndoBusy,
                accent = exercises.firstOrNull { it.exerciseId == exerciseSwap?.sourceId }?.let { restTabColor(it.muscleGroups) }
                    ?: palette.accent,
                onQuery = onSwapQuery,
                onDismiss = onDismissExerciseSwap,
                onPick = { id ->
                    onSwapExercise(id) { target, syncId ->
                        reviewingCompletedCardId = orderedExercises.firstOrNull { it.exerciseId == target }?.cardId
                        pendingPagerFocusExerciseId = target
                        pendingPagerFocusSetSyncId = syncId
                        pagerFocusRequest++
                    }
                },
            )

            if (isCalendarVisible) {
                ModalBottomSheet(
                    onDismissRequest = ::closeCalendarSheet,
                    sheetState = calendarSheetState,
                    containerColor = Color.Transparent,
                    scrimColor = palette.scrim
                ) {
                    LogCalendarSheet(
                        selectedDate = selectedCalendarDate,
                        workedDays = workedDays,
                        onDateSelected = ::selectCalendarDate,
                        onRefresh = onRefresh,
                        onClose = ::closeCalendarSheet,
                        palette = palette
                    )
                }
            }

            plateSheetWeight?.let { weight ->
                ModalBottomSheet(
                    onDismissRequest = ::closePlateSheet,
                    sheetState = plateSheetState,
                    containerColor = EspressoPalette.panel,
                    contentColor = EspressoPalette.ink,
                    scrimColor = EspressoPalette.mast.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tonalElevation = 0.dp,
                    dragHandle = { EspressoGrabber() },
                ) {
                    PlateSheet(weightText = weight)
                }
            }

            activeSetActions?.let { target ->
                ModalBottomSheet(
                    onDismissRequest = { activeSetActions = null },
                    sheetState = setActionSheetState,
                    containerColor = Color.Transparent,
                    scrimColor = palette.scrim
                ) {
                    GlassSetActionSheet(
                        target = target,
                        palette = palette,
                        onComplete = {
                            if (!DemoGuard.blockIfDemo()) {
                                activeSetActions = null
                                onCompleteSet(target.set.id) {}
                            }
                        },
                        onAddCopiedSet = {
                            if (!DemoGuard.blockIfDemo()) {
                                activeSetActions = null
                                onAddSetFrom(target.exerciseId, target.set.id)
                            }
                        },
                        onDelete = {
                            requestDeleteSet(target.set)
                        },
                        modifier = Modifier.padding(bottom = navigationBarPadding + 72.dp)
                    )
                }
            }

            val ledgerTarget = swapTarget
            val ledgerProgram = clipboardPrograms.firstOrNull { it.groupKey == ledgerTarget?.first }
            val ledgerSheet = if (orderedExercises.isEmpty() && emptyDayLedger.date == currentDate)
                ledgerTarget?.let { emptyDayLedger.sheets[it] } else null
            SlotSwapSheet(
                ui = ledgerSheet,
                onDismiss = { swapTarget = null },
                onPick = { id ->
                    if (ledgerTarget != null && ledgerProgram != null && !DemoGuard.blockIfDemo()) {
                        onSwapTicketRow(ledgerProgram, ledgerTarget.second, id)
                        swapTarget = null
                    }
                },
                onSearch = { query -> onSearchSwapLibrary(ledgerTarget?.second ?: 0, ledgerProgram?.slotId, query) },
                onDrop = {
                    if (ledgerTarget != null && ledgerProgram != null && !DemoGuard.blockIfDemo()) {
                        onDropTicketRow(ledgerProgram, ledgerTarget.second); swapTarget = null
                    }
                },
                onUndo = {
                    if (ledgerTarget != null && ledgerProgram != null && !DemoGuard.blockIfDemo()) {
                        onUndoTicketSwap(ledgerProgram, ledgerTarget.second); swapTarget = null
                    }
                },
            )

            pendingSplitSwitch?.let { pending ->
                ConfirmSplitSwitchSheet(
                    pending = pending,
                    onCancel = onCancelSplitSwitch,
                    onConfirm = onConfirmSplitSwitch,
                )
            }

            if (isPickerOpen) {
                val currentProg = emptyDayProgram(clipboardPrograms, chosenEmptyDaySlot[currentDate])
                LogEmptyDayCardPickerOverlay(
                    currentProgram = currentProg,
                    programs = clipboardPrograms,
                    sheetFor = { p ->
                        emptyDayLedger.programs.firstOrNull { emptyDayLedger.date == currentDate && it.groupKey == p.groupKey } ?: p
                    },
                    includedFor = { p -> ticketInclusion[ticketInclusionKey(currentDate, p.groupKey)] ?: defaultTicketInclusion(p) },
                    slotBounds = { slotBounds[0] },
                    onClose = {
                        isPickerOpen = false
                        placingCard = null
                    },
                    onPlacingStarted = { card -> placingCard = card },
                    onPlace = { placed ->
                        onChooseEmptyDaySlot(currentDate, placed.slotId)
                    },
                    onFinished = {
                        isPickerOpen = false
                        placingCard = null
                    },
                    onRetire = { program -> retireTarget = program },
                )
            }

            // Retiring the last split from the picker leaves nothing to choose, so it closes.
            val noPrograms = clipboardPrograms.isEmpty()
            LaunchedEffect(isPickerOpen, noPrograms) {
                if (isPickerOpen && noPrograms) {
                    isPickerOpen = false
                    placingCard = null
                }
            }

            retireTarget?.let { target ->
                ConfirmRetireDialog(
                    splitName = target.name,
                    onDismiss = { retireTarget = null },
                    onConfirm = {
                        onRetireSplit(target.slotId)
                        retireTarget = null
                    },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = navigationBarPadding + 14.dp)
                    .padding(bottom = pillClearance)
            )
        }
    }
}
