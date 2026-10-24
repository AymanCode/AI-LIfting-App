package com.ayman.ecolift.ui.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot
import com.ayman.ecolift.data.progress.highlights.ProgressHighlightFrame
import kotlin.math.min

@Composable
fun ProgressHighlightsReel(
    snapshot: HighlightReelSnapshot,
    onBack: () -> Unit,
    initialPage: Int = 0,
    onOpenReport: (Long) -> Unit = {},
    onPageColorChanged: (Color) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val frames = snapshot.frames

    if (frames.isEmpty()) {
        LaunchedEffect(Unit) { onPageColorChanged(HighlightColors.Ink) }
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(HighlightColors.Ink),
        ) {
            val posterScale = min(
                maxWidth.value / HighlightPosterWidth,
                maxHeight.value / HighlightPosterHeight,
            )
            HighlightPosterViewport(scale = posterScale) {
                HighlightEmptyPoster()
                HighlightReelChrome(
                    pageCount = 0,
                    currentPage = 0,
                    onBack = onBack,
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(frames.indices), pageCount = { frames.size })
    val activePage = pagerState.currentPage.coerceIn(frames.indices)
    val activeColor = frames[activePage].chapterColor()

    LaunchedEffect(activePage, activeColor) {
        onPageColorChanged(activeColor)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(activeColor),
    ) {
        val posterScale = min(
            maxWidth.value / HighlightPosterWidth,
            maxHeight.value / HighlightPosterHeight,
        )

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val frame = frames[page]
            HighlightPosterViewport(
                scale = posterScale,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = snapshot.cards.getOrNull(page)?.accessibilitySummary.orEmpty()
                    stateDescription = "Highlight ${page + 1} of ${frames.size}"
                },
            ) {
                when (frame) {
                    is ProgressHighlightFrame.Beat -> BeatPoster(frame)
                    is ProgressHighlightFrame.RepeatableBest -> RepeatableBestPoster(frame)
                    else -> {
                        // Reserve room for additive chrome by scaling the entire approved composition
                        // uniformly. Its original drawing, number hierarchy and proof geometry stay intact.
                        Box(Modifier.requiredSize(HighlightPosterWidth.dp, HighlightPosterHeight.dp).graphicsLayer {
                            scaleX = 0.72f; scaleY = 0.72f
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = 54.6.dp.toPx(); translationY = 90.dp.toPx()
                        }) {
                            when (frame) {
                                is ProgressHighlightFrame.MatchCut -> MatchCutPoster(frame)
                                is ProgressHighlightFrame.CadenceBands -> CadenceBandsPoster(frame)
                                is ProgressHighlightFrame.RunningTotal -> RunningTotalPoster(frame)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }

        HighlightPosterViewport(scale = posterScale) {
            HighlightReelChrome(
                pageCount = frames.size,
                currentPage = activePage,
                onBack = onBack,
                calibration = frames[activePage].chromeCalibration(),
                card = snapshot.cards.getOrNull(activePage),
                onOpenReport = onOpenReport,
            )
        }
    }
}

internal fun ProgressHighlightFrame.chapterColor(): Color = when (this) {
    is ProgressHighlightFrame.MatchCut, is ProgressHighlightFrame.Beat -> HighlightColors.Orange
    is ProgressHighlightFrame.CadenceBands, is ProgressHighlightFrame.RepeatableBest -> HighlightColors.Cobalt
    is ProgressHighlightFrame.RunningTotal -> HighlightColors.Magenta
}

private fun ProgressHighlightFrame.chromeCalibration(): HighlightChromeCalibration = when (this) {
    is ProgressHighlightFrame.MatchCut, is ProgressHighlightFrame.Beat -> HighlightChromeCalibration.MatchCut
    is ProgressHighlightFrame.CadenceBands, is ProgressHighlightFrame.RepeatableBest -> HighlightChromeCalibration.Cadence
    is ProgressHighlightFrame.RunningTotal -> HighlightChromeCalibration.RunningTotal
}
