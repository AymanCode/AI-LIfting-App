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

    @Test
    fun shippedCatalogWithNothingElseStillSeeds() {
        assertTrue(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy()))
        assertTrue(isShippedExerciseCatalog(SHIPPED_EXERCISE_CATALOG))
    }

    @Test
    fun anotherPersonalRowOrAChangedExerciseBlocksTheSeed() {
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(exercisesAreShippedCatalog = false)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasCycleSlots = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasSplitChoices = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasCardioSessions = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasSplitMembers = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasSettings = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasCycle = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasWorkoutDays = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasCards = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasPendingReviews = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasArchives = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasAliases = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasDistinctPairs = true)))
        assertFalse(shouldSeedDemo(debug = true, requested = true, hasSets = false, occupancy = DemoSeedOccupancy(hasSplitAlternatives = true)))
        assertFalse(shouldSeedDemo(debug = false, requested = true, hasSets = false, occupancy = DemoSeedOccupancy()))
        assertFalse(shouldSeedDemo(debug = true, requested = false, hasSets = false, occupancy = DemoSeedOccupancy()))
        val renamed = SHIPPED_EXERCISE_CATALOG.mapIndexed { index, row -> if (index == 0) row.copy(name = "Renamed") else row }
        val extra = SHIPPED_EXERCISE_CATALOG + SHIPPED_EXERCISE_CATALOG.first().copy(id = 99, name = "Extra Lift", createdAt = 1)
        val dropped = SHIPPED_EXERCISE_CATALOG.drop(1)
        assertFalse(isShippedExerciseCatalog(renamed))
        assertFalse(isShippedExerciseCatalog(extra))
        assertFalse(isShippedExerciseCatalog(dropped))
    }
}
