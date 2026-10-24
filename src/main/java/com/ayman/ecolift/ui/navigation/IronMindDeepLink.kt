package com.ayman.ecolift.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries the Splits tab's "open this smart split in IronMind" request across the tab switch,
 * out of band from the nav route. A nullable query argument on the "ai" route would have worked
 * too, but a query argument would have to round-trip through the route for every visit,
 * so the request lives here instead.
 * No persistence: a request is not meant to survive process death.
 *
 * Consumer contract: a pending id must stay pending until it matches a live item — the
 * hub's build starts empty and fills in asynchronously, so "not found yet" is not "invalid" and
 * must not clear it. [clear] should only be called once the id resolves, or when the screen
 * watching for it leaves composition (so an id that never resolves cannot outlive that visit).
 */
internal object IronMindDeepLink {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(itemId: String) {
        _pending.value = itemId
    }

    fun clear() {
        _pending.value = null
    }
}
