package com.ayman.ecolift.data.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VolumeEquivalenceTest {
    @Test
    fun zero_volume_has_no_equivalence() {
        assertNull(volumeEquivalence(0f))
    }

    @Test
    fun below_pickup_tracks_first_pickup_milestone() {
        val equivalence = volumeEquivalence(3_000f)

        assertEquals("getting started", equivalence?.label)
        assertEquals("1,000 lb to your first pickup truck", equivalence?.nextMilestoneLabel)
        assertTrue((equivalence?.progressToNext ?: -1f) in 0f..1f)
    }

    @Test
    fun elephant_count_and_next_number_use_floor_count() {
        val equivalence = volumeEquivalence(23_090f)

        assertEquals("about 2 elephants", equivalence?.label)
        assertEquals("3,910 lb to elephant #3", equivalence?.nextMilestoneLabel)
        assertTrue((equivalence?.progressToNext ?: -1f) in 0f..1f)
    }

    @Test
    fun large_volume_uses_semi_trucks_and_next_truck_number() {
        val equivalence = volumeEquivalence(1_131_450f)

        assertEquals("about 32 semi trucks", equivalence?.label)
        assertEquals("23,550 lb to semi truck #33", equivalence?.nextMilestoneLabel)
        assertTrue((equivalence?.progressToNext ?: 0f) > 0f)
        assertTrue((equivalence?.progressToNext ?: 1f) < 1f)
    }
}
