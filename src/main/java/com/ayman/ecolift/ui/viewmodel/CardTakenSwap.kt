package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.splits.SplitAlternative

/** Visibility is projected on data changes, never calculated by the resting card. */
internal fun canUseTakenChip(slotId: Long?, alternativeCount: Int, hasCheckedSet: Boolean): Boolean =
    slotId != null && slotId > 0 && alternativeCount > 1 && !hasCheckedSet

/** Captured with a Start request; a not-yet-saved group has no membership write or message. */
internal fun proposalAppends(slotId: Long?, swaps: Map<Long, Long>): List<SplitAlternative> =
    if (slotId == null || slotId <= 0) emptyList() else swaps.filter { it.key != it.value }.map { (source, target) -> SplitAlternative(slotId, source, target) }
