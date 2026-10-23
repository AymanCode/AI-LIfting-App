package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hub.BacklogItem
import com.ayman.ecolift.data.hub.UncheckedSetsItem
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.CurrentAppMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HubPolicyTest {

    @BeforeTest
    fun setUp() { CurrentAppMode.mode = AppMode.Authenticated }

    @AfterTest
    fun tearDown() { CurrentAppMode.mode = AppMode.Authenticated }

    private fun set(
        id: Long = 0,
        exerciseId: Long = 1,
        date: String = "2026-08-10",
        setNumber: Int = 1,
        weightLbs: Int? = null,
        reps: Int? = null,
        isBodyweight: Boolean = false,
        completed: Boolean = false,
        restTimeSeconds: Int? = null,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        isBodyweight = isBodyweight,
        completed = completed,
        restTimeSeconds = restTimeSeconds,
    )

    // =========================================================================
    // 1. individualReviewFrom
    // =========================================================================

    @Test
    fun individualReviewFrom_with_default_window_of_21_days() {
        val today = LocalDate(2026, 8, 22)
        val result = individualReviewFrom(today)
        assertEquals("2026-08-01", result)
    }

    @Test
    fun individualReviewFrom_crosses_month_boundary() {
        val augustTen = LocalDate(2026, 8, 10)
        assertEquals("2026-07-20", individualReviewFrom(augustTen, windowDays = 21))

        // Non-leap year February (28 days in 2026)
        val marchFive2026 = LocalDate(2026, 3, 5)
        assertEquals("2026-02-12", individualReviewFrom(marchFive2026, windowDays = 21))

        // Leap year February (29 days in 2024)
        val marchFive2024 = LocalDate(2024, 3, 5)
        assertEquals("2024-02-13", individualReviewFrom(marchFive2024, windowDays = 21))
    }

    @Test
    fun individualReviewFrom_crosses_year_boundary() {
        val janTen = LocalDate(2026, 1, 10)
        assertEquals("2025-12-20", individualReviewFrom(janTen, windowDays = 21))
    }

    @Test
    fun individualReviewFrom_with_custom_window() {
        val date = LocalDate(2026, 8, 25)
        assertEquals("2026-08-15", individualReviewFrom(date, windowDays = 10))
        assertEquals("2026-08-25", individualReviewFrom(date, windowDays = 0))

        val monthCross = LocalDate(2026, 8, 5)
        assertEquals("2026-07-29", individualReviewFrom(monthCross, windowDays = 7))
    }

    // =========================================================================
    // 2. hubTopSetLabel
    // =========================================================================

    @Test
    fun topSetLabel_returns_null_for_empty_input() {
        assertNull(hubTopSetLabel(emptyList()))
    }

    @Test
    fun topSetLabel_returns_null_for_all_unchecked_input() {
        val uncheckedSets = listOf(
            set(id = 1, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(225), reps = 5, completed = false),
            set(id = 2, setNumber = 2, weightLbs = WeightLbs.fromWholePounds(245), reps = 3, completed = false),
        )
        assertNull(hubTopSetLabel(uncheckedSets))
    }

    @Test
    fun topSetLabel_formats_single_completed_set_with_times_sign() {
        val sets = listOf(
            set(id = 1, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(225), reps = 5, completed = true),
        )
        val result = hubTopSetLabel(sets)
        assertEquals("225 × 5", result)
    }

    @Test
    fun topSetLabel_picks_heaviest_set_among_completed() {
        val sets = listOf(
            set(id = 1, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(185), reps = 10, completed = true),
            set(id = 2, setNumber = 2, weightLbs = WeightLbs.fromWholePounds(225), reps = 5, completed = true),
            set(id = 3, setNumber = 3, weightLbs = WeightLbs.fromWholePounds(205), reps = 8, completed = true),
        )
        assertEquals("225 × 5", hubTopSetLabel(sets))
    }

    @Test
    fun topSetLabel_ignores_unchecked_sets_even_if_heavier() {
        val sets = listOf(
            set(id = 1, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(225), reps = 5, completed = true),
            set(id = 2, setNumber = 2, weightLbs = WeightLbs.fromWholePounds(315), reps = 1, completed = false),
            set(id = 3, setNumber = 3, weightLbs = WeightLbs.fromWholePounds(405), reps = 10, completed = false),
        )
        assertEquals("225 × 5", hubTopSetLabel(sets))
    }

    @Test
    fun topSetLabel_tiebreaks_equal_weight_by_higher_reps() {
        val sets = listOf(
            set(id = 1, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(225), reps = 3, completed = true),
            set(id = 2, setNumber = 2, weightLbs = WeightLbs.fromWholePounds(225), reps = 8, completed = true),
            set(id = 3, setNumber = 3, weightLbs = WeightLbs.fromWholePounds(225), reps = 5, completed = true),
        )
        assertEquals("225 × 8", hubTopSetLabel(sets))
    }

    @Test
    fun topSetLabel_handles_null_weight_or_reps() {
        val bodyweightSet = listOf(
            set(id = 1, setNumber = 1, weightLbs = null, reps = 10, isBodyweight = true, completed = true),
        )
        assertEquals("0 × 10", hubTopSetLabel(bodyweightSet))

        val nullRepsSet = listOf(
            set(id = 2, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(150), reps = null, completed = true),
        )
        assertEquals("150 × 0", hubTopSetLabel(nullRepsSet))
    }

    @Test
    fun topSetLabel_formatting_is_strict_times_sign_with_spaces() {
        val sets = listOf(
            set(id = 1, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(225), reps = 5, completed = true),
        )
        val label = hubTopSetLabel(sets)
        assertTrue(label != null)
        assertEquals("225 × 5", label)
        assertTrue(label.contains(" × "), "Label must contain ' × '")
        assertTrue(label.contains("×"), "Label must contain multiplication sign '×'")
        assertFalse(label.contains("X"), "Label must not contain uppercase 'X'")
        assertFalse(label.contains("—"), "Label must not contain em-dash '—'")
    }

    @Test
    fun topSetLabel_renders_stored_tenths_as_pounds() {
        // weightLbs is stored in tenths of a pound. Printing the field raw showed a 225 lb top set
        // as "2250", which is what shipped in the first hub build.
        val wholePounds = listOf(
            set(id = 1, setNumber = 1, weightLbs = WeightLbs.fromWholePounds(225), reps = 5, completed = true),
        )
        assertEquals("225 × 5", hubTopSetLabel(wholePounds))

        val halfPound = listOf(
            set(id = 2, setNumber = 1, weightLbs = 2255, reps = 5, completed = true),
        )
        assertEquals("225.5 × 5", hubTopSetLabel(halfPound))
    }

    @Test
    fun individualRemovalTarget_selects_only_the_requested_unchecked_set() {
        val completed = set(id = 1, setNumber = 1, completed = true)
        val firstOpen = set(id = 2, setNumber = 2, completed = false)
        val secondOpen = set(id = 3, setNumber = 3, completed = false)
        val allSets = listOf(completed, firstOpen, secondOpen)

        assertEquals(firstOpen, hubIndividualRemovalTarget(allSets, firstOpen.id))
        assertEquals(secondOpen, hubIndividualRemovalTarget(allSets, secondOpen.id))
        assertNull(hubIndividualRemovalTarget(allSets, completed.id))
        assertNull(hubIndividualRemovalTarget(allSets, 99L))
        assertNull(hubIndividualRemovalTarget(allSets, 0L))
    }

    @Test
    fun clearOpenSets_preserves_a_set_checked_after_the_alert_was_captured() = runVmTest {
        val captured = set(id = 1, weightLbs = 1_000, reps = 8).copy(syncId = "hub-1")
        val stillOpen = set(id = 2, setNumber = 2).copy(syncId = "hub-2")
        val data = ViewModelMutationTestRepos(listOf(captured, stillOpen))
        val item = uncheckedItem(listOf(captured, stillOpen))
        val checked = captured.copy(completed = true, rowRevision = 2L)
        data.currentSets.value = listOf(checked, stillOpen)

        HubViewModel(data.repos, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate).clearOpenSets(item)
        withTimeout(2_000L) { data.deletedIds.first { stillOpen.id in it } }

        assertEquals(listOf(checked), data.currentSets.value)
        assertEquals(listOf(stillOpen.id), data.deletedIds.value)
    }

    @Test
    fun confirmItem_keeps_current_values_and_only_marks_the_current_row_completed() = runVmTest {
        val captured = set(id = -1, weightLbs = 1_000, reps = 8).copy(syncId = "hub-1")
        val data = ViewModelMutationTestRepos(listOf(captured))
        val item = uncheckedItem(listOf(captured))
        // A refresh can change both values and the optimistic ID before Confirm is tapped.
        val current = captured.copy(id = 41L, weightLbs = 1_250, reps = 10, rowRevision = 3L)
        data.currentSets.value = listOf(current)

        HubViewModel(data.repos, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate).confirmItem(item)
        withTimeout(2_000L) { data.currentSets.first { rows -> rows.single().completed } }

        assertEquals(listOf(current.copy(completed = true)), data.currentSets.value)
        assertEquals(listOf(current.copy(completed = true)), data.updates)
    }

    @Test
    fun clearBacklog_preserves_a_set_checked_since_the_snapshot() = runVmTest {
        val captured = set(id = 1).copy(syncId = "backlog-1")
        val stillOpen = set(id = 2, setNumber = 2).copy(syncId = "backlog-2")
        val snapshot = listOf(captured, stillOpen)
        val data = ViewModelMutationTestRepos(snapshot)
        val checked = captured.copy(completed = true)
        data.currentSets.value = listOf(checked, stillOpen)
        val backlog = BacklogItem("backlog", captured.date, captured.date, 2, 1)

        HubViewModel(data.repos, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate).clearBacklog(backlog, snapshot)
        withTimeout(2_000L) { data.deletedIds.first { stillOpen.id in it } }

        assertEquals(listOf(checked), data.currentSets.value)
        assertEquals(listOf(stillOpen.id), data.deletedIds.value)
    }

    @Test
    fun individualRemovalTarget_uses_stable_identity_and_skips_missing_or_checked_rows() {
        val current = set(id = 41).copy(syncId = "hub-1")
        val reusedId = set(id = -1).copy(syncId = "different-set")

        assertEquals(current, hubIndividualRemovalTarget(listOf(current, reusedId), -1L, "hub-1"))
        assertNull(hubIndividualRemovalTarget(listOf(reusedId), -1L, "hub-1"))
        assertNull(hubIndividualRemovalTarget(listOf(current.copy(completed = true)), -1L, "hub-1"))
    }

    private fun uncheckedItem(openSets: List<WorkoutSet>) = UncheckedSetsItem(
        id = "unchecked:test",
        date = openSets.first().date,
        exerciseId = openSets.first().exerciseId,
        exerciseName = "Bench Press",
        muscleGroups = "CHEST",
        isBodyweight = false,
        openSets = openSets,
        referenceSets = emptyList(),
    )
}
