package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutRepository(private val db: AppDatabase) {
    val cycle: Flow<Cycle> = db.cycleDao().observeCycle().map { it ?: Cycle() }

    fun observeWorkoutDay(date: String): Flow<WorkoutDay?> = db.workoutDayDao().observeByDate(date)

    fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = db.workoutDayDao().observeAll()

    fun observeCycleSlots(): Flow<List<CycleSlot>> = db.cycleSlotDao().observeAll()

    suspend fun getCycleSlots(): List<CycleSlot> = db.cycleSlotDao().getAll()

    suspend fun addCycleSlot(name: String) {
        db.cycleSlotDao().upsert(CycleSlot(name = name))
        syncCycleSlotCount()
    }

    suspend fun deleteCycleSlot(id: Long) {
        db.cycleSlotDao().delete(id)
        syncCycleSlotCount()
    }

    suspend fun getCycle(): Cycle = db.cycleDao().getCycle() ?: Cycle()

    suspend fun saveCycle(isActive: Boolean, numTypes: Int) {
        val current = getCycle()
        db.cycleDao().upsert(
            Cycle(
                id = 1,
                isActive = isActive,
                numTypes = numTypes.coerceAtLeast(1),
                nextSessionType = current.nextSessionType
            )
        )
    }

    suspend fun setNextSessionType(slotType: Int) {
        val current = getCycle()
        db.cycleDao().upsert(current.copy(nextSessionType = slotType))
    }

    suspend fun assignCycleSlot(date: String, slotId: Long, alternativeFor: String? = null): WorkoutDay {
        val occurrence = (db.workoutDayDao().getMaxOccurrenceForSlotBefore(date, slotId) ?: 0) + 1
        val slotType = getCycleSlots()
            .indexOfFirst { it.id == slotId }
            .takeIf { it >= 0 }
        val day = WorkoutDay(
            date = date,
            cycleSlotType = slotType,
            cycleSlotId = slotId,
            cycleSlotOccurrence = occurrence,
            alternativeForDate = alternativeFor
        )
        db.workoutDayDao().upsert(day)
        return day
    }

    suspend fun getPreviousOccurrenceDayForSlot(date: String, slotId: Long, occurrence: Int): WorkoutDay? {
        if (occurrence <= 0) return null
        return db.workoutDayDao().getPreviousOccurrenceForSlot(slotId, occurrence, date)
    }

    suspend fun getWorkoutDay(date: String): WorkoutDay? = db.workoutDayDao().getByDate(date)

    suspend fun resolveSlotType(day: WorkoutDay?): Int? {
        if (day == null) {
            return null
        }
        day.cycleSlotType?.let { return it }
        val slotId = day.cycleSlotId ?: return null
        return getCycleSlots().indexOfFirst { it.id == slotId }.takeIf { it >= 0 }
    }

    suspend fun getLatestAssignedDayBefore(date: String, slotType: Int): WorkoutDay? {
        val slotId = getCycleSlots().getOrNull(slotType)?.id
        if (slotId != null) {
            db.workoutDayDao().getLatestForSlotIdBefore(date, slotId)?.let { return it }
        }
        return db.workoutDayDao().getLatestForSlotTypeBefore(date, slotType)
    }

    private suspend fun syncCycleSlotCount() {
        val slots = getCycleSlots()
        val maxIndex = (slots.size - 1).coerceAtLeast(0)
        val current = getCycle()
        db.cycleDao().upsert(
            current.copy(
                numTypes = slots.size.coerceAtLeast(1),
                nextSessionType = current.nextSessionType?.coerceIn(0, maxIndex),
            )
        )
    }
}
