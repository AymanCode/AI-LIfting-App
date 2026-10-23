package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class SmartSetAdjusterTest {
    private fun set(
        id: Long,
        setNumber: Int,
        weightLbs: Int,
        reps: Int,
        completed: Boolean = false,
    ) = WorkoutSet(
        id = id,
        exerciseId = 1L,
        date = "2026-08-21",
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        completed = completed,
    )

    @Test
    fun typing_preserves_weight_offsets_even_when_an_intermediate_hint_hits_zero() {
        val sets = listOf(set(1, 1, 650, 8), set(2, 2, 600, 5), set(3, 3, 550, 3))
        var before = sets[0]
        var hints = emptyMap<Long, SmartSetAdjustment>()
        for (weight in listOf(null, 60, 650, 655)) {
            val source = before.copy(weightLbs = weight)
            hints = buildSmartAdjustmentsFromSource(
                hints, sets, source, before, SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
            )
            before = source
        }
        assertEquals(605, hints.getValue(2).suggestedWeightLbs)
        assertEquals(555, hints.getValue(3).suggestedWeightLbs)
        assertEquals(listOf(650, 600, 550), sets.map { it.weightLbs })
    }

    @Test
    fun first_set_delta_preserves_each_later_sets_previous_shape() {
        val sets = listOf(
            set(1, 1, weightLbs = 1_850, reps = 10),
            set(2, 2, weightLbs = 1_750, reps = 8),
            set(3, 3, weightLbs = 1_650, reps = 6),
        )

        val adjustments = buildSmartAdjustmentsFromSource(
            current = emptyMap(),
            sessionSets = sets,
            source = sets[0].copy(weightLbs = 1_900, reps = 9),
            beforeSourceEdit = sets[0],
            propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
        )

        assertEquals(1_800, adjustments.getValue(2).suggestedWeightLbs)
        assertEquals(7, adjustments.getValue(2).suggestedReps)
        assertEquals(1_700, adjustments.getValue(3).suggestedWeightLbs)
        assertEquals(5, adjustments.getValue(3).suggestedReps)
    }

    @Test
    fun adjusting_a_pending_second_set_cascades_from_what_the_user_sees() {
        val sets = listOf(
            set(1, 1, weightLbs = 1_850, reps = 10),
            set(2, 2, weightLbs = 1_750, reps = 8),
            set(3, 3, weightLbs = 1_650, reps = 6),
        )
        var adjustments = buildSmartAdjustmentsFromSource(
            current = emptyMap(),
            sessionSets = sets,
            source = sets[0].copy(weightLbs = 1_900),
            beforeSourceEdit = sets[0],
            propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
        )
        val secondPreview = smartAdjustmentPreview(sets[1], adjustments.getValue(2))
        val secondBefore = sets[1].copy(
            weightLbs = secondPreview.weightLbs,
            reps = secondPreview.reps,
            isBodyweight = secondPreview.isBodyweight,
        )
        val secondAfter = secondBefore.copy(weightLbs = 1_850)

        adjustments = buildSmartAdjustmentsFromSource(
            current = adjustments + (2L to adjustments.getValue(2).copy(suggestedWeightLbs = 1_850)),
            sessionSets = sets,
            source = secondAfter,
            beforeSourceEdit = secondBefore,
            propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
            keepSourceAdjustment = true,
        )

        assertEquals(1_850, adjustments.getValue(2).suggestedWeightLbs)
        assertEquals(1_750, adjustments.getValue(3).suggestedWeightLbs)
        assertEquals(2L, adjustments.getValue(3).sourceSetId)
    }

    @Test
    fun completed_and_earlier_sets_are_never_rewritten() {
        val sets = listOf(
            set(1, 1, weightLbs = 1_850, reps = 10),
            set(2, 2, weightLbs = 1_750, reps = 8),
            set(3, 3, weightLbs = 1_650, reps = 6),
            set(4, 4, weightLbs = 1_550, reps = 6, completed = true),
        )

        val adjustments = buildSmartAdjustmentsFromSource(
            current = emptyMap(),
            sessionSets = sets,
            source = sets[1].copy(weightLbs = 1_800),
            beforeSourceEdit = sets[1],
            propagationPolicy = SmartSetPropagationPolicy.SUGGEST_LATER_SETS,
        )

        assertFalse(1L in adjustments)
        assertFalse(2L in adjustments)
        assertEquals(1_700, adjustments.getValue(3).suggestedWeightLbs)
        assertFalse(4L in adjustments)
    }

    @Test
    fun weight_delta_uses_target_baseline_instead_of_copying_source() {
        assertEquals(
            1_800,
            calculateSmartSuggestedWeight(
                sourceNewWeightLbs = 1_900,
                sourceBaselineWeightLbs = 1_850,
                targetBaselineWeightLbs = 1_750,
            ),
        )
    }

    @Test
    fun manual_edit_isolation_drops_its_hint_and_descendants_without_rewriting_other_hints() {
        val sets = listOf(
            set(1, 1, weightLbs = 1_850, reps = 10),
            set(2, 2, weightLbs = 1_750, reps = 7),
            set(3, 3, weightLbs = 1_650, reps = 6),
            set(4, 4, weightLbs = 1_550, reps = 5),
        )
        val secondHint = SmartSetAdjustment(
            setId = 2,
            sourceSetId = 1,
            suggestedWeightLbs = 1_800,
            hasWeightSuggestion = true,
            suggestedIsBodyweight = false,
            suggestedReps = 8,
        )
        val independentThirdHint = SmartSetAdjustment(
            setId = 3,
            sourceSetId = 1,
            suggestedWeightLbs = 1_700,
            hasWeightSuggestion = true,
            suggestedIsBodyweight = false,
            suggestedReps = 7,
        )
        val descendantOfSecond = SmartSetAdjustment(
            setId = 4,
            sourceSetId = 2,
            suggestedWeightLbs = 1_600,
            hasWeightSuggestion = true,
            suggestedIsBodyweight = false,
            suggestedReps = 6,
        )

        val adjustments = buildSmartAdjustmentsFromSource(
            current = mapOf(
                2L to secondHint,
                3L to independentThirdHint,
                4L to descendantOfSecond,
            ),
            sessionSets = sets,
            source = sets[1].copy(reps = 10),
            beforeSourceEdit = sets[1],
            propagationPolicy = SmartSetPropagationPolicy.ISOLATE_MANUAL_EDIT,
            keepSourceAdjustment = true,
        )

        assertFalse(2L in adjustments, "the manually edited row no longer carries a pending hint")
        assertFalse(4L in adjustments, "a prior cascade sourced from that row is invalidated")
        assertSame(independentThirdHint, adjustments[3L], "unrelated suggestions stay separate and unchanged")
    }

    @Test
    fun suggestion_never_replaces_the_primary_editable_value() {
        assertEquals(7, smartSetPrimaryValue(persistedValue = 7, suggestedValue = 10))
        assertEquals(10, smartSetSuggestionValue(suggestedValue = 10))
    }

    @Test
    fun preview_is_not_a_workout_set_and_acceptance_is_explicit() {
        val stored = set(2, 2, weightLbs = 1_750, reps = 7, completed = true)
        val hint = SmartSetAdjustment(
            setId = 2,
            sourceSetId = 1,
            suggestedWeightLbs = 1_800,
            hasWeightSuggestion = true,
            suggestedIsBodyweight = false,
            suggestedReps = 10,
        )

        val preview = smartAdjustmentPreview(stored, hint)

        assertEquals(1_800, preview.weightLbs)
        assertEquals(10, preview.reps)
        assertEquals(stored.copy(weightLbs = 1_800, reps = 10, completed = false), acceptSmartAdjustment(stored, hint))
    }

    @Test
    fun mismatched_adjustment_id_cannot_apply_to_the_wrong_row() {
        val stored = set(2, 2, weightLbs = 1_750, reps = 7)
        val wrongRowHint = SmartSetAdjustment(
            setId = 3,
            sourceSetId = 1,
            suggestedWeightLbs = 2_250,
            hasWeightSuggestion = true,
            suggestedIsBodyweight = false,
            suggestedReps = 99,
        )

        assertEquals(
            SmartSetAdjustmentPreview(weightLbs = 1_750, reps = 7, isBodyweight = false),
            smartAdjustmentPreview(stored, wrongRowHint),
        )
        assertSame(stored, acceptSmartAdjustment(stored, wrongRowHint))
    }
}
