package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate

/** Small builders for engine tests: one checked working set per lift per day unless told otherwise. */
internal object LearnedFixtures {
    private var nextSetId = 1L

    fun set(
        date: String,
        exerciseId: Long,
        setNumber: Int = 1,
        weightLbs: Int? = 1000,
        reps: Int? = 8,
        completed: Boolean = true,
        warmup: Boolean = false,
        bodyweight: Boolean = false,
    ) = WorkoutSet(
        id = nextSetId++,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        isBodyweight = bodyweight,
        completed = completed,
        isWarmup = warmup,
    )

    /** One checked working set for each lift on [date]. */
    fun day(date: String, vararg lifts: Long): List<WorkoutSet> = lifts.map { set(date, it) }

    /** Cards for [date] in the given order (position 0 first). */
    fun cards(date: String, vararg lifts: Long): List<WorkoutExerciseCard> = lifts.mapIndexed { index, lift ->
        WorkoutExerciseCard(
            id = "$date-$lift",
            date = date,
            exerciseId = lift,
            position = index,
            displayMode = WorkoutCardDisplayMode.EXPANDED,
            origin = WorkoutCardOrigin.MANUAL,
        )
    }

    fun slot(
        id: Long,
        name: String = "",
        learned: Boolean = false,
        createdAt: Long = 0,
        orderIndex: Int = id.toInt(),
        colorIndex: Int = id.toInt(),
        archivedAt: Long? = null,
        suppressedAt: Long? = null,
        anchorUpdatedAt: Long? = null,
    ) = CycleSlot(
        id = id,
        name = name,
        orderIndex = orderIndex,
        archivedAt = archivedAt,
        learned = learned,
        createdAt = createdAt,
        anchorUpdatedAt = anchorUpdatedAt,
        suppressedAt = suppressedAt,
        colorIndex = colorIndex,
    )

    fun members(slotId: Long, vararg lifts: Long): List<SplitExercise> = lifts.mapIndexed { index, lift ->
        SplitExercise(id = slotId * 1000 + index, splitId = slotId, exerciseId = lift, orderIndex = index)
    }

    /** Epoch millis at noon UTC on [iso], for created_at and anchor_updated_at. */
    fun millis(iso: String): Long = LocalDate.parse(iso).toEpochDays().toLong() * 86_400_000L + 43_200_000L

    fun inputs(
        asOf: String,
        sets: List<WorkoutSet>,
        cards: List<WorkoutExerciseCard> = emptyList(),
        resolve: (Long) -> Long = { it },
        slots: List<CycleSlot> = emptyList(),
        slotMembers: List<SplitExercise> = emptyList(),
        workoutDays: List<WorkoutDay> = emptyList(),
        archivedCycleEndDates: List<String> = emptyList(),
        cycleStartDate: String? = null,
    ) = LearnedInputs(
        asOf = LocalDate.parse(asOf),
        sets = sets,
        cards = cards,
        resolve = resolve,
        slots = slots,
        slotMembers = slotMembers,
        workoutDays = workoutDays,
        archivedCycleEndDates = archivedCycleEndDates,
        cycleStartDate = cycleStartDate,
    )

    fun history(sets: List<WorkoutSet>, cards: List<WorkoutExerciseCard> = emptyList(), resolve: (Long) -> Long = { it }) =
        buildLiftHistory(sets, cards, resolve)

    fun asOf(iso: String): Int = epochDay(iso)
}
