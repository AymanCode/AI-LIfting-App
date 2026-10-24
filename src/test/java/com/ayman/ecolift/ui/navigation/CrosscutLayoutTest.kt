package com.ayman.ecolift.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosscutLayoutTest {
    @Test
    fun portraitClearanceMatchesTheFixedBarAndHasNoSideInset() {
        val clearance = chromeClearance(
            navigationStart = 0.dp,
            navigationEnd = 0.dp,
            navigationBottom = 48.dp,
            cutoutStart = 0.dp,
            cutoutEnd = 0.dp,
            bottomBarHeight = 74.dp,
            showBottomBar = true,
        )
        assertEquals(0.dp, clearance.start)
        assertEquals(0.dp, clearance.end)
        assertEquals(122.dp, clearance.bottom)
    }

    @Test
    fun landscapeContentClearsTheSideNavigationBar() {
        val clearance = chromeClearance(
            navigationStart = 0.dp,
            navigationEnd = 48.dp,
            navigationBottom = 0.dp,
            cutoutStart = 0.dp,
            cutoutEnd = 12.dp,
            bottomBarHeight = 74.dp,
            showBottomBar = true,
        )
        assertEquals(0.dp, clearance.start)
        assertEquals(48.dp, clearance.end)
        assertEquals(74.dp, clearance.bottom)
    }

    @Test
    fun openKeyboardDropsTheHiddenBarReserveAndKeepsTheSideInset() {
        assertFalse(contentShowsBottomBar("ai", keyboardVisible = true, reportVisit = false))
        val clearance = chromeClearance(
            navigationStart = 0.dp,
            navigationEnd = 48.dp,
            navigationBottom = 0.dp,
            cutoutStart = 0.dp,
            cutoutEnd = 0.dp,
            bottomBarHeight = 74.dp,
            showBottomBar = contentShowsBottomBar("ai", keyboardVisible = true, reportVisit = false),
        )
        assertEquals(0.dp, clearance.bottom)
        assertEquals(48.dp, clearance.end)
    }

    @Test
    fun reportAndReelHideTheBarEvenWhenTheKeyboardIsClosed() {
        assertFalse(contentShowsBottomBar("log", keyboardVisible = false, reportVisit = true))
        assertFalse(contentShowsBottomBar(PROGRESS_HIGHLIGHTS_REEL_ROUTE, keyboardVisible = false, reportVisit = false))
        assertTrue(contentShowsBottomBar("log", keyboardVisible = false, reportVisit = false))
        val clearance = chromeClearance(
            navigationStart = 0.dp,
            navigationEnd = 0.dp,
            navigationBottom = 24.dp,
            cutoutStart = 0.dp,
            cutoutEnd = 0.dp,
            bottomBarHeight = 74.dp,
            showBottomBar = false,
        )
        assertEquals(0.dp, clearance.bottom)
    }

    @Test
    fun barHeightStays74AtScaleOneAndGrowsAtScaleTwo() {
        assertEquals(74.dp, bottomBarContentHeight(1f))
        assertEquals(76.dp, bottomBarContentHeight(2f))
        assertFalse(rowsGrowWithFontScale(1f))
        assertTrue(rowsGrowWithFontScale(2f))
    }

    @Test
    fun ironMindKeepsTheHeaderUnlessLandscapeTypingCannotFitIt() {
        assertTrue(ironMindPinsHeader(keyboardOpen = false, landscape = false))
        assertTrue(ironMindPinsHeader(keyboardOpen = false, landscape = true))
        assertTrue(ironMindPinsHeader(keyboardOpen = true, landscape = false))
        assertFalse(ironMindPinsHeader(keyboardOpen = true, landscape = true))
    }
}
