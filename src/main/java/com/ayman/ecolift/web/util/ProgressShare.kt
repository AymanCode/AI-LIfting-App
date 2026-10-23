package com.ayman.ecolift.web.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import com.ayman.ecolift.android.platform.AndroidHost

private const val RECAP_TITLE = "My EcoLift training recap"

/**
 * The web share sheet sends text only. Android does the same with ACTION_SEND.
 * The chooser does not report cancel, so a shown sheet is "shared" and a missing handler
 * falls through to the clipboard the way the browser falls through to copy.
 */
fun shareProgressRecap(text: String, onResult: (String) -> Unit) {
    val activity = AndroidHost.current()
    if (activity == null) {
        onResult("failed")
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, RECAP_TITLE)
        putExtra(Intent.EXTRA_SUBJECT, RECAP_TITLE)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        activity.startActivity(Intent.createChooser(send, RECAP_TITLE))
        onResult("shared")
    } catch (error: ActivityNotFoundException) {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            onResult("failed")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(RECAP_TITLE, text))
        onResult("copied")
    }
}
