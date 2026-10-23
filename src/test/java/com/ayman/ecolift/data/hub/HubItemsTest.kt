package com.ayman.ecolift.data.hub

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HubItemsTest {

    private fun set(
        id: Long = 0,
        exerciseId: Long,
        date: String,
        setNumber: Int,
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

    private fun exercise(
        id: Long,
        name: String,
        muscleGroups: String = "CHEST · TRICEPS",
        isBodyweight: Boolean = false,
        mergedIntoId: Long? = null,
    ) = Exercise(
        id = id,
        name = name,
        muscleGroups = muscleGroups,
        isBodyweight = isBodyweight,
        mergedIntoId = mergedIntoId,
    )

    @Test
    fun today_unchecked_sets_produce_no_items() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val exercises = mapOf(1L to exercise(1L, "Bench Press"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = today, setNumber = 1, completed = false),
            set(id = 2, exerciseId = 1L, date = today, setNumber = 2, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertTrue(build.days.isEmpty())
        assertNull(build.backlog)
        assertEquals(0, build.count)
    }

    @Test
    fun date_with_unchecked_and_zero_checked_is_NeverVerified() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-10"
        val exercises = mapOf(1L to exercise(1L, "Squat", muscleGroups = "LEGS"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = date, setNumber = 1, completed = false),
            set(id = 2, exerciseId = 1L, date = date, setNumber = 2, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertEquals(1, build.days.size)
        val day = build.days.single()
        assertEquals(date, day.date)
        assertEquals(HubDayKind.NeverVerified, day.kind)
        assertEquals(1, day.items.size)

        val item = day.items.single() as UncheckedSetsItem
        assertEquals("unchecked:$date:1", item.id)
        assertEquals("Squat", item.exerciseName)
        assertEquals("LEGS", item.muscleGroups)
        assertFalse(item.isBodyweight)
        assertEquals(listOf(1, 2), item.openSets.map { it.setNumber })
        assertTrue(item.referenceSets.isEmpty())
    }

    @Test
    fun date_with_mix_produces_Trained_and_referenceSets_holds_checked_in_setNumber_order() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-12"
        val exercises = mapOf(1L to exercise(1L, "Bench Press", muscleGroups = "CHEST"))
        val sets = listOf(
            set(id = 3, exerciseId = 1L, date = date, setNumber = 3, weightLbs = 185, reps = 6, completed = false),
            set(id = 1, exerciseId = 1L, date = date, setNumber = 1, weightLbs = 185, reps = 8, completed = true),
            set(id = 2, exerciseId = 1L, date = date, setNumber = 2, weightLbs = 185, reps = 7, completed = true),
        )
        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertEquals(1, build.days.size)
        val day = build.days.single()
        assertEquals(HubDayKind.Trained, day.kind)
        assertEquals(1, day.items.size)

        val item = day.items.single() as UncheckedSetsItem
        assertEquals("unchecked:$date:1", item.id)
        assertEquals(listOf(3), item.openSets.map { it.setNumber })
        assertEquals(listOf(1, 2), item.referenceSets.map { it.setNumber })
        assertTrue(item.referenceSets.all { it.completed })
    }

    @Test
    fun two_exercises_unchecked_on_same_date_produce_two_items_ordered_by_first_setNumber_then_name() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-15"
        val exercises = mapOf(
            1L to exercise(1L, "Pull-Up", isBodyweight = true),
            2L to exercise(2L, "Deadlift", isBodyweight = false),
            3L to exercise(3L, "Barbell Row", isBodyweight = false),
        )
        val sets = listOf(
            // Pull-Up first open set is 2
            set(id = 1, exerciseId = 1L, date = date, setNumber = 1, completed = true),
            set(id = 2, exerciseId = 1L, date = date, setNumber = 2, completed = false),
            // Deadlift first open set is 1
            set(id = 3, exerciseId = 2L, date = date, setNumber = 1, completed = false),
            // Barbell Row first open set is 1
            set(id = 4, exerciseId = 3L, date = date, setNumber = 1, completed = false),
        )
        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertEquals(1, build.days.size)
        val day = build.days.single()
        assertEquals(3, day.items.size)

        val items = day.items.filterIsInstance<UncheckedSetsItem>()
        assertEquals(3, items.size)

        // Set 1 items ("Barbell Row", "Deadlift") tie on first setNumber = 1, ordered alphabetically by name
        assertEquals("Barbell Row", items[0].exerciseName)
        assertEquals("Deadlift", items[1].exerciseName)
        // Set 2 item ("Pull-Up") comes after set 1 items
        assertEquals("Pull-Up", items[2].exerciseName)
    }

    @Test
    fun old_unchecked_sets_excluded_from_days_and_folded_into_backlog_with_correct_metrics() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val exercises = mapOf(1L to exercise(1L, "Bench Press"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = "2026-05-10", setNumber = 1, completed = false),
            set(id = 2, exerciseId = 1L, date = "2026-05-10", setNumber = 2, completed = false),
            set(id = 3, exerciseId = 1L, date = "2026-06-20", setNumber = 1, completed = false),
            set(id = 4, exerciseId = 1L, date = "2026-07-31", setNumber = 1, completed = false),
            // Recent unchecked set
            set(id = 5, exerciseId = 1L, date = "2026-08-05", setNumber = 1, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertEquals(1, build.days.size)
        assertEquals("2026-08-05", build.days.single().date)

        val backlog = build.backlog
        assertNotNull(backlog)
        assertEquals("backlog", backlog.id)
        assertEquals("2026-05-10", backlog.fromDate)
        assertEquals("2026-07-31", backlog.toDate)
        assertEquals(4, backlog.setCount)
        assertEquals(3, backlog.dayCount)
    }

    @Test
    fun no_old_sets_means_backlog_is_null() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val exercises = mapOf(1L to exercise(1L, "Squat"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = "2026-08-05", setNumber = 1, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertNull(build.backlog)
        assertEquals(1, build.count)
    }

    @Test
    fun missing_exercise_and_merged_exercise_produce_no_item() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-10"
        val exercises = mapOf(
            1L to exercise(1L, "Valid Exercise"),
            2L to exercise(2L, "Merged Exercise", mergedIntoId = 1L),
            // Exercise 3L is missing entirely from exercises map
        )
        val sets = listOf(
            set(id = 1, exerciseId = 2L, date = date, setNumber = 1, completed = false),
            set(id = 2, exerciseId = 3L, date = date, setNumber = 1, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertTrue(build.days.isEmpty())
        assertEquals(0, build.count)
    }

    @Test
    fun checked_date_without_a_split_does_not_produce_a_notification() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-10"
        val exercises = mapOf(1L to exercise(1L, "Bench Press"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = date, setNumber = 1, completed = true),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertTrue(build.days.isEmpty())
        assertEquals(0, build.count)
    }

    @Test
    fun mixed_checked_and_unchecked_date_only_produces_the_unchecked_exercise_notification() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-10"
        val exercises = mapOf(1L to exercise(1L, "Bench Press"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = date, setNumber = 1, completed = true),
            set(id = 2, exerciseId = 1L, date = date, setNumber = 2, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertEquals(1, build.days.size)
        val day = build.days.single()
        assertEquals(1, day.items.size)
        val item = day.items.single() as UncheckedSetsItem
        assertEquals("unchecked:$date:1", item.id)
    }

    @Test
    fun count_equals_all_items_across_days_plus_one_if_backlog_present() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val exercises = mapOf(
            1L to exercise(1L, "Squat"),
            2L to exercise(2L, "Bench Press"),
        )
        val sets = listOf(
            // 2 old sets -> 1 backlog item
            set(id = 1, exerciseId = 1L, date = "2026-07-01", setNumber = 1, completed = false),
            set(id = 2, exerciseId = 1L, date = "2026-07-02", setNumber = 1, completed = false),
            // Day 1: 2 unchecked exercise items. Its completed set does not become a split alert.
            set(id = 3, exerciseId = 1L, date = "2026-08-10", setNumber = 1, completed = false),
            set(id = 4, exerciseId = 2L, date = "2026-08-10", setNumber = 1, completed = false),
            set(id = 5, exerciseId = 1L, date = "2026-08-10", setNumber = 2, completed = true),
            // Day 2: 1 unchecked item = 1 item
            set(id = 6, exerciseId = 1L, date = "2026-08-12", setNumber = 1, completed = false),
        )
        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertNotNull(build.backlog)
        // Day 2026-08-12: 1 item
        // Day 2026-08-10: 2 unchecked items
        // Total day items = 3
        // Backlog = 1
        // Total count = 4
        assertEquals(2, build.days.size)
        assertEquals(3, build.days.sumOf { it.items.size })
        assertEquals(4, build.count)
    }

    @Test
    fun days_are_sorted_newest_first() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val exercises = mapOf(1L to exercise(1L, "Squat"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = "2026-08-03", setNumber = 1, completed = false),
            set(id = 2, exerciseId = 1L, date = "2026-08-15", setNumber = 1, completed = false),
            set(id = 3, exerciseId = 1L, date = "2026-08-08", setNumber = 1, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        val dates = build.days.map { it.date }
        assertEquals(listOf("2026-08-15", "2026-08-08", "2026-08-03"), dates)
    }

    @Test
    fun completed_sets_do_not_add_a_second_item_to_an_unchecked_day() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-10"
        val exercises = mapOf(1L to exercise(1L, "Squat"))
        val sets = listOf(
            set(id = 1, exerciseId = 1L, date = date, setNumber = 1, completed = true),
            set(id = 2, exerciseId = 1L, date = date, setNumber = 2, completed = false),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        val day = build.days.single()
        assertEquals(1, day.items.size)
        assertTrue(day.items.single() is UncheckedSetsItem)
    }

    @Test
    fun old_checked_dates_do_not_produce_notifications() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val exercises = mapOf(1L to exercise(1L, "Squat"))
        val sets = listOf(
            // Checked set before individualReviewFrom
            set(id = 1, exerciseId = 1L, date = "2026-07-20", setNumber = 1, completed = true),
        )

        val build = HubItems.build(
            sets = sets,
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertTrue(build.days.isEmpty())
        assertNull(build.backlog)
        assertEquals(0, build.count)
    }

    @Test
    fun withoutItems_removes_a_swiped_notification_and_its_empty_day_immediately() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-10"
        val exercises = mapOf(
            1L to exercise(1L, "Bench Press"),
            2L to exercise(2L, "Barbell Row"),
        )
        val build = HubItems.build(
            sets = listOf(
                set(id = 1, exerciseId = 1L, date = date, setNumber = 1, completed = false),
                set(id = 2, exerciseId = 2L, date = "2026-08-09", setNumber = 1, completed = false),
            ),
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        val dismissed = build.withoutItems(setOf("unchecked:$date:1"))

        assertEquals(listOf("2026-08-09"), dismissed.days.map { it.date })
        assertEquals(1, dismissed.count)
        assertFalse("unchecked:$date:1" in dismissed.itemIds())
    }

    @Test
    fun removing_one_open_set_keeps_the_alert_until_the_last_open_set_is_removed() {
        val today = "2026-08-17"
        val reviewFrom = "2026-08-01"
        val date = "2026-08-10"
        val exercises = mapOf(1L to exercise(1L, "Bench Press"))
        val checked = set(id = 1, exerciseId = 1L, date = date, setNumber = 1, completed = true)
        val firstOpen = set(id = 2, exerciseId = 1L, date = date, setNumber = 2, completed = false)
        val secondOpen = set(id = 3, exerciseId = 1L, date = date, setNumber = 3, completed = false)

        val before = HubItems.build(
            sets = listOf(checked, firstOpen, secondOpen),
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )
        val afterOneRemoval = HubItems.build(
            sets = listOf(checked, secondOpen),
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )
        val afterLastRemoval = HubItems.build(
            sets = listOf(checked),
            exercisesById = exercises,
            today = today,
            individualReviewFrom = reviewFrom,
        )

        assertEquals(listOf(2, 3), (before.days.single().items.single() as UncheckedSetsItem).openSets.map { it.setNumber })
        val remainingItem = afterOneRemoval.days.single().items.single() as UncheckedSetsItem
        assertEquals(listOf(3), remainingItem.openSets.map { it.setNumber })
        assertEquals(listOf(1), remainingItem.referenceSets.map { it.setNumber })
        assertEquals(1, afterOneRemoval.count)
        assertTrue(afterLastRemoval.days.isEmpty())
        assertEquals(0, afterLastRemoval.count)
    }
}
