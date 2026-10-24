package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.normalizedBodyweightLoad

internal data class SmartSetAdjustment(
    val setId: Long,
    val sourceSetId: Long,
    val suggestedWeightLbs: Int?,
    val hasWeightSuggestion: Boolean,
    val suggestedIsBodyweight: Boolean,
    val suggestedReps: Int?,
    val baseline: SmartSetAdjustmentBaseline? = null,
)

/** Keep the original difference while a keypad passes through blanks and partial numbers. */
internal data class SmartSetAdjustmentBaseline(
    val source: SmartSetAdjustmentPreview,
    val target: SmartSetAdjustmentPreview,
)

/**
 * Smart adjustments are hints, not writes. Callers must choose whether a source edit should only
 * affect that source row or should calculate independent hints for its later sibling sets.
 */
internal enum class SmartSetPropagationPolicy {
    /** A direct/manual edit owns one row and must not make any later row appear edited. */
    ISOLATE_MANUAL_EDIT,

    /** Preserve the progression math, but emit it only as later-set suggestions. */
    SUGGEST_LATER_SETS,
}

/**
 * A calculation-only view of a set plus its pending hint. It deliberately is not a [WorkoutSet],
 * so suggestion values cannot accidentally travel through a repository write path.
 */
internal data class SmartSetAdjustmentPreview(
    val weightLbs: Int?,
    val reps: Int?,
    val isBodyweight: Boolean,
)

internal fun normalizedSetWeight(set: WorkoutSet, weightLbs: Int?): Int? =
    if (set.isBodyweight) normalizedBodyweightLoad(weightLbs) else weightLbs

/**
 * The primary value shown in an editable control is always the value actually stored for that row.
 * A suggestion belongs in separate hint UI and must never impersonate a persisted edit.
 */
internal fun <T> smartSetPrimaryValue(
    persistedValue: T?,
    @Suppress("UNUSED_PARAMETER") suggestedValue: T?,
): T? = persistedValue

/** Returns the optional hint channel without merging it into the persisted value channel. */
internal fun <T> smartSetSuggestionValue(suggestedValue: T?): T? = suggestedValue

internal fun smartAdjustmentPreview(
    set: WorkoutSet,
    adjustment: SmartSetAdjustment?,
): SmartSetAdjustmentPreview {
    if (adjustment == null || adjustment.setId != set.id) {
        return SmartSetAdjustmentPreview(
            weightLbs = set.weightLbs,
            reps = set.reps,
            isBodyweight = set.isBodyweight,
        )
    }

    val nextIsBodyweight = if (adjustment.hasWeightSuggestion) {
        adjustment.suggestedIsBodyweight
    } else {
        set.isBodyweight
    }
    return SmartSetAdjustmentPreview(
        weightLbs = if (adjustment.hasWeightSuggestion) {
            if (nextIsBodyweight) {
                normalizedBodyweightLoad(adjustment.suggestedWeightLbs)
            } else {
                adjustment.suggestedWeightLbs
            }
        } else {
            set.weightLbs
        },
        reps = adjustment.suggestedReps ?: set.reps,
        isBodyweight = nextIsBodyweight,
    )
}

/**
 * Converts a hint to a repository-write candidate only after an explicit accept action.
 * A missing or mismatched hint is ignored rather than being applied to the wrong row.
 */
internal fun acceptSmartAdjustment(
    set: WorkoutSet,
    adjustment: SmartSetAdjustment?,
): WorkoutSet {
    if (adjustment == null || adjustment.setId != set.id) return set
    val preview = smartAdjustmentPreview(set, adjustment)
    return set.copy(
        weightLbs = preview.weightLbs,
        reps = preview.reps,
        isBodyweight = preview.isBodyweight,
        completed = false,
    )
}

@Deprecated(
    message = "Suggestions are not persisted values. Use smartAdjustmentPreview for math or " +
        "acceptSmartAdjustment after an explicit accept action.",
)
// Temporary migration shim: delete once every ViewModel caller names preview vs explicit accept.
internal fun setWithSmartAdjustmentValues(
    set: WorkoutSet,
    adjustment: SmartSetAdjustment?,
): WorkoutSet = acceptSmartAdjustment(set, adjustment)

/**
 * Apply the source set's weight change to the target set's own prior value.
 *
 * Example: last workout was 185, 175. If today's first set becomes 190, the
 * second set becomes 180 rather than being flattened to 190.
 */
internal fun calculateSmartSuggestedWeight(
    sourceNewWeightLbs: Int?,
    sourceBaselineWeightLbs: Int?,
    targetBaselineWeightLbs: Int?,
): Int? {
    val newWeight = sourceNewWeightLbs ?: return null
    val sourceBaseline = sourceBaselineWeightLbs ?: return newWeight
    val targetBaseline = targetBaselineWeightLbs ?: return newWeight
    return (targetBaseline + (newWeight - sourceBaseline)).coerceAtLeast(0)
}

internal fun buildSmartAdjustmentsFromSource(
    current: Map<Long, SmartSetAdjustment>,
    sessionSets: List<WorkoutSet>,
    source: WorkoutSet,
    beforeSourceEdit: WorkoutSet?,
    propagationPolicy: SmartSetPropagationPolicy,
    keepSourceAdjustment: Boolean = false,
): Map<Long, SmartSetAdjustment> {
    val next = current
        .filterValues { it.sourceSetId != source.id }
        .let { adjustments ->
            if (keepSourceAdjustment && propagationPolicy == SmartSetPropagationPolicy.SUGGEST_LATER_SETS) {
                adjustments
            } else {
                adjustments.filterKeys { it != source.id }
            }
        }
        .toMutableMap()

    if (source.isWarmup || propagationPolicy == SmartSetPropagationPolicy.ISOLATE_MANUAL_EDIT) return next

    val laterSiblingSets = sessionSets
        .filter {
            it.exerciseId == source.exerciseId &&
                it.id != source.id &&
                !it.completed &&
                !it.isWarmup &&
                it.setNumber > source.setNumber
        }
        .sortedBy { it.setNumber }

    val sourceBefore = beforeSourceEdit ?: source
    val hasWeightSuggestion = source.isBodyweight || source.weightLbs != null
    if (laterSiblingSets.isEmpty()) return next

    laterSiblingSets.forEach { target ->
        // Chain suggestion math without constructing a fake persisted WorkoutSet.
        val previous = current[target.id]
        val baseline = previous?.takeIf { it.sourceSetId == source.id }?.baseline
            ?: SmartSetAdjustmentBaseline(
                source = smartAdjustmentPreview(sourceBefore, null),
                target = smartAdjustmentPreview(target, previous),
            )
        val effectiveTarget = baseline.target
        val targetIsBodyweight = if (hasWeightSuggestion) source.isBodyweight else effectiveTarget.isBodyweight
        val suggestedWeight = if (hasWeightSuggestion) {
            val adjusted = calculateSmartSuggestedWeight(
                sourceNewWeightLbs = normalizedSetWeight(source, source.weightLbs) ?: 0,
                sourceBaselineWeightLbs = baseline.source.weightLbs ?: 0,
                targetBaselineWeightLbs = if (effectiveTarget.isBodyweight) {
                    normalizedBodyweightLoad(effectiveTarget.weightLbs)
                } else {
                    effectiveTarget.weightLbs
                } ?: 0,
            )
            if (targetIsBodyweight) normalizedBodyweightLoad(adjusted) else adjusted
        } else {
            effectiveTarget.weightLbs
        }
        val suggestedReps = if (source.reps != null) {
            calculateSmartSuggestedReps(
                sourceNewReps = source.reps,
                sourceBaselineReps = baseline.source.reps,
                targetBaselineReps = effectiveTarget.reps,
                sourceSetNumber = source.setNumber,
                targetSetNumber = target.setNumber,
            )
        } else {
            effectiveTarget.reps
        }

        next[target.id] = SmartSetAdjustment(
            setId = target.id,
            sourceSetId = source.id,
            suggestedWeightLbs = suggestedWeight,
            hasWeightSuggestion = hasWeightSuggestion,
            suggestedIsBodyweight = targetIsBodyweight,
            suggestedReps = suggestedReps,
            baseline = baseline,
        )
    }
    return next
}

@Deprecated(
    message = "Choose ISOLATE_MANUAL_EDIT for direct input or SUGGEST_LATER_SETS for hint generation.",
)
// Temporary migration shim: delete once every caller passes propagationPolicy explicitly.
internal fun buildSmartAdjustmentsFromSource(
    current: Map<Long, SmartSetAdjustment>,
    sessionSets: List<WorkoutSet>,
    source: WorkoutSet,
    beforeSourceEdit: WorkoutSet?,
    keepSourceAdjustment: Boolean = false,
): Map<Long, SmartSetAdjustment> {
    // Naming propagationPolicy makes this resolve to the policy overload above, never this shim.
    return buildSmartAdjustmentsFromSource(
        current = current,
        sessionSets = sessionSets,
        source = source,
        beforeSourceEdit = beforeSourceEdit,
        propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
        keepSourceAdjustment = keepSourceAdjustment,
    )
}

/**
 * Move a later set by the same amount the edited set moved, keeping its own offset.
 *
 * This is a difference, not a ratio: "we notice the difference in the rep count 8 --> 5; 3 reps
 * lower so we can apply the same difference". The two agree often enough to hide the bug — last
 * session 10/8, edited 10 -> 9, gives 7 either way — and diverge as soon as reps climb. History
 * 5 then 8, first set pushed 5 -> 7, owes a suggestion of 10; the ratio form capped it back down
 * to 7 and quietly erased the progression.
 *
 * Set numbers no longer take part. The offset a set carries is its own reps against the edited
 * set's, and that is already in [targetBaselineReps]; ordering only decided which cap to apply,
 * and the caps were the ratio's problem, not a rule of the lift.
 */
internal fun calculateSmartSuggestedReps(
    sourceNewReps: Int?,
    sourceBaselineReps: Int?,
    targetBaselineReps: Int?,
    @Suppress("UNUSED_PARAMETER") sourceSetNumber: Int,
    @Suppress("UNUSED_PARAMETER") targetSetNumber: Int,
): Int? {
    val newReps = sourceNewReps?.takeIf { it > 0 } ?: return null
    val sourceBaseline = sourceBaselineReps?.takeIf { it > 0 } ?: return newReps
    val targetBaseline = targetBaselineReps?.takeIf { it > 0 } ?: return newReps

    // Below one the delta is unusable, so the set keeps the reps it already had rather than being
    // handed an impossible number rounded up to a single.
    val shifted = targetBaseline + (newReps - sourceBaseline)
    return if (shifted < 1) targetBaseline else shifted
}
