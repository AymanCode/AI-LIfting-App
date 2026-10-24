package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.normalizedUserBodyweightLbs
import com.ayman.ecolift.data.splits.isSettled
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.notSaved
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class GlassSettingsViewModel(private val repos: AppRepos) : ViewModel() {
    val paletteChoice: StateFlow<GlassPaletteChoice> = repos.settings.settings
        .map { s -> GlassPaletteChoice.entries.firstOrNull { it.name == s.glassPaletteChoice } ?: GlassPaletteChoice.Sage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlassPaletteChoice.Sage)

    val userBodyweightLbs: StateFlow<Int?> = repos.settings.settings
        .map { normalizedUserBodyweightLbs(it.userBodyweightLbs) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val smartSuggestEnabled: StateFlow<Boolean> = repos.settings.settings
        .map { it.smartSuggest }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setPaletteChoice(choice: GlassPaletteChoice) {
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            runCatching { repos.settings.setPaletteChoice(choice.name) }
                .onFailure {
                    if (it is CancellationException) throw it
                    SyncErrors.report(notSaved("Palette not saved", it))
                }
        }
    }

    fun setUserBodyweightLbs(lbs: Int?) {
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            runCatching { repos.settings.setBodyweight(normalizedUserBodyweightLbs(lbs)) }
                .onFailure {
                    if (it is CancellationException) throw it
                    SyncErrors.report(notSaved("Bodyweight not saved", it))
                }
        }
    }

    /**
     * Saves the toggle, then reloads the page: Smart Suggest is read once at startup. [onReload]
     * runs only when the reload does; a failed save leaves the page (and the caller) untouched.
     */
    fun setSmartSuggestEnabled(enabled: Boolean, onReload: () -> Unit) {
        if (smartSuggestEnabled.value == enabled) return
        viewModelScope.launch {
            runCatching { repos.settings.setSmartSuggest(enabled) }
                .onFailure {
                    if (it is CancellationException) throw it
                    SyncErrors.report(notSaved("Smart suggest not saved", it))
                }
                .onSuccess {
                    // A reload drops an in-flight board save (not durable); wait for one to land.
                    withTimeoutOrNull(3_000) {
                        repos.workout.splitMemberChoices.first { it.isSettled }
                    }
                    onReload()
                }
        }
    }
}
