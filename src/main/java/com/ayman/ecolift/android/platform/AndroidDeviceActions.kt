package com.ayman.ecolift.android.platform

import androidx.compose.runtime.compositionLocalOf

data class AndroidDeviceActions(
    val backup: () -> Unit,
    val restore: () -> Unit,
    val importHealth: () -> Unit,
    val healthSessionsAdded: Int?,
)

val LocalAndroidDeviceActions = compositionLocalOf<AndroidDeviceActions?> { null }
