package com.ayman.ecolift.data.progress

import com.ayman.ecolift.web.util.groupThousands
import kotlin.math.abs
import kotlin.math.floor

/**
 * A whimsical unit the month sheet expresses total load in — "24,318 chickens".
 * A model picks the noun and its weight once a week; this file does the arithmetic.
 * The model never computes a count, so the sheet's figures stay checkable.
 */
data class EquivalenceUnit(
    val singular: String,
    val plural: String,
    val weightLbs: Double,
)

object EquivalenceUnits {

    const val MIN_WEIGHT_LBS = 1.0
    const val MAX_WEIGHT_LBS = 5_000.0

    /**
     * Used when the model is unavailable, which includes every offline render.
     * Order is fixed so week seeding is reproducible.
     */
    val FALLBACKS: List<EquivalenceUnit> = listOf(
        EquivalenceUnit("chicken", "chickens", 6.0),
        EquivalenceUnit("sea otter", "sea otters", 65.0),
        EquivalenceUnit("washing machine", "washing machines", 170.0),
        EquivalenceUnit("vending machine", "vending machines", 600.0),
        EquivalenceUnit("grand piano", "grand pianos", 800.0),
        EquivalenceUnit("walrus", "walruses", 2_700.0),
    )

    /** Returns the unit when it is usable, or null. Callers fall back rather than repair. */
    fun validate(unit: EquivalenceUnit?): EquivalenceUnit? {
        if (unit == null) return null
        if (unit.singular.isBlank() || unit.plural.isBlank()) return null
        if (!unit.weightLbs.isFinite()) return null
        if (unit.weightLbs < MIN_WEIGHT_LBS || unit.weightLbs > MAX_WEIGHT_LBS) return null
        return unit
    }

    /** The model's pick when it validates, otherwise a fallback that is stable within the week. */
    fun forWeek(weekIndex: Int, modelChoice: EquivalenceUnit?): EquivalenceUnit {
        validate(modelChoice)?.let { return it }
        val index = abs(weekIndex.toLong()).rem(FALLBACKS.size.toLong()).toInt()
        return FALLBACKS[index]
    }

    /** How many of [unit] the given load amounts to. Rounds down; never negative. */
    fun countFor(totalLoadLbs: Double, unit: EquivalenceUnit): Int {
        if (totalLoadLbs <= 0.0 || unit.weightLbs <= 0.0) return 0
        return floor(totalLoadLbs / unit.weightLbs).toInt()
    }

    /** "24,318 chickens" / "1 walrus". */
    fun label(totalLoadLbs: Double, unit: EquivalenceUnit): String {
        val count = countFor(totalLoadLbs, unit)
        val noun = if (count == 1) unit.singular else unit.plural
        return "${groupThousands(count)} $noun"
    }
}
