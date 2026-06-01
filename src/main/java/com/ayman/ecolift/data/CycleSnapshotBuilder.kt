package com.ayman.ecolift.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

object CycleSnapshotBuilder {
    const val UNASSIGNED_SLOT_ID = -1L

    data class BucketKey(val slotId: Long, val kind: SplitBucketKind)

    fun build(
        startDate: String,
        endDate: String,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,
        workoutDays: List<WorkoutDay>,
        sets: List<WorkoutSet>,
        exerciseNames: Map<Long, ExerciseMeta>,
    ): CycleSnapshot {
        val inWindow = sets
            .asSequence()
            .filter { it.completed && it.date in startDate..endDate }
            .sortedWith(compareBy<WorkoutSet> { it.date }.thenBy { it.setNumber }.thenBy { it.id })
            .toList()

        val daysByDate = workoutDays.associateBy { it.date }
        val slotIds = slots.map { it.id }.toSet()
        val orderedSlots = slots.sortedWith(compareBy<CycleSlot> { it.orderIndex }.thenBy { it.id })

        fun bucketFor(set: WorkoutSet): BucketKey {
            val day = daysByDate[set.date]
            val resolved = day?.cycleSlotId
                ?: day?.cycleSlotType?.let { orderedSlots.getOrNull(it)?.id }
            return when {
                resolved == null -> BucketKey(UNASSIGNED_SLOT_ID, SplitBucketKind.Unassigned)
                resolved in slotIds -> BucketKey(resolved, SplitBucketKind.Real)
                else -> BucketKey(resolved, SplitBucketKind.Deleted)
            }
        }

        val setsByBucket = inWindow.groupBy(::bucketFor)
        val savedOrder = splitExercises
            .groupBy { it.splitId }
            .mapValues { (_, rows) -> rows.associate { it.exerciseId to it.orderIndex } }

        val splits = mutableListOf<SplitSnapshot>()
        orderedSlots.forEachIndexed { index, slot ->
            splits += buildSplit(
                slotId = slot.id,
                kind = SplitBucketKind.Real,
                name = slot.name,
                orderIndex = index,
                bucketSets = setsByBucket[BucketKey(slot.id, SplitBucketKind.Real)].orEmpty(),
                savedOrder = savedOrder[slot.id].orEmpty(),
                exerciseNames = exerciseNames,
            )
        }

        val deletedKeys = setsByBucket.keys
            .filter { it.kind == SplitBucketKind.Deleted }
            .sortedBy { it.slotId }
        deletedKeys.forEachIndexed { index, key ->
            splits += buildSplit(
                slotId = key.slotId,
                kind = SplitBucketKind.Deleted,
                name = "Deleted split",
                orderIndex = orderedSlots.size + index,
                bucketSets = setsByBucket.getValue(key),
                savedOrder = emptyMap(),
                exerciseNames = exerciseNames,
            )
        }

        val unassigned = setsByBucket[BucketKey(UNASSIGNED_SLOT_ID, SplitBucketKind.Unassigned)].orEmpty()
        if (unassigned.isNotEmpty()) {
            splits += buildSplit(
                slotId = UNASSIGNED_SLOT_ID,
                kind = SplitBucketKind.Unassigned,
                name = "Unassigned",
                orderIndex = orderedSlots.size + deletedKeys.size,
                bucketSets = unassigned,
                savedOrder = emptyMap(),
                exerciseNames = exerciseNames,
            )
        }

        val totalVolume = splits.sumOf { split ->
            split.exercises.sumOf { exercise -> exercise.sessions.sumOf { it.volumeLbs } }
        }
        return CycleSnapshot(
            startDate = startDate,
            endDate = endDate,
            totals = CycleTotals(
                sessions = inWindow.map { it.date }.distinct().size,
                totalVolumeLbs = totalVolume,
                totalSets = inWindow.size,
                spanDays = spanDays(startDate, endDate),
            ),
            splits = splits,
        )
    }

    fun splitCount(snapshot: CycleSnapshot): Int =
        snapshot.splits.count { it.bucketKind == SplitBucketKind.Real && it.usageCount > 0 }

    private fun buildSplit(
        slotId: Long,
        kind: SplitBucketKind,
        name: String,
        orderIndex: Int,
        bucketSets: List<WorkoutSet>,
        savedOrder: Map<Long, Int>,
        exerciseNames: Map<Long, ExerciseMeta>,
    ): SplitSnapshot {
        val days = bucketSets.map { it.date }.distinct().sorted()
        val byExercise = bucketSets.groupBy { it.exerciseId }
        val firstSeen = bucketSets.map { it.exerciseId }.distinct()
        val orderedIds = firstSeen.sortedWith(
            compareBy<Long> { savedOrder[it] ?: Int.MAX_VALUE }
                .thenBy { firstSeen.indexOf(it) }
        )
        return SplitSnapshot(
            slotId = slotId,
            bucketKind = kind,
            name = name,
            orderIndex = orderIndex,
            firstUsedDate = days.firstOrNull(),
            lastUsedDate = days.lastOrNull(),
            usageCount = days.size,
            exercises = orderedIds.map { id ->
                buildExercise(id, byExercise.getValue(id), exerciseNames[id])
            },
        )
    }

    private fun buildExercise(
        exerciseId: Long,
        sets: List<WorkoutSet>,
        meta: ExerciseMeta?,
    ): ExerciseSnapshot {
        val sessions = sets.groupBy { it.date }
            .toSortedMap()
            .map { (date, daySets) -> sessionPoint(date, daySets) }
        val start = sessions.firstOrNull()
        val end = sessions.lastOrNull()
        return ExerciseSnapshot(
            exerciseId = exerciseId,
            name = meta?.name ?: "Exercise #$exerciseId",
            isBodyweight = meta?.isBodyweight ?: false,
            sessions = sessions,
            startE1rm = start?.bestE1rm,
            endE1rm = end?.bestE1rm,
            startTopWeight = start?.topWeight,
            endTopWeight = end?.topWeight,
            startVolumeLbs = start?.volumeLbs,
            endVolumeLbs = end?.volumeLbs,
        )
    }

    private fun sessionPoint(date: String, daySets: List<WorkoutSet>): SessionPoint {
        var topWeight: Float? = null
        var bestE1rm: Float? = null
        var volume = 0.0
        var totalReps = 0

        daySets.forEach { set ->
            val reps = set.reps ?: 0
            totalReps += reps
            if (!set.isBodyweight && set.weightLbs != null) {
                val weight = WeightLbs.toLbs(set.weightLbs)
                val weightFloat = weight.toFloat()
                topWeight = maxOf(topWeight ?: weightFloat, weightFloat)
                bestE1rm = maxOf(bestE1rm ?: 0f, (weight * (1.0 + reps / 30.0)).toFloat())
                volume += weight * reps
            }
        }

        return SessionPoint(
            date = date,
            topWeight = topWeight,
            bestE1rm = bestE1rm,
            volumeLbs = volume.roundToLong(),
            totalReps = totalReps,
            setCount = daySets.size,
        )
    }

    private fun spanDays(startDate: String, endDate: String): Int =
        runCatching {
            (ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1)
                .toInt()
                .coerceAtLeast(0)
        }.getOrDefault(0)
}
