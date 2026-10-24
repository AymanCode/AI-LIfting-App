package com.ayman.ecolift.web

import androidx.compose.runtime.staticCompositionLocalOf

val LocalRepos = staticCompositionLocalOf<AppRepos> {
    error("LocalRepos not provided — must be set inside LoggedInApp")
}
