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
        assertTrue(isRouteSelected(currentRoute = "cardio", tabRoute = "cardio"))
        assertTrue(isRouteSelected(currentRoute = "cycleArchive/7", tabRoute = "split"))
        assertFalse(isRouteSelected(currentRoute = "progress/99", tabRoute = "log"))
        assertFalse(isRouteSelected(currentRoute = "cardio", tabRoute = "ai"))
    }

    @Test
    fun `bottom nav never suppresses log taps`() {
        assertFalse(shouldIgnoreBottomTabTap(currentRoute = "progress", targetRoute = "log"))
        assertFalse(shouldIgnoreBottomTabTap(currentRoute = "ai", targetRoute = "log"))
        assertFalse(shouldIgnoreBottomTabTap(currentRoute = "log/42", targetRoute = "log"))
        assertFalse(shouldIgnoreBottomTabTap(currentRoute = "log", targetRoute = "log"))
    }

    @Test
    fun `bottom nav still suppresses selected non log taps`() {
        assertTrue(shouldIgnoreBottomTabTap(currentRoute = "progress", targetRoute = "progress"))
        assertTrue(shouldIgnoreBottomTabTap(currentRoute = "progress/99", targetRoute = "progress"))
        assertTrue(shouldIgnoreBottomTabTap(currentRoute = "cycleArchive/7", targetRoute = "split"))
        assertFalse(shouldIgnoreBottomTabTap(currentRoute = "progress", targetRoute = "ai"))
    }
}
