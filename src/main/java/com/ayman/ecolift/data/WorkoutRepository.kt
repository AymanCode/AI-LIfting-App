package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutRepository(private val db: AppDatabase) {
    val cycle: Flow<Cycle> = db.cycleDao().observeCycle().map { it ?: Cycle() }

    fun observeWorkoutDay(date: String): Flow<WorkoutDay?> = db.workoutDayDao().observeByDate(date)

    fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = db.workoutDayDao().observeAll()

    suspend fun getCycle(): Cycle = db.cycleDao().getCycle() ?: Cycle()

    suspend fun getWorkoutDay(date: String): WorkoutDay? = db.workoutDayDao().getByDate(date)

    suspend fun saveCycle(isActive: Boolean, numTypes: Int) {
        val existing = db.cycleDao().getCycle() ?: Cycle()
        db.cycleDao().upsert(
            Cycle(
                id = 1,
                isActive = isActive,
                numTypes = numTypes.coerceAtLeast(1),
                nextSessionType = existing.nextSessionType?.takeIf { it in 0 until numTypes.coerceAtLeast(1) },
            )
        )
    }

    suspend fun assignCycleSlot(date: String, slotType: Int): WorkoutDay {
        val occurrence = (db.workoutDayDao().getMaxOccurrenceBefore(date, slotType) ?: 0) + 1
        val day = WorkoutDay(
            date = date,
            cycleSlotType = slotType,
            cycleSlotOccurrence = occurrence,
        )
        db.workoutDayDao().upsert(day)
        val cycle = db.cycleDao().getCycle()
        if (cycle?.nextSessionType != null) {
            db.cycleDao().upsert(cycle.copy(nextSessionType = null))
        }
        return day
    }

    suspend fun getPreviousOccurrenceDay(date: String, slotType: Int, occurrence: Int): WorkoutDay? {
        if (occurrence <= 0) {
            return null
        }
        return db.workoutDayDao().getPreviousOccurrence(slotType, occurrence, date)
    }

    suspend fun getLatestAssignedDayBefore(date: String, slotType: Int): WorkoutDay? {
        return db.workoutDayDao().getLatestAssignedBeforeDate(slotType, date)
    }

    suspend fun setNextSessionType(slotType: Int?) {
        val current = db.cycleDao().getCycle() ?: Cycle()
        db.cycleDao().upsert(current.copy(nextSessionType = slotType))
    }
}
