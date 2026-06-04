package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.normalizedUserBodyweightLbs
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlassSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val _paletteChoice = MutableStateFlow(GlassPaletteChoice.Sage)
    val paletteChoice: StateFlow<GlassPaletteChoice> = _paletteChoice.asStateFlow()
    private val _userBodyweightLbs = MutableStateFlow<Int?>(null)
    val userBodyweightLbs: StateFlow<Int?> = _userBodyweightLbs.asStateFlow()

    init {
        viewModelScope.launch {
            database.userSettingsDao().observe().collect { settings ->
                _paletteChoice.value = GlassPaletteChoice.fromStorageKey(settings?.glassPaletteChoice)
                _userBodyweightLbs.value = normalizedUserBodyweightLbs(settings?.userBodyweightLbs)
            }
        }
    }

    fun setPaletteChoice(choice: GlassPaletteChoice) {
        if (_paletteChoice.value == choice) return
        _paletteChoice.value = choice
        viewModelScope.launch {
            updateSettings { current ->
                current.copy(glassPaletteChoice = choice.storageKey)
            }
        }
    }

    fun setUserBodyweightLbs(bodyweightLbs: Int?) {
        val normalizedBodyweight = normalizedUserBodyweightLbs(bodyweightLbs)
        if (_userBodyweightLbs.value == normalizedBodyweight) return
        _userBodyweightLbs.value = normalizedBodyweight
        viewModelScope.launch {
            updateSettings { current ->
                current.copy(userBodyweightLbs = normalizedBodyweight)
            }
        }
    }

    private suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        database.withTransaction {
            val current = database.userSettingsDao().get() ?: UserSettings()
            database.userSettingsDao().upsert(transform(current))
        }
    }
}
