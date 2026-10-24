package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.BodyPartRegionMapper
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.epleyEstimate
import com.ayman.ecolift.data.selectableBodyPartGroups
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.ui.theme.EspressoDeltaChip
import com.ayman.ecolift.ui.theme.EspressoEyebrow
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoTone
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogGlassPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.ShaderWarmup
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.espressoButtonShadow
import com.ayman.ecolift.ui.theme.rememberHeavyHaptic
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import com.ayman.ecolift.ui.viewmodel.GhostSetUi
import com.ayman.ecolift.ui.viewmodel.RecommendationState
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.formatTrimmedDecimal
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The log, one exercise per page.
 *
 * The old screen stacked every exercise of the day as a tall card in a vertical list, which meant
 * scrolling past finished work to reach the lift actually in front of you, and set rows that swiped
 * up to delete. Here the horizontal axis means "next exercise" and nothing else, and each card is
 * aimed at one number: what you did on this same set last time.
 *
 * Everything drawn here already exists in the database. `ghostSets` is last session's rows kept
 * whole instead of collapsed into a single "Last 185x8" label; the accent colour is derived from the
 * exercise's muscle region rather than stored; the e1RM figure uses the same Epley estimate the
 * progress tab already charts.
 */

/**
 * One notch of the weight wheel, in stored tenths of a pound: half a pound.
 * The wheel is for nudging around a weight you are already near — 240 to 242.5 — and the
 * keypad is for jumping somewhere else entirely. A plate-sized notch made the wheel useless
 * for the only thing it is good at.
 */
private const val WEIGHT_STEP_STORED = 5

/** Short vertical travel changes a value; the ruler also tracks motion between notches. */
internal const val ROTATION_SCRUB_DP_PER_STEP = 18

/** Font sizes the field's big number may use, largest first. */
internal val ROTATION_FIELD_VALUE_SIZES_SP = listOf(42f, 34f, 28f, 22f)

/**
 * The largest size at which the number fits the body column beside the tape. A half-pound
 * value ("187.5") is five characters but far wider than "1425", so the choice is made from the
 * measured width, not the character count. An unmeasured column (0) keeps the largest size.
 */
internal fun rotationFieldValueFontSp(widthAt: (Float) -> Float, maxWidth: Float): Float {
    if (maxWidth <= 0f) return ROTATION_FIELD_VALUE_SIZES_SP.first()
    return ROTATION_FIELD_VALUE_SIZES_SP.firstOrNull { widthAt(it) <= maxWidth }
        ?: ROTATION_FIELD_VALUE_SIZES_SP.last()
}

/** Sizes for the EST. 1RM headline, largest first. 50 is the resting size. */
internal val ROTATION_HEADLINE_SIZES_SP = listOf(50f, 42f, 36f, 30f, 26f, 22f, 18f, 15f)

/**
 * Largest headline size whose measured width fits. An unmeasured column keeps 50.
 * A value that still overflows at 15sp uses [rotationHeadlineCompact] instead of clipping digits.
 */
internal fun rotationHeadlineFontSp(widthAt: (Float) -> Float, maxWidth: Float): Float {
    if (maxWidth <= 0f) return ROTATION_HEADLINE_SIZES_SP.first()
    return ROTATION_HEADLINE_SIZES_SP.firstOrNull { widthAt(it) <= maxWidth }
        ?: ROTATION_HEADLINE_SIZES_SP.last()
}

/** "1.43M" for a value that cannot fit as digits. Values under 10,000 stay written out. */
internal fun rotationHeadlineCompact(value: Int): String {
    if (value == Int.MIN_VALUE) return value.toString()
    val negative = value < 0
    val magnitude = kotlin.math.abs(value)
    val unit = when {
        magnitude >= 1_000_000_000 -> 1_000_000_000
        magnitude >= 1_000_000 -> 1_000_000
        magnitude >= 10_000 -> 1_000
        else -> return value.toString()
    }
    val suffix = when (unit) {
        1_000_000_000 -> "B"
        1_000_000 -> "M"
        else -> "K"
    }
    val hundredths = ((magnitude.toLong() * 100L) + (unit / 2)) / unit
    val whole = hundredths / 100
    val frac = (hundredths % 100).toInt()
    val body = when {
        whole >= 100 -> whole.toString()
        whole >= 10 -> {
            val tenth = (frac + 5) / 10
            if (tenth >= 10) (whole + 1).toString() else if (tenth == 0) whole.toString() else "$whole.$tenth"
        }
        frac == 0 -> whole.toString()
        frac % 10 == 0 -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
    return (if (negative) "-" else "") + body + suffix
}

internal enum class RotationGestureAxis { UNDECIDED, VERTICAL, HORIZONTAL }

/**
 * A drag belongs to the value only when it is this much taller than it is wide: within 30° of
 * straight up or down. A thumb sweeping sideways to the next lift travels in an arc, and at 45°
 * the first few pixels of that arc were enough to take the number with it.
 */
internal const val ROTATION_VERTICAL_RATIO = 1.73f

/** Choose once after touch slop; only a steep drag stays on the value, the rest goes to the pager. */
internal fun rotationGestureAxis(dx: Float, dy: Float, touchSlop: Float): RotationGestureAxis = when {
    maxOf(abs(dx), abs(dy)) < touchSlop -> RotationGestureAxis.UNDECIDED
    abs(dy) >= abs(dx) * ROTATION_VERTICAL_RATIO -> RotationGestureAxis.VERTICAL
    else -> RotationGestureAxis.HORIZONTAL
}

/** How long a field stays in hand after a tap or after the tape comes to rest. */
internal const val ROTATION_ARMED_MS = 3000L

/** The in-hand halo: this many rings of this width, fading outward from the field's edge. */
private const val ROTATION_HALO_RINGS = 6
private const val ROTATION_HALO_RING_DP = 1.5f

/** How long the undo chip stays after the tape comes to rest. */
internal const val ROTATION_UNDO_WINDOW_MS = 4000L

/** Only a page moving right now owns a touch that could otherwise arm a still field. */
internal fun rotationPageScrollOwnsTouch(scrolling: Boolean): Boolean = scrolling

/** The Log page's current scroll state, read only when a finger lands. */
internal class RotationPageScroll {
    private var scrolling = false

    fun update(scrolling: Boolean) {
        this.scrolling = scrolling
    }

    fun ownsTouch(): Boolean = rotationPageScrollOwnsTouch(scrolling)
}

/** Only the card whose Log row is on screen counts its strip, so a composed pager neighbour never moves the list. */
internal fun rotationStripLiftsLog(foldStrip: Boolean, spotOwner: Any?, rowOwner: Any): Boolean =
    foldStrip && spotOwner === rowOwner

/**
 * One card's stay on screen. Swiping onto a card whose strip is already up lifts Log the same as a strip
 * arriving, but only once per stay, so two partly visible cards handing the spot back and forth never lift
 * again. The stay ends when the card's Log row leaves the screen or its strip goes.
 */
internal class RotationStripStay {
    var counted = false
        private set

    fun shouldLift(foldStrip: Boolean, spotOwner: Any?, rowOwner: Any): Boolean {
        if (counted || !rotationStripLiftsLog(foldStrip, spotOwner, rowOwner)) return false
        counted = true
        return true
    }

    fun end() {
        counted = false
    }
}

/** A fully clipped pager neighbour reports empty bounds at the origin, which must not claim the Log row spot. */
internal fun rotationLogRowOnScreen(bounds: Rect, windowWidthPx: Float): Boolean =
    bounds.width > 0f && bounds.height > 0f && bounds.center.x in 0f..windowWidthPx

/** Provided by the Log screen around the pager; the default never owns a touch. */
internal val LocalRotationPageScroll = staticCompositionLocalOf { RotationPageScroll() }

/**
 * Where the on-screen card's Log row is, in window pixels (0 when no card is up), so the Log screen
 * can lift it clear of the rest tab or the Undo pill as one comes up over it. Plain fields, written
 * on every placement and read only by that one-off lift, so scrolling never recomposes anything.
 * Pages swiped off to the side do not write.
 */
internal class RotationLogRowSpot {
    var topPx = 0f
    var bottomPx = 0f
    var owner: Any? = null
    /** Counts a not-saved strip coming up on the on-screen card. A short phone runs the deck under the bar, so each lifts Log clear. */
    var stripArrivals by mutableIntStateOf(0)
}

/**
 * How far the list scrolls so a Log row starting above [reachPx] ends at [clearLinePx]; 0 when it
 * already clears or starts lower.
 */
internal fun rotationLogRowLiftPx(topPx: Float, bottomPx: Float, reachPx: Float, clearLinePx: Float): Float =
    if (bottomPx > 0f && topPx < reachPx && bottomPx > clearLinePx) bottomPx - clearLinePx else 0f

/** Provided by the Log screen around the pager; the default is written to and never read. */
internal val LocalRotationLogRowSpot = staticCompositionLocalOf { RotationLogRowSpot() }

/** The value the undo chip goes back to: pounds as the field writes them, reps whole. */
internal fun rotationUndoLabel(value: Float, isReps: Boolean): String =
    if (isReps) value.roundToInt().toString() else formatTrimmedDecimal(value.toDouble())

internal data class RotationScrubProgress(
    val steps: Int,
    val remainderPx: Float,
)

/**
 * Convert vertical travel into discrete wheel notches while retaining sub-notch movement.
 * Screen y grows downward, so an upward drag produces positive steps.
 */
internal fun rotationScrubProgress(
    carryPx: Float,
    verticalDragPx: Float,
    pixelsPerStep: Float,
): RotationScrubProgress {
    require(pixelsPerStep > 0f)
    val nextCarry = carryPx - verticalDragPx
    val steps = (nextCarry / pixelsPerStep).toInt()
    return RotationScrubProgress(
        steps = steps,
        remainderPx = nextCarry - steps * pixelsPerStep,
    )
}

/** The header name may wrap once; the pager reserves the second line when it does. */
internal const val ROTATION_NAME_MAX_LINES = 2
private const val ROTATION_NAME_LINE_HEIGHT_DP = 24

/** One style for the header and for measuring how many lines the header will take. */
@Composable
internal fun rotationExerciseNameStyle(): TextStyle = TextStyle(
    fontFamily = LogDisplayFontFamily,
    fontSize = 22.sp,
    lineHeight = ROTATION_NAME_LINE_HEIGHT_DP.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.4).sp,
)

/**
 * Horizontal room the header leaves the name: 20dp side padding, 14dp safety gap, the two
 * 36dp actions and their 6dp gap, then the name's 8dp gap and the 32dp report chevron button.
 * Keep in step with the header row in [RotationCard]; this reuses its existing cached measurement.
 */
private val ROTATION_NAME_INSET_DP = 20 + 20 + 14 + 36 + 6 + 36 + 8 + 32

/** Height the pager must add so the tallest wrapped name never pushes the action row off the card. */
internal fun rotationNameExtraHeightDp(maxLineCount: Int): Int =
    (maxLineCount - 1).coerceIn(0, ROTATION_NAME_MAX_LINES - 1) * ROTATION_NAME_LINE_HEIGHT_DP

// RotationCard's fixed sections, top to bottom, for a one-line name and no last-time tab
// row. A lazy pager cannot ask its pages how tall they are, so the deck height is summed
// here: change a padding or a row height in the card, change it here too.
private const val ROTATION_AXIS_LINE_HEIGHT_DP = 12
private const val ROTATION_CARD_HEAD_DP = 12 + 20 + 4 + ROTATION_NAME_LINE_HEIGHT_DP + 12
private const val ROTATION_CARD_CHIPS_DP = 14 + 40
private const val ROTATION_CARD_TREND_DP = 16 + 124 + 4 + ROTATION_AXIS_LINE_HEIGHT_DP
private const val ROTATION_CARD_TREND_COMPACT_DP = 16 + 44
private const val ROTATION_CARD_FIELDS_DP = 16 + 104
private const val ROTATION_CARD_STRIP_DP = 12 + 6 + 60
private const val ROTATION_CARD_ACTIONS_DP = 12 + 56 + 16
// Text boxes round to whole pixels; a couple of dp of slack lands above the set strip, unseen.
private const val ROTATION_CARD_SLACK_DP = 2

internal fun rotationCardBaseHeightDp(showFullTrend: Boolean): Int =
    ROTATION_CARD_HEAD_DP +
        ROTATION_CARD_CHIPS_DP +
        (if (showFullTrend) ROTATION_CARD_TREND_DP else ROTATION_CARD_TREND_COMPACT_DP) +
        ROTATION_CARD_FIELDS_DP +
        ROTATION_CARD_STRIP_DP +
        ROTATION_CARD_ACTIONS_DP +
        ROTATION_CARD_SLACK_DP

/**
 * Epley, matching ProgressViewModel's estimate so the two screens never disagree.
 * Takes the STORED weight (tenths of a pound) and converts, like every other consumer.
 */
internal fun rotationE1rm(storedWeight: Int?, reps: Int?): Float {
    val w = WeightLbs.toLbs(storedWeight).toFloat()
    val r = (reps ?: 0).coerceAtLeast(0)
    if (r == 0) return 0f
    return epleyEstimate(w, r)
}

/** `storedWeight` is in tenths of a pound — never print it raw. */
internal fun rotationLoadLabel(storedWeight: Int?, isBodyweight: Boolean): String = when {
    isBodyweight && (storedWeight ?: 0) == 0 -> "BW"
    isBodyweight -> "BW + " + WeightLbs.formatStored(storedWeight)
    storedWeight == null -> "-"
    else -> WeightLbs.formatStored(storedWeight).ifBlank { "-" }
}

internal fun rotationSetLabel(storedWeight: Int?, reps: Int?, isBodyweight: Boolean): String =
    "${rotationLoadLabel(storedWeight, isBodyweight)}×${reps ?: 0}"

/**
 * The last-time mark label on the weight tape. `rotationLoadLabel`'s "BW + 8" is too wide for
 * the 46dp tape (see TrendField); this is the compact form: "+8" for added weight, plain "BW"
 * at zero. Non-bodyweight sets keep the ordinary stored-weight label.
 */
internal fun rotationTapeLoadLabel(storedWeight: Int?, isBodyweight: Boolean): String = when {
    isBodyweight && (storedWeight ?: 0) == 0 -> "BW"
    isBodyweight -> "+" + WeightLbs.formatStored(storedWeight)
    storedWeight == null -> "-"
    else -> WeightLbs.formatStored(storedWeight).ifBlank { "-" }
}

/** A LoggedSet carries the weight already formatted for display; get back to storage units. */
internal fun storedWeightOf(set: LoggedSet): Int? = WeightLbs.parseInputToStorage(set.weight)

internal fun storedRepsOf(set: LoggedSet): Int? = set.reps.toIntOrNull()

/** The actual stored weight of the row. Never replaced by an advisory suggestion. */
internal fun rotationDisplayedWeightOf(set: LoggedSet): Int? = storedWeightOf(set)

/** The actual stored reps of the row. Never replaced by an advisory suggestion. */
internal fun rotationDisplayedRepsOf(set: LoggedSet): Int? = storedRepsOf(set)

internal fun rotationSuggestedWeightOf(set: LoggedSet): Int? =
    set.suggestedWeight?.let(WeightLbs::parseInputToStorage)

internal fun rotationSuggestedRepsOf(set: LoggedSet): Int? =
    set.suggestedReps?.toIntOrNull()

internal fun rotationHasSuggestedWeight(set: LoggedSet): Boolean {
    if (set.isCompleted) return false
    val suggested = rotationSuggestedWeightOf(set) ?: return false
    return suggested != storedWeightOf(set)
}

internal fun rotationHasSuggestedReps(set: LoggedSet): Boolean {
    if (set.isCompleted) return false
    val suggested = rotationSuggestedRepsOf(set) ?: return false
    return suggested != storedRepsOf(set)
}

internal fun rotationHasSuggestion(set: LoggedSet): Boolean =
    rotationHasSuggestedWeight(set) || rotationHasSuggestedReps(set)

internal fun rotationWeightSuggestionLabel(set: LoggedSet): String? {
    if (!rotationHasSuggestedWeight(set)) return null
    val suggestedWeight = rotationSuggestedWeightOf(set)
    return "TRY ${rotationLoadLabel(suggestedWeight, set.isBodyweight)}"
}

internal fun rotationWeightSuggestionAccessibilityLabel(set: LoggedSet): String? {
    if (!rotationHasSuggestedWeight(set)) return null
    val suggestedWeight = rotationSuggestedWeightOf(set)
    val load = rotationLoadLabel(suggestedWeight, set.isBodyweight)
    return if (set.isBodyweight) "Suggested load $load" else "Suggested weight $load lb"
}

internal fun rotationRepsSuggestionLabel(set: LoggedSet): String? {
    if (!rotationHasSuggestedReps(set)) return null
    val suggestedReps = rotationSuggestedRepsOf(set) ?: return null
    return "TRY $suggestedReps"
}

internal fun rotationRepsSuggestionAccessibilityLabel(set: LoggedSet): String? {
    if (!rotationHasSuggestedReps(set)) return null
    val suggestedReps = rotationSuggestedRepsOf(set) ?: return null
    return "Suggested $suggestedReps reps"
}

internal fun rotationSuggestionLabel(set: LoggedSet): String? {
    val weightDiffers = rotationHasSuggestedWeight(set)
    val repsDiffers = rotationHasSuggestedReps(set)
    return when {
        weightDiffers && repsDiffers -> {
            val sWeight = rotationSuggestedWeightOf(set)
            val sReps = rotationSuggestedRepsOf(set)
            "TRY ${rotationSetLabel(sWeight, sReps, set.isBodyweight)}"
        }
        repsDiffers -> {
            val sReps = rotationSuggestedRepsOf(set)
            "TRY $sReps"
        }
        weightDiffers -> {
            val sWeight = rotationSuggestedWeightOf(set)
            "TRY ${rotationLoadLabel(sWeight, set.isBodyweight)}"
        }
        else -> null
    }
}

internal fun rotationSuggestionAccessibilityLabel(set: LoggedSet): String? {
    val weightDiffers = rotationHasSuggestedWeight(set)
    val repsDiffers = rotationHasSuggestedReps(set)
    return when {
        weightDiffers && repsDiffers -> {
            val sWeight = rotationSuggestedWeightOf(set)
            val sReps = rotationSuggestedRepsOf(set)
            "Suggested ${rotationSetLabel(sWeight, sReps, set.isBodyweight)}"
        }
        repsDiffers -> {
            val sReps = rotationSuggestedRepsOf(set)
            "Suggested $sReps reps"
        }
        weightDiffers -> {
            val sWeight = rotationSuggestedWeightOf(set)
            val load = rotationLoadLabel(sWeight, set.isBodyweight)
            if (set.isBodyweight) "Suggested load $load" else "Suggested weight $load lb"
        }
        else -> null
    }
}

/**
 * Give the selectable ledger row one authoritative description. Compose's browser semantics can
 * otherwise retain a removed child's old "Suggested"/"logged" label after the row changes state.
 */
internal fun rotationSetOrdinal(sets: List<LoggedSet>, set: LoggedSet): Int =
    sets.takeWhile { it.id != set.id }.count { it.isWarmup == set.isWarmup } + 1

internal fun rotationLedgerRowAccessibilityLabel(
    setNumber: Int,
    set: LoggedSet,
    ghost: GhostSetUi?,
): String = buildString {
    append(if (set.isWarmup) "Warm-up set " else "Set ")
    append(setNumber)
    append(", today ")
    append(rotationSetLabel(rotationDisplayedWeightOf(set), rotationDisplayedRepsOf(set), set.isBodyweight))
    ghost?.let {
        append(", last ")
        append(rotationSetLabel(it.weightLbs, it.reps, it.isBodyweight))
    }
    rotationSuggestionAccessibilityLabel(set)?.let {
        append(", ")
        append(it)
    }
    append(if (set.isCompleted) ", logged" else ", not logged")
}

/** ViewModel deltas use stored tenths of a pound; one scrub tick is half a pound. */
internal fun rotationWeightDelta(steps: Int): Int = steps * WEIGHT_STEP_STORED

/**
 * Groups are stored "CHEST·TRICEPS" (see data/MuscleGroups.kt); the first named group is the
 * primary region. Older strings with commas, slashes or plus signs still resolve.
 */
internal fun rotationPrimaryRegion(muscleGroups: String): BodyPartRegion {
    val primary = selectableBodyPartParts(muscleGroups).firstOrNull()
        ?: muscleGroups.split(',', '/', '&', '+', '·')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    return BodyPartRegionMapper.mapGroupToRegion(primary)
}

internal fun rotationRegionColor(muscleGroups: String): Color = rotationPrimaryRegion(muscleGroups).color

/** The poster appears only when every surviving exercise has at least one set and all are logged. */
internal fun rotationSummaryEligible(
    exercises: List<ExerciseLog>,
    pendingDeletedSetIds: Set<Long> = emptySet(),
): Boolean = exercises.isNotEmpty() &&
    exercises.all { exercise ->
    rotationLiftFinished(exercise.sets.filterNot { it.id in pendingDeletedSetIds })
}

/**
 * A lift is finished once something on it is logged and every working set is. A warm-up left
 * unlogged was skipped, and skipping one never holds the workout back.
 */
internal fun rotationLiftFinished(sets: List<LoggedSet>): Boolean =
    sets.any { it.isCompleted } && sets.all { it.isCompleted || it.isWarmup }

/**
 * A completion poster appears once every set left on a current or historical day is logged. A set
 * or lift removed behind Undo has already left the day, so taking away the last unfinished one
 * finishes it; Undo brings the work back and the deck with it.
 */
internal fun shouldShowRotationSummary(
    selectedDate: LocalDate,
    today: LocalDate,
    exercises: List<ExerciseLog>,
    pendingDeletedSetIds: Set<Long> = emptySet(),
    reviewingCompletedCardId: String? = null,
): Boolean =
    selectedDate <= today &&
        reviewingCompletedCardId == null &&
        rotationSummaryEligible(exercises, pendingDeletedSetIds)

/**
 * Showing the summary finishes its cards just like Done, unless the user reopened it to edit. While a
 * removal stands behind Undo the cards wait: parking one changes the day, and any change commits the
 * removal, so the Undo would be gone the moment the summary appeared.
 */
internal fun rotationCardsToFinish(
    exercises: List<ExerciseLog>,
    showCompletedSummary: Boolean,
    reviewingWholeWorkout: Boolean,
    pendingDeletedSetIds: Set<Long> = emptySet(),
): List<ExerciseLog> =
    if (showCompletedSummary && !reviewingWholeWorkout && pendingDeletedSetIds.isEmpty()) {
        exercises.filter {
            it.displayMode != com.ayman.ecolift.data.WorkoutCardDisplayMode.COMPACT &&
                rotationLiftFinished(it.sets)
        }
    } else {
        emptyList()
    }

internal data class RotationSetSelection(
    val setId: Long?,
    val setNumber: Int,
    val setSyncId: String = "",
    val pendingExistingSetIds: Set<Long> = emptySet(),
    val pendingExistingSyncIds: Set<String> = emptySet(),
)

internal val RotationSetSelectionsSaver = listSaver<SnapshotStateMap<String, RotationSetSelection>, Any>(
    save = { selections ->
        selections.flatMap { (card, selection) ->
        listOf(card, selection.setId?.toString().orEmpty(), selection.setNumber, selection.setSyncId,
            selection.pendingExistingSetIds.toList(), selection.pendingExistingSyncIds.toList())
    }
    },
    restore = { values ->
        mutableStateMapOf<String, RotationSetSelection>().apply {
        values.chunked(6).forEach {
            put(it[0] as String, RotationSetSelection(
                setId = (it[1] as String).toLongOrNull(),
                setNumber = it[2] as Int,
                setSyncId = it[3] as String,
                pendingExistingSetIds = (it[4] as List<*>).map { id -> id as Long }.toSet(),
                pendingExistingSyncIds = (it[5] as List<*>).map { id -> id as String }.toSet(),
            ))
        }
    }
    },
)

/** Keep the selected set, and the adjacent add control on the final set, inside the strip. */
internal fun rotationSetStripTarget(
    activeIndex: Int,
    setCount: Int,
    viewportPx: Int,
    currentPx: Int,
    columnPx: Float,
    addPx: Float,
): Int {
    if (activeIndex < 0 || viewportPx <= 0) return currentPx
    val start = activeIndex * columnPx
    val end = start + columnPx + if (activeIndex == setCount - 1) addPx else 0f
    return when {
        end > currentPx + viewportPx -> (end - viewportPx).roundToInt().coerceAtLeast(0)
        start < currentPx -> start.roundToInt().coerceAtLeast(0)
        else -> currentPx
    }
}

/** A late animation frame must never reclaim a set after a tap selected another row. */
internal fun rotationScrubTargetsSelection(
    visibleSets: List<LoggedSet>,
    selection: RotationSetSelection?,
    setId: Long,
): Boolean = resolvedRotationSet(visibleSets, selection)?.id == setId

/** Stable through renumbering and optimistic ID replacement, distinct for a replacement row. */
internal fun rotationFieldIdentity(set: LoggedSet?): String = when {
    set == null -> "empty"
    set.syncId.isNotBlank() -> "sync:${set.syncId}"
    else -> "local:${set.id}"
}

internal fun rotationSelectionOf(set: LoggedSet): RotationSetSelection =
    RotationSetSelection(
        setId = set.id,
        setNumber = set.setNumber,
        setSyncId = set.syncId,
    )

/** Reserve the next logical row while the optimistic insert is still in flight. */
internal fun nextRotationSetSelection(
    visibleSets: List<LoggedSet>,
    existingSets: List<LoggedSet> = visibleSets,
): RotationSetSelection =
    RotationSetSelection(
        setId = null,
        setNumber = (visibleSets.maxOfOrNull { it.setNumber } ?: 0) + 1,
        // Include rows hidden by a staged delete. If Undo restores one before this add returns,
        // that restored row is still old and cannot satisfy the pending selection.
        pendingExistingSetIds = existingSets.mapTo(mutableSetOf()) { it.id },
        pendingExistingSyncIds = existingSets
            .mapNotNullTo(mutableSetOf()) { it.syncId.takeIf(String::isNotBlank) },
    )

private fun RotationSetSelection.matches(set: LoggedSet): Boolean =
    (setSyncId.isNotBlank() && set.syncId == setSyncId) ||
        (setId != null && set.id == setId)

private fun RotationSetSelection.existedBeforePendingAdd(set: LoggedSet): Boolean =
    set.id in pendingExistingSetIds ||
        (set.syncId.isNotBlank() && set.syncId in pendingExistingSyncIds)

/** Sync ID survives server remapping; set number remains the compatibility fallback. */
internal fun resolvedRotationSet(
    visibleSets: List<LoggedSet>,
    selection: RotationSetSelection?,
): LoggedSet? {
    if (selection == null) {
        return visibleSets.firstOrNull { !it.isCompleted } ?: visibleSets.lastOrNull()
    }

    if (selection.setId == null) {
        // A pending insert must resolve to a row that did not exist when ADD SET was tapped. This
        // matters when the user undoes a staged deletion while the insert is in flight: an older
        // row can move into the reserved set number, but it must never steal the scrub controls.
        val newRows = visibleSets.filterNot(selection::existedBeforePendingAdd)
        return newRows.firstOrNull { it.setNumber == selection.setNumber }
            ?: if (
                selection.pendingExistingSetIds.isNotEmpty() ||
                selection.pendingExistingSyncIds.isNotEmpty()
            ) {
                newRows.singleOrNull()
            } else {
                null
            }
    }

    visibleSets.firstOrNull(selection::matches)?.let { return it }
    visibleSets.firstOrNull { it.setNumber == selection.setNumber }?.let { return it }
    return visibleSets.firstOrNull { !it.isCompleted } ?: visibleSets.lastOrNull()
}

/** Compatibility overload used by focused regression tests and non-composable callers. */
internal fun resolvedRotationSet(
    visibleSets: List<LoggedSet>,
    preferredSetNumber: Int?,
    preferredSetId: Long? = null,
    preferredSetSyncId: String = "",
): LoggedSet? = resolvedRotationSet(
    visibleSets = visibleSets,
    selection = preferredSetNumber?.let { setNumber ->
        RotationSetSelection(
            setId = preferredSetId,
            setNumber = setNumber,
            setSyncId = preferredSetSyncId,
        )
    },
)

/*
 * Seed a new row from the row the user is actually working on. If no selection has been retained,
 * the newest completed row is the only safe implicit source; an untouched preloaded tail is not.
 */

/**
 * A new set is seeded from the latest row on the card, full stop: "sets should use the most
 * latest weight as their preloaded weight when adding a set".
 *
 * Neither the scrubber's selection nor whether a row is checked gets a vote. Parking the scrubber
 * on set 1 to fix a typo used to make the next added set copy set 1, and preferring the highest
 * *completed* row made the add gesture skip back over rows you had not checked yet. Both made
 * "add a set" copy an arbitrary earlier weight.
 */
internal fun rotationAddSourceSet(
    visibleSets: List<LoggedSet>,
    @Suppress("UNUSED_PARAMETER") selection: RotationSetSelection?,
): LoggedSet? = visibleSets.maxByOrNull { it.setNumber }

/** Keep selection on the same surviving row, or its nearest neighbor when that row is removed. */
internal fun rotationSelectionAfterRemoval(
    visibleSets: List<LoggedSet>,
    selection: RotationSetSelection?,
    removedSetId: Long,
): RotationSetSelection? {
    if (selection == null) return null
    val removedIndex = visibleSets.indexOfFirst { it.id == removedSetId }
    if (removedIndex < 0) return selection

    val remaining = visibleSets
        .filterNot { it.id == removedSetId }
        .mapIndexed { index, set ->
            if (set.setNumber == index + 1) set else set.copy(setNumber = index + 1)
        }
    if (remaining.isEmpty()) return null

    // A null ID is a pending insert. Deleting an earlier row shifts its reserved logical number.
    if (selection.setId == null) {
        val removedNumber = visibleSets[removedIndex].setNumber
        return selection.copy(
            setNumber = selection.setNumber - if (removedNumber < selection.setNumber) 1 else 0,
        )
    }

    val selectedBeforeRemoval = visibleSets.firstOrNull(selection::matches)
        ?: visibleSets.firstOrNull { it.setNumber == selection.setNumber }
    val target = if (selectedBeforeRemoval?.id == removedSetId) {
        remaining[removedIndex.coerceAtMost(remaining.lastIndex)]
    } else {
        selectedBeforeRemoval?.let { selected -> remaining.firstOrNull { it.id == selected.id } }
            ?: remaining.getOrNull((selection.setNumber - 1).coerceIn(0, remaining.lastIndex))
    }
    return target?.let(::rotationSelectionOf)
}

/**
 * Where the fields go when the primary button is tapped on [toggled]. Logging a set hands the
 * fields to the next set still open (the ones after it first, then any skipped before it), so a
 * straight run through the card is one tap per set. Unlogging, or logging the last open set,
 * stays put: that set is the one being looked at.
 */
internal fun rotationSelectionAfterToggle(
    visibleSets: List<LoggedSet>,
    toggled: LoggedSet,
): RotationSetSelection {
    if (toggled.isCompleted) return rotationSelectionOf(toggled)
    val index = visibleSets.indexOfFirst { it.id == toggled.id }
    val open = { set: LoggedSet -> !set.isCompleted && set.id != toggled.id }
    val next = visibleSets.drop(index + 1).firstOrNull(open) ?: visibleSets.take(index.coerceAtLeast(0)).firstOrNull(open)
    return rotationSelectionOf(next ?: toggled)
}

internal class RotationCompletionSelection(
    private val selections: MutableMap<String, RotationSetSelection>,
) {
    private var selectionGeneration = 0L

    fun selectionChanged() { selectionGeneration++ }

    fun toggle(
        cardKey: String,
        toggled: LoggedSet,
        currentVisibleSets: () -> List<LoggedSet>,
        isCurrentContext: () -> Boolean,
        complete: (Long, () -> Unit) -> Unit,
    ) {
        val generation = ++selectionGeneration
        selections[cardKey] = rotationSelectionOf(toggled)
        complete(toggled.id) {
            if (generation != selectionGeneration || !isCurrentContext()) return@complete
            val visibleSets = currentVisibleSets()
            val live = visibleSets.firstOrNull {
                if (toggled.syncId.isNotBlank()) it.syncId == toggled.syncId else it.id == toggled.id
            } ?: return@complete
            if (selections[cardKey]?.matches(live) != true) return@complete
            selections[cardKey] = rotationSelectionAfterToggle(
                visibleSets,
                live.copy(isCompleted = toggled.isCompleted),
            )
        }
    }
}

/** Exercise ID is stable while optimistic card IDs are replaced during repository sync. */
internal fun rotationCardKey(exercise: ExerciseLog): String =
    "exercise:${exercise.exerciseId}"

/** Sync identity keeps a ledger row stable when its optimistic database ID is replaced. */
internal fun rotationSetKey(set: LoggedSet): String =
    set.syncId.takeIf(String::isNotBlank)?.let { "sync:$it" } ?: "set:${set.id}"

/**
 * Saved card positions preserve write identity, including gaps left by old deletions. They are not
 * presentation numbers. Keep the saved order, preserve the incoming order when legacy cards share
 * a position, and let the UI assign a fresh contiguous number to the surviving exercises.
 */
internal fun rotationExerciseDisplayOrder(exercises: List<ExerciseLog>): List<ExerciseLog> =
    exercises.withIndex()
        .sortedWith(
            compareBy<IndexedValue<ExerciseLog>> { it.value.cardPosition }
                .thenBy { it.index },
        )
        .map(IndexedValue<ExerciseLog>::value)

internal fun rotationExerciseVisualPositions(exercises: List<ExerciseLog>): Map<String, Int> =
    rotationExerciseDisplayOrder(exercises)
        .mapIndexed { index, exercise -> rotationCardKey(exercise) to index + 1 }
        .toMap()

internal fun rotationExercisePositionLabel(position: Int): String =
    "EX ${position.coerceAtLeast(1).toString().padStart(2, '0')}"

internal data class RotationExerciseProgress(
    val completed: Int,
    val total: Int,
) {
    val remaining: Int get() = (total - completed).coerceAtLeast(0)
    val isComplete: Boolean get() = total > 0 && remaining == 0
    val label: String
        get() = when {
            isComplete -> "COMPLETE"
            remaining == 1 -> "1 LEFT"
            else -> "$remaining LEFT"
        }
}

internal fun rotationExerciseProgress(sets: List<LoggedSet>): RotationExerciseProgress =
    RotationExerciseProgress(
        completed = sets.count(LoggedSet::isCompleted),
        total = sets.size,
    )

internal enum class RotationReviewScope {
    NONE,
    WHOLE_WORKOUT,
    COMPLETED_EXERCISE,
}

/** The more specific row correction owns DONE if asynchronous state briefly overlaps. */
internal fun rotationReviewScope(
    reviewingCompletedCardId: String?,
    reviewingWholeWorkout: Boolean,
): RotationReviewScope = when {
    reviewingCompletedCardId != null -> RotationReviewScope.COMPLETED_EXERCISE
    reviewingWholeWorkout -> RotationReviewScope.WHOLE_WORKOUT
    else -> RotationReviewScope.NONE
}

/** A global reopen exposes the whole historical deck; a row click still isolates one exercise. */
internal fun rotationExercisesInDeck(
    exercises: List<ExerciseLog>,
    reviewingCompletedCardId: String?,
    reviewingWholeWorkout: Boolean,
): List<ExerciseLog> {
    val scoped = when (rotationReviewScope(reviewingCompletedCardId, reviewingWholeWorkout)) {
        RotationReviewScope.COMPLETED_EXERCISE ->
            exercises.filter { it.cardId == reviewingCompletedCardId }
        RotationReviewScope.WHOLE_WORKOUT -> exercises
        RotationReviewScope.NONE -> exercises.filter {
            it.displayMode != com.ayman.ecolift.data.WorkoutCardDisplayMode.COMPACT
        }
    }
    // Repository emissions are not a presentation order. Summary and correction modes must open
    // the same workout sequence, with exercise 01 first even when rows arrive out of order.
    return rotationExerciseDisplayOrder(scoped)
}

/** Lifetime workout ordinal on this date. A workout is a worked date, independent of split use. */
internal fun rotationWorkoutNumber(workedDays: Set<LocalDate>, selectedDate: LocalDate): Int =
    workedDays.count { it <= selectedDate }.coerceAtLeast(1)

internal fun rotationWorkoutNumberLabel(number: Int): String =
    number.coerceAtLeast(1).toString().let { if (it.length < 2) it.padStart(2, '0') else it }

internal fun rotationSummaryTitle(splitLabel: String): String =
    splitLabel.trim().takeIf { it.isNotEmpty() }?.uppercase() ?: "WORKOUT\nSUMMARY"

internal fun rotationSummaryDateLabel(date: LocalDate): String {
    val weekdays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    return "${weekdays[date.dayOfWeek.isoDayNumber - 1]} ${months[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/** Wins only: a chip for a rise of at least +1%; drops, zero and words get nothing. */
internal fun summaryChipVisible(comparison: String): Boolean {
    if (!comparison.startsWith("+")) return false
    val digits = comparison.drop(1).takeWhile { it.isDigit() }
    return (digits.toIntOrNull() ?: 0) > 0
}

/** A concise, evidence-backed comparison for the completed exercise row. */
internal fun rotationSummaryComparisonLabel(exercise: ExerciseLog): String {
    val today = exercise.sets.filter { it.isCompleted && !it.isWarmup }
    val last = exercise.ghostSets.filterNot { it.isWarmup }
    if (today.isEmpty() || last.isEmpty()) return "BASELINE"

    if (today.any { it.isBodyweight }) {
        val nowReps = today.maxOfOrNull { rotationDisplayedRepsOf(it) ?: 0 } ?: 0
        val lastReps = last.maxOfOrNull { it.reps ?: 0 } ?: 0
        val delta = nowReps - lastReps
        return when {
            delta > 0 -> "+$delta ${if (delta == 1) "REP" else "REPS"}"
            delta < 0 -> "$delta ${if (delta == -1) "REP" else "REPS"}"
            else -> "HELD"
        }
    }

    val now = today.maxOfOrNull { set ->
        rotationE1rm(rotationDisplayedWeightOf(set), rotationDisplayedRepsOf(set))
    } ?: 0f
    val then = last.maxOfOrNull { set -> rotationE1rm(set.weightLbs, set.reps) } ?: 0f
    if (then <= 0f) return "BASELINE"
    val percent = ((now - then) / then * 100f).roundToInt()
    return when {
        percent > 0 -> "+$percent%"
        percent < 0 -> "$percent%"
        else -> "HELD"
    }
}

/* ───────────────────────────── Trend card ─────────────────────────────
 *
 * One exercise per page. The chart is the hero: this exercise's best estimated 1RM per past
 * session, with today's point live so the dot moves as you drag either field. "Before" lives on
 * the thing you are touching, not only on the chart: each field says what you did last time, and
 * every set box shows last session's numbers until the set is logged, when it flips to today's.
 */

/** "185 × 8" for the boxes and the button; [rotationSetLabel] keeps the compact form for tests. */
internal fun rotationSetDisplayLabel(storedWeight: Int?, reps: Int?, isBodyweight: Boolean): String =
    "${rotationLoadLabel(storedWeight, isBodyweight)} × ${reps ?: 0}"

/**
 * Today's point on the trend. The best logged set wins; while the selected set is still unlogged
 * and beats everything logged so far, its live values are the point instead. Second value is
 * whether the point is settled (logged) or still moving.
 */
internal fun rotationTodayE1rm(
    visibleSets: List<LoggedSet>,
    selected: LoggedSet?,
    userBodyweightLbs: Int? = null,
): Pair<Float, Boolean>? {
    // A bodyweight set only counts once a bodyweight is on file; the total load is bodyweight
    // plus whatever's added, same rule as sessionE1rmHistory so the live point and the history
    // it moves against are computed the same way.
    fun eligible(set: LoggedSet) = !set.isBodyweight || userBodyweightLbs != null
    fun e1rmOf(set: LoggedSet): Float {
        val reps = rotationDisplayedRepsOf(set) ?: 0
        if (reps <= 0) return 0f
        val addedLbs = WeightLbs.toLbs(rotationDisplayedWeightOf(set)).toFloat()
        val loadLbs = if (set.isBodyweight) (userBodyweightLbs ?: 0) + addedLbs else addedLbs
        return epleyEstimate(loadLbs, reps)
    }
    val logged = visibleSets.filter { it.isCompleted && !it.isWarmup && eligible(it) }
        .maxOfOrNull(::e1rmOf) ?: 0f
    val live = selected?.takeIf { !it.isCompleted && !it.isWarmup && eligible(it) }
        ?.let(::e1rmOf) ?: 0f
    return when {
        live > logged && live > 0f -> live to false
        logged > 0f -> logged to true
        else -> null
    }
}

/** Month labels for the axis: first, middle and last session, collapsed when they repeat. */
internal fun rotationAxisMonthLabels(dates: List<String>): List<String> {
    if (dates.isEmpty()) return emptyList()
    val picks = listOf(0, dates.size / 2, dates.lastIndex).distinct()
    return picks
        .mapNotNull { index ->
            runCatching { LocalDate.parse(dates[index]) }.getOrNull()
                ?.let { MONTH_ABBREV.getOrNull(it.month.ordinal)?.uppercase() }
        }
        .fold(mutableListOf()) { acc, month ->
            if (acc.lastOrNull() != month) acc.add(month)
            acc
        }
}

/** "5" for a whole number of pounds, "2.5" otherwise; the delta line never prints "5.0". */
internal fun rotationFormatLbs(lbs: Float): String {
    val whole = lbs.roundToInt()
    return if (abs(lbs - whole) < 0.01f) whole.toString() else ((lbs * 10f).roundToInt() / 10f).toString()
}

/** Percentage change of today's point against the last session's, or null without both. */
internal fun rotationTrendPercent(lastSession: Float?, today: Float?): Int? {
    if (lastSession == null || today == null || lastSession <= 0f) return null
    return ((today - lastSession) / lastSession * 100f).roundToInt()
}

/**
 * Three horizontal gridline values for the trend plot.
 * Steps of 5 / 10 / 25 / 50 lb (or higher multiples) chosen so the three lines cover
 * the range [min, max] with the top line at or above max and the bottom at or below min.
 * A flat history (min == max) still yields three distinct lines.
 */
internal fun rotationTrendGridValues(min: Float, max: Float): List<Float> {
    if (min.isNaN() || max.isNaN() || min.isInfinite() || max.isInfinite() || min > max) {
        return listOf(0f, 50f, 100f)
    }
    val candidateSteps = listOf(5f, 10f, 25f, 50f, 100f, 250f, 500f)
    for (s in candidateSteps) {
        val minUnits = kotlin.math.floor(min / s)
        val maxUnits = kotlin.math.ceil(max / s)
        if (maxUnits - minUnits <= 2f) {
            val idealK = kotlin.math.round(((min + max) / 2f) / s).toInt()
            val minK = (maxUnits - 1).toInt()
            val maxK = (minUnits + 1).toInt()
            val k = idealK.coerceIn(minK, maxK)
            val bottom = (k - 1) * s
            val mid = k * s
            val top = (k + 1) * s
            return listOf(bottom, mid, top)
        }
    }
    val fallbackStep = candidateSteps.last()
    val k = kotlin.math.round(((min + max) / 2f) / fallbackStep).toInt()
    return listOf((k - 1) * fallbackStep, k * fallbackStep, (k + 1) * fallbackStep)
}

/**
 * Labels for the trend axis: first month, middle month (collapsed when equal),
 * then the actual last-session date in MMM d uppercase form.
 * Single-session history gives just the date.
 */
internal fun rotationAxisLabels(dates: List<String>): List<String> {
    if (dates.isEmpty()) return emptyList()
    val lastLabel = runCatching { WorkoutDates.formatAxis(dates.last()).uppercase() }.getOrNull()
        ?: return emptyList()
    if (dates.size == 1) return listOf(lastLabel)

    val candidateIndices = if (dates.size <= 2) listOf(0) else listOf(0, dates.size / 2)
    val months = candidateIndices
        .mapNotNull { index ->
            runCatching { LocalDate.parse(dates[index]) }.getOrNull()
                ?.let { MONTH_ABBREV.getOrNull(it.month.ordinal)?.uppercase() }
        }
        .fold(mutableListOf<String>()) { acc, month ->
            if (acc.lastOrNull() != month) acc.add(month)
            acc
        }

    return months + lastLabel
}

/**
 * Y-position of the last-time mark on the vertical tape.
 * Higher values sit above the center (smaller y), lower values below the center (larger y).
 * Clamped between top and bottom.
 */
internal fun rotationTapeLastMarkY(
    center: Float,
    value: Float,
    last: Float,
    pxPerUnit: Float,
    top: Float,
    bottom: Float,
): Float {
    // Same orientation as the ticks: a heavier last time sits below the thumb.
    val unclamped = center + (last - value) * pxPerUnit
    val minBound = minOf(top, bottom)
    val maxBound = maxOf(top, bottom)
    return unclamped.coerceIn(minBound, maxBound)
}

/**
 * Finger speed picks the step size: slow travel keeps the half-pound or one-rep notch, while
 * faster travel covers a wider range. The field-specific tiers are recorded in the 2026-10-09
 * sensitivity entry in CHANGELOG.md.
 */
internal fun rotationScrubGain(dpPerMs: Float, isReps: Boolean): Int = when {
    !isReps && abs(dpPerMs) > 2.4f -> 5
    abs(dpPerMs) > 1.2f -> 2
    else -> 1
}

/** The range a field may hold. A bodyweight lift's "added" cannot go below zero. */
internal data class RotationFieldBounds(val min: Float, val max: Float)

/** A finger and its coast share one allowance, measured from where that finger took the tape. */
internal fun rotationSwipeBounds(start: Float, isReps: Boolean, bounds: RotationFieldBounds): RotationFieldBounds {
    val allowance = if (isReps) 10f else 25f
    return RotationFieldBounds(maxOf(bounds.min, start - allowance), minOf(bounds.max, start + allowance))
}

/** Slow releases settle where the thumb stopped; a throw spends at most the range still available. */
internal fun rotationSwipeReleaseVelocity(value: Float, velocity: Float, gain: Int, bounds: RotationFieldBounds): Float {
    if (gain == 1) return 0f
    val remaining = if (velocity > 0f) bounds.max - value else value - bounds.min
    val limit = maxOf(0f, remaining) / 160f
    return velocity.coerceIn(-limit, limit)
}

internal enum class RotationFlingPhase { DECAY, SETTLE }

/** Where a released tape is: its live value, how fast it is still moving, and what it is doing. */
internal data class RotationFlingState(
    val value: Float,
    val velocity: Float,
    val phase: RotationFlingPhase,
)

/** Per 16 ms frame, from react-ruler-picker by way of the feel board. */
private const val ROTATION_FLING_FRICTION = 0.9f

/** Below this the release was a stop, not a throw: settle straight away. */
internal const val ROTATION_FLING_MIN_VELOCITY = 0.004f

/** Units per millisecond at which coasting has visibly ended. */
internal const val ROTATION_FLING_STOP_VELOCITY = 0.0006f

/** Past a bound the tape bleeds speed fast instead of running off into nothing. */
private const val ROTATION_FLING_BOUND_BLEED = 0.6f

/** Units past a bound at which even a hard throw gives up and snaps back. */
private const val ROTATION_FLING_BOUND_SLACK = 3f

/** A frame longer than this was the tab being backgrounded, not a slow frame. */
private const val ROTATION_FLING_MAX_FRAME_MS = 48f

/** The value is throwable only if the finger was still moving it when it left. */
internal fun rotationFlingLaunch(value: Float, velocity: Float): RotationFlingState =
    RotationFlingState(
        value = value,
        velocity = velocity,
        phase = if (abs(velocity) > ROTATION_FLING_MIN_VELOCITY) {
            RotationFlingPhase.DECAY
        } else {
            RotationFlingPhase.SETTLE
        },
    )

/**
 * One frame of coasting. Kept pure so the numbers can be tested without a composition: the
 * composable only feeds it the frame time and reads the value back out.
 */
internal fun rotationFlingFrame(
    state: RotationFlingState,
    dtMs: Float,
    bounds: RotationFieldBounds,
): RotationFlingState {
    if (state.phase != RotationFlingPhase.DECAY) return state
    val dt = dtMs.coerceIn(0f, ROTATION_FLING_MAX_FRAME_MS)
    val decay = ROTATION_FLING_FRICTION.pow(dt / 16f)
    // The 16ms distance is unchanged; longer frames spend the same finite coast budget.
    val value = state.value + state.velocity * 160f * (1f - decay)
    var velocity = state.velocity * decay
    if (value < bounds.min || value > bounds.max) velocity *= ROTATION_FLING_BOUND_BLEED
    val spent = abs(velocity) < ROTATION_FLING_STOP_VELOCITY ||
        value < bounds.min - ROTATION_FLING_BOUND_SLACK ||
        value > bounds.max + ROTATION_FLING_BOUND_SLACK
    return RotationFlingState(
        value = value,
        velocity = velocity,
        phase = if (spent) RotationFlingPhase.SETTLE else RotationFlingPhase.DECAY,
    )
}

/** Release velocity is stale after this long; a finger that paused before lifting did not throw. */
private const val ROTATION_FLING_SAMPLE_AGE_MS = 80L

/** A throw faster than this is a slip, not an intention. */
private const val ROTATION_FLING_MAX_VELOCITY = 0.2f

/**
 * Release speed measured on the value rather than pixels, so the gain the finger earned
 * carries into a throw; the swipe allowance limits that throw before it starts.
 */
internal fun rotationReleaseVelocity(
    oldestValue: Float,
    oldestTimeMs: Long,
    latestValue: Float,
    latestTimeMs: Long,
    nowMs: Long,
): Float {
    if (nowMs - latestTimeMs >= ROTATION_FLING_SAMPLE_AGE_MS) return 0f
    if (latestTimeMs <= oldestTimeMs) return 0f
    val perMs = (latestValue - oldestValue) / (latestTimeMs - oldestTimeMs).toFloat()
    return perMs.coerceIn(-ROTATION_FLING_MAX_VELOCITY, ROTATION_FLING_MAX_VELOCITY)
}

/**
 * Last time can catch a landing only inside half a step. A wider detent steals neighboring
 * half-pound selections after release, even though those values were shown under the finger.
 */
internal fun rotationSettleTarget(
    value: Float,
    step: Float,
    last: Float?,
    detentRadius: Float,
    bounds: RotationFieldBounds,
): Float {
    val nearest = (value / step).roundToInt() * step
    val target = if (last != null && abs(value - last) < minOf(detentRadius, step / 2f)) last else nearest
    return target.coerceIn(
        kotlin.math.ceil(bounds.min / step) * step,
        kotlin.math.floor(bounds.max / step) * step,
    )
}

/** The integer columns of a rolling readout, and whether the half-pound suffix is showing. */
internal data class RotationRollingDigits(val digits: String, val half: Boolean)

private val ROTATION_ROLLING_PATTERN = Regex("""^\d+(\.5)?$""")

/**
 * Only a plain pound or rep count rolls. "BW + 8", "—" and anything else the field can be asked to
 * show has no digit columns to move, so it renders as one piece of text the way it always did.
 */
internal fun rotationRollingDigits(text: String): RotationRollingDigits? {
    if (!ROTATION_ROLLING_PATTERN.matches(text)) return null
    val dot = text.indexOf('.')
    return if (dot < 0) {
        RotationRollingDigits(digits = text, half = false)
    } else {
        RotationRollingDigits(digits = text.substring(0, dot), half = true)
    }
}

internal enum class RotationDeltaKind { UP, DOWN, MATCH }

internal data class RotationDeltaChip(val kind: RotationDeltaKind, val text: String)

/** The G2 last line: one blue chip for last time's number, one chip for the change against it. */
internal data class RotationLastLineChips(
    val lastText: String,
    val unitText: String?,
    val delta: RotationDeltaChip?,
)

/**
 * Last time and the change against it, as two chips.
 *
 * [nowSettled] is the *settled* value — the nearest step of the live one — so a fling does not
 * spray a new delta on every frame. [lastText] is the label the field already draws on the tape
 * mark, which is also the bodyweight form ("+8") rather than the long "BW + 8". No ghost set means
 * no line at all.
 */
internal fun rotationLastLineChips(
    nowSettled: Float?,
    last: Float?,
    lastText: String?,
    step: Float,
    unit: String?,
): RotationLastLineChips? {
    if (lastText == null) return null
    // Wins only: a rise of at least half a step gets a chip; matching or dropping gets none.
    val delta = if (nowSettled == null || last == null) {
        null
    } else {
        val d = nowSettled - last
        if (d >= step / 2f) RotationDeltaChip(RotationDeltaKind.UP, rotationStepAmountText(abs(d), step)) else null
    }
    return RotationLastLineChips(lastText = lastText, unitText = unit, delta = delta)
}

/**
 * "MATCHES LAST" is the phrase; "MATCH" is what fits when the field's body column cannot hold the
 * phrase beside the set chip at a size anyone could read. See [rotationLastLineScale].
 */
internal const val ROTATION_MATCH_TEXT = "MATCHES LAST"
internal const val ROTATION_MATCH_TEXT_SHORT = "MATCH"

/** Half pounds print as "2.5"; reps are whole. */
private fun rotationStepAmountText(amount: Float, step: Float): String =
    if (step < 1f) rotationFormatLbs(amount) else amount.roundToInt().toString()

/** Scales the last line may draw at, largest first. */
internal val ROTATION_LAST_LINE_SCALES = listOf(1f, 0.85f, 0.72f, 0.6f)

/**
 * The largest scale at which both chips fit the body column beside the tape.
 *
 * The chips were drawn on a field the width of a phone; in the card they share 98dp with the tape,
 * and "LAST 187.5 lb" next to "MATCHES LAST" is wider than that at full size. Rather than clip a
 * chip — which reads as a bug — the whole line shrinks by one factor, so every proportion in it
 * survives. An unmeasured column (0) keeps the full size.
 */
internal fun rotationLastLineScale(widthAt: (Float) -> Float, maxWidth: Float): Float {
    if (maxWidth <= 0f) return ROTATION_LAST_LINE_SCALES.first()
    return ROTATION_LAST_LINE_SCALES.firstOrNull { widthAt(it) <= maxWidth }
        ?: ROTATION_LAST_LINE_SCALES.last()
}

/**
 * The estimated-1RM trend. Number column on the left (104dp wide), plot on the right (124dp tall)
 * with labelled gridlines and axis line beneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendChart(
    history: List<Float>,
    historyDates: List<String>,
    today: Float?,
    todayLogged: Boolean,
    accent: Color,
    accentSoft: Color,
    cardBackground: Color,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
    // "at BW <lb>" under the headline, for bodyweight exercises once a bodyweight is on file.
    caption: String? = null,
    // Bodyweight exercise, no bodyweight on file: there is nothing to plot or estimate.
    bodyweightMissing: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val lastSession = history.lastOrNull()
    val percent = rotationTrendPercent(lastSession, today)
    val headline = today ?: lastSession
    val hasAnyPoint = history.isNotEmpty() || today != null

    Row(modifier = modifier.fillMaxWidth()) {
        // 1. Number column (104dp wide, 10dp end padding, 1dp hairline on its right edge)
        Column(
            modifier = Modifier
                .width(112.dp)
                .heightIn(min = 124.dp)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "EST. 1RM",
                fontFamily = SheetSansFontFamily,
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = EspressoPalette.dim,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            if (headline == null && bodyweightMissing) {
                // Bodyweight not set: there is no total load to estimate, so say why instead of
                // showing an empty grey bar.
                Text(
                    text = "Set bodyweight in Settings to see Est. 1RM",
                    fontFamily = LogUiFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = EspressoPalette.dim,
                    maxLines = 3,
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val digits = headline?.roundToInt()
                    val maxPx = constraints.maxWidth.toFloat()
                    fun measure(text: String, sizeSp: Float): Float {
                        val style = TextStyle(
                            fontFamily = LogDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = sizeSp.sp,
                            letterSpacing = (-2.5f * sizeSp / 50f).sp,
                        )
                        return textMeasurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toFloat()
                    }
                    val (label, sizeSp) = if (digits == null) {
                        "—" to 50f
                    } else {
                        val plain = digits.toString()
                        val plainSize = rotationHeadlineFontSp({ measure(plain, it) }, maxPx)
                        if (measure(plain, plainSize) <= maxPx) {
                            plain to plainSize
                        } else {
                            val compact = rotationHeadlineCompact(digits)
                            compact to rotationHeadlineFontSp({ measure(compact, it) }, maxPx)
                        }
                    }
                    Text(
                        text = label,
                        fontFamily = LogDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = sizeSp.sp,
                        lineHeight = sizeSp.sp,
                        letterSpacing = (-2.5f * sizeSp / 50f).sp,
                        color = when {
                            today == null -> EspressoPalette.dim
                            todayLogged -> palette.complete
                            else -> accent
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (percent != null) {
                    Spacer(Modifier.height(6.dp))
                    val percentString = when {
                        percent > 0 -> "+$percent%"
                        percent < 0 -> "$percent%"
                        else -> "HELD"
                    }
                    val tone = when {
                        percent > 0 -> EspressoTone.Up
                        percent < 0 -> EspressoTone.Down
                        else -> EspressoTone.Flat
                    }
                    val lastVal = lastSession?.roundToInt() ?: 0
                    // Large text drops the comparison under the chip instead of hiding its number.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        EspressoDeltaChip(text = percentString, tone = tone)
                        Text(
                            text = "VS $lastVal",
                            fontFamily = LogUiFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            color = EspressoPalette.dim,
                            maxLines = 1,
                        )
                    }
                }
                if (caption != null) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            text = "BW ",
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            color = EspressoPalette.dim,
                            maxLines = 1,
                        )
                        Text(
                            text = caption,
                            fontFamily = LogDisplayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            color = palette.ink,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 2. Plot + Axis line (remaining width)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(124.dp)) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val left = 30.dp.toPx()
                    val todayX = w - 14.dp.toPx()
                    val top = 12.dp.toPx()
                    val bottom = h - 8.dp.toPx()

                    val all = history + listOfNotNull(today)
                    if (all.isEmpty()) return@Canvas

                    val n = history.size
                    if (n == 0) {
                        // With no history: no gridlines or labels, dotted baseline at mid height, today point only.
                        if (today != null) {
                            val todayPt = Offset(todayX, (top + bottom) / 2f)
                            drawLine(
                                color = palette.inkSubtle.copy(alpha = 0.25f),
                                start = Offset(left, todayPt.y),
                                end = Offset(todayX - 10.dp.toPx(), todayPt.y),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())),
                            )
                            val todayColor = if (todayLogged) palette.complete else accent
                            drawCircle(todayColor.copy(alpha = 0.25f), 8.dp.toPx(), todayPt)
                            drawCircle(todayColor, 4.5.dp.toPx(), todayPt)
                        }
                        return@Canvas
                    }

                    val minV = all.min()
                    val maxV = all.max()
                    val gridValues = rotationTrendGridValues(minV, maxV)
                    val bottomVal = gridValues[0]
                    val topVal = gridValues[2]
                    val vRange = (topVal - bottomVal).takeIf { it > 0.001f } ?: 1f
                    fun yOf(v: Float): Float = bottom - ((v - bottomVal) / vRange) * (bottom - top)

                    // Three horizontal gridlines with lb labels
                    gridValues.forEach { gv ->
                        val gy = yOf(gv)
                        drawLine(
                            color = EspressoPalette.hair2.copy(alpha = 0.45f),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1.dp.toPx(),
                        )
                        val textResult = textMeasurer.measure(
                            text = gv.roundToInt().toString(),
                            style = TextStyle(
                                fontFamily = LogUiFontFamily,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        drawText(
                            textLayoutResult = textResult,
                            color = EspressoPalette.faint,
                            topLeft = Offset(4.dp.toPx(), gy - textResult.size.height / 2f),
                        )
                    }

                    val lastX = if (today != null) (todayX - 30.dp.toPx()).coerceAtLeast(left) else todayX
                    val xs = if (n == 1) listOf(lastX) else List(n) { i -> left + (lastX - left) * i / (n - 1) }
                    val pts = xs.mapIndexed { i, x -> Offset(x, yOf(history[i])) }
                    val lastPt = pts.last()
                    val line = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    // History carries a soft fill in the last-time blue; today's projection carries
                    // its own fill in the muscle colour (green once logged). Spec §Log.
                    val historyArea = Path().apply {
                        addPath(line)
                        lineTo(lastPt.x, bottom)
                        lineTo(pts.first().x, bottom)
                        close()
                    }
                    drawPath(path = historyArea, color = palette.repStrong.copy(alpha = 0.10f))
                    val todayColor = if (todayLogged) palette.complete else accent
                    if (today != null) {
                        val todayPt = Offset(todayX, yOf(today))
                        val todayArea = Path().apply {
                            moveTo(lastPt.x, lastPt.y)
                            lineTo(todayPt.x, todayPt.y)
                            lineTo(todayPt.x, bottom)
                            lineTo(lastPt.x, bottom)
                            close()
                        }
                        drawPath(path = todayArea, color = todayColor.copy(alpha = 0.14f))
                    }
                    if (n >= 2) {
                        drawPath(
                            path = line,
                            color = palette.repStrong,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    pts.dropLast(1).forEach { drawCircle(palette.repStrong, 2.5.dp.toPx(), it) }
                    // The last real session is hollow: the point today is measured against.
                    drawCircle(cardBackground, 4.dp.toPx(), lastPt)
                    drawCircle(palette.repStrong, 4.dp.toPx(), lastPt, style = Stroke(2.dp.toPx()))

                    if (today != null) {
                        val todayPt = Offset(todayX, yOf(today))
                        drawLine(
                            color = todayColor,
                            start = lastPt,
                            end = todayPt,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (todayLogged) null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                        )
                        drawCircle(todayColor.copy(alpha = 0.25f), 8.dp.toPx(), todayPt)
                        drawCircle(todayColor, 4.5.dp.toPx(), todayPt)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TrendAxis(
                historyDates = historyDates,
                accentSoft = accentSoft,
                palette = palette,
                showTodayLabel = hasAnyPoint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, end = 14.dp),
            )
        }
    }
}

@Composable
private fun TrendAxis(
    historyDates: List<String>,
    accentSoft: Color,
    palette: LogGlassPalette,
    // False for an empty plot (no history, no today point) so the axis doesn't promise a
    // point that isn't there — e.g. a bodyweight exercise with no bodyweight on file.
    showTodayLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val labels = remember(historyDates) { rotationAxisLabels(historyDates) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Explicit line height: without it the label inherits Material's 24sp body line height
        // and the axis row grows to twice its text, which the deck height budget does not carry.
        labels.forEach { label ->
            Text(
                text = label,
                fontFamily = SheetSansFontFamily,
                fontSize = 9.5.sp,
                lineHeight = ROTATION_AXIS_LINE_HEIGHT_DP.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                color = EspressoPalette.faint,
                maxLines = 1,
            )
        }
        if (showTodayLabel) {
            Text(
                text = "TODAY",
                fontFamily = SheetSansFontFamily,
                fontSize = 9.5.sp,
                lineHeight = ROTATION_AXIS_LINE_HEIGHT_DP.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                color = accentSoft,
                maxLines = 1,
            )
        }
    }
}

/** The half-pound suffix is 24sp when the digits are 42sp, and scales with them. */
private const val ROTATION_HALF_SUFFIX_RATIO = 24f / 42f

/** A digit column changes width and slides its digit over the same 220ms. */
private const val ROTATION_DIGIT_ROLL_MS = 220

/** The rolling digits sit this tight, like the plain readout beside them. */
private const val ROTATION_DIGIT_TRACKING_SP = -2f

/** The ".5" fades and rises over its own, shorter beat. */
private const val ROTATION_HALF_FADE_MS = 160

/** The delta chip never hard-cuts between up, down and matching. */
private const val ROTATION_CHIP_FADE_MS = 150

/**
 * The feel board's settle curve: a cubic ease-out multiplied by a slow cosine, which overshoots
 * about 4% and comes back. A `spring(dampingRatio = 0.75f)` was the offered stand-in; the explicit
 * curve is used instead because it is the board's own number and it lands in a known duration,
 * which the settle needs (it must be over before the next finger touches the tape).
 */
private val ROTATION_SETTLE_EASING = Easing { p ->
    1f - (1f - p).pow(3) * cos(p * PI.toFloat() * 0.9f)
}

/** One pointer sample kept for the speed-gain and the release throw. */
internal data class RotationScrubSample(val y: Float, val timeMs: Long, val value: Float)

/** How many samples the gain and the throw look back over. */
private const val ROTATION_SCRUB_SAMPLES = 6

/** The last few samples of a drag, starting at touch-down, for speed gain and release velocity. */
internal class RotationScrubTrack(start: RotationScrubSample) {
    private val samples = ArrayDeque<RotationScrubSample>().apply { addLast(start) }

    /** Finger speed in dp per ms from the oldest sample to this one. */
    fun speedDp(y: Float, timeMs: Long, density: Float): Float {
        val oldest = samples.first()
        val elapsed = maxOf(1f, (timeMs - oldest.timeMs).toFloat())
        return abs(y - oldest.y) / density / elapsed
    }

    fun add(sample: RotationScrubSample) {
        if (sample.y == samples.last().y) return
        samples.addLast(sample)
        while (samples.size > ROTATION_SCRUB_SAMPLES) samples.removeFirst()
    }

    /** See [rotationReleaseVelocity]; a drag of one sample was not a throw. */
    fun releaseVelocity(nowMs: Long): Float {
        val oldest = samples.firstOrNull() ?: return 0f
        val latest = samples.last()
        return rotationReleaseVelocity(
            oldestValue = oldest.value,
            oldestTimeMs = oldest.timeMs,
            latestValue = latest.value,
            latestTimeMs = latest.timeMs,
            nowMs = nowMs,
        )
    }
}

/**
 * A vertical-slider number field: a tap picks it up, up raises it, and down lowers it.
 * The tape on the right scrolls with the value, last time has a tick, and the fixed thumb marks now.
 *
 * An unarmed field leaves every swipe to the page or pager. A tap arms it for [ROTATION_ARMED_MS]
 * with a grab click; a touch landing while the page moves belongs to the page entirely. In hand,
 * a steep drag takes the tape from touch-down and a tap opens the keypad. A logged set is [locked]:
 * its first tap unlocks and arms it. Spinning tape can be caught at its current value.
 *
 * Finger speed multiplies the half-pound or one-rep notch within one swipe's allowance, and a
 * fast release coasts before settling to a notch, with last time catching only within half a step.
 * Slow releases settle without coasting. Every whole step crossing still reaches [onStep], and
 * the chip offers the adjustment's starting value for [ROTATION_UNDO_WINDOW_MS] after landing.
 */
@Composable
private fun TrendField(
    value: String,
    unit: String,
    numericValue: Float?,
    numericLast: Float?,
    lastMarkLabel: String?,
    pxPerUnit: Float,
    tint: Color?,
    logged: Boolean,
    palette: LogGlassPalette,
    enabled: Boolean,
    interactionKey: Any?,
    onStep: (Int) -> Unit,
    onTap: () -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onPlates: (() -> Unit)? = null,
    peek: Boolean = false,
    onPeekDone: () -> Unit = {},
    locked: Boolean = false,
    onUnlock: () -> Unit = {},
) = key(interactionKey) {
    // A selected row owns its complete gesture lifetime, including delayed fling frames and
    // undo/armed timers. Replacing it must cancel the old work before callbacks can target a new row.
    DisposableEffect(Unit) {
        val releaseInteraction = onInteractionActiveChange
        onDispose { releaseInteraction(false) }
    }
    val density = LocalDensity.current
    val pxPerStep = with(density) { ROTATION_SCRUB_DP_PER_STEP.dp.toPx() }
    val lightHaptic = rememberLightHaptic()
    val heavyHaptic = rememberHeavyHaptic()
    val haptics = LocalHapticFeedback.current
    val latestOnStep by rememberUpdatedState(onStep)
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnInteractionActiveChange by rememberUpdatedState(onInteractionActiveChange)
    val latestLocked by rememberUpdatedState(locked)
    val latestOnUnlock by rememberUpdatedState(onUnlock)
    val pageScroll = LocalRotationPageScroll.current
    val textMeasurer = rememberTextMeasurer()

    val isReps = unit == "REPS"
    // One step is what a single notch of the wheel is worth, in the field's own unit.
    val step = if (isReps) 1f else 0.5f
    // Nothing outside these can be a real set, and the rubber band needs somewhere to push back.
    val bounds = remember(isReps) { RotationFieldBounds(0f, if (isReps) 99f else 1500f) }
    // Last time only catches its own notch; adjacent half pounds remain selectable.
    val detentRadius = step / 2f

    var isInteracting by remember(interactionKey) { mutableStateOf(false) }
    var coasting by remember(interactionKey) { mutableStateOf(false) }
    var liveValue by remember(interactionKey) { mutableStateOf(numericValue ?: 0f) }
    var stepIndex by remember(interactionKey) { mutableStateOf(((numericValue ?: 0f) / step).roundToInt()) }
    var gainLevel by remember(interactionKey) { mutableStateOf(1) }
    var rollingUp by remember(interactionKey) { mutableStateOf(true) }
    var releaseTicket by remember(interactionKey) { mutableStateOf(0) }
    var releaseVelocity by remember(interactionKey) { mutableStateOf(0f) }
    var swipeBounds by remember(interactionKey) { mutableStateOf(bounds) }
    // The step the value stood on when the tape was first picked up, until the undo window closes.
    // Later pickups inside the window keep it, so undo goes back past the whole adjustment.
    var undoBaseline by remember(interactionKey) { mutableStateOf<Int?>(null) }
    var undoTicket by remember(interactionKey) { mutableStateOf(0) }
    // Still in hand after the tape comes to rest (see ROTATION_ARMED_MS): the next swipe needs no tap.
    var armed by remember(interactionKey) { mutableStateOf(false) }
    var armTicket by remember(interactionKey) { mutableStateOf(0) }
    val latestOnPlates by rememberUpdatedState(onPlates)
    // Where the plate glyph and the undo chip sit inside the field, for routing a tap on them (see
    // the gesture below): 0 the field, 1 the glyph, 2 the chip.
    val placed = remember { arrayOfNulls<LayoutCoordinates>(3) }
    val undoVisible = undoBaseline != null && undoBaseline != stepIndex && !isInteracting && !coasting && !locked
    val latestUndoVisible by rememberUpdatedState(undoVisible)
    // The chip keeps the number it offered while it fades out, after the baseline has cleared.
    var undoLabel by remember(interactionKey) { mutableStateOf("") }
    LaunchedEffect(undoBaseline) {
        undoBaseline?.let { undoLabel = rotationUndoLabel(it * step, isReps) }
    }
    // Picked up: the tile rises a little and its ring thickens, so the click has something to see.
    val lift by animateFloatAsState(if (isInteracting) 1f else 0f, tween(140), label = "fieldLift")
    // The thicker ring stays for as long as the field is in hand, and eases off when it lets go.
    val inHand by animateFloatAsState(
        if (isInteracting || coasting || (armed && !locked)) 1f else 0f,
        tween(if (armed || isInteracting) 140 else 320),
        label = "fieldInHand",
    )

    LaunchedEffect(armTicket) {
        if (armTicket == 0) return@LaunchedEffect
        delay(ROTATION_ARMED_MS)
        if (isInteracting || coasting) return@LaunchedEffect
        armed = false
    }

    LaunchedEffect(undoTicket) {
        if (undoTicket == 0) return@LaunchedEffect
        delay(ROTATION_UNDO_WINDOW_MS)
        // Still in hand: the next landing starts the window again.
        if (isInteracting || coasting) return@LaunchedEffect
        undoBaseline = null
    }

    /** Back to where the adjustment started, the same way the tape got here: whole steps. */
    fun undoAdjustment() {
        val baseline = undoBaseline ?: return
        undoBaseline = null
        val steps = baseline - stepIndex
        if (steps == 0) return
        stepIndex = baseline
        rollingUp = steps > 0
        liveValue = baseline * step
        lightHaptic()
        latestOnStep(steps)
    }

    // First sight of a card with sets: the tape nudges up a few notches and back, silently, the
    // way a wheel shows it turns. Only the drawing moves; the value never does. A finger landing
    // mid-peek takes the tape back at once.
    val reducedMotion = rememberReducedMotion()
    val peekShift = remember { Animatable(0f) }
    val latestOnPeekDone by rememberUpdatedState(onPeekDone)
    val peekScope = rememberCoroutineScope()
    LaunchedEffect(peek) {
        if (!peek) return@LaunchedEffect
        if (reducedMotion) {
            latestOnPeekDone()
            return@LaunchedEffect
        }
        delay(ROTATION_PEEK_DELAY_MS)
        if (isInteracting || coasting) return@LaunchedEffect
        try {
            val notches = ROTATION_PEEK_NOTCHES * step
            peekShift.animateTo(notches, tween(240, easing = FastOutSlowInEasing))
            peekShift.animateTo(-notches * 0.6f, tween(300, easing = FastOutSlowInEasing))
            peekShift.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        } finally {
            withContext(NonCancellable) { peekShift.snapTo(0f) }
            latestOnPeekDone()
        }
    }

    // Espresso: a tonal tile; a muscle-coloured field (the Log card tints both) carries a 1.5dp ring
    // and its number in that colour, an untinted one is ink. Logged swaps the ring to green.
    val accentSoft = tint ?: palette.ink
    val borderColor = when {
        logged -> palette.complete
        tint != null -> tint
        // The ink field has no ring of its own; it shows one while it is in hand.
        else -> palette.ink.copy(alpha = 0.55f * inHand)
    }
    val fillColor = EspressoPalette.panel2
    val tapeColor = EspressoPalette.hair2.copy(alpha = 0.9f)
    val thumbColor = when {
        logged -> palette.complete
        tint != null -> tint
        else -> palette.ink
    }
    val valueColor = when {
        logged || tint == null -> palette.ink
        else -> tint
    }
    val shape = RoundedCornerShape(18.dp)

    /**
     * The value moved; tell the ViewModel about every whole step it crossed. A fast frame can cross
     * several at once, so the count goes out in one call rather than one call per step, and a value
     * held past a bound by the rubber band never dispatches at all.
     */
    fun dispatchCrossings(next: Float) {
        val index = (next.coerceIn(swipeBounds.min, swipeBounds.max) / step).roundToInt().coerceIn(
            kotlin.math.ceil(swipeBounds.min / step).toInt(),
            kotlin.math.floor(swipeBounds.max / step).toInt(),
        )
        val steps = index - stepIndex
        if (steps == 0) return
        stepIndex = index
        rollingUp = steps > 0
        lightHaptic()
        latestOnStep(steps)
    }

    /** The accessibility actions move the value the old way: one step, no physics. */
    fun dispatchSteps(steps: Int) {
        if (steps == 0) return
        lightHaptic()
        latestOnStep(steps)
    }

    // While a finger or its fling owns the field the live float is the truth; the moment both are
    // done the ViewModel owns it again, so a keypad entry or an undo still shows up here.
    LaunchedEffect(numericValue, isInteracting, coasting) {
        if (isInteracting || coasting) return@LaunchedEffect
        val settled = numericValue ?: 0f
        if (settled != liveValue) rollingUp = settled > liveValue
        liveValue = settled
        stepIndex = (settled / step).roundToInt()
    }

    // Release physics. The decay is the pure state machine frame by frame; the settle is one
    // animation onto the target, so the tape coasts, slows, and is pulled the last half pound in.
    LaunchedEffect(releaseTicket) {
        if (releaseTicket == 0 || !coasting) return@LaunchedEffect
        var state = rotationFlingLaunch(liveValue, releaseVelocity)
        var previousFrame = 0L
        while (state.phase == RotationFlingPhase.DECAY) {
            withFrameNanos { frame ->
                val dt = if (previousFrame == 0L) 16f else (frame - previousFrame) / 1_000_000f
                previousFrame = frame
                state = rotationFlingFrame(state, dt, swipeBounds)
                liveValue = state.value
                dispatchCrossings(state.value)
            }
        }
        val from = liveValue
        val outOfBounds = from < bounds.min || from > bounds.max
        val target = rotationSettleTarget(from, step, numericLast, detentRadius, swipeBounds)
        if (abs(target - from) > 1e-4f) {
            val duration = minOf(320f, 140f + abs(target - from) * 40f).roundToInt()
            val settle = Animatable(from)
            // Only the tape moves during the settle; the crossing is dispatched once on landing so
            // an overshoot does not tick the same step twice.
            settle.animateTo(target, tween(duration, easing = ROTATION_SETTLE_EASING)) {
                liveValue = this.value
            }
        }
        liveValue = target
        dispatchCrossings(target)
        val onLast = numericLast != null && abs(target - numericLast) < 1e-4f
        if (onLast || outOfBounds) heavyHaptic()
        coasting = false
        // The undo window and the field staying in hand both run from the moment the tape is at rest.
        undoTicket += 1
        armed = true
        armTicket += 1
        // The set is editable again only now: while the tape was spinning the pager, the Log
        // button and the keypad were all held off so nothing could act on a value still moving.
        latestOnInteractionActiveChange(false)
        // The landing is the first value change in a while that no pointer event follows. Ask for
        // one more frame so the accessibility tree is rebuilt on the number the coast stopped on
        // rather than the one it passed through last.
        withFrameNanos { }
    }

    Box(
        modifier = modifier
            .height(104.dp)
            .graphicsLayer {
                val lifted = 1f + 0.03f * maxOf(lift, inHand)
                scaleX = lifted
                scaleY = lifted
            }
            .drawBehind {
                // In hand: a glow in the ring's colour blooms outside the tile, so a tap that only
                // arms the field reads at a glance. Stacked rings fade outward; no blur shader.
                if (inHand <= 0f) return@drawBehind
                val radius = 18.dp.toPx()
                val ringPx = ROTATION_HALO_RING_DP.dp.toPx()
                for (i in 0 until ROTATION_HALO_RINGS) {
                    val fade = 1f - i / ROTATION_HALO_RINGS.toFloat()
                    val out = ringPx * (i + 0.5f)
                    drawRoundRect(
                        color = borderColor.copy(alpha = borderColor.alpha * 0.42f * fade * fade * inHand),
                        topLeft = Offset(-out, -out),
                        size = Size(size.width + 2 * out, size.height + 2 * out),
                        cornerRadius = CornerRadius(radius + out),
                        style = Stroke(ringPx),
                    )
                }
            }
            .clip(shape)
            .background(fillColor, shape)
            .border((1.5f + 1.5f * inHand).dp, borderColor, shape)
            .then(if (!enabled) Modifier else Modifier.pointerInput(enabled, interactionKey) {
                /**
                 * A tap on a still field. The undo chip and the plate glyph live inside the field,
                 * whose gesture owns every tap, so a tap on (or right around) them is routed here;
                 * otherwise a still field arms, an armed field opens the keypad, and a locked set
                 * unlocks and arms.
                 */
                fun stillTap(at: Offset) {
                    val field = placed[0]
                    fun near(slot: Int, padX: Float): Boolean {
                        val target = placed[slot]
                        return field != null &&
                            target != null &&
                            field.isAttached &&
                            target.isAttached &&
                            rotationPlateHitRect(field.localBoundingBoxOf(target), padX, 8.dp.toPx()).contains(at)
                    }
                    val plates = latestOnPlates
                    when {
                        latestUndoVisible && near(2, 8.dp.toPx()) -> undoAdjustment()
                        plates != null && near(1, 12.dp.toPx()) -> plates()
                        latestLocked -> {
                            latestOnUnlock()
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            armed = true
                            armTicket += 1
                        }
                        !armed -> {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            armed = true
                            armTicket += 1
                        }
                        else -> {
                            armed = false
                            // A typed number is deliberate; the chip would offer a value from before it.
                            undoBaseline = null
                            latestOnTap()
                        }
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val wasCoasting = coasting
                    // Spinning tape, or tape that came to rest a moment ago, is still in hand.
                    val live = wasCoasting || (armed && !latestLocked)
                    // A moving page owns an unarmed touch, including the tap that stops a fling.
                    if (!live && pageScroll.ownsTouch()) return@awaitEachGesture
                    if (peekShift.value != 0f || peekShift.isRunning) peekScope.launch { peekShift.snapTo(0f) }
                    val slop = viewConfiguration.touchSlop
                    if (!live) {
                        var maxTravel = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            maxTravel = maxOf(maxTravel, abs(change.position.x - down.position.x), abs(change.position.y - down.position.y))
                            if (maxTravel >= slop) break
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                stillTap(down.position)
                                break
                            }
                        }
                        return@awaitEachGesture
                    }
                    swipeBounds = rotationSwipeBounds(liveValue, isReps, bounds)
                    if (undoBaseline == null) undoBaseline = stepIndex
                    // A new finger takes the tape back mid-coast and starts from where it is now.
                    coasting = false
                    releaseVelocity = 0f
                    if (wasCoasting) releaseTicket += 1
                    // Still tape that is merely in hand is not being worked yet: until a swipe takes it,
                    // the undo chip stays up and a tap on it is still a tap on the chip.
                    isInteracting = wasCoasting
                    var axis = RotationGestureAxis.UNDECIDED
                    var lastY = down.position.y
                    var maxTravel = 0f
                    val track = RotationScrubTrack(RotationScrubSample(down.position.y, down.uptimeMillis, liveValue))
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val totalX = change.position.x - down.position.x
                            val totalY = change.position.y - down.position.y
                            val dragY = change.position.y - lastY
                            maxTravel = maxOf(maxTravel, abs(totalX), abs(totalY))
                            var justLockedVertical = false
                            if (axis == RotationGestureAxis.UNDECIDED) {
                                axis = rotationGestureAxis(totalX, totalY, slop)
                                if (axis == RotationGestureAxis.VERTICAL) {
                                    justLockedVertical = true
                                    latestOnInteractionActiveChange(true)
                                }
                            }
                            isInteracting = when (axis) {
                                RotationGestureAxis.VERTICAL -> true
                                RotationGestureAxis.HORIZONTAL -> false
                                RotationGestureAxis.UNDECIDED -> wasCoasting
                            }
                            if (axis == RotationGestureAxis.VERTICAL) {
                                change.consume()
                                val moved = if (justLockedVertical) totalY else dragY
                                // Lifting without travel keeps the gain earned by the last move and
                                // does not make an old movement sample fresh again.
                                if (moved != 0f) {
                                    gainLevel = rotationScrubGain(
                                        track.speedDp(change.position.y, change.uptimeMillis, density.density),
                                        isReps,
                                    )
                                    // 18dp of travel is still one notch at ×1; the gain multiplies the
                                    // notch, not the mapping, so the slow drag feels exactly as it did.
                                    var dv = -moved / pxPerStep * step * gainLevel
                                    val over = when {
                                        liveValue < swipeBounds.min -> swipeBounds.min - liveValue
                                        liveValue > swipeBounds.max -> liveValue - swipeBounds.max
                                        else -> 0f
                                    }
                                    if (over > 0f) dv *= 0.35f / (1f + over)
                                    liveValue += dv
                                    track.add(RotationScrubSample(change.position.y, change.uptimeMillis, liveValue))
                                    dispatchCrossings(liveValue)
                                }
                            }
                            lastY = change.position.y
                            if (change.changedToUpIgnoreConsumed()) {
                                if (axis == RotationGestureAxis.UNDECIDED && maxTravel < slop) {
                                    // A tap on a spinning tape stops it where it is; the keypad
                                    // needs a second, deliberate tap on a number that is still. On
                                    // still tape that is merely in hand, a tap is an ordinary tap.
                                    change.consume()
                                    if (!wasCoasting) stillTap(down.position)
                                } else if (axis == RotationGestureAxis.VERTICAL) {
                                    releaseVelocity = rotationSwipeReleaseVelocity(
                                        liveValue, track.releaseVelocity(nowMs = change.uptimeMillis), gainLevel, swipeBounds,
                                    )
                                    coasting = true
                                    releaseTicket += 1
                                }
                                break
                            }
                        }
                    } finally {
                        isInteracting = false
                        gainLevel = 1
                        if (!coasting) {
                            latestOnInteractionActiveChange(false)
                            undoTicket += 1
                            // Put down after moving it: the next swipe needs no tap either.
                            if (axis == RotationGestureAxis.VERTICAL && maxTravel >= slop) {
                                armed = true
                                armTicket += 1
                            }
                        }
                    }
                }
            })
            .onGloballyPositioned { placed[0] = it }
            .semantics {
                contentDescription = if (locked) {
                    "$value, $unit. Locked. Tap to unlock."
                } else if (armed) {
                    "$value, $unit. Swipe up or down to adjust. Picked up; tap to type."
                } else {
                    "$value, $unit. Swipe up or down to adjust after a tap."
                }
                val platesAction = onPlates?.let { plates ->
                    CustomAccessibilityAction("Plates") {
                        plates()
                        true
                    }
                }
                customActions = if (locked) {
                    listOfNotNull(
                        platesAction,
                        CustomAccessibilityAction("Unlock $unit") {
                            latestOnUnlock()
                            true
                        },
                    )
                } else {
                    listOfNotNull(
                        platesAction,
                        CustomAccessibilityAction("Increase $unit") {
                            dispatchSteps(1)
                            true
                        },
                        CustomAccessibilityAction("Decrease $unit") {
                            dispatchSteps(-1)
                            true
                        },
                        CustomAccessibilityAction("Type $unit") {
                            latestOnTap()
                            true
                        },
                    )
                }
            },
    ) {
        // What the tape and the chips are drawn from: the live float while it is moving, the
        // ViewModel's value the rest of the time.
        val shownValue = if (isInteracting || coasting) liveValue else numericValue ?: 0f
        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Body (12dp start padding, 10dp top and bottom)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = unit,
                        fontFamily = SheetSansFontFamily,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.6.sp,
                        color = EspressoPalette.dim,
                        maxLines = 1,
                    )
                    if (onPlates != null) {
                        Spacer(Modifier.width(6.dp))
                        PlateGlyph(
                            color = EspressoPalette.dim,
                            modifier = Modifier
                                .onGloballyPositioned { placed[1] = it }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Plates for $value lb"
                                    onClick {
                                        onPlates()
                                        true
                                    }
                                },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // The row's far end holds one thing at a time: the padlock on a logged set, or
                    // the undo chip after an adjustment (an unlocked set has no padlock).
                    AnimatedVisibility(
                        visible = locked,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.6f),
                    ) {
                        LockGlyph(
                            color = EspressoPalette.dim,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    AnimatedVisibility(
                        visible = undoVisible,
                        enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.8f),
                        exit = fadeOut(tween(160)),
                    ) {
                        UndoChip(
                            label = undoLabel,
                            color = tint ?: palette.ink,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .onGloballyPositioned { placed[2] = it }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Undo, back to $undoLabel"
                                    onClick {
                                        undoAdjustment()
                                        true
                                    }
                                },
                        )
                    }
                }
                // The number shrinks until it fits beside the tape: "187.5" is five characters but
                // wider than the column at 42sp, and the tape used to cover its ".5".
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
                    val rolling = remember(value) { rotationRollingDigits(value) }
                    val valueSp = remember(value, maxWidthPx, density, rolling) {
                        rotationFieldValueFontSp(
                            widthAt = { sizeSp ->
                                if (rolling == null) {
                                    rotationMeasuredWidth(textMeasurer, value, sizeSp, 0f)
                                } else {
                                    // The ".5" slot is always reserved, so it is always measured.
                                    rotationMeasuredWidth(textMeasurer, rolling.digits, sizeSp, 0f) +
                                        rotationMeasuredWidth(
                                            textMeasurer, ".5", sizeSp * ROTATION_HALF_SUFFIX_RATIO, -0.5f,
                                        )
                                }
                            },
                            maxWidth = maxWidthPx,
                        )
                    }
                    if (rolling == null) {
                        Text(
                            text = value,
                            fontFamily = LogDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = valueSp.sp,
                            lineHeight = (valueSp + 2f).sp,
                            letterSpacing = (-2).sp,
                            color = valueColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    } else {
                        RotationRollingReadout(
                            digits = rolling.digits,
                            half = rolling.half,
                            rising = rollingUp,
                            sizeSp = valueSp,
                            color = valueColor,
                            textMeasurer = textMeasurer,
                        )
                    }
                }
                val chips = rotationLastLineChips(
                    nowSettled = numericValue?.let { (shownValue / step).roundToInt() * step },
                    last = numericLast,
                    lastText = lastMarkLabel,
                    step = step,
                    unit = if (isReps) null else "lb",
                )
                if (chips != null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        RotationLastLine(
                            chips = chips,
                            palette = palette,
                            maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f,
                            textMeasurer = textMeasurer,
                        )
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Right: Tape (46dp wide, full height, 1dp hairline on its left edge)
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight(),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val centerY = h / 2f
                    // The peek moves the drawing only; everything else reads the real value.
                    val shownValue = shownValue + peekShift.value
                    val centerTickVal = kotlin.math.round(shownValue / step) * step

                    // 1. Ticks — three tiers, the 5s labelled, last time in blue.
                    for (i in -8..8) {
                        val tickVal = centerTickVal + i * step
                        if (tickVal < bounds.min || tickVal > bounds.max) continue
                        // The tape is a wheel: it moves with the finger. Dragging up still means
                        // more, so the next higher value waits below the thumb and rises into it.
                        val tickY = centerY + (tickVal - shownValue) * pxPerUnit
                        if (tickY !in 0f..h) continue
                        // Vertical fade over top 22% and bottom 22%
                        val fadeAlpha = when {
                            tickY < 0.22f * h -> (tickY / (0.22f * h)).coerceIn(0f, 1f)
                            tickY > 0.78f * h -> ((h - tickY) / (0.22f * h)).coerceIn(0f, 1f)
                            else -> 1f
                        }
                        val isMajor = abs(tickVal / 5f - kotlin.math.round(tickVal / 5f)) < 1e-4f
                        val isMid = !isMajor &&
                            !isReps &&
                            abs(tickVal / 2.5f - kotlin.math.round(tickVal / 2.5f)) < 1e-4f
                        val isLast = numericLast != null && abs(tickVal - numericLast) < 1e-4f
                        val tickLength = when {
                            isMajor -> 14.dp.toPx()
                            isMid -> 9.dp.toPx()
                            else -> 5.dp.toPx()
                        }
                        val tierAlpha = when {
                            isMajor -> 0.85f
                            isMid -> 0.6f
                            else -> 0.42f
                        }
                        drawLine(
                            color = if (isLast) {
                                palette.repStrong.copy(alpha = 0.9f * fadeAlpha)
                            } else {
                                tapeColor.copy(alpha = tierAlpha * fadeAlpha)
                            },
                            start = Offset(0f, tickY),
                            end = Offset(tickLength, tickY),
                            strokeWidth = if (isLast) 1.5.dp.toPx() else 1.dp.toPx(),
                        )
                        if (isMajor) {
                            // Right-aligned so the label clears the 22dp thumb.
                            val label = textMeasurer.measure(
                                text = tickVal.roundToInt().toString(),
                                style = TextStyle(
                                    fontFamily = SheetSansFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            drawText(
                                textLayoutResult = label,
                                color = EspressoPalette.dim.copy(alpha = 0.9f * fadeAlpha),
                                topLeft = Offset(
                                    w - 3.dp.toPx() - label.size.width,
                                    tickY - label.size.height / 2f,
                                ),
                            )
                        }
                    }

                    // 2. Fixed Thumb at vertical centre: 22dp x 3dp, 2dp radius, 3dp halo at 22% alpha
                    val thumbWidth = 22.dp.toPx()
                    val thumbHeight = 3.dp.toPx()
                    val thumbTop = centerY - thumbHeight / 2f
                    val haloPadding = 3.dp.toPx()

                    drawRoundRect(
                        color = thumbColor.copy(alpha = 0.22f),
                        topLeft = Offset(0f, thumbTop - haloPadding),
                        size = Size(thumbWidth + haloPadding, thumbHeight + haloPadding * 2f),
                        cornerRadius = CornerRadius(5.dp.toPx()),
                    )
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(0f, thumbTop),
                        size = Size(thumbWidth, thumbHeight),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )

                    // 3. Last-time mark, clamped to the tape — only while last time is off it. On
                    // the tape it is the blue tick above, drawn where the value actually sits.
                    val lastEdge = 16.dp.toPx()
                    val lastOnTape = numericLast != null &&
                        (centerY + (numericLast - shownValue) * pxPerUnit) in lastEdge..(h - lastEdge)
                    if (numericValue != null &&
                        numericLast != null &&
                        lastMarkLabel != null &&
                        numericLast != numericValue &&
                        !lastOnTape
                    ) {
                        val lastY = rotationTapeLastMarkY(
                            center = centerY,
                            value = shownValue,
                            last = numericLast,
                            pxPerUnit = pxPerUnit,
                            // Clear of the chevrons (drawn 2dp-12dp from each edge below).
                            top = lastEdge,
                            bottom = h - lastEdge,
                        )
                        drawRoundRect(
                            color = palette.repStrong,
                            topLeft = Offset(0f, lastY - 1.dp.toPx()),
                            size = Size(12.dp.toPx(), 2.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx()),
                        )
                        val lastText = textMeasurer.measure(
                            text = lastMarkLabel,
                            style = TextStyle(
                                fontFamily = LogUiFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        // Never let the label run past the tape's right edge.
                        val markX = (15.dp.toPx()).coerceAtMost((w - lastText.size.width).coerceAtLeast(0f))
                        drawText(
                            textLayoutResult = lastText,
                            color = palette.repStrong,
                            topLeft = Offset(markX, lastY - lastText.size.height / 2f),
                        )
                    }

                    // 4. Gain badge: what a fast finger is buying, while it is buying it.
                    if (isInteracting && gainLevel > 1) {
                        val badge = textMeasurer.measure(
                            text = "×$gainLevel",
                            style = TextStyle(
                                fontFamily = SheetSansFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        drawText(
                            textLayoutResult = badge,
                            color = accentSoft,
                            topLeft = Offset(
                                w - 2.dp.toPx() - badge.size.width,
                                h - 6.dp.toPx() - badge.size.height,
                            ),
                        )
                    }

                    // 5. Chevrons: 8dp, 1.5dp stroke, inkSubtle 50%, 2dp from edges
                    val chevColor = palette.inkSubtle.copy(alpha = 0.50f)
                    val chevStroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val chevCx = 12.dp.toPx()
                    val chevArm = 4.dp.toPx()

                    val topPath = Path().apply {
                        moveTo(chevCx - chevArm, 2.dp.toPx() + chevArm)
                        lineTo(chevCx, 2.dp.toPx())
                        lineTo(chevCx + chevArm, 2.dp.toPx() + chevArm)
                    }
                    drawPath(topPath, color = chevColor, style = chevStroke)

                    val bottomPath = Path().apply {
                        moveTo(chevCx - chevArm, h - 2.dp.toPx() - chevArm)
                        lineTo(chevCx, h - 2.dp.toPx())
                        lineTo(chevCx + chevArm, h - 2.dp.toPx() - chevArm)
                    }
                    drawPath(bottomPath, color = chevColor, style = chevStroke)
                }
            }
        }
    }
}

/** How long a card rests before its first-sight peek, and how many notches the tape travels. */
internal const val ROTATION_PEEK_DELAY_MS = 700L
internal const val ROTATION_PEEK_NOTCHES = 3f

/**
 * The plate glyph's tap target inside its field: the glyph widened by [padX] a side and deepened
 * by [padBottom], from the field's top edge, so a thumb finds it without landing on the number.
 */
internal fun rotationPlateHitRect(glyph: Rect, padX: Float, padBottom: Float): Rect =
    Rect(glyph.left - padX, 0f, glyph.right + padX, glyph.bottom + padBottom)

/** Two plates on a bar seen from the side: the calculator's way in, beside LB on barbell lifts. */
@Composable
private fun PlateGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 18.dp, height = 13.dp)) {
        val u = size.height / 13f
        val midY = size.height / 2f
        drawLine(color, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        drawRoundRect(color, topLeft = Offset(5f * u, 0f), size = Size(3.2f * u, 13f * u), cornerRadius = CornerRadius(1.2f * u))
        drawRoundRect(color, topLeft = Offset(9.6f * u, 2.5f * u), size = Size(2.6f * u, 8f * u), cornerRadius = CornerRadius(1f * u))
    }
}

/** A small padlock: a logged set's fields hold still until it is tapped open. */
@Composable
private fun LockGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 9.dp, height = 11.dp)) {
        val u = size.width / 9f
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(1.9f * u, 0.7f * u),
            size = Size(5.2f * u, 7f * u),
            style = Stroke(width = 1.3f * u),
        )
        drawLine(color, Offset(1.9f * u, 4.2f * u), Offset(1.9f * u, 5.4f * u), strokeWidth = 1.3f * u)
        drawLine(color, Offset(7.1f * u, 4.2f * u), Offset(7.1f * u, 5.4f * u), strokeWidth = 1.3f * u)
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 5f * u),
            size = Size(9f * u, 6f * u),
            cornerRadius = CornerRadius(1.4f * u),
        )
    }
}

/**
 * The way back after an adjustment: a hooked arrow and the number the tape started from, on a
 * wash of the field's own colour. It overhangs its row rather than growing it, so appearing never
 * moves the big number.
 */
@Composable
private fun UndoChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .requiredHeight(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(start = 5.dp, end = 7.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 10.dp, height = 9.dp)) {
            val u = size.width / 10f
            val stroke = 1.4f * u
            // A half turn over the top, right to left, then the head pointing back down.
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = -180f,
                useCenter = false,
                topLeft = Offset(2f * u, 1.2f * u),
                size = Size(7f * u, 7f * u),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawLine(color, Offset(2f * u, 5.4f * u), Offset(0.2f * u, 3.4f * u), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(2f * u, 5.4f * u), Offset(3.9f * u, 3.5f * u), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontFamily = LogDisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Width of one piece of the readout at a given size, in pixels. */
private fun rotationMeasuredWidth(
    textMeasurer: TextMeasurer,
    text: String,
    sizeSp: Float,
    letterSpacingSp: Float,
): Float = textMeasurer.measure(
    text = text,
    style = TextStyle(
        fontFamily = LogDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = sizeSp.sp,
        letterSpacing = letterSpacingSp.sp,
    ),
    maxLines = 1,
    softWrap = false,
).size.width.toFloat()

/**
 * The number as rolling digit columns.
 *
 * Each column is only as wide as the digit it is showing, so "1" leaves no hole beside "8", and a
 * column that changes slides the new digit in from the side the value is travelling. The half
 * pound is a small ".5" on the same baseline rather than a digit of its own, so a fling through
 * 187 / 187.5 / 188 never resizes or rebuilds the columns — the suffix just fades.
 */
@Composable
private fun RotationRollingReadout(
    digits: String,
    half: Boolean,
    rising: Boolean,
    sizeSp: Float,
    color: Color,
    textMeasurer: TextMeasurer,
) {
    val density = LocalDensity.current
    val digitStyle = TextStyle(
        fontFamily = LogDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp + 2f).sp,
        letterSpacing = ROTATION_DIGIT_TRACKING_SP.sp,
        color = color,
    )
    val suffixStyle = TextStyle(
        fontFamily = LogDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (sizeSp * ROTATION_HALF_SUFFIX_RATIO).sp,
        lineHeight = (sizeSp * ROTATION_HALF_SUFFIX_RATIO + 2f).sp,
        letterSpacing = (-0.5).sp,
        color = color,
    )
    val metrics = remember(sizeSp, textMeasurer, density) {
        val digitLayout = textMeasurer.measure("0", digitStyle, maxLines = 1, softWrap = false)
        val suffixLayout = textMeasurer.measure(".5", suffixStyle, maxLines = 1, softWrap = false)
        with(density) {
            Triple(
                digitLayout.size.height.toDp(),
                suffixLayout.size.width.toDp(),
                // Sit the small suffix on the digits' baseline rather than on their box.
                (digitLayout.firstBaseline - suffixLayout.firstBaseline).toDp(),
            )
        }
    }
    val (rowHeight, suffixWidth, suffixBaselineDp) = metrics
    val rollSpec = tween<Float>(ROTATION_DIGIT_ROLL_MS, easing = LinearOutSlowInEasing)

    // TrendField announces the complete value and owns its accessibility actions. The rolling
    // digits are decorative; exposing their transient AnimatedContent nodes duplicates that value
    // and can hit Compose 1.9's stale-node cache as a digit leaves and re-enters the semantics tree.
    Row(modifier = Modifier.height(rowHeight).clearAndSetSemantics { }) {
        digits.forEachIndexed { index, digit ->
            val targetWidth = remember(digit, sizeSp, textMeasurer, density) {
                with(density) {
                    textMeasurer.measure(digit.toString(), digitStyle, maxLines = 1, softWrap = false)
                        .size.width.toDp()
                }
            }
            val columnWidth by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = tween(ROTATION_DIGIT_ROLL_MS, easing = FastOutSlowInEasing),
                label = "rotationDigitWidth",
            )
            AnimatedContent(
                targetState = digit,
                transitionSpec = {
                    val direction = if (rising) 1 else -1
                    (slideInVertically(rollSpec.toIntSpec()) { h -> direction * h } +
                        fadeIn(tween(ROTATION_DIGIT_ROLL_MS))) togetherWith
                        (slideOutVertically(rollSpec.toIntSpec()) { h -> -direction * h } +
                            fadeOut(tween(ROTATION_DIGIT_ROLL_MS)))
                },
                modifier = Modifier
                    .width(columnWidth)
                    .height(rowHeight)
                    .then(
                        if (index == digits.lastIndex) {
                            Modifier.clipToBoundsOpenRight(overhang = (-ROTATION_DIGIT_TRACKING_SP).sp)
                        } else {
                            Modifier.clipToBounds()
                        },
                    ),
                label = "rotationDigit",
            ) { shown ->
                Text(text = shown.toString(), style = digitStyle, maxLines = 1, softWrap = false)
            }
        }
        // The slot keeps its width whether or not the half is on, so the digits never shift. Skia
        // spends half of the digits' tracking before each glyph and half after, so the last digit's
        // own advance ends that half past its column; the ".5" starts there rather than on a 4.
        Box(
            modifier = Modifier
                .padding(start = with(density) { (-ROTATION_DIGIT_TRACKING_SP / 2f).sp.toDp() })
                .width(suffixWidth)
                .height(rowHeight),
        ) {
            val suffixAlpha by animateFloatAsState(
                targetValue = if (half) 1f else 0f,
                animationSpec = tween(ROTATION_HALF_FADE_MS),
                label = "rotationHalfAlpha",
            )
            val suffixLift by animateFloatAsState(
                targetValue = if (half) 0f else 4f,
                animationSpec = tween(ROTATION_HALF_FADE_MS),
                label = "rotationHalfLift",
            )
            Text(
                text = ".5",
                style = suffixStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .offset(y = suffixBaselineDp + suffixLift.dp)
                    .alpha(suffixAlpha)
                    // The slot is always in the layout so the digits never shift; a faded-out
                    // ".5" must not still be read out as part of the value.
                    .clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Clips a digit column like [clipToBounds], except its right side, which gets [overhang] of room.
 * The roll needs a digit hidden once it slides out of the row, but the tracking makes a column
 * narrower than its glyph, so a flush clip shaved the right edge off the last digit, most visibly a
 * lone rep count like 8. Between two digits a flush edge reads as spacing, so only the last opens.
 */
private fun Modifier.clipToBoundsOpenRight(overhang: TextUnit): Modifier = drawWithContent {
    clipRect(left = 0f, top = 0f, right = size.width + overhang.toPx(), bottom = size.height) {
        this@drawWithContent.drawContent()
    }
}

/** The digit slide and its width animation share one duration and one curve. */
private fun TweenSpec<Float>.toIntSpec(): TweenSpec<IntOffset> =
    tween(durationMillis = this.durationMillis, easing = this.easing)

/**
 * The last line: last time as one blue unit, the change against it as a second chip.
 *
 * "LAST 185 lb" reads as one thing because it is one thing — a set you already did — so the label,
 * the number and the unit share the blue and the pill. The delta is separate because it is a
 * different fact, and it says "MATCHES LAST" rather than showing a zero. The whole line scales to
 * whatever the body column can hold, because a clipped chip reads as a bug.
 */
@Composable
private fun RotationLastLine(
    chips: RotationLastLineChips,
    palette: LogGlassPalette,
    maxWidthPx: Float,
    textMeasurer: TextMeasurer,
) {
    val density = LocalDensity.current
    fun labelStyle(scale: Float, sizeSp: Float, tracking: Float) = TextStyle(
        fontFamily = SheetSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (sizeSp * scale).sp,
        letterSpacing = (tracking * scale).sp,
    )
    fun numberStyle(scale: Float, sizeSp: Float, tracking: Float) = TextStyle(
        fontFamily = LogDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (sizeSp * scale).sp,
        letterSpacing = (tracking * scale).sp,
    )
    fun unitStyle(scale: Float) = TextStyle(
        fontFamily = LogUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (10f * scale).sp,
    )
    fun width(text: String, style: TextStyle): Float =
        textMeasurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toFloat()

    // Every padding and gap in the line, plus the text it has to hold.
    fun lineWidth(s: Float, deltaText: String?): Float = with(density) {
        var total = (15f * s).dp.toPx() +
            width("LAST", labelStyle(s, 8.5f, 1.6f)) +
            (4f * s).dp.toPx() +
            width(chips.lastText, numberStyle(s, 14f, -0.3f))
        chips.unitText?.let { total += (2f * s).dp.toPx() + width(it, unitStyle(s)) }
        if (deltaText != null && chips.delta != null) {
            total += (6f * s).dp.toPx() + (14f * s).dp.toPx()
            total += if (chips.delta.kind == RotationDeltaKind.MATCH) {
                width(deltaText, labelStyle(s, 8.5f, 1.4f))
            } else {
                width("+" + deltaText, numberStyle(s, 12f, -0.2f))
            }
        }
        total
    }

    // "MATCHES LAST" beside "LAST 187.5 lb" is wider than the column at every scale the line is
    // allowed to use, so that one chip says "MATCH" instead of being cut in half.
    val fitted = remember(chips, maxWidthPx, density, textMeasurer) {
        val floor = ROTATION_LAST_LINE_SCALES.last()
        val wanted = chips.delta?.text
        val text = if (
            chips.delta?.kind == RotationDeltaKind.MATCH &&
            maxWidthPx > 0f &&
            lineWidth(floor, wanted) > maxWidthPx
        ) {
            ROTATION_MATCH_TEXT_SHORT
        } else {
            wanted
        }
        text to rotationLastLineScale({ s -> lineWidth(s, text) }, maxWidthPx)
    }
    val deltaText = fitted.first
    val scale = fitted.second

    // Keep one stable spoken comparison while AnimatedContent replaces and clips its visual
    // delta chips; their temporary layout nodes must not enter the browser's semantics cache.
    val spokenDelta = chips.delta?.let { delta ->
        when (delta.kind) {
            RotationDeltaKind.MATCH -> "Matches last time"
            RotationDeltaKind.UP -> "Up ${delta.text}"
            RotationDeltaKind.DOWN -> "Down ${delta.text}"
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy((6f * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = listOfNotNull(
                "Last ${chips.lastText}" + (chips.unitText?.let { " $it" } ?: ""),
                spokenDelta,
            ).joinToString(", ")
        },
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(palette.repStrong.copy(alpha = 0.14f))
                .padding(
                    start = (7f * scale).dp,
                    end = (8f * scale).dp,
                    top = (1f * scale).dp,
                    bottom = (1f * scale).dp,
                ),
            horizontalArrangement = Arrangement.spacedBy((4f * scale).dp),
        ) {
            Text(
                text = "LAST",
                style = labelStyle(scale, 8.5f, 1.6f),
                lineHeight = (14f * scale).sp,
                color = palette.repStrong.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = chips.lastText,
                style = numberStyle(scale, 14f, -0.3f),
                lineHeight = (14f * scale).sp,
                color = palette.repStrong,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            chips.unitText?.let { unitText ->
                Text(
                    text = unitText,
                    style = unitStyle(scale),
                    lineHeight = (14f * scale).sp,
                    color = palette.repStrong.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline().offset(x = (-2f * scale).dp),
                )
            }
        }
        AnimatedContent(
            targetState = chips.delta,
            transitionSpec = {
                fadeIn(tween(ROTATION_CHIP_FADE_MS)) togetherWith fadeOut(tween(ROTATION_CHIP_FADE_MS))
            },
            label = "rotationLastDelta",
        ) { delta ->
            when (delta?.kind) {
                null -> Spacer(Modifier.height((16f * scale).dp))
                RotationDeltaKind.MATCH -> Text(
                    text = deltaText ?: delta.text,
                    style = labelStyle(scale, 8.5f, 1.4f),
                    lineHeight = (12f * scale).sp,
                    color = palette.repStrong,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.repStrong.copy(alpha = 0.14f))
                        .padding(horizontal = (7f * scale).dp, vertical = (2f * scale).dp),
                )
                else -> {
                    val up = delta.kind == RotationDeltaKind.UP
                    val chipColor = if (up) palette.complete else palette.load
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipColor.copy(alpha = 0.16f))
                            .padding(
                                start = (5f * scale).dp,
                                end = (7f * scale).dp,
                                top = (1f * scale).dp,
                                bottom = (1f * scale).dp,
                            ),
                    ) {
                        // A sign, not a caret: the caret sat off the number's centre line.
                        Text(
                            text = (if (up) "+" else "\u2212") + delta.text,
                            style = numberStyle(scale, 12f, -0.2f),
                            lineHeight = (14f * scale).sp,
                            color = chipColor,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Last session laid over today's strip. Every one of last session's sets lands in exactly one
 * place, so none of them needs a new set added today before it can be seen:
 * [overSets] is the tab on each of today's boxes (null where last session has nothing for it),
 * [overAdd] is the set the + box would add next, and [trailing] is whatever is left, each with
 * its own "SET 4" / "W1" name because no box under it says which set it was.
 */
internal data class RotationLastTimeTabs(
    val overSets: List<GhostSetUi?>,
    val overAdd: GhostSetUi?,
    val trailing: List<Pair<String, GhostSetUi>>,
)

/**
 * The one rule for which of last session's sets a set today is measured against — the number
 * this set is trying to beat. Same kind, same number: today's working set 2 meets last session's
 * working set 2 however many warm-ups either day opened with, and a warm-up only meets a warm-up.
 */
internal fun rotationMatchedGhost(ghosts: List<GhostSetUi>, todayIsWarmup: List<Boolean>, index: Int): GhostSetUi? {
    val isWarmup = todayIsWarmup.getOrNull(index) ?: return null
    val ordinal = todayIsWarmup.take(index + 1).count { it == isWarmup }
    return ghosts.filter { it.isWarmup == isWarmup }.getOrNull(ordinal - 1)
}

internal fun rotationLastTimeTabs(todayIsWarmup: List<Boolean>, ghosts: List<GhostSetUi>): RotationLastTimeTabs {
    val overSets = todayIsWarmup.indices.map { rotationMatchedGhost(ghosts, todayIsWarmup, it) }
    val todayWarmups = todayIsWarmup.count { it }
    val todayWorking = todayIsWarmup.size - todayWarmups
    var warmups = 0
    var working = 0
    var overAdd: GhostSetUi? = null
    val trailing = mutableListOf<Pair<String, GhostSetUi>>()
    ghosts.forEach { ghost ->
        val ordinal = if (ghost.isWarmup) ++warmups else ++working
        // Today already has this set, so its box wears the tab.
        if (ordinal <= (if (ghost.isWarmup) todayWarmups else todayWorking)) return@forEach
        if (!ghost.isWarmup && ordinal == todayWorking + 1) {
            overAdd = ghost
        } else {
            trailing += (if (ghost.isWarmup) "W$ordinal" else "SET $ordinal") to ghost
        }
    }
    return RotationLastTimeTabs(overSets, overAdd, trailing)
}

/** Tab text with no spaces, so "BW+25×12" still fits over a narrow box. */
internal fun rotationLastTimeTabLabel(ghost: GhostSetUi): Pair<String, String> =
    rotationLoadLabel(ghost.weightLbs, ghost.isBodyweight).replace(" ", "") to (ghost.reps ?: 0).toString()

/** "Jun 1", with the year only when last session was not this year. */
internal fun rotationLastTimeDateLabel(isoDate: String?, currentYear: Int): String? {
    if (isoDate.isNullOrBlank()) return null
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
    val day = WorkoutDates.formatAxis(isoDate)
    return if (date.year == currentYear) day else "$day, ${date.year}"
}

private const val ROTATION_LAST_TIME_HEAD_DP = 14
private const val ROTATION_LAST_TIME_TAB_DP = 26
/** Height the last-time header and tab row add above the set strip. Keep in step with [RotationCard]. */
internal const val ROTATION_CARD_LAST_TIME_DP = ROTATION_LAST_TIME_HEAD_DP + 2 + ROTATION_LAST_TIME_TAB_DP

/** "LAST TIME  Jun 1 ————": names the blue tabs under it once, so the tabs carry only numbers. */
@Composable
private fun LastTimeHeader(isoDate: String?, setCount: Int, palette: LogGlassPalette, modifier: Modifier = Modifier) {
    val currentYear = remember { LocalDate.parse(WorkoutDates.today()).year }
    val dateLabel = rotationLastTimeDateLabel(isoDate, currentYear)
    val spoken = listOfNotNull("Last time", dateLabel, "$setCount ${if (setCount == 1) "set" else "sets"}").joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().height(ROTATION_LAST_TIME_HEAD_DP.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            text = "LAST TIME",
            fontFamily = SheetSansFontFamily, fontSize = 9.sp, lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp, color = palette.repStrong,
        )
        dateLabel?.let { date ->
            Text(
                text = date,
                fontFamily = LogDisplayFontFamily, fontSize = 12.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold, color = palette.ink,
            )
        }
        Box(
            Modifier.weight(1f).height(1.dp).background(
                Brush.horizontalGradient(listOf(palette.repStrong.copy(alpha = 0.35f), Color.Transparent)),
            ),
        )
    }
}

/**
 * One of last session's sets, as a tab. Solid when it sits on today's box for the same set;
 * dashed when that set has not been added today ([attached] = it still has the + box under it).
 */
@Composable
private fun LastTimeTab(
    ghost: GhostSetUi?,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dashed: Boolean = false,
    attached: Boolean = true,
    accessibilityLabel: String? = null,
) {
    if (ghost == null) {
        Spacer(modifier.height(ROTATION_LAST_TIME_TAB_DP.dp))
        return
    }
    val blue = palette.repStrong
    val (load, reps) = rotationLastTimeTabLabel(ghost)
    val bottomRadius = if (attached) 0.dp else 10.dp
    val shape = RoundedCornerShape(10.dp, 10.dp, bottomRadius, bottomRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(ROTATION_LAST_TIME_TAB_DP.dp)
            .padding(horizontal = if (dashed) 2.dp else 5.dp)
            .then(
                if (dashed) {
                    Modifier.clipToBounds().drawBehind {
                        val stroke = 1.5.dp.toPx()
                        val r = 10.dp.toPx()
                        // An attached tab leaves its bottom edge open: it runs into the + box.
                        val bottom = if (attached) size.height + r else size.height - stroke / 2f
                        drawRoundRect(
                            color = blue.copy(alpha = 0.45f),
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, bottom - stroke / 2f),
                            cornerRadius = CornerRadius(r),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            ),
                        )
                    }
                } else {
                    Modifier.clip(shape).background(blue.copy(alpha = if (selected) 0.26f else 0.13f), shape)
                }
            )
            .then(
                if (accessibilityLabel != null) Modifier.semantics { contentDescription = accessibilityLabel }
                // The box under a solid tab already reads "…, last 185×8".
                else Modifier.clearAndSetSemantics { }
            ),
    ) {
        Text(
            text = buildAnnotatedString {
                append(load)
                withStyle(SpanStyle(color = blue.copy(alpha = 0.55f))) { append("×") }
                append(reps)
            },
            fontFamily = LogDisplayFontFamily, fontSize = 12.5.sp, lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold, color = blue.copy(alpha = if (dashed) 0.8f else 1f),
            maxLines = 1, softWrap = false,
        )
    }
}

/**
 * One set in the strip. Shows today's editable values and completion state. Tap to select it; the fields above follow.
 * The selected box carries an × badge to remove it.
 */
@Composable
private fun SetSegment(
    setNumber: Int,
    isWarmup: Boolean = false,
    label: String,
    showsLastTime: Boolean,
    logged: Boolean,
    selected: Boolean,
    accent: Color,
    accentSoft: Color,
    cardBackground: Color,
    palette: LogGlassPalette,
    accessibilityLabel: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Espresso: current = filled muscle colour; logged = green tint with a green ring and dot;
    // pending = the quiet tile. No borders otherwise.
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        logged -> palette.complete
        else -> Color.Transparent
    }
    val fillColor = when {
        logged -> palette.complete.copy(alpha = 0.22f)
        selected -> accent
        else -> EspressoPalette.panel2
    }
    val onTile = if (selected && !logged) EspressoPalette.onColor else palette.ink
    val numberColor = when {
        showsLastTime && !selected -> palette.repStrong
        else -> onTile
    }

    Box(modifier = modifier.height(60.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fillColor, shape)
                .border(1.5.dp, borderColor, shape)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                }
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            if (logged) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.complete),
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = if (isWarmup) "WARM-UP" else "SET $setNumber",
                    fontFamily = SheetSansFontFamily,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = if (selected && !logged) EspressoPalette.onColor.copy(alpha = 0.75f) else if (logged) palette.complete else EspressoPalette.dim,
                )
                Text(
                    text = label,
                    fontFamily = LogUiFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = numberColor,
                    maxLines = 1,
                    softWrap = false,
                )
                if (showsLastTime) {
                    Text(
                        text = "LAST",
                        fontFamily = LogUiFontFamily,
                        fontSize = 9.5.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                        color = palette.repStrong.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // × badge on the selected box only
        if (selected && onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(EspressoPalette.panel)
                    .border(1.5.dp, palette.danger, CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Remove set $setNumber"
                    }
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    val stroke = 1.5.dp.toPx()
                    val pad = 1.5.dp.toPx()
                    drawLine(
                        color = palette.danger,
                        start = Offset(pad, pad),
                        end = Offset(size.width - pad, size.height - pad),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = palette.danger,
                        start = Offset(size.width - pad, pad),
                        end = Offset(pad, size.height - pad),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** One exercise, full card. */
@Composable
internal fun RotationCard(
    exercise: ExerciseLog,
    positionLabel: String,
    splitLabel: String,
    visibleSets: List<LoggedSet>,
    selectedSetId: Long?,
    palette: LogGlassPalette,
    onWeightStep: (Long, Int) -> Unit,
    onRepsStep: (Long, Int) -> Unit,
    onWeightInput: (Long) -> Unit,
    onRepsInput: (Long) -> Unit,
    onRecommend: () -> Unit,
    onMuscleGroupChange: (String) -> Unit,
    recommendationState: RecommendationState,
    onSelectSet: (LoggedSet) -> Unit,
    onToggleLogged: (LoggedSet) -> Unit,
    onToggleWarmup: (Long) -> Unit = {},
    onAddSet: () -> Unit,
    onRemoveSet: (LoggedSet) -> Unit,
    onRemoveExercise: () -> Unit,
    onSwapExercise: (() -> Unit)? = null,
    onUndoSwap: (() -> Unit)? = null,
    swapIsToday: Boolean = true,
    onPark: () -> Unit,
    onNumberScrubActiveChange: (String, Boolean) -> Unit = { _, _ -> },
    numberScrubActive: Boolean = false,
    addSetEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    showFullTrend: Boolean = true,
    onExpandTrend: () -> Unit = {},
    onPlaced: (card: Rect, head: Rect) -> Unit = { _, _ -> },
    onOpenPlates: (Float) -> Unit = {},
    peekScrub: Boolean = false,
    onPeekDone: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onTaken: () -> Unit = {},
) {
    val accent = rotationRegionColor(exercise.muscleGroups)
    val placed = remember { arrayOfNulls<Rect>(2) }
    val report = { slot: Int, rect: Rect ->
        placed[slot] = rect
        val (card, head) = placed
        if (card != null && head != null) onPlaced(card, head)
    }
    // Espresso: the muscle colour is used as-is; text on it is always the dark ink.
    val accentSoft = accent
    val accentInk = EspressoPalette.onColor
    val cardBackground = EspressoPalette.panel
    val heavyHaptic = rememberHeavyHaptic()
    val lightHaptic = rememberLightHaptic()
    val loggedCount = visibleSets.count { it.isCompleted }
    // The fields follow whatever set you tapped, so a logged set is still editable.
    val activeSet = visibleSets.firstOrNull { it.id == selectedSetId }
    val activeIndex = visibleSets.indexOfFirst { it.id == selectedSetId }
    val activeOrdinal = activeSet?.let { rotationSetOrdinal(visibleSets, it) }
    val nextWorkingOrdinal = visibleSets.count { !it.isWarmup } + 1
    val lastTimeTabs = remember(visibleSets, exercise.ghostSets) {
        rotationLastTimeTabs(visibleSets.map { it.isWarmup }, exercise.ghostSets)
    }
    // The fields read the very set the tab over the active box shows, so the two cannot disagree.
    val ghost = lastTimeTabs.overSets.getOrNull(activeIndex)
    val hasLastTime = exercise.ghostSets.isNotEmpty()
    val activeLogged = activeSet?.isCompleted == true
    // A logged set's fields are locked until tapped open, so a finished workout reopened to show
    // someone cannot be changed by a stray thumb. Open lasts while you stay on that set; moving to
    // another set, or unlogging this one, locks it again.
    var unlockedSetId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(activeSet?.id, activeLogged) {
        if (unlockedSetId != null && (unlockedSetId != activeSet?.id || !activeLogged)) unlockedSetId = null
    }
    val fieldsLocked = activeLogged && activeSet?.id != unlockedSetId
    val unlockFields = { unlockedSetId = activeSet?.id }
    // The strip folds into the card at every width. Growing the deck put Log set under the bar.
    val unsavedEditing = LocalUnsavedSetEditing.current
    val unsaved = activeSet?.let { unsavedEditing?.forSet(it.id, it.syncId) }
    val foldStrip = unsaved != null
    val upperScroll = rememberScrollState()
    val storedWeight = activeSet?.let(::rotationDisplayedWeightOf)
    val reps = activeSet?.let(::rotationDisplayedRepsOf)
    val isBw = activeSet?.isBodyweight == true
    val trendHistory = remember(exercise.e1rmHistory) { exercise.e1rmHistory.map { it.e1rm } }
    val trendDates = remember(exercise.e1rmHistory) { exercise.e1rmHistory.map { it.date } }
    val todayPoint = rotationTodayE1rm(visibleSets, activeSet, exercise.userBodyweightLbs)

    val cardShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .onGloballyPositioned { report(0, it.boundsInRoot()) }
            .clip(cardShape)
            .background(cardBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(bottom = 16.dp),
        ) {
            @Composable
            fun ColumnScope.cardHeadFields() {
            // 1. Head: the muscle colour as a solid block; eyebrow and name in dark ink.
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { report(1, it.boundsInRoot()) }
                    .background(accent)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = if (onUndoSwap != null) if (swapIsToday) "SWAPPED FOR TODAY" else "SWAPPED FOR THIS DAY"
                               else if (splitLabel.isBlank()) positionLabel
                               else "$positionLabel · ${splitLabel.uppercase()}",
                        fontFamily = SheetSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        letterSpacing = 2.sp,
                        color = accentInk.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onUndoSwap != null) TextButton(onClick = onUndoSwap,
                        modifier = Modifier.height(20.dp).widthIn(min = 48.dp)
                            .semantics { contentDescription = "Undo exercise swap" },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Undo", fontFamily = LogUiFontFamily, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = accentInk)
                    }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exercise.exerciseName,
                            style = rotationExerciseNameStyle(),
                            color = accentInk,
                            minLines = ROTATION_NAME_MAX_LINES,
                            maxLines = ROTATION_NAME_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { contentDescription = exercise.exerciseName },
                        )
                        Spacer(Modifier.width(8.dp))
                        // Only the chevron is the door (Ayman, 2026-10-02): the press highlight
                        // stays on this 32dp circle instead of lighting up the whole name.
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .semantics { contentDescription = "Open ${exercise.exerciseName} progress" }
                                .clickable(role = Role.Button, onClick = onOpenProgress),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(22.dp).background(accentInk, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                // A 36dp action can extend its hit area 6dp to meet Compose's 48dp minimum:
                // 14dp of layout space leaves 8dp between that hit area and the report door.
                Spacer(Modifier.width(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val btnShape = CircleShape
                    // Remove exercise
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(btnShape)
                            .background(Color.Black.copy(alpha = 0.25f), btnShape)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Remove ${exercise.exerciseName}"
                            }
                            .clickable(role = Role.Button, onClick = onRemoveExercise),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val stroke = 1.5.dp.toPx()
                            val color = EspressoPalette.ink
                            val s = size.width / 24f
                            drawLine(color, Offset(4f * s, 7f * s), Offset(20f * s, 7f * s), strokeWidth = stroke, cap = StrokeCap.Round)
                            val lid = Path().apply {
                                moveTo(9f * s, 7f * s)
                                lineTo(9f * s, 4f * s)
                                lineTo(15f * s, 4f * s)
                                lineTo(15f * s, 7f * s)
                            }
                            drawPath(lid, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            val body = Path().apply {
                                moveTo(6f * s, 7f * s)
                                lineTo(7f * s, 20f * s)
                                lineTo(17f * s, 20f * s)
                                lineTo(18f * s, 7f * s)
                            }
                            drawPath(body, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawLine(color, Offset(10f * s, 11f * s), Offset(10f * s, 17f * s), strokeWidth = stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(14f * s, 11f * s), Offset(14f * s, 17f * s), strokeWidth = stroke, cap = StrokeCap.Round)
                        }
                    }

                    // Done
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(btnShape)
                            .background(Color.Black.copy(alpha = 0.25f), btnShape)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Done with ${exercise.exerciseName}"
                            }
                            .clickable(role = Role.Button, onClick = onPark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = EspressoPalette.ink,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 2. Chip row: muscle, contextual taken action and the existing set progress.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            ) {
                // Muscle chip (anchor for MuscleGroupSelector menu)
                var muscleMenuExpanded by remember { mutableStateOf(false) }
                // Same states as MuscleGroupSelector: tagged, untagged with a suggestion, untagged.
                val selectedGroup = primaryMuscleGroup(exercise.muscleGroups)
                val needsMuscle = selectedGroup == null
                val muscleSuggestion = exercise.suggestedMuscleGroup?.takeIf { needsMuscle }
                val primaryGroup = selectedGroup?.uppercase() ?: ""
                val muscleChipLabel = when {
                    muscleSuggestion != null -> "SUGGESTED ${formatMuscleGroupLabel(muscleSuggestion).uppercase()}"
                    needsMuscle -> "+ TAG MUSCLE"
                    else -> formatMuscleGroupLabel(exercise.muscleGroups).uppercase()
                }
                val muscleChipTint = if (needsMuscle) palette.load else accent
                val muscleChipBase = if (needsMuscle) palette.load else accent
                val muscleChipShape = RoundedCornerShape(10.dp)
                // Measured last: it takes the width left by the action and progress chips.
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .heightIn(min = 26.dp)
                            .clip(muscleChipShape)
                            .background(EspressoPalette.tint(muscleChipBase), muscleChipShape)
                            .clickable(role = Role.Button) { muscleMenuExpanded = true }
                            .padding(horizontal = 9.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = when {
                                    muscleSuggestion != null -> "$muscleChipLabel. Choose muscle group"
                                    needsMuscle -> "Tag muscle. Choose muscle group"
                                    else -> "Change muscle group from $primaryGroup"
                                }
                                stateDescription = if (muscleMenuExpanded) "Muscle group menu open" else "Muscle group menu closed"
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(muscleChipTint),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = muscleChipLabel,
                            fontFamily = LogUiFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                            color = muscleChipTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    DropdownMenu(
                        expanded = muscleMenuExpanded,
                        onDismissRequest = { muscleMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 208.dp, max = 232.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = palette.glassFillStrong.copy(alpha = 0.96f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, palette.glassStrokeStrong),
                    ) {
                        selectableBodyPartGroups.forEach { choice ->
                            val isSelected = choice.equals(primaryGroup, ignoreCase = true)
                            DropdownMenuItem(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (isSelected) palette.accent.copy(alpha = 0.18f) else Color.Transparent
                                    ),
                                text = {
                                    Text(
                                        text = choice.lowercase().replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() },
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) palette.ink else palette.inkMuted,
                                        maxLines = 1,
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                if (isSelected) palette.complete.copy(alpha = 0.94f) else palette.glassFill.copy(alpha = 0.82f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) palette.complete else palette.glassStroke,
                                                shape = RoundedCornerShape(7.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF06100F),
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    muscleMenuExpanded = false
                                    onMuscleGroupChange(choice)
                                },
                            )
                        }
                    }
                }

                if (exercise.showTakenChip) {
                    val takenShape = RoundedCornerShape(10.dp)
                    Box(Modifier.height(40.dp).clip(takenShape)
                        .border(1.dp, palette.load, takenShape)
                        .background(EspressoPalette.tint(palette.load), takenShape)
                        .clickable(role = Role.Button, onClick = onTaken)
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = "${exercise.takenChipLabel} ${exercise.exerciseName}"
                        }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(exercise.takenChipLabel, fontFamily = LogUiFontFamily,
                            fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = palette.load,
                            maxLines = 1, softWrap = false)
                    }
                }

                // Progress chip: dashes + "n OF m"
                val progressChipShape = RoundedCornerShape(10.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 26.dp)
                        .clip(progressChipShape)
                        .background(EspressoPalette.panel2, progressChipShape)
                        .padding(horizontal = 10.dp),
                ) {
                    if (visibleSets.size > 6) {
                        Box(Modifier.width(64.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(EspressoPalette.hair2)) {
                            Box(Modifier.fillMaxWidth(loggedCount.toFloat() / visibleSets.size).fillMaxHeight().background(palette.complete))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        visibleSets.forEach { set ->
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            set.isCompleted -> palette.complete
                                            set.id == selectedSetId -> accent
                                            else -> EspressoPalette.hair2
                                        }
                                    ),
                            )
                        }
                    }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$loggedCount OF ${visibleSets.size}",
                        fontFamily = LogUiFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                        color = EspressoPalette.dim,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (onSwapExercise != null && !exercise.showTakenChip) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onSwapExercise)
                            .padding(horizontal = 9.dp)
                            .semantics { contentDescription = "Swap ${exercise.exerciseName} for ${if (swapIsToday) "today" else "this workout"}" }) {
                        Icon(Icons.Filled.SwapHoriz, null, Modifier.size(17.dp), tint = accent)
                        Spacer(Modifier.width(4.dp))
                        Text("Swap", fontFamily = LogUiFontFamily, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = accent)
                    }
                }
            }

            // The estimate block yields its height while the strip is folded in, so the name and the fields stay on screen.
            if (!foldStrip) {
                Spacer(Modifier.height(16.dp))
                val isBodyweightExercise = visibleSets.any { it.isBodyweight }
                val cardBodyweightLbs = exercise.userBodyweightLbs
                if (showFullTrend) {
                    TrendChart(
                        history = trendHistory,
                        historyDates = trendDates,
                        today = todayPoint?.first,
                        todayLogged = todayPoint?.second == true,
                        accent = accent,
                        accentSoft = accentSoft,
                        cardBackground = cardBackground,
                        palette = palette,
                        caption = cardBodyweightLbs.takeIf { isBodyweightExercise }?.let { "$it lb" },
                        bodyweightMissing = isBodyweightExercise && cardBodyweightLbs == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                    )
                } else {
                    TextButton(onClick = onExpandTrend, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Text(
                            text = todayPoint?.first?.let { "${it.roundToInt()} lb · Est. 1RM · History" } ?: "History",
                            color = palette.inkMuted,
                            fontFamily = LogUiFontFamily,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4. Fields (vertical sliders)
            val lastWeightLbs = ghost?.weightLbs?.let { WeightLbs.toLbs(it).toFloat() }
            val nowWeightLbs = storedWeight?.let { WeightLbs.toLbs(it).toFloat() }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            ) {
                TrendField(
                    value = if (activeSet == null) "—" else rotationLoadLabel(storedWeight, isBw),
                    unit = if (isBw) "ADDED LB" else "LB",
                    numericValue = nowWeightLbs,
                    numericLast = lastWeightLbs,
                    lastMarkLabel = ghost?.let { rotationTapeLoadLabel(it.weightLbs, it.isBodyweight) },
                    pxPerUnit = with(LocalDensity.current) { 18.dp.toPx() },
                    tint = accent,
                    logged = activeLogged,
                    palette = palette,
                    enabled = activeSet != null,
                    interactionKey = rotationFieldIdentity(activeSet),
                    onStep = { steps -> activeSet?.let { onWeightStep(it.id, rotationWeightDelta(steps)) } },
                    onTap = { activeSet?.let { onWeightInput(it.id) } },
                    onInteractionActiveChange = { onNumberScrubActiveChange("${rotationFieldIdentity(activeSet)}:weight", it) },
                    modifier = Modifier.weight(1f),
                    // Barbell lifts only: the plate glyph beside LB opens the plate calculator.
                    onPlates = if (activeSet != null && !isBw && isBarbellLift(exercise.exerciseName)) {
                        { onOpenPlates(nowWeightLbs ?: 0f) }
                    } else {
                        null
                    },
                    peek = peekScrub,
                    onPeekDone = onPeekDone,
                    locked = fieldsLocked,
                    onUnlock = unlockFields,
                )
                TrendField(
                    value = if (activeSet == null) "—" else (reps?.toString() ?: "0"),
                    unit = "REPS",
                    numericValue = reps?.toFloat(),
                    numericLast = ghost?.reps?.toFloat(),
                    lastMarkLabel = ghost?.reps?.toString(),
                    pxPerUnit = with(LocalDensity.current) { 26.dp.toPx() },
                    // Both fields wear the lift's colour; an ink reps field read as a different control.
                    tint = accent,
                    logged = activeLogged,
                    palette = palette,
                    enabled = activeSet != null,
                    interactionKey = rotationFieldIdentity(activeSet),
                    onStep = { steps -> activeSet?.let { onRepsStep(it.id, steps) } },
                    onTap = { activeSet?.let { onRepsInput(it.id) } },
                    onInteractionActiveChange = { onNumberScrubActiveChange("${rotationFieldIdentity(activeSet)}:reps", it) },
                    modifier = Modifier.weight(1f),
                    locked = fieldsLocked,
                    onUnlock = unlockFields,
                )
            }

            Spacer(Modifier.height(12.dp))
            }

            @Composable
            fun ColumnScope.cardLastTimeAndSets() {
            if (hasLastTime) {
                LastTimeHeader(exercise.lastSessionDate, exercise.ghostSets.size, palette, Modifier.padding(horizontal = 18.dp))
                Spacer(Modifier.height(2.dp))
            }

            // 5. Set strip: set segments + dashed + box at the end. Each column carries last
            // session's set for that slot as a tab on top, so then and now read straight down.
            val addHasTab = lastTimeTabs.overAdd != null
            val strip = visibleSets.size + lastTimeTabs.trailing.size + (if (addHasTab) 1 else 0) > 4
            val stripScroll = rememberScrollState()
            var stripWidth by remember { mutableStateOf(0) }
            val stripDensity = LocalDensity.current
            val stripSetCount = visibleSets.size
            val stripColumnPx = with(stripDensity) { 84.dp.toPx() }
            val stripAddPx = with(stripDensity) { 50.dp.toPx() }
            LaunchedEffect(strip, activeIndex, stripSetCount, stripWidth) {
                if (strip && stripWidth > 0) {
                    val target = rotationSetStripTarget(
                        activeIndex, stripSetCount, stripWidth, stripScroll.value, stripColumnPx, stripAddPx,
                    )
                    stripScroll.animateScrollTo(target)
                }
            }
            // Switching scroll containers removes their semantic parent. Recreate this subtree
            // with it so Compose Web cannot retain child IDs from the previous parent.
            key(strip) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 6.dp)
                        .onSizeChanged { stripWidth = it.width }
                        .then(if (strip) Modifier.horizontalScroll(stripScroll) else Modifier),
                ) {
                    val columnWidth = if (strip) Modifier.width(78.dp) else Modifier.weight(1f)
                    visibleSets.forEachIndexed { index, set ->
                        val setGhost = lastTimeTabs.overSets.getOrNull(index)
                        val label = rotationSetDisplayLabel(
                            rotationDisplayedWeightOf(set), rotationDisplayedRepsOf(set), set.isBodyweight,
                        )
                        Column(modifier = columnWidth) {
                            if (hasLastTime) {
                                LastTimeTab(setGhost, palette, Modifier.fillMaxWidth(), selected = set.id == selectedSetId)
                            }
                            SetSegment(
                                setNumber = rotationSetOrdinal(visibleSets, set),
                                isWarmup = set.isWarmup,
                                label = label,
                                showsLastTime = false,
                                logged = set.isCompleted,
                                selected = set.id == selectedSetId,
                                accent = accent,
                                accentSoft = accentSoft,
                                cardBackground = cardBackground,
                                palette = palette,
                                accessibilityLabel = rotationLedgerRowAccessibilityLabel(
                                    setNumber = rotationSetOrdinal(visibleSets, set),
                                    set = set,
                                    ghost = setGhost,
                                ),
                                onClick = { onSelectSet(set) },
                                onRemove = if (set.id == selectedSetId) { { onRemoveSet(set) } } else {
                                    null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Dashed Add Set box at the end of the strip. When last session has a set the +
                    // would add next, the box widens to a full column and wears that set as a dashed tab.
                    Column(modifier = if (addHasTab) columnWidth else Modifier.width(44.dp)) {
                        lastTimeTabs.overAdd?.let { next ->
                            val (load, reps) = rotationLastTimeTabLabel(next)
                            LastTimeTab(
                                next, palette, Modifier.fillMaxWidth(), dashed = true,
                                accessibilityLabel = "Last time set $nextWorkingOrdinal, $load×$reps, not added today",
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .drawBehind {
                                    val strokeWidth = 1.5.dp.toPx()
                                    val halfStroke = strokeWidth / 2f
                                    drawRoundRect(
                                        color = if (addSetEnabled) EspressoPalette.hair2 else palette.inkSubtle.copy(alpha = 0.20f),
                                        topLeft = Offset(halfStroke, halfStroke),
                                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                        cornerRadius = CornerRadius(14.dp.toPx()),
                                        style = Stroke(
                                            width = strokeWidth,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                                        ),
                                    )
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = if (addSetEnabled) "Add set $nextWorkingOrdinal" else "Adding set"
                                }
                                .clickable(enabled = addSetEnabled, role = Role.Button, onClick = onAddSet),
                        ) {
                            Text(
                                text = if (addSetEnabled) "+" else "…",
                                fontFamily = LogUiFontFamily,
                                fontSize = if (addSetEnabled) 20.sp else 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (addSetEnabled) accentSoft else palette.inkSubtle,
                            )
                        }
                    }

                    // Last session's sets with no slot today: a free-standing dashed tab with its name.
                    lastTimeTabs.trailing.forEach { (name, leftover) ->
                        val (load, reps) = rotationLastTimeTabLabel(leftover)
                        Column(modifier = columnWidth.height((ROTATION_LAST_TIME_TAB_DP + 60).dp)) {
                            LastTimeTab(
                                leftover, palette, Modifier.fillMaxWidth(), dashed = true, attached = false,
                                accessibilityLabel = "Last time $name, $load×$reps, not added today",
                            )
                            Text(
                                text = name,
                                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                                textAlign = TextAlign.Center,
                                fontFamily = SheetSansFontFamily, fontSize = 9.sp, lineHeight = 10.sp,
                                fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
                                color = palette.repStrong.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
            }
            if (foldStrip) {
                // A scroll child has no max height, so a weight spacer inside it takes nothing
                // and the empty space lands under the set chips. Give the scroll content the
                // viewport's height and keep that empty space above the set chips. A shorter
                // window still opens at the top, so the name stays in view.
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val viewportPx = constraints.maxHeight
                    Layout(
                        modifier = Modifier.fillMaxWidth().verticalScroll(upperScroll),
                        content = {
                            Column(Modifier.fillMaxWidth()) { cardHeadFields() }
                            Column(Modifier.fillMaxWidth()) { cardLastTimeAndSets() }
                        },
                    ) { measurables, constraints ->
                        val width = constraints.maxWidth
                        val loose = Constraints(maxWidth = width)
                        val top = measurables[0].measure(loose)
                        val bottom = measurables[1].measure(loose)
                        val height = maxOf(
                            if (viewportPx == Constraints.Infinity) 0 else viewportPx,
                            top.height + bottom.height,
                        )
                        layout(width, height) {
                            top.place(0, 0)
                            bottom.place(0, height - bottom.height)
                        }
                    }
                }
            } else {
                cardHeadFields()
                // A shorter name or a missing last session leaves slack here, so the set chips
                // stay at the bottom of the card.
                Spacer(Modifier.weight(1f))
                cardLastTimeAndSets()
            }

            Spacer(Modifier.height(12.dp))

            // A save storage refused stays here with Retry until it saves or is discarded.
            if (unsavedEditing != null && unsaved != null) {
                val (unsavedKey, unsavedInput) = unsaved
                UnsavedSetStrip(
                    input = unsavedInput,
                    loadLabel = rotationLoadLabel(unsavedInput.attempted.weightLbs, unsavedInput.attempted.isBodyweight),
                    onRetry = { unsavedEditing.onRetry(unsavedKey) },
                    onDiscard = { unsavedEditing.onDiscard(unsavedKey) },
                    accent = accent,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.height(6.dp))
            }

            // 6. Action row: Log button + WARM / REC pill
            val logRowSpot = LocalRotationLogRowSpot.current
            val logRowOwner = remember { Any() }
            val windowWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
            val stripStay = remember { RotationStripStay() }
            fun countStripIfDue() {
                if (stripStay.shouldLift(foldStrip, logRowSpot.owner, logRowOwner)) logRowSpot.stripArrivals++
            }
            LaunchedEffect(logRowSpot, foldStrip) {
                if (!foldStrip) {
                    stripStay.end()
                    return@LaunchedEffect
                }
                // A card arriving with its strip takes a few frames to place; only the on-screen one takes ownership.
                repeat(45) {
                    withFrameNanos { }
                    countStripIfDue()
                    if (stripStay.counted) return@LaunchedEffect
                }
            }
            DisposableEffect(logRowSpot) {
                onDispose {
                    if (logRowSpot.owner === logRowOwner) {
                        logRowSpot.owner = null
                        logRowSpot.topPx = 0f
                        logRowSpot.bottomPx = 0f
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        if (rotationLogRowOnScreen(bounds, windowWidthPx)) {
                            logRowSpot.owner = logRowOwner
                            logRowSpot.topPx = bounds.top
                            logRowSpot.bottomPx = bounds.bottom
                            countStripIfDue()
                        } else {
                            stripStay.end()
                        }
                    },
            ) {
                val buttonShape = RoundedCornerShape(18.dp)
                val primaryEnabled = activeSet != null || addSetEnabled
                val addPending = activeSet == null && !addSetEnabled
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .then(
                            if (activeLogged || addPending || activeSet == null) Modifier
                            else Modifier.espressoButtonShadow(accent, buttonShape)
                        )
                        .clip(buttonShape)
                        .background(
                            when {
                                activeLogged -> EspressoPalette.panel2
                                activeSet == null -> EspressoPalette.panel2
                                else -> accent
                            }
                        )
                        .then(
                            if (activeLogged) Modifier.border(1.5.dp, palette.complete, buttonShape) else Modifier
                        )
                        .semantics {
                            contentDescription = when {
                                addPending -> "Adding set"
                                activeSet == null -> "Start set $nextWorkingOrdinal"
                                activeLogged -> "${if (activeSet.isWarmup) "Warm-up" else "Set $activeOrdinal"} logged. Tap to mark unlogged. Values remain editable."
                                else -> if (activeSet.isWarmup) "Log warm-up" else "Log set $activeOrdinal"
                            }
                        }
                        .clickable(enabled = primaryEnabled, role = Role.Button) {
                            // A value still moving must not be logged or advanced; the tap is dropped
                            // rather than the button greyed, so the card does not flicker mid-fling.
                            if (numberScrubActive) return@clickable
                            // Logging a set is the one moment on the card that deserves a real thump.
                            if (activeSet != null) heavyHaptic() else lightHaptic()
                            activeSet?.let { onToggleLogged(it) } ?: onAddSet()
                        },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                addPending -> "Adding…"
                                activeSet == null -> "Start set $nextWorkingOrdinal"
                                activeLogged -> if (activeSet.isWarmup) "Warm-up logged" else "Set $activeOrdinal logged"
                                else -> if (activeSet.isWarmup) "Log warm-up" else "Log set $activeOrdinal"
                            },
                            fontFamily = LogDisplayFontFamily,
                            fontSize = 16.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                activeLogged -> palette.complete
                                activeSet == null -> EspressoPalette.dim
                                else -> accentInk
                            },
                        )
                        if (activeSet != null) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = buildString {
                                    append(rotationLoadLabel(storedWeight, isBw))
                                    append(" × ")
                                    append(reps ?: 0)
                                },
                                fontFamily = LogUiFontFamily,
                                fontSize = 11.5.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = (if (activeLogged) palette.complete else accentInk).copy(alpha = 0.75f),
                            )
                        }
                    }
                }

                // WARM / REC pill (112dp x 56dp)
                val pillShape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier
                        .width(112.dp)
                        .height(56.dp)
                        .clip(pillShape)
                        .background(EspressoPalette.panel2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // WARM cell
                    val isWarmupActive = activeSet?.isWarmup == true
                    val warmColor = when {
                        activeSet == null -> palette.inkSubtle
                        isWarmupActive -> palette.repStrong
                        else -> palette.inkMuted
                    }
                    val warmBg = if (isWarmupActive) palette.repStrong.copy(alpha = 0.10f) else Color.Transparent

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(warmBg)
                            .semantics {
                                role = Role.Button
                                contentDescription = if (isWarmupActive) "Warm-up. Mark as working set" else "Mark as warm-up"
                                stateDescription = if (isWarmupActive) "Warm-up" else "Working set"
                            }
                            .clickable(enabled = activeSet != null, role = Role.Button) {
                                activeSet?.let { onToggleWarmup(it.id) }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Same 16dp glyph box as the REC sparkle so the two labels sit level.
                        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "W",
                                fontFamily = LogDisplayFontFamily,
                                fontSize = 15.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeSet == null) EspressoPalette.dim else if (isWarmupActive) palette.repStrong else EspressoPalette.ink,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "WARM",
                            fontFamily = LogUiFontFamily,
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.6.sp,
                            color = warmColor,
                        )
                    }

                    // 1dp vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(EspressoPalette.hair),
                    )

                    // REC cell
                    val isWorking = recommendationState == RecommendationState.Working
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Button
                                contentDescription = if (isWorking) "Thinking…" else "Recommend sets"
                            }
                            .clickable(enabled = !isWorking, role = Role.Button, onClick = onRecommend),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = accentSoft,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isWorking) "…" else "SUGGEST",
                            fontFamily = LogUiFontFamily,
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.6.sp,
                            color = accentSoft,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedWorkoutMasthead(
    workoutNumber: Int,
    splitLabel: String,
    date: LocalDate,
    receipt: RotationReceipt,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
) {
    // Espresso: the head of the earned poster, on a block that fades from a tint of the day's colour
    // into the panel. The workout number prints once, with the split under it and the day's tally at
    // its right; a head too narrow for both side by side (the wide layout's column) stacks the tally.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(EspressoPalette.chest.copy(alpha = 0.22f), Color.Transparent)))
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
    ) {
        if (maxWidth >= SUMMARY_HEAD_SIDE_BY_SIDE) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                CompletedWorkoutIdentity(workoutNumber, splitLabel, date, palette, Modifier.weight(1f))
                CompletedWorkoutTally(receipt = receipt, modifier = Modifier.width(SUMMARY_TALLY_WIDTH))
            }
        } else {
            Column {
                CompletedWorkoutIdentity(workoutNumber, splitLabel, date, palette)
                CompletedWorkoutTally(receipt = receipt, modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
            }
        }
    }
}

/** The tally column's width, and the narrowest head that still fits the identity beside it. */
private val SUMMARY_TALLY_WIDTH = 152.dp
private val SUMMARY_HEAD_SIDE_BY_SIDE = 280.dp

@Composable
private fun CompletedWorkoutIdentity(
    workoutNumber: Int,
    splitLabel: String,
    date: LocalDate,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
) {
    val number = rotationWorkoutNumberLabel(workoutNumber)
    // A day with no split prints no title: "Workout" under the WORKOUT kicker would say it twice.
    val title = splitLabel.trim()
    val numberSize = if (number.length >= 4) 40.sp else 48.sp
    Column(modifier = modifier) {
        // The Log masthead above already shows the date, so this head prints none of its own.
        // The summary only exists once every set is checked, so the finish needs no pill; a
        // screen reader still hears the date and the finish on the workout kicker.
        EspressoEyebrow(
            "Workout",
            modifier = Modifier.semantics { contentDescription = "Workout $number, ${rotationSummaryDateLabel(date)} · COMPLETE" },
        )
        Text(
            text = number,
            fontFamily = LogDisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = numberSize,
            lineHeight = numberSize,
            letterSpacing = (-2).sp,
            color = EspressoPalette.chest,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .padding(top = 8.dp)
                // Digits stand at cap height: the box is that tall and ends on their baseline, so
                // the font's ascent and descent leave no gap around the number.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val height = (numberSize.toPx() * 0.68f).roundToInt()
                    layout(placeable.width, height) { placeable.place(0, height - placeable[FirstBaseline]) }
                }
                .clearAndSetSemantics { },
        )
        if (title.isNotEmpty()) {
            Text(
                text = title,
                fontFamily = LogDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.5).sp,
                color = palette.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun CompletedWorkoutExerciseRow(
    exercise: ExerciseLog,
    displayPosition: Int,
    visibleSets: List<LoggedSet>,
    palette: LogGlassPalette,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Float = 0f,
    hideText: Boolean = false,
    onPlaced: (SummaryRowPieces) -> Unit = {},
    rowIndex: Int = displayPosition - 1,
    rowCount: Int = displayPosition,
    ticketsMs: () -> Float = { TICKETS_AT_REST_MS },
    onAddSet: (() -> Unit)? = null,
) {
    val accent = rotationRegionColor(exercise.muscleGroups)
    val placed = remember { arrayOfNulls<Rect>(3) }
    val report = { slot: Int, rect: Rect ->
        placed[slot] = rect
        val (row, number, name) = placed
        if (row != null && number != null && name != null) onPlaced(SummaryRowPieces(row, number, name, displayPosition))
    }
    val comparison = rotationSummaryComparisonLabel(exercise.copy(sets = visibleSets))
    val tone = when {
        comparison.startsWith("+") -> EspressoTone.Up
        comparison.startsWith("-") -> EspressoTone.Down
        else -> EspressoTone.Flat
    }
    val chipText = if (tone == EspressoTone.Flat) {
        comparison.lowercase().replaceFirstChar { it.uppercase() }
    } else {
        comparison.lowercase()
    }
    val tickets = remember(visibleSets, exercise.ghostSets) { rotationSetTickets(visibleSets, exercise.ghostSets) }
    val rowDescription = buildString {
        append(exercise.exerciseName)
        append(", ")
        append(tickets.joinToString(", ", transform = ::rotationTicketDescription))
        append(", ")
        append(comparison.lowercase())
        append(". Open completed exercise")
    }

    val textAlpha = if (hideText) 0f else 1f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { report(0, it.boundsInRoot()) }
            // Pressed: the lift's colour fills the row from its number outward.
            .drawBehind {
                if (tint > 0f) {
                    val inset = 20.dp.toPx()
                    drawRect(
                        accent.copy(alpha = 0.16f),
                        topLeft = Offset(-inset, 0f),
                        size = Size((size.width + inset * 2) * tint, size.height),
                    )
                }
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics {
                            role = Role.Button
                            contentDescription = rowDescription
                        }
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        // The number leads the name's line; the tickets take the full width underneath.
        Row(verticalAlignment = Alignment.Top) {
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = 24.dp)) {
                Text(
                    text = displayPosition.toString().padStart(2, '0'),
                    fontFamily = LogDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = accent,
                    modifier = Modifier
                        .width(24.dp)
                        .graphicsLayer { alpha = textAlpha }
                        .onGloballyPositioned { report(1, it.boundsInRoot()) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).heightIn(min = 24.dp)) {
                // The name and, right after it, the way to add a set to it.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.exerciseName,
                        fontFamily = LogDisplayFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        color = palette.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .graphicsLayer { alpha = textAlpha }
                            .onGloballyPositioned { report(2, it.boundsInRoot()) },
                    )
                    if (onAddSet != null) {
                        SummaryAddSetButton(
                            lift = accent,
                            liftName = exercise.exerciseName,
                            alpha = textAlpha,
                            onClick = onAddSet,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                if (summaryChipVisible(comparison)) EspressoDeltaChip(text = chipText, tone = tone)
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = EspressoPalette.faint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        CompletedWorkoutTickets(
            tickets = tickets,
            lift = accent,
            row = rowIndex,
            rows = rowCount,
            ms = ticketsMs,
        )
    }
}

/**
 * A small plus beside a finished lift's name: opens the lift on a new set. A soft disc of the lift's
 * colour inside a touch box no taller than the name's line, so the summary does not grow.
 */
@Composable
private fun SummaryAddSetButton(lift: Color, liftName: String, alpha: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 34.dp, height = 24.dp)
            .graphicsLayer { this.alpha = alpha }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Add a set to $liftName" },
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val squeeze = if (pressed) 0.88f else 1f
            val radius = size.minDimension / 2f * squeeze
            drawCircle(lift.copy(alpha = if (pressed) 0.30f else 0.18f), radius)
            val arm = 4.5.dp.toPx() * squeeze
            val stroke = 1.8.dp.toPx()
            drawLine(lift, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(lift, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun CompletedWorkoutLedger(
    exercises: List<ExerciseLog>,
    pendingDeletedSetIds: Set<Long>,
    palette: LogGlassPalette,
    onOpenExercise: ((ExerciseLog) -> Unit)?,
    modifier: Modifier = Modifier,
    flightCardKey: String? = null,
    flightTint: Float = 0f,
    flightTextHidden: Boolean = false,
    onRowPlaced: (String, SummaryRowPieces) -> Unit = { _, _ -> },
    ticketsMs: () -> Float = { TICKETS_AT_REST_MS },
    onAddSet: ((ExerciseLog) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            exercises.forEachIndexed { index, exercise ->
                // The summary is what was logged: a skipped warm-up never prints as a ticket.
                val visibleSets = exercise.sets.filter { it.isCompleted && it.id !in pendingDeletedSetIds }
                val key = rotationCardKey(exercise)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(EspressoPalette.hair),
                )
                CompletedWorkoutExerciseRow(
                    exercise = exercise,
                    displayPosition = index + 1,
                    visibleSets = visibleSets,
                    palette = palette,
                    onClick = onOpenExercise?.let { open -> { open(exercise) } },
                    tint = if (key == flightCardKey) flightTint else 0f,
                    hideText = flightTextHidden && key == flightCardKey,
                    onPlaced = { pieces -> onRowPlaced(key, pieces) },
                    rowIndex = index,
                    rowCount = exercises.size,
                    ticketsMs = ticketsMs,
                    onAddSet = onAddSet?.let { add -> { add(exercise) } },
                )
            }
            // The tally lives in the head now; the card ends at its last lift. The latch's top half
            // sits over the card's last 38dp, so the last row's tickets keep clear of it.
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** The earned poster shown after the whole day is checked off. */
@Composable
private fun CompletedWorkoutSummary(
    exercises: List<ExerciseLog>,
    splitLabel: String,
    workoutNumber: Int,
    date: LocalDate,
    pendingDeletedSetIds: Set<Long>,
    palette: LogGlassPalette,
    onOpenExercise: (ExerciseLog) -> Unit,
    onReopenWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    latchMs: Float = 0f,
    onLatchPressedChange: (Boolean) -> Unit = {},
    /** False for the copy drawn while the summary leaves: it shows, it does not respond. */
    interactive: Boolean = true,
    flightCardKey: String? = null,
    flightTint: Float = 0f,
    /** Once the travelling copies of the row's number and name are drawn, the row hides its own. */
    flightTextHidden: Boolean = false,
    onRowPlaced: (String, SummaryRowPieces) -> Unit = { _, _ -> },
    /** The set tickets' print clock; copies drawn during the other motions show them at rest. */
    ticketsMs: () -> Float = { TICKETS_AT_REST_MS },
    /** A lift's next ticket: opens it on a new set. Only the live summary offers it. */
    onAddSet: ((ExerciseLog) -> Unit)? = null,
) {
    val summaryExercises = rotationExerciseDisplayOrder(exercises)
    // Built from the rows' own order, so the tally's 01, 02… match the rows' numbers.
    val receipt = remember(summaryExercises, pendingDeletedSetIds) { rotationSummaryReceipt(summaryExercises, pendingDeletedSetIds) }
    val openExercise = if (interactive) onOpenExercise else null
    val addSet = if (interactive) onAddSet else null
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 940.dp),
        ) {
            if (maxWidth >= 720.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(44.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CompletedWorkoutMasthead(
                        workoutNumber = workoutNumber,
                        splitLabel = splitLabel,
                        date = date,
                        receipt = receipt,
                        palette = palette,
                        modifier = Modifier.width(310.dp),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        CompletedWorkoutLedger(
                            exercises = summaryExercises,
                            pendingDeletedSetIds = pendingDeletedSetIds,
                            palette = palette,
                            onOpenExercise = openExercise,
                            flightCardKey = flightCardKey,
                            flightTint = flightTint,
                            flightTextHidden = flightTextHidden,
                            onRowPlaced = onRowPlaced,
                            ticketsMs = ticketsMs,
                            onAddSet = addSet,
                        )
                        ReopenLatch(
                            ms = latchMs,
                            interactive = interactive,
                            onPressedChange = onLatchPressedChange,
                            onClick = onReopenWorkout,
                        )
                    }
                }
            } else {
                // The latch sits on the card's bottom edge, half in and half out, so the card is
                // clipped on its own and the latch hangs over its edge.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = REOPEN_LATCH_BELOW)
                            .clip(RoundedCornerShape(24.dp))
                            .background(EspressoPalette.panel),
                    ) {
                        CompletedWorkoutMasthead(
                            workoutNumber = workoutNumber,
                            splitLabel = splitLabel,
                            date = date,
                            receipt = receipt,
                            palette = palette,
                        )
                        CompletedWorkoutLedger(
                            exercises = summaryExercises,
                            pendingDeletedSetIds = pendingDeletedSetIds,
                            palette = palette,
                            onOpenExercise = openExercise,
                            flightCardKey = flightCardKey,
                            flightTint = flightTint,
                            flightTextHidden = flightTextHidden,
                            onRowPlaced = onRowPlaced,
                            ticketsMs = ticketsMs,
                            onAddSet = addSet,
                        )
                    }
                    ReopenLatch(
                        ms = latchMs,
                        interactive = interactive,
                        onPressedChange = onLatchPressedChange,
                        onClick = onReopenWorkout,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/**
 * The day, as a deck. Only exercises still in the rotation get a page; parking one drops it out
 * and the pager closes the gap.
 */
@Composable
internal fun RotationExercisePager(
    exercises: List<ExerciseLog>,
    selectedSetByCard: MutableMap<String, RotationSetSelection>,
    splitLabel: String,
    showCompletedSummary: Boolean,
    completedWorkoutNumber: Int,
    /** Every lift on the day is finished, whether or not a lift opened from the summary is showing. */
    dayFinished: Boolean = showCompletedSummary,
    summaryDate: LocalDate,
    totalVolumeLbs: Int,
    reviewingCompletedCardId: String?,
    palette: LogGlassPalette,
    pendingDeletedSetIds: Set<Long>,
    onWeightStep: (Long, Int) -> Unit,
    onRepsStep: (Long, Int) -> Unit,
    onCompleteSet: (Long, () -> Unit) -> Unit,
    onToggleWarmup: (Long) -> Unit = {},
    onAddSet: (ExerciseLog, LoggedSet?) -> Unit,
    onRemoveSet: (LoggedSet) -> Unit,
    onRemoveExercise: (ExerciseLog) -> Unit,
    onSwapExercise: ((ExerciseLog) -> Unit)? = null,
    swapUndoExerciseId: Long? = null,
    onUndoSwap: () -> Unit = {},
    onWeightInput: (Long) -> Unit,
    onRepsInput: (Long) -> Unit,
    onRecommend: (Long) -> Unit,
    onMuscleGroupChange: (ExerciseLog, String) -> Unit,
    onPark: (ExerciseLog) -> Unit,
    onUnpark: (ExerciseLog) -> Unit,
    onOpenCompletedExercise: (ExerciseLog) -> Unit,
    onExitCompletedReview: () -> Unit,
    onOpenExerciseProgress: (Long, Int) -> Unit = { _, _ -> },
    onTaken: (ExerciseLog) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenPlates: (Float) -> Unit = {},
    teachScrub: Boolean = false,
    onScrubTaught: () -> Unit = {},
    // An Undo can restore a card that a removal just took out of the rotation. The pager tracks
    // *which card is on screen* by key, not by raw page index, so the card that shifted into the
    // removed one's slot stays on screen even once the restored card is re-inserted ahead of it —
    // Undo would otherwise leave you looking at a different lift than the one you asked back for.
    // `focusRequest` is a counter that only ever goes up: the scroll fires once per increment,
    // whatever `focusExerciseId` was at the time, with nothing here to reset afterward.
    focusExerciseId: Long? = null,
    focusSetSyncId: String? = null,
    focusRequest: Int = 0,
) {
    val viewportHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compactTrend = viewportHeight < 740.dp
    var expandedTrend by rememberSaveable(summaryDate) { mutableStateOf(false) }
    val showFullTrend = !compactTrend || expandedTrend
    var reviewingWholeWorkout by rememberSaveable(summaryDate.toString()) { mutableStateOf(false) }
    val cardsToFinish = rotationCardsToFinish(exercises, showCompletedSummary, reviewingWholeWorkout, pendingDeletedSetIds)
    LaunchedEffect(summaryDate, cardsToFinish) {
        cardsToFinish.forEach(onPark)
    }

    // Leaving the summary. Every state change happens exactly when it did before; the motion only
    // fills the middle: the summary is drawn over the next screen while it goes.
    val motionScope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val latchHaptic = rememberLightHaptic()
    val latchClock = remember(summaryDate) { Animatable(0f) }
    var lidLeaving by remember(summaryDate) { mutableStateOf(false) }
    var lidOrigin by remember(summaryDate) { mutableStateOf(Offset.Zero) }
    var pagerOrigin by remember { mutableStateOf(Offset.Zero) }
    val rowPieces = remember(summaryDate) { mutableStateMapOf<String, SummaryRowPieces>() }
    val cardHeads = remember(summaryDate) { mutableStateMapOf<String, ExerciseCardPlacement>() }
    var flight by remember(summaryDate) { mutableStateOf<RowFlight?>(null) }

    // The set tickets print each time a day's summary comes into view: when the day is finished, and
    // again after leaving the day or the Log tab and coming back. Coming back from a lift opened on
    // the summary keeps them at rest, since that row flies home into its place.
    val summaryShowing = showCompletedSummary && !reviewingWholeWorkout
    // A haptic answers a finger. The tickets tick only once this day's cards or summary have been
    // touched, which is how a day gets finished here (its last set logged, or Done after Reopen); a
    // day that opens already finished, at boot or from the arrows or the calendar, prints silently.
    var dayTouched by remember(summaryDate) { mutableStateOf(false) }
    val watchDayTouch = Modifier.pointerInput(summaryDate) {
        // Watches only: the first press on this day is noted and never consumed, so every gesture
        // under it behaves exactly as before.
        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial) }
        dayTouched = true
    }
    // Scoped to this visit: another day, or another tab and back, starts it over.
    var ticketsPrinted by remember(summaryDate) { mutableStateOf(false) }
    val ticketClock = remember(summaryDate) {
        Animatable(if (reducedMotion) TICKETS_AT_REST_MS else 0f)
    }
    LaunchedEffect(summaryDate, summaryShowing) {
        if (!summaryShowing) return@LaunchedEffect
        if (reducedMotion || ticketsPrinted) {
            ticketClock.snapTo(TICKETS_AT_REST_MS)
            return@LaunchedEffect
        }
        ticketsPrinted = true
        val rows = exercises.size
        val end = ticketsEndMs(rows, exercises.maxOfOrNull { it.sets.size } ?: 1)
        try {
            ticketClock.snapTo(0f)
            // One light tick per lift, as last time lands on its first ticket.
            if (dayTouched) launch {
                var elapsed = 0f
                repeat(rows) { row ->
                    val at = ticketDelayMs(row, 0, rows) + TICKET_POP_START_MS
                    delay((at - elapsed).toLong())
                    elapsed = at
                    latchHaptic()
                }
            }
            ticketClock.animateTo(end, tween(end.toInt(), easing = LinearEasing))
        } finally {
            withContext(NonCancellable) { ticketClock.snapTo(TICKETS_AT_REST_MS) }
        }
    }

    // The lid's shaders, compiled behind the settled summary before the latch is ever pressed:
    // the first swing used to stall on them (see ShaderWarmup).
    var lidWarming by remember { mutableStateOf(false) }
    LaunchedEffect(summaryShowing) {
        if (!summaryShowing || reducedMotion || ShaderWarmup.isDone(LID_WARMUP)) return@LaunchedEffect
        snapshotFlow { ticketClock.value >= TICKETS_AT_REST_MS }.first { it }
        ShaderWarmup.run(LID_WARMUP) { lidWarming = it }
    }

    // Reopen settles the deck in through a layer, and content drawn into a layer for the first
    // time compiles its own programs. While the deck is on screen for a workout, it passes through
    // an identical layer once, in a still moment, so the first Reopen finds them compiled.
    var deckWarming by remember { mutableStateOf(false) }
    LaunchedEffect(summaryShowing) {
        if (summaryShowing || reducedMotion || ShaderWarmup.isDone(DECK_WARMUP)) return@LaunchedEffect
        delay(DECK_WARMUP_DELAY_MS)
        ShaderWarmup.run(DECK_WARMUP) { deckWarming = it }
    }

    fun pressLatch(down: Boolean) {
        if (reducedMotion) return
        motionScope.launch {
            if (down) {
                latchHaptic()
                latchClock.animateTo(REOPEN_PRESS_MS, tween(REOPEN_PRESS_MS.toInt(), easing = LinearEasing))
            } else if (!lidLeaving) {
                latchClock.animateTo(0f, tween(120))
            }
        }
    }
    fun reopenWorkout() {
        // Finishing a reopened day prints its tickets again, against whatever changed.
        ticketsPrinted = false
        if (reducedMotion) {
            reviewingWholeWorkout = true
            return
        }
        motionScope.launch { ticketClock.snapTo(0f) }
        latchHaptic()
        lidOrigin = pagerOrigin
        lidLeaving = true
        reviewingWholeWorkout = true
        motionScope.launch {
            if (latchClock.value < REOPEN_PRESS_MS) latchClock.snapTo(REOPEN_PRESS_MS)
            latchClock.animateTo(
                REOPEN_END_MS,
                tween((REOPEN_END_MS - latchClock.value).toInt(), easing = LinearEasing),
            )
            lidLeaving = false
            latchClock.snapTo(0f)
        }
    }
    // A lift's next ticket on the summary: the card it opens adds a set and lands on it.
    var pendingSummaryAdd by remember(summaryDate) { mutableStateOf<String?>(null) }
    fun openCompletedExercise(exercise: ExerciseLog) {
        val key = rotationCardKey(exercise)
        if (reducedMotion || flight != null || rowPieces[key] == null) {
            onOpenCompletedExercise(exercise)
            return
        }
        val next = RowFlight(key, exercise, opening = true, summaryOrigin = pagerOrigin)
        flight = next
        onOpenCompletedExercise(exercise)
        motionScope.launch {
            next.clock.animateTo(ROW_FLIGHT_OPEN_MS, tween(ROW_FLIGHT_OPEN_MS.toInt(), easing = LinearEasing))
            if (flight === next) flight = null
        }
    }
    fun addSetFromSummary(exercise: ExerciseLog) {
        pendingSummaryAdd = rotationCardKey(exercise)
        openCompletedExercise(exercise)
    }
    fun closeCompletedExercise(exercise: ExerciseLog) {
        val key = rotationCardKey(exercise)
        if (reducedMotion || flight != null || cardHeads[key] == null) {
            onExitCompletedReview()
            return
        }
        val next = RowFlight(key, exercise, opening = false, summaryOrigin = pagerOrigin)
        flight = next
        motionScope.launch {
            next.clock.animateTo(ROW_FLIGHT_CLOSE_MS, tween(ROW_FLIGHT_CLOSE_MS.toInt(), easing = LinearEasing))
            onExitCompletedReview()
            if (flight === next) flight = null
        }
    }

    // A lift opened from the summary that left the day unfinished (its + ticket added a set) goes
    // home on its own once the day is finished again, its new set logged or removed: the same moment
    // the deck would have shown the summary. Opening a finished lift just to look or edit leaves the
    // day finished, so it stays until Done.
    var reviewLeftDayOpen by remember(summaryDate, reviewingCompletedCardId) { mutableStateOf(false) }
    val latestReviewExercises = rememberUpdatedState(exercises)
    LaunchedEffect(reviewingCompletedCardId, dayFinished) {
        val cardId = reviewingCompletedCardId ?: return@LaunchedEffect
        if (!dayFinished) {
            reviewLeftDayOpen = true
            return@LaunchedEffect
        }
        // Done is already flying this row home.
        if (!reviewLeftDayOpen || flight != null) return@LaunchedEffect
        reviewLeftDayOpen = false
        latestReviewExercises.value.firstOrNull { it.cardId == cardId }?.let(::closeCompletedExercise)
    }

    if (showCompletedSummary && !reviewingWholeWorkout) {
        Box(modifier = modifier.fillMaxWidth().onGloballyPositioned { pagerOrigin = it.positionInRoot() }.then(watchDayTouch)) {
            CompletedWorkoutSummary(
                exercises = exercises,
                splitLabel = splitLabel,
                workoutNumber = completedWorkoutNumber,
                date = summaryDate,
                pendingDeletedSetIds = pendingDeletedSetIds,
                palette = palette,
                onOpenExercise = ::openCompletedExercise,
                onAddSet = ::addSetFromSummary,
                onReopenWorkout = ::reopenWorkout,
                latchMs = latchClock.value,
                onLatchPressedChange = ::pressLatch,
                onRowPlaced = { key, pieces -> rowPieces[key] = pieces },
                ticketsMs = { ticketClock.value },
            )
        }
        return
    }

    val inRotation = remember(exercises, reviewingCompletedCardId, reviewingWholeWorkout) {
        rotationExercisesInDeck(
            exercises = exercises,
            reviewingCompletedCardId = reviewingCompletedCardId,
            reviewingWholeWorkout = reviewingWholeWorkout,
        )
    }
    val outOfRotation = remember(exercises, reviewingWholeWorkout) {
        if (reviewingWholeWorkout) {
            emptyList()
        } else {
            rotationExerciseDisplayOrder(
                exercises.filter {
                    it.displayMode == com.ayman.ecolift.data.WorkoutCardDisplayMode.COMPACT
                },
            )
        }
    }
    val visualPositions = remember(exercises) { rotationExerciseVisualPositions(exercises) }

    // Key the saved pager state to the day. A date-reset effect would also run when the
    // clipboard pushes this lazy item offscreen, cancelling a pending search focus on return.
    val pagerState = key(summaryDate) {
        rememberPagerState(pageCount = { maxOf(inRotation.size, 1) })
    }

    // Added from the summary's next ticket: the same add as the card's own +, a copy of the last set,
    // made as the card opens so it lands already on the new set.
    LaunchedEffect(pendingSummaryAdd) {
        val key = pendingSummaryAdd ?: return@LaunchedEffect
        pendingSummaryAdd = null
        val exercise = exercises.firstOrNull { rotationCardKey(it) == key } ?: return@LaunchedEffect
        val visibleSets = exercise.sets
            .filterNot { it.id in pendingDeletedSetIds }
            .mapIndexed { position, set ->
                if (set.setNumber == position + 1) set else set.copy(setNumber = position + 1)
            }
        selectedSetByCard[key] = nextRotationSetSelection(
            visibleSets = visibleSets,
            existingSets = exercise.sets,
        )
        onAddSet(exercise, rotationAddSourceSet(visibleSets = visibleSets, selection = null))
    }
    // Two fields can coast at once; one landing must not unlock the other field's set.
    val activeNumberFields = remember(summaryDate) { mutableStateMapOf<String, Boolean>() }
    val isNumberScrubActive = activeNumberFields.isNotEmpty()
    val completionSelection = remember(summaryDate, selectedSetByCard) { RotationCompletionSelection(selectedSetByCard) }
    val latestSelectionDate = rememberUpdatedState(summaryDate)
    val latestSelections = rememberUpdatedState(selectedSetByCard)
    val latestSelectionExercises = rememberUpdatedState(exercises)
    val latestSelectionDeletedIds = rememberUpdatedState(pendingDeletedSetIds)

    // Reads through `rememberUpdatedState` rather than the raw `focusExerciseId`/`inRotation`
    // parameters inside this effect's body — see the note by `latestDayUndo` in LogScreenV2.kt for
    // why capturing an unstable parameter (`inRotation` is a `List`) directly inside a
    // `LaunchedEffect` body reliably corrupted this wasmJs target elsewhere in the same
    // recomposition. Keyed on `focusRequest`, not `focusExerciseId`: nothing here needs to write
    // back to whatever it's keyed on to mark the request handled.
    val latestFocusExerciseId = rememberUpdatedState(focusExerciseId)
    val latestFocusSetSyncId = rememberUpdatedState(focusSetSyncId)
    val latestInRotationForFocus = rememberUpdatedState(inRotation)
    var handledFocusRequest by rememberSaveable(summaryDate) { mutableStateOf(0) }
    val focusSetReady = inRotation.any { ex ->
        ex.exerciseId == focusExerciseId && ex.sets.any { it.syncId == focusSetSyncId }
    }
    LaunchedEffect(focusRequest, inRotation.map { it.exerciseId }, focusSetReady) {
        if (focusRequest == 0 || handledFocusRequest == focusRequest) return@LaunchedEffect
        val targetId = latestFocusExerciseId.value ?: return@LaunchedEffect
        val index = latestInRotationForFocus.value.indexOfFirst { it.exerciseId == targetId }
        if (index >= 0) {
            completionSelection.selectionChanged()
            val exercise = latestInRotationForFocus.value[index]
            val syncId = latestFocusSetSyncId.value
            if (syncId != null) {
                val set = exercise.sets.firstOrNull { it.syncId == syncId } ?: return@LaunchedEffect
                selectedSetByCard[rotationCardKey(exercise)] = rotationSelectionOf(set)
            }
            pagerState.animateScrollToPage(index)
            handledFocusRequest = focusRequest
        }
    }

    // Selection is keyed by stable exercise identity and logical set number. Both card IDs and set
    // IDs may be replaced while optimistic work is synchronized.
    LaunchedEffect(exercises, pendingDeletedSetIds) {
        val activeCardKeys = exercises.mapTo(mutableSetOf(), ::rotationCardKey)
        selectedSetByCard.keys.toList()
            .filterNot(activeCardKeys::contains)
            .forEach(selectedSetByCard::remove)

        selectedSetByCard.toMap().forEach { (cardKey, selection) ->
            val exercise = exercises.firstOrNull { rotationCardKey(it) == cardKey }
                ?: return@forEach
            val visibleSets = exercise.sets
                .filterNot { it.id in pendingDeletedSetIds }
                .mapIndexed { position, set ->
                    if (set.setNumber == position + 1) set else set.copy(setNumber = position + 1)
                }
            val exact = resolvedRotationSet(visibleSets, selection)
            if (
                exact != null &&
                (!selection.matches(exact) || exact.setNumber != selection.setNumber)
            ) {
                selectedSetByCard[cardKey] = rotationSelectionOf(exact)
            }
        }
    }
    val lidPose = if (lidLeaving) reopenLidPose(latchClock.value) else null
    val activeFlight = flight
    val flightProgress = activeFlight?.let { rowFlightProgress(it.clock.value, it.opening) }
    val flightRow = activeFlight?.let { rowPieces[it.cardKey] }
    val flightCard = activeFlight?.let { cardHeads[it.cardKey] }
    val flightFrame = if (activeFlight != null && flightProgress != null && flightRow != null && flightCard != null) {
        with(LocalDensity.current) {
            rowFlightFrame(
                p = flightProgress.first,
                tint = flightProgress.second,
                row = flightRow,
                card = flightCard,
                accent = rotationRegionColor(activeFlight.exercise.muscleGroups),
                rowInsetPx = 20.dp.toPx(),
                radiusPx = 24.dp.toPx(),
            )
        }
    } else {
        null
    }
    Box(modifier = modifier.fillMaxWidth().onGloballyPositioned { pagerOrigin = it.positionInRoot() }.then(watchDayTouch)) {
    if (activeFlight != null && flightProgress != null) {
        // The summary fading back as the row leaves it (or returning under the row coming home).
        val p = flightProgress.first
        CompletedWorkoutSummary(
            exercises = exercises,
            splitLabel = splitLabel,
            workoutNumber = completedWorkoutNumber,
            date = summaryDate,
            pendingDeletedSetIds = pendingDeletedSetIds,
            palette = palette,
            onOpenExercise = {},
            onReopenWorkout = {},
            interactive = false,
            flightCardKey = activeFlight.cardKey,
            flightTint = flightProgress.second,
            flightTextHidden = flightFrame != null,
            onRowPlaced = { key, pieces -> rowPieces[key] = pieces },
            modifier = Modifier
                .summaryGhost(activeFlight.summaryOrigin - pagerOrigin, z = -1f)
                .graphicsLayer {
                    alpha = 1f - (p * 2.2f).coerceIn(0f, 1f)
                    scaleX = 1f - 0.035f * p
                    scaleY = scaleX
                },
        )
    }
    if (lidWarming) {
        // A copy in the lid's pose at 2% alpha: every program the swing needs, nothing to see.
        CompletedWorkoutSummary(
            exercises = exercises,
            splitLabel = splitLabel,
            workoutNumber = completedWorkoutNumber,
            date = summaryDate,
            pendingDeletedSetIds = pendingDeletedSetIds,
            palette = palette,
            onOpenExercise = {},
            onReopenWorkout = {},
            latchMs = LID_WARMUP_LATCH_MS,
            interactive = false,
            modifier = Modifier
                .clearAndSetSemantics { }
                .summaryGhost(Offset.Zero, z = -2f)
                .reopenLid(LID_WARMUP_POSE, hingeFromTop = -pagerOrigin.y),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                compositingStrategy = if (deckWarming) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .reopenDeckSettle(lidPose)
            .rowFlightDeck(flightFrame, pagerOrigin, hidden = activeFlight != null && flightFrame == null),
    ) {
        // Anything you parked. The chip says exactly how much work is still open, so a partially
        // logged exercise cannot masquerade as finished just because one of its sets was checked.
        // Tap to put it back in the deck with its sets intact.
        if (reviewingCompletedCardId == null && outOfRotation.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
            ) {
                Text(
                    text = "OUT ${outOfRotation.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.5.sp,
                    letterSpacing = 2.sp,
                    color = palette.inkSubtle.copy(alpha = 0.75f),
                )
                outOfRotation.forEach { ex ->
                    val visibleSets = ex.sets.filterNot { it.id in pendingDeletedSetIds }
                    val progress = rotationExerciseProgress(visibleSets)
                    val tone = if (progress.isComplete) palette.complete else palette.load
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(EspressoPalette.panel2)
                            .clickable { onUnpark(ex) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(tone),
                        )
                        Text(
                            text = rotationExercisePositionLabel(
                                visualPositions[rotationCardKey(ex)] ?: 1,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = tone,
                            maxLines = 1,
                        )
                        Text(
                            text = ex.exerciseName,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.inkMuted,
                            maxLines = 1,
                        )
                        Text(
                            text = progress.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            letterSpacing = 1.2.sp,
                            color = tone,
                        )
                    }
                }
            }
        }
        // An empty day has no deck at all; its starters sit right under the clipboard.
        if (inRotation.isEmpty() && outOfRotation.isEmpty()) return@Column
        if (inRotation.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            ) {
                // The OUT chips above already show what to tap, so the empty deck only needs its name.
                if (outOfRotation.isNotEmpty()) EspressoEyebrow("Rotation empty")
            }
            return@Column
        }
        // One tick per exercise still in play, so the deck has a position.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            inRotation.forEachIndexed { index, ex ->
                val complete = rotationExerciseProgress(ex.sets.filterNot { it.id in pendingDeletedSetIds }).isComplete
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                index == pagerState.currentPage -> rotationRegionColor(ex.muscleGroups)
                                complete -> palette.complete
                                else -> EspressoPalette.hair2
                            }
                        ),
                )
            }
        }
        // Every card in the deck gets the same height so paging never jumps: the base card, the
        // last-time tab row if any card has one, and a second name line if any name in the deck wraps.
        val nameStyle = rotationExerciseNameStyle()
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val nameWidthPx = with(density) { (maxWidth - ROTATION_NAME_INSET_DP.dp).roundToPx() }
            val nameExtraDp = remember(inRotation, nameStyle, nameWidthPx) {
                rotationNameExtraHeightDp(
                    inRotation.maxOfOrNull { ex ->
                        textMeasurer.measure(
                            text = ex.exerciseName,
                            style = nameStyle,
                            maxLines = ROTATION_NAME_MAX_LINES,
                            constraints = Constraints(maxWidth = nameWidthPx.coerceAtLeast(1)),
                        ).lineCount
                    } ?: 1,
                )
            }
            // A swap may introduce a longer name or the first historical session. Reserve
            // those slots up front so the set strip and action row never jump or get squeezed.
            val ghostExtraDp = if (onSwapExercise != null || inRotation.any { it.ghostSets.isNotEmpty() }) ROTATION_CARD_LAST_TIME_DP else 0
            HorizontalPager(
            state = pagerState,
            // `inRotation.getOrNull` rather than a plain `[page]` index: a removal followed by its
            // Undo can shrink `inRotation` and then grow it back within consecutive frames (a
            // set's staged removal hides its exercise's card immediately; Undo un-hides it), and
            // the pager's own prefetch/measure pass can ask for a page one past the end of
            // whichever size it saw mid-transition. That produced an uncaught
            // `IndexOutOfBoundsException` from this exact `[page]` access — reported once via
            // `console.error` rather than as a page crash, but it stopped this pager (and the rest
            // of the Log screen) from painting another frame. A key that can't resolve a page is
            // treated as gone rather than crashing.
            key = { page -> inRotation.getOrNull(page)?.let(::rotationCardKey) ?: "pager-page-$page" },
            userScrollEnabled = !isNumberScrubActive,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(
                (rotationCardBaseHeightDp(showFullTrend) + ghostExtraDp +
                    maxOf(nameExtraDp, rotationNameExtraHeightDp(ROTATION_NAME_MAX_LINES))).dp,
            ),
        ) { page ->
            val exercise = inRotation.getOrNull(page) ?: return@HorizontalPager
            val cardKey = rotationCardKey(exercise)
            val visibleSets = exercise.sets
                .filterNot { it.id in pendingDeletedSetIds }
                .mapIndexed { position, set ->
                    if (set.setNumber == position + 1) set else set.copy(setNumber = position + 1)
                }
            val selectedSet = resolvedRotationSet(
                visibleSets = visibleSets,
                selection = selectedSetByCard[cardKey],
            )
            val selectedId = selectedSet?.id
            val pendingAdd = selectedSetByCard[cardKey]?.let { selection ->
                selection.setId == null && selectedSet == null
            } == true
            LaunchedEffect(cardKey, pendingAdd, selectedSetByCard[cardKey]?.setNumber) {
                if (pendingAdd) {
                    delay(2_000)
                    val selection = selectedSetByCard[cardKey]
                    if (selection?.setId == null) selectedSetByCard.remove(cardKey)
                }
            }
            RotationCard(
                exercise = exercise,
                onTaken = { onTaken(exercise) },
                showFullTrend = showFullTrend,
                onExpandTrend = { expandedTrend = true },
                positionLabel = rotationExercisePositionLabel(
                    visualPositions[cardKey] ?: page + 1,
                ),
                splitLabel = splitLabel,
                onOpenProgress = { onOpenExerciseProgress(exercise.exerciseId, visualPositions[cardKey] ?: page + 1) },
                visibleSets = visibleSets,
                selectedSetId = selectedId,
                palette = palette,
                onWeightStep = { setId, delta ->
                    if (rotationScrubTargetsSelection(visibleSets, selectedSetByCard[cardKey], setId)) {
                        completionSelection.selectionChanged()
                        visibleSets.firstOrNull { it.id == setId }?.let { set ->
                            selectedSetByCard[cardKey] = rotationSelectionOf(set)
                        }
                        onWeightStep(setId, delta)
                    }
                },
                onRepsStep = { setId, delta ->
                    if (rotationScrubTargetsSelection(visibleSets, selectedSetByCard[cardKey], setId)) {
                        completionSelection.selectionChanged()
                        visibleSets.firstOrNull { it.id == setId }?.let { set ->
                            selectedSetByCard[cardKey] = rotationSelectionOf(set)
                        }
                        onRepsStep(setId, delta)
                    }
                },
                onWeightInput = { setId ->
                    visibleSets.firstOrNull { it.id == setId }?.let { set ->
                        completionSelection.selectionChanged()
                        selectedSetByCard[cardKey] = rotationSelectionOf(set)
                    }
                    onWeightInput(setId)
                },
                onRepsInput = { setId ->
                    visibleSets.firstOrNull { it.id == setId }?.let { set ->
                        completionSelection.selectionChanged()
                        selectedSetByCard[cardKey] = rotationSelectionOf(set)
                    }
                    onRepsInput(setId)
                },
                onRecommend = { onRecommend(exercise.exerciseId) },
                onMuscleGroupChange = { group -> onMuscleGroupChange(exercise, group) },
                recommendationState = exercise.recommendationState,
                onSelectSet = { set ->
                    completionSelection.selectionChanged()
                    selectedSetByCard[cardKey] = rotationSelectionOf(set)
                },
                onToggleWarmup = onToggleWarmup,
                onToggleLogged = { set ->
                    val requestedDate = summaryDate
                    val requestedSelections = selectedSetByCard
                    completionSelection.toggle(
                        cardKey = cardKey,
                        toggled = set,
                        currentVisibleSets = {
                            latestSelectionExercises.value.firstOrNull { rotationCardKey(it) == cardKey }?.sets.orEmpty()
                                .filterNot { it.id in latestSelectionDeletedIds.value }
                                .mapIndexed { position, row -> row.copy(setNumber = position + 1) }
                        },
                        isCurrentContext = {
                            latestSelectionDate.value == requestedDate && latestSelections.value === requestedSelections
                        },
                        complete = onCompleteSet,
                    )
                },
                onAddSet = {
                    completionSelection.selectionChanged()
                    val sourceSelection = selectedSetByCard[cardKey]
                        ?: selectedSet?.let(::rotationSelectionOf)
                    val sourceSet = rotationAddSourceSet(
                        visibleSets = visibleSets,
                        selection = sourceSelection,
                    )
                    selectedSetByCard[cardKey] = nextRotationSetSelection(
                        visibleSets = visibleSets,
                        existingSets = exercise.sets,
                    )
                    onAddSet(exercise, sourceSet)
                },
                onRemoveSet = { set ->
                    completionSelection.selectionChanged()
                    val currentSelection = selectedSetByCard[cardKey]
                        ?: selectedSet?.let(::rotationSelectionOf)
                    val nextSelection = rotationSelectionAfterRemoval(
                        visibleSets = visibleSets,
                        selection = currentSelection,
                        removedSetId = set.id,
                    )
                    if (nextSelection == null) {
                        selectedSetByCard.remove(cardKey)
                    } else {
                        selectedSetByCard[cardKey] = nextSelection
                    }
                    onRemoveSet(set)
                },
                onRemoveExercise = { onRemoveExercise(exercise) },
                onSwapExercise = onSwapExercise?.let { swap -> { swap(exercise) } },
                onUndoSwap = if (swapUndoExerciseId == exercise.exerciseId) onUndoSwap else null,
                swapIsToday = summaryDate.toString() == com.ayman.ecolift.data.WorkoutDates.today(),
                onPark = {
                    // While this card is shrinking back into its row it has already been parked.
                    if (flight?.opening != false) {
                        onPark(exercise)
                        when (rotationReviewScope(reviewingCompletedCardId, reviewingWholeWorkout)) {
                            RotationReviewScope.COMPLETED_EXERCISE -> closeCompletedExercise(exercise)
                            RotationReviewScope.WHOLE_WORKOUT -> reviewingWholeWorkout = false
                            RotationReviewScope.NONE -> Unit
                        }
                    }
                },
                onNumberScrubActiveChange = { field, active ->
                    val owner = "$cardKey:$field"
                    if (active) activeNumberFields[owner] = true else activeNumberFields.remove(owner)
                },
                numberScrubActive = isNumberScrubActive,
                addSetEnabled = !pendingAdd,
                onPlaced = { card, head -> cardHeads[cardKey] = ExerciseCardPlacement(card, head) },
                onOpenPlates = onOpenPlates,
                // The first card with sets anyone sees shows, once, that its weight is a wheel.
                peekScrub = teachScrub && page == pagerState.currentPage && selectedSet != null,
                onPeekDone = onScrubTaught,
            )
        }
        }
    }
    if (lidPose != null) {
        // The summary card swinging up off its latch over the reopened deck.
        CompletedWorkoutSummary(
            exercises = exercises,
            splitLabel = splitLabel,
            workoutNumber = completedWorkoutNumber,
            date = summaryDate,
            pendingDeletedSetIds = pendingDeletedSetIds,
            palette = palette,
            onOpenExercise = {},
            onReopenWorkout = {},
            latchMs = latchClock.value,
            interactive = false,
            modifier = Modifier
                .summaryGhost(lidOrigin - pagerOrigin, z = 1f)
                .reopenLid(lidPose, hingeFromTop = -lidOrigin.y),
        )
    }
    if (activeFlight != null && flightFrame != null && flightRow != null && flightCard != null) {
        val position = rotationExercisePositionLabel(visualPositions[activeFlight.cardKey] ?: flightRow.position)
        RowFlightCopies(
            frame = flightFrame,
            row = flightRow,
            card = flightCard,
            origin = pagerOrigin,
            accent = rotationRegionColor(activeFlight.exercise.muscleGroups),
            eyebrow = if (splitLabel.isBlank()) position else "$position · ${splitLabel.uppercase()}",
            name = activeFlight.exercise.exerciseName,
        )
    }
    }
}

private const val LID_WARMUP = "reopen-lid"
private const val DECK_WARMUP = "reopen-deck"

/** The day's cards have arrived and the lifter is looking at them by then. */
private const val DECK_WARMUP_DELAY_MS = 2_500L

/** Part way through the swing: tilted, fading, shaded, with the latch's release ring still out. */
private val LID_WARMUP_POSE = reopenLidPose(REOPEN_LID_START_MS + 0.3f * (REOPEN_END_MS - REOPEN_LID_START_MS)).copy(alpha = 0.02f)
private const val LID_WARMUP_LATCH_MS = REOPEN_LID_START_MS + 40f
