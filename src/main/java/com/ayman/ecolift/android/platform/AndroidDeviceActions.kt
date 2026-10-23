package com.ayman.ecolift.android.platform

import androidx.compose.runtime.compositionLocalOf

data class AndroidDeviceActions(
    val backup: () -> Unit,
    val restore: () -> Unit,
    val localBackups: () -> Unit,
    val busy: Boolean,
    val importHealth: () -> Unit,
    val healthSessionsAdded: Int?,
)

val LocalAndroidDeviceActions = compositionLocalOf<AndroidDeviceActions?> { null }
