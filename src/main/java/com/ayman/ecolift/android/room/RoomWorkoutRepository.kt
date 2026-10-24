package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.CycleSnapshotBuilder
import com.ayman.ecolift.data.DayQueries
import com.ayman.ecolift.data.ExerciseMeta
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitBucketKind
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.normalizedUserBodyweightLbs
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCalculator
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.data.splits.DayWrite
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.web.supabase.ArchiveSummary
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.SmartSplitRegistration
import com.ayman.ecolift.web.supabase.SplitChoiceBook
import com.ayman.ecolift.web.supabase.UserSettingsRepository
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import com.ayman.ecolift.web.supabase.sync.toWorkoutDay
import com.ayman.ecolift.web.supabase.toPendingWrite
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class RoomWorkoutRepository(
    private val store: RoomStore,
    private val sets: RoomSetRepository,
    private val exercises: ExerciseRepository,
    private val settings: UserSettingsRepository,
    scope: CoroutineScope,
) : WorkoutRepository {
    private val archiveJson = Json { ignoreUnknownKeys = true }
    private val choiceLock = Any()
    private var choiceBook = SplitChoiceBook()
    private var choiceGeneration = 0L
    private val choiceState = MutableStateFlow<SplitChoicesState>(SplitChoicesState.Loading)
    private val choiceWrites = Mutex()

    init {
        store.publishSplitChoices = { rows, replace ->
            synchronized(choiceLock) {
                if (replace) {
                    choiceGeneration++
                    choiceBook = SplitChoiceBook()
                }
                choiceBook.loaded(rows.mapNotNull { it.toChoiceOrNull() })
                choiceState.value = choiceBook.state.value
            }
        }
        scope.launch {
            store.db.splitMemberChoiceDao().observeAll().collect {
                store.write {
                    val rows = db.splitMemberChoiceDao().getAll()
                    editChoices { loaded(rows.mapNotNull { it.toChoiceOrNull() }) }
                }
            }
        }
    }

    override val cycle: StateFlow<Cycle> = store.cycle

    override val splitMemberChoices: StateFlow<SplitChoicesState> = choiceState

    private fun <T> editChoices(block: SplitChoiceBook.() -> T): T = synchronized(choiceLock) {
        val result = choiceBook.block()
        choiceState.value = choiceBook.state.value
        result
    }

    private fun editCurrentChoices(generation: Long, block: SplitChoiceBook.() -> Unit) {
        synchronized(choiceLock) {
            if (generation != choiceGeneration) return
            choiceBook.block()
            choiceState.value = choiceBook.state.value
        }
    }

    override fun observeWorkoutDay(date: String): Flow<WorkoutDay?> =
        store.days.map { days -> days.firstOrNull { it.date == date } }

    override fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = store.days

    override fun observeCycleSlots(): Flow<List<CycleSlot>> = store.slots.map { it.activeSlots() }

    override fun observeAllCycleSlots(): Flow<List<CycleSlot>> = store.slots

    override suspend fun getCycleSlots(): List<CycleSlot> = store.slotsNow().activeSlots()

    override suspend fun getSlotByIdIncludingArchived(id: Long): CycleSlot? =
        store.slotsNow().firstOrNull { it.id == id }

    override fun observeSplitExercises(splitId: Long): Flow<List<SplitExercise>> =
        store.splitExercises.map { rows -> rows.filter { it.splitId == splitId }.sortedBy { it.orderIndex } }

    override fun observeAllSplitExercises(): Flow<List<SplitExercise>> =
        combine(store.slots, store.splitExercises) { slots, rows ->
            val active = slots.activeSlots().map { it.id }.toSet()
            rows.filter { it.splitId in active }
        }

    override fun observeAllSplitExercisesIncludingRetired(): Flow<List<SplitExercise>> = store.splitExercises

    override fun observeArchivedCycles(): Flow<List<ArchivedCycle>> = store.archives

    override fun observeUserBodyweightLbs(): Flow<Int?> =
        settings.settings.map { it.userBodyweightLbs }

    override suspend fun addCycleSlot(name: String): Long = store.write {
        val order = (slots.activeSlots().maxOfOrNull { it.orderIndex } ?: -1) + 1
        val color = (slots.maxOfOrNull { it.colorIndex } ?: -1) + 1
        val stored = upsertSlot(
            CycleSlot(
                name = name,
                orderIndex = order,
                createdAt = System.currentTimeMillis(),
                colorIndex = color,
            ),
        )
        syncCycleCount()
        stored.id
    }

    override suspend fun deleteCycleSlot(id: Long) {
        store.write {
            deleteSlot(id)
            syncCycleCount()
        }
    }

    override suspend fun renameCycleSlot(id: Long, name: String) {
        store.write {
            val slot = slots.firstOrNull { it.id == id } ?: return@write
            upsertSlot(slot.copy(name = name))
        }
    }

    override suspend fun appendSplitExercise(slotId: Long, exerciseId: Long) {
        store.write {
            val rows = splitExercises.filter { it.splitId == slotId }.sortedBy { it.orderIndex }
            if (rows.any { it.exerciseId == exerciseId }) return@write
            val slot = slots.firstOrNull { it.id == slotId } ?: return@write
            replaceSplitMembers(
                slotId,
                (rows + SplitExercise(
                    splitId = slotId,
                    exerciseId = exerciseId,
                    orderIndex = rows.size,
                    userAdded = true,
                )).mapIndexed { index, row -> row.copy(orderIndex = index) },
            )
            upsertSlot(slot.copy(seedRevision = slot.seedRevision + 1, anchorUpdatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun saveSplitMembers(slotId: Long, board: EffectiveSplitMembers) {
        val (generation, token) = editChoices { choiceGeneration to stage(slotId, board) }
        try {
            val saved = choiceWrites.withLock {
                store.write {
                    if (synchronized(choiceLock) { generation != choiceGeneration }) return@write null
                    require(slots.any { it.id == slotId && it.isActive }) { "Active cycle slot $slotId not found" }
                    val revision = (db.splitMemberChoiceDao().getBySplitId(slotId)?.revision ?: 0L) + 1L
                    val choice = SplitMemberChoice(slotId, revision, board)
                    db.splitMemberChoiceDao().upsert(choice.toRoomChoice())
                    choice
                }
            }
            if (saved != null) editCurrentChoices(generation) { confirm(token, saved) }
        } finally {
            editCurrentChoices(generation) { drop(token) }
        }
    }

    override suspend fun getCycle(): Cycle = store.cycleNow()

    override suspend fun saveCycle(isActive: Boolean, numTypes: Int) {
        store.write { upsertCycle(cycle.copy(isActive = isActive, numTypes = numTypes)) }
    }

    override suspend fun setNextSessionType(slotType: Int) {
        store.write { upsertCycle(cycle.copy(nextSessionType = slotType)) }
    }

    override suspend fun assignCycleSlot(date: String, slotId: Long, alternativeFor: String?): WorkoutDay {
        return store.write {
            val active = slots.activeSlots()
            val slotType = active.indexOfFirst { it.id == slotId }
            require(slotType >= 0) { "Active cycle slot $slotId not found" }
            val occurrence = (DayQueries.maxOccurrenceForSlotBefore(
                days = days,
                date = date,
                slotId = slotId,
                notBeforeDate = activeCycleStart(),
            ) ?: 0) + 1
            val day = WorkoutDay(date, slotType, occurrence, alternativeFor, slotId)
            upsertDay(day)
            day
        }
    }

    override suspend fun clearCycleSlot(date: String) {
        store.write {
            val day = days.firstOrNull { it.date == date } ?: return@write
            upsertDay(day.copy(cycleSlotType = null, cycleSlotOccurrence = null, alternativeForDate = null, cycleSlotId = null))
        }
    }

    override suspend fun ensureWorkoutDay(date: String) {
        store.write {
            if (days.none { it.date == date }) upsertDay(WorkoutDay(date = date))
        }
    }

    override suspend fun enqueueDayBatch(op: DayPendingWrite) {
        store.write { applyDay(op) }
    }

    override suspend fun getPreviousOccurrenceDayForSlot(date: String, slotId: Long, occurrence: Int): WorkoutDay? {
        if (occurrence <= 0) return null
        require(store.slotsNow().any { it.id == slotId && it.isActive }) { "Active cycle slot $slotId not found" }
        return DayQueries.previousOccurrenceForSlot(
            days = store.daysNow(),
            slotId = slotId,
            occurrence = occurrence,
            beforeDate = date,
            notBeforeDate = activeCycleStart(),
        )
    }

    override suspend fun getArchivedCycle(id: Long): ArchivedCycle? =
        store.archivesNow().firstOrNull { it.id == id }

    override suspend fun deleteArchivedCycle(id: Long) {
        store.write { deleteArchive(id) }
    }

    override suspend fun countOverlappingArchives(start: String, end: String): Int =
        store.archivesNow().count { it.startDate <= end && it.endDate >= start }

    override suspend fun getLatestWorkoutDate(): String? = SetQueries.latestCompletedDate(sets.sets.value)

    override suspend fun moveWorkoutDay(currentDate: String, newDate: String) {
        if (currentDate == newDate) return
        store.write {
            val day = days.firstOrNull { it.date == currentDate } ?: return@write
            if (days.any { it.date == newDate }) {
                throw IllegalArgumentException("A workout day already exists on $newDate")
            }
            this@RoomWorkoutRepository.sets.updateDate(currentDate, newDate)
            deleteDay(currentDate)
            upsertDay(day.copy(date = newDate))
        }
    }

    override suspend fun getActiveCycleStart(): String = activeCycleStart()

    override suspend fun archivedCycleProgress(archiveId: Long): CycleProgressCore =
        buildArchivedCycleProgress(archiveId).core

    override suspend fun archivedCycleComparison(archiveId: Long, window: ComparisonWindow): CycleComparison {
        val coreResult = buildArchivedCycleProgress(archiveId)
        return CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = loadPriorSets(coreResult.core.lifts.map { it.exerciseId }, coreResult.core.startDate),
            window = window,
            userBodyweightLbs = bodyweight(),
        )
    }

    override suspend fun archiveCurrentCycle(name: String, startDate: String, endDate: String): Long {
        val parsedStart = LocalDate.parse(startDate)
        val parsedEnd = LocalDate.parse(endDate)
        require(!parsedStart.isAfter(parsedEnd)) { "Archive startDate must be on or before endDate" }
        return store.write {
            val bodyweight = normalizedUserBodyweightLbs(settings.userBodyweightLbs)
            val cycleSets = SetQueries.completedSetsInRange(sets, startDate, endDate)
            val active = slots.activeSlots()
            val activeIds = active.map { it.id }.toSet()
            val members = splitExercises.filter { it.splitId in activeIds }
            val snapshot = CycleSnapshotBuilder.build(
                startDate = startDate,
                endDate = endDate,
                slots = active,
                splitExercises = members,
                workoutDays = days,
                sets = cycleSets,
                exerciseNames = exerciseMeta(cycleSets.map { it.exerciseId }),
                userBodyweightLbs = bodyweight,
            )
            val archivedAt = System.currentTimeMillis()
            val stored = insertArchive(
                ArchivedCycle(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    splitCount = CycleSnapshotBuilder.splitCount(snapshot),
                    totalVolumeLbs = snapshot.totals.totalVolumeLbs,
                    totalSessions = snapshot.totals.sessions,
                    archivedAt = archivedAt,
                    schemaVersion = snapshot.schemaVersion,
                    snapshotJson = archiveJson.encodeToString(snapshot),
                ),
            )
            activeIds.forEach { slotId ->
                val slot = slots.first { it.id == slotId }
                upsertSlot(slot.copy(archivedAt = archivedAt))
            }
            upsertCycle(
                cycle.copy(
                    isActive = false,
                    numTypes = 0,
                    nextSessionType = null,
                    startDate = parsedEnd.plusDays(1).toString(),
                    name = null,
                ),
            )
            stored.id
        }
    }

    override suspend fun archiveSummary(archive: ArchivedCycle): ArchiveSummary {
        val snapshot = buildMutableArchivedSnapshot(archive, bodyweight())
        return ArchiveSummary(
            splitCount = CycleSnapshotBuilder.splitCount(snapshot),
            totalSessions = snapshot.totals.sessions,
            totalVolumeLbs = snapshot.totals.totalVolumeLbs,
        )
    }

    override suspend fun registerSmartSplit(
        name: String,
        exerciseIds: List<Long>,
        dates: List<String>,
    ): SmartSplitRegistration {
        if (name.isBlank() || exerciseIds.isEmpty()) return SmartSplitRegistration.Refused("bad_response")
        return store.write {
            val order = (slots.activeSlots().maxOfOrNull { it.orderIndex } ?: -1) + 1
            val color = (slots.maxOfOrNull { it.colorIndex } ?: -1) + 1
            val slot = upsertSlot(
                CycleSlot(
                    name = name,
                    orderIndex = order,
                    createdAt = System.currentTimeMillis(),
                    colorIndex = color,
                ),
            )
            replaceSplitMembers(
                slot.id,
                exerciseIds.distinct().mapIndexed { index, exerciseId ->
                    SplitExercise(splitId = slot.id, exerciseId = exerciseId, orderIndex = index)
                },
            )
            val claimed = mutableListOf<String>()
            dates.distinct().forEach { date ->
                val existing = days.firstOrNull { it.date == date }
                if (existing?.cycleSlotId != null) return@forEach
                val occurrence = (DayQueries.maxOccurrenceForSlotBefore(days, date, slot.id, activeCycleStart()) ?: 0) + 1
                val slotType = slots.activeSlots().indexOfFirst { it.id == slot.id }.coerceAtLeast(0)
                upsertDay(WorkoutDay(date, slotType, occurrence, existing?.alternativeForDate, slot.id))
                claimed += date
            }
            syncCycleCount()
            SmartSplitRegistration.Added(slot.id, claimed)
        }
    }

    override suspend fun upsertLearnedSlot(
        slotId: Long?,
        name: String?,
        learned: Boolean,
        memberIds: List<Long>,
        anchorUpdatedAt: Long,
    ): Long = store.write {
        val existing = slotId?.let { id -> slots.firstOrNull { it.id == id } }
        val stored = if (existing == null) {
            val order = (slots.activeSlots().maxOfOrNull { it.orderIndex } ?: -1) + 1
            val color = (slots.maxOfOrNull { it.colorIndex } ?: -1) + 1
            upsertSlot(
                CycleSlot(
                    name = name.orEmpty(),
                    orderIndex = order,
                    learned = learned,
                    createdAt = System.currentTimeMillis(),
                    anchorUpdatedAt = anchorUpdatedAt,
                    colorIndex = color,
                ),
            )
        } else {
            upsertSlot(
                existing.copy(
                    name = name ?: existing.name,
                    learned = learned,
                    anchorUpdatedAt = anchorUpdatedAt,
                ),
            )
        }
        replaceSplitMembers(
            stored.id,
            memberIds.distinct().mapIndexed { index, exerciseId ->
                SplitExercise(splitId = stored.id, exerciseId = exerciseId, orderIndex = index)
            },
        )
        syncCycleCount()
        stored.id
    }

    override suspend fun refreshSlots() {
        store.write { reloadRoutine() }
    }

    override suspend fun archiveLearnedSlot(slotId: Long) {
        store.write {
            val slot = slots.firstOrNull { it.id == slotId } ?: return@write
            upsertSlot(slot.copy(archivedAt = System.currentTimeMillis()))
            syncCycleCount()
        }
    }

    override suspend fun suppressCycleSlot(slotId: Long) {
        store.write {
            val slot = slots.firstOrNull { it.id == slotId } ?: return@write
            upsertSlot(slot.copy(suppressedAt = System.currentTimeMillis()))
            syncCycleCount()
        }
    }

    override suspend fun enqueueLearnedDayWrites(writes: List<DayWrite>) {
        store.write { writes.forEach { applyDay(it.toPendingWrite()) } }
    }

    private suspend fun RoomDraft.applyDay(op: DayPendingWrite) {
        if (op.ensureOnly && days.any { it.date == op.date }) return
        upsertDay(op.toWorkoutDay())
    }

    private suspend fun RoomDraft.syncCycleCount() {
        val activeCount = slots.count { it.isActive }
        val maxIndex = (activeCount - 1).coerceAtLeast(0)
        upsertCycle(
            cycle.copy(
                numTypes = activeCount.coerceAtLeast(1),
                nextSessionType = cycle.nextSessionType?.coerceIn(0, maxIndex),
            ),
        )
    }

    private fun activeCycleStart(): String =
        store.cycleNow().startDate
            ?: SetQueries.earliestCompletedDate(sets.sets.value)
            ?: todayLocalDate().toString()

    private suspend fun bodyweight(): Int? = normalizedUserBodyweightLbs(settings.settings.value.userBodyweightLbs)

    private suspend fun buildArchivedCycleProgress(archiveId: Long): CycleProgressCalculator.CoreResult {
        val archive = requireNotNull(getArchivedCycle(archiveId)) { "Archived cycle not found: $archiveId" }
        val userBodyweightLbs = bodyweight()
        val snapshot = buildMutableArchivedSnapshot(archive, userBodyweightLbs)
        val cycleSets = SetQueries.completedSetsInRange(sets.sets.value, archive.startDate, archive.endDate)
        return CycleProgressCalculator.buildCore(
            snapshot = snapshot,
            sets = cycleSets.map { it.toProgressInput() },
            userBodyweightLbs = userBodyweightLbs,
            realSlotCount = snapshot.splits.count { it.bucketKind == SplitBucketKind.Real },
        )
    }

    private suspend fun buildMutableArchivedSnapshot(archive: ArchivedCycle, userBodyweightLbs: Int?): CycleSnapshot {
        val archivedSnapshot = archiveJson.decodeFromString<CycleSnapshot>(archive.snapshotJson)
        val restoredSlots = archivedSnapshot.splits
            .filter { it.bucketKind == SplitBucketKind.Real }
            .sortedWith(compareBy({ it.orderIndex }, { it.slotId }))
            .map { split -> CycleSlot(id = split.slotId, name = split.name, orderIndex = split.orderIndex) }
        val members = archivedSnapshot.splits
            .filter { it.bucketKind == SplitBucketKind.Real }
            .flatMap { split ->
                split.exercises.mapIndexed { index, exercise ->
                    SplitExercise(splitId = split.slotId, exerciseId = exercise.exerciseId, orderIndex = index)
                }
            }
        val cycleSets = SetQueries.completedSetsInRange(sets.sets.value, archive.startDate, archive.endDate)
        val snapshotMeta = archivedSnapshot.splits.flatMap { it.exercises }
            .associate { exercise -> exercise.exerciseId to ExerciseMeta(exercise.name, exercise.isBodyweight) }
        val live = exerciseMeta((cycleSets.map { it.exerciseId } + snapshotMeta.keys).distinct())
        return CycleSnapshotBuilder.build(
            startDate = archive.startDate,
            endDate = archive.endDate,
            slots = restoredSlots,
            splitExercises = members,
            workoutDays = store.daysNow(),
            sets = cycleSets,
            exerciseNames = snapshotMeta + live,
            userBodyweightLbs = userBodyweightLbs,
        )
    }

    private fun loadPriorSets(exerciseIds: List<Long>, beforeDate: String): Map<Long, List<CycleProgressCalculator.SetInput>> =
        exerciseIds.distinct().associateWith { exerciseId ->
            SetQueries.historyBeforeDate(sets.sets.value, exerciseId, beforeDate).map { it.toProgressInput() }
        }

    private suspend fun exerciseMeta(exerciseIds: List<Long>): Map<Long, ExerciseMeta> =
        exerciseIds.distinct().mapNotNull { exerciseId ->
            exercises.getById(exerciseId)?.let { exercise ->
                exerciseId to ExerciseMeta(exercise.name, exercise.isBodyweight)
            }
        }.toMap()
}

private fun List<CycleSlot>.activeSlots(): List<CycleSlot> =
    filter { it.isActive }.sortedWith(compareBy<CycleSlot> { it.orderIndex }.thenBy { it.id })

private fun WorkoutSet.toProgressInput() = CycleProgressCalculator.SetInput(
    id = id,
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed,
    isWarmup = isWarmup,
)
