package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.supabase.PendingReviewRepository
import com.ayman.ecolift.web.supabase.sync.orThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

data class PendingReviewSet(val exerciseId: Long, val weightLbs: Int?, val reps: Int)

interface PendingReviewResolver {
    suspend fun resolve(id: Long, set: PendingReviewSet): Boolean
}

class RoomPendingReviewRepository(
    private val store: RoomStore,
) : PendingReviewRepository, PendingReviewResolver {
    private val setRepository = RoomSetRepository(store)
    override val unresolved: Flow<List<PendingReview>> =
        store.reviews.map { rows -> rows.filterNot { it.resolved } }

    override suspend fun add(rawInput: String, dateLogged: String) {
        store.write {
            insertReview(PendingReview(rawInput = rawInput, dateLogged = dateLogged))
        }
    }

    override suspend fun markResolved(id: Long) {
        store.write { markReviewResolved(id) }
    }

    override suspend fun resolve(id: Long, set: PendingReviewSet): Boolean = store.write {
        val review = reviews.firstOrNull { it.id == id && !it.resolved } ?: return@write false
        val exercise = exercises.first { it.id == set.exerciseId && it.mergedIntoId == null }
        LocalDate.parse(review.dateLogged)
        require(set.reps in 1..100)
        require(set.weightLbs == null && exercise.isBodyweight || set.weightLbs != null && set.weightLbs > 0)
        setRepository.insert(
            WorkoutSet(
                exerciseId = exercise.id,
                date = review.dateLogged,
                setNumber = SetQueries.nextSetNumber(sets.filter { it.exerciseId == exercise.id && it.date == review.dateLogged }),
                weightLbs = set.weightLbs,
                reps = set.reps,
                isBodyweight = exercise.isBodyweight,
                completed = true,
            ),
        ).orThrow()
        markReviewResolved(id)
        true
    }
}
