package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutCardOrderTest {
    @Test
    fun active_cards_follow_workout_order_and_legacy_rows_follow() {
        val date = "2026-08-18"
        val cardExerciseIds = (1L..6L).toList()
        val cards = cardExerciseIds.mapIndexed { position, exerciseId ->
            WorkoutExerciseCard(
                id = "card-$exerciseId",
                date = date,
                exerciseId = exerciseId,
                position = position,
                displayMode = WorkoutCardDisplayMode.EXPANDED,
                origin = WorkoutCardOrigin.MANUAL,
            )
        }.reversed()
        val sets = cardExerciseIds.map { exerciseId ->
            WorkoutSet(exerciseId = exerciseId, date = date, setNumber = 1, reps = 8)
        } + WorkoutSet(exerciseId = 99, date = date, setNumber = 1, reps = 5)

        val ordered = orderLogExerciseGroups(sets, cards, date)

        assertEquals(cardExerciseIds + 99L, ordered.map { it.exerciseId })
        assertEquals<List<Int>>(
            listOf(0, 1, 2, 3, 4, 5),
            ordered.take(6).map { requireNotNull(it.card).position },
        )
        assertEquals(null, ordered.last().card)
    }

    @Test
    fun compact_cards_move_below_active_cards_without_changing_saved_positions() {
        val date = "2026-08-18"
        val cards = listOf(
            card(date, exerciseId = 1, position = 0, displayMode = WorkoutCardDisplayMode.EXPANDED),
            card(date, exerciseId = 2, position = 1, displayMode = WorkoutCardDisplayMode.COMPACT),
            card(date, exerciseId = 3, position = 2, displayMode = WorkoutCardDisplayMode.EXPANDED),
            card(date, exerciseId = 4, position = 3, displayMode = WorkoutCardDisplayMode.COMPACT),
        )
        val sets = cards.map { card ->
            WorkoutSet(exerciseId = card.exerciseId, date = date, setNumber = 1, reps = 8)
        } + WorkoutSet(exerciseId = 99, date = date, setNumber = 1, reps = 8)

        val ordered = orderLogExerciseGroups(sets, cards, date)

        assertEquals(listOf(1L, 3L, 99L, 2L, 4L), ordered.map { it.exerciseId })
        assertEquals(
            listOf(
                WorkoutCardDisplayMode.EXPANDED,
                WorkoutCardDisplayMode.EXPANDED,
                null,
                WorkoutCardDisplayMode.COMPACT,
                WorkoutCardDisplayMode.COMPACT,
            ),
            ordered.map { it.card?.displayMode },
        )
    }

    private fun card(
        date: String,
        exerciseId: Long,
        position: Int,
        displayMode: WorkoutCardDisplayMode,
    ) = WorkoutExerciseCard(
        id = "card-$exerciseId",
        date = date,
        exerciseId = exerciseId,
        position = position,
        displayMode = displayMode,
        origin = WorkoutCardOrigin.MANUAL,
    )
}
