package com.ayman.ecolift.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A one-shot request for the existing group sheet, kept out of browser route arguments. */
internal object DeskSplitDeepLink {
    private val slot = MutableStateFlow<Long?>(null)
    val pending = slot.asStateFlow()
    fun request(slotId: Long) { if (slotId > 0) slot.value = slotId }
    fun clear() { slot.value = null }
}
