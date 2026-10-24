package com.ayman.ecolift.android.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.room.UserSettings
import com.ayman.ecolift.web.DataGeneration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearnedSmartSuggestRestoreTest {
    @Test
    fun a_restored_smart_suggest_value_reaches_the_running_learner() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope, DataGeneration())
        try {
            db.userSettingsDao().upsert(UserSettings(smartSuggest = false))
            repos.loadAll()
            assertTrue(
                "the first load did not install Smart suggest off",
                withTimeoutOrNull(20_000) { repos.learnedSplits.model.first { !it.smartSuggest } } != null,
            )

            repos.replaceDataset {
                db.userSettingsDao().upsert(UserSettings(smartSuggest = true))
            }
            assertTrue(
                "restored Smart suggest on did not reach the learner",
                withTimeoutOrNull(20_000) { repos.learnedSplits.model.first { it.smartSuggest } } != null,
            )

            repos.replaceDataset {
                db.userSettingsDao().upsert(UserSettings(smartSuggest = false))
            }
            assertTrue(
                "restored Smart suggest off did not reach the learner",
                withTimeoutOrNull(20_000) { repos.learnedSplits.model.first { !it.smartSuggest } } != null,
            )
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }
}
