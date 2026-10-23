package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.UnitSystem
import com.ayman.ecolift.data.UserSettings
import kotlinx.coroutines.flow.StateFlow

interface UserSettingsRepository {
    val settings: StateFlow<UserSettings>
    suspend fun refresh()
    suspend fun setPaletteChoice(choice: String)
    suspend fun setBodyweight(lbs: Int?)
    suspend fun setTimeZone(timeZone: String)
    suspend fun setUnitSystem(unitSystem: UnitSystem)
    suspend fun setSmartSuggest(enabled: Boolean)
}
