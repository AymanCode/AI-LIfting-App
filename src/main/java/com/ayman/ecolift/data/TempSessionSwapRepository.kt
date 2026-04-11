package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow

class TempSessionSwapRepository(private val db: AppDatabase) {
    fun observeActiveForWeek(date: String): Flow<List<TempSessionSwap>> {
        return db.tempSessionSwapDao().observeActiveForWeek(WorkoutDates.startOfWeek(date))
    }

    suspend fun getActiveForWeek(date: String): List<TempSessionSwap> {
        return db.tempSessionSwapDao().getActiveForWeek(WorkoutDates.startOfWeek(date))
    }

    suspend fun createSwap(
        date: String,
        sourceSlotType: Int,
        sourceExerciseId: Long,
        targetSlotType: Int,
        targetExerciseId: Long,
    ): TempSessionSwap {
        val swap = TempSessionSwap(
            weekStartDate = WorkoutDates.startOfWeek(date),
            sourceSlotType = sourceSlotType,
            sourceExerciseId = sourceExerciseId,
            targetSlotType = targetSlotType,
            targetExerciseId = targetExerciseId,
        )
        val id = db.tempSessionSwapDao().insert(swap)
        return swap.copy(id = id)
    }

    suspend fun applySwapsToDate(date: String, slotType: Int) {
        val swaps = getActiveForWeek(date).filter {
            it.sourceSlotType == slotType || it.targetSlotType == slotType
        }
        if (swaps.isEmpty()) return
        swaps.forEach { swap ->
            val fromExerciseId = if (swap.sourceSlotType == slotType) {
                swap.sourceExerciseId
            } else {
                swap.targetExerciseId
            }
            val toExerciseId = if (swap.sourceSlotType == slotType) {
                swap.targetExerciseId
            } else {
                swap.sourceExerciseId
            }
            db.workoutSetDao().getForDateAndExercise(date, fromExerciseId)
                .forEach { set ->
                    db.workoutSetDao().update(set.copy(exerciseId = toExerciseId))
                }
        }
    }

    suspend fun getSwapNoticesForDate(date: String, slotType: Int): List<SwapNotice> {
        val exerciseDao = db.exerciseDao()
        return getActiveForWeek(date)
            .filter { it.sourceSlotType == slotType || it.targetSlotType == slotType }
            .mapNotNull { swap ->
                val isTargetDay = swap.targetSlotType == slotType
                val incoming = if (isTargetDay) {
                    exerciseDao.getById(swap.sourceExerciseId)
                } else {
                    exerciseDao.getById(swap.targetExerciseId)
                } ?: return@mapNotNull null
                val outgoing = if (isTargetDay) {
                    exerciseDao.getById(swap.targetExerciseId)
                } else {
                    exerciseDao.getById(swap.sourceExerciseId)
                } ?: return@mapNotNull null
                SwapNotice(
                    title = if (isTargetDay) {
                        "Previously swapped"
                    } else {
                        "Temporary swap active"
                    },
                    detail = if (isTargetDay) {
                        "${incoming.name} is scheduled for today instead of ${outgoing.name}."
                    } else {
                        "${incoming.name} replaces ${outgoing.name} for this session."
                    },
                )
            }
    }
}

data class SwapNotice(
    val title: String,
    val detail: String,
)
