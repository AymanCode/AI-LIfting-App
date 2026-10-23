package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.rememberReducedMotion


/** One retained app-scene surface for the ledger and active-card swap flows. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun <T : Any> SwapSheetFrame(
    state: T?,
    busy: Boolean = false,
    centered: Boolean = false,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(T, Boolean, Boolean, FocusRequester, () -> Unit) -> Unit,
) {
    var displayed by remember { mutableStateOf(state) }
    val progress = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    SideEffect { if (state != null) displayed = state }
    LaunchedEffect(state != null) {
        progress.animateTo(if (state != null) 1f else 0f, tween(if (reduced) 0 else if (state != null) 220 else 180))
        if (state == null) displayed = null
    }
    val shown = state ?: displayed
    InSwapOverlay(shown != null, onEscape = { if (state != null && !busy) onDismiss() }) {
        if (shown != null) {
            val focus = LocalFocusManager.current
            val closeFocus = remember { FocusRequester() }
            val interactive = state != null && !busy
            val dismiss = { if (interactive) { focus.clearFocus(); onDismiss() } }
            val keyboard = 0.dp
            val height = (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } - keyboard).coerceAtLeast(0.dp)
            val tight = height < 500.dp
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f * progress.value))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss)
                    .semantics { contentDescription = "Close swap sheet" })
                CompositionLocalProvider(LocalContentColor provides EspressoPalette.ink) {
                    Column(Modifier.align(if (centered) Alignment.Center else Alignment.BottomCenter)
                        .offset(y = if (centered) 0.dp else -keyboard)
                        .widthIn(max = 640.dp).fillMaxWidth()
                        .then(if (centered) Modifier else Modifier.height((height * .90f).coerceAtMost(650.dp)))
                        .graphicsLayer { translationY = if (centered) 0f else size.height * (1f - progress.value) }
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = if (centered) 28.dp else 0.dp, bottomEnd = if (centered) 28.dp else 0.dp))
                        .background(EspressoPalette.panel)
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                        .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
                        if (!centered) {
                            LaunchedEffect(Unit) { closeFocus.requestFocus() }
                            Box(Modifier.fillMaxWidth().height(22.dp).pointerInput(interactive) {
                                var distance = 0f
                                detectVerticalDragGestures(onDragStart = { distance = 0f },
                                    onDragEnd = { if (distance > 48.dp.toPx()) dismiss() }) { change, dy -> change.consume(); distance += dy }
                            }, contentAlignment = Alignment.Center) {
                                Box(Modifier.size(42.dp, 4.dp).clip(RoundedCornerShape(3.dp)).background(EspressoPalette.dim.copy(alpha = .45f)))
                            }
                        }
                        content(shown, interactive, tight, closeFocus, dismiss)
                    }
                }
            }
        }
    }
}
