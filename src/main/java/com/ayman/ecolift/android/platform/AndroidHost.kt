package com.ayman.ecolift.android.platform

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ayman.ecolift.web.installLogSearch

/**
 * The one activity handle the web-package shims share.
 * MainActivity calls [install] before setContent. A second call on the same instance
 * only refreshes the log deep link, which is what onNewIntent needs after setIntent.
 */
object AndroidHost {
    private var activity: ComponentActivity? = null

    fun current(): ComponentActivity? = activity

    fun install(activity: ComponentActivity) {
        if (this.activity !== activity) {
            this.activity = activity
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (this@AndroidHost.activity === activity) {
                        this@AndroidHost.activity = null
                    }
                }
            })
        }
        installLogSearch(searchFrom(activity.intent))
    }

    private fun searchFrom(intent: Intent?): String {
        val query = intent?.data?.query ?: return ""
        return if (query.isEmpty()) "" else "?$query"
    }
}
