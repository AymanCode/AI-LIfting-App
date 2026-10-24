package com.ayman.ecolift.web.util

import android.util.Log

/** Phone stand-in for the web console breadcrumb. Warnings stay out of the error channel. */
fun consoleWarn(message: String) {
    Log.w("EcoLift", message)
}
