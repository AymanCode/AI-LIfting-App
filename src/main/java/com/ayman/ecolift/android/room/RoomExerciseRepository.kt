package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseAlias
import com.ayman.ecolift.data.ExerciseDistinctPair
import com.ayman.ecolift.data.ExerciseMatch
import com.ayman.ecolift.data.ExerciseMatchInput
import com.ayman.ecolift.data.ExerciseMatcher
import com.ayman.ecolift.data.ExerciseNames
import com.ayman.ecolift.data.ExerciseSuggestion
import com.ayman.ecolift.data.FuzzyMatcher
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.looksLikeBodyweightExerciseName
import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.data.room.toRoom
import com.ayman.ecolift.web.supabase.ExerciseMergePreparationResult
import com.ayman.ecolift.web.supabase.ExerciseMergePreview
import com.ayman.ecolift.web.supabase.ExerciseMergeResult
import com.ayman.ecolift.web.supabase.ExerciseRenameResult
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.simpleNameKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RoomExerciseRepository(
    private val store: RoomStore,
) : ExerciseRepository {
    private val visible = MutableStateFlow<List<Exercise>>(emptyList())

    @Volatile
    private var canonicalSnapshot: Pair<List<Exercise>, Map<Long, Exercise>>? = null

    override val exercises: StateFlow<List<Exercise>> = visible

    override fun observeAliases(): StateFlow<List<ExerciseAlias>> = store.aliases

    internal fun publishVisible() {
        visible.value = store.exercises.value.filter { it.mergedIntoId == null }
    }

    override suspend fun refresh() {
        store.write {
            exercises = db.exerciseDao().getAll().map { it.toDomain() }
            aliases = db.exerciseAliasDao().getAll().map { it.toDomain() }
            pairs = db.exerciseDistinctPairDao().getAll().map { it.toDomain() }
        }
        publishVisible()
    }

    override suspend fun getAll(): List<Exercise> = visibleNow()

    override fun mergeGraph(): Map<Long, Long?> =
        store.exercisesNow().associate { it.id to it.mergedIntoId }

    override fun canonicalId(id: Long): Long {
        val rows = store.exercisesNow()
        val byId = canonicalSnapshot?.takeIf { it.first === rows }?.second
            ?: rows.associateBy { it.id }.also { canonicalSnapshot = rows to it }
        var current = id
        repeat(16) {
            val next = byId[current]?.mergedIntoId ?: return current
            current = next
        }
        if (byId[current]?.mergedIntoId != null) {
            throw IllegalStateException("Exercise $id has a cyclic or overlong merge chain")
        }
        return current
    }

    override suspend fun getById(id: Long): Exercise? =
        visibleNow().firstOrNull { it.id == canonicalId(id) }

    override suspend fun getByIds(ids: Collection<Long>): List<Exercise> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.map(::canonicalId).toSet()
        return visibleNow().filter { it.id in wanted }
    }

    override suspend fun findExact(name: String): Exercise? {
        val key = simpleNameKey(name)
        return visibleNow().firstOrNull { simpleNameKey(it.name) == key }
    }

    override suspend fun getOrCreate(name: String): Exercise {
        val normalized = ExerciseNames.normalize(name)
        val key = simpleNameKey(normalized)
        findExact(normalized)?.let { return it }
        store.exercisesNow().firstOrNull { simpleNameKey(it.name) == key }?.let { merged ->
            return getById(merged.id) ?: merged
        }
        return store.write {
            val stored = upsertExercise(
                Exercise(
                    name = normalized,
                    isBodyweight = looksLikeBodyweightExerciseName(normalized),
                ),
            )
            stored
        }.also { publishVisible() }
    }

    override suspend fun suggestions(query: String, maxDistance: Int, limit: Int): List<ExerciseSuggestion> {
        val normalizedQuery = normalizeName(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        return visibleNow()
            .filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            .map { exercise ->
                ExerciseSuggestion(
                    exercise = exercise,
                    distance = FuzzyMatcher.levenshteinDistance(
                        normalizedQuery.lowercase(),
                        exercise.name.lowercase(),
                    ),
                )
            }
            .filter { it.distance <= maxDistance }
            .sortedWith(compareBy<ExerciseSuggestion> { it.distance }.thenBy { it.exercise.name })
            .take(limit)
    }

    override suspend fun getPredictive(query: String, limit: Int): List<Exercise> {
        if (query.isBlank()) return emptyList()
        val visibleRows = visibleNow()
        val byId = visibleRows.associateBy { it.id }
        val counts = com.ayman.ecolift.data.SetQueries.usageCounts(store.setsNow())
        val aliasesByExercise = store.aliasesNow().groupBy { it.exerciseId }
        return ExerciseMatcher.rank(
            visibleRows.map { exercise ->
                ExerciseMatchInput(
                    exercise = exercise,
                    aliases = aliasesByExercise[exercise.id].orEmpty().map { it.alias },
                    usageCount = counts[exercise.id] ?: 0,
                )
            },
            query.trim(),
            limit,
        ).mapNotNull { byId[it.exerciseId] }
    }

    override suspend fun duplicateCandidate(exerciseId: Long): ExerciseMatch? {
        val inputs = matchInputs()
        val target = inputs.firstOrNull { it.exercise.id == canonicalId(exerciseId) } ?: return null
        return ExerciseMatcher.duplicateCandidate(target, inputs)
    }

    override suspend fun duplicateCandidate(name: String): ExerciseMatch? =
        ExerciseMatcher.duplicateCandidate(
            target = ExerciseMatchInput(Exercise(id = Long.MIN_VALUE, name = normalizeName(name))),
            others = matchInputs(),
        )

    override fun normalizeName(name: String): String = ExerciseNames.normalize(name)

    override suspend fun updateName(id: Long, newName: String): ExerciseRenameResult {
        val canonical = canonicalId(id)
        val current = store.exercisesNow().firstOrNull { it.id == canonical }
            ?: return ExerciseRenameResult.Success(Exercise(id = canonical, name = normalizeName(newName)))
        val normalized = ExerciseNames.normalize(newName)
        val key = simpleNameKey(normalized)
        val collision = store.exercisesNow().firstOrNull { it.id != canonical && simpleNameKey(it.name) == key }
        if (collision != null) {
            val existing = getById(collision.id) ?: collision
            return ExerciseRenameResult.NameCollision(existing)
        }
        val previous = current.name
        val updated = store.write {
            val stored = upsertExercise(current.copy(name = normalized))
            val aliasKey = ExerciseMatcher.matchKey(previous)
            if (aliasKey.isNotEmpty() && simpleNameKey(previous) != key) {
                insertAlias(
                    ExerciseAlias(
                        exerciseId = stored.id,
                        alias = previous,
                        normalizedKey = aliasKey,
                    ),
                )
            }
            stored
        }
        publishVisible()
        return ExerciseRenameResult.Success(updated)
    }

    override suspend fun updateMuscleGroups(id: Long, muscleGroups: String) {
        val current = store.exercisesNow().firstOrNull { it.id == canonicalId(id) } ?: return
        store.write { upsertExercise(current.copy(muscleGroups = muscleGroups)) }
        publishVisible()
    }

    override suspend fun deleteExercise(id: Long) {
        val target = canonicalId(id)
        store.write { deleteExerciseRow(target) }
        publishVisible()
    }

    override suspend fun loadDistinctPairs(): List<ExerciseDistinctPair> = store.pairsNow()

    override suspend fun recordAlias(exerciseId: Long, alias: String) {
        val canonical = canonicalId(exerciseId)
        val raw = alias.trim()
        val key = ExerciseMatcher.matchKey(raw)
        if (raw.isEmpty() || key.isEmpty()) return
        store.write {
            insertAlias(ExerciseAlias(exerciseId = canonical, alias = raw, normalizedKey = key))
        }
    }

    override suspend fun recordDistinctPair(idA: Long, idB: Long) {
        val ordered = orderDistinctPair(canonicalId(idA), canonicalId(idB)) ?: return
        store.write {
            insertPair(
                ExerciseDistinctPair(
                    exerciseIdA = ordered.first,
                    exerciseIdB = ordered.second,
                ),
            )
        }
    }

    override suspend fun prepareMerge(idA: Long, idB: Long): ExerciseMergePreparationResult {
        val a = exerciseOrThrow(canonicalId(idA))
        val b = exerciseOrThrow(canonicalId(idB))
        require(a.id != b.id) { "Cannot merge an exercise with itself" }
        val keeper = chooseMergeKeeper(a, b, store.setsNow())
        return ExerciseMergePreparationResult.Ready(
            ExerciseMergePreview(exerciseIdA = a.id, exerciseIdB = b.id, expectedKeeperId = keeper),
        )
    }

    override suspend fun mergeExercises(
        idA: Long,
        idB: Long,
        expectedKeeperId: Long,
        alias: String?,
    ): ExerciseMergeResult {
        val a = exerciseOrThrow(canonicalId(idA))
        val b = exerciseOrThrow(canonicalId(idB))
        require(a.id != b.id) { "Cannot merge an exercise with itself" }
        val expected = canonicalId(expectedKeeperId)
        val keeperId = chooseMergeKeeper(a, b, store.setsNow())
        if (keeperId != expected) return ExerciseMergeResult.KeeperDrift
        val loser = if (keeperId == a.id) b else a
        store.write { applyMerge(keeperId, loser, alias) }
        publishVisible()
        return ExerciseMergeResult.Success(keeperId)
    }

    private suspend fun RoomDraft.applyMerge(keeperId: Long, loser: Exercise, alias: String?) {
        val keeper = exercises.first { it.id == keeperId }
        exercises = exercises.map { row ->
            when (row.id) {
                loser.id -> row.copy(mergedIntoId = keeperId)
                else -> row
            }
        }
        db.exerciseDao().upsert(exercises.first { it.id == loser.id }.toRoom())
        moveSetsAndCards(loser.id, keeperId)
        moveSplitMembers(loser.id, keeperId)
        val loserKey = ExerciseMatcher.matchKey(loser.name)
        if (loserKey.isNotEmpty() && simpleNameKey(loser.name) != simpleNameKey(keeper.name)) {
            insertAlias(ExerciseAlias(exerciseId = keeperId, alias = loser.name, normalizedKey = loserKey))
        }
        val passed = alias?.trim()?.takeIf { it.isNotEmpty() }
        if (passed != null) {
            val key = ExerciseMatcher.matchKey(passed)
            if (key.isNotEmpty()) {
                insertAlias(ExerciseAlias(exerciseId = keeperId, alias = passed, normalizedKey = key))
            }
        }
    }

    private suspend fun RoomDraft.moveSetsAndCards(loserId: Long, keeperId: Long) {
        val moving = sets.filter { it.exerciseId == loserId }
        val keeperSets = sets.filter { it.exerciseId == keeperId }
        val dates = (moving.map { it.date } + keeperSets.map { it.date }).toSet()
        val rewritten = mutableListOf<WorkoutSet>()
        dates.forEach { date ->
            val combined = sets.filter { it.date == date && (it.exerciseId == loserId || it.exerciseId == keeperId) }
            if (combined.isEmpty()) return@forEach
            val card = ensureKeeperCard(date, keeperId, loserId)
            val ranked = renumberSets(combined.map { it.copy(exerciseId = keeperId, cardId = card.id) })
            rewritten += ranked.map { row ->
                val previous = sets.first { it.id == row.id }
                row.copy(rowRevision = previous.rowRevision + 1)
            }
        }
        if (rewritten.isNotEmpty()) rewriteExistingSets(rewritten)
        val dropCards = cards.filter { it.exerciseId == loserId }.map { it.id }
        deleteCards(dropCards)
    }

    private suspend fun RoomDraft.ensureKeeperCard(date: String, keeperId: Long, loserId: Long): WorkoutExerciseCard {
        cards.firstOrNull { it.date == date && it.exerciseId == keeperId }?.let { return it }
        val loserCard = cards.firstOrNull { it.date == date && it.exerciseId == loserId }
        if (loserCard != null) {
            val moved = loserCard.copy(exerciseId = keeperId, rowRevision = loserCard.rowRevision + 1)
            db.workoutExerciseCardDao().update(moved.toRoom())
            cards = cards.map { if (it.id == moved.id) moved else it }
            return moved
        }
        val position = (cards.filter { it.date == date }.maxOfOrNull { it.position } ?: -1) + 1
        val created = WorkoutExerciseCard(
            id = newCardId(),
            date = date,
            exerciseId = keeperId,
            position = position,
            displayMode = com.ayman.ecolift.data.WorkoutCardDisplayMode.EXPANDED,
            origin = WorkoutCardOrigin.MANUAL,
        )
        insertCard(created)
        return created
    }

    private suspend fun RoomDraft.moveSplitMembers(loserId: Long, keeperId: Long) {
        val touched = splitExercises.filter { it.exerciseId == loserId || it.exerciseId == keeperId }
            .map { it.splitId }
            .toSet()
        touched.forEach { slotId ->
            val rows = splitExercises.filter { it.splitId == slotId }
            val seen = mutableSetOf<Long>()
            val kept = rows.mapNotNull { row ->
                val exerciseId = if (row.exerciseId == loserId) keeperId else row.exerciseId
                if (!seen.add(exerciseId)) null else row.copy(exerciseId = exerciseId)
            }
            replaceSplitMembers(slotId, kept.mapIndexed { index, row -> row.copy(id = 0, orderIndex = index) })
        }
    }

    private fun visibleNow(): List<Exercise> =
        store.exercisesNow().filter { it.mergedIntoId == null }.sortedBy { it.name.lowercase() }

    private fun exerciseOrThrow(id: Long): Exercise =
        store.exercisesNow().firstOrNull { it.id == id && it.mergedIntoId == null }
            ?: throw IllegalArgumentException("Exercise $id was not found")

    private fun matchInputs(): List<ExerciseMatchInput> {
        val counts = com.ayman.ecolift.data.SetQueries.usageCounts(store.setsNow())
        val aliasesByExercise = store.aliasesNow().groupBy { it.exerciseId }
        return visibleNow().map { exercise ->
            ExerciseMatchInput(
                exercise = exercise,
                aliases = aliasesByExercise[exercise.id].orEmpty().map { it.alias },
                usageCount = counts[exercise.id] ?: 0,
            )
        }
    }
}
