package com.ayman.ecolift.android.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.effectiveSplitMembers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

@RunWith(AndroidJUnit4::class)
class RoomSplitMembersTest {
    @Test
    fun delayed_save_confirmation_cannot_replace_a_fully_reloaded_older_board() = runBlocking {
        withRepository { fixture ->
            val slot = fixture.repo.addCycleSlot("Restored manual")
            val restored = EffectiveSplitMembers.of(listOf(1, 2), listOf(3))
            fixture.repo.saveSplitMembers(slot, restored)
            val resumes = LinkedBlockingQueue<Runnable>()
            val paused = object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    resumes.put(block)
                }
            }
            val save = launch(paused, start = CoroutineStart.UNDISPATCHED) {
                fixture.repo.saveSplitMembers(slot, EffectiveSplitMembers.of(listOf(3, 2), listOf(1)))
            }
            val resume = withContext(Dispatchers.IO) { checkNotNull(resumes.poll(5, TimeUnit.SECONDS)) }
            try {
                assertEquals(2L, fixture.store.db.splitMemberChoiceDao().getBySplitId(slot)!!.revision)
                fixture.store.write {
                    db.splitMemberChoiceDao().upsert(SplitMemberChoice(slot, 1, restored).toRoomChoice())
                    reloadAll()
                }
            } finally {
                resume.run()
                save.join()
            }
            assertEquals(restored, fixture.board(slot))
            assertEquals(1L, fixture.choices().confirmed.getValue(slot).revision)
        }
    }

    @Test
    fun rapid_edits_show_the_latest_board_then_commit_in_order_without_changing_anchors() = runBlocking {
        withRepository { fixture ->
            val slotId = fixture.repo.upsertLearnedSlot(null, "Lower", true, listOf(1, 2, 3), 10)
            fixture.choices()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocker = launch { fixture.store.write { entered.complete(Unit); release.await() } }
            entered.await()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.repo.saveSplitMembers(slotId, EffectiveSplitMembers.of(listOf(3, 1), listOf(2)))
            }
            val second = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.repo.saveSplitMembers(slotId, EffectiveSplitMembers.of(listOf(3, 1, 4), listOf(2)))
            }
            try {
                val pending = fixture.repo.splitMemberChoices.value as SplitChoicesState.Ready
                assertEquals(setOf(slotId), pending.pendingSlots)
                assertEquals(listOf(3L, 1L, 4L), pending.choices.getValue(slotId).board.inSplit)
            } finally {
                release.complete(Unit)
            }
            joinAll(blocker, first, second)
            val saved = fixture.choices()
            assertEquals(emptySet<Long>(), saved.pendingSlots)
            assertEquals(2L, saved.confirmed.getValue(slotId).revision)
            assertEquals(listOf(1L, 2L, 3L), fixture.store.splitExercisesNow().map { it.exerciseId })
            assertEquals(listOf(3L, 1L, 4L), fixture.restart().board(slotId).inSplit)
        }
    }

    @Test
    fun learned_refresh_keeps_the_users_order_exclusions_and_added_lifts() = runBlocking {
        withRepository { fixture ->
            val slotId = fixture.repo.upsertLearnedSlot(null, "Lower", true, listOf(1, 2, 3), 10)
            fixture.repo.saveSplitMembers(slotId, EffectiveSplitMembers.of(listOf(3, 1, 4), listOf(2)))
            fixture.repo.upsertLearnedSlot(slotId, null, true, listOf(1, 2, 3, 5), 20)
            fixture.repo.refreshSlots()

            val restarted = fixture.restart()
            val board = restarted.board(slotId)
            assertEquals(listOf(3L, 1L, 4L), board.inSplit)
            assertEquals(listOf(2L), board.otherOptions)
            val shown = effectiveSplitMembers(
                slotId, null, restarted.store.splitExercisesNow(), restarted.choices().choices[slotId], { it },
            )
            assertEquals(listOf(3L, 1L, 4L), shown.inSplit)
            assertEquals(listOf(2L, 5L), shown.otherOptions)
        }
    }

    @Test
    fun manual_board_keeps_other_options_and_order_after_repository_restart() = runBlocking {
        withRepository { fixture ->
            val slotId = fixture.repo.addCycleSlot("Manual")
            fixture.repo.appendSplitExercise(slotId, 1)
            fixture.repo.appendSplitExercise(slotId, 2)
            fixture.repo.appendSplitExercise(slotId, 3)
            fixture.repo.saveSplitMembers(slotId, EffectiveSplitMembers.of(listOf(3, 1, 4), listOf(2)))

            val board = fixture.restart().board(slotId)
            assertEquals(listOf(3L, 1L, 4L), board.inSplit)
            assertEquals(listOf(2L), board.otherOptions)
        }
    }

    @Test
    fun deliberately_empty_board_stays_empty_after_learning_and_restart() = runBlocking {
        withRepository { fixture ->
            val slotId = fixture.repo.upsertLearnedSlot(null, "Lower", true, listOf(1, 2), 10)
            fixture.repo.saveSplitMembers(slotId, EffectiveSplitMembers.of(emptyList(), listOf(2, 1)))
            fixture.repo.upsertLearnedSlot(slotId, null, true, listOf(1, 2, 3), 20)

            val board = fixture.restart().board(slotId)
            assertEquals(emptyList<Long>(), board.inSplit)
            assertEquals(listOf(2L, 1L), board.otherOptions)
        }
    }

    private suspend fun withRepository(block: suspend (Fixture) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = Fixture(db, scope)
            fixture.store.write {
                reloadAll()
                (1L..5L).forEach { upsertExercise(Exercise(id = it, name = "Lift $it")) }
            }
            block(fixture)
        } finally {
            scope.cancel()
            db.close()
        }
    }

    private class Fixture(private val db: AppDatabase, private val scope: CoroutineScope) {
        val store = RoomStore(db)
        val repo = RoomWorkoutRepository(
            store, RoomSetRepository(store), RoomExerciseRepository(store), RoomUserSettingsRepository(store) {}, scope,
        )

        suspend fun restart(): Fixture = Fixture(db, scope).also { it.store.write { reloadAll() } }

        suspend fun choices(): SplitChoicesState.Ready = withTimeout(5_000) {
            repo.splitMemberChoices.first { it is SplitChoicesState.Ready } as SplitChoicesState.Ready
        }

        suspend fun board(slotId: Long): EffectiveSplitMembers = withTimeout(5_000) {
            val state = repo.splitMemberChoices.first { it is SplitChoicesState.Ready && slotId in it.choices }
            checkNotNull((state as SplitChoicesState.Ready).choices[slotId]).board
        }
    }
}
