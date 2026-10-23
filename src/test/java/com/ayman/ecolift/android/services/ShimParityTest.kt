package com.ayman.ecolift.android.services

import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.currentAppMode
import com.ayman.ecolift.web.detectAppMode
import com.ayman.ecolift.web.installLogSearch
import com.ayman.ecolift.web.logDateFromLocation
import com.ayman.ecolift.web.logDateFromSearch
import com.ayman.ecolift.web.scaledPhotoSize
import com.ayman.ecolift.web.util.hapticConstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant

class ShimParityTest {
    @get:Rule
    val searchClearedAfterTheTest = object : TestWatcher() {
        override fun finished(description: Description) {
            assertNull(logDateFromLocation())
        }
    }

    @After
    fun clearInstalledSearch() {
        installLogSearch("")
    }

    @Test
    fun `log date reads an iso date query and ignores the rest`() {
        assertEquals("2026-09-02", logDateFromSearch("?date=2026-09-02"))
        assertEquals("2026-09-02", logDateFromSearch("?qaPhone=1&date=2026-09-02&x=1"))
        assertEquals("2026-09-02", logDateFromSearch("?date=2026%2d09%2d02"))
        assertNull(logDateFromSearch(""))
        assertNull(logDateFromSearch("?demo=1"))
        assertNull(logDateFromSearch("?date="))
        assertNull(logDateFromSearch("?date=yesterday"))
        assertNull(logDateFromSearch("?date=2026-9-2"))
    }

    @Test
    fun `installed location search is the log date`() {
        installLogSearch("?date=2026-09-02")
        assertEquals("2026-09-02", logDateFromLocation())
        installLogSearch("")
        assertNull(logDateFromLocation())
    }

    @Test
    fun `installed location search does not outlive the test`() {
        installLogSearch("?date=2026-09-02")
        assertEquals("2026-09-02", logDateFromLocation())
    }

    @Test
    fun `demo mode does not stick`() {
        CurrentAppMode.mode = AppMode.Demo
        assertEquals(AppMode.Authenticated, CurrentAppMode.mode)
        assertEquals(AppMode.Authenticated, currentAppMode())
        assertEquals(AppMode.Authenticated, detectAppMode("/demo", "?demo=1"))
    }

    @Test
    fun `photo long edge caps at 1600 and small photos stay`() {
        assertEquals(1600 to 1200, scaledPhotoSize(4000, 3000))
        assertEquals(800 to 600, scaledPhotoSize(800, 600))
    }

    @Test
    fun `haptic kinds map to the closest android constant`() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(HapticFeedbackType.LongPress))
        assertEquals(
            HapticFeedbackConstants.CONTEXT_CLICK,
            hapticConstant(HapticFeedbackType.GestureThresholdActivate),
        )
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(HapticFeedbackType.TextHandleMove))
    }

    @Test
    fun `health connect uid is claimed once`() {
        val seen = mutableSetOf("hc-1")
        assertFalse(claimNewHealthConnectUid(seen, "hc-1"))
        assertFalse(claimNewHealthConnectUid(seen, ""))
        assertFalse(claimNewHealthConnectUid(seen, " "))
        assertFalse(claimNewHealthConnectUid(seen, null))
        assertTrue(claimNewHealthConnectUid(seen, "hc-2"))
        assertFalse(claimNewHealthConnectUid(seen, "hc-2"))
        assertEquals(setOf("hc-1", "hc-2"), seen)
    }

    @Test
    fun `health connect session uses the zone date and the stored uid`() {
        val start = Instant.parse("2026-10-02T03:30:00Z").toEpochMilli()
        val end = Instant.parse("2026-10-02T04:02:15Z").toEpochMilli()
        val session = sessionFromHealthConnect(
            activityType = "TREADMILL",
            startEpochMilli = start,
            endEpochMilli = end,
            zoneOffsetSeconds = -28_800,
            hcUid = "hc-2",
            dataOriginPackage = "com.example.watch",
            lastModifiedEpochMilli = 1_700_000_000_000L,
            nowEpochMilli = 1_700_000_100_000L,
            localUuid = "uuid-fixed",
        )

        assertEquals("2026-10-01", session.date)
        assertEquals(1935, session.durationSec)
        assertEquals("TREADMILL", session.activityType)
        assertEquals(CardioSessionSource.HEALTH_CONNECT, session.source)
        assertEquals("hc-2", session.hcUid)
        assertEquals("com.example.watch", session.hcDataOriginPackage)
        assertEquals(1_700_000_000_000L, session.hcLastModifiedTime)
        assertEquals(-28_800, session.zoneOffsetSeconds)
        assertEquals(start, session.startTime)
        assertEquals(end, session.endTime)
        assertEquals("uuid-fixed", session.localUuid)
    }
}
