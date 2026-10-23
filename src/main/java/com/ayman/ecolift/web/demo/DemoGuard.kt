package com.ayman.ecolift.web.demo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DemoGuard {
    const val MESSAGE = "Demo with sample data — editing is disabled."

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    const val isDemo: Boolean = false

    fun notifyBlocked() {
        _events.tryEmit(MESSAGE)
    }

    fun blockIfDemo(): Boolean = false
}
