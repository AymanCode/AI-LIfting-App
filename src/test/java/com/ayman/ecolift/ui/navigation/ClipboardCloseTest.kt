package com.ayman.ecolift.ui.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardCloseTest {
    @Test
    fun closing_after_an_add_still_clears_a_typed_query() {
        assertTrue(clipboardCloseNotifiesQuery(queryDraft = "bench", query = ""))
    }

    @Test
    fun closing_with_nothing_typed_does_not_notify() {
        assertFalse(clipboardCloseNotifiesQuery(queryDraft = "", query = ""))
    }

    @Test
    fun closing_from_the_parent_field_notifies_even_after_an_add() {
        assertTrue(clipboardCloseNotifiesQuery(queryDraft = "", query = "squat"))
    }
}
