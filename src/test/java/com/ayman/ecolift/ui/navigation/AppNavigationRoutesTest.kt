package com.ayman.ecolift.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationRoutesTest {
    @Test
    fun `split log route carries selected split id`() {
        assertEquals("log/42", buildLogRouteForSplit(42L))
    }

    @Test
    fun `exercise progress route carries selected exercise id`() {
        assertEquals("progress/99", buildProgressRouteForExercise(99L))
    }

    @Test
    fun `cycle archive route carries selected archive id`() {
        assertEquals("cycleArchive/7", buildCycleArchiveRoute(7L))
    }

    @Test
    fun `bottom nav treats detail routes as their parent tab`() {
        assertTrue(isRouteSelected(currentRoute = "log/42", tabRoute = "log"))
        assertTrue(isRouteSelected(currentRoute = "progress/99", tabRoute = "progress"))
        assertTrue(isRouteSelected(currentRoute = "cycleArchive/7", tabRoute = "split"))
        assertFalse(isRouteSelected(currentRoute = "progress/99", tabRoute = "log"))
    }
}
