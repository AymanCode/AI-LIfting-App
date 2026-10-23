package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex

/** A single-scene overlay avoids the Wasm popup-disposal accessibility bug (CMP-10623). */
internal class SwapOverlayHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    private var owner: Any? = null
    var onEscape: (() -> Unit)? = null
        private set

    fun show(token: Any, onEscape: () -> Unit, content: @Composable () -> Unit) {
        owner = token
        this.onEscape = onEscape
        this.content = content
    }

    fun hide(token: Any) {
        if (owner === token) { owner = null; content = null; onEscape = null }
    }
}

internal val LocalSwapOverlayHost = staticCompositionLocalOf<SwapOverlayHost?> { null }

@Composable
internal fun InSwapOverlay(active: Boolean, onEscape: () -> Unit, content: @Composable () -> Unit) {
    val host = LocalSwapOverlayHost.current
    val token = remember { Any() }
    val latest by rememberUpdatedState(content)
    val latestEscape by rememberUpdatedState(onEscape)
    DisposableEffect(host, active) {
        if (active) host?.show(token, { latestEscape() }) { latest() }
        onDispose { host?.hide(token) }
    }
    if (host == null && active) content()
}

@Composable
internal fun SwapOverlayRoot(host: SwapOverlayHost, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val active = host.content != null
    androidx.activity.compose.BackHandler(enabled = active) { host.onEscape?.invoke() }
    CompositionLocalProvider(LocalSwapOverlayHost provides host) {
        Box(modifier) {
            Box(if (active) Modifier.clearAndSetSemantics { } else Modifier) {
                content()
            }
            host.content?.let { overlay ->
                Box(Modifier.fillMaxSize().zIndex(3f)) { overlay() }
            }
        }
    }
}
