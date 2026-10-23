package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot
import com.ayman.ecolift.ui.highlights.ProgressHighlightsReel
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.web.util.applyBrowserChrome

/**
 * Freezes one truthful reel chapter for the lifetime of this navigation entry.
 *
 * A background sync or edit may change the next reel the user opens, but it cannot add, remove,
 * or reorder pages underneath an in-progress swipe.
 */
@Composable
fun ProgressHighlightsRoute(
    snapshot: HighlightReelSnapshot,
    initialPage: Int,
    onOpenReport: (Long) -> Unit,
    onBack: () -> Unit,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(palette) {
        onDispose { applyBrowserChrome(palette) }
    }

    ProgressHighlightsReel(
        snapshot = snapshot,
        initialPage = initialPage,
        onOpenReport = onOpenReport,
        onBack = onBack,
        onPageColorChanged = { color -> applyBrowserChrome(color) },
        modifier = modifier,
    )
}
