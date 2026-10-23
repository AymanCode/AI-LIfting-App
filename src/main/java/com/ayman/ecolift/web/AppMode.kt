package com.ayman.ecolift.web

enum class AppMode { Authenticated, Demo }

/**
 * Android is the local signed-in user. Demo mode never sticks, so DemoGuard does not block.
 */
object CurrentAppMode {
    var mode: AppMode = AppMode.Authenticated
        set(ignored) {
            field = AppMode.Authenticated
        }
}

fun detectAppMode(pathname: String, search: String): AppMode = AppMode.Authenticated

fun currentAppMode(): AppMode = AppMode.Authenticated
