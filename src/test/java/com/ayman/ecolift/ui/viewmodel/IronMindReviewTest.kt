package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.agent.AgentMutationRepos
import com.ayman.ecolift.agent.FakeReadyEngine
import com.ayman.ecolift.android.room.PendingReviewResolver
import com.ayman.ecolift.android.room.PendingReviewSet
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.supabase.PendingReviewRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IronMindReviewTest {
    @Test
    fun savedReviewsAppearOnTheDeskWithoutSessionMessages() = runVmTest {
        val h = ReviewHarness()
        val vm = HubViewModel(h.repos, Dispatchers.Main)
        val desk = withTimeout(2_000) { vm.desk.first { it.pendingReviews.isNotEmpty() } }

        assertEquals(listOf(h.saved), desk.pendingReviews)
        assertEquals(emptyList(), desk.asked)
        assertEquals(0, desk.decisionCount)
    }

    @Test
    fun dismissOnlyResolvesTheSelectedSavedReview() = runVmTest {
        val h = ReviewHarness()
        val other = PendingReview(9, "keep this draft", "2026-10-19")
        h.reviews.value += other
        val vm = OrchestratorViewModel(h.repos, FakeReadyEngine())

        vm.dismissSavedReview(7)

        assertEquals(listOf(other), h.reviews.value)
        assertEquals(emptyList(), h.data.currentSets.value)
        assertNull(vm.reviewError.value)
    }

    @Test
    fun resolvePassesExplicitSetValuesAndTheSelectedReviewId() = runVmTest {
        val h = ReviewHarness()
        val vm = OrchestratorViewModel(h.repos, FakeReadyEngine())

        vm.resolveSavedReview(7, PendingReviewSet(1, 1355, 12))

        assertEquals(listOf(7L to PendingReviewSet(1, 1355, 12)), h.resolutions)
        assertEquals(emptyList(), h.reviews.value)
        assertNull(vm.reviewError.value)
    }

    @Test
    fun failureLeavesTheSavedReviewAvailableAndReportsAnError() = runVmTest {
        val h = ReviewHarness()
        h.failWrites = true
        val vm = OrchestratorViewModel(h.repos, FakeReadyEngine())

        vm.resolveSavedReview(7, PendingReviewSet(1, 1350, 12))
        assertEquals("Couldn't save your answer. Try again.", vm.reviewError.value)
        assertEquals(listOf(h.saved), h.reviews.value)

        vm.dismissSavedReview(7)
        assertEquals("Couldn't save your answer. Try again.", vm.reviewError.value)
        assertEquals(listOf(h.saved), h.reviews.value)
    }

    @Test
    fun repeatedResolveWhileSavingSubmitsOnlyOneSet() = runVmTest {
        val h = ReviewHarness()
        h.resolveGate = CompletableDeferred()
        val vm = OrchestratorViewModel(h.repos, FakeReadyEngine())
        val set = PendingReviewSet(1, 1350, 12)

        vm.resolveSavedReview(7, set)
        vm.resolveSavedReview(7, set)
        vm.dismissSavedReview(7)
        assertEquals(listOf(h.saved), h.reviews.value)
        h.resolveGate?.complete(Unit)

        assertEquals(listOf(7L to set), h.resolutions)
        assertEquals(emptyList(), h.reviews.value)
    }

    @Test
    fun explicitSetInputRejectsInvalidNumbersAndPreservesDecimalPounds() {
        val exercise = Exercise(1, "Bench Press", "Chest")
        assertEquals(PendingReviewSet(1, 1355, 12), pendingReviewSet(exercise, "135.5", "12"))
        assertEquals(PendingReviewSet(1, null, 12), pendingReviewSet(exercise.copy(isBodyweight = true), "", "12"))
        listOf("0", "-135", "999999999999", "NaN", "Infinity", "135 lb", "0.01").forEach {
            assertNull(pendingReviewSet(exercise, it, "12"))
        }
        assertNull(pendingReviewSet(exercise, "135", "0"))
        assertNull(pendingReviewSet(exercise, "135", "101"))
        assertNull(pendingReviewSet(null, "135", "12"))
    }
}

private class ReviewHarness {
    val data = AgentMutationRepos(listOf(Exercise(1, "Bench Press", "Chest")))
    val saved = PendingReview(7, "log leg thing three plates for twelve", "2026-10-20")
    val reviews = MutableStateFlow(listOf(saved))
    var failWrites = false
    var resolveGate: CompletableDeferred<Unit>? = null
    val resolutions = mutableListOf<Pair<Long, PendingReviewSet>>()
    val repos = object : AppRepos by data {
        override val pendingReviews = object : PendingReviewRepository, PendingReviewResolver {
            override val unresolved = reviews
            override suspend fun add(rawInput: String, dateLogged: String) {
                reviews.value += PendingReview(8, rawInput, dateLogged)
            }
            override suspend fun markResolved(id: Long) {
                check(!failWrites)
                reviews.value = reviews.value.filterNot { it.id == id }
            }
            override suspend fun resolve(id: Long, set: PendingReviewSet): Boolean {
                resolveGate?.await()
                check(!failWrites)
                resolutions += id to set
                markResolved(id)
                return true
            }
        }
    }
}
