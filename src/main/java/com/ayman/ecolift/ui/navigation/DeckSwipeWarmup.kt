package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LogGlassPalette
import com.ayman.ecolift.ui.theme.TabWarmupShell
import com.ayman.ecolift.ui.theme.warmupSweepsAllowed
import com.ayman.ecolift.ui.viewmodel.E1rmPointUi
import com.ayman.ecolift.ui.viewmodel.GhostSetUi
import com.ayman.ecolift.ui.viewmodel.RecommendationState

private const val DECK_SWIPE_WARMUP = "deck-swipe"

/** Before the tab warm-ups: the Log is the screen people open the app on and swipe first. */
private const val DECK_SWIPE_WARMUP_DELAY_MS = 2_000L

/**
 * Stops per page of the sweep, one frame each: enough for the pager's edge to cut through each
 * column of the card (the weight and reps fields, the set tiles), which is what the programs depend
 * on, while keeping the hidden frames few (each is a whole card, costly on a software renderer).
 */
private const val DECK_SWEEP_STOPS_PER_PAGE = 6
private const val DECK_SWEEP_FRAMES = 2 * DECK_SWEEP_STOPS_PER_PAGE + 2

/**
 * Compiles the Log deck's first-swipe shaders while nothing is moving (see
 * [com.ayman.ecolift.ui.theme.ShaderWarmup]). Mid-swipe the leaving card is cut straight along one
 * side and the arriving card along the other, and Skia needs a separate program for a rounded card
 * (and each rounded field inside it) cut on its left or on its right, so the first swipe after every
 * launch compiled about twenty programs: 550 ms of stalls in Safari's engine, measured on /demo.
 * Here a pager of made-up cards, the real [RotationCard] at the real card size, steps itself
 * across two pages once per app load, stopping [DECK_SWEEP_STOPS_PER_PAGE] times on each.
 *
 * It cannot sit under the app background like the other warm-ups: that background is one
 * full-screen opaque fill, which Skia turns into a clear that throws away everything drawn before
 * it in the frame (warm-ups drawn through an offscreen layer survive; plain drawing like this does
 * not, and compiled nothing). So it is drawn just above the background and under the screens, which
 * take every touch first, and hidden by a patch of the background's own colour that stops 1dp short
 * of the top, so it is not a full-screen fill and nothing is thrown away. [enabled] is false where
 * that colour is not the background (Cardio's Dawn sky, the Highlights reel), and like the scroll
 * sweeps it runs only on a hardware renderer ([warmupSweepsAllowed]): on a software one a hidden
 * card frame is slow enough that a finger landing during it read as a hold, and the release gate's
 * reps scrub stopped registering.
 */
@Composable
internal fun DeckSwipeWarmup(palette: LogGlassPalette, bottomReserved: Dp, enabled: Boolean) {
    if (!enabled || !warmupSweepsAllowed) return
    TabWarmupShell(DECK_SWIPE_WARMUP, DECK_SWIPE_WARMUP_DELAY_MS, DECK_SWEEP_FRAMES, bottomReserved) {
        val cards = remember { warmupDeckCards() }
        SweptDeck(cards, palette, top = 16.dp)
        Box(Modifier.fillMaxSize().padding(top = 1.dp).background(EspressoPalette.bg))
    }
}

@Composable
private fun SweptDeck(cards: List<ExerciseLog>, palette: LogGlassPalette, top: Dp) {
    val pagerState = rememberPagerState(pageCount = { cards.size })
    LaunchedEffect(pagerState) {
        // Until its first layout the pager knows no pages or their width.
        withFrameNanos { }
        val stride = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
        repeat(2 * DECK_SWEEP_STOPS_PER_PAGE) {
            pagerState.scrollBy(stride.toFloat() / DECK_SWEEP_STOPS_PER_PAGE)
            withFrameNanos { }
        }
    }
    // The Log list's 16dp side padding.
    Box(Modifier.fillMaxSize().padding(start = 16.dp, top = top, end = 16.dp)) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            userScrollEnabled = false,
            // Cut at its sides as the cards are on the Log, or no card here is ever cut and nothing compiles.
            modifier = Modifier.fillMaxWidth().height(
                (rotationCardBaseHeightDp(showFullTrend = true) + ROTATION_CARD_LAST_TIME_DP).dp,
            ).clipToBounds(),
        ) { page ->
            val card = cards[page]
            RotationCard(
                exercise = card,
                positionLabel = rotationExercisePositionLabel(page + 1),
                splitLabel = "Upper Strength",
                visibleSets = card.sets,
                selectedSetId = card.sets.firstOrNull { !it.isCompleted }?.id,
                palette = palette,
                onWeightStep = { _, _ -> },
                onRepsStep = { _, _ -> },
                onWeightInput = {},
                onRepsInput = {},
                onRecommend = {},
                onMuscleGroupChange = {},
                recommendationState = RecommendationState.Idle,
                onSelectSet = {},
                onToggleLogged = {},
                onAddSet = {},
                onRemoveSet = {},
                onRemoveExercise = {},
                onPark = {},
            )
        }
    }
}

/**
 * Three cards like a workout in progress: a barbell lift half logged against last time, a
 * weighted bodyweight lift, and a lift with no history yet.
 */
private fun warmupDeckCards(): List<ExerciseLog> {
    fun set(id: Long, exerciseId: Long, number: Int, weight: String, reps: String, done: Boolean, bodyweight: Boolean = false) =
        LoggedSet(id = -id, exerciseId = exerciseId, setNumber = number, weight = weight, reps = reps, isBodyweight = bodyweight, isCompleted = done)
    fun trend(vararg e1rm: Float) = e1rm.mapIndexed { i, v -> E1rmPointUi("2026-0${6 + i / 4}-${10 + (i % 4) * 5}", v) }
    return listOf(
        ExerciseLog(
            cardId = "warmup-1", cardPosition = 0, displayMode = WorkoutCardDisplayMode.EXPANDED,
            exerciseId = -1, exerciseName = "Barbell Bench Press", muscleGroups = "Chest, Triceps",
            lastTopSet = "215×10", bestTopSet = "215×10",
            ghostSets = listOf(GhostSetUi(195, 10, false), GhostSetUi(205, 10, false), GhostSetUi(215, 10, false)),
            lastSessionDate = "2026-09-23",
            e1rmHistory = trend(262f, 268f, 266f, 275f, 279f, 283f, 281f, 287f),
            sets = listOf(set(1, -1, 1, "200", "11", true), set(2, -1, 2, "210", "11", true), set(3, -1, 3, "220", "10", false)),
        ),
        ExerciseLog(
            cardId = "warmup-2", cardPosition = 1, displayMode = WorkoutCardDisplayMode.EXPANDED,
            exerciseId = -2, exerciseName = "Pull Up", muscleGroups = "Back, Biceps",
            lastTopSet = "BW+10×9", bestTopSet = "BW+10×9",
            ghostSets = listOf(GhostSetUi(10, 9, true), GhostSetUi(10, 9, true)),
            lastSessionDate = "2026-09-23", userBodyweightLbs = 180,
            e1rmHistory = trend(231f, 236f, 238f, 242f),
            sets = listOf(set(4, -2, 1, "15", "10", true, bodyweight = true), set(5, -2, 2, "15", "9", false, bodyweight = true)),
        ),
        ExerciseLog(
            cardId = "warmup-3", cardPosition = 2, displayMode = WorkoutCardDisplayMode.EXPANDED,
            exerciseId = -3, exerciseName = "Incline Dumbbell Press", muscleGroups = "Chest",
            lastTopSet = null, bestTopSet = null, showBaselineHint = true,
            sets = listOf(set(6, -3, 1, "", "", false), set(7, -3, 2, "", "", false)),
        ),
    )
}
