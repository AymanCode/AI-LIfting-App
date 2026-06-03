package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlassSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val _paletteChoice = MutableStateFlow(GlassPaletteChoice.Sage)
    val paletteChoice: StateFlow<GlassPaletteChoice> = _paletteChoice.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = database.userSettingsDao().get()
            _paletteChoice.value = GlassPaletteChoice.fromStorageKey(settings?.glassPaletteChoice)
        }
    }

    fun setPaletteChoice(choice: GlassPaletteChoice) {
        if (_paletteChoice.value == choice) return
        _paletteChoice.value = choice
        viewModelScope.launch {
            val current = database.userSettingsDao().get() ?: UserSettings()
            database.userSettingsDao().upsert(
                current.copy(glassPaletteChoice = choice.storageKey)
            )
        }
    }
}
