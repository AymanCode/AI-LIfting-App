package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuscleGroupsTest {
    @Test
    fun selectable_body_parts_exclude_broad_categories() {
        assertFalse("FULL BODY" in selectableBodyPartGroups)
        assertFalse("CARDIO" in selectableBodyPartGroups)
        assertFalse("OTHER" in selectableBodyPartGroups)
    }

    @Test
    fun selectable_parts_filter_legacy_broad_values() {
        assertEquals("BACK", normalizeBodyPartGroup(" back "))
        assertEquals(listOf("CHEST", "TRICEPS"), selectableBodyPartParts("CHEST · CARDIO · TRICEPS"))
        assertTrue(selectableBodyPartParts("FULL BODY").isEmpty())
    }
}
