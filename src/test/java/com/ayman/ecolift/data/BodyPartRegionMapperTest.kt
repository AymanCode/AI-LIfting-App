package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BodyPartRegionMapperTest {

    @Test
    fun maps_chest_correctly() {
        assertEquals(BodyPartRegion.CHEST, BodyPartRegionMapper.mapGroupToRegion("CHEST"))
        assertEquals(BodyPartRegion.CHEST, BodyPartRegionMapper.mapGroupToRegion(" chest "))
    }

    @Test
    fun maps_back_correctly() {
        assertEquals(BodyPartRegion.BACK, BodyPartRegionMapper.mapGroupToRegion("BACK"))
        assertEquals(BodyPartRegion.BACK, BodyPartRegionMapper.mapGroupToRegion("Back"))
    }

    @Test
    fun maps_shoulders_correctly() {
        assertEquals(BodyPartRegion.SHOULDERS, BodyPartRegionMapper.mapGroupToRegion("SHOULDERS"))
        assertEquals(BodyPartRegion.SHOULDERS, BodyPartRegionMapper.mapGroupToRegion("shoulders"))
    }

    @Test
    fun maps_arms_correctly() {
        assertEquals(BodyPartRegion.ARMS, BodyPartRegionMapper.mapGroupToRegion("BICEPS"))
        assertEquals(BodyPartRegion.ARMS, BodyPartRegionMapper.mapGroupToRegion("triceps"))
        assertEquals(BodyPartRegion.ARMS, BodyPartRegionMapper.mapGroupToRegion("FOREARMS"))
    }

    @Test
    fun maps_legs_correctly() {
        assertEquals(BodyPartRegion.LEGS, BodyPartRegionMapper.mapGroupToRegion("QUADS"))
        assertEquals(BodyPartRegion.LEGS, BodyPartRegionMapper.mapGroupToRegion("hamstrings"))
        assertEquals(BodyPartRegion.LEGS, BodyPartRegionMapper.mapGroupToRegion("glutes"))
        assertEquals(BodyPartRegion.LEGS, BodyPartRegionMapper.mapGroupToRegion("CALVES"))
    }

    @Test
    fun maps_core_correctly() {
        assertEquals(BodyPartRegion.CORE, BodyPartRegionMapper.mapGroupToRegion("CORE"))
        assertEquals(BodyPartRegion.CORE, BodyPartRegionMapper.mapGroupToRegion("core"))
    }

    @Test
    fun maps_unknown_correctly() {
        assertEquals(BodyPartRegion.UNKNOWN, BodyPartRegionMapper.mapGroupToRegion("UNKNOWN"))
        assertEquals(BodyPartRegion.UNKNOWN, BodyPartRegionMapper.mapGroupToRegion(""))
        assertEquals(BodyPartRegion.UNKNOWN, BodyPartRegionMapper.mapGroupToRegion("nonexistent"))
    }
}
