package com.ayman.ecolift.web.util

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralTest {
    @Test
    fun singular_and_plural_labels() {
        assertEquals("1 session", countLabel(1, "session"))
        assertEquals("2 sessions", countLabel(2, "session"))
        assertEquals("0 sets", countLabel(0, "set"))
        assertEquals("1 REP", countLabel(1, "REP"))
        assertEquals("3 REPS", countLabel(3, "REP"))
        assertEquals("2 entries", countLabel(2, "entry", "entries"))
    }

    @Test
    fun noun_only() {
        assertEquals("rep", noun(1, "rep"))
        assertEquals("reps", noun(4, "rep"))
        assertEquals("REPS", noun(4, "REP"))
    }
}
