package com.ayman.ecolift.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.ayman.ecolift.R

/**
 * Stand-ins for writer E's FontLoader, QuietDepth and ShaderWarmup.
 * Delete this file when those three files arrive.
 */

object QuietDepth {
    val top = EspressoPalette.bg
    val middle = EspressoPalette.bg
    val bottom = EspressoPalette.bg
    val navigation = EspressoPalette.mast
    val ink = EspressoPalette.ink
    val muted = EspressoPalette.dim
    val subtle = EspressoPalette.faint
    val accent = EspressoPalette.chest
    val line = EspressoPalette.hair
    val negative = EspressoPalette.danger
}

var QuietUiFontFamily: FontFamily = FontFamily.SansSerif

@Composable
fun QuietDepthBackground() {
    Box(Modifier.fillMaxSize().background(EspressoPalette.bg))
}

enum class QuietIconName {
    Calendar,
    Progress,
    Sparkles,
    Heart,
    Split,
    Search,
    Left,
    Right,
    Down,
    Trophy,
    Dumbbell,
    Arrow,
}

@Composable
fun QuietIcon(
    name: QuietIconName,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = QuietDepth.muted,
    selected: Boolean = false,
) {
    val res = if (selected) selectedRes(name) else idleRes(name)
    Icon(painterResource(res), description, modifier, tint)
}

private fun idleRes(name: QuietIconName): Int = when (name) {
    QuietIconName.Calendar -> R.drawable.quiet_calendar
    QuietIconName.Progress -> R.drawable.quiet_progress
    QuietIconName.Sparkles -> R.drawable.quiet_sparkles
    QuietIconName.Heart -> R.drawable.quiet_heart
    QuietIconName.Split -> R.drawable.quiet_split
    QuietIconName.Search -> R.drawable.quiet_search
    QuietIconName.Left -> R.drawable.quiet_left
    QuietIconName.Right -> R.drawable.quiet_right
    QuietIconName.Down -> R.drawable.quiet_down
    QuietIconName.Trophy -> R.drawable.quiet_trophy
    QuietIconName.Dumbbell -> R.drawable.quiet_dumbbell
    QuietIconName.Arrow -> R.drawable.quiet_arrow
}

private fun selectedRes(name: QuietIconName): Int = when (name) {
    QuietIconName.Calendar -> R.drawable.quiet_calendar_active
    QuietIconName.Progress -> R.drawable.quiet_progress_active
    QuietIconName.Sparkles -> R.drawable.quiet_sparkles_active
    QuietIconName.Heart -> R.drawable.quiet_heart_active
    QuietIconName.Split -> R.drawable.quiet_split_active
    else -> idleRes(name)
}

@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

suspend fun loadAppFonts() {
    QuietUiFontFamily = FontFamily(
        Font(R.font.arimo_regular, FontWeight.Normal),
        Font(R.font.arimo_medium, FontWeight.Medium),
        Font(R.font.arimo_bold, FontWeight.Bold),
    )
    LogUiFontFamily = FontFamily(
        Font(R.font.satoshi_regular, FontWeight.Normal),
        Font(R.font.satoshi_medium, FontWeight.Medium),
        Font(R.font.satoshi_bold, FontWeight.Bold),
    )
    LogDisplayFontFamily = FontFamily(
        Font(R.font.clash_grotesk_semibold, FontWeight.SemiBold),
        Font(R.font.clash_grotesk_bold, FontWeight.Bold),
    )
    PosterFontFamily = FontFamily(Font(R.font.archivo_black, FontWeight.Normal))
    MarkerFontFamily = FontFamily(Font(R.font.caveat_variable, FontWeight.Normal))
    HighlightsDisplayFontFamily = FontFamily(Font(R.font.league_gothic_condensed, FontWeight.Normal))
    HighlightsImpactFontFamily = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))
    HighlightsCondensedImpactFontFamily = FontFamily(Font(R.font.ecolift_impact_condensed, FontWeight.Normal))
    SheetSerifFontFamily = FontFamily(
        Font(R.font.fraunces_light, FontWeight.Light),
        Font(R.font.fraunces_light_italic, FontWeight.Light, FontStyle.Italic),
    )
    SheetSansFontFamily = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    )
}

internal const val TAB_TRANSITION_MS = 230
internal const val TAB_SHIFT_DP = 17
internal val TabTransitionEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)
internal const val TAB_ARRIVAL_FRAMES = 4
internal val warmupSweepsAllowed: Boolean = false

/**
 * Sweeps stay off until writer E's ShaderWarmup arrives.
 * Reporting every key done keeps the hidden copy uncomposed.
 */
internal object ShaderWarmup {
    fun touched() = Unit

    fun isDone(key: String): Boolean = true

    suspend fun run(
        key: String,
        frames: Int = 3,
        yieldToTouch: Boolean = false,
        show: (Boolean) -> Unit,
    ): Boolean {
        if (key.isEmpty() && frames < 0 && yieldToTouch) return false
        show(false)
        return true
    }
}

internal fun Modifier.watchTouchesForWarmup(): Modifier = this

@Composable
internal fun TabWarmupShell(
    key: String,
    delayMs: Long,
    frames: Int,
    bottomReserved: Dp,
    content: @Composable () -> Unit,
) {
    // Hidden shader warm-up is writer E's ShaderWarmup. Skip it until that file arrives.
}

@Composable
internal fun TabArrival(content: @Composable () -> Unit) {
    content()
}

@Composable
internal fun WarmupSweep(state: LazyListState, stepDp: Float, frames: Int) = Unit
