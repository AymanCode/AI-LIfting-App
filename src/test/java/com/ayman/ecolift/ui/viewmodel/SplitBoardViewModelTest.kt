package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.SplitMemberEdit
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.ui.navigation.AddLiftDraft
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.SplitChoiceBook
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.WorkoutRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitBoardViewModelTest {
    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    private class Harness(fail: Boolean = false) {
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 7, 5)))
        val saved = mutableListOf<Pair<Long, EffectiveSplitMembers>>()
        private val recording = object : WorkoutRepository by base.workout {
            override suspend fun saveSplitMembers(slotId: Long, board: EffectiveSplitMembers) {
                if (fail) error("network down")
                saved += slotId to board
            }
        }
        val viewModel = SplitViewModel(object : AppRepos by base { override val workout: WorkoutRepository = recording })

        suspend fun card(): SplitGroupCardUi = withTimeout(2_000) {
            viewModel.uiState.first { state -> state.boardsReady && state.cards.any { it.isSaved && it.members.size >= 2 } }
        }.cards.first { it.isSaved && it.members.size >= 2 }
    }

    @Test
    fun an_edit_saves_the_whole_board_on_screen() = runVmTest {
        val harness = Harness()
        val card = harness.card()
        val ids = card.members.map { it.exerciseId }
        val options = card.otherOptions.map { it.exerciseId }

        harness.viewModel.editMembers(card.slotId, SplitMemberEdit.Exclude(ids.first()))
        harness.viewModel.editMembers(card.slotId, SplitMemberEdit.Exclude(ids.last()))

        assertEquals(card.slotId to EffectiveSplitMembers.of(ids.drop(1), options + ids.first()), harness.saved.first())
        assertEquals(2, harness.saved.size)
    }

    @Test
    fun a_second_quick_edit_builds_on_the_first_while_its_save_is_out() = runVmTest {
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 7, 5)))
        val gate = CompletableDeferred<Unit>()
        val saved = mutableListOf<EffectiveSplitMembers>()
        val staging = object : WorkoutRepository by base.workout {
            val book = SplitChoiceBook().also { it.loaded(emptyList()) }
            override val splitMemberChoices: StateFlow<SplitChoicesState> = book.state
            override suspend fun saveSplitMembers(slotId: Long, board: EffectiveSplitMembers) {
                val token = book.stage(slotId, board)
                saved += board
                if (saved.size == 1) gate.await()
                book.confirm(token, SplitMemberChoice(slotId, saved.size.toLong(), board))
            }
        }
        val viewModel = SplitViewModel(object : AppRepos by base { override val workout: WorkoutRepository = staging })
        val card = withTimeout(2_000) {
            viewModel.uiState.first { state -> state.boardsReady && state.cards.any { it.isSaved && it.members.size >= 2 } }
        }.cards.first { it.isSaved && it.members.size >= 2 }
        val ids = card.members.map { it.exerciseId }
        val options = card.otherOptions.map { it.exerciseId }

        viewModel.editMembers(card.slotId, SplitMemberEdit.Exclude(ids[0]))
        viewModel.editMembers(card.slotId, SplitMemberEdit.Exclude(ids[1]))

        assertEquals(EffectiveSplitMembers.of(ids.drop(2), options + ids[0] + ids[1]), saved[1])
        gate.complete(Unit)
    }

    @Test
    fun a_saved_name_keeps_a_newer_draft() {
        val draft = AddLiftDraft()
        draft.text = "Cable Fly"
        val submitted = draft.submit()!!
        draft.text = "Squat"
        draft.saved(submitted)
        assertEquals("Squat", draft.text)
    }

    @Test
    fun a_saved_name_clears_its_own_draft() {
        val draft = AddLiftDraft()
        draft.text = "Cable Fly"
        draft.saved(draft.submit()!!)
        assertEquals("", draft.text)
    }

    @Test
    fun a_failed_save_says_so() = runVmTest {
        val harness = Harness(fail = true)
        val card = harness.card()
        val failure = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        harness.viewModel.editMembers(card.slotId, SplitMemberEdit.Exclude(card.members.first().exerciseId))
        assertEquals("Couldn't save the split.", failure.await().message)
    }
}
