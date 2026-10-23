package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.UnitSystem
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.web.supabase.UserSettingsRepository
import kotlinx.coroutines.flow.StateFlow

class RoomUserSettingsRepository(
    private val store: RoomStore,
    private val onSmartSuggest: suspend (Boolean) -> Unit,
) : UserSettingsRepository {
    override val settings: StateFlow<UserSettings> = store.settings

    override suspend fun refresh() {
        store.write {
            val row = db.userSettingsDao().get()?.toDomain()
            if (row != null) settings = row
        }
    }

    override suspend fun setPaletteChoice(choice: String) {
        store.write { upsertSettings(settings.copy(glassPaletteChoice = choice)) }
    }

    override suspend fun setBodyweight(lbs: Int?) {
        store.write { upsertSettings(settings.copy(userBodyweightLbs = lbs)) }
    }

    override suspend fun setTimeZone(timeZone: String) {
        store.write { upsertSettings(settings.copy(timeZone = timeZone)) }
    }

    override suspend fun setUnitSystem(unitSystem: UnitSystem) {
        store.write { upsertSettings(settings.copy(unitSystem = unitSystem)) }
    }

    override suspend fun setSmartSuggest(enabled: Boolean) {
        store.write { upsertSettings(settings.copy(smartSuggest = enabled)) }
        onSmartSuggest(enabled)
    }
}
