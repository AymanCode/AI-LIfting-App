package com.ayman.ecolift.data.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EquivalenceUnitTest {

    @Test
    fun count_divides_total_by_unit_weight_and_rounds_down() {
        val chicken = EquivalenceUnit("chicken", "chickens", 6.0)
        assertEquals(24_318, EquivalenceUnits.countFor(145_908.5, chicken))
    }

    @Test
    fun count_is_zero_when_total_is_zero_or_negative() {
        val chicken = EquivalenceUnit("chicken", "chickens", 6.0)
        assertEquals(0, EquivalenceUnits.countFor(0.0, chicken))
        assertEquals(0, EquivalenceUnits.countFor(-5.0, chicken))
    }

    @Test
    fun validate_rejects_weights_outside_the_allowed_range() {
        assertNull(EquivalenceUnits.validate(EquivalenceUnit("ant", "ants", 0.4)))
        assertNull(EquivalenceUnits.validate(EquivalenceUnit("whale", "whales", 5_001.0)))
    }

    @Test
    fun validate_rejects_blank_names_and_non_finite_weights() {
        assertNull(EquivalenceUnits.validate(EquivalenceUnit("", "things", 10.0)))
        assertNull(EquivalenceUnits.validate(EquivalenceUnit("thing", " ", 10.0)))
        assertNull(EquivalenceUnits.validate(EquivalenceUnit("thing", "things", Double.NaN)))
    }

    @Test
    fun validate_accepts_a_unit_at_the_range_boundaries() {
        val low = EquivalenceUnit("pigeon", "pigeons", 1.0)
        val high = EquivalenceUnit("rhino", "rhinos", 5_000.0)
        assertEquals(low, EquivalenceUnits.validate(low))
        assertEquals(high, EquivalenceUnits.validate(high))
    }

    @Test
    fun for_week_uses_the_model_choice_when_it_validates() {
        val walrus = EquivalenceUnit("walrus", "walruses", 2_700.0)
        assertEquals(walrus, EquivalenceUnits.forWeek(weekIndex = 3, modelChoice = walrus))
    }

    @Test
    fun for_week_falls_back_when_the_model_choice_is_invalid_or_absent() {
        val bad = EquivalenceUnit("dust mote", "dust motes", 0.001)
        assertTrue(EquivalenceUnits.forWeek(weekIndex = 3, modelChoice = bad) in EquivalenceUnits.FALLBACKS)
        assertTrue(EquivalenceUnits.forWeek(weekIndex = 3, modelChoice = null) in EquivalenceUnits.FALLBACKS)
    }

    @Test
    fun fallback_is_stable_within_a_week_and_rotates_between_weeks() {
        val week3 = EquivalenceUnits.forWeek(weekIndex = 3, modelChoice = null)
        assertEquals(week3, EquivalenceUnits.forWeek(weekIndex = 3, modelChoice = null))
        val week4 = EquivalenceUnits.forWeek(weekIndex = 4, modelChoice = null)
        assertTrue(week3 != week4)
    }

    @Test
    fun fallback_handles_negative_week_index_without_crashing() {
        assertTrue(EquivalenceUnits.forWeek(weekIndex = -7, modelChoice = null) in EquivalenceUnits.FALLBACKS)
    }

    @Test
    fun every_bundled_fallback_passes_its_own_validation() {
        EquivalenceUnits.FALLBACKS.forEach { assertEquals(it, EquivalenceUnits.validate(it)) }
    }

    @Test
    fun label_groups_thousands_so_the_sheet_reads_as_a_number() {
        val chicken = EquivalenceUnit("chicken", "chickens", 6.0)
        assertEquals("24,318 chickens", EquivalenceUnits.label(145_908.5, chicken))
    }

    @Test
    fun label_uses_the_singular_for_exactly_one() {
        val walrus = EquivalenceUnit("walrus", "walruses", 2_700.0)
        assertEquals("1 walrus", EquivalenceUnits.label(3_000.0, walrus))
    }

    @Test
    fun label_uses_the_plural_for_zero() {
        val walrus = EquivalenceUnit("walrus", "walruses", 2_700.0)
        assertEquals("0 walruses", EquivalenceUnits.label(0.0, walrus))
    }
}
