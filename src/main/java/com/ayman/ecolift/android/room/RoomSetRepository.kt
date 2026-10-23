package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.DateVolume
import com.ayman.ecolift.data.ExerciseMaxWeight
import com.ayman.ecolift.data.ExerciseProgressSummary
import com.ayman.ecolift.data.ExerciseVolume
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.data.normalizedBodyweightLoad
import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.data.room.toRoom
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class RoomSetRepository(
    private val store: RoomStore,
) : SetRepository {
    override val sets: StateFlow<List<WorkoutSet>> = store.sets
    override val cards: StateFlow<List<WorkoutExerciseCard>> = store.cards

    override suspend fun refresh() {
        store.write {
            sets = db.workoutSetDao().getAll().map { it.toDomain() }
            cards = db.workoutExerciseCardDao().getAll().map { it.toDomain() }
        }
    }

    override suspend fun insert(set: WorkoutSet): LocalCommit<WorkoutSet> = store.write {
        val card = ensureCard(set.date, set.exerciseId, WorkoutCardOrigin.MANUAL)
        LocalCommit.Committed(insertSet(set.copy(cardId = set.cardId.ifBlank { card.id })))
    }

    override suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet> = updateSet(set, null)

    override suspend fun delete(id: Long) {
        deleteSet(id)
    }

    override suspend fun updateCardDisplayMode(
        cardId: String,
        displayMode: WorkoutCardDisplayMode,
    ): LocalCommit<Unit> = store.write {
        val card = cards.firstOrNull { it.id == cardId } ?: return@write LocalCommit.Committed(Unit)
        updateCard(card.copy(displayMode = displayMode))
        LocalCommit.Committed(Unit)
    }

    override suspend fun getAllSets(): List<WorkoutSet> = store.setsNow()

    override fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> =
        sets.map { SetQueries.forDate(it, date) }

    override suspend fun getSetsForDate(date: String): List<WorkoutSet> =
        SetQueries.forDate(store.setsNow(), date)

    override suspend fun addSet(
        date: String,
        exerciseId: Long,
        copyFrom: WorkoutSet?,
    ): LocalCommit<WorkoutSet> = store.write {
        val snapshot = sets
        val current = SetQueries.forDateAndExercise(snapshot, date, exerciseId)
        val lastSession = SetQueries.lastCompletedSession(snapshot, exerciseId, date).filterNot { it.isWarmup }
        val position = current.count { !it.isWarmup } + 1
        val template = copyFrom?.takeUnless { it.isWarmup }
            ?: current.lastOrNull { !it.isWarmup }
            ?: lastSession.firstOrNull()
            ?: SetQueries.mostRecentBeforeDate(snapshot.filterNot { it.isWarmup }, exerciseId, date)
        val exercise = store.exercisesNow().firstOrNull { it.id == exerciseId }
        val isBodyweight = template?.isBodyweight ?: exercise?.isBodyweight ?: false
        val card = ensureCard(date, exerciseId, WorkoutCardOrigin.MANUAL)
        val created = insertSet(
            WorkoutSet(
                exerciseId = exerciseId,
                date = date,
                setNumber = SetQueries.nextSetNumber(current),
                weightLbs = if (isBodyweight) normalizedBodyweightLoad(template?.weightLbs) else template?.weightLbs,
                reps = SetQueries.predictedRepsForNewSet(
                    previousReps = template?.reps,
                    lastSessionReps = lastSession.map(WorkoutSet::reps),
                    position = position,
                ),
                isBodyweight = isBodyweight,
                completed = false,
                cardId = card.id,
            ),
        )
        LocalCommit.Committed(created)
    }

    override suspend fun addExerciseSession(
        date: String,
        exerciseId: Long,
        isBodyweightDefault: Boolean,
    ): LocalCommit<List<WorkoutSet>> = store.write {
        LocalCommit.Committed(seedSession(date, exerciseId, isBodyweightDefault))
    }

    override suspend fun addSetsForExercises(date: String, exerciseIds: List<Long>): LocalCommit<Unit> =
        store.write {
            val existing = SetQueries.forDate(sets, date).map { it.exerciseId }.toSet()
            exerciseIds.distinct().filterNot { it in existing }.forEach { exerciseId ->
                seedSession(date, exerciseId, false)
            }
            LocalCommit.Committed(Unit)
        }

    override suspend fun replaceDayBatch(
        date: String,
        desiredSets: List<WorkoutSet>,
        cardModes: Map<Long, WorkoutCardDisplayMode>,
        precondition: () -> Unit,
    ): LocalCommit<Unit> = store.write {
        precondition()
        require(desiredSets.all { it.date == date }) { "Every desired set must belong to $date" }
        require(desiredSets.map { it.date to it.exerciseId to it.setNumber }.distinct().size == desiredSets.size) {
            "Desired sets must have unique natural keys"
        }
        applyDayBatch(date, desiredSets, cardModes)
        LocalCommit.Committed(Unit)
    }

    override suspend fun cloneDay(templateDate: String, targetDate: String): LocalCommit<Unit> = store.write {
        val present = sets.filter { it.date == targetDate }.map { it.exerciseId }.toSet()
        val copies = sets.filter { it.date == templateDate && it.exerciseId !in present }
            .groupBy { it.exerciseId }
        copies.forEach { (exerciseId, rows) ->
            val card = ensureCard(targetDate, exerciseId, WorkoutCardOrigin.MANUAL)
            rows.sortedBy { it.setNumber }.forEach { template ->
                insertSet(
                    template.copy(
                        id = 0,
                        date = targetDate,
                        completed = false,
                        syncId = "",
                        rowRevision = 1,
                        cardId = card.id,
                        restTimeSeconds = null,
                    ),
                )
            }
        }
        LocalCommit.Committed(Unit)
    }

    override suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet?): LocalCommit<WorkoutSet> = store.write {
        val current = sets.firstOrNull { it.id == set.id }
            ?: return@write LocalCommit.Committed(set)
        val merged = mergeSetFields(observed, current, set).copy(rowRevision = current.rowRevision)
        val card = ensureCard(merged.date, merged.exerciseId, WorkoutCardOrigin.MANUAL)
        val next = merged.copy(cardId = merged.cardId.ifBlank { card.id })
        if (current.date != next.date || current.exerciseId != next.exerciseId || current.setNumber != next.setNumber) {
            rewriteExistingSets(listOf(next))
        } else {
            updateSetRow(next)
        }
        LocalCommit.Committed(sets.first { it.id == next.id })
    }

    override suspend fun upsertSet(set: WorkoutSet): LocalCommit<Long> {
        val existing = store.setsNow().firstOrNull { it.id == set.id && set.id != 0L }
        val saved = if (existing == null) insert(set) else updateSet(set, null)
        return when (saved) {
            is LocalCommit.Committed -> LocalCommit.Committed(saved.value.id)
            is LocalCommit.NotCommitted -> saved
        }
    }

    override suspend fun getLastForDateAndExercise(date: String, exerciseId: Long): WorkoutSet? =
        SetQueries.forDateAndExercise(store.setsNow(), date, exerciseId).lastOrNull()

    override suspend fun getMostRecentBeforeDate(exerciseId: Long, date: String): WorkoutSet? =
        SetQueries.mostRecentBeforeDate(store.setsNow(), exerciseId, date)

    override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> =
        SetQueries.completedHistoryBeforeDate(store.setsNow(), exerciseId, beforeDate).take(100)

    override suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int? =
        SetQueries.maxCompletedWeightBefore(store.setsNow(), exerciseId, beforeDate)

    override suspend fun getLastSessionDate(beforeDate: String): String? =
        store.setsNow().filter { it.date < beforeDate }.maxOfOrNull { it.date }

    override suspend fun getSetsByDate(date: String): List<WorkoutSet> = getSetsForDate(date)

    override suspend fun getSetsForDates(dates: List<String>): List<WorkoutSet> {
        if (dates.isEmpty()) return emptyList()
        val wanted = dates.toSet()
        return store.setsNow().filter { it.date in wanted }
    }

    override suspend fun getCompletedSetsForDates(dates: List<String>): List<WorkoutSet> {
        if (dates.isEmpty()) return emptyList()
        val wanted = dates.toSet()
        return store.setsNow().filter { it.completed && it.date in wanted }
    }

    override suspend fun getById(id: Long): WorkoutSet? = store.setsNow().firstOrNull { it.id == id }

    override fun observeExerciseProgressSummaries(): Flow<List<ExerciseProgressSummary>> =
        sets.map { rows ->
            val exercisesById = store.exercisesNow().associateBy { it.id }
            rows.filter { it.isCheckedWorking }
                .groupBy { it.exerciseId }
                .mapNotNull { (exerciseId, exerciseSets) ->
                    val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
                    ExerciseProgressSummary(
                        exerciseId = exerciseId,
                        exerciseName = exercise.name,
                        isBodyweight = exercise.isBodyweight,
                        sessionCount = exerciseSets.map { it.date }.distinct().size,
                        lastSessionDate = exerciseSets.maxOf { it.date },
                    )
                }
        }

    override fun observeSetsSince(exerciseId: Long, sinceDate: String): Flow<List<WorkoutSet>> =
        sets.map { rows ->
            rows.filter { it.exerciseId == exerciseId && it.date >= sinceDate && it.isCheckedWorking }
                .sortedWith(compareBy({ it.date }, { it.setNumber }))
        }

    override suspend fun getVolumeHistory(exerciseId: Long, limit: Int): List<DateVolume> =
        store.setsNow().filter { it.exerciseId == exerciseId && it.isCheckedWorking }
            .groupBy { it.date }
            .map { (date, daySets) -> DateVolume(date, WorkoutMetrics.roundedVolume(daySets)) }
            .sortedByDescending { it.date }
            .take(limit)

    override suspend fun getSetsSince(exerciseId: Long, sinceDate: String): List<WorkoutSet> =
        store.setsNow().filter { it.exerciseId == exerciseId && it.date >= sinceDate && it.isCheckedWorking }
            .sortedWith(compareBy({ it.date }, { it.setNumber }))

    override suspend fun getVolumesSince(sinceDate: String): List<ExerciseVolume> =
        store.setsNow().filter { it.date >= sinceDate && it.isCheckedWorking }
            .groupBy { it.exerciseId }
            .map { (exerciseId, exerciseSets) ->
                ExerciseVolume(exerciseId, WorkoutMetrics.roundedVolume(exerciseSets))
            }

    override suspend fun getAllTimeMaxWeights(): List<ExerciseMaxWeight> =
        store.setsNow().filter { it.isCheckedWorking }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exerciseSets) ->
                val max = exerciseSets.mapNotNull { it.weightLbs }.maxOrNull() ?: return@mapNotNull null
                ExerciseMaxWeight(exerciseId, max)
            }

    override suspend fun getMaxWeightsForExercises(exerciseIds: List<Long>): List<ExerciseMaxWeight> {
        if (exerciseIds.isEmpty()) return emptyList()
        val ids = exerciseIds.toSet()
        return store.setsNow().filter { it.isCheckedWorking && it.exerciseId in ids }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exerciseSets) ->
                val max = exerciseSets.mapNotNull { it.weightLbs }.maxOrNull() ?: return@mapNotNull null
                ExerciseMaxWeight(exerciseId, max)
            }
    }

    override suspend fun deleteSet(id: Long) {
        store.write { deleteSetRow(id) }
    }

    override suspend fun deleteSets(ids: List<Long>, precondition: () -> Unit): LocalCommit<Unit> = store.write {
        precondition()
        ids.distinct().forEach { deleteSetRow(it) }
        LocalCommit.Committed(Unit)
    }

    override suspend fun updateDate(fromDate: String, toDate: String) {
        if (fromDate == toDate) return
        store.write { moveSetsToDate(fromDate, toDate) }
    }

    private suspend fun RoomDraft.seedSession(
        date: String,
        exerciseId: Long,
        isBodyweightDefault: Boolean,
    ): List<WorkoutSet> {
        val current = SetQueries.forDateAndExercise(sets, date, exerciseId)
        val copies = if (current.isNotEmpty()) {
            emptyList()
        } else {
            SetQueries.buildLastSessionSetCopies(date, exerciseId, SetQueries.historyBeforeDate(sets, exerciseId, date))
        }
        val card = ensureCard(date, exerciseId, WorkoutCardOrigin.MANUAL)
        return if (copies.isNotEmpty()) {
            copies.map { copy -> insertSet(copy.copy(cardId = card.id)) }
        } else {
            val template = current.lastOrNull()
            listOf(
                insertSet(
                    WorkoutSet(
                        exerciseId = exerciseId,
                        date = date,
                        setNumber = SetQueries.nextSetNumber(current),
                        weightLbs = template?.weightLbs,
                        reps = template?.reps,
                        isBodyweight = template?.isBodyweight ?: isBodyweightDefault,
                        completed = false,
                        cardId = card.id,
                    ),
                ),
            )
        }
    }

    private suspend fun RoomDraft.applyDayBatch(
        date: String,
        desiredSets: List<WorkoutSet>,
        cardModes: Map<Long, WorkoutCardDisplayMode>,
    ) {
        val current = sets.filter { it.date == date }
        val desiredKeys = desiredSets.map { it.exerciseId to it.setNumber }.toSet()
        current.filter { (it.exerciseId to it.setNumber) !in desiredKeys }.forEach { row ->
            db.workoutSetDao().deleteById(row.id)
        }
        sets = sets.filterNot { it.date == date && (it.exerciseId to it.setNumber) !in desiredKeys }
        val exerciseIds = desiredSets.map { it.exerciseId }.distinct()
        val retiredCards = cards.filter { it.date == date && it.exerciseId !in exerciseIds }.map { it.id }
        deleteCards(retiredCards)
        exerciseIds.forEach { exerciseId -> ensureCard(date, exerciseId, WorkoutCardOrigin.MANUAL) }
        cardModes.forEach { (exerciseId, mode) ->
            val card = cards.firstOrNull { it.date == date && it.exerciseId == exerciseId } ?: return@forEach
            if (card.displayMode != mode) updateCard(card.copy(displayMode = mode))
        }
        desiredSets.forEach { desired ->
            val card = cards.first { it.date == date && it.exerciseId == desired.exerciseId }
            val existing = sets.firstOrNull {
                it.date == date && it.exerciseId == desired.exerciseId && it.setNumber == desired.setNumber
            }
            if (existing == null) {
                insertSet(
                    desired.copy(
                        id = 0,
                        cardId = card.id,
                        syncId = desired.syncId,
                        rowRevision = 1,
                    ),
                )
            } else {
                updateSetRow(
                    existing.copy(
                        weightLbs = desired.weightLbs,
                        reps = desired.reps,
                        isBodyweight = desired.isBodyweight,
                        completed = desired.completed,
                        restTimeSeconds = desired.restTimeSeconds,
                        isWarmup = desired.isWarmup,
                        cardId = card.id,
                    ),
                )
            }
        }
        val emptyCards = cards.filter { card -> card.date == date && sets.none { it.cardId == card.id } }.map { it.id }
        deleteCards(emptyCards)
    }

    private suspend fun RoomDraft.moveSetsToDate(fromDate: String, toDate: String) {
        val moving = sets.filter { it.date == fromDate }
        if (moving.isEmpty() && cards.none { it.date == fromDate }) return
        val movingExercises = moving.map { it.exerciseId }.toSet()
        val occupied = sets.filter { it.date == toDate }.map { it.exerciseId }.toSet()
        require(movingExercises.none { it in occupied }) {
            "Target date already has one of these exercises"
        }
        val nextPosition = (cards.filter { it.date == toDate }.maxOfOrNull { it.position } ?: -1) + 1
        val movedCards = cards.filter { it.date == fromDate && it.exerciseId in movingExercises }
            .sortedBy { it.position }
            .mapIndexed { index, card -> card.copy(date = toDate, position = nextPosition + index) }
        movedCards.forEach { card ->
            db.workoutExerciseCardDao().update(card.toRoom())
        }
        val movedIds = movedCards.map { it.id }.toSet()
        cards = cards.filterNot { it.id in movedIds } + movedCards
        val movedSets = moving.map { row ->
            val cardId = movedCards.firstOrNull { it.exerciseId == row.exerciseId }?.id ?: row.cardId
            row.copy(date = toDate, cardId = cardId, rowRevision = row.rowRevision + 1)
        }
        rewriteExistingSets(movedSets)
    }

    private suspend fun RoomDraft.ensureCard(
        date: String,
        exerciseId: Long,
        origin: WorkoutCardOrigin,
    ): WorkoutExerciseCard {
        cards.firstOrNull { it.date == date && it.exerciseId == exerciseId }?.let { return it }
        val position = (cards.filter { it.date == date }.maxOfOrNull { it.position } ?: -1) + 1
        val card = WorkoutExerciseCard(
            id = newCardId(),
            date = date,
            exerciseId = exerciseId,
            position = position,
            displayMode = WorkoutCardDisplayMode.EXPANDED,
            origin = origin,
        )
        insertCard(card)
        return card
    }
}
