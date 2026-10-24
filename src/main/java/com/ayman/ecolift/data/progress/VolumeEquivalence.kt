package com.ayman.ecolift.data.progress

import com.ayman.ecolift.web.util.groupThousands
import kotlin.math.ceil
import kotlin.math.floor

internal data class VolumeEquivalence(
    val label: String,
    val nextMilestoneLabel: String,
    val progressToNext: Float,
)

private data class VolumeUnit(
    val thresholdLbs: Float,
    val singular: String,
    val plural: String,
)

private val VolumeUnits = listOf(
    VolumeUnit(35_000f, "semi truck", "semi trucks"),
    VolumeUnit(25_000f, "school bus", "school buses"),
    VolumeUnit(9_000f, "elephant", "elephants"),
    VolumeUnit(4_000f, "pickup truck", "pickup trucks"),
)

internal fun volumeEquivalence(totalVolumeLbs: Float): VolumeEquivalence? {
    if (totalVolumeLbs <= 0f) return null

    val unit = VolumeUnits.firstOrNull { totalVolumeLbs / it.thresholdLbs >= 1f }
    if (unit == null) {
        val firstUnit = VolumeUnits.last()
        val remaining = ceil((firstUnit.thresholdLbs - totalVolumeLbs).toDouble()).toLong().coerceAtLeast(0L)
        return VolumeEquivalence(
            label = "getting started",
            nextMilestoneLabel = "${groupThousands(remaining)} lb to your first ${firstUnit.singular}",
            progressToNext = (totalVolumeLbs / firstUnit.thresholdLbs).coerceIn(0f, 1f),
        )
    }

    val count = floor((totalVolumeLbs / unit.thresholdLbs).toDouble()).toInt().coerceAtLeast(1)
    val nextMilestoneTotal = (count + 1) * unit.thresholdLbs
    val remaining = ceil((nextMilestoneTotal - totalVolumeLbs).toDouble()).toLong().coerceAtLeast(0L)
    val progressToNext = ((totalVolumeLbs - count * unit.thresholdLbs) / unit.thresholdLbs)
        .coerceIn(0f, 1f)

    return VolumeEquivalence(
        label = "about $count ${if (count == 1) unit.singular else unit.plural}",
        nextMilestoneLabel = "${groupThousands(remaining)} lb to ${unit.singular} #${count + 1}",
        progressToNext = progressToNext,
    )
}
