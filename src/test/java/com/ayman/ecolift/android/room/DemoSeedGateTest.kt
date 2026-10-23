package com.ayman.ecolift.android.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedGateTest {
    @Test
    fun seedsOnlyADebugLaunchThatAsksAndFindsNoSets() {
        assertTrue(shouldSeedDemo(debug = true, requested = true, hasSets = false))
        assertFalse(shouldSeedDemo(debug = false, requested = true, hasSets = false))
        assertFalse(shouldSeedDemo(debug = true, requested = false, hasSets = false))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = true))
    }
}
