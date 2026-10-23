package com.ayman.ecolift.android.room

import androidx.room.withTransaction
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseAlias
import com.ayman.ecolift.data.ExerciseDistinctPair
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.room.WorkoutDayState
import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.data.room.toRoom
import com.ayman.ecolift.data.splits.SplitAlternative
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext.Key
import kotlin.coroutines.coroutineContext

/**
 * The only writer of the in-memory lists. A nested call joins the open draft and does not
 * publish. The lists change after the outermost transaction commits.
 */
class RoomStore(val db: AppDatabase) {
    private val mutex = Mutex()
    private val active = ThreadLocal<RoomDraft?>()

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    private val _aliases = MutableStateFlow<List<ExerciseAlias>>(emptyList())
    private val _pairs = MutableStateFlow<List<ExerciseDistinctPair>>(emptyList())
    private val _sets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private val _cards = MutableStateFlow<List<WorkoutExerciseCard>>(emptyList())
    private val _settings = MutableStateFlow(UserSettings())
    private val _days = MutableStateFlow<List<WorkoutDay>>(emptyList())
    private val _cycle = MutableStateFlow(Cycle())
    private val _slots = MutableStateFlow<List<CycleSlot>>(emptyList())
    private val _splitExercises = MutableStateFlow<List<SplitExercise>>(emptyList())
    private val _archives = MutableStateFlow<List<ArchivedCycle>>(emptyList())
    private val _reviews = MutableStateFlow<List<PendingReview>>(emptyList())
    private val _cardio = MutableStateFlow<List<CardioSession>>(emptyList())
    private val _journal = MutableStateFlow<Map<String, PreparedSplitReplacement>>(emptyMap())
    private val _alternatives = MutableStateFlow<List<SplitAlternative>>(emptyList())

    val exercises: StateFlow<List<Exercise>> = _exercises
    val aliases: StateFlow<List<ExerciseAlias>> = _aliases
    val pairs: StateFlow<List<ExerciseDistinctPair>> = _pairs
    val sets: StateFlow<List<WorkoutSet>> = _sets
    val cards: StateFlow<List<WorkoutExerciseCard>> = _cards
    val settings: StateFlow<UserSettings> = _settings
    val days: StateFlow<List<WorkoutDay>> = _days
    val cycle: StateFlow<Cycle> = _cycle
    val slots: StateFlow<List<CycleSlot>> = _slots
    val splitExercises: StateFlow<List<SplitExercise>> = _splitExercises
    val archives: StateFlow<List<ArchivedCycle>> = _archives
    val reviews: StateFlow<List<PendingReview>> = _reviews
    val cardio: StateFlow<List<CardioSession>> = _cardio
    val alternatives: StateFlow<List<SplitAlternative>> = _alternatives

    fun exercisesNow(): List<Exercise> = active.get()?.exercises ?: _exercises.value
    fun setsNow(): List<WorkoutSet> = active.get()?.sets ?: _sets.value
    fun cardsNow(): List<WorkoutExerciseCard> = active.get()?.cards ?: _cards.value
    fun aliasesNow(): List<ExerciseAlias> = active.get()?.aliases ?: _aliases.value
    fun pairsNow(): List<ExerciseDistinctPair> = active.get()?.pairs ?: _pairs.value
    fun settingsNow(): UserSettings = active.get()?.settings ?: _settings.value
    fun daysNow(): List<WorkoutDay> = active.get()?.days ?: _days.value
    fun cycleNow(): Cycle = active.get()?.cycle ?: _cycle.value
    fun slotsNow(): List<CycleSlot> = active.get()?.slots ?: _slots.value
    fun splitExercisesNow(): List<SplitExercise> = active.get()?.splitExercises ?: _splitExercises.value
    fun archivesNow(): List<ArchivedCycle> = active.get()?.archives ?: _archives.value
    fun reviewsNow(): List<PendingReview> = active.get()?.reviews ?: _reviews.value
    fun cardioNow(): List<CardioSession> = active.get()?.cardio ?: _cardio.value
    fun journalNow(): Map<String, PreparedSplitReplacement> = active.get()?.journal ?: _journal.value

    fun saveJournal(replacement: PreparedSplitReplacement) {
        val draft = active.get() ?: return
        draft.journal = draft.journal + (replacement.date to replacement)
    }

    fun clearJournal(date: String) {
        val draft = active.get() ?: return
        draft.journal = draft.journal - date
    }

    suspend fun <T> write(block: suspend RoomDraft.() -> T): T {
        if (coroutineContext[WriteToken]?.store === this) {
            return checkNotNull(active.get()).block()
        }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val draft = publishedDraft()
                withContext(WriteToken(this@RoomStore) + active.asContextElement(draft)) {
                    val callerJob = coroutineContext[Job]!!
                    withContext(NonCancellable) {
                        val result = db.withTransaction {
                            withContext(callerJob) {
                                val value = draft.block()
                                draft.reconcileDayRevisions()
                                value
                            }
                        }
                        publish(draft)
                        draft.afterCommit.forEach { it() }
                        result
                    }
                }
            }
        }
    }

    private fun publishedDraft() = RoomDraft(
        db = db,
        exercises = _exercises.value,
        aliases = _aliases.value,
        pairs = _pairs.value,
        sets = _sets.value,
        cards = _cards.value,
        settings = _settings.value,
        days = _days.value,
        cycle = _cycle.value,
        slots = _slots.value,
        splitExercises = _splitExercises.value,
        archives = _archives.value,
        reviews = _reviews.value,
        cardio = _cardio.value,
        journal = _journal.value,
        alternatives = _alternatives.value,
    )

    private fun publish(draft: RoomDraft) {
        _exercises.value = draft.exercises.sortedBy { it.name.lowercase() }
        _aliases.value = draft.aliases.sortedBy { it.alias.lowercase() }
        _pairs.value = draft.pairs.sortedWith(compareBy({ it.exerciseIdA }, { it.exerciseIdB }))
        _sets.value = draft.sets
        _cards.value = draft.cards.sortedWith(compareBy({ it.date }, { it.position }))
        _settings.value = draft.settings
        _days.value = draft.days.sortedBy { it.date }
        _cycle.value = draft.cycle
        _slots.value = draft.slots.sortedWith(compareBy({ it.orderIndex }, { it.id }))
        _splitExercises.value = draft.splitExercises.sortedWith(compareBy({ it.splitId }, { it.orderIndex }, { it.id }))
        _archives.value = draft.archives.sortedWith(compareByDescending<ArchivedCycle> { it.endDate }.thenByDescending { it.id })
        _reviews.value = draft.reviews.sortedWith(compareByDescending<PendingReview> { it.dateLogged }.thenByDescending { it.id })
        _cardio.value = draft.cardio.sortedWith(
            compareByDescending<CardioSession> { it.date }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )
        _journal.value = draft.journal
        _alternatives.value = draft.alternatives
    }
}

class RoomDraft(
    val db: AppDatabase,
    var exercises: List<Exercise>,
    var aliases: List<ExerciseAlias>,
    var pairs: List<ExerciseDistinctPair>,
    var sets: List<WorkoutSet>,
    var cards: List<WorkoutExerciseCard>,
    var settings: UserSettings,
    var days: List<WorkoutDay>,
    var cycle: Cycle,
    var slots: List<CycleSlot>,
    var splitExercises: List<SplitExercise>,
    var archives: List<ArchivedCycle>,
    var reviews: List<PendingReview>,
    var cardio: List<CardioSession>,
    var journal: Map<String, PreparedSplitReplacement>,
    var alternatives: List<SplitAlternative>,
) {
    internal val afterCommit = mutableListOf<() -> Unit>()
    private var setsAtOpen: List<WorkoutSet> = sets
    private val bumpedDays = mutableSetOf<String>()
    suspend fun reloadAll() {
        exercises = db.exerciseDao().getAll().map { it.toDomain() }
        aliases = db.exerciseAliasDao().getAll().map { it.toDomain() }
        pairs = db.exerciseDistinctPairDao().getAll().map { it.toDomain() }
        sets = db.workoutSetDao().getAll().map { it.toDomain() }
        cards = db.workoutExerciseCardDao().getAll().map { it.toDomain() }
        days = db.workoutDayDao().getAll().map { it.toDomain() }
        cycle = db.cycleDao().getCycle()?.toDomain() ?: Cycle().also { db.cycleDao().upsert(it.toRoom()) }
        slots = db.cycleSlotDao().getAll().map { it.toDomain() }
        splitExercises = db.splitExerciseDao().getAll().map { it.toDomain() }
        archives = db.archivedCycleDao().getAll().map { it.toDomain() }
        reviews = db.pendingReviewDao().getAll().map { it.toDomain() }
        cardio = db.cardioSessionDao().getAll().map { it.toDomain() }
        alternatives = db.splitAlternativeDao().getAll().map { row ->
            SplitAlternative(row.splitId, row.sourceId, row.alternativeId, row.replacesSlot)
        }
        settings = readSettings()
        setsAtOpen = sets
    }

    suspend fun reloadRoutine() {
        cycle = db.cycleDao().getCycle()?.toDomain() ?: cycle
        slots = db.cycleSlotDao().getAll().map { it.toDomain() }
        splitExercises = db.splitExerciseDao().getAll().map { it.toDomain() }
        days = db.workoutDayDao().getAll().map { it.toDomain() }
        archives = db.archivedCycleDao().getAll().map { it.toDomain() }
    }

    private suspend fun readSettings(): UserSettings {
        val stored = db.userSettingsDao().get()?.toDomain()
        if (stored == null) {
            val created = UserSettings(timeZone = deviceTimeZone())
            db.userSettingsDao().upsert(created.toRoom())
            return created
        }
        if (!untouchedDefaults(stored)) return stored
        val zone = deviceTimeZone()
        if (zone == "UTC" || stored.timeZone != "UTC") return stored
        val updated = stored.copy(timeZone = zone)
        db.userSettingsDao().upsert(updated.toRoom())
        return updated
    }

    suspend fun upsertExercise(exercise: Exercise): Exercise {
        val id = db.exerciseDao().upsert(exercise.toRoom())
        val stored = exercise.copy(id = if (exercise.id == 0L) id else exercise.id)
        exercises = exercises.filterNot { it.id == stored.id } + stored
        return stored
    }

    suspend fun deleteExerciseRow(id: Long) {
        db.exerciseDao().deleteById(id)
        exercises = exercises.filterNot { it.id == id }
        aliases = aliases.filterNot { it.exerciseId == id }
        pairs = pairs.filterNot { it.exerciseIdA == id || it.exerciseIdB == id }
        sets = sets.filterNot { it.exerciseId == id }
        cards = cards.filterNot { it.exerciseId == id }
        splitExercises = splitExercises.filterNot { it.exerciseId == id }
    }

    suspend fun insertAlias(alias: ExerciseAlias): ExerciseAlias? {
        if (aliases.any { it.normalizedKey == alias.normalizedKey }) return null
        val id = db.exerciseAliasDao().insert(alias.copy(id = 0).toRoom())
        if (id == -1L) return null
        val stored = alias.copy(id = id)
        aliases = aliases + stored
        return stored
    }

    suspend fun insertPair(pair: ExerciseDistinctPair): ExerciseDistinctPair? {
        if (pairs.any { it.exerciseIdA == pair.exerciseIdA && it.exerciseIdB == pair.exerciseIdB }) return null
        val id = db.exerciseDistinctPairDao().insert(pair.copy(id = 0).toRoom())
        if (id == -1L) return null
        val stored = pair.copy(id = id)
        pairs = pairs + stored
        return stored
    }

    suspend fun insertSet(row: WorkoutSet): WorkoutSet {
        val prepared = row.copy(
            id = 0,
            syncId = row.syncId.ifBlank { newSyncId() },
            rowRevision = if (row.rowRevision > 0L) row.rowRevision else 1L,
        )
        val id = db.workoutSetDao().insert(prepared.toRoom())
        val stored = prepared.copy(id = id)
        sets = sets + stored
        return stored
    }

    /** Parks every row on a negative set number first so two final keys cannot collide mid-update. */
    suspend fun rewriteExistingSets(next: List<WorkoutSet>) {
        if (next.isEmpty()) return
        next.forEachIndexed { index, row ->
            val current = sets.first { it.id == row.id }
            db.workoutSetDao().update(current.copy(setNumber = -(index + 1)).toRoom())
        }
        next.forEach { row -> db.workoutSetDao().update(row.toRoom()) }
        val ids = next.map { it.id }.toSet()
        sets = sets.filterNot { it.id in ids } + next
    }

    suspend fun updateSetRow(row: WorkoutSet) {
        val current = sets.first { it.id == row.id }
        val next = row.copy(
            syncId = current.syncId.ifBlank { row.syncId.ifBlank { newSyncId() } },
            rowRevision = current.rowRevision + 1,
        )
        // A rewritten natural key can collide with a row this same batch has not moved yet.
        if (current.setNumber != next.setNumber || current.date != next.date || current.exerciseId != next.exerciseId) {
            db.workoutSetDao().update(next.copy(setNumber = -1).toRoom())
        }
        db.workoutSetDao().update(next.toRoom())
        sets = sets.map { if (it.id == next.id) next else it }
    }

    suspend fun saveRenumbered(group: List<WorkoutSet>) {
        val next = renumberSets(group).map { updated ->
            val current = sets.first { it.id == updated.id }
            if (current.setNumber == updated.setNumber) current else updated.copy(rowRevision = current.rowRevision + 1)
        }
        next.forEachIndexed { index, row ->
            val current = sets.first { it.id == row.id }
            if (current.setNumber != row.setNumber) {
                db.workoutSetDao().update(row.copy(setNumber = -(index + 1)).toRoom())
            }
        }
        next.forEach { row ->
            val current = sets.first { it.id == row.id }
            if (current != row) db.workoutSetDao().update(row.toRoom())
        }
        val ids = next.map { it.id }.toSet()
        sets = sets.filterNot { it.id in ids } + next
    }

    suspend fun removeSets(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { db.workoutSetDao().deleteById(it) }
        val drop = ids.toSet()
        sets = sets.filterNot { it.id in drop }
    }

    suspend fun bumpDay(date: String): Long {
        if (!bumpedDays.add(date)) return db.workoutDayStateDao().revision(date) ?: 0L
        val next = (db.workoutDayStateDao().revision(date) ?: 0L) + 1
        db.workoutDayStateDao().upsert(WorkoutDayState(date, next))
        return next
    }

    suspend fun reconcileDayRevisions() {
        val before = setsAtOpen.groupBy { it.date }
        val after = sets.groupBy { it.date }
        for (date in before.keys + after.keys) {
            val left = before[date].orEmpty().sortedBy { it.id }
            val right = after[date].orEmpty().sortedBy { it.id }
            if (left != right) bumpDay(date)
        }
    }

    // The unique date and position index is checked immediately, so cards move through a high position first.
    suspend fun rewriteDayCards(date: String, finalCards: List<WorkoutExerciseCard>) {
        val finalIds = finalCards.map { it.id }.toSet()
        deleteCards(cards.filter { it.date == date && it.id !in finalIds }.map { it.id })
        cards.filter { it.date == date }.forEachIndexed { index, card ->
            val parked = card.copy(position = PARKED_CARD_POSITION + index)
            db.workoutExerciseCardDao().update(parked.toRoom())
            cards = cards.map { if (it.id == parked.id) parked else it }
        }
        val present = cards.filter { it.date == date }.map { it.id }.toSet()
        finalCards.filter { it.id !in present }.forEachIndexed { index, card ->
            val parked = card.copy(position = NEW_CARD_POSITION + index)
            db.workoutExerciseCardDao().insert(parked.toRoom())
            cards = cards + parked
        }
        finalCards.forEach { card ->
            val current = cards.first { it.id == card.id }
            if (
                current.position == card.position &&
                current.displayMode == card.displayMode &&
                current.origin == card.origin &&
                current.sourcePlanId == card.sourcePlanId &&
                current.createdAt == card.createdAt &&
                current.updatedAt == card.updatedAt &&
                current.exerciseId == card.exerciseId &&
                current.rowRevision == card.rowRevision &&
                current.date == card.date
            ) {
                return@forEach
            }
            db.workoutExerciseCardDao().update(card.toRoom())
            cards = cards.map { if (it.id == card.id) card else it }
        }
    }

    suspend fun deleteSetRow(id: Long) {
        val existing = sets.firstOrNull { it.id == id } ?: return
        db.workoutSetDao().deleteById(id)
        sets = sets.filterNot { it.id == id }
        val survivors = sets.filter { it.date == existing.date && it.exerciseId == existing.exerciseId }
        if (survivors.isEmpty()) {
            deleteCards(cards.filter { it.date == existing.date && it.exerciseId == existing.exerciseId }.map { it.id })
        } else {
            saveRenumbered(survivors)
        }
    }

    suspend fun insertCard(card: WorkoutExerciseCard) {
        db.workoutExerciseCardDao().insert(card.toRoom())
        cards = cards + card
    }

    suspend fun updateCard(card: WorkoutExerciseCard) {
        val next = card.copy(rowRevision = card.rowRevision + 1)
        db.workoutExerciseCardDao().update(next.toRoom())
        cards = cards.map { if (it.id == next.id) next else it }
    }

    suspend fun deleteCards(ids: Collection<String>) {
        if (ids.isEmpty()) return
        ids.forEach { db.workoutExerciseCardDao().deleteById(it) }
        val drop = ids.toSet()
        cards = cards.filterNot { it.id in drop }
    }

    suspend fun upsertDay(day: WorkoutDay) {
        db.workoutDayDao().upsert(day.toRoom())
        days = days.filterNot { it.date == day.date } + day
    }

    suspend fun deleteDay(date: String) {
        db.workoutDayDao().deleteByDate(date)
        days = days.filterNot { it.date == date }
    }

    suspend fun upsertCycle(next: Cycle) {
        db.cycleDao().upsert(next.toRoom())
        cycle = next
    }

    suspend fun upsertSlot(slot: CycleSlot): CycleSlot {
        val id = db.cycleSlotDao().upsert(slot.toRoom())
        val stored = slot.copy(id = if (slot.id == 0L) id else slot.id)
        slots = slots.filterNot { it.id == stored.id } + stored
        return stored
    }

    suspend fun deleteSlot(id: Long) {
        db.cycleSlotDao().delete(id)
        slots = slots.filterNot { it.id == id }
        splitExercises = splitExercises.filterNot { it.splitId == id }
    }

    suspend fun markSplitMemberUserAdded(id: Long) {
        db.splitExerciseDao().markUserAdded(id)
        splitExercises = splitExercises.map { if (it.id == id) it.copy(userAdded = true) else it }
    }

    suspend fun insertSplitMember(row: SplitExercise): SplitExercise {
        val id = db.splitExerciseDao().insert(row.copy(id = 0).toRoom())
        val stored = row.copy(id = id)
        splitExercises = splitExercises + stored
        return stored
    }

    suspend fun replaceSplitMembers(slotId: Long, rows: List<SplitExercise>) {
        db.splitExerciseDao().deleteForSplit(slotId)
        if (rows.isNotEmpty()) {
            val inserted = rows.map { row ->
                val id = db.splitExerciseDao().insert(row.copy(id = 0).toRoom())
                row.copy(id = id)
            }
            splitExercises = splitExercises.filterNot { it.splitId == slotId } + inserted
        } else {
            splitExercises = splitExercises.filterNot { it.splitId == slotId }
        }
    }

    suspend fun upsertSettings(next: UserSettings) {
        db.userSettingsDao().upsert(next.toRoom())
        settings = next
    }

    suspend fun insertCardio(session: CardioSession): CardioSession {
        val id = db.cardioSessionDao().insert(session.copy(id = 0).toRoom())
        val stored = session.copy(id = id)
        cardio = cardio + stored
        return stored
    }

    suspend fun updateCardio(session: CardioSession): CardioSession {
        db.cardioSessionDao().update(session.toRoom())
        cardio = cardio.map { if (it.id == session.id) session else it }
        return session
    }

    suspend fun deleteCardio(id: Long) {
        db.cardioSessionDao().deleteById(id)
        cardio = cardio.filterNot { it.id == id }
    }

    suspend fun insertReview(review: PendingReview): PendingReview {
        val id = db.pendingReviewDao().insert(review.copy(id = 0).toRoom())
        val stored = review.copy(id = id)
        reviews = reviews + stored
        return stored
    }

    suspend fun markReviewResolved(id: Long) {
        db.pendingReviewDao().markResolved(id)
        reviews = reviews.map { if (it.id == id) it.copy(resolved = true) else it }
    }

    suspend fun insertArchive(archive: ArchivedCycle): ArchivedCycle {
        val id = db.archivedCycleDao().insert(archive.copy(id = 0).toRoom())
        val stored = archive.copy(id = id)
        archives = archives + stored
        return stored
    }

    suspend fun deleteArchive(id: Long) {
        db.archivedCycleDao().deleteById(id)
        archives = archives.filterNot { it.id == id }
    }
}

private fun untouchedDefaults(settings: UserSettings): Boolean =
    settings.userBodyweightLbs == null &&
        settings.glassPaletteChoice == null &&
        settings.timeZone == "UTC" &&
        settings.unitSystem == com.ayman.ecolift.data.UnitSystem.LB &&
        settings.smartSuggest

fun deviceTimeZone(): String =
    runCatching { ZoneId.systemDefault().id }.getOrNull()?.takeIf { it.isNotBlank() } ?: "UTC"

private const val PARKED_CARD_POSITION = 1_000_000
private const val NEW_CARD_POSITION = 2_000_000

private class WriteToken(val store: RoomStore) : AbstractCoroutineContextElement(WriteToken) {
    companion object : Key<WriteToken>
}
